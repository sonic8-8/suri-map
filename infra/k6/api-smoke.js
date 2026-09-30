import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const INCIDENT_ID = __ENV.INCIDENT_ID || 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001';
const POLICE_PHONE_ID = __ENV.POLICE_PHONE_ID || '00000000-0000-0000-0000-000000000205';
const WEB_ACCESS_TOKEN = requiredEnv('WEB_ACCESS_TOKEN');
const APP_ACCESS_TOKEN = requiredEnv('APP_ACCESS_TOKEN');
const SMOKE_DURATION = __ENV.SMOKE_DURATION || '30s';
const WEB_API_P95_MS = Number(__ENV.WEB_API_P95_MS || __ENV.WEB_AUTH_P95_MS || '2000');
const APP_API_P95_MS = Number(__ENV.APP_API_P95_MS || __ENV.APP_AUTH_P95_MS || '2000');
const SSE_P95_MS = Number(__ENV.SSE_P95_MS || '15000');
const HTTP_FAIL_RATE = Number(__ENV.HTTP_FAIL_RATE || '0.05');

export const options = {
  scenarios: {
    web_api: {
      executor: 'constant-vus',
      vus: 1,
      duration: SMOKE_DURATION,
      exec: 'web_api',
      tags: { scenario: 'web_api' },
    },
    app_api: {
      executor: 'constant-vus',
      vus: 1,
      duration: SMOKE_DURATION,
      exec: 'app_api',
      tags: { scenario: 'app_api' },
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
    'http_req_duration{scenario:web_api}': [`p(95)<${WEB_API_P95_MS}`],
    'http_req_duration{scenario:app_api}': [`p(95)<${APP_API_P95_MS}`],
    'http_req_duration{scenario:sse_event}': [`p(95)<${SSE_P95_MS}`],
  },
};

export function web_api() {
  const res = http.get(
    `${BASE_URL}/api/incidents`,
    { headers: webHeaders() }
  );

  check(res, {
    'web incidents status 200': (r) => r.status === 200,
  });

  sleep(1);
}

export function app_api() {
  const heartbeatRes = http.post(
    `${BASE_URL}/api/police-phones/${POLICE_PHONE_ID}/heartbeat`,
    JSON.stringify({ clientTs: new Date().toISOString(), sequence: __VU * 1000 + __ITER }),
    {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${APP_ACCESS_TOKEN}`,
        'X-Client-Channel': 'APP',
        'X-PolicePhone-Id': POLICE_PHONE_ID,
      },
    }
  );

  check(heartbeatRes, { 'app heartbeat status 200': (r) => r.status === 200 });

  sleep(1);
}

export function sse_event() {
  const sseRes = http.get(
    `${BASE_URL}/api/incidents/${INCIDENT_ID}/events`,
    {
      headers: {
        ...webHeaders(),
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

function webHeaders() {
  return {
    'Authorization': `Bearer ${WEB_ACCESS_TOKEN}`,
    'X-Client-Channel': 'WEB',
  };
}

function requiredEnv(name) {
  const value = __ENV[name];
  if (!value) {
    throw new Error(`${name} is required. Provide a Keycloak access token; /api/auth/login is removed.`);
  }
  return value;
}
