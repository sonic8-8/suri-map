// Serialized with the existing observer/readiness functions by the Aside caller.
function armHistoryCapture(config, installPathObserver, readReadiness) {
  readReadiness(config);
  for (const name of ['deadlineMs', 'maxResponses', 'maxBodyCharacters', 'sampleIntervalMs']) {
    if (!Number.isSafeInteger(config[name]) || config[name] <= 0) throw Error('capture_limit_missing');
  }
  if (sessionStorage.getItem(config.key) || sessionStorage.getItem(config.key + 'Exit')) throw Error('already_attempted');
  const boardPath = `/incidents/${config.incidentId}/board`;
  let guard;
  const onStop = reason => {
    clearInterval(guard);
    try {
      // Large bodies are already fsynced by the caller; failure retains only earlier checkpoints.
      sessionStorage.setItem(config.key + 'Exit', JSON.stringify({ reason,
        recovery: 'disk-checkpoints', finalCheckpointSaved: reason === 'complete' }));
    } finally {
      if (location.origin === config.origin && [boardPath, '/incidents'].includes(location.pathname)) location.replace('/incidents');
    }
  };
  sessionStorage.setItem(config.key + 'Exit', JSON.stringify({ reason: 'armed', finalCheckpointSaved: false }));
  const controller = installPathObserver(config, onStop);
  window[config.key + 'Control'] = controller;
  guard = setInterval(() => {
    try {
      const claims = JSON.parse(atob((sessionStorage.getItem('suriMapAccessToken') || '').split('.')[1]
        .replace(/-/g, '+').replace(/_/g, '/')));
      if (location.origin !== config.origin || ![boardPath, '/incidents'].includes(location.pathname) ||
          document.querySelector('script[type=module][src]')?.getAttribute('src') !== config.script ||
          claims.accountId !== config.accountId || !Number.isFinite(claims.exp) || claims.exp * 1000 <= Date.now()) {
        controller.stop('environment_changed');
      }
    } catch (_) { controller.stop('authentication_lost'); }
  }, 250);
  return { armed: true };
}

module.exports = { armHistoryCapture };
