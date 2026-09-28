# 제목

[BE] 상황판 조회 중 DB 커넥션을 확보하지 못해 요청이 실패하는 문제

# 본문

## 문제 배경

상황판은 서버의 변경 알림(SSE)을 받으면 최신 경로·마커·패키지 설치 상태를 다시 조회합니다. 놓친 알림을 재접속 후 다시 받더라도, 이 조회가 완료돼야 지도에 최신 상태를 표시할 수 있습니다.

서버는 SQL을 실행할 때 데이터베이스 커넥션 풀에서 DB 연결을 빌립니다. 사용할 연결이 없으면 반환될 때까지 기다리고, 설정된 대기시간을 넘으면 요청이 실패합니다.

2026-09-28 Jenkins #44로 배포한 `b1eff4f9`에서 브라우저 복구 검증을 준비하던 중 요청이 지연됐습니다. 서버에는 커넥션 10개가 모두 사용 중이고, 추가 커넥션을 30초 동안 얻지 못했다는 오류가 반복됐습니다. 브라우저를 오프라인으로 바꾸기 전에도 발생했으므로 검증 도구가 통신을 끊어서 생긴 문제로만 볼 수는 없습니다.

### 문제 해결: 체크리스트

- [x] 상황판 조회 안에서 패키지 상태를 읽을 때 기존 DB 커넥션을 재사용하는가?
- [x] 풀의 커넥션을 모두 사용 중인 동시 조회에서도 추가 연결 대기로 실패하지 않고 결과를 반환하는가?
- [x] 패키지 설치 상태 조회 결과와 기존 저장 동작을 유지하는가?
- [ ] 배포 후 SSE 재접속·상황판 재조회·지도 반영을 한 번에 검증하는가?

## 원인 분석과 선택지

처음에는 SQL 실행이 오래 걸리는지, DB 잠금에 막혔는지, 애플리케이션이 추가 커넥션을 기다리는지 구분해 확인하기로 했습니다. 커넥션 풀 크기를 늘리기 전에 어느 호출이 연결을 사용하고 추가로 요구하는지 추적합니다. 조사 결과와 후속 변경은 아래 댓글에 기록합니다.

# 댓글

## 변경 내용과 트레이드오프

- 원인: 상황판 조회가 커넥션 하나를 잡은 채 패키지 상태 조회에서 두 번째 커넥션을 요구했습니다.
- 변경: 패키지 상태 조회가 상황판의 기존 트랜잭션과 커넥션을 사용하도록 바꿨습니다. 동일한 실제 DB 테스트에서 수정 전 실패와 수정 후 통과를 확인했습니다.
- 범위: SQL·API·운영 커넥션 풀 크기·저장 메서드는 유지했습니다. 배포 후 SSE 재접속부터 지도 반영까지 확인하는 작업은 남아 있습니다.

### 커넥션 대기: 기존 연결을 잡은 채 새 연결 요청

상황판은 경로·마커뿐 아니라 업무폰의 오프라인 자료 설치 상태도 함께 조회합니다. 수정 전에는 이 과정의 트랜잭션이 다음과 같이 나뉘어 있었습니다.

1. [상황판 조회](../../../backend/src/main/java/com/surimap/board/DefaultIncidentBoardSourceRowCollector.java)의 `collect()`가 읽기 전용 트랜잭션을 시작하고 커넥션 하나를 확보합니다.
2. 마커 알림까지 조회한 뒤 [패키지 상태 조립](../../../backend/src/main/java/com/surimap/board/PackageBadgeBoardAssembler.java)을 거쳐 `OfflinePackageService.byIncident()`를 호출합니다.
3. [서비스](../../../backend/src/main/java/com/surimap/offlinepackage/service/OfflinePackageService.java)에 붙어 있던 `REQUIRES_NEW`가 별도 트랜잭션을 시작하려고 새 커넥션을 요구합니다. 이때 상황판이 확보한 첫 번째 커넥션은 반환되지 않습니다.

같은 시점의 서버 상태와 오류 기록도 이 흐름을 뒷받침했습니다.

| 확인한 항목 | 관측 결과와 의미 |
|---|---|
| HikariCP | 사용 중 10개·유휴 0개·대기 33개. 빌릴 수 있는 연결이 없었습니다. |
| PostgreSQL | 애플리케이션 연결 10개가 `idle in transaction`·`ClientRead` 상태였습니다. 트랜잭션은 열려 있지만 SQL을 실행 중이지는 않았습니다. |
| 마지막 SQL·DB 잠금 | 모두 마커 알림 SELECT 이후 대기했고, `pg_blocking_pids`는 빈 목록이었습니다. 관측 시점에는 다른 DB 세션의 잠금에 막힌 상태가 아니었습니다. |
| 서버 예외 | 패키지 상태 조회의 새 트랜잭션을 시작하다가 `HikariPool.getConnection()`에서 30초 대기 후 실패했습니다. |

각 요청이 기존 연결을 잡은 채 추가 연결을 기다리면 커넥션 풀이 고갈될 수 있습니다. Spring도 `REQUIRES_NEW`를 사용할 때 같은 위험을 설명합니다. [Spring 6.2 트랜잭션 전파 문서](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/tx-propagation.html#tx-propagation-requires-new).

### 조회 트랜잭션: 기존 커넥션 재사용

현재 [Repository의 `byIncident()`](../../../backend/src/main/java/com/surimap/offlinepackage/service/OfflinePackageRepository.java)는 설치 상태를 SELECT하고 결과를 변환합니다. 별도로 저장하는 동작은 없습니다.

Git 이력에서는 [`27eb44c7`](https://github.com/sonic8-8/suri-map/commit/27eb44c7a5e1347a5b17d64b2b4997910c24318a)이 `REQUIRES_NEW`를 추가했고, 이후 [`054406a1`](https://github.com/sonic8-8/suri-map/commit/054406a12eea75dfd14ed4267cb4e09a33c70ea2)이 조회 중 시험용 패키지 자료를 저장하던 호출을 제거했습니다. 저장 호출은 없어졌지만 별도 트랜잭션 설정은 남았습니다. 최초에 그 설정을 선택한 이유까지 커밋 메시지만으로 확정하지는 않습니다.

서비스의 `byIncident()`를 `@Transactional(readOnly = true)`로 바꾸고, 사용하지 않게 된 `Propagation` import를 제거했습니다. 기본 전파 방식인 `REQUIRED`가 적용돼 상황판의 기존 트랜잭션과 커넥션을 사용합니다. 별도 저장이 없는 조회이므로 커넥션 풀을 늘리는 대신 불필요한 추가 연결 요청을 없앴습니다.

외부 트랜잭션이 없는 호출에서는 읽기 전용 트랜잭션을 시작합니다. 외부 트랜잭션이 있으면 그 설정과 완료·실패 범위를 따르며, 패키지 조회만 따로 커밋하는 경계는 만들지 않습니다. 다른 저장 메서드의 트랜잭션은 바꾸지 않았습니다.

### 회귀 테스트: 두 요청의 상태 조회 완료

[OfflinePackageServiceTest](../../../backend/src/test/java/com/surimap/offlinepackage/OfflinePackageServiceTest.java)에 실제 Spring 서비스·MyBatis·PostgreSQL을 연결하는 테스트 한 개를 추가했습니다.

풀을 커넥션 2개로 제한하고 읽기 트랜잭션 두 개가 각각 연결을 확보한 뒤, 동시에 패키지 상태를 조회하도록 구성했습니다. 두 요청 모두 저장한 `READY` 상태를 반환해야 통과합니다. 어노테이션 이름이나 메서드 호출 횟수가 아니라 조회 완료와 반환값을 확인합니다.

커넥션 2개는 이 대기 구조를 재현하기 위한 시험 조건입니다. 연결 대기 1초와 준비·결과 회수의 10초 제한도 테스트가 끝없이 기다리지 않게 하는 값이며, 운영 설정이나 성능 목표가 아닙니다.

## 검증 결과

Docker 실행 환경을 복구한 뒤 준비된 테스트를 실행했습니다. 수정 전에는 실제 PostgreSQL에서 추가 커넥션을 기다리다가 실패했고, 트랜잭션 설정만 바꾼 뒤에는 같은 테스트가 통과했습니다.

| 검증 | 결과 |
|---|---|
| 수정 전 PostgreSQL 회귀 테스트 | 1개 실행·1개 실패. 커넥션 2개가 모두 사용 중인 상태에서 새 연결을 1초 동안 얻지 못했습니다. |
| 수정 후 동일 회귀 테스트 | 1개 통과. 두 요청 모두 저장한 사건·설치 식별자와 `READY`·오프라인 사용 가능 상태를 반환했습니다. |
| WSL에서 Backend 전체 실행 | 1,330개 중 1,323개 통과·7개 실패, 오류·건너뜀 0개. 실패는 모두 `TomcatProxyTest`의 로컬 HTTP 접속 오류였습니다. |
| Docker에서 Backend 전체 재실행 | 223개 클래스·1,330개 통과, 실패·오류·건너뜀 0개. 앞서 실패한 HTTP 검사 7개와 새 DB 회귀 테스트를 포함합니다. |

Docker 검사는 현재 소스·설정·공용 입력을 읽기 전용으로 연결한 기존 CI 이미지에서 `./gradlew --no-daemon --no-build-cache test --rerun-tasks`로 실행했습니다. 기본 설정에 따라 성능 태그는 제외했으며 전체 명령에 4분 59초가 걸렸습니다. 운영 요청의 응답 시간이나 부하 성능을 측정한 결과는 아닙니다. WSL과 Docker의 JDK 패치 버전도 달라, HTTP 접속 오류의 근본 원인까지 확정하지는 않았습니다.

기존 패키지 동작도 전체 실행 안에서 확인했습니다. [설치 상태 보고](../../../backend/src/test/java/com/surimap/offlinepackage/OfflinePackageInstallationApiRedTest.java) 8개, [수색 구역 변경](../../../backend/src/test/java/com/surimap/offlinepackage/OfflinePackageSearchAreaChangedConsumerRedTest.java) 3개, [파기 처리](../../../backend/src/test/java/com/surimap/offlinepackage/OfflinePackagePurgeHookRedTest.java) 7개, [패키지 자료 조립](../../../backend/src/test/java/com/surimap/offlinepackage/OfflinePackageManifestSourceIntegrationTest.java) 4개가 통과했습니다. 이 기존 검사들은 H2와 일부 대역을 사용하므로, 새 PostgreSQL 회귀나 실제 업무폰·브라우저 검증과 구분합니다.

해결 체크리스트의 앞 세 항목은 이 코드·회귀 검증을 근거로 완료했습니다. 마지막 항목인 배포 후 SSE 재접속 → 상황판 재조회 → 지도 반영의 전체 복구는 미완료입니다. 이번 결과는 기존 미커밋 변경을 포함한 로컬 작업 트리 기준이며, 커밋·푸시·배포·부하 시험은 하지 않았습니다.

명령·환경 복구·수정 전후 로그·JUnit 결과는 [이번 검증 기록(로컬 전용)](../../../_workspace/board-pool-verify-20260928.kAymhc/RESULTS.md)에 보존했습니다. 최초 Docker 연결 실패 기록도 유지했습니다.

앞선 진단에서 일부 HTTP 재전송이 성공하고 브라우저 종료 후 커넥션 대기가 0으로 돌아온 것은 전체 복구 통과를 뜻하지 않습니다. 당시 마커 9개·전송 작업 622개·마지막 순번 30·기준 마커 버전 15를 확인했고, 마커 수정·서버 재시작은 하지 않았습니다. 도구의 비교 실행과 상세 기록은 [이전 진단 기록(로컬 전용)](../../../_workspace/sse-db-deploy-20260928.oTBOB4/RESULTS.md)에 보존했습니다. 과거 자동 검사의 모든 실패를 이번 원인으로 소급해 확정하지 않습니다.

GitHub에는 아직 등록하지 않은 로컬 이슈입니다.
