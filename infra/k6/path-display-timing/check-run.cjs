const assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {run,classifyWrite,onlyPlannedChange}=require('./run.cjs');
const {scope,createGpsPlan}=require('./gps-plan.cjs');
const before=require('./fixtures/writer-path-before.json');
const root=fs.mkdtempSync(path.join(os.tmpdir(),'suri-full-flow-'));
const config={...scope,origin:'https://suri-map.sonic8-8.com',target:'7EC67B1ADE0732A5B21357B5872144A9',key:'__fullFlowTest',
 script:'/assets/index-test.js',deploymentEvidence:__filename,limits:['1','80','480'],deadlineMs:1050000,maxResponses:800,maxBodyCharacters:134217728,sampleIntervalMs:250};
const token='test.'+Buffer.from(JSON.stringify({exp:Date.now()/1000+7200})).toString('base64url')+'.test';
const credential={accessToken:token};

async function trial(mode){
 const dir=path.join(root,mode),events=[],rows=[],storage={};let writes=0,after,dbAfter,now=Date.now();
 const oldSegment={id:before.latestSegment.id,version:'1',startPointOrder:762,endPointOrder:767,movementType:'FOOT',coordinates:before.recentPoints.map(p=>[p.lon,p.lat])};
 const dbBefore={incidentId:scope.incidentId,opId:scope.opId,paths:[{id:scope.pathId,accountId:scope.accountId,opId:scope.opId,status:'RECORDING',version:before.version,segments:[oldSegment]}]};
 const addPage=(db,done)=>{rows.push({kind:'segments',status:200,returned:true,bodyReadAt:done?300:50,body:JSON.stringify({hasMore:!done,nextSearchPathId:null,paths:db.paths.map(p=>({...p,baselineVersion:p.version,
 segments:p.segments.map(s=>({...s,geometry:{type:'LineString',coordinates:s.coordinates},coordinates:undefined,startedAt:'2026-10-15T00:00:00Z',endedAt:'2026-10-15T00:00:15Z'})),
 segmentsProgress:{completed:done,beforeStartPointOrder:done?0:762},changesProgress:{completed:true,beforeStartPointOrder:0,appliedVersion:p.version,targetVersion:p.version}}))})});};
 const io={now:()=>now,pause:async()=>{now+=1000;},runtime:()=>({fcm:mode==='fcm'?'firebase':'mock',limits:config.limits}),
 writer:()=>after??structuredClone(before),snapshot:file=>{const d=file.endsWith('database-before.json')?dbBefore:dbAfter;fs.writeFileSync(file,JSON.stringify(d),{flag:'wx'});return d;},
 action:async action=>{events.push(action);if(action==='open'){rows.push({kind:'map_connected',returned:true,at:5},{kind:'map_first_observed',returned:true,at:55});addPage(dbBefore,false);}return{ready:mode!=='return_failure'};},
 evaluate:async(fn,args)=>{
  if(fn.name==='readReadiness')return{tokenSeconds:mode==='expired'?10:7200};
  if(fn.name==='readTarget')return{};
  if(fn.name==='readManifest'){
   if(mode==='capture_failure'&&writes)throw Error('capture_failed');
   const from=args.cursors,next={rows:rows.length,resources:0,cadence:0,visibility:0};
   return{from,next,records:{rows:structuredClone(rows.slice(from.rows)),resources:[],cadence:[],visibility:[]},lengths:next,bodies:[],state:{started:0,timeOrigin:1000,wallStarted:1000,stopped:false},page:{path:'/incidents/'+scope.incidentId+'/board',visibility:'visible',measurement:false}};
  }
  if(fn.name==='confirmCapture'){if(args.finish){events.push('finish');storage.exit={reason:'complete',finalCheckpointSaved:true};}return{activeWhenChecked:true,stopRequested:!!args.finish};}
  if(fn.name==='sendGpsBatch'){
   writes++;events.push('send');assert.ok(fs.existsSync(path.join(dir,'gps-plan.json')));assert.ok(fs.existsSync(path.join(dir,'gps-dispatch.json')));
   const p=args.plan,body={...p.body,points:p.body.points.map((v,i)=>({...v,clientTs:new Date(100000+i*2500).toISOString()}))};
   after={...before,version:p.target.version,pointCount:before.pointCount+6,recentPoints:body.points.map((v,i)=>({pointId:v.pointId,pointOrder:before.pointCount+i,lon:v.lon,lat:v.lat,elapsedRealtimeNanos:String(v.elapsedRealtimeNanos),clientTs:v.clientTs})),latestSegment:{id:p.target.segmentId,version:'1',lastChangedPathVersion:p.target.version,startPointOrder:768,endPointOrder:773,movementType:'FOOT'}};
   dbAfter=structuredClone(dbBefore);dbAfter.paths[0].version=p.target.version;dbAfter.paths[0].segments.push({id:p.target.segmentId,version:'1',startPointOrder:768,endPointOrder:773,movementType:'FOOT',coordinates:p.target.coordinates});
   if(mode==='unknown_commit')throw Error('aside_evaluation_failed');
   rows.push({kind:'gps_request_prepared',requestId:p.requestId,body:JSON.stringify(body),returned:true,at:90},
    {kind:'gps_write_started',requestId:p.requestId,clock:'observer-relative-performance',returned:true,at:100},
    {kind:'gps_write_completed',requestId:p.requestId,clock:'observer-relative-performance',accepted:true,status:200,returned:true,at:200},
    {kind:'map_target_expected',target:p.target,returned:true,at:90},
    {kind:'map_target_observed',target:p.target,returned:true,at:250,sourceReadStarted:210,sourceReadEnded:240});
   addPage(dbAfter,true);rows.push({...rows.at(-1),kind:'changes'});return{accepted:true};
  }
  const source=fn.toString();
  if(source.startsWith('function(config)')){events.push('arm');return{armed:true};}
  if(source.includes('captureSource')){
   const features=dbAfter.paths[0].segments.map((s,i)=>({type:'Feature',geometry:{type:'LineString',coordinates:i?[oldSegment.coordinates.at(-1),...s.coordinates]:s.coordinates},properties:{slot:'path',entityId:s.id,searchPathId:scope.pathId,searchPathVersion:dbAfter.paths[0].version,opId:scope.opId,accountId:scope.accountId,movementType:'FOOT'}}));
   rows.push({kind:'map_final_source',returned:true,body:JSON.stringify({type:'FeatureCollection',features}),at:400});return{};
  }
  if(source.includes(".stop('runner_failed')")||source.includes(".stop('capture_failed')")){events.push('stop');storage.exit={reason:'runner_failed',finalCheckpointSaved:false};return true;}
  if(source.includes('sessionStorage.getItem'))return storage.exit;
  events.push('arm');return{armed:true};
 }};
 const r=await run(config,dir,credential,io);
 assert.equal(writes,['fcm','expired'].includes(mode)?0:1,JSON.stringify({r,events}));
 assert.ok(!fs.readFileSync(path.join(dir,'result.json'),'utf8').includes(token));
 return{r,events,dir};
}
(async()=>{
 const ok=await trial('success');assert.equal(ok.r.completed,true,JSON.stringify(ok));assert.equal(ok.r.gps.sendToObservedMs,150);assert.equal(ok.r.httpResponses,3);assert.deepEqual(ok.events,['arm','open','send','finish','return']);
 for(const mode of ['fcm','expired','unknown_commit','capture_failure','return_failure']){const t=await trial(mode);assert.equal(t.r.completed,false);if(mode==='unknown_commit')assert.equal(t.r.writeObservation,'planned_points_observed');}
 const plan=createGpsPlan(before,'mock');assert.equal(classifyWrite(plan,before),'not_observed_at_read_time');assert.equal(classifyWrite(plan,{...before,version:'999'}),'conflicting_state');
 await assert.rejects(run({...config,maxResponses:801},path.join(root,'badconfig'),credential,{}));
 console.log('PASS full caller order, fsynced plan before single send, real page merge/capture/final DB+map check, unknown commit, cleanup and return failure, config/auth/FCM guards');
})().catch(e=>{console.error(e);process.exitCode=1;});
