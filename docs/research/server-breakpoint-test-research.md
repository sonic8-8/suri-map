# 서버 Breakpoint Test 설계 조사

조사 기준일은 2026년 9월 1일이다. 이 문서는 Issue #8의 서버 Breakpoint Test 초기 설계 근거다. 아래 수치는 당시 선택과 이유이며, 실행할 때는 [k6 실행 안내](../../infra/k6/README.md)와 [요청 스크립트](../../infra/k6/search-path-batch-load.js)를 확인한다. 후속 측정은 [Issue #8 기록](../issues/8-gps-collection-transmission-basis.md)에서 확인한다.

## 결론

- 기존 `periodic`, `backlog-drain`, `recovery-catch-up`은 업무폰별 순차 전송을 검증하는 closed model로 유지한다.
- 새 `breakpoint` 시나리오는 요청 완료 속도와 무관하게 요청 시작률을 유지하는 `constant-arrival-rate`로 분리한다.
- 한계 후보는 `220 RPS` 간격의 독립 실행으로 찾고 이분 탐색으로 좁힌다. 자동 ramp는 사용하지 않는다.
- `dropped_iterations == 0`, 부하 발생기 여유, 데이터 무결성을 모두 만족한 실행만 Breakpoint 근거로 사용한다.
- 결과는 "현재 개발 환경에서 이 요청 모델로 확인한 한계 후보"로 표현한다. 실패 지점에 도달하지 못했다면 최대값이 아니라 검증한 하한만 말한다.

## 부하 모델

시험 대상은 당시 Hetzner 개발 환경의 `POST /api/search-paths/batch`다. 한 사건에 독립된 requester fixture를 준비하며, 업무폰 468대의 실제 사용자 흐름 검증과 서버 처리 한계 측정을 분리한다.

Closed model은 이전 iteration이 끝나야 다음 iteration을 시작하므로 서버 응답이 느려질수록 주입률도 낮아진다. Grafana는 이를 coordinated omission 문제로 설명한다. Arrival-rate executor인 open model은 iteration 시작과 완료를 분리한다. 따라서 실제 단말의 순차 흐름에는 기존 VU 기반 모드가, 정해진 요청률에 대한 서버 응답에는 open model이 맞다. [Open and closed models](https://grafana.com/docs/k6/latest/using-k6/scenarios/concepts/open-vs-closed/)

`constant-arrival-rate`는 정한 rate를 duration 동안 유지하므로 각 RPS의 지연·오류·서버 지표를 비교하기 쉽다. Grafana의 breakpoint 가이드는 부하를 서서히 올리는 탐색을 설명하지만, 특정 증가폭이나 실행 시간을 정해 주지는 않는다. Suri-Map의 `220 RPS` 증가폭과 1분 실행은 기존 혼합 부하 목표 약 `218.4 RPS`를 한 단계로 삼아 비교하기로 한 프로젝트 선택이다. [Constant arrival rate](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/constant-arrival-rate/) · [Breakpoint testing](https://grafana.com/docs/k6/latest/testing-guides/test-types/breakpoint-testing/)

Suri-Map은 쓰기 결과와 Fixture를 실행별로 검증해야 하므로 다음 순서가 해석하기 쉽다.

1. `1 RPS × 30초` Smoke Test를 실행하고 DB까지 확인한다.
2. `220`, `440`, `660`, `880`, `1,100 RPS`처럼 220씩 늘려 각각 1분 동안 실행한다.
3. 최초 서버 실패 RPS는 회복과 Fixture 초기화 후 같은 값에서 다시 실패해야 상한으로 확정한다.
4. 마지막 성공과 최초 실패의 정수 중간값을 같은 방식으로 확인해 차이가 `1 RPS`가 될 때까지 좁힌다.
5. 마지막 성공 RPS를 5분 동안 다시 확인한다.

## VU 할당과 `dropped_iterations`

Arrival-rate executor는 각 iteration에 사용 가능한 VU가 필요하다. 공식 문서의 초기 추정식은 `median_iteration_duration × rate + 변동 여유`이며, 실제 값은 사전 실행으로 보정한다. 런타임에 `maxVUs`를 늘리면 CPU·메모리 할당 자체가 부하 발생기를 흔들어 결과를 왜곡할 수 있으므로 필요한 VU를 `preAllocatedVUs`로 미리 확보한다. `maxVUs`는 생략해 `preAllocatedVUs`와 같게 두고, 시작부터 drop이 생기면 사전 할당을 늘려 다시 실행한다. [Arrival-rate VU allocation](https://grafana.com/docs/k6/latest/using-k6/scenarios/concepts/arrival-rate-vu-allocation/)

Arrival-rate 실행의 `dropped_iterations`는 사용할 VU가 없어 시작하지 못한 iteration 수다. 시작 직후 발생하면 VU 부족일 가능성이 크고, 뒤늦게 발생하면 서버 지연 증가로 VU 점유 시간이 길어진 결과일 수 있다. 이 지표만으로 원인을 단정할 수 없다. [Dropped iterations](https://grafana.com/docs/k6/latest/using-k6/scenarios/concepts/dropped-iterations/)

초기 설계에서는 독립 fixture와 VU를 `468 × 3 = 1,404`개로 시작하기로 했다. `1,404`는 공식 권장값이나 상한이 아니라 응답 지연 중에도 468개 실제 fixture 수의 세 배까지 독립 요청자를 확보하자는 프로젝트 선택이다. 이후 실행에서 수량을 늘릴 수 있으며 다음처럼 해석한다.

- `dropped_iterations`가 하나라도 있으면 목표 요청률을 온전히 주입하지 못했으므로 실행을 중단하고 용량 근거에서 제외한다.
- Fixture와 사전 할당 VU를 함께 늘린 같은 조건에서 다시 실행한다.
- Ops CPU·메모리·네트워크가 먼저 포화되면 서버 한계로 해석하지 않고 마지막 유효 RPS까지만 기록한다.

## 실행 시간과 중단 기준

`duration`은 요청을 새로 시작하는 구간이고 `gracefulStop`은 종료 뒤 진행 중인 iteration을 기다리는 별도 구간이다. 요청 timeout은 조사 당시 확인한 Android OkHttp 기본값을 시험 상한으로 근사한 10초이고 SLA가 아니다. `gracefulStop` 30초는 timeout보다 길게 유지한다. Arrival-rate executor가 속도를 조절하므로 iteration 끝에 `sleep()`은 두지 않는다. [Graceful stop](https://grafana.com/docs/k6/latest/using-k6/scenarios/concepts/graceful-stop/) · [Constant arrival rate](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/constant-arrival-rate/)

Threshold는 합격·실패 조건이며, 실패 시 k6가 non-zero로 종료한다. `abortOnFail`은 실패한 실행을 일찍 멈춘다. 이번 시험은 첫 기능 실패를 즉시 후보로 잡으므로 `delayAbortEval`을 두지 않는다. `check()`만으로는 실행이 실패하지 않으므로 필요한 check에는 threshold가 함께 있어야 한다. [Thresholds](https://grafana.com/docs/k6/latest/using-k6/thresholds/)

`breakpoint` 시나리오의 최소 기준은 다음과 같다.

- 기능 성공: status 200, 좌표 6개 전부 수락, 제외 좌표 0개를 나타내는 `checks rate == 1`
- 주입 유효성: `dropped_iterations count == 0`
- 안전 중단: 위 threshold에 `abortOnFail`을 적용하고 첫 실패율이나 지연 시간을 별도 허용치로 두지 않는다.
- 사후 무결성: 요청 수, 좌표 수와 고유성, 제외 좌표, 멱등성 기록 수·고유성·완료 상태를 DB에서 확인한다.
- 성능 관찰: endpoint p95·p99는 진단 지표로 기록하되 합격값으로 사용하지 않는다.

## 지표와 태그

요청의 `name: POST /api/search-paths/batch`를 유지하면 URL 값과 무관하게 endpoint 지표를 모을 수 있다. k6의 기본 `scenario` 태그에는 `breakpoint`가, 추가 태그에는 `target_rps`가 기록된다. 실행 ID는 실행별 필터가 필요할 때만 한 번씩 증가하는 metadata로 사용한다. 업무폰·경로·좌표·멱등성 키처럼 요청마다 바뀌는 값은 Prometheus 태그로 만들지 않는다. 고유한 label 조합마다 별도 시계열이 생기기 때문이다. 태그는 요청·check·custom metric을 분류하고, `metric{tag:value}` 형태로 endpoint별 threshold를 만들 수 있다. [Tags and Groups](https://grafana.com/docs/k6/latest/using-k6/tags-and-groups/) · [Tagged thresholds](https://grafana.com/docs/k6/latest/using-k6/thresholds/#set-thresholds-for-specific-tags) · [Running large tests](https://grafana.com/docs/k6/latest/testing-guides/running-large-tests/#limit-resource-intensive-k6-operations)

한 iteration이 `POST /api/search-paths/batch` 한 건만 실행될 때만 iteration rate를 목표 요청 시작률과 동일하게 해석할 수 있다. 향후 한 iteration에 HTTP 요청이 추가되면 rate와 RPS는 달라진다. Grafana도 `constant-arrival-rate`의 rate는 요청 수가 아니라 script function의 iteration 수라고 구분한다. [API load testing](https://grafana.com/docs/k6/latest/testing-guides/api-load-testing/#request-rate)

## 부하 발생기와 서버 병목 구분

Grafana는 대규모 실행에서 부하 발생기의 CPU를 80% 이하, 메모리를 90% 미만으로 유지하고 네트워크 포화를 함께 감시하라고 안내한다. CPU가 100%에 가까우면 부하 발생기 throttling이 응답 시간까지 왜곡할 수 있다. [Running large tests](https://grafana.com/docs/k6/latest/testing-guides/running-large-tests/#monitoring-guidelines)

기존 `Suri-Map Load Test` 대시보드는 k6 목표 결과, `dropped_iterations`, App·Ops CPU/메모리, Backend, HikariCP와 PostgreSQL 지표를 함께 보여준다. App 네트워크는 수집하지만 Ops 네트워크 포화는 별도로 확인해야 한다. 서버 한계 후보는 다음 조건이 함께 나타날 때만 기록한다.

- k6가 목표 rate를 실제로 유지하고 `dropped_iterations`가 없다.
- Ops CPU·메모리·네트워크가 위 한계에 닿지 않는다.
- HTTP 처리량 증가가 둔화하면서 지연·오류 또는 서버·DB 대기가 증가한다.
- 실행 후 좌표 누락·중복과 멱등성 충돌이 없다.

부하 발생기가 먼저 포화되면 그 실행은 서버 한계가 아니라 테스트 장비 한계다. 서버와 부하 발생기의 위치·자원·k6 버전·네트워크 경로를 결과와 함께 고정해야 실행 간 비교가 가능하다.

## 요청 데이터 모델

`appendPathBatch()`는 `execution.vu.idInTest - 1`로 requester fixture를 고르고 `execution.vu.iterationInScenario`로 그 VU의 요청 순서를 만든다. VU 하나가 fixture와 경로 하나만 사용하므로 같은 업무폰의 요청은 겹치지 않는다. 필요한 동시성이 1,404 VU를 넘으면 fixture와 VU를 같이 늘린다. [k6/execution](https://grafana.com/docs/k6/latest/javascript-api/k6-execution/)

한 iteration은 요청 한 건이고 payload는 좌표 6개다. 각 좌표는 2.5초 간격이며 마지막 `clientTs`는 요청 생성 시각이다. 2.5초는 차량 모드 전환 정책이 아니라 현재 서비스에서 가능한 가장 잦은 수집 입력을 고정해 더 큰 payload 생성 빈도를 시험하려는 값이다. `elapsedRealtimeNanos`도 좌표마다 25억 ns씩 증가시켜 실제 Android payload의 수집 순서를 보존한다. `pointId`와 `Idempotency-Key`는 실행 ID, phase, requester, batch 순서로 고유하게 만든다.

## 결과 표현의 한계

Breakpoint 결과는 환경, 데이터, payload, 네트워크와 중단 기준에 종속된다. 탄력 확장이 켜진 환경은 자원 한계 대신 확장 정책이나 비용 한계를 측정할 수 있으므로 고정해야 한다. [Breakpoint testing considerations](https://grafana.com/docs/k6/latest/testing-guides/test-types/breakpoint-testing/#considerations)

- 실패나 처리량 정체가 재현되면 `현재 개발 환경의 한계 후보: X RPS`로 기록한다.
- 최고 RPS에서도 유효했다면 `X RPS까지 검증`으로 기록하고, 최대 처리량이라고 부르지 않는다.
- 운영 안전 처리량은 반복 횟수, 허용 지연·오류와 여유 폭을 별도로 정한 뒤에만 제안한다.
- 개발 환경 결과를 운영 환경 전체 용량이나 SLA로 일반화하지 않는다.

## 실행 뒤 남길 근거

각 성공 실행은 k6 summary의 실제 `iterations`를 기준으로 요청당 좌표 6개와 멱등성 기록 한 건이 정확히 저장됐는지 DB에서 확인한다. `rate × duration` 계산값을 요청 수로 가정하지 않는다. stdout와 DB 검증 로그는 서버에 보관하고, 저장소에는 최종 결과 요약과 대시보드 증거를 남기기로 했다.
