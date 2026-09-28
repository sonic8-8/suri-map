# 제목

[BE/Infra] Jenkins 배포에 Backend DB 테스트가 연결되지 않은 문제

# 본문

## 문제 배경

Backend에는 실제 PostgreSQL/PostGIS를 연결해 SSE 재전송·업무 데이터 저장을 검증하는 테스트가 있습니다. 이 테스트가 실패하거나 건너뛰어졌다면 배포 전에 알 수 있어야 합니다.

2026-09-28 SSE DB 재전송 변경 `b1eff4f9`를 푸시한 뒤 Jenkins #38의 실행 경로를 확인했습니다. 저장소의 `infra/Jenkinsfile`에 DB 테스트 단계를 추가했지만 실제 작업은 그 파일을 읽지 않았습니다. Jenkins 작업 설정에 별도로 저장된 `Checkout → Sync → Deploy` 스크립트가 Hetzner App 서버에서 이미지만 빌드하고 배포하고 있었습니다.

### 문제 해결: 체크리스트

- [x] 실제 Jenkins 작업이 별도 테스트 DB로 Backend 검사를 실행하는가?
- [x] 테스트 실패·필수 보고서 누락·SSE DB 검사 건너뜀을 배포 전에 차단하는가?
- [x] 실행 결과와 보고서를 Jenkins에서 확인할 수 있는가?

## 원인 분석과 선택지

기존 Dockerfile은 이미지 빌드 중 테스트를 실행했지만 Docker가 필요한 DB 검사는 건너뛸 수 있었습니다. `b1eff4f9`에서는 테스트를 컴파일하는 `testClasses`와 실제 검사를 분리하고, Docker 소켓을 연결한 테스트 컨테이너에서 실행하도록 저장소의 Jenkinsfile을 바꿨습니다.

그러나 실제 작업 설정을 먼저 대조하지 않아 이 실행 단계가 연결되지 않았습니다. #38의 `testClasses`는 컴파일이지 테스트 실행이 아니며, 다음 `bootJar -x test`도 테스트를 수행하지 않습니다. 당시 실제 자동 배포에서는 DB 검사뿐 아니라 Backend 테스트 전체를 실행하지 않았습니다.

해당 커밋만 분리한 로컬 Docker/JDK 17 검증에서는 222개 클래스·1,325개 테스트가 통과했습니다(실패·오류·건너뜀 0). 이 결과를 실제 Jenkins 테스트 통과로 대신 기록하지 않습니다.

기존 Hetzner 배포 방식을 유지하면서 실제 Jenkins 작업에 테스트 실행·실패 차단·보고서 수집을 연결하기로 했습니다. 저장소의 기존 Jenkinsfile 전체를 적용하면 배포 설정까지 달라질 수 있으므로, 파일 경로만 바꾸지는 않습니다.

설정 변경 전 확인(2026-09-28): 사용자가 실제 Jenkins 작업 변경을 승인했습니다. 당시 작업 설정에는 `Backend Test CI` 단계가 없었으며 다음 빌드 번호는 39였습니다. Ops 서버의 Docker 소켓과 Jenkins의 JUnit 플러그인은 사용할 수 있었지만, 관리 API 인증 수단이 없어 사용자에게 토큰 등록을 요청했습니다. 이 시점에는 설정 변경·빌드 실행·배포를 하지 않았습니다.

근거: Jenkins 작업 설정의 stage 목록, #38의 `testClasses`·`bootJar -x test` 실행 로그, [Backend CI 정의와 한계](../../../infra/ci/README.md). GitHub에는 아직 등록하지 않은 로컬 이슈입니다.

## 후속 수정과 검증

2026-09-28 토큰 등록 후 실제 작업에 `Backend Test CI`를 추가했습니다. 기존 배포·Discord 알림은 유지하고, `Checkout → Backend Test CI → Sync → Deploy` 순서로 바꿨습니다. 실행 정의는 여전히 Jenkins의 inline 설정이며, [hetzner.Jenkinsfile](../../../infra/ci/hetzner.Jenkinsfile)은 대조용 사본입니다. 이 파일을 수정하거나 푸시하는 것만으로 작업 설정이 바뀌지는 않습니다.

- 체크아웃한 커밋으로 테스트 이미지를 만들고 Ops 서버에서 별도 PostgreSQL/PostGIS를 연결합니다. 운영 DB 정보·데이터는 사용하지 않습니다.
- 테스트 실패, 필수 보고서 누락, 필수 SSE DB 검사 건너뜀을 배포 전에 차단합니다. 실패해도 회수한 보고서는 Jenkins에 보관합니다.
- JUnit 결과는 Jenkins의 Test Result에 게시하고, HTML·JaCoCo·SSE 검사 결과와 대상 커밋은 `ci-artifacts/backend-test/<빌드 번호>/`에 보관합니다.

### 실패 조건에서 배포를 막는가?

대상 커밋은 모두 `b1eff4f9`입니다. 아래 임시 실패 조건은 확인 후 작업 설정에서 제거했습니다.

| Jenkins 빌드 | 검증 조건 | 결과 |
|---|---|---|
| [#40](https://jenkins.sonic8-8.com/job/%EC%88%98%EB%A6%AC%EB%A7%B5/40/) | 테스트 명령을 종료 코드 23으로 실패시킴 | 실패 코드 유지, Sync·Deploy 건너뜀, 커밋·SSE 진단 결과 보관 |
| [#41](https://jenkins.sonic8-8.com/job/%EC%88%98%EB%A6%AC%EB%A7%B5/41/) | 명령은 성공하지만 필수 보고서 없음 | 빌드 실패, Sync·Deploy 건너뜀, 진단 결과 보관 |
| [#42](https://jenkins.sonic8-8.com/job/%EC%88%98%EB%A6%AC%EB%A7%B5/42/) | Docker 소켓 없이 실제 테스트 실행 | Gradle 성공이어도 필수 SSE DB 검사 건너뜀을 감지해 배포 차단. Jenkins 집계 통과 995개·건너뜀 257개 |

### DB 검사가 계속 건너뛰어진 이유는 무엇인가?

정상 설정으로 실행한 [#43](https://jenkins.sonic8-8.com/job/%EC%88%98%EB%A6%AC%EB%A7%B5/43/)에서도 DB 검사가 건너뛰어졌습니다. Docker 소켓은 연결됐지만, 기본 bridge 네트워크의 테스트 컨테이너가 Ryuk의 호스트 공개 포트에 접속하지 못했습니다. Ryuk는 테스트가 끝난 뒤 임시 컨테이너를 정리하는 Testcontainers 구성 요소입니다. 이 빌드도 필수 검사에서 실패해 배포하지 않았습니다.

같은 이미지에 `--network host`만 추가한 비교 실행에서는 `SseReplayServiceTest` 17개·`SseStreamServiceTest` 6개가 모두 통과했습니다(실패·오류·건너뜀 0, Gradle 53초). 이 설정을 실제 작업에 적용했습니다. Linux Ops 호스트의 네트워크를 공유하므로 신뢰하는 코드만 실행합니다. 방화벽 규칙·운영 서비스 네트워크를 바꾸거나 Ryuk를 비활성화하지 않았으며, 특정 방화벽 규칙이 원인이었다고 확정한 것은 아닙니다.

### 정상 실행에서 무엇을 확인했는가?

[Jenkins #44](https://jenkins.sonic8-8.com/job/%EC%88%98%EB%A6%AC%EB%A7%B5/44/)는 테스트·보고서 게시·배포까지 성공했습니다. 전체 빌드 5분 37초, Gradle `test jacocoTestReport` 5분 8초입니다.

- [Test Result](https://jenkins.sonic8-8.com/job/%EC%88%98%EB%A6%AC%EB%A7%B5/44/testReport/): 222개 클래스·1,325개 통과, 실패·오류·건너뜀 0. 기존 Gradle 설정의 성능 태그 제외는 유지했습니다.
- [보관 파일](https://jenkins.sonic8-8.com/job/%EC%88%98%EB%A6%AC%EB%A7%B5/44/artifact/ci-artifacts/backend-test/44/): JUnit XML 222개, HTML 테스트 보고서, JaCoCo XML·HTML, SSE 검사 결과, 대상 커밋을 확인했습니다. 재전송·실시간 전송 검사 모두 PASS입니다.
- 배포 후 API health는 HTTP 200입니다. Backend가 새로 시작됐고 JAR의 SHA-256은 기존 배포와 같습니다. 제품 코드를 추가로 바꾼 것이 아니라 같은 `b1eff4f9`를 CI로 검사하고 재배포한 결과입니다.
- 빌드 후 Jenkins의 정상 설정을 다시 조회해 로컬 사본과 일치함을 확인했습니다. 브라우저 전체 복구·부하 시험은 이번 검증에 포함하지 않았으며 별도로 남아 있습니다.

이 수정에서 새 플러그인·제품 코드·테스트·의존성은 추가하지 않았습니다. 설정 사본과 기록 문서는 아직 커밋·푸시하지 않았습니다.

### 설정 변경 중 별도로 발생한 일

최초 설정 저장은 UTF-8 문자 처리 오류로 HTTP 500을 반환했습니다. 에이전트가 저장 실패 후 빌드를 이어서 호출하는 실행 절차 오류를 내어 #39는 기존 설정으로 같은 커밋을 재배포했습니다. Backend JAR의 SHA-256은 이전과 같았고 API health 200을 확인했습니다. #39는 테스트 통과 근거에서 제외합니다. 이후 요청에 UTF-8을 명시하고, 설정 저장·재조회·내용 일치를 확인한 뒤에만 빌드를 별도로 호출하도록 수정했습니다.

변경 전 설정은 Ops 서버의 `/srv/ops/config-backups/suri-map-ci.cqQeKl/config.xml`에 보관했습니다. API 토큰은 Git 제외·권한 제한된 로컬 파일로만 사용했으며 이 문서나 Jenkins 스크립트에 넣지 않았습니다.
