import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const INCIDENT_ID = __ENV.INCIDENT_ID || 'inc-precinct-first-001';
const POLICE_PHONE_ID = __ENV.POLICE_PHONE_ID || 'dev-alpha-phone-01';
const WEB_ACCOUNT_CODE = __ENV.WEB_ACCOUNT_CODE || 'acct-cmd-alpha';
const APP_ACCOUNT_CODE = __ENV.APP_ACCOUNT_CODE || 'acct-team-alpha';
const FIXTURE_PASSWORD = __ENV.FIXTURE_PASSWORD || 'fixture';
const SMOKE_DURATION = __ENV.SMOKE_DURATION || '30s';
const WEB_AUTH_P95_MS = Number(__ENV.WEB_AUTH_P95_MS || '2000');
const APP_AUTH_P95_MS = Number(__ENV.APP_AUTH_P95_MS || '2000');
const SSE_P95_MS = Number(__ENV.SSE_P95_MS || '15000');
const HTTP_FAIL_RATE = Number(__ENV.HTTP_FAIL_RATE || '0.05');

export const options = {
  scenarios: {
    web_auth: {
      executor: 'constant-vus',
      vus: 1,
      duration: SMOKE_DURATION,
      exec: 'web_auth',
      tags: { scenario: 'web_auth' },
    },
    app_auth: {
      executor: 'constant-vus',
      vus: 1,
      duration: SMOKE_DURATION,
      exec: 'app_auth',
      tags: { scenario: 'app_auth' },
    },
    sse_event: {
      executor: 'constant-vus',
      vus: 1,
      duration: SMOKE_DURATION,
      exec: 'sse_event',
      tags: { scenario: 'sse_event' },
    },
  },
  thresholds: {
    http_req_failed: [`rate<${HTTP_FAIL_RATE}`],
    'http_req_duration{scenario:web_auth}': [`p(95)<${WEB_AUTH_P95_MS}`],
    'http_req_duration{scenario:app_auth}': [`p(95)<${APP_AUTH_P95_MS}`],
    'http_req_duration{scenario:sse_event}': [`p(95)<${SSE_P95_MS}`],
  },
};

function loginWeb() {
  const res = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ accountCode: WEB_ACCOUNT_CODE, password: FIXTURE_PASSWORD, channel: 'WEB' }),
    { headers: { 'Content-Type': 'application/json', 'X-Client-Channel': 'WEB' } }
  );
  let accessToken;
  try { accessToken = JSON.parse(res.body).accessToken; } catch (_) { /* ignore */ }
  return { res, accessToken };
}

export function web_auth() {
  const { res: loginRes, accessToken } = loginWeb();

  check(loginRes, {
    'web login status 200': (r) => r.status === 200,
    'web login has accessToken': () => !!accessToken,
  });

  if (!accessToken) { sleep(1); return; }

  const logoutRes = http.post(
    `${BASE_URL}/api/auth/logout`,
    null,
    { headers: { 'Authorization': `Bearer ${accessToken}`, 'X-Client-Channel': 'WEB' } }
  );

  check(logoutRes, { 'web logout status 200': (r) => r.status === 200 });

  sleep(1);
}

export function app_auth() {
  const loginRes = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ accountCode: APP_ACCOUNT_CODE, password: FIXTURE_PASSWORD, channel: 'APP', policePhoneCode: POLICE_PHONE_ID }),
    { headers: { 'Content-Type': 'application/json', 'X-Client-Channel': 'APP' } }
  );

  let accessToken;
  try { accessToken = JSON.parse(loginRes.body).accessToken; } catch (_) { /* ignore */ }

  check(loginRes, {
    'app login status 200': (r) => r.status === 200,
    'app login has accessToken': () => !!accessToken,
  });

  if (!accessToken) { sleep(1); return; }

  const heartbeatRes = http.post(
    `${BASE_URL}/api/police-phones/${POLICE_PHONE_ID}/heartbeat`,
    JSON.stringify({ clientTs: new Date().toISOString(), sequence: __VU * 1000 + __ITER }),
    {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${accessToken}`,
        'X-Client-Channel': 'APP',
        'X-PolicePhone-Id': POLICE_PHONE_ID,
      },
    }
  );

  check(heartbeatRes, { 'app heartbeat status 200': (r) => r.status === 200 });

  const logoutRes = http.post(
    `${BASE_URL}/api/auth/logout`,
    null,
    { headers: { 'Authorization': `Bearer ${accessToken}`, 'X-Client-Channel': 'APP' } }
  );

  check(logoutRes, { 'app logout status 200': (r) => r.status === 200 });

  sleep(1);
}

export function sse_event() {
  const { res: loginRes, accessToken } = loginWeb();

  check(loginRes, { 'sse login status 200': (r) => r.status === 200 });

  if (!accessToken) { sleep(1); return; }

  const sseRes = http.get(
    `${BASE_URL}/api/incidents/${INCIDENT_ID}/events`,
    {
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'X-Client-Channel': 'WEB',
        'Accept': 'text/event-stream',
      },
      timeout: '10s',
    }
  );

  check(sseRes, {
    'sse status 200': (r) => r.status === 200,
    'sse content-type event-stream': (r) => (r.headers['Content-Type'] || '').includes('text/event-stream'),
  });

  sleep(1);
}
