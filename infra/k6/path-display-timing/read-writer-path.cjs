const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { validateWriterPath } = require('./gps-plan.cjs');
const { serverOf, sshArgs, psqlCommand } = require('./server.cjs');

function readWriterPath(server = serverOf()) {
  let raw;
  try {
    raw = execFileSync('ssh', sshArgs(server, psqlCommand(server)),
    { input: fs.readFileSync(path.join(__dirname, 'read-writer-path.sql')), timeout: 25000, maxBuffer: 1000000 });
  } catch (_) { throw Error('writer_database_read_failed'); }
  const result = JSON.parse(raw);
  validateWriterPath(result);
  return result;
}

module.exports = { readWriterPath };
if (require.main === module) {
  if (![3, 5].includes(process.argv.length)) throw Error('Usage: node read-writer-path.cjs NEW-output.json [SSH_HOST DB_CONTAINER]');
  const server = serverOf({ sshHost: process.argv[3], databaseContainer: process.argv[4] });
  const fd = fs.openSync(process.argv[2], 'wx', 0o600);
  try {
    const result = readWriterPath(server);
    fs.writeFileSync(fd, JSON.stringify(result, null, 2)); fs.fsyncSync(fd);
    console.log(JSON.stringify({ pathId: result.pathId, version: result.version, points: result.pointCount, readOnly: true }));
  } finally { fs.closeSync(fd); }
}
