const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const vm = require('node:vm');
const { installPathObserver } = require('./page-path-observer.cjs');
const { readDelta, createCheckpointWriter, restoreCheckpoints } = require('./checkpoint.cjs');

const incidentId = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001';
const origin = 'https://suri-map.sonic8-8.com';
const key = '__pathObserverTest';
const url = `${origin}/api/incidents/${incidentId}/board/search-paths/segments/query`;
const rawBody = '{"paths":[{"id":"path-1","segments":[{"id":"segment-1","geometry":{"type":"LineString","coordinates":[[127,37],[127.1,37.1]]}}]}],"hasMore":false}';

function createBrowser(overrides = {}) {
  let clock = 0;
  const timers = new Map();
  let timerId = 0;
  const stops = [];
  class BrowserResponse extends Response {}
  const resourceObservers = [];
  class ResourceObserver {
    constructor(callback) { this.callback = callback; resourceObservers.push(this); }
    observe() { this.active = true; }
    disconnect() { this.active = false; }
    takeRecords() { return []; }
  }
  const window = {};
  const document = new EventTarget();
  document.visibilityState = 'visible';
  const config = { key, incidentId, maxResponses: 20, maxBodyCharacters: 10000, deadlineMs: 1000, sampleIntervalMs: 20, ...overrides };
  const context = vm.createContext({
    window, document, location: { origin, pathname: '/incidents' },
    performance: { now: () => ++clock, timeOrigin: 1000 }, Date, URL,
    Response: BrowserResponse, PerformanceObserver: ResourceObserver, sessionStorage: { getItem: () => null },
    setInterval: callback => { timers.set(++timerId, callback); return timerId; },
    clearInterval: id => timers.delete(id),
    setTimeout: callback => { timers.set(++timerId, callback); return timerId; },
    clearTimeout: id => timers.delete(id),
    config, onStop: reason => stops.push(reason),
  });
  const originalText = BrowserResponse.prototype.text;
  const observer = vm.runInContext(`(${installPathObserver.toString()})(config, onStop)`, context);
  return {
    context, window, document, config, observer, stops, timers, originalText, BrowserResponse, resourceObservers,
    response(body = rawBody, responseUrl = url, status = 200) {
      const response = new BrowserResponse(body, { status });
      Object.defineProperty(response, 'url', { value: responseUrl });
      return response;
    },
    read(cursors) {
      context.args = { key, cursors };
      return JSON.parse(JSON.stringify(vm.runInContext(`(${readDelta.toString()})(args)`, context)));
    },
  };
}

async function checkResponseCapture() {
  // given: 기존 체크포인트 저장기와 실제 Response 본문 읽기를 연결한다.
  const browser = createBrowser();
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'suri-path-observer-'));
  const file = path.join(directory, 'records.jsonl');
  const writer = createCheckpointWriter(file);
  try {
    // when: 앱이 응답을 읽고 측정기는 중간 기록을 디스크로 회수한다.
    assert.equal(await browser.response().text(), rawBody);
    await writer.capture(browser.read);
    const recovered = restoreCheckpoints(file);

    // then: 응답의 구간과 좌표 원문이 변형 없이 복원된다.
    assert.equal(recovered.state.rows.length, 1);
    assert.equal(recovered.state.rows[0].body, rawBody);
    assert.equal(recovered.state.rows[0].kind, 'segments');
    console.log('PASS response unchanged and checkpoint body recovered');
  } finally {
    browser.observer.stop('manual');
    writer.close();
  }
}

const target = { searchPathId: 'path-1', segmentId: 'segment-1', version: '2', coordinates: [[127.1, 37], [127.2, 37]] };
function createMap() {
  const listeners = new Map();
  const feature = version => ({
    source: 'operational-movement-path', type: 'Feature',
    properties: { entityId: 'segment-1', searchPathId: 'path-1', searchPathVersion: version },
    geometry: { type: 'LineString', coordinates: [[127, 37], ...target.coordinates] },
  });
  const values = { rendered: [feature('1')], source: [feature('2')], outside: false, reads: 0 };
  const source = { getData: async () => { values.reads++; return structuredClone({ type: 'FeatureCollection', features: values.source }); } };
  const map = {
    on: (type, fn) => listeners.set(type, fn),
    off: (type, fn) => { if (listeners.get(type) === fn) listeners.delete(type); },
    getSource: () => source, isSourceLoaded: () => true, getLayer: () => ({}),
    getCanvas: () => ({ clientWidth: 800, clientHeight: 600 }),
    project: () => ({ x: values.outside ? 900 : 100, y: 100 }),
    queryRenderedFeatures: () => values.rendered,
  };
  return { map, values, listeners, feature };
}

async function checkMapObservation() {
  // given: 같은 구간 ID의 이전 버전이 화면에 있고 source에는 새 좌표가 준비돼 있다.
  const b = createBrowser();
  const m = createMap();
  try {
    b.observer.expectCoordinates(target);
    b.window.__SURI_MAP_OBSERVER__.connect(m.map);
    await b.observer.sample();
    const rows = b.window[key].rows;
    assert.equal(rows.filter(row => row.kind === 'map_first_observed').length, 1);
    assert.equal(rows.filter(row => row.kind === 'map_target_observed').length, 0);
    assert.equal(m.values.reads, 0);

    // when: 같은 구간의 새 버전이 화면에도 나타난다.
    m.values.rendered = [m.feature('2')];
    await b.observer.sample();
    await b.observer.sample();

    // then: 원본 좌표와 화면 위치를 확인한 새 버전만 한 번 기록한다.
    const recorded = rows.filter(row => row.kind === 'map_target_observed');
    assert.equal(recorded.length, 1);
    assert.deepEqual(JSON.parse(JSON.stringify(recorded[0].target)), target);
    assert.equal(m.values.reads, 1);
    assert.equal(rows.filter(row => row.kind === 'map_source').length, 1);
    console.log('PASS old render rejected; new coordinates/source/visible version observed once');
  } finally { b.observer.stop('manual'); }
}

async function checkBoundedStop() {
  // given: 본문 1건만 보존할 수 있는 관찰기에 지도와 응답을 연결한다.
  const b = createBrowser({ maxBodyCharacters: rawBody.length });
  const m = createMap();
  b.window.__SURI_MAP_OBSERVER__.connect(m.map);
  await b.response().text();

  // when: 다음 본문 때문에 상한을 넘겨도 앱에는 그 본문을 그대로 전달한다.
  assert.equal(await b.response().text(), rawBody);
  const state = b.window[key];
  assert.equal(state.stopped, true);
  assert.equal(state.reason, 'body_limit');
  assert.equal(state.rows.filter(row => row.body === rawBody).length, 1);
  assert.equal(state.rows.find(row => row.bodyOmitted)?.bodyOmitted, 'body_limit');
  assert.equal(b.BrowserResponse.prototype.text, b.originalText);
  assert.equal(m.listeners.size, 0);
  assert.equal(b.timers.size, 0);
  assert.equal(b.window.__SURI_MAP_OBSERVER__, undefined);
  assert.equal(state.completed, undefined);
  b.observer.stop('manual');
  assert.deepEqual(b.stops, ['body_limit']);

  // then: 중단 직전까지 보존한 자료는 기존 회수 인터페이스로 계속 읽힌다.
  const delta = b.read({ rows: 0, resources: 0, cadence: 0, visibility: 0 });
  assert.equal(delta.records.rows.filter(row => row.body === rawBody).length, 1);
  console.log('PASS bounded incomplete stop, original response and prior evidence preserved');
}

async function checkFinalSourceCapture() {
  // given: 새 GPS 감시와 별도로 최종 지도 원본을 한 번 회수한다.
  const b = createBrowser();
  const m = createMap();
  try {
    b.window.__SURI_MAP_OBSERVER__.connect(m.map);
    const first = await b.observer.captureSource();
    const second = await b.observer.captureSource();
    assert.equal(first.recordIndex, second.recordIndex);
    assert.equal(m.values.reads, 1);
    const row = b.window[key].rows[first.recordIndex];
    assert.equal(row.kind, 'map_final_source');
    assert.deepEqual(JSON.parse(row.body).features[0].geometry.coordinates, [[127, 37], [127.1, 37], [127.2, 37]]);
    assert.equal(b.window[key].completed, undefined);
    console.log('PASS explicit final source captured once without declaring completion');
  } finally { b.observer.stop('manual'); }
}

async function checkStopDuringBodyRead() {
  // given: 응답 본문이 아직 도착하지 않은 상태에서 관찰만 종료한다.
  const b = createBrowser();
  let controller;
  const stream = new ReadableStream({ start(value) { controller = value; } });
  const pending = b.response(stream).text();
  b.observer.stop('manual');
  const before = JSON.stringify(b.window[key]);
  assert.equal(b.window[key].rows[0].bodyOmitted, 'stopped_in_flight');

  // when/then: 늦은 응답은 앱에 반환하지만 종료 기록은 바꾸지 않는다.
  controller.enqueue(new TextEncoder().encode(rawBody));
  controller.close();
  assert.equal(await pending, rawBody);
  assert.equal(JSON.stringify(b.window[key]), before);
  console.log('PASS stopped in-flight body still delivered, final evidence stays stable');
}

async function checkInvalidObservations() {
  // given/when/then: source만 갱신됐거나 새 좌표가 화면 밖이면 표시 성공이 아니다.
  for (const mode of ['outside', 'old-source', 'changed-source']) {
    const b = createBrowser();
    const m = createMap();
    try {
      b.observer.expectCoordinates(target);
      b.window.__SURI_MAP_OBSERVER__.connect(m.map);
      m.values.rendered = [m.feature('2')];
      if (mode === 'outside') m.values.outside = true;
      if (mode === 'old-source') m.values.source[0].geometry.coordinates = [[127, 37], [127.01, 37]];
      if (mode === 'changed-source') {
        const source = m.map.getSource();
        const original = source.getData;
        source.getData = async () => {
          const data = await original();
          m.listeners.get('sourcedata')({ sourceId: 'operational-movement-path' });
          source.getData = original;
          return data;
        };
      }
      await b.observer.sample();
      assert.equal(b.window[key].rows.some(row => row.kind === 'map_target_observed'), false, mode);
      if (mode === 'outside') assert.equal(m.values.reads, 0);
      else if (mode === 'old-source') assert.equal(b.window[key].reason, 'target_not_confirmed');
      else {
        assert.equal(b.window[key].stopped, false);
        assert.equal(b.window[key].rows.filter(row => row.kind === 'map_sample_discarded').length, 1);
        assert.equal(await b.response().text(), rawBody);
        await b.observer.sample();
        await b.observer.sample();
        assert.equal(b.window[key].rows.filter(row => row.kind === 'map_target_observed').length, 1);
      }
    } finally { b.observer.stop('manual'); }
  }
  console.log('PASS offscreen/old geometry/concurrent source change never count as new GPS display');
}

async function checkExitAndFailures() {
  for (const mode of ['hidden', 'deadline', 'map-replaced', 'measurement-enabled', 'http-failed', 'response-limit', 'read-failed']) {
    const b = createBrowser({ maxResponses: 1 });
    const m = createMap();
    b.window.__SURI_MAP_OBSERVER__.connect(m.map);
    if (mode === 'hidden') { b.document.visibilityState = 'hidden'; b.document.dispatchEvent(new Event('visibilitychange')); }
    if (mode === 'deadline') [...b.timers.values()].at(-1)();
    if (mode === 'map-replaced') b.window.__SURI_MAP_OBSERVER__.connect(createMap().map);
    if (mode === 'measurement-enabled') { b.window.__SURI_MAP_MEASUREMENT_ENABLED__ = true; await b.observer.sample(); }
    if (mode === 'http-failed') assert.equal(await b.response('failure-body', url, 500).text(), 'failure-body');
    if (mode === 'response-limit') { await b.response().text(); assert.equal(await b.response().text(), rawBody); }
    if (mode === 'read-failed') {
      const error = new Error('synthetic stream failure');
      const stream = new ReadableStream({ start(controller) { controller.error(error); } });
      await assert.rejects(b.response(stream).text(), failure => failure === error);
    }
    assert.equal(b.window[key].stopped, true, mode);
    assert.equal(b.stops.length, 1, mode);
    assert.equal(b.BrowserResponse.prototype.text, b.originalText, mode);
    assert.equal(b.timers.size, 0, mode);
    assert.equal(m.listeners.size, 0, mode);
    assert.equal(b.resourceObservers[0].active, false, mode);
    assert.equal(b.window[key].rows.some(row => row.body === 'failure-body'), false);
  }
  console.log('PASS visibility/deadline/replacement/HTTP/body failure stops release own hooks');
}

async function checkScopeAndCheckpointLoss() {
  const b = createBrowser();
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'suri-path-evidence-'));
  const file = path.join(directory, 'records.jsonl');
  const writer = createCheckpointWriter(file);
  try {
    assert.equal(await b.response('unrelated', origin + '/api/account').text(), 'unrelated');
    assert.equal(await b.response('external', 'https://other.invalid' + new URL(url).pathname).text(), 'external');
    assert.equal(b.window[key].rows.length, 0);
    await b.response().text();
    b.resourceObservers[0].callback({ getEntries: () => [{ name: url, startTime: 10, responseStart: 20, responseEnd: 22, transferSize: 123, responseStatus: 200 }] });
    await writer.capture(b.read);
    await assert.rejects(writer.capture(() => { throw Error('tab disappeared'); }), /tab disappeared/);
    const saved = restoreCheckpoints(file);
    assert.equal(saved.state.rows[0].body, rawBody);
    assert.equal(saved.state.resources[0].transferSize, 123);
    assert.equal(saved.errors.length, 1);
    assert.throws(() => b.observer.expectCoordinates({ ...target, coordinates: [[Infinity, 37]] }), /Expected one GPS/);
    assert.throws(() => vm.runInContext(`(${installPathObserver.toString()})(config, onStop)`, b.context), /Invalid observer/);
    console.log('PASS scope guards and checkpoint survives final tab loss');
  } finally { b.observer.stop('manual'); writer.close(); }
}

async function checkConcurrentCapture() {
  const b = createBrowser();
  const m = createMap();
  let resolve;
  m.map.getSource().getData = () => { m.values.reads++; return new Promise(done => { resolve = done; }); };
  b.window.__SURI_MAP_OBSERVER__.connect(m.map);
  b.observer.expectCoordinates(target);
  m.values.rendered = m.values.source;
  const pending = b.observer.captureSource();
  void b.observer.sample();
  assert.equal(m.values.reads, 1, 'final capture and timer sample must not overlap');
  b.observer.stop('manual');
  const saved = JSON.stringify(b.window[key]);
  resolve({ type: 'FeatureCollection', features: m.values.source });
  await assert.rejects(pending, /incomplete/);
  assert.equal(JSON.stringify(b.window[key]), saved);
  assert.equal(b.window[key].rows.at(-1).mapCheckInFlight, true);
  const c = createBrowser();
  const broken = createMap();
  broken.map.on = () => { throw Error('registration failed'); };
  assert.doesNotThrow(() => c.window.__SURI_MAP_OBSERVER__.connect(broken.map));
  assert.equal(c.window[key].reason, 'map_connection_failed');
  assert.equal(c.timers.size, 0);
  console.log('PASS source reads do not overlap; pending snapshot and connection failure clean up');
}

async function checkChangingSourceIsBounded() {
  const b = createBrowser({ deadlineMs: 20, sampleIntervalMs: 20 });
  const m = createMap();
  b.window.__SURI_MAP_OBSERVER__.connect(m.map);
  b.observer.expectCoordinates(target);
  m.values.rendered = m.values.source;
  m.map.getSource().getData = async () => {
    m.listeners.get('sourcedata')({ sourceId: 'operational-movement-path' });
    return { type: 'FeatureCollection', features: m.values.source };
  };
  for (let i = 0; i < 10; i++) await b.observer.sample();
  assert.equal(b.window[key].reason, 'sample_limit');
  assert.equal(b.window[key].rows.filter(row => row.kind === 'map_sample_discarded').length, 2);
  assert.equal(b.window[key].rows.some(row => row.kind === 'map_target_observed'), false);
  assert.equal(b.timers.size, 0);
  console.log('PASS repeated source changes stay bounded and never become success');
}

(async () => {
  await checkResponseCapture();
  await checkMapObservation();
  await checkBoundedStop();
  await checkFinalSourceCapture();
  await checkStopDuringBodyRead();
  await checkInvalidObservations();
  await checkExitAndFailures();
  await checkScopeAndCheckpointLoss();
  await checkConcurrentCapture();
  await checkChangingSourceIsBounded();
})().catch(error => { console.error(error); process.exitCode = 1; });
