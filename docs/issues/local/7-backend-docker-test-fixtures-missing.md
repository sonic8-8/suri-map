# 제목

[BE/Infra] 테스트 입력을 옮긴 뒤 Docker 빌드에서 파일을 찾지 못하는 문제

# 본문

## 문제 배경

2026-09-28(KST), Jenkins 배포를 준비하면서 테스트 입력의 위치와 Dockerfile을 대조했습니다. 공용 JSON은 `docs`에서 `test-fixtures`로 옮겼지만, Dockerfile은 여전히 `docs`만 복사합니다. 로컬 테스트는 파일을 읽을 수 있어도 같은 소스로 만든 Docker 빌드 환경에는 입력이 없는 상태입니다.

### 문제 해결: 체크리스트

- [x] 수정 전 Docker 이미지에 실제로 입력이 없는가?
- [x] 수정 후 같은 경로로 입력을 읽을 수 있는가?
- [ ] Backend 전용 JSON도 Docker 내부에서 기존 테스트가 읽을 수 있는가?
- [ ] Jenkins에서 해당 커밋의 이미지 빌드·배포가 완료되는가?

## 원인과 수정

[Backend Dockerfile](../../../backend/Dockerfile)의 `source` 단계에 `COPY test-fixtures /test-fixtures` 한 줄을 추가합니다. 작업 디렉터리 `/workspace`에서 `../test-fixtures/common-fixtures.json`을 읽는 [기존 로더](../../../backend/src/test/java/com/surimap/sync/outbox/CommonFixtureJson.java)의 경로와 맞춥니다. JSON 내용·Backend/Android 소비 경로·테스트 제외 조건은 바꾸지 않습니다.

## 검증 범위

수정 전 `6a256834`의 Git 입력으로 실제 Dockerfile의 `source` 이미지를 빌드했습니다. 컨테이너에서 다음 명령은 종료 코드 1로 실패했고 파일이 없음을 확인했습니다.

```bash
docker run --rm suri-map-sse-source-check:20260928 \
  sh -ec 'test -r /test-fixtures/common-fixtures.json'
```

이는 Docker 내부의 입력 누락을 재현한 검사입니다. 수정 전 Jenkins 전체 빌드나 JUnit 실패를 실행해 관측한 결과는 아닙니다. 기존 로컬 작업 트리의 Backend 1,296개 통과와 Docker 입력 포함 여부를 구분합니다. 수정 후 확인과 Jenkins 실행 결과는 이어서 기록합니다.

같은 Dockerfile에 복사 명령을 추가한 `suri-map-sse-source-check:20260928-fixed`에서는 같은 입력 경로 검사가 통과했습니다. 컨테이너와 저장소 JSON의 SHA-256도 일치했습니다. 새 검사 프레임워크·테스트 파일이나 입력 복제본은 추가하지 않았으며, Jenkins 전체 이미지 빌드·배포는 별도로 확인합니다.

## Jenkins에서 추가로 확인한 경로 문제

`676e4bed`를 받은 Jenkins #34는 Backend 이미지 빌드의 테스트 단계에서 실패했습니다. `BoardApiSseConvergenceHarnessRedTest`의 2개 테스트가 `NoSuchFileException`으로 실패했습니다. 공용 JSON과 별개로, Backend 전용 JSON을 저장소 루트 기준 `backend/src/test/resources`에서 찾고 있었습니다. Docker에서는 같은 파일이 `/workspace/src/test/resources`에 있습니다.

해당 JSON을 읽는 두 호출을 Java classpath 리소스 조회로 변경합니다. JSON 내용과 검증 항목은 유지하며, 공용 JSON의 저장소 로더와 구분합니다. 파일을 중복 복사하거나 테스트를 제외하지 않습니다. 클래스의 `Red`·`Harness` 명명은 기존 [이름 정리 후보](../../refactoring/codebase-naming-candidates.md)에 이미 등록되어 있으며 이번 배포 수정에서는 변경하지 않습니다.

#34 로그의 집계는 `1195 tests completed, 2 failed, 209 skipped`입니다. Docker 빌드에서는 Docker 연결이 필요한 테스트가 건너뛰어질 수 있으므로, 성공하더라도 로컬의 실제 DB 검증이나 배포 후 HTTP·DB·브라우저 검증을 대신하지 않습니다.

수정한 로컬 작업 트리에서 `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon test --console=plain`을 다시 실행했습니다. 실제 DB 테스트를 포함해 222개 클래스·1,296개가 통과했습니다(실패·오류·건너뜀 0, 4분 7초, 기본 성능 태그 제외). 변경 Java 파일의 포맷 검사와 `git diff --check`도 통과했습니다. Docker 경로에서의 최종 확인은 다음 Jenkins 빌드 결과로 남깁니다.
