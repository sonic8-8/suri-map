const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const vm = require('node:vm');
const { createHistoryCapture, readManifest, readBodyChunk, confirmCapture } = require('./history-capture.cjs');
const { armHistoryCapture } = require('./page-history-capture.cjs');
const { installPathObserver } = require('./page-path-observer.cjs');
const { readReadiness } = require('./page-readiness.cjs');
const { restoreCheckpoints } = require('./checkpoint.cjs');

const incidentId = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001', opId = 'op', key = '__historyCaptureTest';
const config = { key, incidentId, opId, accountId: 'account', origin: 'https://suri-map.sonic8-8.com',
  script: '/assets/index-test.js', deadlineMs: 30000, maxResponses: 100, maxBodyCharacters: 50000000, sampleIntervalMs: 250 };
const points = [[127, 37], [127.1, 37.1]];
const segment = { id: 'segment', version: '1', startPointOrder: 0, endPointOrder: 1, movementType: 'FOOT', coordinates: points };
const snapshot = { incidentId, opId, paths: [{ id: 'path', accountId: 'account', opId, version: '1', status: 'RECORDING', segments: [segment] }] };
const pathRow = { ...snapshot.paths[0], baselineVersion: '1', segments: [{ ...segment, coordinates: undefined,
  geometry: { type: 'LineString', coordinates: points }, startedAt: '2026-10-15T00:00:00Z', endedAt: '2026-10-15T00:00:02Z' }],
  segmentsProgress: { beforeStartPointOrder: 0, completed: true },
  changesProgress: { beforeStartPointOrder: 0, completed: true, appliedVersion: '1', targetVersion: '1' } };
const source = { type: 'FeatureCollection', features: [{ type: 'Feature',
  geometry: { type: 'LineString', coordinates: points }, properties: { slot: 'path', entityId: 'segment',
    searchPathId: 'path', searchPathVersion: '1', opId, accountId: 'account', movementType: 'FOOT' } }] };

function browser() {
  let tick = 0, nextTimer = 0;
  const timers = new Map(), storage = new Map();
  storage.set('suriMapAccessToken', 'header.' + Buffer.from(JSON.stringify({ accountId: 'account', exp: Date.now() / 1000 + 3600 })).toString('base64url') + '.sig');
  const document = new EventTarget();
  document.visibilityState = 'visible';
  document.querySelector = selector => selector.includes('script') ? { getAttribute: () => config.script } : {};
  class BrowserResponse extends Response {}
  class ResourceObserver { observe() {} disconnect() {} takeRecords() { return []; } }
  const context = vm.createContext({ config, window: {}, document, performance: { now: () => ++tick, timeOrigin: 1000 },
    location: { origin: config.origin, pathname: '/incidents', replace(value) { this.pathname = value; } },
    sessionStorage: { getItem: key => storage.get(key) ?? null, setItem(key, value) {
      if (value.length > 1000) throw Error('storage_quota');
      storage.set(key, value);
    } }, atob, Date, URL, Response: BrowserResponse, PerformanceObserver: ResourceObserver,
    setInterval: fn => { timers.set(++nextTimer, fn); return nextTimer; }, clearInterval: id => timers.delete(id),
    setTimeout: fn => { timers.set(++nextTimer, fn); return nextTimer; }, clearTimeout: id => timers.delete(id),
  });
  vm.runInContext(`(${armHistoryCapture.toString()})(config,${installPathObserver.toString()},${readReadiness.toString()})`, context);
  context.location.pathname = `/incidents/${incidentId}/board`;
  let maxTransportBytes = 0, mapReadCount = 0;
  const values = { source: structuredClone(source), loaded: true };
  context.window.__SURI_MAP_OBSERVER__.connect({ on() {}, off() {}, isSourceLoaded: () => values.loaded,
    getSource: () => ({ getData: async () => { mapReadCount++; return structuredClone(values.source); } }),
    getLayer: () => true, queryRenderedFeatures: () => [{ source: 'operational-movement-path' }] });
  return { context, storage, timers, values,
    get state() { return context.window[key]; },
    get maxTransportBytes() { return maxTransportBytes; }, get mapReadCount() { return mapReadCount; },
    async evaluate(fn, args) {
      context.args = structuredClone(args);
      const result = await vm.runInContext(`(${fn.toString()})(args)`, context);
      if (result === undefined) return undefined;
      const serialized = JSON.stringify(result);
      maxTransportBytes = Math.max(maxTransportBytes, Buffer.byteLength(serialized));
      return JSON.parse(serialized);
    },
    async response(kind = 'segments', row = pathRow, hasMore = false) {
      const body = JSON.stringify({ paths: [row], hasMore, nextSearchPathId: null });
      const response = new BrowserResponse(body);
      Object.defineProperty(response, 'url', { value: `${config.origin}/api/incidents/${incidentId}/board/search-paths/${kind}/query` });
      assert.equal(await response.text(), body);
    },
  };
}

async function withCapture(check, overrideEvaluate) {
  const b = browser();
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'suri-history-capture-'));
  const file = path.join(directory, 'records.jsonl');
  const capture = createHistoryCapture({ file, key, incidentId, opId,
    evaluate: (fn, args) => overrideEvaluate ? overrideEvaluate(b, fn, args) : b.evaluate(fn, args) });
  try { await check(b, capture, file); }
  finally { b.context.window[key + 'Control'].stop('test_cleanup'); capture.close(); }
}

async function main() {
  // given: 실제 DB 스냅샷 크기의 본문과 UTF-16 경계를 가로지르는 문자열을 준비한다.
  await withCapture(async (b, capture, file) => {
    // 원본 점검이 읽던 실측 DB 스냅샷 파일(17,570,025바이트)은 옮기지 않고 같은 길이의 합성 본문을 쓴다.
    const large = '[126.919175,35.160235],'.repeat(763915).slice(0, 17570025);
    assert.ok(Buffer.byteLength(large) > 12000000);
    const escaped = '\u0000'.repeat(262143) + '😀한글';
    b.state.rows.push({ kind: 'map_source', returned: true, body: large }, { kind: 'map_source', returned: true, body: escaped });
    // when: 한 번의 전송 상한보다 큰 본문을 조각으로 회수한다.
    let delta;
    do { delta = await capture.capture(); } while (delta.next.rows < delta.lengths.rows);
    const restored = restoreCheckpoints(file);
    // then: 원문을 그대로 복구하며 브라우저 저장소에는 전체 본문을 넣지 않는다.
    assert.equal(restored.state.rows[1].body, large);
    assert.equal(restored.state.rows[2].body, escaped);
    const identity = JSON.stringify([b.state.timeOrigin, b.state.started, b.state.wallStarted]);
    const boundary = await b.evaluate(readBodyChunk, { key, identity, name: 'rows', index: 2, length: escaped.length, offset: 0 });
    assert.equal(boundary.text.length, 262143);
    assert.equal(boundary.text.at(-1), '\u0000');
    assert.ok(b.maxTransportBytes < 2000000);
    assert.equal(b.storage.has(key), false);
    console.log('PASS 17.6MB body and escaped/split-surrogate text restore exactly; each transport <2MB');
  });

  let bodyCalls = 0;
  await withCapture(async (b, capture, file) => {
    for (let i = 0; i < 100; i++) b.state.rows.push({ kind: 'map_source', returned: true, body: 'x'.repeat(10000) });
    let delta, captures = 0;
    do { delta = await capture.capture(); captures++; } while (delta.next.rows < delta.lengths.rows);
    assert.equal(restoreCheckpoints(file).state.rows.length, 101);
    assert.equal(bodyCalls, 0); assert.equal(captures, 4);
    assert.ok(b.maxTransportBytes < 300000);
  }, async (b, fn, args) => {
    if (fn === readBodyChunk) bodyCalls++;
    return b.evaluate(fn, args);
  });
  console.log('PASS 100 small bodies use 4 bounded manifests and zero separate body calls');

  await withCapture(async (b, capture, file) => {
    for (let i = 0; i < 260; i++) b.state.cadence.push({ at: i, duration: 1 });
    const pending = { kind: 'segments', returned: false }; b.state.rows.push(pending);
    const first = await capture.capture();
    assert.equal(first.next.rows, 1); assert.equal(first.next.cadence, 128);
    Object.assign(pending, { returned: true, status: 200, body: JSON.stringify({ paths: [pathRow], hasMore: false, nextSearchPathId: null }) });
    await capture.capture(); await capture.capture();
    assert.deepEqual(restoreCheckpoints(file).state.rows, structuredClone(b.state.rows));
    assert.equal(restoreCheckpoints(file).state.cadence.length, 260);
    console.log('PASS pending body cursor held; bounded metadata batches restore without loss');
  });

  await withCapture(async (b, capture, file) => {
    await b.response(); await b.response('changes', { ...pathRow, segments: [] });
    await capture.capture();
    const result = await capture.finish();
    assert.deepEqual(result.mapSource, source);
    assert.deepEqual(result.responses.map(row => row.kind), ['segments', 'changes']);
    assert.equal(result.stopRequested, true);
    assert.equal(result.returnToListVerified, false); assert.equal(result.performanceComparison, false);
    assert.equal(b.context.location.pathname, '/incidents'); assert.equal(b.timers.size, 0);
    assert.equal(JSON.parse(b.storage.get(key + 'Exit')).finalCheckpointSaved, true);
    assert.equal(restoreCheckpoints(file).state.rows.at(-1).kind, 'map_final_source');
    assert.equal(b.mapReadCount, 1);
    console.log('PASS actual observer → capture → fsync → server responses and path source returned → stop/navigation wiring');
  });

  await withCapture(async (b, capture) => {
    await b.response('segments', pathRow, true); await capture.capture();
    await assert.rejects(capture.finish(), /pages_incomplete/);
    assert.equal(b.mapReadCount, 0); assert.equal(b.state.reason, 'capture_failed');
    assert.equal(JSON.parse(b.storage.get(key + 'Exit')).finalCheckpointSaved, false);
  });
  console.log('PASS incomplete history cannot request successful exit');

  await withCapture(async (b, capture) => {
    await b.response('segments', pathRow, true); await capture.capture();
    const continuation = { ...pathRow }; delete continuation.baselineVersion;
    await b.response('segments', continuation); await capture.capture();
    await b.response('changes', { ...pathRow, segments: [] }); await capture.capture();
    const result = await capture.finish();
    assert.equal(result.responses.length, 3);
    assert.equal(b.state.reason, 'complete');
  });
  console.log('PASS continuation without repeated baseline works across checkpoint boundaries');

  await withCapture(async (b, capture) => {
    await b.response(); await b.response('changes', { ...pathRow, segments: [] }); await capture.capture();
    b.values.loaded = false;
    await assert.rejects(capture.finish(), /not ready/);
    assert.equal(b.state.reason, 'capture_failed');
  });
  await withCapture(async (b, capture) => {
    await b.response(); await b.response('changes', { ...pathRow, segments: [] }); await capture.capture();
    b.state.rows.push({ kind: 'segments', returned: false });
    await assert.rejects(capture.finish(), /response_in_flight/);
    assert.equal(b.state.reason, 'capture_failed');
  });
  console.log('PASS unloaded source and in-flight response cannot finalize');

  let changed = false;
  await withCapture(async (b, capture, file) => {
    await b.response(); await capture.capture();
    b.state.rows.push({ kind: 'map_source', returned: true, body: 'x'.repeat(300000) });
    changed = true;
    await assert.rejects(capture.capture(), /capture_identity_changed/);
    const restored = restoreCheckpoints(file);
    assert.equal(restored.checkpoints, 1); assert.equal(restored.errors.length, 1);
    assert.equal(restored.state.rows.length, 2);
  }, async (b, fn, args) => {
    if (changed && fn === readBodyChunk && args.offset > 0) b.state.started++;
    return b.evaluate(fn, args);
  });
  console.log('PASS mid-transfer identity loss preserves prior fsynced checkpoint and error');

  await withCapture(async (b, capture) => {
    await b.response(); await b.response('changes', { ...pathRow, segments: [] }); await capture.capture();
    await assert.rejects(capture.finish(), /capture_changed_before_stop/);
    assert.equal(b.state.reason, 'capture_failed');
  }, async (b, fn, args) => {
    if (fn === confirmCapture && args.finish) await b.response('changes', { ...pathRow, segments: [] });
    return b.evaluate(fn, args);
  });
  console.log('PASS response arriving after final disk comparison invalidates successful exit');

  await withCapture(async (b, capture, file) => {
    await b.response('segments', { ...pathRow, opId: 'wrong' });
    await assert.rejects(capture.capture());
    assert.equal(restoreCheckpoints(file).checkpoints, 1);
    assert.equal(b.state.reason, 'capture_failed');
  });
  const b = browser();
  b.context.window[key + 'Control'].stop('deadline');
  assert.equal(b.context.location.pathname, '/incidents');
  assert.equal(JSON.parse(b.storage.get(key + 'Exit')).finalCheckpointSaved, false);
  assert.equal(b.storage.has(key), false);
  console.log('PASS wrong scope preserves evidence then stops; deadline exits without huge storage copy');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
