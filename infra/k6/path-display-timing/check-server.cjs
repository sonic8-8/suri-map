// 실서버 없이, 설정한 서버 이름과 DB 컨테이너 이름이 ssh 호출에 쓰이는지 확인한다.
// ssh 대신 가짜 exec 함수를 넘겨 인자를 기록하고 정해 둔 응답을 돌려준다. ssh 프로세스는 실행하지 않는다.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { defaults, serverOf } = require('./server.cjs');
const { realIO, scope } = require('./run.cjs');

const root = fs.mkdtempSync(path.join(os.tmpdir(), 'suri-server-config-'));
const calls = [];
// 가짜 exec를 거치지 않은 호출이 진짜 ssh로 실서버에 닿지 않고 실패하도록 PATH를 빈 폴더로 둔다.
process.env.PATH = fs.mkdtempSync(path.join(root, 'no-bin-'));
let reply;
// execFileSync의 모양: 출력 파일이 stdio로 오면 그 파일에 쓰고, 아니면 출력을 돌려준다.
const exec = (file, args, options) => {
  calls.push({ command: [file, ...args], input: options.input });
  if (!options.stdio) return options.encoding ? reply : Buffer.from(reply);
  fs.writeSync(options.stdio[1], reply);
  return null;
};
const config = { ...scope, target: '7EC67B1ADE0732A5B21357B5872144A9' };
const inspect = [{ Image: 'backend', State: { StartedAt: 't' }, Config: { Env: ['FCM_PROVIDER=mock'] } },
  { Image: 'frontend', State: { StartedAt: 't' } }];
const snapshot = { incidentId: scope.incidentId, opId: scope.opId,
  paths: [{ id: 'path', accountId: 'account', opId: scope.opId, version: '1', status: 'RECORDING', segments: [] }] };
const sql = fs.readFileSync(path.join(__dirname, 'db-snapshot.sql'));

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
  const io = realIO({ ...config, ...settings }, exec);
  const ssh = ['ssh', '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=10', expected.sshHost];
  const dbCommand = `docker exec -i ${expected.databaseContainer} sh -c 'psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'`;

  reply = JSON.stringify(inspect);
  assert.equal(io.runtime().fcm, 'mock');
  assert.deepEqual(calls.at(-1).command, [...ssh, 'docker inspect suri-map-backend suri-map-frontend'], label);

  reply = JSON.stringify(snapshot);
  assert.equal(io.snapshot(path.join(root, label + '-snapshot.json')).paths.length, 1);
  assert.deepEqual(calls.at(-1).command, [...ssh, dbCommand], label);
  assert.deepEqual(calls.at(-1).input, sql, label);

  reply = '0'.repeat(40) + '\n';
  assert.equal(io.scriptVersion().commit, '0'.repeat(40), label);
  assert.equal(calls.at(-1).command[0], 'git', label);
}
assert.throws(() => realIO({ ...config, databaseContainer: 'db;rm' }), /invalid_server_config/);
console.log('PASS ssh host and DB container default to hetzner-app/suri-map-postgis and follow config in runtime and DB snapshot reads, and the version read uses the same exec');
