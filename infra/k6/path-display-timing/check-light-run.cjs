const assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path'),vm=require('node:vm');
const {scope}=require('./run.cjs');
const {runLight}=require('./light-run.cjs');
const {armLightTiming}=require('./page-light-timing.cjs');
const root=fs.mkdtempSync(path.join(os.tmpdir(),'suri-light-run-'));
// A call that skips the fake io fails here instead of running the real git or ssh.
process.env.PATH=fs.mkdtempSync(path.join(root,'no-bin-'));
const config={...scope,accountId:'c1000000-0000-4000-8000-000000000001',origin:'https://suri-map.sonic8-8.com',target:'7EC67B1ADE0732A5B21357B5872144A9',key:'__lightRunTest',
 script:'/assets/index-test.js',deploymentEvidence:__filename,limits:['1','80','480'],deadlineMs:1050000,maxResponses:800,maxBodyCharacters:134217728,sampleIntervalMs:250};
const boardPath='/incidents/'+scope.incidentId+'/board';
// Fake page events with fractional values, so any rounding or recomputation shows up.
const opened={incidentId:scope.incidentId,atMs:812.5,stage:'board_open_clicked',timeOriginMs:1761700000000.25,elapsedMs:813.75};
const idle={incidentId:scope.incidentId,atMs:4321.125,stage:'all_paths_map_idle',timeOriginMs:1761700000000.25,elapsedMs:4321.375};
const fakeVersion={commit:'0123456789abcdef0123456789abcdef01234567',uncommittedChanges:false};

// The real page code runs in a fake tab whose clock and timers move only when the run pauses.
function browser(){
 const start=1761700000000;let clock=start,id=0;const timers=new Map(),storage=new Map();
 const window=new EventTarget(),document=new EventTarget(),location={origin:config.origin,pathname:'/incidents'};
 document.visibilityState='visible';
 document.querySelector=selector=>selector.startsWith('script')?{getAttribute:()=>config.script}:location.pathname==='/incidents'?{}:null;
 const token='x.'+Buffer.from(JSON.stringify({accountId:config.accountId,exp:start/1000+7200})).toString('base64url')+'.y';
 storage.set('suriMapAccessToken',token);
 const timer=repeat=>(fn,ms)=>{timers.set(++id,{fn,ms,repeat,due:clock+ms});return id;};
 const context=vm.createContext({window,document,location,atob,Date:{now:()=>clock},performance:{now:()=>clock-start,timeOrigin:start},
  sessionStorage:{getItem:k=>storage.get(k)??null,setItem:(k,v)=>storage.set(k,String(v))},
  setInterval:timer(true),setTimeout:timer(false),clearInterval:i=>timers.delete(i),clearTimeout:i=>timers.delete(i),AbortSignal:{timeout:()=>undefined},
  fetch:async()=>({ok:true,json:async()=>({incidentId:scope.incidentId,activeOpId:scope.opId,selectedOpIds:[scope.opId]})})});
 const advance=ms=>{
  const until=clock+ms;
  for(;;){
   const next=[...timers].filter(([,t])=>t.due<=until).sort((a,b)=>a[1].due-b[1].due)[0];
   if(!next)break;
   const [i,t]=next;clock=t.due;if(t.repeat)t.due+=t.ms;else timers.delete(i);t.fn();
  }
  clock=until;
 };
 // Like boardMeasurement.ts, the board sends its events only while the flag is on.
 const send=detail=>{if(window.__SURI_MAP_MEASUREMENT_ENABLED__===true)window.dispatchEvent(new CustomEvent('suri-map:board-measurement',{detail}));};
 return {window,document,location,storage,context,send,advance,now:()=>clock,
  evaluate:async(fn,args)=>{context.args=JSON.parse(JSON.stringify(args));return JSON.parse(JSON.stringify((await vm.runInContext(`(${fn.toString()})(args)`,context))??null));}};
}

async function trial(mode){
 const dir=path.join(root,mode),b=browser(),actions=[],flags=[];let step=0,failed=false;
 const io={now:b.now,scriptVersion:()=>fakeVersion,
  runtime:()=>({fcm:'mock',limits:config.limits,image:mode==='runtime_changed'&&step>0?'new':'old'}),
  // One lost page call; the cleanup call after it reaches the page.
  evaluate:async(fn,args)=>{if(mode==='poll_failure'&&step===2&&!failed){failed=true;throw Error('aside_evaluation_failed');}return b.evaluate(fn,args);},
  action:async action=>{actions.push(action);
   if(action==='open'){flags.push(b.window.__SURI_MAP_MEASUREMENT_ENABLED__);b.location.pathname=boardPath;b.send(opened);return{clicked:true};}
   b.location.pathname='/incidents';return{ready:true};},
  pause:async()=>{step++;b.advance(1000);
   // The end event right after hiding finds the flag already off.
   if(step===2&&mode==='hidden'){b.document.visibilityState='hidden';b.document.dispatchEvent(new Event('visibilitychange'));b.send(idle);}
   if(step===3&&!['idle_missing','poll_failure'].includes(mode))b.send(idle);}};
 const r=await runLight(config,dir,io);
 const saved=JSON.parse(fs.readFileSync(path.join(dir,'result.json')));
 return {r,saved,dir,actions,flags,b};
}

(async()=>{
 const ok=await trial('success');
 assert.equal(ok.r.completed,true,JSON.stringify(ok.r));
 assert.deepEqual(ok.saved.boardEvents,{board_open_clicked:opened,all_paths_map_idle:idle});assert.deepEqual(ok.saved.missingBoardEvents,[]);
 assert.equal(ok.saved.boardEvents.all_paths_map_idle.atMs-ok.saved.boardEvents.board_open_clicked.atMs,3508.625);
 assert.deepEqual(ok.flags,[true]);assert.equal(ok.b.window.__SURI_MAP_MEASUREMENT_ENABLED__,false);
 assert.deepEqual(ok.actions,['open','return']);
 console.log('PASS both board events end the run, saved exactly as the page sent them, and the flag is off at the end');
 for(const [mode,error,missing] of [['idle_missing','deadline',['all_paths_map_idle']],['hidden','hidden',['all_paths_map_idle']],['poll_failure','aside_evaluation_failed',['all_paths_map_idle']],['runtime_changed','runtime_changed',[]]]){
  const t=await trial(mode);
  assert.equal(t.saved.completed,false,mode);assert.equal(t.saved.error,error,mode);
  assert.deepEqual(t.saved.missingBoardEvents,missing,mode);assert.deepEqual(t.saved.boardEvents.board_open_clicked,opened,mode);
  assert.equal(t.b.window.__SURI_MAP_MEASUREMENT_ENABLED__,false,mode);assert.equal(t.actions.at(-1),'return',mode);
  if(mode==='idle_missing')assert.ok(t.b.now()-1761700000000>=config.deadlineMs,'stopped before the deadline');
 }
 console.log('PASS a missing end event fails after the deadline, a hidden tab stops the run, and the flag is off after every failure');
 const files=fs.readdirSync(ok.dir).sort();
 assert.deepEqual(files,['attempt.json','exit.json','measurement-setup.json','result.json','runtime-after.json','runtime-before.json']);
 assert.deepEqual(JSON.parse(fs.readFileSync(path.join(ok.dir,'measurement-setup.json'))),{mode:'light',scriptVersion:fakeVersion,
  settings:{boardUrl:config.origin+boardPath,incidentId:scope.incidentId,opId:scope.opId,limits:{segmentsPerPath:'1',maxSegments:'80',maxCoordinates:'480'}}});
 console.log('PASS measurement-setup.json marks the light mode, and no DB snapshot, response or map source file is written');
 // The light page code never touches response bodies, the map or the path recording hook.
 for(const word of ['Response','__SURI_MAP_OBSERVER__','queryRenderedFeatures','sourcedata','getSource'])assert.equal(armLightTiming.toString().includes(word),false,word);
 console.log('PASS the light page code does not read responses or the map');
 const secret=/eyJ[\w-]+\.[\w-]+|Bearer\s|PRIVATE KEY|password/i;
 for(const mode of ['success','idle_missing','hidden','poll_failure','runtime_changed'])for(const name of fs.readdirSync(path.join(root,mode)))
  assert.doesNotMatch(fs.readFileSync(path.join(root,mode,name),'utf8'),secret,path.join(mode,name));
 console.log('PASS no token, password or private key in any result file');
})().catch(e=>{console.error(e);process.exitCode=1;});
