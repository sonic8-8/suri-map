const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const pipeline = fs.readFileSync(path.join(__dirname, 'hetzner.Jenkinsfile'), 'utf8');
const verify = pipeline.split("stage('Verify Backend')")[1].split("stage('Deploy Web')")[0];
assert.match(verify, /"\$\{SSH_USER\}@\$\{APP_HOST\}" sh -s <<'REMOTE'/);
const shell = verify.match(/<<'REMOTE'\n([\s\S]*?)\nREMOTE/)[1];
assert.match(verify, /timeout\(time: 3, unit: 'MINUTES'\)/);
assert.doesNotMatch(pipeline, /\binput\s*\(/);
assert.match(pipeline, /disableConcurrentBuilds\(\)/);
assert.match(pipeline, /disableRestartFromStage\(\)/);
assert.ok(pipeline.indexOf('docker tag') < pipeline.indexOf('docker build -t'));
assert.deepEqual([...pipeline.matchAll(/stage\('([^']+)'\)/g)].map(match => match[1]),
  ['Checkout', 'Backend Test CI', 'Frontend Test CI', 'Path Display Timing Checks CI', 'Sync', 'Build Images', 'Deploy Backend', 'Verify Backend', 'Deploy Web']);

const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'suri-backend-health-'));
const mock = path.join(directory, 'mock.cjs');
fs.writeFileSync(mock, `#!/usr/bin/env node
const fs = require('node:fs'), path = require('node:path');
const command = path.basename(process.argv[1]), mode = process.env.HEALTH_TEST_MODE;
const counterFile = path.join(process.env.HEALTH_TEST_STATE, command);
const count = Number(fs.existsSync(counterFile) ? fs.readFileSync(counterFile, 'utf8') : 0) + 1;
fs.writeFileSync(counterFile, String(count));
if (command === 'docker') {
  if (process.argv[2] === 'image') console.log('sha256:expected');
  else if (process.argv[2] === 'port') console.log(mode === 'public-port' ? '0.0.0.0:18091' : '127.0.0.1:18091');
  else if (mode === 'inspect-error') process.exit(1);
  else console.log((mode === 'replaced' ? 'container-' + count : 'container') + '|' +
    (mode === 'wrong-image' ? 'sha256:old' : 'sha256:expected') + '|' +
    (mode === 'restarting' ? 'restarting' : 'running') + '|0');
} else if (command === 'curl') {
  if (mode === 'http-failed') process.exit(22);
  if (mode === 'invalid-json') console.log('not JSON');
  else console.log(JSON.stringify({status: mode === 'down' || (mode === 'recovers' && count < 3) ? 'DOWN' : 'UP'}));
}
`, { mode: 0o700 });
for (const name of ['docker', 'curl', 'sleep']) fs.symlinkSync(mock, path.join(directory, name));

for (const mode of ['up', 'recovers', 'down', 'http-failed', 'invalid-json', 'wrong-image', 'restarting', 'replaced', 'public-port', 'inspect-error']) {
  const state = path.join(directory, mode);
  fs.mkdirSync(state);
  const result = spawnSync('sh', [], { input: shell, encoding: 'utf8', timeout: 30000,
    env: { ...process.env, PATH: directory + ':' + process.env.PATH, HEALTH_TEST_MODE: mode, HEALTH_TEST_STATE: state } });
  assert.ifError(result.error);
  assert.equal(result.status, ['up', 'recovers'].includes(mode) ? 0 : 1, mode + ': ' + result.stderr);
  if (mode === 'recovers') assert.match(result.stdout, /attempt=3/);
  if (mode === 'down') assert.equal(fs.readFileSync(path.join(state, 'curl'), 'utf8'), '60');
  console.log('PASS ' + mode);
}
console.log('PASS stage order, timeout, no manual input, concurrency and image preservation');
