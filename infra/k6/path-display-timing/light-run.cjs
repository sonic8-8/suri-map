const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { isDeepStrictEqual } = require('node:util');
const { checkConfig, measurementSettings, boardEventsOf, save, realIO } = require('./run.cjs');
const { armLightTiming } = require('./page-light-timing.cjs');
const { readReadiness, readTarget } = require('./page-readiness.cjs');

// The same steps as run.cjs without path recording: no response bodies, map source, DB snapshot or coordinate comparison.
// The gap between the two modes' times is the cost of path recording; board measurement stays on in both.
async function runLight(config, directory, io = realIO(config)) {
  checkConfig(config);
  fs.mkdirSync(directory,{mode:0o700}); save(path.join(directory,'attempt.json'),config);
  const result = { started:new Date().toISOString(), completed:false, calls:0, ...boardEventsOf([]) };
  let armed=false, end;
  const evaluate = async (fn,args) => {
    if (end && io.now()>=end) throw Error('external_deadline');
    result.calls++; return io.evaluate(fn,args);
  };
  try {
    // The page does not check the map in this mode, so there is no sample interval to keep.
    const {sampleIntervalMs,...settings}=measurementSettings(config);
    save(path.join(directory,'measurement-setup.json'),{mode:'light',scriptVersion:io.scriptVersion(),settings});
    const environment=io.runtime(); save(path.join(directory,'runtime-before.json'),environment);
    assert.equal(environment.fcm,'mock'); assert.deepEqual(environment.limits,config.limits);
    const ready=await evaluate(readReadiness,config); assert.ok(ready.tokenSeconds*1000>config.deadlineMs+150000,'web_token_expiring');
    await evaluate(readTarget,config);
    const arm = new Function('return function(config){return ('+armLightTiming.toString()+')(config,('+readReadiness.toString()+'));}')();
    armed=true; end=io.now()+1080000;
    await evaluate(arm,config); await io.action('open');
    // The page stops itself on both board events, a hidden tab, an environment change or its own deadline.
    let exit;
    while ((exit=await evaluate(key=>JSON.parse(sessionStorage.getItem(key+'Exit')||'null'),config.key))?.reason==='armed') await io.pause();
    if (exit?.reason!=='complete') throw Error(exit?.reason ?? 'exit_missing');
    const after=io.runtime(); save(path.join(directory,'runtime-after.json'),after);
    // A thrown name, not assert.deepEqual: its message carries a diff and would be saved as run_failed.
    if (!isDeepStrictEqual(after,environment)) throw Error('runtime_changed');
    result.completed=true;
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
        const page=await io.evaluate(key=>({exit:JSON.parse(sessionStorage.getItem(key+'Exit')||'null'),
          state:JSON.parse(sessionStorage.getItem(key)||'null')}),config.key);
        result.exit=page.exit; save(path.join(directory,'exit.json'),result.exit);
        Object.assign(result,boardEventsOf(page.state?.rows??[])); result.visibility=page.state?.visibility??[];
        if (!result.returned.ready || result.exit?.reason!=='complete' || result.exit?.saved!==true) result.completed=false;
      } catch (_) { result.returnUnconfirmed=true; result.completed=false; }
    }
    result.ended=new Date().toISOString(); save(path.join(directory,'result.json'),result);
  }
  return result;
}

module.exports={runLight};
if(require.main===module){
  const config=JSON.parse(fs.readFileSync(process.argv[2]));
  runLight(config,process.argv[3]).then(r=>{console.log(JSON.stringify(r));if(!r.completed)process.exitCode=1;})
    .catch(()=>{console.error('run_preparation_failed');process.exitCode=1;});
}
