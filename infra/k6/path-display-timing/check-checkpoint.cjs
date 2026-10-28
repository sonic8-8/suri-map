const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const vm = require('node:vm');
const { readDelta, createCheckpointWriter, restoreCheckpoints } = require('./checkpoint.cjs');
const key = '__syntheticCheckpoint';
const empty = () => ({ rows: 0, resources: 0, cadence: 0, visibility: 0 });
const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'suri-checkpoint-'));
const state = { timeOrigin: 1, started: 2, wallStarted: 3, rows: [], resources: [], cadence: [], visibility: [], stopped: false };
let clock = 0;
const context = vm.createContext({ window: { [key]: state }, performance: { now: () => ++clock },
  sessionStorage: { getItem: () => JSON.stringify(state) }, location: { pathname: '/incidents' }, document: { visibilityState: 'visible' } });
function read(cursors) {
  context.args = { key, cursors };
  return JSON.parse(JSON.stringify(vm.runInContext('(' + readDelta.toString() + ')(args)', context)));
}
const file = path.join(directory, 'checkpoints.jsonl');
const writer = createCheckpointWriter(file);
const records = () => fs.readFileSync(file, 'utf8').trim().split('\n').map(JSON.parse);

(async () => {
  state.rows.push({ entered: 0, returned: false });
  const first = await writer.capture(read);
  assert.equal(first.next.rows, 0);
  assert.equal(first.records.rows[0].returned, false);
  assert.equal(records().length, 1); // On disk before another poll or final cleanup.
  state.rows[0].returned = true; state.rows[0].bodyReadAt = 10;
  assert.equal(first.records.rows[0].returned, false); // Export does not retain mutable references.
  state.resources.push({ start: 0, end: 10 });
  const second = await writer.capture(read);
  assert.equal(second.from.rows, 0);
  assert.equal(second.next.rows, 1);
  assert.equal(second.records.rows[0].bodyReadAt, 10);
  assert.deepEqual((await writer.capture(read)).records.rows, []);

  state.rows.push({ entered: 20, returned: false }, { entered: 21, returned: true, bodyReadAt: 22 });
  const pending = await writer.capture(read);
  assert.equal(pending.next.rows, 1); // Later completion cannot skip an earlier pending body.
  state.rows[1].returned = true; state.rows[1].bodyReadAt = 23;
  assert.equal((await writer.capture(read)).next.rows, 3);

  const beforeLoss = records().length;
  await assert.rejects(writer.capture(() => { throw Error('target gone'); }), /target gone/);
  assert.equal(records().length, beforeLoss + 1);
  assert.equal(records().at(-1).type, 'error');
  assert.equal(records().at(-2).delta.next.rows, 3); // Prior evidence survived loss of browser state.

  state.rows.push({ entered: 30, returned: true, bodyReadAt: 31 });
  const originalSync = fs.fsyncSync;
  fs.fsyncSync = () => { throw Error('synthetic disk failure'); };
  try { await assert.rejects(writer.capture(read), /disk failure/); }
  finally { fs.fsyncSync = originalSync; }
  const retried = await writer.capture(read);
  assert.equal(retried.from.rows, 3); // Failed durability acknowledgement never advances the cursor.
  assert.equal(retried.next.rows, 4);

  const queuedOffsets = [];
  await Promise.all([writer.capture(async cursors => {
    queuedOffsets.push(cursors.rows);
    await new Promise(resolve => setImmediate(resolve));
    state.rows.push({ entered: 40, returned: true, bodyReadAt: 41 });
    return read(cursors);
  }), writer.capture(cursors => { queuedOffsets.push(cursors.rows); return read(cursors); })]);
  assert.deepEqual(queuedOffsets, [4, 5]);

  state.rows.push({ entered: 50, returned: false }); state.stopped = true; state.reason = 'response_limit';
  delete context.window[key]; // Navigation fallback uses the final sessionStorage snapshot.
  const final = await writer.capture(read);
  assert.equal(final.state.stopped, true);
  assert.equal(final.records.rows[0].returned, false);
  const merged = [];
  for (const record of records().filter(record => record.type === 'checkpoint')) {
    record.delta.records.rows.forEach((row, index) => { merged[record.delta.from.rows + index] = row; });
  }
  assert.deepEqual(merged, state.rows); // Replay/upsert by source offset tolerates duplicate chunks.
  assert.deepEqual(restoreCheckpoints(file).state.rows, state.rows);
  state.timeOrigin++;
  await assert.rejects(writer.capture(read), /identity changed/);
  assert.throws(() => read({ ...empty(), rows: 100 }), /Invalid checkpoint cursor/);
  writer.close();
  assert.throws(() => createCheckpointWriter(file), /EEXIST/);
  const torn = path.join(directory, 'torn.jsonl');
  fs.writeFileSync(torn, fs.readFileSync(file, 'utf8') + '{"type":"checkpoint"', { flag: 'wx' });
  assert.equal(restoreCheckpoints(torn).incompleteTail, true);
  assert.deepEqual(restoreCheckpoints(torn).state.rows, state.rows);
  console.log(JSON.stringify({ pass: true, scope: 'synthetic checkpoint durability, pending mutation, loss, failure/retry, serialized capture, saved stop, identity and overwrite guards', directory }));
})().catch(error => { console.error(error); process.exitCode = 1; });
