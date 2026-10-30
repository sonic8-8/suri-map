// Serialized with readReadiness by the Aside caller. Keeps only the two board events and tab visibility,
// so the page carries no response bodies, map checks or map source events while it is measured.
function armLightTiming(config, readReadiness) {
  readReadiness(config);
  if (!Number.isSafeInteger(config.deadlineMs) || config.deadlineMs <= 0) throw Error('capture_limit_missing');
  if (sessionStorage.getItem(config.key) || sessionStorage.getItem(config.key + 'Exit')) throw Error('already_attempted');
  const boardPath = `/incidents/${config.incidentId}/board`;
  const stages = ['board_open_clicked', 'all_paths_map_idle'];
  const state = { started: performance.now(), timeOrigin: performance.timeOrigin, wallStarted: Date.now(),
    rows: [], visibility: [], stopped: false };
  let guard, deadline;
  const received = stage => state.rows.some(row => row.event.stage === stage);
  const boardMeasured = event => {
    const detail = event.detail;
    if (state.stopped || detail?.incidentId !== config.incidentId || !stages.includes(detail.stage) || received(detail.stage)) return;
    state.rows.push({ kind: 'board_measurement', event: JSON.parse(JSON.stringify(detail)), at: performance.now() - state.started });
    if (stages.every(received)) stop('complete');
  };
  const visibilityChanged = () => {
    state.visibility.push({ at: performance.now() - state.started, value: document.visibilityState });
    if (document.visibilityState !== 'visible') stop('hidden');
  };
  function stop(reason) {
    if (state.stopped) return;
    Object.assign(state, { stopped: true, reason, ended: performance.now() - state.started, wallEnded: Date.now() });
    clearInterval(guard);
    clearTimeout(deadline);
    window.removeEventListener('suri-map:board-measurement', boardMeasured);
    document.removeEventListener('visibilitychange', visibilityChanged);
    window.__SURI_MAP_MEASUREMENT_ENABLED__ = false;
    // The state is two events, so sessionStorage keeps it for the caller to read after returning to the list.
    let saved = false;
    try { sessionStorage.setItem(config.key, JSON.stringify(state)); saved = true; }
    catch (_) { /* The exit record still tells the caller the run failed. */ }
    sessionStorage.setItem(config.key + 'Exit', JSON.stringify({ reason, saved }));
  }
  sessionStorage.setItem(config.key + 'Exit', JSON.stringify({ reason: 'armed', saved: false }));
  window[config.key + 'Control'] = { stop };
  // The board sends board_open_clicked and all_paths_map_idle only while board measurement is on.
  window.__SURI_MAP_MEASUREMENT_ENABLED__ = true;
  window.addEventListener('suri-map:board-measurement', boardMeasured);
  document.addEventListener('visibilitychange', visibilityChanged);
  // Same environment checks as armHistoryCapture, plus the hidden tab and flag checks of the path recording.
  guard = setInterval(() => {
    try {
      const claims = JSON.parse(atob((sessionStorage.getItem('suriMapAccessToken') || '').split('.')[1]
        .replace(/-/g, '+').replace(/_/g, '/')));
      if (location.origin !== config.origin || ![boardPath, '/incidents'].includes(location.pathname) ||
          document.querySelector('script[type=module][src]')?.getAttribute('src') !== config.script ||
          claims.accountId !== config.accountId || !Number.isFinite(claims.exp) || claims.exp * 1000 <= Date.now()) {
        stop('environment_changed');
        return;
      }
    } catch (_) { stop('authentication_lost'); return; }
    if (document.visibilityState !== 'visible') stop('hidden');
    else if (window.__SURI_MAP_MEASUREMENT_ENABLED__ !== true) stop('measurement_disabled');
  }, 250);
  deadline = setTimeout(() => stop('deadline'), config.deadlineMs);
  return { armed: true };
}

module.exports = { armLightTiming };
