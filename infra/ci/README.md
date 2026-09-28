# Backend CI 실행

[Jenkinsfile](../Jenkinsfile)의 `Backend Test CI` → `S4 Evidence`가 성공해야 실행 이미지 빌드·배포로 넘어간다.

## DB 테스트 실행

[Backend Dockerfile](../../backend/Dockerfile)의 `tester` 이미지는 테스트 코드를 컴파일한다. 테스트 자체는 이미지 빌드 중이 아니라 컨테이너를 실행할 때 수행한다. 따라서 `docker build` 성공만으로 테스트 통과를 판단하지 않는다.

```sh
docker build --target tester -t suri-map-backend-test:local -f backend/Dockerfile .
docker run --rm \
  --mount type=bind,source=/var/run/docker.sock,target=/var/run/docker.sock \
  --add-host host.docker.internal:host-gateway \
  --env TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  suri-map-backend-test:local
```

저장소 루트에서 실행한다. 기본 명령은 `test jacocoTestReport`이며 성능 태그는 기존 Gradle 설정대로 제외한다. Gradle 테스트 결과 캐시는 사용하지 않는다.

- Testcontainers가 별도 PostgreSQL/PostGIS 컨테이너를 생성한다. 운영 DB 주소·인증 정보를 전달하거나 운영 DB 데이터를 사용하지 않는다.
- 테스트 컨테이너에 호스트 Docker 소켓을 연결한다. 호스트 Docker를 제어할 수 있는 권한이므로 신뢰하는 코드만 실행한다. 별도 Docker daemon을 띄우거나 Docker API를 TCP로 공개하지 않는다. 테스트 DB는 임시 동적 포트를 사용한다.
- `host-gateway`는 테스트 컨테이너가 DB의 동적 포트에 접근할 호스트 주소다. 현재 구성은 로컬 Unix 소켓 기반 Docker를 전제로 하며, 원격 Docker·rootless 환경까지 지원하는 설정은 아니다.

구성 근거: [Testcontainers의 컨테이너 내부 실행 안내](https://java.testcontainers.org/supported_docker_environment/continuous_integration/dind_patterns/), [Docker의 host-gateway 안내](https://docs.docker.com/reference/cli/docker/container/run/#add-host).

## 실패 처리와 결과 파일

Jenkins는 `docker create` → `docker start --attach`로 실행하고, 실패해도 컨테이너를 지우기 전에 결과를 복사한다. 테스트 종료 코드가 실패이거나 필수 보고서를 복사하지 못하면 배포 단계로 넘어가지 않는다. 복사 후 해당 테스트 컨테이너와 익명 Gradle 볼륨을 정리한다.

| 결과 | 위치·판정 |
|---|---|
| JUnit XML·HTML | `backend/build/test-results/test`, `backend/build/reports/tests/test`. Jenkins에서 게시·보관한다. |
| JaCoCo | `ci-artifacts/backend-test/coverage`. 실행·보고서 생성 전에 실패하면 없을 수 있다. |
| SSE DB 검사 | `collect-s4-evidence.sh`가 `SseReplayServiceTest`·`SseStreamServiceTest`의 실제 JUnit XML을 읽는다. `verify-s4-evidence.sh`는 실패·건너뜀·0개 실행·결과 누락을 거부한다. |
| SonarQube | 서버 미설정으로 `SKIPPED_NO_SERVER`를 기록한다. 분석 성공이 아니다. |

`S4`·`evidence` 파일명은 기존 Jenkins 호출·산출물 경로를 유지하기 위한 이름이다. DB 테스트 통과는 실제 서버 재시작·브라우저 복구·부하 시험 통과와 다르다.

기존 `verify-evidence.sh`는 커버리지 파일·요약·SonarQube 상태 파일의 존재와 문구를 확인하는 수동 검사다. 현재 Jenkins에서 직접 호출하지 않으며, 테스트 통과나 커버리지 기준 충족을 대신 판정하지 않는다.
