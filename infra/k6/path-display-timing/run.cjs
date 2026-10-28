const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { execFileSync, execFile } = require('node:child_process');
const { promisify } = require('node:util');
const { createHash } = require('node:crypto');
const { createAsideEvaluator } = require('./aside-evaluator.cjs');
const { createHistoryCapture } = require('./history-capture.cjs');
const { armHistoryCapture } = require('./page-history-capture.cjs');
const { installPathObserver } = require('./page-path-observer.cjs');
const { readReadiness, readTarget } = require('./page-readiness.cjs');
const { validateConfig, parseResult, waitForList } = require('./smoke-run.cjs');
const { restoreCheckpoints } = require('./checkpoint.cjs');
const { validateSnapshot, pagesComplete, compareMeasurement } = require('./history-compare.cjs');
const { serverOf, sshArgs } = require('./server.cjs');
const { createSearchPathPages, parseSearchPathPage, mergeSearchPathPage } = require('../../../frontend/src/features/path/model/searchPathPages.ts');
const execute = promisify(execFile);
// Same fixed incident and operational period as db-snapshot.sql.
const scope = Object.freeze({ incidentId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', opId: '88888888-8888-8888-8888-888888880001' });
// The measurement code: this directory and the Frontend module it loads.
const repository = path.join(__dirname, '../../..');
const scriptPaths = ['infra/k6/path-display-timing', 'frontend/src/features/path/model/searchPathPages.ts'];
const boardStages = ['board_open_clicked', 'all_paths_map_idle'];

function scriptVersion() {
  try {
    const git = args => execFileSync('git', ['-C', repository, ...args], { timeout: 10000, encoding: 'utf8' });
    const commit = git(['rev-parse', 'HEAD']).trim();
    assert.match(commit, /^[0-9a-f]{40}$/);
    return { commit, uncommittedChanges: git(['status', '--porcelain', '--', ...scriptPaths]).trim() !== '' };
  } catch (_) { throw Error('script_version_unreadable'); }
}
function measurementSettings(config) {
  const [segmentsPerPath, maxSegments, maxCoordinates] = config.limits;
  return { boardUrl: `${config.origin}/incidents/${config.incidentId}/board`, incidentId: config.incidentId, opId: config.opId,
    sampleIntervalMs: config.sampleIntervalMs, limits: { segmentsPerPath, maxSegments, maxCoordinates } };
}
// Each value is the page's event as sent. A missing one stays null and is listed, never filled from another time.
function boardEventsOf(rows) {
  const boardEvents = Object.fromEntries(boardStages.map(stage =>
    [stage, rows.find(row => row.kind === 'board_measurement' && row.event?.stage === stage)?.event ?? null]));
  return { boardEvents, missingBoardEvents: boardStages.filter(stage => !boardEvents[stage]) };
}

function save(file, value) {
  const fd = fs.openSync(file, 'wx', 0o600);
  try { fs.writeFileSync(fd, JSON.stringify(value)); fs.fsyncSync(fd); } finally { fs.closeSync(fd); }
}
function runtime(server = serverOf()) {
  const raw = execFileSync('ssh', sshArgs(server, 'docker inspect suri-map-backend suri-map-frontend'),
    { timeout: 15000, maxBuffer: 2000000 });
  const [backend, frontend] = JSON.parse(raw);
  const env = Object.fromEntries(backend.Config.Env.map(v => [v.slice(0,v.indexOf('=')),v.slice(v.indexOf('=')+1)]));
  return { image: backend.Image, started: backend.State.StartedAt, frontendImage: frontend.Image,
    frontendStarted: frontend.State.StartedAt, fcm: env.FCM_PROVIDER,
    environmentHash: createHash('sha256').update(JSON.stringify(Object.entries(env).sort())).digest('hex'),
    limits: ['SEGMENTSPERPATH','MAXSEGMENTS','MAXCOORDINATES'].map(k => env['SURIMAP_BOARD_SEARCHPATH_'+k]) };
}
function snapshot(file, server = serverOf()) {
  try { execFileSync(process.execPath, [path.join(__dirname, 'capture-db-snapshot.cjs'), file, server.sshHost, server.databaseContainer],
    { timeout: 50000, stdio: ['ignore','ignore','pipe'] }); }
  catch (_) { throw Error('database_snapshot_failed'); }
  const data = JSON.parse(fs.readFileSync(file)); validateSnapshot(data); return data;
}
// Compares only from the saved files so a finished result directory can be compared again.
function compareSaved(directory, config) {
  const read=name=>JSON.parse(fs.readFileSync(path.join(directory,name)));
  return compareMeasurement(read('server-responses.json'),read('map-source.json'),read('database-before.json'),
    read('database-after.json'),config.incidentId,config.opId);
}
// Same criterion as the capture finish and DB comparison; see pathComplete for the late-baseline case.
const complete = pagesComplete;
async function pageAction(config, action) {
  // UI-only arguments on argv.
  const code = `await(async()=>{const p=await attachBrowserTab(${JSON.stringify(config.target)});` +
    (action === 'open' ? `const s=await snapshot(p,{interactive:true});console.log(s.tree);const matches=[...s.tree.matchAll(/button "상황판 열기" \\[ref=(\\w+)\\]/g)];if(matches.length!==1)throw Error('board_button_ambiguous');await p.locator(matches[0][1]).click({timeout:5000});console.log((await snapshot(p,{interactive:true})).diff);console.log('RESULT:'+JSON.stringify({clicked:true}));` :
      `const fn=(0,eval)(${JSON.stringify('('+waitForList.toString()+')')});console.log('RESULT:'+JSON.stringify(await fn(p,snapshot,${JSON.stringify(config)})));`) + `})()`;
  try {
    const output=await execute('aside.exe',['repl',code],{timeout:20000,maxBuffer:1000000});
    return {...parseResult(output.stdout), stdoutBytes:Buffer.byteLength(output.stdout)};
  }
  catch (_) { throw Error('browser_action_failed'); }
}
function realIO(config) {
  const server = serverOf(config);
  return { runtime: () => runtime(server), snapshot: file => snapshot(file, server), scriptVersion,
    evaluate: createAsideEvaluator({target:config.target,timeoutMs:30000}),
    action: action => pageAction(config, action), now: Date.now, pause: () => new Promise(r => setTimeout(r,1000)) };
}

async function run(config, directory, io = realIO(config)) {
  validateConfig(config);
  assert.equal(config.incidentId,scope.incidentId); assert.equal(config.opId,scope.opId);
  assert.deepEqual([config.deadlineMs,config.maxResponses,config.maxBodyCharacters,config.sampleIntervalMs], [1050000,800,134217728,250]);
  assert.ok(['1/80/480','4/320/1920','16/1280/7680','200/16000/64000','100000/100000/400000'].includes(config.limits.join('/')));
  fs.mkdirSync(directory,{mode:0o700}); save(path.join(directory,'attempt.json'),config);
  const result = { started:new Date().toISOString(), completed:false, calls:0, ...boardEventsOf([]) }, file=path.join(directory,'checkpoints.jsonl');
  let armed=false, capture, end, diskState={rows:[]};
  const evaluate = async (fn,args) => {
    if (end && io.now()>=end) throw Error('external_deadline');
    result.calls++; return io.evaluate(fn,args);
  };
  try {
    // Results are compared only with runs of the same script version and settings.
    save(path.join(directory,'measurement-setup.json'),{scriptVersion:io.scriptVersion(),settings:measurementSettings(config)});
    const environment=io.runtime(); save(path.join(directory,'runtime-before.json'),environment);
    assert.equal(environment.fcm,'mock'); assert.deepEqual(environment.limits,config.limits);
    io.snapshot(path.join(directory,'database-before.json'));
    const ready=await evaluate(readReadiness,config); assert.ok(ready.tokenSeconds*1000>config.deadlineMs+150000,'web_token_expiring');
    await evaluate(readTarget,config);
    capture=createHistoryCapture({file,key:config.key,incidentId:config.incidentId,opId:config.opId,evaluate});
    const arm = new Function('return function(config){return ('+armHistoryCapture.toString()+')(config,('+installPathObserver.toString()+'),('+readReadiness.toString()+'));}')();
    armed=true; end=io.now()+1080000;
    await evaluate(arm,config); await io.action('open');
    let pages=createSearchPathPages(), lastProgress=io.now(), fullReceivedAt, boardIdle=false;
    while (io.now()<end) {
      const delta=await capture.capture();
      for (const row of delta.records.rows) {
        diskState.rows.push(row);
        if (row.kind==='board_measurement' && row.event?.stage==='all_paths_map_idle') boardIdle=true;
        if (['segments','changes'].includes(row.kind)) {
          pages=mergeSearchPathPage(pages,parseSearchPathPage(JSON.parse(row.body),row.kind,[config.opId]),row.kind);
          lastProgress=io.now();
          if (complete(pages) && fullReceivedAt === undefined) fullReceivedAt=row.bodyReadAt;
        }
      }
      // The board's own end event closes the run; without it the run waits until the deadline.
      if (boardIdle && complete(pages)) {
        io.snapshot(path.join(directory,'database-after.json'));
        await capture.capture(); // Sequential fresh drain; finish rejects late responses instead of retrying.
        const {responses,mapSource,...finished}=await capture.finish();
        save(path.join(directory,'server-responses.json'),responses); save(path.join(directory,'map-source.json'),mapSource);
        result.comparison={...finished,...compareSaved(directory,config)};
        result.fullReceivedAt=fullReceivedAt;
        result.firstObservedAt=diskState.rows.find(r=>r.kind==='map_first_observed')?.at;
        result.httpResponses=diskState.rows.filter(r=>['segments','changes'].includes(r.kind)).length;
        const after=io.runtime();save(path.join(directory,'runtime-after.json'),after);assert.deepEqual(after,environment,'runtime_changed');
        result.completed=true; break;
      }
      if (!complete(pages) && io.now()-lastProgress>30000) throw Error('no_progress');
      await io.pause();
    }
    if (!result.completed) throw Error('external_deadline');
  } catch (error) {
    result.error=/^[a-z_ ]{1,80}$/.test(error.message)?error.message:'run_failed';
  } finally {
    if (armed) {
      if (!result.completed) {
        try { await io.evaluate(key=>{window[key+'Control']?.stop('runner_failed');return true;},config.key); }
        catch (_) { result.cleanupUnconfirmed=true; }
      }
      try {
        result.returned=await io.action('return');
        result.exit=await io.evaluate(key=>JSON.parse(sessionStorage.getItem(key+'Exit')||'null'),config.key);
        save(path.join(directory,'exit.json'),result.exit);
        if (!result.returned.ready || result.exit?.reason!=='complete' || result.exit?.finalCheckpointSaved!==true) result.completed=false;
      } catch (_) { result.returnUnconfirmed=true; result.completed=false; }
    }
    if (capture) capture.close();
    if (fs.existsSync(file)) {
      try {const restored=restoreCheckpoints(file);result.checkpoints=restored.checkpoints;
        result.checkpointErrors=restored.errors.length;result.incompleteTail=restored.incompleteTail;
        const rows=restored.state?.rows??[], responses=rows.filter(r=>['segments','changes'].includes(r.kind));
        Object.assign(result,boardEventsOf(rows));
        result.httpResponses=responses.length;result.lastResponseBodyReadAt=responses.at(-1)?.bodyReadAt;
        result.firstObservedAt=rows.find(r=>r.kind==='map_first_observed')?.at;
        result.resourceCount=restored.state?.resources.length??0;
        result.recordedHttpDurationSumMs=(restored.state?.resources??[]).reduce((sum,r)=>sum+r.end-r.start,0);
        if(restored.errors.length||restored.incompleteTail)result.completed=false;}
      catch (_) {result.checkpointRecoveryFailed=true;result.completed=false;}
    }
    result.ended=new Date().toISOString();save(path.join(directory,'result.json'),result);
  }
  return result;
}

module.exports={run,runtime,scope,compareSaved,complete,save,pageAction,realIO,scriptVersion};
if(require.main===module){
  const config=JSON.parse(fs.readFileSync(process.argv[2]));
  run(config,process.argv[3]).then(r=>{console.log(JSON.stringify(r));if(!r.completed)process.exitCode=1;})
    .catch(()=>{console.error('run_preparation_failed');process.exitCode=1;});
}
