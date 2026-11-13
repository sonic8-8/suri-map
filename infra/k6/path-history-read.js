// 상황판에 들어갈 때 나가는 첫 경로 이력 요청을 한 번에 하나씩 보내 응답 시간을 재려고 작성했다.
// 사용자 PC 회선을 빼고 보려고 Ops 서버에서 실행한다. 부하 테스트가 아니다.
// 서버에서 정상 처리된 조회마다 위치 조회 감사 기록이 한 줄씩 쌓이므로 횟수는 20번까지로 제한했다.
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'https://suri-map.sonic8-8.com';
const INCIDENT_ID = __ENV.INCIDENT_ID || 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001';
const OP_ID = __ENV.OP_ID || '88888888-8888-8888-8888-888888880001';
const ITERATIONS = Number(__ENV.ITERATIONS || '5');
const PAUSE_SECONDS = Number(__ENV.PAUSE_SECONDS || '5');
// 환경 변수는 docker inspect에 남으므로 토큰은 파일로만 넘기도록 했다.
const TOKEN = open(__ENV.WEB_ACCESS_TOKEN_FILE).trim();

if (!Number.isInteger(ITERATIONS) || ITERATIONS < 1 || ITERATIONS > 20) throw new Error('ITERATIONS must be 1..20');
if (!(PAUSE_SECONDS >= 1)) throw new Error('PAUSE_SECONDS must be at least 1');

export const options = {
  scenarios: {
    path_history: { executor: 'per-vu-iterations', vus: 1, iterations: ITERATIONS, maxDuration: '10m' },
  },
};

export default function () {
  const startedAt = Date.now();
  const res = http.post(
    `${BASE_URL}/api/incidents/${INCIDENT_ID}/board/search-paths/segments/query`,
    // 캐시가 없을 때 상황판에서 나가는 첫 segments 요청 본문과 같다(createSearchPathPageRequest).
    JSON.stringify({ opIds: [OP_ID], paths: [], nextSearchPathId: null }),
    {
      headers: {
        Authorization: `Bearer ${TOKEN}`,
        'X-Client-Channel': 'WEB',
        'Content-Type': 'application/json',
        Accept: 'application/json',
        // 브라우저 요청의 Accept-Encoding과 맞추지 않고 gzip과 br만 넣었다. 압축 응답은 k6에서 풀려 body에 담긴다.
        'Accept-Encoding': 'gzip, br',
      },
      timeout: '60s',
      tags: { name: 'segments_query' },
    },
  );
  check(res, { 'status is 200': (r) => r.status === 200 });
  // Ops 시계의 시각이다. 브라우저나 App 서버의 시각과 바로 빼지 않는다.
  console.log(JSON.stringify({
    kind: 'path_history_request',
    startedAtMs: startedAt,
    status: res.status,
    protocol: res.proto,
    waitingMs: res.timings.waiting,
    receivingMs: res.timings.receiving,
    durationMs: res.timings.duration,
    bodyCharacters: res.body ? res.body.length : 0,
    contentEncoding: res.headers['Content-Encoding'] || null,
    cfRay: res.headers['Cf-Ray'] || null,
  }));
  sleep(PAUSE_SECONDS);
}
