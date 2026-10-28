const fs = require('node:fs');

// Runs in the page. Pending bodies are exported again until their timing is final.
function readDelta({ key, cursors }) {
  const exportStarted = performance.now();
  const state = window[key] || JSON.parse(sessionStorage.getItem(key) || 'null');
  if (!state?.rows) throw Error('Measurement state unavailable');
  const from = { ...cursors }, next = {}, records = {}, lengths = {};
  for (const name of ['rows', 'resources', 'cadence', 'visibility']) {
    const rows = state[name];
    const offset = cursors[name];
    if (!Array.isArray(rows) || !Number.isInteger(offset) || offset < 0 || offset > rows.length) {
      throw Error('Invalid checkpoint cursor: ' + name);
    }
    records[name] = rows.slice(offset).map(row => ({ ...row }));
    lengths[name] = rows.length;
    let completed = offset;
    if (name === 'rows') {
      while (completed < rows.length && rows[completed].returned) completed++;
    } else completed = rows.length;
    next[name] = completed;
  }
  return {
    from, next, records, lengths,
    state: {
      started: state.started, timeOrigin: state.timeOrigin, wallStarted: state.wallStarted,
      stopped: state.stopped, reason: state.reason, ended: state.ended, wallEnded: state.wallEnded,
    },
    page: { path: location.pathname, visibility: document.visibilityState,
      measurement: window.__SURI_MAP_MEASUREMENT_ENABLED__ === true },
    exportStarted, exportEnded: performance.now(),
  };
}

function createCheckpointWriter(file) {
  const fd = fs.openSync(file, 'wx');
  let cursors = { rows: 0, resources: 0, cadence: 0, visibility: 0 };
  let identity;
  let queue = Promise.resolve();
  function save(record) {
    fs.writeFileSync(fd, JSON.stringify(record) + '\n');
    fs.fsyncSync(fd);
  }
  return {
    capture(read) {
      // A deadline recovery and a regular poll must not export the same cursor concurrently.
      const task = queue.then(async () => {
        const started = new Date().toISOString();
        try {
          const delta = await read({ ...cursors });
          const currentIdentity = JSON.stringify([delta.state.timeOrigin, delta.state.started, delta.state.wallStarted]);
          if (identity && identity !== currentIdentity) throw Error('Measurement identity changed');
          if (JSON.stringify(delta.from) !== JSON.stringify(cursors)) throw Error('Stale checkpoint cursor');
          save({ type: 'checkpoint', started, ended: new Date().toISOString(), delta });
          // Never acknowledge browser rows until their local record has been flushed.
          cursors = { ...delta.next };
          identity = currentIdentity;
          return delta;
        } catch (error) {
          try { save({ type: 'error', started, ended: new Date().toISOString(), message: error.message }); }
          catch (_) { /* The original write/read error still fails the run. */ }
          throw error;
        }
      });
      queue = task.catch(() => {});
      return task;
    },
    close() { fs.closeSync(fd); },
  };
}

function restoreCheckpoints(file) {
  const lines = fs.readFileSync(file, 'utf8').split('\n');
  const tail = lines.pop();
  const restored = { checkpoints: 0, errors: [], incompleteTail: tail.length > 0, state: null };
  for (const line of lines) {
    const record = JSON.parse(line);
    if (record.type === 'error') { restored.errors.push(record); continue; }
    const delta = record.delta;
    if (!restored.state) restored.state = { rows: [], resources: [], cadence: [], visibility: [] };
    Object.assign(restored.state, delta.state);
    for (const name of ['rows', 'resources', 'cadence', 'visibility']) {
      delta.records[name].forEach((row, index) => { restored.state[name][delta.from[name] + index] = row; });
    }
    restored.checkpoints++;
    restored.lastExport = { started: record.started, ended: record.ended, page: delta.page };
  }
  return restored;
}

module.exports = { readDelta, createCheckpointWriter, restoreCheckpoints };
