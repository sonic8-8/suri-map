# 수색 경로의 기록과 조회

경로 시작·중단·종료, GPS 묶음 저장·품질 검사·조회 방식을 바꿀 때 확인한다. 옛 S3-1 요구와 2026-09-21 정적 코드 대조 결과다. 기존 임계값을 현장 검증된 운영 기준으로 새로 확정하지 않았다.

## 경로의 주체와 생명주기

- 기록 주체는 계정이며 업무폰은 입력·전송 맥락이다. 사건·수색 차수·근무 교대를 구분한다. 앱은 경로를 기록하고 웹은 이동 유형을 보정한다. 종료된 사건의 쓰기와 다른 계정·차수의 경로에 좌표를 붙이는 요청을 허용하지 않는 것이 기존 요구다.
- [앱 서비스](../../backend/src/main/java/com/surimap/app/service/path/AppSearchPathService.java)는 시작 시 현재 차수·진행 중인 근무를 확인한다. 일시 중지·재개는 같은 경로에서 처리하고 종료 후 다시 시작하면 새 경로를 만든다. 서로 다른 경로 사이에 이동 선을 임의로 만들지 않는다.
- **추가 확인**: [좌표·조회·보정 Controller](../../backend/src/main/java/com/surimap/api/controller/path/SearchPathController.java)의 조회·보정에는 사건 접근·웹 채널 제한 애노테이션이 없고, 보정 계정은 `X-Account-Id`에서 읽는다. 앱 시작 시 검사만으로 모든 경로 API의 권한·사건 종료 검사가 충족됐다고 판단하지 않는다.

## 좌표 저장과 도형 조립

- [현재 서비스](../../backend/src/main/java/com/surimap/api/service/path/SearchPathService.java)는 새 묶음의 GPS 좌표를 순서와 함께 저장한다. 좌표를 추가할 때마다 이전 경로 전체를 읽고 LineString을 다시 만드는 방식은 [Issue #16](../issues/16-search-path-append-time-increases-with-length.md)에서 변경했다. 새 구간·제외 좌표도 묶어서 저장하며, 조회할 때 GPS 좌표를 순서대로 연결한다.
- GPS 원본 없이 도형만 있는 과거 경로는 조회용 fallback으로 읽는다. 그 경로에 새 좌표를 붙이는 요청은 `write_conflict`로 거부한다. 도형에서 측정 시각·속도 등 원본 값을 임의로 복원하지 않는다.
- **조회 일관성 수정 (2026-10-02, 배포 전)**: 경로 버전을 읽은 뒤 좌표를 별도 SQL로 읽는 사이 저장이 완료되면 이전 버전과 새 좌표가 한 응답에 섞였다. [경로 Mapper](../../backend/src/main/resources/mapper/path/SearchPathMapper.xml)의 조건별·전체 조회를 한 SQL로 묶어 버전·GPS·구간·제외 좌표를 같은 시점 기준으로 읽는다. 하위 컬렉션은 `UNION ALL`로 나열하고 MyBatis로 조립해 다중 JOIN의 행 증폭을 피한다. GPS는 `pointId`가 재사용돼도 DB의 수집 순번 기준으로 보존한다. 서비스는 조회된 좌표로 구간 범위·도형을 조립하며 API 형식·저장 트랜잭션·DB schema는 유지한다.
- **JDBC 수신 메모리**: 서비스의 조회 진입점에 읽기 전용 트랜잭션을 연결해 기존 `default-fetch-size: 100`을 사용한다. PostgreSQL JDBC는 자동 커밋 상태에서 이 설정만으로 결과를 나눠 받지 않는다. 기존 외부 트랜잭션에는 그대로 참여하며 격리 수준·커넥션 풀 크기는 바꾸지 않는다. 로컬 실제 DB의 468개 경로·336,960개 좌표·56,160개 구간 조회는 512MiB 힙에서 누락 없이 완료했다. HTTP·브라우저·동시 부하 검증은 아니다.
- **남은 읽기 비용**: JDBC 수신을 나눠도 최종 응답에는 전체 좌표를 조립한다. SQL 왕복은 줄었지만 전체 좌표의 전송·객체 생성·도형 조립 비용은 남는다. 운영 서버의 메모리·조회 시간과 실제 상황판 반영은 별도로 검증해야 한다. 옛 `sinceVersion`·`limit`·`geometryMode=SIMPLIFIED` 요구는 현재 조회 인자에 없고 정렬도 시작 시각이 아닌 버전 내림차순이다. 간략 표시 때문에 저장 원본을 바꾸지 않는 요구는 유지한다.
- **객체 생성 비용 수정 (2026-10-02, 배포 전)**: GPS 조회에서 MyBatis가 protected 생성자를 호출할 때마다 접근 예외를 처리하고 재시도하는 비용을 확인했다. [공통 객체 생성 설정](../../backend/src/main/java/com/surimap/config/MyBatisConfig.java)에 [Spring 기반 생성 방식](../../backend/src/main/java/com/surimap/config/mybatis/MyBatisObjectFactory.java)을 연결해 기본 생성자 접근을 호출 전에 준비한다. 생성자 제한·SQL·응답 형식은 유지한다. [실제 Mapper 검사](../../backend/src/test/java/com/surimap/domain/path/SearchPathMapperTest.java)에서 좌표 값과 순서를 보존하면서 반복 접근 예외가 사라짐을 확인했다. 누적 경로의 실제 HTTP 조회 개선량과 남은 읽기 비용은 배포 후 별도로 확인한다.

## 측정 시각과 품질 검사

- `clientTs`는 측정 시각을 보존하고, `elapsedRealtimeNanos`는 같은 기기에서 수집 순서를 판단하는 값으로 구분한다. **뒤 point의 `clientTs`가 앞 point보다 이르더라도 그 이유만으로 묶음 전체를 거부하지 않는다. `elapsedRealtimeNanos`가 앞의 비교 대상 point보다 작거나 같은 point는 도형에서 제외하고 제외 이유를 응답·이벤트·조회에서 확인할 수 있어야 한다.** 시각·순서 문제의 배경은 [Issue #12](../issues/12-search-path-gps-time-order.md)에 남긴다.
- [검증기](../../backend/src/main/java/com/surimap/domain/path/validation/GpsPointValidator.java)는 묶음 2~120개, 묶음 안의 `pointId` 중복·좌표 범위·소수점 자릿수를 검사한다. 구조 오류는 묶음을 거부하고, 낮은 정확도·시각 차이·잘못된 속도·좌표 도약·순서 오류는 좌표별 제외로 처리한다. 소수점 6자리 초과는 자동 반올림이 아니라 거부한다.
- **구현 차이**: 시각 차이 검사의 기준으로 실제 수신 시각 대신 첫 point의 `clientTs + 20초`를 넘긴다. 순서 검사는 같은 묶음에서 직전에 수용한 좌표와 비교하며, 도약 검사는 정확히 5초 간격일 때만 수행한다. 전체 수색 범위 인자는 사용하지 않는다. 이 동작을 모든 묶음·주기의 품질 보장으로 확대 해석하지 않는다.
- 자동 이동 유형 분류는 현재 묶음에서 연속 3개 이상인 속도 구간을 사용한다(차량 5m/s 이상, 도보 0.5~2.5m/s). 묶음을 나눴을 때의 분류 연속성과 품질 제외 후 유효 좌표 수는 별도 확인 대상이다. 이 수치는 차량 수집 주기나 현장 정확도 검증을 뜻하지 않는다.

## 수집과 전송은 별도 주기

- [위치 수집](../../android/app/src/main/java/com/surimap/core/location/LocationRecorder.kt)의 요청 간격 기본값은 5초다. GPS·네트워크 공급자의 실제 callback 간격과 동일하다고 가정하지 않는다.
- [묶음 기록기](../../android/app/src/main/java/com/surimap/feature/search/data/SearchPathGpsBatchRecorder.kt)는 최대 120개 또는 2개 이상이면서 측정 시각 범위가 10초 이상일 때 로컬 기록을 요청한다. 별도의 고정 10초 HTTP 타이머가 아니다. 남은 단일 좌표·화면 이동·전송 지연은 [GPS 누락 문제](../issues/5-search-path-gps-measurement-loss.md), [오프라인 저장](offline-sync.md)과 함께 확인한다.
- 부하 시험의 2.5초 좌표 간격과 요청률은 [Issue #8](../issues/8-gps-collection-transmission-basis.md)의 시험 조건이다. 이를 차량 판정·앱 주기 전환 정책이 구현됐다는 근거로 사용하지 않는다.

## 테스트 입력과 원문

- [공용 JSON](../../test-fixtures/common-fixtures.json)의 `gpsPath`·`pathGuardFixtures`와 기존 Java·Android 테스트를 유지한다. 원문의 8개 GPS 좌표·경로·구간 ID는 공용 값과 일치하며, 공용 구간에는 시작·끝 index/point ID가 더 있다. 실행 데이터와 공개 API·이벤트 필드를 변경하지 않았다.
- 현재 레이어별 검증 범위는 [수색 경로 정리 기록](../refactoring/search-path-layer-separation.md)을 참고하고 실제 테스트와 대조한다. 옛 테스트 이름·조회 p95 1초 목표·파기 Hook 계획을 실행·구현 완료로 취급하지 않는다. 파기 연결의 미확인 사항은 [데이터 파기](data-retention.md)에 남긴다.
- 과거 세부 요구·사례·경계값은 [S3-1 원문](https://github.com/sonic8-8/suri-map/blob/0cf8195500344f11adc9cf5dde0a38db636f4da1/docs/spec/specs/S3-1.json)에서 복원한다. 삭제 직전 미커밋 변경 2곳의 `clientTs`·`elapsedRealtimeNanos` 설명은 위 ‘측정 시각과 품질 검사’에 보존했다.
