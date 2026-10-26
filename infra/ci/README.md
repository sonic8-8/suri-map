# Backend CI 실행

Hetzner Jenkins의 `수리맵` 작업은 다음 순서로 실행한다.

`Checkout → Backend Test CI → Sync → Build Images → Deploy Backend → Verify Backend → Deploy Web`

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
