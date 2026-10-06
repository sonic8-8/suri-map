# Backend CI 실행

Hetzner Jenkins의 `수리맵` 작업은 다음 순서로 실행한다.

`Checkout → Backend Test CI → Sync → Build Images → Approve Backend Deployment → Deploy Backend → Verify Backend → Deploy Web`

실행 정의는 Jenkins 작업 설정에 저장된 **inline 스크립트**다. [hetzner.Jenkinsfile](hetzner.Jenkinsfile)은 이 설정을 보관·대조하는 파일이며, Jenkins가 저장소에서 자동으로 읽는 파일은 아니다. 변경할 때는 Jenkins 문법 검사 → 작업 설정 저장 → 저장된 스크립트 재조회·대조를 마친 뒤 빌드를 실행한다.

기존 [infra/Jenkinsfile](../Jenkinsfile)은 다른 배포 설정을 포함하며 현재 작업에서 사용하지 않는다. 이 파일을 수정하는 것만으로 Hetzner CI가 바뀌지 않는다. 테스트 연결이 빠졌던 원인과 실제 빌드별 결과는 [로컬 이슈 10](../../docs/issues/local/10-jenkins-backend-tests-not-connected.md)에 기록한다.

## 경로 조회 전환의 배포 대기

Backend와 웹을 동시에 시작하지 않도록 배포를 분리한다. 현재 파이프라인에서는 모든 빌드가 두 확인 단계에서 멈추며, 승인 없이 다음 단계로 넘어가지 않는다. 전환 후 일반 자동 배포로 복귀할지는 별도로 결정한다.

| 단계 | 수행·확인할 내용 |
|---|---|
| Build Images | 이미지만 만든다. 실행 중 앱은 바꾸지 않는다. |
| Approve Backend Deployment | 기존 이미지·설정 보존, 외부 쓰기 차단, 기존 Backend·시험 writer 중단, 진행 중 쓰기 종료, 최종 DB 백업을 확인한 뒤 승인한다. |
| Deploy Backend | `up -d --no-deps backend`로 Backend만 시작한다. Flyway는 이때 실행된다. |
| Verify Backend | migration 성공·원본 데이터 보존·조회 설정·인증된 실제 HTTP 응답을 검증하고 기록한 뒤 승인한다. health 정상만으로 진행하지 않는다. |
| Deploy Web | `up -d --no-deps frontend mock-112`로 웹과 시연 서버를 시작한다. 검증한 Backend와 DB·인증·저장소는 재생성하지 않는다. |

대기에는 Jenkins 기본 [input 단계](https://www.jenkins.io/doc/pipeline/steps/pipeline-input-step/)를 사용한다. 승인자·승인/중단은 빌드에 남지만, 버튼을 누르는 것이 외부 쓰기 차단·백업·검증을 실행하거나 결과를 입증하지는 않는다. 검증 기록을 별도로 남기고 대기 중 다른 배포나 설정 변경을 하지 않는다. `disableConcurrentBuilds()`는 이 작업의 동시 빌드만 막는다.

중간 배포 단계부터 다시 실행해 확인 절차를 건너뛰지 않도록 `disableRestartFromStage()`를 적용한다. 새 빌드는 처음부터 검증한다. 이 전환용 구조는 기존 최상위 `agent any`를 유지하므로 승인 대기 중에도 실행 슬롯 하나를 점유한다.

기존 DB·Keycloak·저장소가 준비된 서버 전환용이다. `--no-deps`이므로 새 서버의 기반 서비스 설치를 대신하지 않는다. Backend 시작 후 검증을 중단해도 DB·앱을 자동으로 되돌리지 않는다. 쓰기 차단을 유지하고 migration 상태·보존한 백업·이후 기록을 확인해 복구 범위를 정한다. 최종 SUCCESS도 실제 브라우저·Smoke 통과를 뜻하지 않는다.

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
