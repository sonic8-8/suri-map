# 수색 차수와 인수인계

수색 차수 전환, 인수인계 메모, 수색 이력 요약을 바꿀 때 확인할 기록이다. 옛 S8의 필요한 요구와 구현 차이를 남겼다. **기존 요구**는 후속 작업에서 유효성과 범위를 다시 확인할 대상이며, 이번 정리로 새 구현 기준을 확정한 것은 아니다.

현재 구현 설명은 2026-09-18 정적 코드 대조 결과다. [제품 요구](../prd.md#인수인계이력-확인), [차수 기반 인수인계의 선택 이유](../adr/0029-operational-period-history.md), [API 참고](../api/api-spec.md#48-operational-period--duty-shift--handover)는 각각 목적·배경·요청 형식을 확인할 때 사용한다.

## 수색 차수 전환과 근무 교대를 구분

- 수색 차수(OP)는 사건의 수색 이력을 묶고, 근무 교대(DutyShift)는 업무폰을 인수해 근무한 구간을 나타낸다. 사건 가져오기 시 OP1을 내부에서 생성하고, 이후 차수는 웹 지휘관이 사유와 함께 연다는 기존 요구가 있다. 담당 구역 없는 초동 수색의 배정·업무폰 맥락도 유지해야 한다.
- [차수 전환 Service](../../backend/src/main/java/com/surimap/api/service/operationalperiod/OperationalPeriodApiService.java)는 한 트랜잭션에서 이전 차수를 종료하고 다음 번호의 차수를 만든다. `OTHER`는 사유 메모가 필요하지만 인수인계 메모는 선택이며, 입력하면 **이전 차수**에 저장한다. 메모가 없다는 이유로 전환을 막거나 이전 경로·구역·마커를 새 차수로 옮기지 않는 요구를 보존한다.
- 사건당 활성 차수 하나, 중복 요청의 중복 생성 방지, 저장·이벤트 발행의 원자성은 실제 DB·동시 요청으로 확인할 대상이다. 아래 대역 테스트의 통과만으로 이 조건이 검증됐다고 판단하지 않는다.

## 메모가 가리키는 대상과 작성 맥락

- **기존 요구**: 앱·웹의 사건 배정 계정이 차수·근무 교대·경로·구역·마커에 메모를 남긴다. 대상의 존재와 사건·차수 소속을 확인하고, 작성 계정·시각·채널·업무폰 맥락을 추적할 수 있어야 한다. 내용은 1~4,000자였다.
- **현재 차이**: [메모 Service](../../backend/src/main/java/com/surimap/api/service/handover/HandoverMemoApiService.java)는 현재 차수와 대상 종류를 확인하지만, 전달받은 대상 ID가 실제로 존재하고 같은 사건·차수에 속하는지는 조회하지 않는다. [Request](../../backend/src/main/java/com/surimap/api/controller/handover/request/CreateHandoverMemoRequest.java)는 공백을 거부하지만 최대 길이는 검사하지 않는다.
- [저장 SQL](../../backend/src/main/resources/mapper/handover/HandoverMemoMapper.xml)은 작성 계정·서버 시각을 남기지만 채널·업무폰·`clientTs`를 메모 행에 저장하지 않는다. 다른 감사 기록으로 추적되는지와 필요한 보존 범위를 별도로 확인한다. 일반 필드 검증 오류의 표현도 기존 공개 오류와 함께 정하며 임의 오류 코드를 추가하지 않는다.

## 미전송 기록을 기다린 뒤 요약

- **기존 요구**: 근무 종료 요청보다 앞선 같은 사건·업무폰·차수의 경로·마커·사진·메모 전송이 끝날 때까지 종료 요청을 보내지 않는다. `ACKED`·`FAILED_FINAL`·`PURGED`를 대기 종료 상태로 취급하되, 최종 실패를 서버 저장 성공과 혼동하지 않는다.
- [앱 재전송 처리](../../android/app/src/main/java/com/surimap/core/sync/RoomLocalSyncServices.kt)의 `isDutyShiftEndBlocked()`와 [Outbox 조회](../../android/app/src/main/java/com/surimap/core/database/OutboxDao.kt)의 `countUnresolvedLowerSequenceSourceRows()`에 이 대기가 구현돼 있다. 앱 내 전송 순서와 서버가 모든 기록을 확보했다는 판단은 별개다.
- **기존 요구**: 입력이 아직 모이지 않았으면 `GENERATING / PENDING_SYNC / content=null`로 표시한다. 생성 후 늦게 도착한 기록이 입력 해시를 바꾸면 기존 요약을 `STALE`로 표시하고 서버에서 다시 생성한다. 종료 시각 이전에 기록됐거나 같은 근무·차수에 속한 늦은 기록도 재생성에 포함한다.
- **현재 차이**: [요약 생성 처리](../../backend/src/main/java/com/surimap/summary/SearchHistorySummaryGenerationJob.java)는 준비 상태를 `READY`로 지정한다. 다른 입력 해시의 기존 요약을 `STALE`로 바꾸는 처리는 있지만, 호출자는 근무 종료·차수 전환뿐이다. 늦은 기록 저장에서 재생성을 요청하는 연결은 확인되지 않았다.
- 이름은 `GenerationJob`·`enqueue`지만 현재는 커밋 후 콜백에서 [요약 Service](../../backend/src/main/java/com/surimap/summary/SearchHistorySummaryService.java)를 동기 호출한다. 영속 작업 큐·실패 재시도·재시작 후 복구가 구현됐다고 간주하지 않는다. 기존에 요구한 호출 지연·실패 이유·차수 전환과 요약 성공/실패 계측도 후속 확인 대상이다.

## 요약 실패와 차수 비교

- **기존 요구**: 요약은 내부 기록의 사실만 다루며 판단·추천을 만들지 않는다. Android는 근무 교대 요약, Web은 차수 요약·비교에 집중한다. 앱·웹에는 요약 생성·재시도 버튼을 두지 않고, 실패해도 수동 메모·원본 기록·차수 비교는 사용할 수 있어야 한다.
- [OpenAI 어댑터](../../backend/src/main/java/com/surimap/client/openai/OpenAiSearchHistorySummaryAdapter.java)는 입력에서 좌표·계정·업무폰 등의 필드를 덜어내고 응답 형식·금지 표현을 검사한다. 요약 Service는 실패 시 본문 없이 `FAILED`로 저장한다. 이것만으로 자유 입력 메모의 모든 개인정보 제거가 검증된 것은 아니다.
- [요약 조회 응답](../../backend/src/main/java/com/surimap/api/controller/summary/response/SearchHistorySummaryItemResponse.java)은 생성 상태와 입력 준비 상태가 모두 `READY`일 때만 본문을 제공한다. 상황판·인수인계 화면도 이 구분을 지키는지 별도 확인한다. 차수 비교의 수치·근거와 AI 설명의 성공 상태는 구분하며, 비교 생성이 원본 경로·구역·메모를 변경해서는 안 된다는 요구를 유지한다.
- 옛 ADR-0029의 UI 재시도 안내와 아래 공용 fixture는 마지막 S8의 읽기 전용 요구와 다르다. 원문을 현재 기능의 정본으로 삼아 버튼을 되살리지 않는다. OP 목록 300ms는 옛 하네스 목표이며 검증된 제품 성능 기준이 아니다.

## 테스트 입력과 원문

- [공용 fixture](../../test-fixtures/common-fixtures.json)의 `opTransition`·`searchHistorySummary`와 기존 [차수](../../backend/src/test/java/com/surimap/operationalperiod/fixture/OperationalPeriodFixtures.java)·[메모](../../backend/src/test/java/com/surimap/handover/fixture/HandoverMemoFixtures.java)·[요약](../../backend/src/test/java/com/surimap/summary/fixture/SearchHistorySummaryFixtures.java) Java fixture를 그대로 사용한다. S8 JSON을 직접 읽는 Backend·Frontend·Android 테스트는 확인되지 않았다.
- 공용 JSON의 현재 차수, 새 차수, 전환 응답·상황판 기대값, 동일 요청 재전송·충돌, 메모 이벤트 값은 원문과 같다. 원문에만 있는 전체 조회·이벤트 기대값과 어댑터 세부 값은 아래 Git 기록에 보존한다.
- **기존 불일치**: 공용 `coveredEndpoints`에는 요약 생성 POST가 남아 있지만 원문은 근무 종료 PATCH를 사용한다. 공용 `retryCta`는 `요약 다시 생성`이지만 원문은 `null`이다. 공용 요약·이벤트의 일부 기대값에는 원문의 `sourceReadiness`·`sourceHash`가 없다. 실제 소비 테스트를 정리할 때 함께 전환하며 이번에는 값·필드·ID를 변경하지 않았다.
- [Java 고정값 검사](../../backend/src/test/java/com/surimap/operationalperiod/fixturetest/OperationalPeriodFixtureExactnessTest.java)에도 옛 `PENDING_PROJECTION`, `search_session`, 차수 하위 배정 URL이 남아 있다. 이 검사와 메모 전송 fixture의 `MARKER` 분류를 현재 구현의 올바른 상태·URL·전송 순서로 간주하지 않는다.
- [SC-10 테스트](../../backend/src/test/java/com/surimap/harness/sc10/Sc10OpHandoverHarnessTest.java)는 대역으로 흐름을 구성하고, [SC-11 실행기](../../frontend/src/test/sc11OpSearchHistorySummaryHarnessRunner.ts)는 고정 입력을 조립한다. 실제 서버·DB·SSE·브라우저의 연결 검증과 구분한다. `Sc`·`S8`·`RedTest`·`ContractTest`·`FixtureTest`·`Runner` 이름은 [정리 후보](../refactoring/codebase-naming-candidates.md)에서 별도로 다룬다.
- 과거 요구·미정 사항·구현 계획과 전체 fixture: [S8 원문 (`93072f1d`)](https://github.com/sonic8-8/suri-map/blob/93072f1d728f8085bc6d13d6c466bb0788ee076d/docs/spec/specs/S8.json). 기존 주석·시험 기록의 S8 표기는 이 원문을 가리킨다.
