const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { EventEmitter } = require('node:events');
const { PassThrough } = require('node:stream');
const source = fs.readFileSync(path.join(__dirname, 'aside-evaluator.cjs'), 'utf8');
const target = '7EC67B1ADE0732A5B21357B5872144A9';

function fake(mode) {
  let argv, stdin = '', killed = false;
  const module = { exports: {} };
  const spawn = (command, args) => {
    argv = [command, ...args];
    const child = new EventEmitter();
    child.stdout = new PassThrough(); child.stderr = new PassThrough(); child.stdin = new PassThrough();
    child.stdin.on('data', chunk => { stdin += chunk.toString(); });
    child.kill = () => { killed = true; queueMicrotask(() => child.emit('close', null)); };
    child.stdin.on('finish', () => {
      if (mode === 'timeout') return;
      queueMicrotask(() => {
        if (mode === 'spawn_error') { child.emit('error', Error('secret must not escape')); return; }
        if (mode === 'overflow') child.stdout.write(Buffer.alloc(12000001));
        else if (mode === 'invalid') child.stdout.write('[error | secret must not escape]');
        else child.stdout.write('prompt\nRESULT:{"ok":true}\n');
        child.emit('close', 0);
      });
    });
    return child;
  };
  vm.runInNewContext(source, { module, Buffer, setTimeout, clearTimeout,
    require: name => name === 'node:child_process' ? { spawn } : require(name) });
  return { evaluate: module.exports.createAsideEvaluator({ target, timeoutMs: 10 }),
    get argv() { return argv; }, get stdin() { return stdin; }, get killed() { return killed; } };
}

(async () => {
  const ok = fake('ok');
  assert.equal((await ok.evaluate(value => value, { token: 'synthetic-private-value' })).ok, true);
  assert.deepEqual(ok.argv, ['aside.exe', 'repl']);
  assert.ok(ok.stdin.includes('synthetic-private-value'));
  const multiline = fake('ok');
  await multiline.evaluate(function echo(value) {
    // Preserve newlines inside functions and line comments.
    return value;
  }, { marker: 'synthetic' });
  assert.equal(multiline.stdin.trimEnd().split('\n').length, 1, 'Aside stdin executes one command per line');
  let resultLine;
  await vm.runInNewContext('(async()=>{' + multiline.stdin + '})()', {
    attachBrowserTab: async id => {
      assert.equal(id, target);
      return { evaluate: async (fn, args) => fn(args) };
    }, console: { log: line => { resultLine = line; } },
  });
  assert.deepEqual(JSON.parse(resultLine.trim().slice(7)), { marker: 'synthetic' });
  for (const mode of ['timeout', 'overflow', 'spawn_error', 'invalid']) {
    const trial = fake(mode);
    await assert.rejects(trial.evaluate(() => null, {}), error => error.message === 'aside_evaluation_failed');
    if (mode === 'timeout' || mode === 'overflow') assert.equal(trial.killed, true);
  }
  console.log('PASS stdin-only arguments, bounded timeout/output, sanitized spawn and REPL failures');
})().catch(error => { console.error(error); process.exitCode = 1; });
