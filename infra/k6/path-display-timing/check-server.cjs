// 실서버 없이, 설정한 서버 이름과 DB 컨테이너 이름이 ssh 호출에 쓰이는지 확인한다.
// PATH 앞에 가짜 ssh를 두어 인자를 기록하고 정해 둔 응답을 돌려준다.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { defaults, serverOf } = require('./server.cjs');
const { realIO, scope } = require('./run.cjs');

const root = fs.mkdtempSync(path.join(os.tmpdir(), 'suri-server-config-'));
const log = path.join(root, 'ssh.jsonl'), reply = path.join(root, 'reply');
fs.mkdirSync(path.join(root, 'bin'));
fs.writeFileSync(path.join(root, 'bin', 'ssh'), `#!${process.execPath}
const fs = require('node:fs');
fs.appendFileSync(${JSON.stringify(log)}, JSON.stringify(process.argv.slice(2)) + '\\n');
process.stdin.resume();
process.stdin.on('end', () => process.stdout.write(fs.readFileSync(${JSON.stringify(reply)})));
`, { mode: 0o755 });
process.env.PATH = path.join(root, 'bin') + path.delimiter + process.env.PATH;

const lastCall = () => JSON.parse(fs.readFileSync(log, 'utf8').trim().split('\n').at(-1));
const config = { ...scope, target: '7EC67B1ADE0732A5B21357B5872144A9' };
const inspect = [{ Image: 'backend', State: { StartedAt: 't' }, Config: { Env: ['FCM_PROVIDER=mock'] } },
  { Image: 'frontend', State: { StartedAt: 't' } }];
const snapshot = { incidentId: scope.incidentId, opId: scope.opId,
  paths: [{ id: 'path', accountId: 'account', opId: scope.opId, version: '1', status: 'RECORDING', segments: [] }] };

assert.deepEqual(serverOf({}), defaults);
assert.deepEqual(defaults, { sshHost: 'hetzner-app', databaseContainer: 'suri-map-postgis' });
for (const bad of ['-oProxyCommand=x', 'db;rm -rf /', 'a b', '', 1]) {
  assert.throws(() => serverOf({ sshHost: bad }), /invalid_server_config/);
  assert.throws(() => serverOf({ databaseContainer: bad }), /invalid_server_config/);
}

for (const [label, settings, expected] of [
  ['default', {}, defaults],
  ['configured', { sshHost: 'other-app', databaseContainer: 'other-postgis' }, { sshHost: 'other-app', databaseContainer: 'other-postgis' }],
]) {
  const io = realIO({ ...config, ...settings });
  const dbCommand = `docker exec -i ${expected.databaseContainer} sh -c 'psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'`;

  fs.writeFileSync(reply, JSON.stringify(inspect));
  assert.equal(io.runtime().fcm, 'mock');
  assert.deepEqual(lastCall().slice(4), [expected.sshHost, 'docker inspect suri-map-backend suri-map-frontend'], label);

  fs.writeFileSync(reply, JSON.stringify(snapshot));
  assert.equal(io.snapshot(path.join(root, label + '-snapshot.json')).paths.length, 1);
  assert.deepEqual(lastCall().slice(4), [expected.sshHost, dbCommand], label);
}
assert.throws(() => realIO({ ...config, databaseContainer: 'db;rm' }), /invalid_server_config/);
console.log('PASS ssh host and DB container default to hetzner-app/suri-map-postgis and follow config in runtime and DB snapshot reads');
