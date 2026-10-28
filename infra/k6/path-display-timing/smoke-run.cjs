const fs = require('node:fs');
const path = require('node:path');
const { execFile } = require('node:child_process');
const { promisify } = require('node:util');
const { installPathObserver } = require('./page-path-observer.cjs');
const { readDelta, createCheckpointWriter, restoreCheckpoints } = require('./checkpoint.cjs');
const { readReadiness, readTarget, armRunner } = require('./page-readiness.cjs');
const { serverOf } = require('./server.cjs');
const execute = promisify(execFile);

function parseResult(stdout) {
  if (stdout.includes('[error |')) throw Error('aside_error');
  const line = stdout.split('\n').find(value => value.startsWith('RESULT:'));
  if (!line) throw Error('result_missing');
  return JSON.parse(line.slice(7));
}

function validateConfig(config) {
  const uuid = /^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i;
  if (config.origin !== 'https://suri-map.sonic8-8.com' || !/^[A-F0-9]{32}$/.test(config.target) ||
      ![config.accountId, config.incidentId, config.opId].every(value => uuid.test(value)) ||
      !/^__[A-Za-z0-9_]+$/.test(config.key) || !/^\/assets\/index-[\w-]+\.js$/.test(config.script) ||
      typeof config.deploymentEvidence !== 'string' || !fs.existsSync(config.deploymentEvidence)) throw Error('invalid_config');
  serverOf(config);
}

function assessSmoke(result, opId) {
  const rows = result.collected?.state?.rows ?? [];
  const responses = rows.filter(row => ['segments', 'changes'].includes(row.kind) && typeof row.body === 'string');
  let scopeMatches = false;
  try {
    scopeMatches = responses.length > 0 && responses.every(row => {
      const body = JSON.parse(row.body);
      return row.status === 200 && Array.isArray(body.paths) && body.paths.every(item => item.opId === opId);
    });
  } catch (_) { /* Malformed captures cannot pass the smoke check. */ }
  return !result.error && !result.finalCaptureFailed && !result.returnFailed && !result.stopRequestFailed &&
    !result.localRecoveryFailed && result.returned?.ready === true && result.returned?.listVisible === true && result.returned?.path === '/incidents' &&
    result.returned?.observer === false && result.returned?.saved === true &&
    result.collected.checkpoints >= 2 && result.collected.errors.length === 0 && !result.collected.incompleteTail &&
    ['deadline', 'response_limit', 'resource_limit'].includes(result.reason) && scopeMatches &&
    rows.some(row => row.kind === 'map_connected') && rows.some(row => row.kind === 'map_first_observed') &&
    !rows.some(row => row.kind === 'cleanup_failed' || row.kind === 'stop_handler_failed');
}

async function waitForList(p, snapshot, config) {
  const began = Date.now();
  const current = new URL(p.url());
  if (current.origin !== config.origin || !['/incidents', `/incidents/${config.incidentId}/board`].includes(current.pathname)) {
    throw Error('user_navigation');
  }
  if (current.pathname !== '/incidents') await p.goto(config.origin + '/incidents', { timeout: 5000 });
  console.log((await snapshot(p, { interactive: true })).tree);
  let timedOut = false, waitError;
  try { await p.locator('[aria-label="사건 카드 목록"]').waitFor({ state: 'visible', timeout: 8000 }); }
  catch (error) { timedOut = true; waitError = { name: error.name, message: error.message }; }
  const final = await snapshot(p, { interactive: true }); console.log(final.diff);
  const state = await p.evaluate(config => {
    let accountMatches = false;
    try {
      const claims = JSON.parse(atob((sessionStorage.getItem('suriMapAccessToken') || '').split('.')[1]
        .replace(/-/g, '+').replace(/_/g, '/')));
      accountMatches = claims.accountId === config.accountId && claims.exp * 1000 > Date.now();
    } catch (_) { /* Unreadable credentials fail the return check. */ }
    return { path: location.pathname, script: document.querySelector('script[type=module][src]')?.getAttribute('src'),
      accountMatches, observer: Boolean(window.__SURI_MAP_OBSERVER__),
      saved: JSON.parse(sessionStorage.getItem(config.key + 'Exit') || 'null')?.saved === true };
  }, config);
  const listVisible = final.tree.includes('region "사건 카드 목록"');
  return { ...state, listVisible, timedOut, waitError, waitMs: Date.now() - began,
    ready: !timedOut && listVisible && state.path === '/incidents' && state.script === config.script && state.accountMatches && !state.observer };
}

async function run(config, outputDirectory, call = callAside) {
  validateConfig(config);
  // mkdir without recursive prevents reusing or overwriting an attempted trial.
  fs.mkdirSync(outputDirectory, { mode: 0o700 });
  fs.writeFileSync(path.join(outputDirectory, 'attempt.json'), JSON.stringify(config), { flag: 'wx', mode: 0o600 });
  const file = path.join(outputDirectory, 'checkpoints.jsonl');
  const writer = createCheckpointWriter(file);
  const result = { started: new Date().toISOString(), calls: [], performanceComparison: false };
  const attach = `const p=await attachBrowserTab(${JSON.stringify(config.target)});`;
  async function repl(code, timeout = 10000) {
    const entry = { started: new Date().toISOString() }; result.calls.push(entry);
    try { return parseResult(await call(attach + code, timeout)); }
    finally { entry.ended = new Date().toISOString(); }
  }
  const capture = () => writer.capture(async cursors => repl(`console.log('RESULT:'+JSON.stringify(await p.evaluate(${readDelta.toString()},${JSON.stringify({ key: config.key, cursors })})));`));
  let armed = false;
  try {
    result.before = await repl(`console.log((await snapshot(p,{interactive:true})).tree);
      console.log('RESULT:'+JSON.stringify(await p.evaluate(${readReadiness.toString()},${JSON.stringify(config)})));`);
    result.target = await repl(`console.log('RESULT:'+JSON.stringify(await p.evaluate(${readTarget.toString()},${JSON.stringify(config)})));`);
    // Set before dispatch: an installation may succeed even when its acknowledgement is lost.
    armed = true;
    const end = Date.now() + 45000;
    await repl(`const s=await snapshot(p,{interactive:true});console.log(s.tree);
      const matches=[...s.tree.matchAll(/button "상황판 열기" \\[ref=(\\w+)\\]/g)];
      if(matches.length!==1)throw Error('board_button_ambiguous');
      await p.evaluate(config=>(${armRunner.toString()})(config,${installPathObserver.toString()},${readReadiness.toString()}),${JSON.stringify(config)});
      await p.locator(matches[0][1]).click({timeout:5000});
      console.log((await snapshot(p,{interactive:true})).diff);console.log('RESULT:'+JSON.stringify({clicked:true}));`);
    while (Date.now() < end) {
      const delta = await capture();
      if (delta.state.stopped) { result.reason = delta.state.reason; break; }
      if (delta.page.path !== `/incidents/${config.incidentId}/board`) throw Error('wrong_board');
      if (delta.page.visibility !== 'visible' || delta.page.measurement) throw Error('environment_changed');
      await new Promise(resolve => setTimeout(resolve, 1000));
    }
    if (!result.reason) throw Error('external_deadline');
  } catch (error) { result.error = error.message; }
  finally {
    if (armed) {
      if (!result.reason) {
        try {
          await repl(`console.log('RESULT:'+JSON.stringify(await p.evaluate(key=>{
            window[key+'Control']?.stop('runner_cleanup'); return {stopRequested:true};
          },${JSON.stringify(config.key)})));`, 5000);
        } catch (_) { result.stopRequestFailed = true; }
      }
      // Let the new document render before reading its saved final state.
      try {
        result.returned = await repl(`console.log('RESULT:'+JSON.stringify(await (${waitForList.toString()})(p,snapshot,${JSON.stringify(config)})));`, 15000);
        if (!result.returned.ready) result.returnFailed = true;
      } catch (_) { result.returnFailed = true; }
      try { await capture(); } catch (_) { result.finalCaptureFailed = true; }
    }
    writer.close();
    try { result.collected = restoreCheckpoints(file); } catch (_) { result.localRecoveryFailed = true; }
    result.smokePassed = assessSmoke(result, config.opId);
    result.ended = new Date().toISOString();
    fs.writeFileSync(path.join(outputDirectory, 'result.json'), JSON.stringify(result, null, 2), { flag: 'wx', mode: 0o600 });
  }
  return result;
}

async function callAside(code, timeout) {
  if (code.length > 28000) throw Error('windows_command_limit');
  try {
    const { stdout } = await execute('aside.exe', ['repl', `await(async()=>{${code}})()`], { timeout, maxBuffer: 12000000 });
    return stdout;
  } catch (_) { throw Error('aside_transport_failed'); }
}

module.exports = { run, parseResult, validateConfig, assessSmoke, waitForList };
if (require.main === module) {
  if (process.argv.length !== 4) throw Error('Usage: node smoke-run.cjs confirmed-config.json NEW-output-directory');
  run(JSON.parse(fs.readFileSync(process.argv[2])), process.argv[3]).then(result => {
    console.log(JSON.stringify({ smokePassed: result.smokePassed, reason: result.reason, error: result.error, returned: result.returned,
      finalCaptureFailed: result.finalCaptureFailed }));
    if (!result.smokePassed) process.exitCode = 1;
  }).catch(() => { console.error('Runner failed; inspect preserved attempt/result. No automatic retry.'); process.exitCode = 1; });
}
