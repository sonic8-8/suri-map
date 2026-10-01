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

## 상황판 반영 시간과 읽기 부하 — 구현 중

합의한 시험 조건과 구현 진척을 기록한다. 전체 실행기와 브라우저 계측은 아직 연결 중이다.

- 부하 규모는 [동시 상황판 수](../../CONTEXT.md#language)로 표현한다. 계정 수와 실제 SSE 연결 수는 별도로 기록한다.
- 사건 하나의 업무폰 468대에 해당하는 경로 각각에 30분·8시간·24시간 분량을 미리 채워 비교한다. 비교하는 실행의 동시 상황판 수와 쓰기 부하는 같게 맞춘다.
- 다수 상황판의 부하는 HTTP·SSE 통신으로 재현하고, 실제 브라우저를 함께 실행해 지도 반영을 관찰한다. 실제 브라우저를 제외한 통신 부하만으로 지도 표시 성공을 판정하지 않는다. 현재 화면당 사건 SSE 2개와 이벤트에 따른 재조회 흐름을 대조해 재현하며, 이벤트마다 조회 요청이 정확히 1개 발생한다고 가정하지 않는다.
- GPS 좌표 묶음은 `constant-arrival-rate`로 초당 31건의 요청 시작을 목표로 한다. 기존 긴 경로 비교시험과 같은 입력을 유지해 상황판 수·경로 길이의 영향을 비교하기 위한 선택이다. 성공 처리량 31건/초를 보장하거나 실제 업무폰 전송 흐름을 재현한다는 뜻은 아니다. 468대가 각각 15초마다 전송할 때의 명목값 31.2 RPS와 구분한다.

- 첫 비교 범위는 동시 상황판 1 → 2 → 4 → 8개다. 모든 조건에 실제 관찰 브라우저 1개를 포함하고 나머지는 통신으로 재현한다. 예를 들어 총 8개는 실제 화면 1개와 모의 화면 7개다. 8개는 최초 시험 범위이며 실제 사용자 수나 서버 한계로 해석하지 않는다.
- 마커는 사건 전체에서 10초마다 1개를 생성한다. 업무폰별 빈도가 아니며, 마커의 알림·지도 반영을 반복 관찰하되 GPS 쓰기 부하에 추가되는 생성 요청을 작게 유지하려는 시험값이다. 현장 발생 빈도나 k6 공식 권장값이 아니다.
- 생성할 마커는 사진 없는 지원 요청(`SUPPORT_REQUEST`)으로 고정한다. 지도 갱신뿐 아니라 지휘 계정 대상 알림 저장·전달도 관찰한다. 사진 업로드 성능과 실제 업무폰의 FCM 수신은 이번 웹 시험의 검증 범위가 아니다. 실행 전 알림 수신 대상·활성 FCM 토큰을 확인하고 의도하지 않은 발송을 막을 수 없다면 실행하지 않는다. 기존 토큰 삭제나 제품 알림 로직 비활성화로 대신하지 않는다.
- 모든 상황판은 동일한 시험용 지휘 계정으로 접속한다. 계정 조건을 고정해 화면 수의 영향을 비교하며 결과에 계정 1개·상황판 최대 8개를 구분해 기록한다. 서로 다른 지휘관 8명의 사용을 검증한 것으로 해석하지 않는다.
- 통신 부하는 Hetzner Ops 서버에서 실행하고, 실제 브라우저는 로컬 환경에서 Hetzner App 서버에 접속한다. App 서버는 시험 대상이며 부하 발생기를 함께 실행하지 않는다. 브라우저 측 네트워크 지연을 포함한 관측값을 모든 사용자의 체감 시간으로 일반화하지 않는다.
- 상황판 시험의 부하 요청도 공개 HTTPS 주소 `https://suri-map.sonic8-8.com`으로 보낸다. 기존 GPS 시험의 사설 IP 연결 설정을 그대로 가져오지 않는다. 공개 접점은 같아도 Ops와 로컬 브라우저의 네트워크 지연은 다르다.
- 경로 길이·상황판 수 조합마다 5분씩 실행한다. 첫 1분과 이후 4분의 추세를 구분하되, 1분 뒤 반드시 안정화된다고 가정하거나 초기 오류를 제외하지 않는다. 3가지 경로 길이 × 4가지 화면 수의 실행 시간은 준비·정리 제외 총 60분이다. 마커는 조합당 약 30건이므로 드문 지연이나 장시간 안정성까지 입증한 것으로 보지 않는다.
- 저장·상황판 조회 실패, 데이터 불일치 또는 요청 시작 누락(`dropped_iterations`)을 발견하면 현재 구간을 중단하고 원인을 확인한다. 실패 조건을 유지한 채 다음 화면 수로 자동 진행하지 않는다. 이 중단은 서버 한계 확정이나 성공률 기준이 아니라 진단을 위한 절차다.

2026-10-01 위 시험 조건과 구현 순서를 합의했다. 계정·데이터 준비와 정리 → 단계별 계측 → 상황판 통신 부하·브라우저 관찰 → 상황판 1개 Smoke 순서로 진행한다. 본 부하 시험은 Smoke 결과를 확인한 뒤 실행한다. 지휘 계정의 실제 로그인·상황판 조회를 확인했다. 마커 요청 ID 준비·정리와 FCM 안전 확인 도구는 추가했으며, 전체 실행기 연결·단계별 계측·새 경로/마커 반영 Smoke·부하 실행은 완료하지 않았다.

경로 길이는 기존 긴 경로 시험의 입력을 재사용한 값이며 k6의 공식 권장 수치가 아니다. 누적 경로가 길어질 때 조회와 지도 표시 비용이 어떻게 달라지는지 확인하기 위해 세 조건을 비교한다.

### 서버 단계별 계측

`DatabaseEventPublisher`와 `ServerSentEventJobWorker`의 Lombok `@Slf4j`로 이벤트 ID별 관측 로그를 남긴다. 기본 INFO 설정에서는 계측 로그를 출력하지 않는다. 시험 배포에서 다음 두 클래스만 DEBUG로 설정하면 아래 단계를 기록한다. 기존 INFO·WARN 진단 로그는 유지한다.

```properties
logging.level.com.surimap.global.event.DatabaseEventPublisher=DEBUG
logging.level.com.surimap.global.sse.ServerSentEventJobWorker=DEBUG
```

Compose 환경에서는 [측정용 override](board-measurement.compose.yml)를 runtime Compose 뒤에 추가한다. 두 클래스의 이름을 logging group으로 묶어 환경변수의 대소문자 제약을 피한다. 적용 전에 병합 설정에서 두 환경변수 외에 바뀌는 값이 없는지 확인하고, `up -d --no-deps --pull never backend`로 Backend만 재생성한다. 실행 중인 시험에는 적용하지 않는다. 측정 후 override 없이 같은 명령을 실행해 DEBUG를 제거한다. 설정 방식은 [Spring Boot logging groups](https://docs.spring.io/spring-boot/3.5/reference/features/logging.html#features.logging.log-groups)를 따른다.

2026-10-01 실제 서버에 계측 코드를 배포하고 이 설정을 적용했다가 원복했다. 쓰기 전 상황판 사전 검사에서 커넥션 풀 대기 오류를 발견해 GPS·마커 쓰기는 시작하지 않았다. 따라서 실제 쓰기의 단계별 로그 연결은 미검증이다.

| `stage` | 기록하는 시점 |
|---|---|
| `outbox_insert_returned` | 이벤트 전송 작업 INSERT가 반환됨. 아직 롤백될 수 있다. |
| `transaction_after_commit` | 업무 트랜잭션의 커밋 후 콜백이 호출됨. DB의 정확한 커밋 시각은 아니다. |
| `dispatch_started` | worker가 해당 작업의 순번 확정·실시간 전달을 시작함. |
| `live_dispatch_returned` | 연결별 전달 호출이 반환됨. 연결이 없을 수도 있으며 소켓 쓰기·브라우저 수신 완료를 뜻하지 않는다. |
| `dispatch_job_completed` | 전송 작업 완료 상태 저장이 반환됨. |
| `dispatch_failed` | worker 처리 중 예외가 발생함. |
| `no_transaction_synchronization` | 트랜잭션 동기화 없이 발행됨. 커밋 후 관측값으로 취급하지 않는다. |

로그에는 `eventId`·`jobId`·`wallTimeMs`·`monotonicNs`만 담고 좌표·토큰·이벤트 본문은 담지 않는다. 시간차 계산에는 같은 JVM 실행의 `monotonicNs`를 사용한다. 재시작 전후 값이나 서버·브라우저의 시각을 그대로 빼지 않는다. `wallTimeMs`는 로그를 대조하는 시각이다.

worker의 주기 조회가 커밋 후 콜백보다 먼저 작업을 발견할 수도 있다. 따라서 `transaction_after_commit`과 `dispatch_started`의 차이를 정확한 대기 시간으로 단정하거나 음수를 0으로 바꾸지 않는다. 로그 비용도 포함되므로 비교하는 조건에서는 같은 계측 설정을 유지한다. 아래 브라우저 계측과 서버 기록을 실제 입력별로 연결하는 작업은 남아 있다.

검증(2026-10-01): 기존 발행기 테스트에 커밋·롤백 콜백별 관측 로그 검사를 추가했다. 현재 소스를 연결한 Docker 환경의 Backend 전체 1,351개가 통과했다(실패·오류·건너뜀 0, 기본 성능 태그 제외). WSL 직접 실행에서는 HTTP 연결·인증 검사 11개가 실패했으므로 해당 실행을 통과로 집계하지 않는다. 실제 서버 계측·브라우저 반영 시간·부하 성능은 아직 검증하지 않았다.

### 브라우저 단계별 계측

시험 브라우저가 페이지 실행 전에 `window.__SURI_MAP_MEASUREMENT_ENABLED__ = true`를 설정하고 `suri-map:board-measurement` 이벤트를 수집한다. 기본값은 꺼짐이며 제품에서 기록을 누적하거나 외부로 전송하지 않는다. 기존 계측은 배포됐지만 아래 시험 대상 제한 변경은 로컬 검증 단계다.

과거 경로·마커는 표시 여부만 확인한다. 전송 전에 받은 좌표는 비교 기준으로 보관하고, 실행기가 등록한 경로·마커 ID 중 새 좌표가 생긴 구간만 정밀 검사한다. 경로 버전만 달라진 과거 구간이나 같은 좌표의 반복 응답은 다시 정밀 검사하지 않는다. 정밀 검사를 생략한 과거 좌표를 성공으로 기록하지 않으며, 요청별 성공은 아래 대조 도구에서 ID·버전·좌표 해시로 따로 판단한다. 읽기 전용 실행은 대상을 등록하지 않아 정밀 좌표 검사도 하지 않는다.

| `stage` | 기록하는 시점 |
|---|---|
| `sse_received` | 지도 갱신 구독이 이벤트를 수신함. 이벤트 ID·순번·대상 ID와 중복 여부만 기록한다. 알림 전용 구독의 수신 기록은 아니다. |
| `board_read_started` / `board_read_completed` / `board_read_failed` | 실제 조회 호출 시작 / 본문 해석 완료 / 실패. 같은 `requestId`로 연결한다. 완료는 React 반영이나 지도 표시가 아니다. |
| `map_data_submitted` | 경로·마커 GeoJSON을 지도에 전달하기 직전. 시험용 `updateId`를 도형 속성에 덧붙이며 원본 객체·좌표는 바꾸지 않는다. |
| `map_features_rendered` | `render` 이후 조회한 도형에서 해당 `updateId`와 ID를 확인함. 이전 데이터나 DOM 버튼의 존재만으로 성공 처리하지 않는다. |
| `map_coordinates_checked` | 이번 시험의 새 구간 끝 좌표 6개 또는 새 마커 좌표 1개를 검사한다. SHA-256, 화면 범위 포함 여부, 각 화면 위치에서 같은 도형·갱신이 조회되는지를 기록한다. |
| `map_coordinate_check_failed` | 좌표 해시 계산 실패. 수집기는 실패로 처리하고 연결된 쓰기를 중단한다. |
| `map_update_replaced` / `map_update_unobserved_at_idle` / `map_update_cancelled` | 미관측 상태에서 다음 데이터로 대체됨 / 지도 idle까지 관측하지 못함 / 지도 제거로 관측 종료. 표시 성공으로 세지 않는다. |

브라우저 안의 시간차는 같은 `timeOriginMs`의 `elapsedMs`끼리 계산한다. 서버의 시각이나 Node 수집기의 수신 시각을 그대로 빼지 않는다. 좌표·토큰·마커 본문은 기록하지 않는다. 계측을 켠 경우에만 GeoJSON 속성 복사와 렌더링 결과 조회 비용이 추가되므로 비교 조건에서 동일하게 유지한다.

이 관측은 [MapLibre의 `render`](https://maplibre.org/maplibre-gl-js/docs/API/type-aliases/MapEventType/)와 [`queryRenderedFeatures`](https://maplibre.org/maplibre-gl-js/docs/API/classes/Map/)를 사용한다. `map_features_rendered`만으로는 경로 일부의 관측일 뿐이다. 좌표 검사는 원본 GeoJSON의 마지막 6개 좌표를 전송한 묶음과 대조하고, `project()`로 얻은 각 화면 위치에서 같은 갱신의 도형을 조회한다. 렌더링 결과의 좌표는 타일 분할·단순화가 적용될 수 있으므로 원본 좌표와 직접 비교하지 않는다.

이 검사도 물리 화면의 픽셀·사람의 식별 가능성을 보증하지 않는다. MapLibre 조회에는 투명 도형이 포함될 수 있고 다른 DOM 패널에 가려진 상태까지 판단하지 않는다. 시험에서는 기존 가시 레이어 설정을 유지하고 캡처로 함께 확인한다. 화면 밖·숨김·필터·`idle` 미관측을 서버 오류나 데이터 유실로 단정하지 않는다. 카메라를 자동으로 이동하거나 시험 좌표·제품 필터를 변경하지 않는다.

해시는 `JSON.stringify([[경도, 위도], ...])`의 UTF-8 바이트를 SHA-256으로 계산한다. 좌표를 임의 반올림하지 않는다. `observedAtMs`는 위치별 도형 조회 시각이며 이벤트의 `elapsedMs`는 비동기 해시 계산 이후 기록 시각이다. 수집 종료 전 해시 계산과 파일 기록을 기다린다. 해시도 익명화 보장은 아니므로 결과 파일의 비공개 권한을 유지한다. 좌표별 조회·해시 계산 비용은 계측을 켠 시험에만 추가되며 성능 비교에서 동일하게 유지한다.

SSE 수신마다 조회가 새로 시작되거나 조회 응답마다 지도 갱신이 하나씩 생긴다고 가정하지 않는다. 시험 마커 ID·경로/구간 ID·버전·좌표 해시로 아래 대조 도구에서 연결한다. 응답의 `latestEventId`를 실제 SSE UUID라고 가정하거나 가까운 시각끼리 짝지어 전체 반영 시간을 만들지 않는다.

관측 기록은 다음 필드로 대조한다. 전송 순번과 데이터 버전은 서로 다른 값이다.

| 단계 | 연결할 필드 |
|---|---|
| GPS 저장 응답 | `id`·`version`은 저장된 경로 ID·버전이다. `requestId`로 요청 시작·결과를 연결하고 시작 기록의 `coordinateHash`·`pointCount`를 대조한다. |
| `sse_received` | `eventId`로 서버 로그와 대조한다. `sourceEntityType`·`sourceEntityId`·`sourceVersion`은 원본 데이터, `sequence`는 SSE 전송 순번이다. 버전이 없는 이벤트는 `null`로 남긴다. |
| `board_read_completed` | `rows`의 `slot`·`id`·`version`으로 조회한 경로·마커를 구분한다. |
| `map_data_submitted` | `entities[].id`는 지도 도형 ID다. 경로의 경우 구간 ID일 수 있으며 `sourceEntityId`·`sourceVersion`에 원본 경로 ID·버전을 따로 남긴다. 마커는 원본 마커 ID·버전을 유지한다. |
| `map_features_rendered` | 같은 페이지의 `timeOriginMs`·같은 source의 `updateId`로 제출 기록을 찾고 `entityIds`를 대조한다. |

원본 ID·버전이 없으면 구간 ID나 임의 버전으로 보완하지 않고 `null`로 남긴다. 동일 버전이 여러 조회에 포함되면 특정 조회 하나가 해당 렌더링을 일으켰다고 단정하지 않는다. 버전 일치는 좌표 일치나 새 좌표의 화면 내 표시까지 증명하지 않는다. 예상 좌표·화면 범위 대조는 별도다.

### 브라우저 기록 파일 수집

[observe-situation-board.cjs](observe-situation-board.cjs)는 전용 지휘 계정으로 로그인한 뒤 상황판을 읽고 계측 이벤트를 파일에 저장한다. GPS·마커를 생성하지 않으며 서비스 API 쓰기 요청은 차단한다. 현재 로컬에 설치된 Puppeteer·Chromium을 재사용한다. 서버에 위 계측 코드를 배포해야 준비 확인을 통과할 수 있다.

저장소 루트에서 실행한다. 계정 파일은 아래 준비 도구가 만든 비공개 파일이며 출력 디렉터리는 아직 없어야 한다.

```bash
node infra/k6/observe-situation-board.cjs ACCOUNT.json _workspace/board-observation-new 300
node infra/k6/observe-situation-board.cjs --self-check
```

`300`은 합의한 5분 관찰 시간이다. 접속 대상은 공개 HTTPS 주소와 시험 사건으로 고정한다. 출력 디렉터리는 권한 `700`, 다음 파일은 `600`으로 생성한다.

- `browser-measurements.jsonl`: 허용한 단계·식별자·시각만 저장한다. 좌표·토큰·본문은 제외한다.
- `ready.json`: 현재 페이지의 조회·지도 관측과 SSE 연결 2개를 확인한 시점이다. 이후에도 정상이라는 보장은 아니다.
- `board-before.png`, `board-after.png`: 준비 완료 뒤와 종료 전의 상황판 화면이다. 준비 실패·브라우저 무응답이면 생성되지 않을 수 있다. 화면의 인물·위치 정보가 포함될 수 있으므로 원본을 바로 공개하지 않는다.
- `result.json`: 기록 완료는 `COLLECTED`, 조회·수집 실패·관찰 중 페이지 이동·SSE 연결 종료는 `FAILED`다. 새 경로·마커 반영을 검증한 `PASS`가 아니며 `newDataVerified`는 `false`다.

준비 제한 45초는 기존 로그인 검증 도구의 대기 한도이고 제품 지연 기준이 아니다. 미저장 기록이 1,024개 쌓이면 조용히 버리지 않고 실패로 중단한다. 이 수치도 서버 용량 기준이 아닌 수집기 메모리 보호 한도다. 파일 쓰기 비용을 포함하므로 비교할 때 같은 수집 설정을 유지한다.

GPS·마커 쓰기와 전송 전 FCM 검사, 요청 좌표 해시·화면 범위 대조를 연결했다. 모의 상황판 추가와 실제 시험의 캡처·지도 범위 확인은 남아 있다. 특히 경로 SSE는 수색 경로 ID, 지도 도형은 구간 ID를 사용하므로 같은 ID라고 가정해 연결하지 않는다.

검증(2026-10-01): 자체 검사에서 파일 저장·민감 필드 제외·파일 권한·조회 실패·대기 한도 초과 처리를 확인했다. 로컬 Chromium/MapLibre의 합성 경로 2회 렌더링 기록도 실제 수집기를 거쳐 파일에 저장됐다. 이전 도형·숨김 레이어의 성공 오인은 각각 0회였다. 실제 로그인부터 새 좌표·마커 반영까지의 Smoke 및 부하 검증은 아니다.

식별 정보 보강 후 Frontend 타입 검사·빌드·56개 파일의 269개 테스트가 통과했다. 실제 로컬 Chromium에서 합성 경로 응답을 기존 mapper·도형 변환·MapLibre·파일 수집기로 처리해 구간 ID와 원본 경로 ID·버전의 연결을 확인했다. Backend·API 응답 형식·지도 좌표·필터·재조회 정책은 변경하지 않았다. 빌드의 500kB 초과 chunk 경고는 남아 있다. 서버 배포와 실제 쓰기 요청 대조는 하지 않았다.

### GPS·마커 쓰기와 브라우저 연동 중단

`search-path-batch-load.js`에 `BOARD_MEASUREMENT=true`를 지정하면 `prepopulated-long-path`의 요청 시작·결과를 JSON으로 기록한다. 이 모드에서는 사설 IP hosts 덮어쓰기를 제거한다. 기존 GPS 시험은 옵션을 켜지 않는 한 기존 연결·검사 방식을 유지한다.

- `path_write_started`: 실행 ID, 멱등성 키와 같은 요청 ID, 경로 ID, fixture·묶음 번호, 요청 직전 시각.
- `path_write_completed` / `path_write_failed`: 같은 요청 ID, HTTP 상태, 응답 경로 버전, HTTP 호출 반환 시각. 원문 응답·토큰·좌표는 출력하지 않는다.
- HTTP 200·좌표 6개 수락·제외 0개뿐 아니라 응답 경로 ID·양의 정수 버전도 검사한다. 실패하면 [k6의 `execution.test.abort()`](https://grafana.com/docs/k6/latest/javascript-api/k6-execution/#test)로 중단한다. 기존 drop threshold도 유지한다.

로그는 [기본 console 파일 출력](https://grafana.com/docs/k6/latest/using-k6/k6-options/reference/#console-output)을 사용한다. 요청별 ID를 Prometheus label로 만들지 않는다. 파일에는 Ops 시계의 `wallTimeMs`가 있으므로 브라우저·서버 시각과 바로 차감하지 않는다. 측정 시 로그 비용도 포함된다.

[run-situation-board-load.py](run-situation-board-load.py)는 Ops에서 k6 컨테이너 하나를 관리한다. Python은 k6 스크립트의 필수 구성 요소가 아니라 원격 시작·중단·안전 확인·결과 보존을 담당한다. 기존 고정 k6 이미지, 공개 HTTPS, 468개 fixture, GPS 31 RPS를 사용한다.

아래는 **계측 코드 배포·새 GPS fixture 준비 후** 사용하는 GPS 연결 명령이다. 아직 Hetzner에서 실행하지 않았다. 실행기와 GPS JS는 Ops의 `/srv/ops/k6/`에, 기존 requester fixture는 `secrets/`에 있어야 한다. `OPS_SSH_ALIAS`는 로컬 SSH 설정의 Ops 호스트 별칭이다.

```bash
node infra/k6/observe-situation-board.cjs \
  ACCOUNT.json _workspace/board-run-new 300 OPS_SSH_ALIAS NEW_RUN_ID
```

호스트·실행 ID를 생략하면 기존 읽기 전용 관찰이다. 지정하면 준비 확인 후 Ops의 GPS 쓰기를 시작한다. 새 실행 ID·출력 디렉터리를 사용하며 기존 파일·컨테이너를 덮어쓰지 않는다.

Frontend·로컬 관찰기·Ops 실행기는 같은 변경 버전으로 준비한다. Ops는 fixture의 경로 468개와 이번 실행에서 준비한 마커의 ID만 `coordinate_check_targets`로 보낸다. 토큰·좌표는 보내지 않는다. 브라우저가 실행 ID·개수·중복·같은 페이지·새 계측 코드 여부를 확인하고 대상을 등록한 뒤 `TARGETS_READY`를 보내야 컨테이너 시작 단계로 넘어간다. 등록 대기는 기존 준비 한도와 같은 45초이며 서비스 SLA가 아니다. 이전 관찰기의 `CONTINUE`만으로는 시작하지 않는다.

마커를 함께 전송하려면 마지막 인자로 **Ops에서 사용하는 App SSH 별칭**을 추가한다.

```bash
node infra/k6/observe-situation-board.cjs \
  ACCOUNT.json _workspace/board-run-new 300 OPS_SSH_ALIAS NEW_RUN_ID APP_SSH_ALIAS_ON_OPS
```

- Ops의 같은 디렉터리에 `situation-board-write-load.js`, `prepare-situation-board-marker-fixtures.py`, `prepare-situation-board-account.py`, `prepare-search-path-batch-fixtures.py`도 둔다. App의 안전 검사는 아래 제한된 SSH 경로를 사용한다. Ops→App의 비대화형 SSH가 필요하다.
- [situation-board-write-load.js](situation-board-write-load.js)는 기존 GPS 함수를 재사용하고 사진 없는 지원 요청을 10초마다 추가한다. 첫 업무폰의 GPS 시험 좌표를 사용한다. 마커 생성 ID·멱등성 키는 실행 전에 파일로 남기며 생성 응답의 ID·사건·차수·업무폰·버전 1·사진 없음이 일치해야 한다.
- 두 종류의 쓰기는 k6의 [여러 시나리오](https://grafana.com/docs/k6/latest/using-k6/scenarios/)로 함께 실행한다. 마커는 [constant-arrival-rate](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/constant-arrival-rate/) 1회/10초, VU 1개다. 빈도는 프로젝트 합의이지 k6 권장값이 아니다. 5분의 마지막 시점에 31번째 요청을 예약하지 않도록 마커 예약 종료만 1ms 앞당긴다. GPS 측정 시간은 그대로이며 이는 서비스 지연 기준이 아니다.
- 각 마커 요청 전에 Ops loopback의 일회성 승인 창구가 App의 `check-marker-safety`를 SSH로 실행한다. 첫 검사와 같은 Backend인지, 기존 mock FCM·활성 토큰 0개·열린 사건/차수인지 확인한다. 실패·검사 불가·중복 승인이면 마커를 보내지 않고 두 쓰기를 중단한다. 이 창구는 시험 중에만 존재하며 제품 API를 추가하지 않는다.
- SSH 검사와 HTTP 요청에는 각각 10초 제한을 둔다. 이는 무한 대기를 막는 도구 보호 한도이며 서비스 SLA가 아니다. 실제 마커 POST는 안전 검사 완료 후 시작하므로 검사 시간이 추가된다. `Ops marker safety check` 지표를 제품 API 응답 시간·RPS와 합산하지 않는다. VU 부족으로 예약이 누락되면 기존 drop threshold로 중단한다.

브라우저 검사에 통과한 동안 1초마다 `CONTINUE`를 보내고, Ops는 마지막 신호부터 5초가 지나거나 stdin 종료·`STOP`을 받으면 생성한 컨테이너를 중단한다. 5초는 네 번의 신호 누락을 허용하는 제어 연결 보호 한도이며 서비스 SLA·k6 공식 권장값이 아니다. Docker 중단 대기는 2초이고 명령 자체에도 제한이 있으므로 실제 중단 완료가 항상 5초 이내라는 보장은 없다. 이미 전송된 요청의 서버 처리를 롤백하지 않는다.

준비 중 쌓인 신호는 시작 허가로 사용하지 않는다. 컨테이너 시작 직전에 새 신호를 받고, 시작 명령에 걸린 시간도 같은 5초에 포함한다. 관찰을 시작한 뒤 SSE가 종료되거나 오류를 반환하면 재연결 여부와 관계없이 이번 시험을 실패로 남기고 쓰기 허용을 중단한다.

쓰기가 정상 종료돼도 브라우저 수집은 45초 더 유지한다. 마지막 요청의 반영 기록을 회수하기 위해 기존 준비 대기 한도를 재사용한 도구 설정이며, 복구 목표 시간이나 모든 요청의 반영을 보장하는 기준은 아니다. 이후 대조 도구에서 요청별 누락을 확인해야 한다.

로컬 SSH 프로세스 종료만으로 성공 처리하지 않는다. Ops의 `containerStopped: true` 결과를 확인할 수 없으면 `remote_writer_stop_unconfirmed`로 남기고 실제 컨테이너를 확인해야 한다. 시험 생성 컨테이너는 종료 확인 후 제거하며 DB 기록·결과 파일은 자동 삭제하지 않는다. GPS·마커는 같은 컨테이너에서 함께 중단한다. 모의 상황판 발생기는 아직 연결하지 않았다.

Ops 결과는 `/srv/ops/k6/results/board-<RUN_ID>/`의 `write-requests.jsonl`, `k6.log`, `writer-result.json`에 남는다. 마커 모드에서는 정리에 필요한 `marker-fixture.json`과 전송 전 검사 결과 `marker-safety.jsonl`도 보존한다. 디렉터리는 `700`, 파일은 `600`으로 제한한다. `COLLECTED`는 도구 실행·기록 완료이며 지도 반영 성공 판정이 아니다.

### 안전 검사 전용 SSH

Ops의 `suri-map-board-check` 별칭을 위 `APP_SSH_ALIAS_ON_OPS`로 사용한다. 이 키로는 안전 검사만 실행할 수 있으며 계정 준비·정리나 파일 복사는 할 수 없다.

- 개인키는 Ops의 `/root/.ssh/suri-map-board-check`에만 둔다(권한 `600`). 기존 관리자·Jenkins 키를 재사용하거나 복사하지 않는다.
- App의 `authorized_keys`에는 이 키에만 `from="178.105.112.254"`, `restrict`, 고정 `command`를 적용한다. [OpenSSH의 키별 제한](https://man.openbsd.org/sshd.8#AUTHORIZED_KEYS_FILE_FORMAT)으로 Ops IP 제한, PTY·포트·agent·X11 전달과 사용자 rc 실행 금지를 설정한다. SSH 데몬의 공용 설정은 바꾸지 않는다.
- 강제 명령은 `/usr/bin/python3 -I /usr/local/libexec/suri-map-board-check/prepare-situation-board-account.py ssh-check-marker-safety`다. 디렉터리는 root 소유 `700`, 두 Python 준비 도구는 root 소유 `600`으로 두며 Jenkins의 소스 동기화와 분리한다. 변경 시 관리자가 소스 해시와 검증 결과를 대조해 설치한다.
- 실행기가 보내는 기존 명령 문자열과 선택적 `--expected-backend`만 검사한다. 문자열에 적힌 `/srv/apps/suri-map/infra/k6/` 경로를 실행하거나 셸로 평가하지 않는다. 실제 실행은 위 root 전용 복사본의 안전 검사 함수다.
- Ops는 기존 관리용 SSH로 확인한 App 호스트 공개키를 전용 `known_hosts` 파일에 고정하고 `StrictHostKeyChecking=yes`, `IdentitiesOnly=yes`를 사용한다. 처음 보는 호스트 키를 자동 승인하지 않는다.

자체 검사는 `python3 -I infra/k6/prepare-situation-board-account.py self-check`로 실행한다. 실제 SSH 검증은 정상 검사·같은 Backend 확인·다른 Backend 거부와 일반 명령·추가 인자·SFTP·PTY·포트 전달 차단을 구분한다. 안전 검사 성공은 GPS·마커 쓰기나 지도 반영 시험 성공이 아니다.

시험을 끝내고 이 접속이 더 필요 없으면 App에서 `surimap-board-marker-safety` 키의 지문을 대조해 해당 한 줄만 제거한다. 그다음 Ops의 전용 키·공개키·known_hosts와 해당 Host 블록을 정리한다. 다른 키 변경을 덮을 수 있으므로 예전 `authorized_keys` 백업 전체를 그대로 복원하지 않는다.

### 요청과 지도 관측 기록 대조

```bash
node infra/k6/correlate-situation-board.cjs \
  WRITE_REQUESTS.jsonl BROWSER_MEASUREMENTS.jsonl NEW_REPORT.json
```

[대조 도구](correlate-situation-board.cjs)는 GPS·마커 요청을 대상으로 한다. 원본 종류·ID·버전으로 SSE·조회 기록을 찾고, 같은 페이지·source·updateId의 도형 렌더링과 연결한다. GPS는 `PATH_APPENDED`, 마커는 `MARKER_CREATED`를 대조하며 알림 SSE로 대신하지 않는다. 버전이 다르거나 렌더링 기록이 없으면 `UNRESOLVED`다. 같은 버전을 담은 조회가 여러 개면 후보를 모두 남기며 인과관계 하나를 임의 선택하지 않는다. 응답의 `latestEventId`나 가까운 시각으로 짝짓지 않는다.

결과는 다음처럼 구분한다.

- `UNRESOLVED`: 원본 종류·ID·버전으로 요청→SSE→조회→도형 관측을 연결하지 못했다.
- `IDENTIFIERS_MATCHED`: 식별 정보는 연결했지만 전송 좌표 해시·화면 범위·좌표별 도형 조회를 모두 확인하지 못했다.
- `COORDINATES_MATCHED`: 식별 정보와 전송 좌표 수·해시가 일치하며 모든 대상 좌표가 화면 안에 있고 해당 위치에서 같은 갱신의 도형을 조회했다.

모든 요청이 `COORDINATES_MATCHED`이고 요청·조회·좌표 검사 오류가 없을 때만 `newCoordinatesVerified=true`이며 종료 코드 0이다. 해시가 없는 과거 로그는 좌표 검증을 통과하지 않는다. 조회가 여러 변경을 합쳐 중간 버전을 건너뛰거나 경로 묶음이 여러 구간으로 나뉘면 현재의 마지막 6개 좌표 검사로는 확인하지 못할 수 있다. 미연결 결과를 데이터 유실로 단정하지 않는다. 전체 경로 좌표를 매번 해시하거나 로그로 내보내지는 않는다. `crossClockLatencyComputed`는 계속 `false`이며 전체 전달 시간·픽셀 캡처 검증과 구분한다. 기존 결과 파일은 덮어쓰지 않는다.

좌표 검사 검증(2026-10-01): 실제 로컬 Chromium·MapLibre에서 정상 경로와 마커의 좌표 해시·위치별 도형 조회를 확인했다. 새 좌표가 화면 밖인 경로, 같은 ID지만 옛 부분만 있는 경로, 숨긴 레이어는 통과하지 않았다. k6의 실제 전송 본문과 기록 해시도 HTTP 대역에서 대조했다. 합성 입력 검사이며 실제 Hetzner 상황판 Smoke나 부하 결과가 아니다.

검증(2026-10-01): 고정 k6 v2.1.0 바이너리와 로컬 HTTP 대역으로 정상 응답·HTTP 오류·경로 ID/버전 오류·좌표 일부 수락을 실행했다. 정상은 종료 0, 실패 4종은 108이었다. 공개 DNS/기존 사설 hosts 분리와 민감정보 제외도 확인했다. OS pipe·실제 자식 프로세스 및 네트워크 없는 Docker 컨테이너로 중단 프로토콜을 검사했다. 정상·비정상 종료, STOP, EOF, 신호 만료, 컨테이너 생성 결과 유실과 중단 명령 실패를 구분했다. 중단 명령 실패는 `containerStopped=false`로 보고됐고 검증 도구에서 따로 정리했다. Hetzner SSH·실제 API·브라우저와 원격 쓰기의 결합 검증은 아직 아니다.

마커 연결 검증(같은 날): 외부 통신을 차단한 Docker에서 실제 k6와 HTTP 대역을 사용했다. 정상 생성 2회, 10초 종료 시 생성 1회, 안전 검사 실패 시 마커 POST 0건을 확인했다. 마커 응답/HTTP 오류·GPS 버전 오류는 전체 k6 종료 108이었다. FCM 검사 응답 검증, 일회성 승인, 기존 컨테이너 제어 7조건, 수집기·대조 도구 자체 검사도 통과했다. 현재 호스트의 loopback 연결 거절 때문에 Docker 내부에서 검증했으며, 초기 UUID 인자 오류는 수정 후 재검증했다. 실제 App SSH·Firebase·지도 신규 데이터 표시·부하 성능을 검증한 결과는 아니다.

### 지휘 계정 준비

[prepare-situation-board-account.py](prepare-situation-board-account.py)는 기존 GPS 도구의 Keycloak 통신을 재사용한다. GPS 도구와 달리 브라우저 로그인 사용자를 정리할 때까지 유지하며, 기존 `suri-map-web`의 로그인 흐름을 사용한다. 기존 사용자·FCM 토큰은 변경하지 않는다. 승인된 옵션을 지정한 경우에만 Realm 속성 편집 정책을 잠시 바꾼 뒤 복구한다.

App 서버에서 같은 디렉터리에 두 Python 파일을 놓고 다음 순서로 실행한다. 이 명령은 서버 배포·실제 로그인 검증을 마쳤다는 뜻이 아니다.

```bash
# 읽기 전용: 사건·수색 차수 상태와 지원 요청 수신 대상의 활성 FCM 토큰 수 확인
python3 prepare-situation-board-account.py inspect

# 시험용 지휘 계정 1개와 사건 배정만 생성한다. 동일 파일을 덮어쓰지 않는다.
python3 prepare-situation-board-account.py prepare \
  --manifest /srv/apps/suri-map/load-test/board-account.json \
  --allow-temporary-profile-edit
```

- 활성 FCM 토큰이 있으면 준비를 중단한다. 계정 상태나 업무폰 등록 여부로 임의 제외하지 않고 제품의 수신자 조회 조건을 따른다. 준비 당시 토큰이 없었어도 나중에 등록될 수 있으므로 **마커 전송 직전에도 재확인해야 한다**. 현재 도구만으로 실행 중 외부 발송 방지를 보장하지 않는다.
- 로그인 정보와 정리 대상 ID는 권한 `600`인 manifest에 저장한다. 이 파일은 비밀정보이므로 Git·공유 로그·시험 결과에 포함하지 않는다. 기존 계정은 덮어쓰지 않으며 준비가 중간에 실패하면 정리할 때까지 manifest를 보존한다.
- Keycloak 사용자 속성이 그대로 저장됐는지 확인한다. `--allow-temporary-profile-edit`는 원본을 `board-account.user-profile.json`에 백업한 뒤 계정 생성 중에만 `ADMIN_EDIT`을 허용한다. 일반 사용자의 편집 권한은 열지 않는다. 성공·예외 모두 원복을 시도하고 GET 결과가 원본과 같은지 확인한다. 동시 설정 변경을 발견하면 덮어쓰지 않고 중단한다. 강제 종료·네트워크 단절 시 자동 복구를 보장하지 못하므로 백업은 남겨두고, 원복 확인 실패 시 다음 시험을 진행하지 않는다.
- 2026-10-01 준비 시 Hetzner Keycloak 26.6.1에 위 세 속성 정의가 없고 미정의 속성 정책도 미설정임을 확인했다. [Keycloak 기본 정책](https://www.keycloak.org/docs/latest/server_admin/)에 따른 제약을 설명하고 사용자에게 일시적 관리자 편집 허용·원복을 승인받았다. 실제 계정 생성 후 원본 설정과 완전히 동일하게 복구됐음을 별도 조회로 재확인했다.
- 이 도구는 업무폰·경로·마커를 만들지 않는다. GPS 데이터는 기존 준비 도구를 재사용하고, 마커 요청 ID와 관련 기록 정리는 아래 도구를 사용한다.

시험 브라우저와 발생기를 종료한 뒤 해당 계정만 정리한다.

```bash
python3 prepare-situation-board-account.py cleanup \
  --manifest /srv/apps/suri-map/load-test/board-account.json
```

manifest의 정확한 계정·배정만 삭제하며, 계정에 업무폰·다른 배정·작성 마커가 생겼거나 소유 정보가 달라지면 중단한다. 생성한 Keycloak 사용자가 다른 사용자로 대체된 경우도 삭제하지 않는다. 정상 완료 후 비밀정보 파일도 제거한다. **마커·알림·이벤트·경로 정리를 대신하는 명령이 아니다.**

검증: `python3 prepare-situation-board-account.py self-check`는 외부 연결 없이 중단 조건과 정리 대상 검사를 확인한다. 2026-10-01 격리된 PostgreSQL에 관련 migration의 테이블과 현재 UUID 배정 컬럼을 준비해 생성·반복 정리·기존 계정 보존·소유 정보 불일치 롤백·FCM 탐지를 검증했다. 전체 migration·Keycloak·실제 서버 로그인을 검증한 결과와 구분한다.

별도 실제 로그인 검증(2026-10-01): 새 지휘 계정 1개·활성 사건 배정 1개를 만든 뒤 로컬 Chromium에서 공개 HTTPS 주소로 로그인했다. JWT의 계정 ID·`COMMAND`·`MISSING_TEAM`·issuer가 일치했고 토큰 수명은 7,200초였다. 해당 사건 상황판 GET 200, 사건 SSE 2개 연결, 지도와 기존 마커 표시를 확인했으며 인증 없는 같은 GET은 401이었다. 새 마커 생성·좌표 전송·다른 사건 접근 권한·토큰 갱신·부하 성능은 검증하지 않았다. 계정은 다음 시험을 위해 유지하며 각 실행 전에 토큰 만료를 다시 확인한다.

### 시험 마커의 준비와 정리

[prepare-situation-board-marker-fixtures.py](prepare-situation-board-marker-fixtures.py)는 요청 전에 마커 ID·멱등성 키를 파일로 남긴다. 응답을 받지 못해도 이 ID로 DB를 대조할 수 있다. 실제 마커 생성 API는 아직 호출하지 않는다. 기존 GPS fixture의 첫 번째 업무폰을 생성자로 사용하며, 사진 없는 지원 요청만을 대상으로 한다.

```bash
# 조건별 새 파일: 5분 동안 10초마다 1건 = 30건. Smoke에는 --count 1 사용.
python3 prepare-situation-board-marker-fixtures.py prepare --fixture board-markers.json

# 시험 발생기·관찰 브라우저 종료와 결과 보존 후, 같은 파일로 정리
python3 prepare-situation-board-marker-fixtures.py cleanup \
  --fixture board-markers.json --writers-stopped
```

정리는 파일에 있는 ID에 연결된 마커·알림·완료된 SSE 작업·멱등성 기록만 한 트랜잭션에서 삭제한다. 다른 작성자·사건·차수, 변경된 마커, 사진, 미완료 이벤트·요청 기록이 있으면 삭제하지 않는다. 실패 근거는 먼저 보존하고 원인을 확인한다. `--writers-stopped`는 실행자의 종료 확인이지 자동 종료 기능이 아니다.

다른 시험 기록과 SSE 순번은 유지한다. 정리 후에도 대조용 fixture 파일은 보존하되 새 실행에는 재사용하지 않는다. **마커 정리 → GPS fixture 정리/재생성 → 마지막에 지휘 계정 정리** 순서를 지킨다. GPS 도구도 시험 업무폰이 만든 마커가 남아 있으면 DB 초기화를 거부한다.

검증: 자체 검사와 격리된 PostgreSQL에서 정확한 대상·반복 정리, 기존 기록 보존, 작성자·버전·사진·미완료 이벤트·요청 결과 불일치 시 롤백을 확인했다. 실제 부하 실행 후 정리를 검증한 결과는 아니다.

### 마커 전송 전 FCM 안전 확인

2026-10-01 Hetzner App을 읽기 전용으로 확인했다. 기존 실행 설정은 `FCM_PROVIDER=mock`이고, 설정 덮어쓰기나 추가 mount는 확인되지 않았다. 사건·차수는 열려 있으며 지원 요청 수신 계정 1개·업무폰 1개·활성 FCM 토큰 0개였다. 서버 설정이나 토큰을 변경한 결과가 아니다.

```bash
python3 prepare-situation-board-account.py check-marker-safety
# 이후 검사에는 첫 결과의 backendIdentity를 전달한다.
python3 prepare-situation-board-account.py check-marker-safety \
  --expected-backend '<첫 결과의 backendIdentity>'
```

실행기의 마커 모드가 첫 검사와 각 마커 전송 직전에 이 검사를 호출한다. 실패·검사 불가·Backend 재시작/교체를 발견하면 현재 조건을 중단한다. 이 검사 때문에 시험 서버에 DB 조회·Docker 확인 부하가 추가되므로 측정 조건에 포함한다. 실제 Hetzner 실행기 연결은 아직 검증하지 않았다.

외부 발송을 하지 않는 근거는 고정된 배포에서 사용 중인 `MockFcmDispatcher`다. 토큰 반복 조회 자체가 발송 차단 장치는 아니며, 검사 직후 배포가 바뀌는 경쟁도 막지 못한다. 시험 중 배포·재시작은 진행하지 않고, 설정이 달라지면 임의로 mock으로 바꾸지 말고 시험을 중단한다. 지원 요청의 DB 알림·SSE는 측정하되 실제 Firebase 전송 성능을 측정한 것으로 보고하지 않는다.

## 결과 보존과 정리

k6 stdout 요약은 Ops 서버의 `-k6.log`, DB 검증은 App 서버의 `-db.log`에 남긴다. 상세 시계열은 Prometheus와 Grafana에서 확인한다. 원시 JSON은 저장하지 않는다.

최종 결과는 조건, k6 요약, DB 검증과 Grafana 화면을 함께 보존한다. Grafana 화면을 만들 수 없으면 실행 구간의 Prometheus 값과 원본 로그 SHA-256을 기록한다.

GPS 시험 Fixture는 App 서버에서 정리한다. 상황판 시험에서는 위 마커 정리를 먼저 완료한다.

```bash
python3 prepare-search-path-batch-fixtures.py --cleanup
```

정리 명령이 성공하고 Ops 서버의 requester token 파일을 제거하면 정리가 완료된다.
