const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const vm = require('node:vm');
const { scope, createGpsPlan, verifyStoredGps } = require('./gps-plan.cjs');
const { sendGpsBatch } = require('./page-gps-send.cjs');
const { installPathObserver } = require('./page-path-observer.cjs');
const { createHistoryCapture } = require('./history-capture.cjs');
const { restoreCheckpoints } = require('./checkpoint.cjs');
const before = JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures/writer-path-before.json')));
const key = '__gpsSenderCheck';

function setup() {
  const plan = createGpsPlan(before, 'mock');
  const token = 'test.' + Buffer.from(JSON.stringify({ accountId: scope.accountId, accountType: 'TEAM',
    organizationType: 'SUPPORT_UNIT', exp: Math.floor(Date.now() / 1000) + 600 })).toString('base64url') + '.not-a-signature';
  const credential = { ...scope, accessToken: token };
  const timers = new Map(); let timer = 0, request;
  class MockRequest {
    constructor() { request = this; this.headers = {}; this.status = 200; }
    open(method, url) { this.method = method; this.url = url; }
    setRequestHeader(name, value) { this.headers[name] = value; }
    send(body) { this.body = JSON.parse(body); }
  }
  class BrowserResponse extends Response {}
  class ResourceObserver { observe() {} disconnect() {} takeRecords() { return []; } }
  const document = new EventTarget(); document.visibilityState = 'visible';
  const context = vm.createContext({ window: {}, document, location: { origin: 'https://suri-map.sonic8-8.com', pathname: '/incidents' },
    performance: { now: () => context.clock, timeOrigin: 1000 }, clock: 0, Date, URL, Response: BrowserResponse,
    PerformanceObserver: ResourceObserver, XMLHttpRequest: MockRequest, atob,
    setInterval: fn => { timers.set(++timer, fn); return timer; }, clearInterval: id => timers.delete(id),
    setTimeout: fn => { timers.set(++timer, fn); return timer; }, clearTimeout: id => timers.delete(id),
    config: { key, incidentId: scope.incidentId, deadlineMs: 10000, maxResponses: 100, maxBodyCharacters: 1000000, sampleIntervalMs: 250 },
  });
  const controller = vm.runInContext(`(${installPathObserver.toString()})(config,()=>{})`, context);
  context.window[key + 'Control'] = controller;
  context.location.pathname = `/incidents/${scope.incidentId}/board`;
  const feature = { type: 'Feature', source: 'operational-movement-path',
    properties: { entityId: plan.target.segmentId, searchPathId: scope.pathId, searchPathVersion: plan.target.version },
    geometry: { type: 'LineString', coordinates: plan.target.coordinates } };
  controller && context.window.__SURI_MAP_OBSERVER__.connect({ on() {}, off() {}, getLayer: () => true,
    isSourceLoaded: () => true, getCanvas: () => ({ clientWidth: 800, clientHeight: 600 }),
    project: () => ({ x: 10, y: 10 }), queryRenderedFeatures: () => [feature],
    getSource: () => ({ getData: async () => ({ type: 'FeatureCollection', features: [feature] }) }) });
  async function evaluate(fn, args) {
    context.args = args;
    const value = await vm.runInContext(`(${fn.toString()})(args)`, context);
    return value === undefined ? undefined : JSON.parse(JSON.stringify(value));
  }
  return { plan, credential, controller, context, evaluate, get request() { return request; },
    get rows() { return JSON.parse(JSON.stringify(context.window[key].rows)); },
    send: () => evaluate(sendGpsBatch, { key, plan, credential, timeoutMs: 1000 }),
    complete(overrides = {}) {
      request.responseText = JSON.stringify({ id: scope.pathId, opId: scope.opId, accountId: scope.accountId,
        version: Number(plan.target.version), status: 'RECORDING', acceptedPointCount: 6, excludedPointCount: 0,
        segments: [{ id: plan.target.segmentId, startIndex: before.pointCount, endIndex: before.pointCount + 5,
          startPointId: plan.body.points[0].pointId, endPointId: plan.body.points[5].pointId }], ...overrides });
      request.onload();
    },
  };
}

async function main() {
  // given: 실제 DB에 있는 바로 앞 구간과 같은 순번으로 ID를 계산한다.
  const previous = structuredClone(before); previous.pointCount -= 6;
  previous.recentPoints.forEach(point => point.pointOrder -= 6);
  assert.equal(createGpsPlan(previous, 'mock').target.segmentId, before.latestSegment.id);
  assert.throws(() => createGpsPlan(before, 'firebase'), /fcm_not_mock/);
  assert.throws(() => createGpsPlan({ ...before, pathId: 'other' }, 'mock'), /scope/);
  assert.throws(() => createGpsPlan({ ...before, status: 'ENDED' }, 'mock'), /not_active/);
  const unsafe = structuredClone(before); unsafe.recentPoints.at(-1).elapsedRealtimeNanos = '9007199254740991';
  assert.throws(() => createGpsPlan(unsafe, 'mock'), /unsafe_next_coordinates/);
  console.log('PASS fixed scope, active fixture, mock FCM and safe numeric range; Java-persisted segment UUID matches');

  const b = setup();
  b.context.clock = 100;
  const sent = b.send();
  assert.equal(b.request.method, 'POST'); assert.equal(b.request.url, '/api/search-paths/batch');
  assert.equal(b.request.headers['X-Client-Channel'], 'APP');
  assert.equal(b.request.headers['Idempotency-Key'], b.plan.requestId);
  assert.equal(b.rows.find(row => row.kind === 'gps_write_started').at, 100);
  assert.equal(Date.parse(b.request.body.points[1].clientTs) - Date.parse(b.request.body.points[0].clientTs), 2500);
  // when: 지도에 먼저 표시되고 HTTP 응답은 나중에 도착한다.
  b.context.clock = 140; await b.controller.sample();
  b.context.clock = 160; b.complete(); await sent;
  const after = { ...before, version: b.plan.target.version, pointCount: before.pointCount + 6,
    recentPoints: b.request.body.points.map((point, index) => ({ pointId: point.pointId, pointOrder: before.pointCount + index,
      lon: point.lon, lat: point.lat, elapsedRealtimeNanos: String(point.elapsedRealtimeNanos), clientTs: point.clientTs })),
    latestSegment: { id: b.plan.target.segmentId, version: '1', lastChangedPathVersion: b.plan.target.version,
      startPointOrder: before.pointCount, endPointOrder: before.pointCount + 5, movementType: 'FOOT' } };
  // then: SSH 준비를 포함하지 않고 같은 문서 시계에서 계산하며 DB 값까지 대조한다.
  assert.deepEqual(verifyStoredGps(b.plan, after, b.rows), { databaseCoordinatesMatch: true,
    sendToObservedMs: 40, responseToObservedMs: -20, sourceConfirmedAt: 140, displayTimeIsObservationUpperBound: true });
  assert.ok(!JSON.stringify(b.rows).includes(b.credential.accessToken));
  await assert.rejects(b.send(), /already_attempted/);
  const changed = structuredClone(after); changed.recentPoints[0].pointId = 'wrong';
  assert.throws(() => verifyStoredGps(b.plan, changed, b.rows), /points_mismatch/);
  assert.throws(() => verifyStoredGps(b.plan, { ...after, version: '999' }, b.rows), /version_or_count/);
  console.log('PASS same-clock send/map/response timing, pre-response target registration, exact DB points, no token retained, no repeat');

  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'suri-gps-clock-'));
  const file = path.join(directory, 'records.jsonl');
  const capture = createHistoryCapture({ file, key, incidentId: scope.incidentId, opId: scope.opId, evaluate: b.evaluate });
  try { await capture.capture(); assert.deepEqual(restoreCheckpoints(file).state.rows, JSON.parse(JSON.stringify(b.rows))); }
  finally { capture.close(); b.controller.stop('test_cleanup'); }
  console.log('PASS actual observer and chunk/checkpoint capture preserve immutable GPS timing rows');

  for (const failure of ['ontimeout', 'onerror', 'onabort', 'mismatch']) {
    const trial = setup();
    const promise = trial.send();
    if (failure === 'mismatch') trial.complete({ version: 999 }); else trial.request[failure]();
    await assert.rejects(promise);
    assert.equal(trial.rows.filter(row => row.kind === 'gps_write_started').length, 1);
    assert.equal(trial.rows.find(row => row.kind === 'gps_write_failed').commitOutcome, 'unconfirmed');
    await assert.rejects(trial.send(), /already_attempted/);
    trial.controller.stop('test_cleanup');
  }
  const expired = setup(); expired.credential.accessToken = 'x.' + Buffer.from(JSON.stringify({ exp: 0 })).toString('base64url') + '.x';
  await assert.rejects(expired.send(), /credential_invalid/); assert.equal(expired.request, undefined); expired.controller.stop('test_cleanup');
  console.log('PASS timeout/network/abort/mismatched response stay unconfirmed; expired credentials send nothing');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
