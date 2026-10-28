function readReadiness(config) {
  const claims = JSON.parse(atob((sessionStorage.getItem('suriMapAccessToken') || '').split('.')[1]
    .replace(/-/g, '+').replace(/_/g, '/')));
  const result = {
    origin: location.origin, path: location.pathname, visibility: document.visibilityState,
    script: document.querySelector('script[type=module][src]')?.getAttribute('src'),
    accountMatches: claims.accountId === config.accountId,
    tokenSeconds: claims.exp - Math.floor(Date.now() / 1000),
    measurement: window.__SURI_MAP_MEASUREMENT_ENABLED__ === true,
    observer: Boolean(window.__SURI_MAP_OBSERVER__),
  };
  if (result.origin !== config.origin || result.path !== '/incidents' || result.visibility !== 'visible' ||
      result.script !== config.script || !result.accountMatches || result.measurement || result.observer ||
      !Number.isFinite(result.tokenSeconds) || result.tokenSeconds <= 120 ||
      !document.querySelector('[aria-label="사건 카드 목록"]')) throw Error('readiness_failed');
  return result;
}

async function readTarget(config) {
  const response = await fetch(`/api/incidents/${config.incidentId}/board?includeSlots=op_toggle`, {
    headers: { Authorization: `Bearer ${sessionStorage.getItem('suriMapAccessToken')}`, 'X-Client-Channel': 'WEB' },
    signal: AbortSignal.timeout(5000),
  });
  if (!response.ok) throw Error('target_read_failed');
  const board = await response.json();
  if (board.incidentId !== config.incidentId || board.activeOpId !== config.opId ||
      !Array.isArray(board.selectedOpIds) || board.selectedOpIds.length !== 1 || board.selectedOpIds[0] !== config.opId) {
    throw Error('target_mismatch');
  }
  return { incidentId: board.incidentId, activeOpId: board.activeOpId, selectedOpIds: board.selectedOpIds };
}

// Serialized with the reviewed observer and readiness functions into the same page closure.
function armRunner(config, installPathObserver, readReadiness) {
  readReadiness(config);
  if (sessionStorage.getItem(config.key) || sessionStorage.getItem(config.key + 'Exit')) throw Error('already_attempted');
  const boardPath = `/incidents/${config.incidentId}/board`;
  let guard, controller, checkedRows = 0;
  const onStop = reason => {
    clearInterval(guard);
    const state = window[config.key];
    let saved = false;
    try { sessionStorage.setItem(config.key, JSON.stringify(state)); saved = true; }
    catch (_) { /* Earlier fsync checkpoints survive a browser storage failure. */ }
    try { sessionStorage.setItem(config.key + 'Exit', JSON.stringify({ reason, saved })); }
    finally {
      // Do not redirect a user who left the test incident for another page.
      if (location.origin === config.origin && [boardPath, '/incidents'].includes(location.pathname)) {
        location.replace('/incidents');
      }
    }
  };
  sessionStorage.setItem(config.key + 'Exit', JSON.stringify({ reason: 'armed', saved: false }));
  controller = installPathObserver({ key: config.key, incidentId: config.incidentId,
    deadlineMs: 30000, maxResponses: 16, maxBodyCharacters: 2000000, sampleIntervalMs: 250 }, onStop);
  window[config.key + 'Control'] = controller;
  guard = setInterval(() => {
    try {
      const claims = JSON.parse(atob((sessionStorage.getItem('suriMapAccessToken') || '').split('.')[1]
        .replace(/-/g, '+').replace(/_/g, '/')));
      if (location.origin !== config.origin || ![boardPath, '/incidents'].includes(location.pathname) ||
          document.querySelector('script[type=module][src]')?.getAttribute('src') !== config.script ||
          claims.accountId !== config.accountId ||
          !Number.isFinite(claims.exp) || claims.exp * 1000 <= Date.now()) {
        controller.stop('environment_changed');
        return;
      }
    } catch (_) { controller.stop('authentication_lost'); return; }
    const rows = window[config.key].rows;
    while (checkedRows < rows.length) {
      const row = rows[checkedRows];
      if (!row.returned) break;
      checkedRows++;
      if (!['segments', 'changes'].includes(row.kind) || typeof row.body !== 'string') continue;
      try {
        const body = JSON.parse(row.body);
        if (!Array.isArray(body.paths) || body.paths.some(item => typeof item?.opId !== 'string' || !item.opId)) {
          throw Error('scope_unverifiable');
        }
        if (body.paths.some(item => item.opId !== config.opId)) {
          controller.stop('response_scope_mismatch'); return;
        }
      } catch (_) { controller.stop('scope_unverifiable'); return; }
    }
  }, 250);
  return { armed: true };
}

module.exports = { readReadiness, readTarget, armRunner };
