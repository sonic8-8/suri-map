const { createCheckpointWriter, restoreCheckpoints } = require('./checkpoint.cjs');
const { readCapturedPages, readFinalCapture, pagesComplete } = require('./history-compare.cjs');
const { parseSearchPathPage } = require('../../../frontend/src/features/path/model/searchPathPages.ts');

// Browser-side: only finalized rows are immutable enough to transfer across several calls.
function readManifest({ key, cursors }) {
  const began = performance.now();
  const state = window[key];
  if (!state?.rows || state.stopped) throw Error('active_capture_missing');
  const records = {}, lengths = {}, next = {}, bodies = [];
  for (const name of ['rows', 'resources', 'cadence', 'visibility']) {
    const items = state[name], from = cursors[name];
    if (!Array.isArray(items) || !Number.isSafeInteger(from) || from < 0 || from > items.length) {
      throw Error('invalid_capture_cursor');
    }
    const limit = Math.min(items.length, from + 128);
    let end = from, inlineCharacters = 0;
    records[name] = [];
    while (end < limit) {
      const row = items[end];
      if (name === 'rows' && row.returned !== true) break;
      const { body, ...metadata } = row;
      if (typeof body === 'string') {
        if (inlineCharacters + body.length <= 262144) {
          metadata.body = body;
          inlineCharacters += body.length;
        } else {
          if (records[name].length) break;
          bodies.push({ name, index: end, length: body.length });
          records[name].push(metadata); end++;
          break;
        }
      }
      records[name].push(metadata); end++;
    }
    next[name] = end;
    lengths[name] = items.length;
  }
  return { from: { ...cursors }, next, records, lengths, bodies,
    state: { started: state.started, timeOrigin: state.timeOrigin, wallStarted: state.wallStarted, stopped: false },
    page: { path: location.pathname, visibility: document.visibilityState,
      measurement: window.__SURI_MAP_MEASUREMENT_ENABLED__ === true },
    exportStarted: began, exportEnded: performance.now() };
}

function readBodyChunk({ key, identity, name, index, length, offset }) {
  const state = window[key];
  if (!state || state.stopped || JSON.stringify([state.timeOrigin, state.started, state.wallStarted]) !== identity) {
    throw Error('capture_identity_changed');
  }
  if (name !== 'rows' || !Number.isSafeInteger(index) || index < 0 ||
      !Number.isSafeInteger(length) || length < 0 || !Number.isSafeInteger(offset) || offset < 0 || offset > length) {
    throw Error('invalid_body_cursor');
  }
  const row = state.rows[index];
  if (row?.returned !== true || typeof row.body !== 'string' || row.body.length !== length) throw Error('capture_body_changed');
  // 256K UTF-16 units remain under the 12MB transport cap even with JSON escaping.
  let end = Math.min(row.body.length, offset + 262144);
  const last = row.body.charCodeAt(end - 1), next = row.body.charCodeAt(end);
  if (last >= 0xd800 && last <= 0xdbff && next >= 0xdc00 && next <= 0xdfff) end--;
  return { offset, text: row.body.slice(offset, end) };
}

function confirmCapture({ key, identity, lengths, finish }) {
  const state = window[key];
  if (!state || state.stopped || JSON.stringify([state.timeOrigin, state.started, state.wallStarted]) !== identity) {
    throw Error('capture_identity_changed');
  }
  if (finish) {
    if (state.rows.length !== lengths.rows || state.rows.some(row => row.returned !== true) ||
        state.rows.filter(row => row.kind === 'map_final_source').length !== 1) throw Error('capture_changed_before_stop');
    window[key + 'Control'].stop('complete');
  }
  return { activeWhenChecked: true, stopRequested: finish === true };
}

// evaluate(fn, argument) is supplied by the Aside caller; every call needs its own timeout.
function createHistoryCapture({ file, key, incidentId, opId, evaluate }) {
  const writer = createCheckpointWriter(file);
  const identityOf = state => JSON.stringify([state.timeOrigin, state.started, state.wallStarted]);
  async function abort() {
    try { await evaluate(key => { window[key + 'Control']?.stop('capture_failed'); }, key); }
    catch (_) { /* The independent browser deadline remains the last safety limit. */ }
  }
  async function capture() {
    try {
    const captured = await writer.capture(async cursors => {
      const delta = await evaluate(readManifest, { key, cursors });
      if (delta.page.path !== `/incidents/${incidentId}/board` || delta.page.visibility !== 'visible' || !delta.page.measurement) {
        throw Error('capture_environment_changed');
      }
      const identity = identityOf(delta.state);
      for (const body of delta.bodies) {
        let text = '';
        while (text.length < body.length) {
          const chunk = await evaluate(readBodyChunk, { key, identity, ...body, offset: text.length });
          if (chunk.offset !== text.length || typeof chunk.text !== 'string' || !chunk.text.length ||
              chunk.text.length > 262144 || text.length + chunk.text.length > body.length) throw Error('invalid_body_chunk');
          text += chunk.text;
        }
        delta.records[body.name][body.index - cursors[body.name]].body = text;
      }
      await evaluate(confirmCapture, { key, identity, finish: false });
      delete delta.bodies;
      return delta;
    });
    // Validate on Node, after fsync, so malformed/wrong-scope evidence survives a failed trial.
    for (const row of captured.records.rows) {
      if (!['segments', 'changes'].includes(row.kind)) continue;
      if (row.returned !== true || row.status !== 200 || typeof row.body !== 'string' || row.bodyOmitted) {
        throw Error('response_incomplete');
      }
      parseSearchPathPage(JSON.parse(row.body), row.kind, [opId]);
    }
    return captured;
    } catch (error) { await abort(); throw error; }
  }

  async function finish() {
    const before = restoreCheckpoints(file);
    if (before.errors.length || before.incompleteTail || !before.state) throw Error('checkpoint_incomplete');
    const pages = readCapturedPages(before.state.rows, opId);
    if (!pagesComplete(pages)) {
      throw Error('pages_incomplete');
    }
    // This is one capture attempt, not a quiet-period guess or an automatic retry.
    await evaluate(async key => {
      const state = window[key];
      if (!state || state.stopped || state.rows.some(row => !row.returned)) throw Error('response_in_flight');
      return window[key + 'Control'].captureSource();
    }, key);
    let delta;
    do {
      delta = await capture();
      // No waiting here: pending bodies fail finalization instead of spinning.
      if (delta.next.rows < delta.lengths.rows && delta.next.rows === delta.from.rows) throw Error('response_in_flight');
    } while (Object.keys(delta.next).some(name => delta.next[name] < delta.lengths[name]));
    const recovered = restoreCheckpoints(file);
    if (recovered.errors.length || recovered.incompleteTail) throw Error('checkpoint_incomplete');
    const captured = readFinalCapture(recovered.state.rows);
    const stopped = await evaluate(confirmCapture, {
      key, identity: identityOf(recovered.state), lengths: delta.next, finish: true,
    });
    // Navigation acknowledgement is not proof that the incident list rendered.
    return { ...captured, ...stopped, returnToListVerified: false, performanceComparison: false };
  }
  return { capture, finish: async () => {
    try { return await finish(); }
    catch (error) { await abort(); throw error; }
  }, close: () => writer.close() };
}

module.exports = { readManifest, readBodyChunk, confirmCapture, createHistoryCapture };
