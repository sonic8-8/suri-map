// A single synthetic APP request, timed by the same document as the map observer.
async function sendGpsBatch({ key, plan, credential, timeoutMs }) {
  const state = window[key], controller = window[key + 'Control'];
  if (!state || state.stopped || !controller || location.origin !== 'https://suri-map.sonic8-8.com' ||
      location.pathname !== `/incidents/${plan.incidentId}/board` || document.visibilityState !== 'visible' ||
      window.__SURI_MAP_MEASUREMENT_ENABLED__ === true) throw Error('gps_observer_not_ready');
  if (!Number.isSafeInteger(timeoutMs) || timeoutMs <= 0) throw Error('gps_timeout_missing');
  if (state.rows.some(row => row.kind.startsWith('gps_'))) throw Error('gps_already_attempted');
  const claims = JSON.parse(atob(credential.accessToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
  const claim = name => Array.isArray(claims[name]) && claims[name].length === 1 ? claims[name][0] : claims[name];
  if (credential.accountId !== plan.accountId || credential.policePhoneId !== plan.policePhoneId || credential.pathId !== plan.pathId ||
      claim('accountId') !== plan.accountId || claim('accountType') !== 'TEAM' || claim('organizationType') !== 'SUPPORT_UNIT' ||
      !Number.isFinite(claims.exp) || claims.exp * 1000 <= Date.now() + timeoutMs) throw Error('gps_credential_invalid');
  if (plan.body.points.length !== 6 || plan.target.coordinates.length !== 6 ||
      plan.body.pathId !== plan.pathId || plan.body.opId !== plan.opId || plan.body.incidentId !== plan.incidentId ||
      plan.target.searchPathId !== plan.pathId || !Number.isSafeInteger(Number(plan.target.version)) ||
      plan.body.points.some((point, index) => !Number.isSafeInteger(point.elapsedRealtimeNanos) ||
        point.lon !== plan.target.coordinates[index][0] || point.lat !== plan.target.coordinates[index][1])) {
    throw Error('gps_plan_invalid');
  }
  const now = Date.now();
  const body = { ...plan.body, points: plan.body.points.map((point, index) => ({ ...point,
    clientTs: new Date(now - (5 - index) * 2500).toISOString() })) };
  const payload = JSON.stringify(body);
  const record = (kind, details, at = performance.now() - state.started) => {
    if (!state.stopped) state.rows.push({ kind, ...details, at, returned: true });
  };
  // Register before sending so a map update that precedes the HTTP response can be observed.
  controller.expectCoordinates(plan.target);
  record('gps_request_prepared', { requestId: plan.requestId, body: payload });
  return new Promise((resolve, reject) => {
    const request = new XMLHttpRequest();
    let settled = false;
    function fail(reason) {
      if (settled) return;
      settled = true;
      record('gps_write_failed', { requestId: plan.requestId, reason, commitOutcome: 'unconfirmed' });
      reject(Error(reason));
    }
    request.onload = () => {
      const at = performance.now() - state.started;
      if (settled) return;
      if (window[key] !== state || state.stopped) { fail('gps_observer_lost'); return; }
      let accepted = false;
      try {
        const response = JSON.parse(request.responseText);
        const segment = response.segments?.[0];
        accepted = request.status === 200 && response.id === plan.pathId && response.opId === plan.opId &&
          response.accountId === plan.accountId && response.status === 'RECORDING' &&
          Number.isSafeInteger(response.version) && String(response.version) === plan.target.version &&
          response.acceptedPointCount === 6 && response.excludedPointCount === 0 &&
          response.segments.length === 1 && segment.id === plan.target.segmentId &&
          segment.startIndex === plan.beforePointCount && segment.endIndex === plan.beforePointCount + 5 &&
          segment.startPointId === body.points[0].pointId && segment.endPointId === body.points[5].pointId;
      } catch (_) { /* Invalid responses cannot confirm a write. */ }
      record('gps_write_completed', { requestId: plan.requestId, status: request.status, accepted,
        clock: 'observer-relative-performance' }, at);
      if (!accepted) { fail('gps_response_mismatch'); return; }
      settled = true;
      resolve({ accepted: true, requestId: plan.requestId });
    };
    request.onerror = () => fail('gps_network_error');
    request.ontimeout = () => fail('gps_timeout');
    request.onabort = () => fail('gps_aborted');
    try {
      request.open('POST', '/api/search-paths/batch', true);
      request.timeout = timeoutMs;
      request.setRequestHeader('Authorization', `Bearer ${credential.accessToken}`);
      request.setRequestHeader('Content-Type', 'application/json');
      request.setRequestHeader('X-Client-Channel', 'APP');
      request.setRequestHeader('X-PolicePhone-Id', plan.policePhoneId);
      request.setRequestHeader('Idempotency-Key', plan.requestId);
      record('gps_write_started', { requestId: plan.requestId, clock: 'observer-relative-performance' });
      request.send(payload);
    } catch (_) { fail('gps_send_failed'); }
  });
}

module.exports = { sendGpsBatch };
