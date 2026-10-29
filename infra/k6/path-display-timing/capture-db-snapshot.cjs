const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { createHash } = require('node:crypto');
const { validateSnapshot } = require('./history-compare.cjs');
const { serverOf, sshArgs, psqlCommand } = require('./server.cjs');

// exec is execFileSync's shape; the checks pass a fake one so no real ssh runs.
function captureDbSnapshot(output, server, exec = execFileSync) {
  const fd = fs.openSync(output, 'wx', 0o600);
  const started = new Date().toISOString();
  try {
    // Reuse the prior DB tool's SSH/psql boundary, but stream the full snapshot instead of its 16 MiB stdout buffer.
    exec('ssh', sshArgs(server, psqlCommand(server)),
      { input: fs.readFileSync(path.join(__dirname, 'db-snapshot.sql')), stdio: ['pipe', fd, 'pipe'], timeout: 45000 });
    fs.fsyncSync(fd);
  } finally { fs.closeSync(fd); }
  const raw = fs.readFileSync(output), snapshot = JSON.parse(raw);
  validateSnapshot(snapshot);
  return { started, ended: new Date().toISOString(), bytes: raw.length,
    sha256: createHash('sha256').update(raw).digest('hex'), paths: snapshot.paths.length,
    segments: snapshot.paths.reduce((sum, path) => sum + path.segments.length, 0) };
}

module.exports = { captureDbSnapshot };
if (require.main === module) {
  if (![3, 5].includes(process.argv.length)) throw Error('Usage: node capture-db-snapshot.cjs NEW-output.json [SSH_HOST DB_CONTAINER]');
  const server = serverOf({ sshHost: process.argv[3], databaseContainer: process.argv[4] });
  try { console.log(JSON.stringify(captureDbSnapshot(process.argv[2], server))); } catch (_) {
    console.error('Snapshot failed. Partial output preserved; no automatic retry.');
    process.exitCode = 1;
  }
}
