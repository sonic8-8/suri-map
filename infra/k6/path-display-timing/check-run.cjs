const assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {run,scope,compareSaved,scriptVersion}=require('./run.cjs');
const root=fs.mkdtempSync(path.join(os.tmpdir(),'suri-full-flow-'));
// A call that skips the fake exec fails here instead of running the real git or ssh.
process.env.PATH=fs.mkdtempSync(path.join(root,'no-bin-'));
const config={...scope,accountId:'c1000000-0000-4000-8000-000000000001',origin:'https://suri-map.sonic8-8.com',target:'7EC67B1ADE0732A5B21357B5872144A9',key:'__fullFlowTest',
 script:'/assets/index-test.js',deploymentEvidence:__filename,limits:['1','80','480'],deadlineMs:1050000,maxResponses:800,maxBodyCharacters:134217728,sampleIntervalMs:250};
const pathId='e1000000-0000-4000-8000-000000000001',accountId='a1000000-0000-4000-8000-000000000001';
const segment=(id,start,coordinates)=>({id,version:'1',startPointOrder:start,endPointOrder:start+coordinates.length-1,movementType:'FOOT',coordinates});
const dbBefore={incidentId:scope.incidentId,opId:scope.opId,paths:[{id:pathId,accountId,opId:scope.opId,status:'RECORDING',version:'2',
 segments:[segment('s1',0,[[127,37],[127.1,37]]),segment('s2',2,[[127.2,37],[127.3,37]])]}]};
// Fake page events with fractional values, so any rounding or recomputation shows up.
const opened={incidentId:scope.incidentId,atMs:812.5,stage:'board_open_clicked',timeOriginMs:1761700000000.25,elapsedMs:813.75};
const idle={incidentId:scope.incidentId,atMs:4321.125,stage:'all_paths_map_idle',timeOriginMs:1761700000000.25,elapsedMs:4321.375};
const fakeVersion={commit:'0123456789abcdef0123456789abcdef01234567',uncommittedChanges:true};

async function trial(mode){
 const dir=path.join(root,mode),events=[],rows=[],storage={};let now=Date.now(),step=0;
 const dbAfter=structuredClone(dbBefore);if(mode==='db_changed')dbAfter.paths[0].version='3';
 // The first page has only the newest segment; the run must keep reading until history is complete.
 const page=(kind,segments,done)=>rows.push({kind,status:200,returned:true,bodyReadAt:done?300:50,body:JSON.stringify({hasMore:false,nextSearchPathId:null,
  paths:dbBefore.paths.map(p=>({...p,baselineVersion:p.version,segments:segments.map(s=>({...s,geometry:{type:'LineString',coordinates:s.coordinates},coordinates:undefined,startedAt:'2026-10-15T00:00:00Z',endedAt:'2026-10-15T00:00:15Z'})),
  segmentsProgress:{completed:done,beforeStartPointOrder:done?0:2},changesProgress:{completed:true,beforeStartPointOrder:0,appliedVersion:p.version,targetVersion:p.version}}))})});
 // History is complete after step 2; the board's end event comes one step later.
 const io={now:()=>now,pause:async()=>{now+=1000;step++;if(step===1)page('segments',dbBefore.paths[0].segments.slice(0,1),true);if(step===2)page('changes',[],true);
  if(step===3&&mode!=='idle_missing')rows.push({kind:'board_measurement',returned:true,at:4300,event:idle});},
 runtime:()=>({fcm:mode==='fcm'?'firebase':'mock',limits:config.limits}),scriptVersion:()=>fakeVersion,
 snapshot:file=>{const d=file.endsWith('database-before.json')?dbBefore:dbAfter;events.push(path.basename(file));fs.writeFileSync(file,JSON.stringify(d),{flag:'wx'});return d;},
 action:async action=>{events.push(action);if(action==='open'){
  if(mode!=='open_missing')rows.push({kind:'board_measurement',returned:true,at:3,event:opened});
  rows.push({kind:'map_connected',returned:true,at:5},{kind:'map_first_observed',returned:true,at:55});page('segments',dbBefore.paths[0].segments.slice(1),false);}return{ready:mode!=='return_failure'};},
 evaluate:async(fn,args)=>{
  if(fn.name==='readReadiness')return{tokenSeconds:7200};
  if(fn.name==='readTarget')return{};
  if(fn.name==='readManifest'){
   if(mode==='capture_failure'&&step>0)throw Error('capture_failed');
   const from=args.cursors,next={rows:rows.length,resources:0,cadence:0,visibility:0};
   return{from,next,records:{rows:structuredClone(rows.slice(from.rows)),resources:[],cadence:[],visibility:[]},lengths:next,bodies:[],state:{started:0,timeOrigin:1000,wallStarted:1000,stopped:false},page:{path:'/incidents/'+scope.incidentId+'/board',visibility:'visible',measurement:true}};
  }
  if(fn.name==='confirmCapture'){if(args.finish){events.push('finish');storage.exit={reason:'complete',finalCheckpointSaved:true};}return{activeWhenChecked:true,stopRequested:!!args.finish};}
  const source=fn.toString();
  if(source.startsWith('function(config)')){events.push('arm');return{armed:true};}
  if(source.includes('captureSource')){
   // The board joins the two connected segments into one line that keeps only the first segment ID.
   const coordinates=[[127,37],[127.1,37],[127.2,37],[127.3,37]];
   rows.push({kind:'map_final_source',returned:true,at:400,body:JSON.stringify({type:'FeatureCollection',features:[{type:'Feature',
    geometry:{type:'LineString',coordinates},properties:{entityId:'s1',searchPathId:pathId,searchPathVersion:'2',movementType:'FOOT',drawOrder:'0.3333333333333333'}}]})});return{};
  }
  if(source.includes(".stop('runner_failed')")||source.includes(".stop('capture_failed')")){events.push('stop');storage.exit={reason:'runner_failed',finalCheckpointSaved:false};return true;}
  if(source.includes('sessionStorage.getItem'))return storage.exit;
  throw Error('unexpected_page_call');
 }};
 const r=await run(config,dir,io);
 return{r,events,dir};
}
(async()=>{
 const ok=await trial('success');assert.equal(ok.r.completed,true,JSON.stringify(ok));
 assert.deepEqual(ok.events,['database-before.json','arm','open','database-after.json','finish','return']);
 assert.equal(ok.r.httpResponses,3);assert.equal(ok.r.fullReceivedAt,300);assert.equal('gpsAttempted' in ok.r,false);
 for(const name of ['gps-plan.json','gps-dispatch.json','writer-before.json','writer-after.json'])assert.equal(fs.existsSync(path.join(ok.dir,name)),false,name);
 const saved=JSON.parse(fs.readFileSync(path.join(ok.dir,'result.json'))).comparison;
 assert.deepEqual([saved.responseMatchesBefore,saved.afterMatchesBefore,saved.coordinates],[true,true,[{searchPathId:pathId,matches:true,firstDifferentIndex:null}]]);
 assert.deepEqual(JSON.parse(fs.readFileSync(path.join(ok.dir,'server-responses.json'))).map(r=>r.kind),['segments','segments','changes']);
 assert.equal(JSON.parse(fs.readFileSync(path.join(ok.dir,'map-source.json'))).type,'FeatureCollection');
 assert.deepEqual(compareSaved(ok.dir,config),{responseMatchesBefore:true,afterMatchesBefore:true,coordinates:saved.coordinates});
 console.log('PASS run reads full history without writing, saves responses and path source, and records coordinate and both DB comparisons');
 assert.deepEqual(ok.r.boardEvents,{board_open_clicked:opened,all_paths_map_idle:idle});assert.deepEqual(ok.r.missingBoardEvents,[]);
 assert.deepEqual(JSON.parse(fs.readFileSync(path.join(ok.dir,'result.json'))).boardEvents,{board_open_clicked:opened,all_paths_map_idle:idle});
 console.log('PASS run waits for the board end event and saves both board events exactly as the page sent them');
 for(const [mode,missing] of [['idle_missing','all_paths_map_idle'],['open_missing','board_open_clicked']]){
  const t=await trial(mode),saved=JSON.parse(fs.readFileSync(path.join(t.dir,'result.json')));
  assert.deepEqual(saved.missingBoardEvents,[missing],mode);assert.equal(saved.boardEvents[missing],null,mode);
  assert.deepEqual(Object.keys(saved.boardEvents),['board_open_clicked','all_paths_map_idle'],mode);
  if(mode==='idle_missing'){assert.equal(saved.completed,false);assert.equal(saved.error,'external_deadline');assert.equal('comparison' in saved,false);}
  else assert.equal(saved.completed,true);
 }
 console.log('PASS a board event that never came is listed as missing and left null, not filled from another time');
 const changed=await trial('db_changed');assert.equal(changed.r.completed,true);
 assert.deepEqual([changed.r.comparison.responseMatchesBefore,changed.r.comparison.afterMatchesBefore],[true,false]);
 console.log('PASS a DB change during the run is recorded, not hidden');
 for(const mode of ['fcm','capture_failure','return_failure']){const t=await trial(mode);assert.equal(t.r.completed,false,mode);}
 await assert.rejects(run({...config,maxResponses:801},path.join(root,'badconfig'),{}));
 console.log('PASS FCM guard, capture failure, return failure and config guard leave the run incomplete');
 const runs=['success','db_changed','idle_missing','open_missing','fcm','capture_failure','return_failure'].map(mode=>path.join(root,mode));
 for(const dir of runs)assert.deepEqual(JSON.parse(fs.readFileSync(path.join(dir,'measurement-setup.json'))),{scriptVersion:fakeVersion,
  settings:{boardUrl:'https://suri-map.sonic8-8.com/incidents/'+scope.incidentId+'/board',incidentId:scope.incidentId,opId:scope.opId,sampleIntervalMs:250,
   limits:{segmentsPerPath:'1',maxSegments:'80',maxCoordinates:'480'}}},dir);
 console.log('PASS result directory keeps the script version and run settings, also for a failed run');
 // JWT, bearer header, private key and password shapes.
 const secret=/eyJ[\w-]+\.[\w-]+|Bearer\s|PRIVATE KEY|password/i;
 for(const dir of runs)for(const name of fs.readdirSync(dir))assert.doesNotMatch(fs.readFileSync(path.join(dir,name),'utf8'),secret,path.join(dir,name));
 console.log('PASS no token, password or private key in any result file');
 // Synthetic credentials added to a valid config: both runners refuse it before the result directory exists.
 const {run:smokeRun,validateConfig}=require('./smoke-run.cjs');
 const secrets={accessToken:'FAKE_TOKEN_CHECK_ONLY',password:'FAKE_PASSWORD_CHECK_ONLY',privateKey:'-----BEGIN PRIVATE KEY-----\nFAKE_CHECK_ONLY\n-----END PRIVATE KEY-----'};
 for(const [name,value] of Object.entries(secrets))for(const [runner,start] of [['run',dir=>run({...config,[name]:value},dir,{})],
  ['smoke',dir=>smokeRun({...config,[name]:value},dir,async()=>{throw Error('unexpected_aside_call');})]]){
  const dir=path.join(root,`secret-${runner}-${name}`);
  await assert.rejects(start(dir),/invalid_config/,dir);
  assert.equal(fs.existsSync(dir),false,dir);
 }
 const full={...config,sshHost:'other-app',databaseContainer:'other-postgis'};
 assert.doesNotThrow(()=>validateConfig(full));
 console.log('PASS a config with keys the runners do not know, such as credentials, is refused before any result file is written');
 // Every allowed key with credentials inside its value, or an array or wrong type in place of the value, read back from JSON like CONFIG.json.
 const wrong=Object.keys(full).flatMap(name=>[[name,{value:full[name],...secrets}],[name,[full[name],secrets.password]],[name,[full[name]]]])
  .concat([['sampleIntervalMs','250'],['deadlineMs',0],['maxResponses',-1],['maxBodyCharacters',1.5],['limits',[1,80,480]],['limits',['1','80']],['limits',null]]);
 for(const [index,[name,value]] of wrong.entries())for(const [runner,start] of [['run',(c,dir)=>run(c,dir,{})],
  ['smoke',(c,dir)=>smokeRun(c,dir,async()=>{throw Error('unexpected_aside_call');})]]){
  const dir=path.join(root,`wrong-${runner}-${index}`),label=`${runner} ${name} ${JSON.stringify(value)}`;
  // sshHost and databaseContainer are refused by serverOf inside the same check.
  await assert.rejects(start(JSON.parse(JSON.stringify({...full,[name]:value})),dir),/^Error: invalid_(server_)?config$/,label);
  assert.equal(fs.existsSync(dir),false,label);
 }
 console.log(`PASS ${wrong.length} credential-carrying or wrongly typed values in allowed keys are refused by both runners before any result file`);
 // A fake exec answers the two git calls the real runner makes, so no git process starts.
 const calls=[];let status='';
 const git=(file,args)=>{calls.push([file,...args]);return args.includes('rev-parse')?fakeVersion.commit+'\n':status;};
 assert.deepEqual(scriptVersion(git),{commit:fakeVersion.commit,uncommittedChanges:false});
 status=' M infra/k6/path-display-timing/run.cjs\n?? infra/k6/path-display-timing/new.cjs\n';assert.deepEqual(scriptVersion(git),fakeVersion);
 const repository=path.join(__dirname,'../../..');
 assert.deepEqual(calls.slice(0,2),[['git','-C',repository,'rev-parse','HEAD'],
  ['git','-C',repository,'status','--porcelain','--','infra/k6/path-display-timing','frontend/src/features/path/model/searchPathPages.ts']]);
 assert.throws(()=>scriptVersion(()=>{throw Error('no git');}),/script_version_unreadable/);
 console.log('PASS script version is the current commit and whether the measurement code has uncommitted changes');
})().catch(e=>{console.error(e);process.exitCode=1;});
