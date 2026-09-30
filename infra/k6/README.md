# Search-path load test

`api-smoke.js`는 인증, 일반 API와 SSE 연결을 적은 부하로 확인한다. GPS 경로 저장 부하는 `search-path-batch-load.js`로 측정한다.

## 시나리오

| 시나리오                 | 확인할 내용                                | 부하 모델                                     |
| ------------------------ | ------------------------------------------ | --------------------------------------------- |
| `periodic`               | 업무폰별 주기적 전송                       | 업무폰마다 VU 하나                            |
| `backlog-drain`          | 오프라인 좌표 묶음 재전송                  | 업무폰마다 요청 120건을 순차 전송             |
| `recovery-catch-up`      | 재전송 중 새 요청 유입                     | 업무폰마다 밀린 요청 뒤에 새 요청을 순차 전송 |
| `breakpoint`             | 현재 서버의 기술적 한계                    | `constant-arrival-rate`                       |
| `prepopulated-long-path` | 기존 경로 길이가 append 비용에 미치는 영향 | `constant-arrival-rate`                       |

앞의 세 시나리오는 같은 업무폰이 앞 요청의 응답을 받은 뒤 다음 요청을 보낸다. 뒤의 두 시나리오는 실제 업무폰 흐름이 아니라 고정된 요청 시작률로 서버 한계와 경로 길이의 영향을 측정한다.

## 공통 준비

Fixture는 App 서버에서 준비한다.

```bash
python3 prepare-search-path-batch-fixtures.py
```

기본 실행은 부하 시험 전용 계정, 업무폰, 사건 배정, DutyShift와 `RECORDING` 경로를 468개 만든다. Keycloak 사용자와 client는 access token을 발급한 뒤 삭제한다. 준비에 실패하면 Fixture 파일을 결과로 사용하지 않는다.

Fixture 파일은 `/srv/apps/suri-map/load-test/path-append-requester-fixtures.json`에 권한 `600`으로 저장된다. 부하 시험 데이터는 `a1000000`부터 `e1000000`까지의 전용 ID 접두사를 사용하며 하네스 Fixture와 기존 데이터는 건드리지 않는다.

각 성능 실행 전 준비 스크립트를 다시 실행한다. 스크립트가 이전 부하 시험의 경로와 멱등성 기록을 지운 뒤 같은 논리 상태를 만든다.

다음 두 파일을 Ops 서버에 복사한다.

- `search-path-batch-load.js`
- `path-append-requester-fixtures.json`

Ops 서버에서는 소유자 `root:12345`, 권한 `640`으로 두고 Fixture를 `/srv/ops/k6/secrets/path-append-requester-fixtures.json`에 저장한다.

준비 완료 조건은 준비 스크립트가 성공하고, 선택한 수만큼의 requester Fixture를 k6 컨테이너가 읽을 수 있는 상태다.

## 공통 실행

시나리오 절에서 `RUN_ID`, `RESULT_BASENAME`, `SCENARIO_ARGS`를 먼저 설정한 뒤 같은 Bash shell에서 다음 명령을 실행한다.

```bash
install -d -m 750 /srv/ops/k6/results
set -o pipefail
{
  echo "run_id=${RUN_ID}"
  printf "scenario_args="
  printf "%q " "${SCENARIO_ARGS[@]}"
  printf "\n"
  docker run --rm \
    --network host \
    -v /srv/ops/k6/search-path-batch-load.js:/scripts/search-path-batch-load.js:ro \
    -v /srv/ops/k6/secrets/path-append-requester-fixtures.json:/secrets/path-append-requester-fixtures.json:ro \
    -e RUN_ID="${RUN_ID}" \
    "${SCENARIO_ARGS[@]}" \
    -e K6_PROMETHEUS_RW_SERVER_URL=http://127.0.0.1:9090/api/v1/write \
    -e "K6_PROMETHEUS_RW_TREND_STATS=p(50),p(95),p(99),min,max" \
    grafana/k6@sha256:e7eeddf1ce2361df6920d925297f487c0ba549c44be242c6a9c22f28d9b08efa \
    run --quiet --no-color --log-output=none \
    -o experimental-prometheus-rw \
    /scripts/search-path-batch-load.js
} 2>&1 | tee "/srv/ops/k6/results/${RESULT_BASENAME}-k6.log"
```

k6는 App 서버 사설 IP로 연결하되 TLS 호스트 이름은 `suri-map.sonic8-8.com`으로 유지한다. iteration 하나는 2.5초 간격 GPS 좌표 6개를 `POST /api/search-paths/batch` 한 건으로 전송한다. HTTP timeout 10초는 Android 네트워크 기본값에 맞춘 시험 상한이며 SLA가 아니다.

유효 실행은 다음 조건을 모두 만족한다.

- status `200`, 좌표 6개 수락, 제외 좌표 0개 검사가 모두 통과한다.
- `constant-arrival-rate` 시나리오는 `dropped_iterations=0`이다.
- Ops 서버 CPU·메모리·네트워크가 먼저 포화되지 않는다.
- DB 검증 대상 시나리오는 `result=PASS`다.

`P95_MS`를 명시한 Android Outbox 시나리오만 응답 시간을 합격 기준으로 사용한다. 나머지 실행은 p50·p95·p99·최댓값을 측정값으로 기록한다.

다음 실행은 Backend 응답 시간과 HikariCP 대기 요청 수가 시험 전 수준으로 돌아온 뒤 시작한다.

## Android Outbox 시나리오

### 주기적 전송

Smoke Test는 업무폰 한 대로 30초 동안 실행한다.

```bash
RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)"
RESULT_BASENAME="search-path-batch-periodic-smoke-${RUN_ID}"
SCENARIO_ARGS=(
  -e SCENARIO=periodic
  -e POLICE_PHONE_COUNT=1
  -e PERIODIC_DURATION=30s
  -e SCENARIO_START_EPOCH_MS="$(date +%s)000"
)
```

Smoke Test 통과 후 새 Fixture로 `POLICE_PHONE_COUNT=468`, `PERIODIC_DURATION=5m`을 실행한다. 업무폰 468대가 15초마다 요청하면 명목 요청률은 `31.2 RPS`다.

### 오프라인 재전송

```bash
RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)"
RESULT_BASENAME="search-path-batch-backlog-drain-${RUN_ID}"
SCENARIO_ARGS=(
  -e SCENARIO=backlog-drain
  -e POLICE_PHONE_COUNT=468
  -e BACKLOG_BATCH_COUNT_PER_POLICE_PHONE=120
  -e BACKLOG_DRAIN_MAX_DURATION=5m
  -e SCENARIO_START_EPOCH_MS="$(date +%s)000"
)
```

총 요청은 `468 × 120 = 56,160건`이다. 이 시나리오는 k6 summary의 `iterations=56,160`일 때 완료된다.

### 복구 중 새 요청 유입

Smoke Test는 업무폰 한 대에 재전송 요청 2건과 새 요청 2건을 사용한다.

```bash
RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)"
RESULT_BASENAME="search-path-batch-recovery-catch-up-smoke-${RUN_ID}"
SCENARIO_ARGS=(
  -e SCENARIO=recovery-catch-up
  -e POLICE_PHONE_COUNT=1
  -e BACKLOG_BATCH_COUNT_PER_POLICE_PHONE=2
  -e RECOVERY_NEW_BATCH_COUNT_PER_POLICE_PHONE=2
  -e RECOVERY_CATCH_UP_MAX_DURATION=45s
  -e MIXED_REPLAY_TARGET_MS=30000
  -e SCENARIO_START_EPOCH_MS="$(date +%s)000"
)
```

Smoke Test 통과 후 새 Fixture로 다음 본시험 값을 사용한다.

```bash
RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)"
RESULT_BASENAME="search-path-batch-recovery-catch-up-${RUN_ID}"
SCENARIO_ARGS=(
  -e SCENARIO=recovery-catch-up
  -e POLICE_PHONE_COUNT=468
  -e BACKLOG_BATCH_COUNT_PER_POLICE_PHONE=120
  -e RECOVERY_NEW_BATCH_COUNT_PER_POLICE_PHONE=20
  -e RECOVERY_CATCH_UP_MAX_DURATION=5m30s
  -e MIXED_REPLAY_TARGET_MS=300000
  -e SCENARIO_START_EPOCH_MS="$(date +%s)000"
)
```

각 업무폰은 재전송 요청 120건 뒤에 15초마다 생기는 새 요청 20건을 처리한다. `iterations=65,520`이고 재전송 처리 시간이 5분 미만일 때 완료된다.

## 미리 채운 긴 경로 시험

`prepopulated-long-path`는 같은 `31 RPS`에서 초기 경로 길이만 바꾸어 비교한다.

| 초기 경로 | 경로당 좌표 | 경로당 구간 | 시작 version |
| --------- | ----------: | ----------: | -----------: |
| `30m`     |         720 |         120 |          121 |
| `8h`      |      11,520 |       1,920 |        1,921 |
| `24h`     |      34,560 |       5,760 |        5,761 |

각 경로는 2.5초 간격 좌표와 과거 요청 한 건당 `FOOT` 구간 하나를 가진다. 준비 스크립트는 전체 geometry도 만들고 관련 테이블을 `ANALYZE`한다.

경로 길이마다 다음 순서로 실행한다.

1. App 서버에서 Smoke Test Fixture 10개를 준비한다.

   ```bash
   python3 prepare-search-path-batch-fixtures.py \
     --police-phone-count 10 \
     --prepopulated-path-duration 30m
   ```

   `30m`은 현재 확인할 `8h` 또는 `24h`로 바꾼다. 준비 스크립트가 성공하면 이 단계가 완료된다.

2. Fixture를 Ops 서버에 복사하고 `1 RPS × 5초` Smoke Test를 실행한다.

   ```bash
   PATH_DURATION=30m
   RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)"
   RESULT_BASENAME="search-path-batch-prepopulated-long-path-${PATH_DURATION}-smoke-${RUN_ID}"
   SCENARIO_ARGS=(
     -e SCENARIO=prepopulated-long-path
     -e POLICE_PHONE_COUNT=10
     -e PREPOPULATED_LONG_PATH_REQUEST_RATE=1
     -e PREPOPULATED_LONG_PATH_TEST_DURATION=5s
   )
   ```

   모든 검사 통과와 `dropped_iterations=0`이면 Smoke Test가 완료된다. 성능 근거는 본시험 결과만 사용한다.

3. App 서버에서 같은 경로 길이의 본시험 Fixture 468개를 새로 준비한다.

   ```bash
   python3 prepare-search-path-batch-fixtures.py \
     --police-phone-count 468 \
     --prepopulated-path-duration 30m
   ```

   준비 스크립트가 성공하고 새 Fixture가 Ops 서버에 복사되면 이 단계가 완료된다.

4. `31 RPS × 5분` 본시험을 실행한다.

   ```bash
   PATH_DURATION=30m
   RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)"
   RESULT_BASENAME="search-path-batch-prepopulated-long-path-${PATH_DURATION}-31rps-5m-${RUN_ID}"
   SCENARIO_ARGS=(
     -e SCENARIO=prepopulated-long-path
     -e POLICE_PHONE_COUNT=468
     -e PREPOPULATED_LONG_PATH_REQUEST_RATE=31
     -e PREPOPULATED_LONG_PATH_TEST_DURATION=5m
   )
   ```

   공통 유효 실행 조건과 DB 검증을 모두 만족하면 한 번의 본시험이 완료된다.

5. 새 Fixture로 같은 본시험을 한 번 더 실행한다. 두 실행의 결론이 다르면 평균내지 않고 원인을 확인한 뒤 다시 실행한다.

Grafana에서는 공통 지표와 다음 패널을 함께 확인한다.

- `k6 응답 대기·수신 시간`: 첫 응답 byte까지 기다린 시간과 응답 body 수신 시간을 분리한다.
- `k6 수신 데이터`: 이번 요청에서 생성된 구간만 반환하는 응답 크기가 경로 길이에 따라 증가하지 않는지 확인한다.
- `경로 테이블 행 처리량`: 새 좌표·구간 삽입과 수색 경로 도형 갱신 규모를 확인한다.

첫 1분과 이후 4분의 추세를 비교한다. 경로 길이 3개가 각각 두 번의 유효 실행을 가지고, 길이에 따른 응답 시간과 PostgreSQL 작업량의 관계를 설명할 수 있을 때 이 시험이 완료된다.

## 서버 Breakpoint Test

`breakpoint`는 RPS별 독립 실행으로 현재 서버의 기술적 한계를 찾는다.

Smoke Test는 requester Fixture 한 개와 `1 RPS × 30초`를 사용한다.

```bash
RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)"
RESULT_BASENAME="search-path-batch-breakpoint-1rps-30s-${RUN_ID}"
SCENARIO_ARGS=(
  -e SCENARIO=breakpoint
  -e POLICE_PHONE_COUNT=1
  -e BREAKPOINT_RATE=1
  -e BREAKPOINT_DURATION=30s
)
```

Smoke Test 통과 후 각 RPS 실행 전에 requester Fixture를 다시 준비한다. `POLICE_PHONE_COUNT`는 준비한 requester 수와 같게 설정한다. `dropped_iterations`가 생기면 requester 수와 `POLICE_PHONE_COUNT`를 함께 늘려 같은 RPS를 다시 실행한다.

경계 탐색 순서는 다음과 같다.

1. `220 RPS`부터 `220 RPS`씩 높이며 각 값을 1분 동안 실행한다.
2. 첫 실패 RPS가 나오면 같은 값에서 한 번 더 실행한다.
3. 두 번 실패하면 마지막 성공 RPS와 첫 실패 RPS의 정수 중간값을 실행한다.
4. 성공값과 실패값 차이가 `1 RPS`가 될 때까지 반복한다.
5. 마지막 성공 RPS를 새 Fixture로 5분 동안 실행한다. 실패하면 성공값을 낮추고 다시 좁힌다.

HTTP 요청 검사가 실패하고 부하 실행기는 유효 실행 조건을 만족하면 서버 성능 실패다. 마지막 성공 RPS의 5분 실행이 유효하고 DB 검증이 `PASS`일 때 Breakpoint Test가 완료된다.

기존 측정 조건과 결과는 [`issue-8-server-breakpoint-test.md`](../../docs/test-results/backend/issue-8-server-breakpoint-test.md)에 보존한다.

## Arrival-rate DB 검증

성공한 `breakpoint`와 `prepopulated-long-path` 실행은 App 서버에서 검증한다. `EXPECTED_REQUEST_COUNT`에는 목표 RPS와 시간을 곱한 값이 아니라 k6 summary의 실제 `iterations`를 입력한다.

- `breakpoint`: `PHASE=breakpoint`
- `prepopulated-long-path`: `PHASE=prepopulated-long-path`

```bash
: "${RUN_ID:?set RUN_ID from the k6 run}"
: "${PHASE:?set PHASE to breakpoint or prepopulated-long-path}"
: "${EXPECTED_REQUEST_COUNT:?set EXPECTED_REQUEST_COUNT from k6 iterations}"
: "${RESULT_BASENAME:?reuse the k6 RESULT_BASENAME}"

install -d -m 750 /srv/apps/suri-map/load-test/results
set -o pipefail
docker exec -i \
  -e VERIFY_RUN_ID="${RUN_ID}" \
  -e VERIFY_PHASE="${PHASE}" \
  -e VERIFY_EXPECTED_REQUESTS="${EXPECTED_REQUEST_COUNT}" \
  suri-map-postgis \
  sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -v run_id="$VERIFY_RUN_ID" -v phase="$VERIFY_PHASE" -v expected_requests="$VERIFY_EXPECTED_REQUESTS"' <<'SQL' 2>&1 \
  | tee "/srv/apps/suri-map/load-test/results/${RESULT_BASENAME}-db.log"
WITH point_counts AS (
  SELECT
    COUNT(*) AS point_count,
    COUNT(DISTINCT point_id) AS distinct_point_count
  FROM search_path_gps_point
  WHERE point_id LIKE 'load-' || :'run_id' || '-' || :'phase' || '-%'
), excluded_counts AS (
  SELECT COUNT(*) AS excluded_point_count
  FROM search_path_excluded_point
  WHERE point_id LIKE 'load-' || :'run_id' || '-' || :'phase' || '-%'
), idempotency_counts AS (
  SELECT
    COUNT(*) AS idempotency_count,
    COUNT(DISTINCT idempotency_key) AS distinct_idempotency_count,
    COUNT(*) FILTER (WHERE idempotency_status = 'COMPLETED') AS completed_count
  FROM idempotency_record
  WHERE idempotency_key LIKE 'load-test-' || :'run_id' || '-' || :'phase' || '-%'
    AND request_path = '/api/search-paths/batch'
)
SELECT
  :'expected_requests'::bigint AS expected_requests,
  :'expected_requests'::bigint * 6 AS expected_points,
  point_count,
  distinct_point_count,
  excluded_point_count,
  idempotency_count,
  distinct_idempotency_count,
  completed_count,
  point_count = :'expected_requests'::bigint * 6
    AND distinct_point_count = point_count
    AND excluded_point_count = 0
    AND idempotency_count = :'expected_requests'::bigint
    AND distinct_idempotency_count = idempotency_count
    AND completed_count = idempotency_count AS valid
FROM point_counts
CROSS JOIN excluded_counts
CROSS JOIN idempotency_counts
\gset verification_

\echo expected_requests=:verification_expected_requests expected_points=:verification_expected_points
\echo point_count=:verification_point_count distinct_point_count=:verification_distinct_point_count excluded_point_count=:verification_excluded_point_count
\echo idempotency_count=:verification_idempotency_count distinct_idempotency_count=:verification_distinct_idempotency_count completed_count=:verification_completed_count
\if :verification_valid
  \echo result=PASS
\else
  \echo result=FAIL
  \quit 1
\endif
SQL
```

검증 명령이 `result=PASS`로 끝나고 `-db.log`가 저장되면 DB 검증이 완료된다.

## 상황판 반영 시간과 읽기 부하 — 조건 결정 중

현재 합의한 조건만 기록한다. 계측과 실행 코드는 나머지 조건을 정한 뒤 작성한다.

- 부하 규모는 [동시 상황판 수](../../CONTEXT.md#language)로 표현한다. 계정 수와 실제 SSE 연결 수는 별도로 기록한다.
- 사건 하나의 업무폰 468대에 해당하는 경로 각각에 30분·8시간·24시간 분량을 미리 채워 비교한다. 비교하는 실행의 동시 상황판 수와 쓰기 부하는 같게 맞춘다.

경로 길이는 기존 긴 경로 시험의 입력을 재사용한 값이며 k6의 공식 권장 수치가 아니다. 누적 경로가 길어질 때 조회와 지도 표시 비용이 어떻게 달라지는지 확인하기 위해 세 조건을 비교한다.

## 결과 보존과 정리

k6 stdout 요약은 Ops 서버의 `-k6.log`, DB 검증은 App 서버의 `-db.log`에 남긴다. 상세 시계열은 Prometheus와 Grafana에서 확인한다. 원시 JSON은 저장하지 않는다.

최종 결과는 조건, k6 요약, DB 검증과 Grafana 화면을 함께 보존한다. Grafana 화면을 만들 수 없으면 실행 구간의 Prometheus 값과 원본 로그 SHA-256을 기록한다.

시험 Fixture는 App 서버에서 정리한다.

```bash
python3 prepare-search-path-batch-fixtures.py --cleanup
```

정리 명령이 성공하고 Ops 서버의 requester token 파일을 제거하면 정리가 완료된다.
