const { spawn } = require('node:child_process');
const { parseResult } = require('./smoke-run.cjs');

function createAsideEvaluator({ target, timeoutMs, onCall = () => {} }) {
  if (!/^[A-F0-9]{32}$/.test(target) || !Number.isSafeInteger(timeoutMs) || timeoutMs <= 0 || timeoutMs > 120000) {
    throw Error('invalid_aside_evaluator');
  }
  return async function evaluate(fn, args) {
    // The REPL reads one command per line; encode function newlines without changing comments.
    const source = JSON.stringify('(' + fn.toString() + ')');
    const code = `await(async()=>{const p=await attachBrowserTab(${JSON.stringify(target)});` +
      `const fn=(0,eval)(${source});console.log('\\nRESULT:'+JSON.stringify((await p.evaluate(fn,${JSON.stringify(args)}))??null))})()\n`;
    const started = Date.now();
    try {
      const stdout = await new Promise((resolve, reject) => {
        // stdin avoids putting a temporary APP token in the OS command-line arguments.
        const child = spawn('aside.exe', ['repl'], { stdio: ['pipe', 'pipe', 'pipe'] });
        const chunks = []; let bytes = 0, failed = false;
        const fail = () => { failed = true; child.kill(); };
        const timer = setTimeout(fail, timeoutMs);
        child.on('error', () => { clearTimeout(timer); reject(Error('aside_transport_failed')); });
        child.stdin.on('error', fail);
        child.stdout.on('data', chunk => {
          bytes += chunk.length;
          if (bytes > 12000000) fail(); else chunks.push(chunk);
        });
        child.stderr.on('data', () => {});
        child.on('close', exitCode => {
          clearTimeout(timer);
          if (failed || exitCode !== 0) reject(Error('aside_transport_failed'));
          else resolve(Buffer.concat(chunks).toString('utf8'));
        });
        child.stdin.end(code);
      });
      return parseResult(stdout);
    } catch (_) { throw Error('aside_evaluation_failed'); }
    finally { onCall({ startedAtMs: started, completedAtMs: Date.now() }); }
  };
}

module.exports = { createAsideEvaluator };
