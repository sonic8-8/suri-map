# 제목

[BE] 같은 요청의 마커 이벤트가 코드의 발행 호출 순서와 다르게 전송되는 문제

# 본문

## 문제 배경

2026-09-28(KST), `d1f04fb7`을 Jenkins #35로 배포한 뒤 실제 HTTP·DB·SSE·브라우저를 연결해 발견 마커 4개를 생성했습니다. [생성 서비스](../../../backend/src/main/java/com/surimap/app/service/marker/AppMarkerService.java)는 `MARKER_CREATED`를 먼저 발행하고 `PERSON_FOUND`를 발행하지만, 4건 중 3건은 발견 알림에 더 작은 SSE 순번이 배정돼 먼저 전달됐습니다.

마커·알림 저장과 화면 갱신은 4건 모두 성공했습니다. 이번에 관측한 것은 관련 이벤트의 호출 순서가 보존되지 않는 현상이며, 화면 누락·데이터 유실이나 전체 SSE 순번의 역전은 아닙니다.

### 문제 해결: 체크리스트

- [x] 실제 서버에서 발행 호출 순서와 DB·수신 순번의 차이를 확인했는가?
- [x] 서비스의 발행 순서와 worker의 작업 선택 SQL을 대조했는가?
- [x] 같은 저장 시각·역순 UUID로 재현하는 기존 Mapper·Worker 테스트를 추가했는가?
- [x] 관련 이벤트의 순서 보존 방식을 정하고 수정했는가?
- [ ] 수정 후 같은 HTTP·DB·브라우저 조건에서 순서를 다시 확인했는가?

## 확인한 현상

| 브라우저 조건 | 마커 생성 순번 | 발견 알림 순번 | 발행 호출 순서 유지 |
|---|---:|---:|---|
| 최초 진입 | 2 | 1 | 아니요 |
| 새로고침 | 4 | 3 | 아니요 |
| 다른 화면에서 복귀 | 5 | 6 | 예 |
| 다시 새로고침 | 8 | 7 | 아니요 |

두 이벤트씩 같은 `created_at`을 가졌으며 8개 작업 모두 `COMPLETED`였습니다. 브라우저가 받은 각 이벤트의 SSE 순번도 DB와 일치했습니다. 사건은 `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001`이며 마커 ID와 전체 연결 검증은 [로컬 이슈 6](6-sse-registration-lost-during-reconnect.md)에 남겼습니다.

## 코드·DB 대조 결과

수정 전 [작업 선택 SQL](../../../backend/src/main/resources/mapper/event/EventDispatchJobMapper.xml)의 `claimPending`은 미확정 작업을 `created_at, id` 순으로 골랐습니다. [전송 작업](../../../backend/src/main/java/com/surimap/eventhub/adapter/EventDispatchJob.java)의 `id`는 `UUID.randomUUID()`로 만듭니다. 실제 네 쌍은 저장 시각이 같아 UUID 비교로 순서가 정해졌으며, 관측한 순번도 그 정렬 순서와 일치합니다.

따라서 배포된 코드의 순번은 worker가 선택한 순서를 보존하지만, 같은 업무 트랜잭션 안의 발행 호출 순서를 기록하지는 않습니다. 반복 횟수를 늘려 우연히 기대 순서가 나오는 것으로 해결 판정하지 않습니다. 동일 시각과 역순 UUID를 고정해 검증하고, 기존 완료 이력·재시도 순번·동시 업무 트랜잭션에 미칠 영향을 함께 확인해야 합니다.

배포 직후 조사에서는 읽기 전용 DB 조회와 코드 대조까지만 진행했습니다. 이후 로컬 수정·검증은 아래와 같습니다. 서버 DB와 기존 행의 SSE 순번은 변경하지 않았습니다.

## 수정과 검증 (2026-09-28)

- **재현**: 기존 `EventDispatchJobMapperTest`에 같은 시각·역순 UUID를 고정했습니다. 먼저 저장한 `…9002`가 아니라 나중에 저장한 `…9001`을 선택해 수정 전 실패했습니다.
- **변경**: [새 migration](../../../backend/src/main/resources/db/migration/V20260928_001__add_event_dispatch_job_insertion_order.sql)으로 `insertion_order`를 추가했습니다. 이후 INSERT에서 DB가 내부 번호를 발급하고, `claimPending`은 저장 시각이 같을 때 UUID보다 이 번호를 먼저 비교합니다. 이미 확정한 SSE 순번의 우선순위와 실패한 앞 작업을 기다리는 동작은 유지합니다.
- **보존**: 기존 행의 저장 순서는 추정하지 않고 `NULL`로 둡니다. 같은 시각의 기존 행끼리는 종전 UUID 정렬을 유지하므로 과거의 발행 순서를 복원하는 수정은 아닙니다. 새 내부 번호는 공개 SSE 순번·이벤트 UUID와 다르며 API·이벤트 필드는 바꾸지 않았습니다.

기존 행을 바꾸지 않도록 컬럼을 추가한 다음 기본값을 지정했습니다. PostgreSQL의 [기본값 변경](https://www.postgresql.org/docs/16/ddl-alter.html#DDL-ALTER-DEFAULT)은 이후 INSERT에 적용됩니다. [Sequence](https://www.postgresql.org/docs/16/functions-sequence.html)는 롤백으로 번호가 비어도 되므로 내부 정렬에만 사용하고, 연속성을 검사하는 공개 SSE 순번과 섞지 않습니다. 이번 수정은 같은 트랜잭션의 발행 순서를 보존하는 범위이며 서로 다른 트랜잭션의 커밋 순서를 보장하지 않습니다.

실제 PostgreSQL/PostGIS를 사용한 Mapper 12개·Service 21개·Worker 13개·Migration 1개, **총 47개가 통과했습니다**(실패·오류·건너뜀 0, 50초). Worker 테스트는 실제 `EventHub` 저장 → 업무 커밋 → 순번 확정 → 전송 경로에서 `MARKER_CREATED(1)` → `PERSON_FOUND(2)`를 확인합니다. 외부 전송 대상은 기록용 대역이며 실제 HTTP·브라우저 검증은 아닙니다. Migration 테스트는 완료·대기·실패·처리 중 작업과 원본 데이터, 이미 확정한 SSE 순번을 보존하는지 확인합니다.

Java 17과 Docker를 사용한 백엔드 전체 검증도 **222개 클래스·1,298개 통과**입니다(실패·오류·건너뜀 0, 4분 8초, 기본 설정상 성능 태그 제외).

```bash
cd backend
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew --no-daemon test --console=plain
```

변경한 테스트 Java 3개의 포맷, `git diff --check`, 관련 문서 3개의 로컬 링크 경로 111개도 확인했습니다.

커밋·푸시·배포와 수정 후 실제 HTTP·DB·브라우저 재검증은 하지 않았습니다. DB 이력 재전송 연결은 이번 수정 범위가 아닙니다.
