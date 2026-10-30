# Hetzner Jenkins CI 실행

Hetzner Jenkins의 `수리맵` 작업은 다음 순서로 실행한다.

`Checkout → Backend Test CI → Frontend Test CI → Path Display Timing Checks CI → Sync → Build Images → Deploy Backend → Verify Backend → Deploy Web`

`Path Display Timing Checks CI`는 Issue #27에서 추가한 단계다. 2026-10-30 [#102](https://jenkins.sonic8-8.com/job/%EC%88%98%EB%A6%AC%EB%A7%B5/102/)에서 이 단계가 Jenkinsfile 안의 반복문으로 측정 스크립트 테스트 9개를 모두 통과했다. 반복문 대신 `run-checks.sh`를 부르도록 바꾼 이 파일은 아직 작업 설정에 저장하고 새 빌드로 확인하기 전이다. 단계 설명은 [경로 표시 시간 측정 스크립트 자기 점검](#경로-표시-시간-측정-스크립트-자기-점검)에 있다.

`Frontend Test CI`는 Issue #25에서 추가한 단계다. 2026-10-29에 이 파일과 같은 스크립트를 작업 설정에 저장하고 다시 조회해 일치하는지 확인했다. 이어서 [#93](https://jenkins.sonic8-8.com/job/%EC%88%98%EB%A6%AC%EB%A7%B5/93/)에서 Frontend 테스트 372개가 통과하고 웹 배포까지 끝났다. 단계 설명은 [Frontend 테스트 실행](#frontend-테스트-실행)에 있다.

2026-10-14 전환용 수동 승인을 제거한 설정을 Jenkins에 저장하고 이 파일과 원문이 일치하는지 확인했다. 이전 승인에서 대기하던 #63은 중단했다. #64에서 발견한 로그인 셸 종료 오류를 수정한 뒤 [#65](https://jenkins.sonic8-8.com/job/%EC%88%98%EB%A6%AC%EB%A7%B5/65/)에서 Backend 테스트·기동 확인·웹 배포가 수동 승인 없이 통과했다. 설정 저장만으로 이미 실행 중인 빌드가 바뀌지는 않는다.

실행 정의는 Jenkins 작업 설정에 저장된 **inline 스크립트**다. [hetzner.Jenkinsfile](hetzner.Jenkinsfile)은 이 설정을 보관·대조하는 파일이며, Jenkins가 저장소에서 자동으로 읽는 파일은 아니다. 변경할 때는 문법·로컬 회귀 검사 → 작업 설정 저장 → 저장된 스크립트 재조회·대조 → 새 빌드 실행으로 확인한다. 이번 변경은 Groovy 구문·shell 검사 후 적용했으며 별도의 Jenkins Declarative lint는 실행하지 않았다.

기존 [infra/Jenkinsfile](../Jenkinsfile)은 다른 배포 설정을 포함하며 현재 작업에서 사용하지 않는다. 이 파일을 수정하는 것만으로 Hetzner CI가 바뀌지 않는다. 테스트 연결이 빠졌던 원인과 실제 빌드별 결과는 [문제 해결 기록 26](../../docs/problem-solving-records/26-jenkins-backend-tests-not-connected.md)에 기록한다.

## 자동 배포와 기동 확인

경로 조회 전환 migration `20261006.001`~`003`은 적용을 마쳤다. 전환 당시의 매 배포 승인·쓰기 중단·최종 백업 절차를 일반 배포에 강제하지 않는다. CI·기존 이미지 보존·동시 빌드 방지는 유지하며 Backend 기동 확인이 통과해야 웹을 배포한다.

| 단계 | 수행·확인할 내용 |
|---|---|
| Build Images | 실행 중인 앱 3개의 이미지 ID를 조회해 `before-build-<UTC 시각>` 태그로 먼저 보존한 뒤 새 이미지를 만든다. 기존 이미지를 찾지 못하면 빌드 전에 실패한다. 실행 중 앱은 바꾸지 않는다. |
| Deploy Backend | `up -d --no-deps backend`로 Backend만 시작한다. Flyway는 이때 실행된다. |
| Verify Backend | 실행 컨테이너의 이미지가 방금 빌드한 `suri-map-backend:develop`과 일치하고, loopback 포트의 `/actuator/health`가 JSON `status: UP`을 반환하는지 확인한다. HTTP 확인 전후 컨테이너 ID·이미지·상태·재시작 횟수도 같아야 한다. 실패하면 웹 배포를 실행하지 않는다. |
| Deploy Web | `up -d --no-deps frontend mock-112`로 웹과 시연 서버를 시작한다. 검증한 Backend와 DB·인증·저장소는 재생성하지 않는다. |

기동 확인은 최대 60회 재시도하며 HTTP 요청 제한 2초·재시도 간격 1초를 사용한다. Jenkins 단계 전체 제한은 3분이다. 무한 대기를 막기 위한 운영상 상한이며 서비스 응답 목표나 공식 권장 시간은 아니다. 실제 기동 시간에 맞춰 조정할 수 있다. App 호스트에 `curl`과 `python3`가 있어야 하며, 현재 설치를 확인했다. 포트는 `docker port`로 읽으므로 8081처럼 기본 포트를 가정하지 않는다.

검증 단계는 SSH에서 `sh -s`를 명시한다. #64에서는 로그인 셸의 `exit 0` 뒤 배포 계정의 화면 정리 명령이 실패해, health가 UP이어도 웹 배포가 차단됐다. 스크립트 셸을 명시해 검사 자체의 종료 코드를 전달하며 서버 계정의 종료 설정은 바꾸지 않는다.

기동 검사는 [Spring Boot health 상태](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)와 [Jenkins 단계 timeout](https://www.jenkins.io/doc/book/pipeline/syntax/)을 사용한다. 인증된 업무 API·원본 데이터 정합성·성능을 검증하는 Smoke/부하테스트와는 다르다. 성공 직후의 장애까지 예방하는 연속 감시도 아니다.

`disableConcurrentBuilds()`는 이 작업의 동시 빌드를 막고, `disableRestartFromStage()`는 테스트·이미지 보존을 건너뛴 중간 단계 재시작을 막는다. 실패 시 이미 시작한 Backend나 적용한 migration을 자동으로 되돌리지 않는다. 기존 DB·Keycloak·저장소는 `--no-deps`로 재생성하지 않는다. 무중단 배포를 보장하는 구조는 아니다.

향후 데이터 변환·비호환 schema 변경이 필요하면 해당 배포 전에 쓰기 중단·백업·검증·복구 범위를 별도로 합의한다. 이번 자동화가 그런 변경의 안전성을 자동 판정하지는 않는다. 일반 배포에 전환용 수동 승인을 다시 상시 적용하지 않는다.

로컬 회귀 검사는 `node infra/ci/verify-backend.test.cjs`로 실행한다. 실제 검증 단계의 shell을 실행하되 Docker·HTTP·대기는 대역으로 둬 UP, 지연 기동, DOWN, HTTP 오류, 잘못된 JSON·이미지, 재시작·교체, 잘못된 포트와 조회 실패를 확인한다. 실제 Jenkins 실행 검증과 구분한다.

## DB 테스트 실행

[Backend Dockerfile](../../backend/Dockerfile)의 `tester` 이미지는 테스트 코드를 컴파일한다. 테스트 자체는 이미지 빌드 중이 아니라 컨테이너를 실행할 때 수행한다. 따라서 `docker build` 성공만으로 테스트 통과를 판단하지 않는다.

Jenkins는 체크아웃한 커밋의 `git archive HEAD`를 이미지 빌드 입력으로 사용한다. 테스트 컨테이너는 **Ops 서버**에서 실행하며, App 서버 동기화·배포는 검사가 통과한 뒤 기존 방식대로 진행한다.

아래 명령은 Linux Docker Engine이 있는 저장소 루트에서 테스트 실행만 재현한다. 보고서 회수·보관은 Jenkins 단계가 담당한다.

```sh
docker build --target tester -t suri-map-backend-test:local -f backend/Dockerfile .
docker run --rm \
  --network host \
  --mount type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock \
  --add-host host.docker.internal:host-gateway \
  --env TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  suri-map-backend-test:local
```

기본 명령은 `test jacocoTestReport`이며 성능 태그는 기존 Gradle 설정대로 제외한다. Gradle 테스트 결과 캐시는 사용하지 않는다.

- Testcontainers가 별도 PostgreSQL/PostGIS 컨테이너를 생성한다. 운영 DB 주소·인증 정보를 전달하거나 운영 DB 데이터를 사용하지 않는다.
- 테스트 컨테이너에 호스트 Docker 소켓을 연결한다. 호스트 Docker를 제어할 수 있는 권한이므로 신뢰하는 코드만 실행한다. 별도 Docker daemon을 띄우거나 Docker API를 TCP로 공개하지 않는다. 테스트 DB는 임시 동적 포트를 사용한다.
- 테스트 실행 컨테이너는 Ops 호스트의 네트워크를 공유한다. 이 서버에서 기본 bridge 네트워크로 실행하면 Ryuk의 호스트 공개 포트에 접속하지 못해 DB 검사가 건너뛰어졌다. 같은 이미지에 `--network host`만 추가한 비교 실행으로 필수 DB 테스트 23개의 통과를 확인했다. 이 설정은 Ops 호스트와 네트워크가 격리되지 않으므로 신뢰하는 코드만 실행한다. 운영 서비스의 네트워크 설정을 바꾸는 것은 아니다.
- `host-gateway`는 테스트 컨테이너가 DB의 동적 포트에 접근할 호스트 주소다. 현재 구성은 로컬 Unix 소켓 기반 Docker를 전제로 하며, 원격 Docker·rootless 환경까지 지원하는 설정은 아니다.

구성 근거: [Testcontainers의 컨테이너 내부 실행 안내](https://java.testcontainers.org/supported_docker_environment/continuous_integration/dind_patterns/), [Docker의 host-gateway 안내](https://docs.docker.com/reference/cli/docker/container/run/#add-host), [Docker의 host 네트워크 안내](https://docs.docker.com/engine/network/drivers/host/).

## 실패 처리와 결과 파일

Jenkins는 `docker create` → `docker start --attach`로 실행하고, 실패해도 컨테이너를 지우기 전에 결과를 복사한다. 테스트의 실패 종료 코드는 보고서 회수 여부와 관계없이 유지한다. 복사 후 해당 테스트 컨테이너와 익명 Gradle 볼륨을 정리한다.

다음 중 하나라도 해당하면 App 서버의 `Sync`와 이후 이미지 빌드·배포 단계를 실행하지 않는다.

- 테스트 실행이 실패했거나 JUnit에 실패한 테스트가 있다.
- JUnit 결과가 없거나 비어 있다. HTML 테스트 보고서·JaCoCo XML·HTML의 필수 파일도 있어야 한다.
- 필수 SSE DB 검사가 실패·건너뜀·0개 실행·결과 누락으로 판정된다.

보고서는 `ci-artifacts/backend-test/<빌드 번호>/`에 모은다. 이전 빌드 결과를 재사용하지 않도록 새 디렉터리인지 확인하며, 이 경로는 App 서버로 동기화하지 않는다. 실패한 빌드도 회수한 파일은 보관한다.

| 결과 | 위치·판정 |
|---|---|
| 대상 커밋 | `commit.txt` |
| JUnit | `test-results/TEST-*.xml`. Jenkins의 **Test Result**에서도 확인한다. |
| HTML 테스트 보고서 | `test-report/index.html`과 연결된 파일 |
| JaCoCo | `coverage/jacocoTestReport.xml`, `coverage/html/index.html`과 연결된 파일 |
| SSE DB 검사 | `sse/`. `collect-s4-evidence.sh`가 `ServerSentEventHistoryServiceTest`·`ServerSentEventJobWorkerTest`의 실제 JUnit XML을 읽고 `verify-s4-evidence.sh`가 판정한다. |

JUnit 게시에는 기존 플러그인, 파일 보관에는 Jenkins의 `archiveArtifacts`를 사용한다. [JUnit 결과 누락·실패 처리](https://www.jenkins.io/doc/pipeline/steps/junit/)와 [산출물 보관](https://www.jenkins.io/doc/pipeline/steps/core/#archiveartifacts-archive-the-artifacts)의 공식 동작을 따른다. 테스트 통과율·커버리지 목표치를 새로 정한 것은 아니다.

`S4`·`evidence` 파일명은 기존 수집·검사 스크립트의 호환성을 유지하기 위한 이름이다. DB 테스트 통과는 실제 서버 재시작·브라우저 복구·부하 시험 통과와 다르다.

SonarQube는 현재 Hetzner 작업에서 실행하지 않는다. 옛 Jenkinsfile의 `SKIPPED_NO_SERVER`를 분석 성공으로 해석하지 않는다.

기존 `verify-evidence.sh`는 커버리지 파일·요약·SonarQube 상태 파일의 존재와 문구를 확인하는 수동 검사다. 현재 Jenkins에서 직접 호출하지 않으며, 테스트 통과나 커버리지 기준 충족을 대신 판정하지 않는다.

## Frontend 테스트 실행

`Frontend Test CI`는 Backend 테스트가 통과한 뒤, `Sync`보다 먼저 Ops 서버의 Docker에서 실행한다. `git archive HEAD:frontend`로 체크아웃한 커밋의 Frontend 코드만 빌드 입력으로 넘기고, [Frontend Dockerfile](../../frontend/Dockerfile)의 `tester` 이미지에서 `npm ci`를 실행한다. 테스트는 이미지 빌드 중이 아니라 컨테이너를 시작할 때 `npm test`(vitest)로 실행한다.

- 테스트 컨테이너는 `--network none`으로 실행한다. 운영 비밀값(`V_WORLD_API_KEY` 등), 운영 DB, Backend, Docker 소켓을 넘기지 않는다. 이미지 빌드의 `npm ci`는 npm 저장소에 접속한다.
- 실행 방식은 Backend와 같다. `docker create` → `docker start --attach`로 실행하고, 실패해도 컨테이너를 지우기 전에 JUnit 결과를 복사한다. 테스트의 실패 종료 코드는 보고서 회수 여부와 관계없이 유지한다.
- 테스트가 하나라도 실패하거나, JUnit 결과가 없거나 비어 있거나, JUnit에 실패한 테스트가 있으면 단계가 실패하고 `Sync` 이후 단계를 실행하지 않는다.

| 결과 | 위치와 확인 방법 |
|---|---|
| 대상 커밋 | `ci-artifacts/frontend-test/<빌드 번호>/commit.txt` |
| JUnit | `ci-artifacts/frontend-test/<빌드 번호>/junit.xml`. Jenkins의 **Test Result**에 Backend 결과와 함께 게시하므로, 빌드 화면의 테스트 수는 Backend와 Frontend를 합친 값이다. |
| Frontend만의 테스트 수 | `Frontend Test CI` 단계 로그의 `Frontend tests: total=<수>, failed=<수>, skipped=<수>` 줄. `junit` 단계의 반환값으로 남긴다. vitest의 `Test Files`, `Tests` 집계 줄도 같은 단계 로그에 있다. |

Test Result 화면에서 Frontend 테스트는 vitest가 JUnit의 classname에 넣은 파일 경로로 묶인다. Jenkins JUnit 플러그인은 classname을 마지막 `.`에서 나눠 패키지와 클래스로 보여 준다. 그래서 `src/shared/config.test.ts`는 패키지 `src/shared/config.test`, 클래스 `ts`로 보이고, Frontend 파일은 모두 `ts`나 `tsx` 클래스가 된다. Backend의 `com.surimap...` 패키지와는 패키지 이름으로 구분한다.

#93에서 실제 Jenkins 동작을 확인한 결과는 다음과 같다.

- 단계 로그의 `Frontend tests: total=372, failed=0, skipped=0`은 vitest의 `Tests  372 passed (372)`와 같다. `junit` 반환값은 앞서 게시한 Backend 결과를 더하지 않고 이번 호출에서 읽은 Frontend 결과만 센다. 빌드 화면의 Test Result는 Backend와 Frontend를 합쳐 통과 1801개다.
- Test Result 화면에서 Frontend 테스트는 위 설명대로 `src/app/useAppSession.test` 같은 패키지로 보인다.
- 집계 값을 읽을 때 Jenkins script sandbox의 거부는 없었다. `--target tester` 빌드에서 Frontend `builder`와 nginx 단계는 실행되지 않았다.

아래 명령은 Linux Docker Engine이 있는 저장소 루트에서 Jenkins 단계의 테스트 실행을 재현한다. `git archive`는 커밋한 내용만 넘기므로 커밋하지 않은 변경은 반영되지 않는다.

```sh
git archive HEAD:frontend | docker build --target tester -t suri-map-frontend-test:local -f Dockerfile -
docker run --rm --network none suri-map-frontend-test:local
```

Docker 없이 같은 테스트를 실행하려면 `cd frontend && npm ci && npm test`를 쓴다. JUnit 파일까지 만들려면 `npm test -- --reporter=default --reporter=junit --outputFile.junit=reports/junit.xml`을 쓴다.

한계:

- vitest와 jsdom으로 실행하는 단위 테스트다. 실제 브라우저, 지도 타일, Backend API와 SSE 연결은 검증하지 않는다.
- Backend 테스트가 실패하면 Jenkins가 이후 단계를 건너뛰므로 그 빌드에는 Frontend 테스트 결과가 없다.
- `Build Images`의 `npm run build`는 따로 typecheck와 build를 실행한다. 이 단계는 그 빌드를 대신하지 않는다.
- 실행마다 결과가 달라지는 테스트를 자동으로 다시 실행하거나 따로 표시하지 않는다. 한 번이라도 실패하면 배포를 막는다.

## 경로 표시 시간 측정 스크립트 자기 점검

`Path Display Timing Checks CI`는 `Frontend Test CI` 다음, `Sync`보다 먼저 Ops 서버의 Docker에서 실행한다. 이 단계는 경로 표시 시간 측정 스크립트의 자기 점검인 `infra/k6/path-display-timing/check-*.cjs`를 컨테이너 안에서 `run-checks.sh`로 모두 실행한다. 측정 스크립트 테스트 목록과 실패 처리는 `run-checks.sh`에서만 바꾼다. 점검마다 무엇을 확인하는지는 [k6 README의 자기 점검](../k6/README.md#자기-점검)에 있다.

- `git archive HEAD`로 체크아웃한 커밋의 `infra/k6/path-display-timing/`과, 점검이 직접 불러오는 `frontend/src/features/path/model/searchPathPages.ts`와 `frontend/package.json`만 컨테이너에 넘긴다. 이미지는 빌드하지 않는다.
- 컨테이너는 `node:22.22.0-alpine`을 `--network none`으로 실행한다. 점검이 TypeScript 파일을 불러오므로 타입 제거를 기본으로 지원하는 Node 버전을 고정했다. 실서버, ssh, 운영 비밀값, Docker 소켓을 넘기지 않는다. 이미지를 처음 받을 때만 Docker가 저장소에 접속한다.
- 단계 로그에는 점검 파일마다 `Path display timing check passed: <파일>` 또는 `Path display timing check FAILED: <파일>` 줄이 남고, 끝에 `Path display timing checks: total=<수>, failed=<수>` 줄과 실패한 파일 목록이 남는다.

점검이 하나라도 실패하면 Jenkins는 단계를 실패로, 빌드를 UNSTABLE로 표시하고 `Sync` 이후 배포 단계를 계속 실행한다. 이 동작은 `catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE', catchInterruptions: false)`로 정했다. Discord에는 `불안정` 알림을 보낸다.

사용자가 이 단계가 도는 동안 빌드를 중단하면 Jenkins는 이를 점검 실패로 처리하지 않는다. `catchInterruptions: false`로 두었으므로 `catchError`는 중단을 잡지 않고 다시 던진다. 그래서 빌드는 UNSTABLE이 아니라 중단으로 끝나고, Jenkins는 `Sync` 이후 단계를 실행하지 않는다.

실패해도 배포를 막지 않는 이유는 이 점검이 서비스 동작이 아니라 측정 도구를 확인하기 때문이다. 측정 도구가 깨져도 Backend와 웹의 동작은 앞의 두 테스트 단계에서 이미 확인했다. 대신 점검이 깨진 것을 측정하는 날이 아니라 그 커밋의 빌드에서 알 수 있게 한다. 사용자가 Issue #27에서 이 결정을 승인했다.

아래 명령은 Linux Docker Engine이 있는 저장소 루트에서 단계의 shell을 Jenkinsfile에서 꺼내 그대로 실행한다. `git archive`는 커밋한 내용만 넘기므로 커밋하지 않은 점검 변경은 반영되지 않는다. 종료 코드가 0이면 모든 점검이 통과한 것이다. 0이 아니면 실패한 점검이 있거나 Docker 실행이나 파일 전달이 실패한 것이므로, 출력에서 `Path display timing check FAILED` 줄을 확인한다.

```sh
node -e "process.stdout.write(require('fs').readFileSync('infra/ci/hetzner.Jenkinsfile','utf8').split(\"stage('Path Display Timing Checks CI')\")[1].split(\"sh '''\")[1].split(\"'''\")[0])" > /tmp/path-display-timing-checks.sh
PATH_DISPLAY_TIMING_CHECK_IMAGE=node:22.22.0-alpine sh /tmp/path-display-timing-checks.sh
```

Docker 없이 실행하려면 저장소 루트에서 `sh infra/k6/path-display-timing/run-checks.sh`를 실행한다. 컨테이너 안에서 부르는 것과 같은 파일이고, 커밋하지 않은 변경도 반영된다.

한계:

- 측정 도구의 자기 점검이다. 실서버 측정, 브라우저 동작, 상황판 성능은 검증하지 않는다.
- 위 명령은 단계의 shell과 종료 코드만 확인한다. 실패 처리는 2026-10-30에 로컬 Jenkins(`jenkins/jenkins:lts-jdk17`, 2.541.3)에서 따로 확인했다. 이 단계와 같은 `catchError` 줄 안에서 `exit 1`로 끝나는 shell을 실행하면 Stage View에서 이 단계가 FAILED로, 빌드가 UNSTABLE로 끝나고, 다음 `Sync` 단계와 `post { unstable }`이 실행됐다. 같은 `catchError` 안에서 실행 중인 빌드를 중단하거나, `catchError`를 감싼 `timeout`이 지나면 빌드가 ABORTED로 끝나고 `Sync`를 건너뛰었다. `catchInterruptions` 인자를 뺀 대조 실행에서는 timeout으로 빌드가 ABORTED로 표시됐지만 `Sync`가 실행됐다.
- 로컬 Jenkins 재현은 점검 shell 대신 `exit 1`과 `sleep`을 썼고, 실제 Docker 점검과 배포 단계는 실행하지 않았다. 로컬 Jenkins와 플러그인 버전은 운영 Jenkins와 다를 수 있다. 운영 Jenkins에서 점검이 실패한 빌드는 아직 확인하지 않았다.

## 빌드 결과 확인

운영 Jenkins는 Ops 서버의 `ops-jenkins` 컨테이너에서 돈다. 빌드 결과와 콘솔 로그는 Ops 서버에 ssh로 접속해 Jenkins 데이터 폴더에서 읽는다. 브라우저 로그인이나 API 토큰이 필요 없다. 읽기만 하고 파일을 바꾸지 않는다.

```sh
OPS=<Ops 서버의 ssh 호스트>
J=/var/lib/docker/volumes/ops_jenkins_home/_data/jobs/수리맵/builds
ssh "$OPS" "ls $J | sort -n | tail -3"                          # 최근 빌드 번호
ssh "$OPS" "grep -o '<result>[A-Z]*</result>' $J/<번호>/build.xml"  # SUCCESS, UNSTABLE, FAILURE, ABORTED
ssh "$OPS" "grep 'Path display timing' $J/<번호>/log"           # 단계 로그 검색
```

빌드가 도는 동안에는 `build.xml`에 결과가 아직 없고 `log`가 계속 늘어난다.
