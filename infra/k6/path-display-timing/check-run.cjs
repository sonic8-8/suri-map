const assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {run,scope,compareSaved}=require('./run.cjs');
const root=fs.mkdtempSync(path.join(os.tmpdir(),'suri-full-flow-'));
const config={...scope,accountId:'c1000000-0000-4000-8000-000000000001',origin:'https://suri-map.sonic8-8.com',target:'7EC67B1ADE0732A5B21357B5872144A9',key:'__fullFlowTest',
 script:'/assets/index-test.js',deploymentEvidence:__filename,limits:['1','80','480'],deadlineMs:1050000,maxResponses:800,maxBodyCharacters:134217728,sampleIntervalMs:250};
const pathId='e1000000-0000-4000-8000-000000000001',accountId='a1000000-0000-4000-8000-000000000001';
const segment=(id,start,coordinates)=>({id,version:'1',startPointOrder:start,endPointOrder:start+coordinates.length-1,movementType:'FOOT',coordinates});
const dbBefore={incidentId:scope.incidentId,opId:scope.opId,paths:[{id:pathId,accountId,opId:scope.opId,status:'RECORDING',version:'2',
 segments:[segment('s1',0,[[127,37],[127.1,37]]),segment('s2',2,[[127.2,37],[127.3,37]])]}]};

async function trial(mode){
 const dir=path.join(root,mode),events=[],rows=[],storage={};let now=Date.now();
 const dbAfter=structuredClone(dbBefore);if(mode==='db_changed')dbAfter.paths[0].version='3';
 // The first page has only the newest segment; the run must keep reading until history is complete.
 const page=(kind,segments,done)=>rows.push({kind,status:200,returned:true,bodyReadAt:done?300:50,body:JSON.stringify({hasMore:false,nextSearchPathId:null,
  paths:dbBefore.paths.map(p=>({...p,baselineVersion:p.version,segments:segments.map(s=>({...s,geometry:{type:'LineString',coordinates:s.coordinates},coordinates:undefined,startedAt:'2026-10-15T00:00:00Z',endedAt:'2026-10-15T00:00:15Z'})),
  segmentsProgress:{completed:done,beforeStartPointOrder:done?0:2},changesProgress:{completed:true,beforeStartPointOrder:0,appliedVersion:p.version,targetVersion:p.version}}))})});
 const io={now:()=>now,pause:async()=>{now+=1000;if(rows.length===3)page('segments',dbBefore.paths[0].segments.slice(0,1),true);if(rows.length===4)page('changes',[],true);},
 runtime:()=>({fcm:mode==='fcm'?'firebase':'mock',limits:config.limits}),
 snapshot:file=>{const d=file.endsWith('database-before.json')?dbBefore:dbAfter;events.push(path.basename(file));fs.writeFileSync(file,JSON.stringify(d),{flag:'wx'});return d;},
 action:async action=>{events.push(action);if(action==='open'){rows.push({kind:'map_connected',returned:true,at:5},{kind:'map_first_observed',returned:true,at:55});page('segments',dbBefore.paths[0].segments.slice(1),false);}return{ready:mode!=='return_failure'};},
 evaluate:async(fn,args)=>{
  if(fn.name==='readReadiness')return{tokenSeconds:7200};
  if(fn.name==='readTarget')return{};
  if(fn.name==='readManifest'){
   if(mode==='capture_failure'&&rows.length>3)throw Error('capture_failed');
   const from=args.cursors,next={rows:rows.length,resources:0,cadence:0,visibility:0};
   return{from,next,records:{rows:structuredClone(rows.slice(from.rows)),resources:[],cadence:[],visibility:[]},lengths:next,bodies:[],state:{started:0,timeOrigin:1000,wallStarted:1000,stopped:false},page:{path:'/incidents/'+scope.incidentId+'/board',visibility:'visible',measurement:false}};
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
 const changed=await trial('db_changed');assert.equal(changed.r.completed,true);
 assert.deepEqual([changed.r.comparison.responseMatchesBefore,changed.r.comparison.afterMatchesBefore],[true,false]);
 console.log('PASS a DB change during the run is recorded, not hidden');
 for(const mode of ['fcm','capture_failure','return_failure']){const t=await trial(mode);assert.equal(t.r.completed,false,mode);}
 await assert.rejects(run({...config,maxResponses:801},path.join(root,'badconfig'),{}));
 console.log('PASS FCM guard, capture failure, return failure and config guard leave the run incomplete');
})().catch(e=>{console.error(e);process.exitCode=1;});
