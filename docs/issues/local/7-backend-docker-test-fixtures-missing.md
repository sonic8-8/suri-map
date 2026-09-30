# 제목

[BE/Infra] 테스트 입력을 옮긴 뒤 Docker 빌드에서 파일을 찾지 못하는 문제

# 본문

## 문제 배경

Backend 테스트는 JSON 파일을 입력으로 사용합니다. 같은 소스라면 로컬과 Jenkins의 Docker 빌드에서도 필요한 입력을 읽을 수 있어야 합니다. 로컬에서만 파일을 찾을 수 있으면 테스트가 통과해도 배포 이미지를 만드는 과정에서 실패할 수 있습니다.

2026-09-28(KST) 배포 준비 중 공용 JSON을 `docs`에서 `test-fixtures`로 옮긴 뒤 Dockerfile의 복사 대상은 바꾸지 않은 것을 발견했습니다. 수정 전 `6a256834`로 Dockerfile의 `source` 이미지를 빌드해 확인하니 `/test-fixtures/common-fixtures.json`이 없었습니다. 이때 확인한 것은 파일 누락이며, Jenkins 전체 빌드나 JUnit 실패를 실행해 관측한 결과는 아니었습니다.

### 문제 해결: 체크리스트

- [x] Docker 안에서 기존 로더가 공용 JSON을 읽고, 그 내용이 저장소 원본과 일치하는가?
- [x] Backend 전용 JSON을 로컬·Docker 양쪽에서 읽어 기존 검사를 수행하는가?
- [x] 입력 누락으로 테스트를 제외하지 않고 Jenkins 이미지 빌드·배포가 완료되는가?

## 원인 분석과 선택지

[공용 JSON 로더](../../../backend/src/test/java/com/surimap/sync/outbox/CommonFixtureJson.java)는 작업 디렉터리의 상위 경로에서 `test-fixtures/common-fixtures.json`을 찾습니다. 로컬 저장소에는 파일이 있지만, 당시 Dockerfile은 `docs`만 복사하므로 컨테이너의 같은 상대 경로에는 없었습니다.

JSON을 복제하거나 테스트를 제외할 필요는 없습니다. [Backend Dockerfile](../../../backend/Dockerfile)에 공용 입력 복사를 추가해 기존 Backend·Android 소비 경로와 JSON 내용을 유지하기로 했습니다. 이 변경으로 전체 Docker 빌드까지 통과하는지는 별도로 확인해야 했습니다.

# 댓글

## 변경 내용과 트레이드오프

- 1차 수정: Docker 이미지에 공용 JSON을 복사해 기존 로더가 읽을 수 있도록 했습니다.
- 2차 수정: 이후 Jenkins에서 드러난 Backend 전용 JSON 경로 문제를 classpath 조회로 수정했습니다.
- 유지: JSON 내용·검증 항목·기존 테스트 제외 조건은 바꾸지 않았습니다. 파일 누락을 피하려고 검사를 건너뛰지는 않았습니다.

### 공용 JSON: Docker 복사 대상에 이동한 디렉터리 추가

Dockerfile의 `source` 단계에 `COPY test-fixtures /test-fixtures`를 추가했습니다. 컨테이너 작업 디렉터리 `/workspace`에서 기존 상대 경로로 접근할 수 있게 한 변경입니다. `676e4bed`에 반영했습니다.

### Backend 전용 JSON: 작업 디렉터리 대신 classpath에서 조회

1차 수정 후 Jenkins #34는 `BoardApiSseConvergenceHarnessRedTest`의 2개 검사에서 `NoSuchFileException`으로 실패했습니다. 공용 JSON과 별개로 Backend 전용 JSON을 `backend/src/test/resources`에서 찾고 있었지만, Docker에서는 같은 파일이 `/workspace/src/test/resources`에 있었습니다.

`d1f04fb7`에서 해당 파일을 읽는 두 호출을 Java classpath 조회로 변경했습니다. 테스트 리소스를 작업 디렉터리 기준으로 찾지 않도록 한 것입니다. 공용 JSON 로더는 유지했습니다. 테스트 이름의 `Red`·`Harness`는 [기존 정리 후보](../../refactoring/codebase-naming-candidates.md)에 남기고 이번 수정에서 바꾸지 않았습니다.

## 검증 결과

| 단계 | 확인한 결과 |
|---|---|
| 수정 전 `source` 이미지 | 공용 JSON 읽기 검사 실패, 종료 코드 1 |
| 복사 명령 추가 후 이미지 | 같은 경로 검사 통과, 컨테이너와 저장소 JSON의 SHA-256 일치 |
| Jenkins #34 | 전용 JSON 경로 문제로 실패. 로그 집계는 `1195 tests completed, 2 failed, 209 skipped` |
| 2차 수정 후 로컬 Backend | 실제 DB 검사 포함 222개 클래스·1,296개 통과, 실패·오류·건너뜀 0, 4분 7초. 기본 성능 태그 제외 |
| `d1f04fb7`의 Jenkins #35 | 5분 4초에 성공. `test jacocoTestReport`·`bootJar` 통과 후 Backend·Frontend 배포 |

공용 JSON 누락은 다음 읽기 검사로 재현했습니다. 수정 후에는 이미지 태그를 `suri-map-sse-source-check:20260928-fixed`로 바꿔 같은 명령을 실행했습니다.

```bash
docker run --rm suri-map-sse-source-check:20260928 \
  sh -ec 'test -r /test-fixtures/common-fixtures.json'
```

로컬 전체 검증은 Java 17에서 `./gradlew --no-daemon test --console=plain`으로 수행했고 변경 Java 포맷·공백 검사도 통과했습니다. 공용·전용 입력을 유지한 채 Jenkins 빌드까지 성공해 위 해결 기준을 충족했습니다.

다만 Docker 빌드의 기존 구성은 Docker 연결이 필요한 DB 테스트를 건너뛸 수 있습니다. Jenkins 성공을 해당 DB 검사 통과로 계산하지 않으며, 실제 DB를 사용한 로컬 결과와 구분합니다. 배포 후 API health·migration·브라우저 검증은 [로컬 이슈 6](6-sse-registration-lost-during-reconnect.md)에 기록했습니다. GitHub에는 등록하지 않은 로컬 이슈입니다.
