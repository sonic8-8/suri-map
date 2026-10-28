function installPathObserver(config, onStop) {
  if (!config || !/^__[A-Za-z0-9_]+$/.test(config.key) || config.key in window ||
      !/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(config.incidentId) ||
      typeof onStop !== 'function') throw Error('Invalid observer configuration');
  for (const name of ['maxResponses', 'maxBodyCharacters', 'deadlineMs', 'sampleIntervalMs']) {
    if (!Number.isSafeInteger(config[name]) || config[name] <= 0) throw Error(`Invalid ${name}`);
  }
  if (location.pathname !== '/incidents' || document.visibilityState !== 'visible' ||
      window.__SURI_MAP_MEASUREMENT_ENABLED__ === true || window.__SURI_MAP_OBSERVER__) {
    throw Error('Observer requires visible incident list, measurement OFF and no registered observer');
  }
  config = { ...config };
  const state = {
    started: performance.now(), timeOrigin: performance.timeOrigin, wallStarted: Date.now(),
    rows: [], resources: [], cadence: [], visibility: [], stopped: false,
  };
  window[config.key] = state;
  // The board sends board_open_clicked and all_paths_map_idle only while board measurement is on.
  window.__SURI_MAP_MEASUREMENT_ENABLED__ = true;
  const prefix = `/api/incidents/${config.incidentId}/board/search-paths/`;
  const originalDescriptor = Object.getOwnPropertyDescriptor(Response.prototype, 'text');
  const originalText = Response.prototype.text;
  let responseCount = 0, bodyCharacters = 0, intervalId, deadlineId;
  const kindOf = value => {
    try {
      const url = new URL(value);
      if (url.origin !== location.origin) return null;
      return ['segments', 'changes'].find(kind => url.pathname === `${prefix}${kind}/query`) ?? null;
    } catch (_) { return null; }
  };
  const sourceId = 'operational-movement-path';
  const layerIds = ['compare', 'vehicle', 'foot', 'unknown'].map(name => `${sourceId}-${name}`);
  let map, target, targetObserved = false, firstObserved = false, sampling = false, generation = 0;
  let finalSourceCapture, capturingSource = false;
  const record = (kind, details = {}) => state.rows.push({ kind, ...details, at: performance.now() - state.started, returned: true });
  function retainBody(row, body) {
    if (bodyCharacters + body.length > config.maxBodyCharacters) {
      row.bodyOmitted = 'body_limit';
      stop('body_limit');
      return false;
    }
    row.body = body;
    bodyCharacters += body.length;
    return true;
  }
  const appendResources = entries => {
    for (const entry of entries) {
      const kind = kindOf(entry.name);
      if (!kind || entry.startTime < state.started) continue;
      if (state.resources.length >= config.maxResponses) { stop('resource_limit'); return; }
      state.resources.push({ kind, start: entry.startTime - state.started,
        responseStart: entry.responseStart - state.started, end: entry.responseEnd - state.started,
        status: entry.responseStatus ?? null, transferSize: entry.transferSize });
    }
  };
  const resourceObserver = new PerformanceObserver(list => {
    if (!state.stopped) appendResources(list.getEntries());
  });
  const sourceChanged = event => { if (event.sourceId === sourceId) generation++; };
  // Keeps the two board timings exactly as the page sent them; the runner never derives them.
  const boardMeasured = event => {
    const detail = event.detail;
    if (state.stopped || detail?.incidentId !== config.incidentId ||
        !['board_open_clicked', 'all_paths_map_idle'].includes(detail.stage)) return;
    record('board_measurement', { event: JSON.parse(JSON.stringify(detail)) });
  };
  window.addEventListener('suri-map:board-measurement', boardMeasured);
  const mapObserver = {
    connect(nextMap) {
      if (state.stopped) return;
      if (map && map !== nextMap) { stop('map_replaced'); return; }
      if (map === nextMap) return;
      map = nextMap;
      try { map.on('sourcedata', sourceChanged); }
      catch (_) { stop('map_connection_failed'); return; }
      record('map_connected');
    },
    disconnect(previousMap) {
      if (map === previousMap) stop('map_disconnected');
    },
  };
  window.__SURI_MAP_OBSERVER__ = mapObserver;

  function expectCoordinates(value) {
    if (target || state.stopped) throw Error('Only one GPS target per active trial');
    if (!value || !['segmentId', 'searchPathId', 'version'].every(name => typeof value[name] === 'string' && value[name]) ||
        !/^\d+$/.test(value.version) || !Array.isArray(value.coordinates) ||
        value.coordinates.length < 1 || value.coordinates.length > 6 ||
        !value.coordinates.every(point => Array.isArray(point) && point.length === 2 &&
          point.every(Number.isFinite) && Math.abs(point[0]) <= 180 && Math.abs(point[1]) <= 90)) {
      throw Error('Expected one GPS batch with IDs, version and up to six coordinates');
    }
    target = JSON.parse(JSON.stringify(value));
    record('map_target_expected', { target });
  }

  function matchesTarget(feature) {
    return feature.properties?.entityId === target.segmentId &&
      feature.properties?.searchPathId === target.searchPathId &&
      String(feature.properties?.searchPathVersion) === target.version;
  }

  function visibleAtTarget(layers) {
    const canvas = map.getCanvas();
    return target.coordinates.every(coordinate => {
      const point = map.project(coordinate);
      return point.x >= 0 && point.y >= 0 && point.x < canvas.clientWidth && point.y < canvas.clientHeight &&
        map.queryRenderedFeatures(point, { layers }).some(feature => feature.source === sourceId && matchesTarget(feature));
    });
  }

  function captureSource() {
    if (finalSourceCapture) return finalSourceCapture;
    if (state.stopped || !map || sampling || !map.isSourceLoaded(sourceId)) {
      return Promise.reject(Error('Map source not ready for final capture'));
    }
    capturingSource = true;
    finalSourceCapture = (async () => {
      const capturedMap = map, before = generation;
      const began = performance.now();
      try {
        const data = await map.getSource(sourceId).getData();
        if (state.stopped || map !== capturedMap || generation !== before || !map.isSourceLoaded(sourceId)) {
          throw Error('Source changed during final capture');
        }
        const recordIndex = state.rows.length;
        const row = { kind: 'map_final_source', returned: true, at: performance.now() - state.started };
        state.rows.push(row);
        if (!retainBody(row, JSON.stringify(data))) throw Error('Final source exceeds retention cap');
        row.captureMs = performance.now() - began;
        return { recordIndex };
      } catch (_) {
        stop('final_source_failed');
        throw Error('Final source capture incomplete');
      } finally {
        capturingSource = false;
      }
    })();
    return finalSourceCapture;
  }

  async function sample() {
    if (state.stopped || sampling || capturingSource) return;
    if (document.visibilityState !== 'visible') { stop('hidden'); return; }
    if (window.__SURI_MAP_MEASUREMENT_ENABLED__ !== true) { stop('measurement_disabled'); return; }
    if (Response.prototype.text !== wrappedText || window.__SURI_MAP_OBSERVER__ !== mapObserver) {
      stop('observer_replaced'); return;
    }
    if (!map) return;
    if (state.cadence.length >= Math.ceil(config.deadlineMs / config.sampleIntervalMs) + 1) {
      stop('sample_limit'); return;
    }
    sampling = true;
    const began = performance.now();
    try {
      const layers = layerIds.filter(id => map.getLayer(id));
      if (!layers.length) return;
      if (!firstObserved && map.queryRenderedFeatures({ layers }).some(feature => feature.source === sourceId)) {
        firstObserved = true;
        record('map_first_observed');
      }
      if (!target || targetObserved || !map.isSourceLoaded(sourceId) || !visibleAtTarget(layers)) return;
      const observedMap = map, before = generation;
      const readStarted = performance.now();
      const data = await map.getSource(sourceId).getData();
      const readEnded = performance.now();
      if (state.stopped) return;
      if (map !== observedMap) { stop('map_replaced'); return; }
      if (generation !== before || !map.isSourceLoaded(sourceId)) {
        record('map_sample_discarded', { reason: 'source_changed_during_check' });
        return;
      }
      const feature = data.features.find(matchesTarget);
      const coordinates = feature?.geometry?.type === 'LineString' ? feature.geometry.coordinates : [];
      const exactCoordinates = JSON.stringify(coordinates.slice(-target.coordinates.length)) === JSON.stringify(target.coordinates);
      if (!exactCoordinates || !visibleAtTarget(layers)) { stop('target_not_confirmed'); return; }
      targetObserved = true;
      record('map_target_observed', { target, sourceReadStarted: readStarted - state.started, sourceReadEnded: readEnded - state.started });
      const row = { kind: 'map_source', at: performance.now() - state.started, returned: true };
      state.rows.push(row);
      retainBody(row, JSON.stringify(data));
    } catch (_) {
      stop('map_check_failed');
    } finally {
      if (!state.stopped) state.cadence.push({ at: began - state.started, duration: performance.now() - began });
      sampling = false;
    }
  }
  const wrappedText = async function () {
    const kind = kindOf(this.url);
    if (!kind || state.stopped) return originalText.call(this);
    if (responseCount >= config.maxResponses) {
      stop('response_limit');
      return originalText.call(this);
    }
    responseCount++;
    const row = { kind, status: this.status, entered: performance.now() - state.started, returned: false };
    state.rows.push(row);
    try {
      const body = await originalText.call(this);
      if (!state.stopped) {
        Object.assign(row, { bodyReadAt: performance.now() - state.started, returned: true });
        if (this.status === 200) retainBody(row, body);
        else { row.bodyOmitted = 'http_status'; stop('http_status'); }
      }
      return body;
    } catch (error) {
      if (!state.stopped) {
        Object.assign(row, { returned: true, bodyOmitted: 'body_read_failure' });
        stop('body_read_failure');
      }
      throw error;
    }
  };
  Response.prototype.text = wrappedText;
  function stop(reason) {
    if (state.stopped) return;
    state.stopped = true;
    state.reason = reason;
    state.ended = performance.now() - state.started;
    state.wallEnded = Date.now();
    clearInterval(intervalId);
    clearTimeout(deadlineId);
    document.removeEventListener('visibilitychange', visibilityChanged);
    window.removeEventListener('suri-map:board-measurement', boardMeasured);
    window.__SURI_MAP_MEASUREMENT_ENABLED__ = false;
    appendResources(resourceObserver.takeRecords());
    resourceObserver.disconnect();
    if (map) {
      try { map.off('sourcedata', sourceChanged); }
      catch (_) { record('cleanup_failed'); }
    }
    map = undefined;
    if (window.__SURI_MAP_OBSERVER__ === mapObserver) delete window.__SURI_MAP_OBSERVER__;
    if (Response.prototype.text === wrappedText) {
      if (originalDescriptor) Object.defineProperty(Response.prototype, 'text', originalDescriptor);
      else delete Response.prototype.text;
    }
    for (const row of state.rows) {
      if (!row.returned) Object.assign(row, { returned: true, bodyOmitted: 'stopped_in_flight' });
    }
    record('observer_stopped', { reason, responseCount, bodyCharacters, mapCheckInFlight: sampling || capturingSource });
    try { onStop(reason); }
    catch (_) { record('stop_handler_failed'); }
  }
  function visibilityChanged() {
    state.visibility.push({ at: performance.now() - state.started, value: document.visibilityState });
    if (document.visibilityState !== 'visible') stop('hidden');
  }
  try {
    resourceObserver.observe({ type: 'resource', buffered: false });
    document.addEventListener('visibilitychange', visibilityChanged);
    intervalId = setInterval(() => { void sample(); }, config.sampleIntervalMs);
    deadlineId = setTimeout(() => stop('deadline'), config.deadlineMs);
  } catch (error) {
    stop('installation_failed');
    throw error;
  }
  return { stop, expectCoordinates, sample, captureSource };
}

module.exports = { installPathObserver };
