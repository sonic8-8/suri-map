# 코드베이스 이름 변경 후보

2026-09-09 1차 탐색. Backend·Frontend·Android·mock-112·Infra의 파일명·선언명을 검색하고, 역할이 모호한 후보는 구현·사용처를 확인했다. 메서드·변수 전수 검토는 아니다.

현재 이름은 유지한다. 마커·알림부터 함께 변경하고, 나머지는 해당 영역을 정리할 때 이름을 확정한다. API·이벤트·DB 필드·fixture ID 같은 계약 이름은 이 목록만으로 바꾸지 않는다.

`Sc02`·`Sc08` 같은 시나리오 번호도 파일·클래스 이름의 변경 대상이다. 번호 대신 검증하는 동작이나 실행·데이터 준비 역할이 드러나도록 정한다. 문서의 시나리오 번호와 fixture ID는 유지한다.

## 마커·알림 — 먼저 진행

`MarkerLocationValidatorRedTest`는 좌표 검증 규칙을 공통 도메인으로 옮기면서 [MarkerTest](../../backend/src/test/java/com/surimap/domain/marker/MarkerTest.java)에 통합했다.

`MarkerOpBindingRedTest`는 중복 수색 차수 검사를 제거하면서 [MarkerWriteAccessValidatorTest](../../backend/src/test/java/com/surimap/domain/marker/MarkerWriteAccessValidatorTest.java)에 통합했다. 차수 일치·부재·불일치·요청 차수 누락을 검증하고, 생성 전 사진 업로드의 차수 우선 오류 순서를 함께 확인한다. 실제 DB의 검사 흐름은 기존 `AppMarkerServiceTest`·`PhotoServiceTest`에서 검증한다.

`Sc06MarkerPhotoHarnessRedTest`·`Sc06MarkerPhotoHarnessRunner`는 [AppMarkerServiceTest](../../backend/src/test/java/com/surimap/app/service/marker/AppMarkerServiceTest.java)에 실제 DB 기반 마커 생성 후 사진 첨부·잘못된 좌표 거부 검증을 보강한 뒤 제거했다. 테스트 내부에서 흉내 낸 SSE 수신·상황판 갱신은 실제 연동 검증으로 옮기지 않았다.

`Sc08NotificationHarnessRedTest`·`Sc08NotificationHarnessRunner`도 기존 `AppMarkerServiceTest`에 알림 저장·DB 커밋 후 FCM 호출·재전송 중복 방지·전송 실패 시 저장 결과 유지 검증을 보강한 뒤 제거했다. DB와 서비스는 실제 구현을 사용하고 외부 FCM만 Mock으로 대체한다. 실제 SSE 수신·브라우저 표시·업무폰 알림 수신은 별도 확인 대상이다.

`SupportRequestNotificationDispatchService`·`BoardToastEvidence`와 전용 테스트 2개는 서버에서 사용하지 않아 제거했다. 실제 알림 저장·전달 검증은 기존 `AppMarkerServiceTest`에 남아 있다. 미사용 코드의 Map 검사와 테스트용 표시 결과를 실제 서버·상황판 동작으로 옮기지는 않았다.

`MarkerNotificationFcmDispatchService`의 토큰 조회·커밋 후 전송은 [MarkerNotificationService](../../backend/src/main/java/com/surimap/app/service/marker/MarkerNotificationService.java)에 합쳤다. 별도 전송 클래스와 전용 테스트는 제거하고, 실제 DB 알림 저장·FCM 전달은 기존 `AppMarkerServiceTest`, 고정 이벤트 ID는 [MarkerEventIdsTest](../../backend/src/test/java/com/surimap/global/event/MarkerEventIdsTest.java)에서 검증한다. 다른 기능에서도 사용하는 Firebase 전송 구현은 유지했다.

`MarkerNotificationToastQuery`를 제거하고 [MarkerNotificationMapper](../../backend/src/main/java/com/surimap/domain/marker/MarkerNotificationMapper.java)의 `findNotificationRowsByIncidentId`로 조회 선언을 모았다. 조회 결과는 Mapper 내부의 `NotificationRow` class로 옮겼다. 기존 알림 조회 테스트는 [MarkerNotificationMapperTest](../../backend/src/test/java/com/surimap/domain/marker/MarkerNotificationMapperTest.java)로 이름·위치를 정리했다. 상황판 슬롯 이름은 `marker_notification`을 사용한다.

`Sc02SupportAssignmentFcmHarnessRunner`·`Sc02SupportAssignmentFcmHarnessRedTest`는 테스트 안에서 배정·수신자 선택을 재구현하고 DB와 무관한 카운터를 검사하므로 제거했다. 기존 `Sc02HandoverSupportAssignmentIntegrationTest`는 [IncidentHandoverSupportAssignmentScenarioTest](../../backend/src/test/java/com/surimap/incident/IncidentHandoverSupportAssignmentScenarioTest.java)로 옮겼다. 실제 배정 서비스가 DB 토큰을 조회해 호출한 FCM 기록과 `marker_notification` 미저장을 검증한다. 테스트가 직접 만드는 FCM payload·수신자 목록은 제거했다. 남은 SSE 직접 호출·고정 자료의 상황판 조립·probe 행 검사는 실제 자동 전달·화면 표시·경로 보존 검증과 구분한다.

`MarkerNotificationContractTest`의 SQL 문자열 검사는 제거했다. 저장 필드는 기존 `AppMarkerServiceTest`가 실제 DB에서 검증한다. 같은 마커에 다른 ID의 알림을 저장해도 추가하거나 덮어쓰지 않는지는 기존 [MarkerNotificationMapperTest](../../backend/src/test/java/com/surimap/domain/marker/MarkerNotificationMapperTest.java)에 보강했다. 마이그레이션 SQL은 변경하지 않았다.

`NotificationType`·`NotificationRecipientPolicy`는 마커 알림 전용임이 드러나도록 `MarkerNotificationType`·`MarkerNotificationRecipientPolicy`로 바꾸고, `MarkerNotificationStatus`와 함께 `domain/marker`로 옮겼다. 조회 결과를 다시 감싸던 `NotificationRecipients`는 제거하고 기존 `IncidentAssignmentView.NotificationTargets`를 그대로 사용한다. 수신자 정책은 알림 종류에서 가져오며, 선정 규칙·목록 보호·DB 및 이벤트 값은 유지한다.

`MarkerType`·`MarkerStatus`·`MarkerSource`·`MarkerSupportRequestType`은 이름과 값을 유지하고 `Marker`와 같은 `domain/marker`로 옮겼다. 호출부·테스트·fixture의 import만 맞췄으며, Mapper XML과 업무 로직은 변경하지 않았다.

마커 조회는 다음과 같이 정리했다.

- `MarkerQuery`·`MyBatisMarkerQuery`를 제거하고 기존 [MarkerService.list()](../../backend/src/main/java/com/surimap/api/service/marker/MarkerService.java)로 통합했다. 상황판·인수인계·오프라인 패키지·수색 차수 비교도 같은 조회를 사용한다.
- `MarkerView`·`MarkerPhotoSummary`는 [MarkersServiceResponse](../../backend/src/main/java/com/surimap/api/service/marker/response/MarkersServiceResponse.java) 내부의 `MarkerServiceResponse`·`MarkerPhotoServiceResponse` class로 옮겼다. 응답 변환은 도메인 `Marker`에서 응답 DTO로 옮기고, 중복 포장인 `MarkerQueryResult`와 값 전달용 `MarkerQueryFilters`는 제거했다. 사진 SQL의 조회 컬럼은 `MarkerMapper.AttachedPhotoRow`로 유지한다.
- `MarkerQueryMapperIntegrationTest`의 필터·정렬·첨부 사진 검증은 기존 [MarkerMapperTest](../../backend/src/test/java/com/surimap/domain/marker/MarkerMapperTest.java)에 합쳤다. `MarkerQueryServiceTest`의 클래스·SQL 문자열 검사는 제거했다. 사건 ID 필수 제약은 실제 DB에서, 응답 필드와 사진 URL 발급 실패 시 조회 유지는 기존 `MarkerServiceTest`·`MarkerControllerTest`에서 확인한다. SQL 조회 조건·정렬·공개 응답 필드는 유지했다.

마커의 나머지 요청·좌표·초기 등록 흐름도 정리했다.

| 이전 구성 | 정리 결과 |
|---|---|
| `MarkerApiException`·`MarkerExceptionHandler` | 기존 `BusinessException`·`ErrorCode`·`GlobalExceptionHandler`로 통합. HTTP 상태와 오류 코드는 유지 |
| `MarkerRequestContext`·`MarkerRequestContextResolver` | ServiceRequest가 인증 정보와 멱등키를 직접 전달. 해석기는 [MarkerAuthenticationResolver](../../backend/src/main/java/com/surimap/global/auth/MarkerAuthenticationResolver.java)로 변경 |
| `MarkerGeoJsonPoint.canonical()` | [GeoJsonPoint.roundToSixDecimals()](../../backend/src/main/java/com/surimap/global/geometry/GeoJsonPoint.java)로 변경. GeoJSON 필드·순서·반올림과 과거 요청 해시 비교 형식은 유지 |
| 기준 마커의 중복 `ReferenceMarkerSeed`·어댑터·저장 서비스 | [ReferenceMarkerSeedService](../../backend/src/main/java/com/surimap/api/service/marker/ReferenceMarkerSeedService.java)에서 원천값 변환·좌표 검사·저장을 처리. 중간 입력·반환 객체와 반환용 재조회 제거 |

사건 가져오기가 사용하는 [ReferenceMarkerSeed](../../backend/src/main/java/com/surimap/api/service/marker/ReferenceMarkerSeed.java) 계약은 저장 서비스 옆으로 옮겼다. 이름·입력 필드·구현 부재 시 가져오기를 막는 처리는 유지하며, 내부 입력 DTO `SeedMarker`는 Lombok 기반 class로 바꿨다. 기준 마커의 초기 등록은 웹 마커 수정과 준비 데이터·의존성이 달라 별도 서비스로 둔다. 기존 [ReferenceMarkerSeedServiceTest](../../backend/src/test/java/com/surimap/api/service/marker/ReferenceMarkerSeedServiceTest.java)는 실제 DB의 저장 내용·중복 방지·잘못된 좌표·최초 수색 차수 부재를 검증한다.

남은 운영 파일도 정리해 `src/main/java/com/surimap/marker` 디렉터리를 제거했다(2026-09-13).

| 대상 | 이동 위치와 유지한 동작 |
|---|---|
| FCM 전송 계약·Firebase 및 Mock 구현 6개 | `client/fcm`. 마커·지원 배정·사건 종료·수색구역 알림이 공유하며 전송·실패 처리 정책은 유지 |
| Firebase 설정 2개 | `config/fcm`. 설정 키·인증 정보 로딩·빈 등록 조건은 유지 |
| `ReferenceMarkerSeed` | `api/service/marker`. 사건 가져오기 내부 계약을 유지하고 DTO 접근을 getter로 변경 |
| `MarkerPhotoPurgeHook`·`MarkerPhotoPurgeHookAdapter` | `api/service/photo`. 요청 전달만 유지. 실제 삭제 구현과 빈 등록은 별도 작업 |

전송·파기 테스트 3개는 운영 파일 위치에 맞춰 옮겼다. `src/test/java/com/surimap/marker`에 남은 테스트·fixture까지 제거한 것은 아니다. FCM 실패 처리·지원 배정의 전송 시점과 실제 SSE 수신·브라우저 표시 검증은 후속 작업으로 남아 있다.

## SSE·인증 — 승인한 이름 반영

2026-09-15 먼저 이름과 테스트 설명을 정리했다. 이 명명 변경에서는 인증 보관·전송·재시도 동작과 공개 계약을 바꾸지 않았다.

| 이전 이름 | 반영한 이름 |
|---|---|
| `SseStreamSessionRegistry`·동명 테스트 | `SseConnectionRegistry`·`SseConnectionRegistryTest` |
| `register`·`registerAccount`·`send`·`release` | `registerForIncident`·`registerForAccount`·`sendToIncident`·`closeIncidentConnections` |
| 공용 전송의 `subscriptionId`·테스트의 `subscription` | `subscriptionTargetId`·`subscriptionType` |
| 인증 필터의 `accessToken`·`isJwt` | `extractBearerToken`·`hasThreeTokenSegments` |
| `hasSuriMapAuthentication` | `checkSuriMapAuthentication` |
| `SecurityFilterBaselineTest`·`AuthHarnessController`·`AuthHarnessResponse` | `SecurityConfigTest`·`SecurityTestController`·`AuthenticationResponse` |
| 전송 테스트의 `pathEvent`·`dispatchStatus` | `createPathAppendedEvent`·`getDispatchStatus` |

2026-09-15 사용자 승인으로 [Backend 테스트 명명 기준](../../backend/AGENTS.md#테스트-기준)을 정리했다. `ScenarioTest`·`E2ETest`를 별도 이름 분류로 두던 규칙은 제거했다. 이어서 승인된 SSE 테스트를 검증 대상별로 모았다.

| 이전 테스트 | 정리 결과 |
|---|---|
| `EventDispatchJobSseFanoutIntegrationTest` | [EventDispatchJobDispatcherTest](../../backend/src/test/java/com/surimap/eventhub/EventDispatchJobDispatcherTest.java). 실제 DB 커밋 후 전달·연결 실패 격리·저장 실패의 3개 검증을 유지하고 기존 PostGIS 테스트 지원 클래스를 사용 |
| `SseStreamLifecycleEvidenceTest` | 기존 [SseConnectionRegistryTest](../../backend/src/test/java/com/surimap/eventhub/stream/SseConnectionRegistryTest.java)에 연결 반복 등록·해제와 사건 연결 종료 2개를 통합 |
| `LiveSseFanoutRedTest`·`SseTerminalStreamReleaseRedTest`·`SseIncidentClosureReplayStopTest` | [SseStreamServiceTest](../../backend/src/test/java/com/surimap/eventhub/SseStreamServiceTest.java)에 저장 후 전송·중복 방지·사건 종료·파기 7개를 통합 |
| `SseFailureInjectionTest` | [SseReplayServiceTest](../../backend/src/test/java/com/surimap/eventhub/SseReplayServiceTest.java)에 순서 복원·순번 누락 2개를 이동 |

Service 테스트는 실제 Spring 빈과 현재 운영 구현인 메모리 재전송 저장소를 사용한다. DB 내구성·네트워크 수신·브라우저 표시는 이 테스트의 검증 범위가 아니다. 시작 상태가 다른 종료·파기 검증은 유지했다. 명명·통합 후 관련 88개 검증은 그대로 통과했고, 클래스는 32개에서 29개로 줄었다. 메서드는 밑줄 이름, 한글 `DisplayName`과 given/when/then 설명으로 정리했다.

그다음 진행한 요청 범위 인증 보관 수정과 추가 회귀 테스트는 [로컬 이슈 3](../issues/local/3-authenticated-sse-access-denied-on-disconnect.md)에 별도로 기록했다.

## Backend — 나머지 Red 테스트

`Red`를 제거하고 실제 검증 대상에 맞춰 이름을 정한다. 마커·알림 대상의 처리 내역은 위 절에 있다.

| 영역 | 변경 대상 |
|---|---|
| account | [AuthPolicePhoneHarnessRunnerRedTest](../../backend/src/test/java/com/surimap/account/AuthPolicePhoneHarnessRunnerRedTest.java) |
| account | [S1_2HarnessAuthMockRedTest](../../backend/src/test/java/com/surimap/account/S1_2HarnessAuthMockRedTest.java) |
| account | [S1_2HarnessSeedLoaderRedTest](../../backend/src/test/java/com/surimap/account/S1_2HarnessSeedLoaderRedTest.java) |
| account | [S1_2RoleChannelMatrixBoundaryRedTest](../../backend/src/test/java/com/surimap/account/S1_2RoleChannelMatrixBoundaryRedTest.java) |
| auth/guard | [GuardAliasRedTest](../../backend/src/test/java/com/surimap/auth/guard/GuardAliasRedTest.java) |
| board | [BoardApiSseConvergenceHarnessRedTest](../../backend/src/test/java/com/surimap/board/BoardApiSseConvergenceHarnessRedTest.java) |
| board | [BoardDtoAssemblyModelRedTest](../../backend/src/test/java/com/surimap/board/BoardDtoAssemblyModelRedTest.java) |
| board | [BoardRefetchConvergenceLagRedTest](../../backend/src/test/java/com/surimap/board/BoardRefetchConvergenceLagRedTest.java) |
| board | [BoardRefetchGuardRedTest](../../backend/src/test/java/com/surimap/board/BoardRefetchGuardRedTest.java) |
| board | [IncidentTerminalPackageBadgePrivacyRedTest](../../backend/src/test/java/com/surimap/board/IncidentTerminalPackageBadgePrivacyRedTest.java) |
| board | [PackageBadgeBoardAssemblyRedTest](../../backend/src/test/java/com/surimap/board/PackageBadgeBoardAssemblyRedTest.java) |
| eventhub | [BaseEventEnvelopeValidationRedTest](../../backend/src/test/java/com/surimap/eventhub/BaseEventEnvelopeValidationRedTest.java) |
| eventhub | [EventDispatchJobOwnershipRedTest](../../backend/src/test/java/com/surimap/eventhub/EventDispatchJobOwnershipRedTest.java) |
| eventhub | [EventDispatchJobTransactionRedTest](../../backend/src/test/java/com/surimap/eventhub/EventDispatchJobTransactionRedTest.java) |
| eventhub | [EventHubHarnessRunnerRedTest](../../backend/src/test/java/com/surimap/eventhub/EventHubHarnessRunnerRedTest.java) |
| eventhub | [EventStreamControllerRedTest](../../backend/src/test/java/com/surimap/eventhub/EventStreamControllerRedTest.java) |
| eventhub | [OwnerPayloadSchemaValidationRedTest](../../backend/src/test/java/com/surimap/eventhub/OwnerPayloadSchemaValidationRedTest.java) |
| handover | [HandoverMemoContextBindingRedTest](../../backend/src/test/java/com/surimap/handover/HandoverMemoContextBindingRedTest.java) |
| handover | [HandoverMemoCreatePublishRequestRedTest](../../backend/src/test/java/com/surimap/handover/HandoverMemoCreatePublishRequestRedTest.java) |
| harness/sc09 | [Sc07Sc09OfflineReplayHarnessRedTest](../../backend/src/test/java/com/surimap/harness/sc09/Sc07Sc09OfflineReplayHarnessRedTest.java) |
| maparea | [SearchAreaAssignmentWriteRedTest](../../backend/src/test/java/com/surimap/maparea/SearchAreaAssignmentWriteRedTest.java) |
| offlinepackage | [OfflinePackageInstallationApiRedTest](../../backend/src/test/java/com/surimap/offlinepackage/OfflinePackageInstallationApiRedTest.java) |
| offlinepackage | [OfflinePackagePurgeHookRedTest](../../backend/src/test/java/com/surimap/offlinepackage/OfflinePackagePurgeHookRedTest.java) |
| offlinepackage | [OfflinePackageSearchAreaChangedConsumerRedTest](../../backend/src/test/java/com/surimap/offlinepackage/OfflinePackageSearchAreaChangedConsumerRedTest.java) |
| offlinepackage | [Sc03PackageTileHarnessRedTest](../../backend/src/test/java/com/surimap/offlinepackage/Sc03PackageTileHarnessRedTest.java) |
| operationalperiod | [OpTransitionRedTest](../../backend/src/test/java/com/surimap/operationalperiod/OpTransitionRedTest.java) |
| policephone | [FcmTokenQueryRedTest](../../backend/src/test/java/com/surimap/policephone/FcmTokenQueryRedTest.java) |
| policephone | [PolicePhoneFreshnessQueryRedTest](../../backend/src/test/java/com/surimap/policephone/PolicePhoneFreshnessQueryRedTest.java) |
| policephone | [PolicePhoneHeartbeatApiRedTest](../../backend/src/test/java/com/surimap/policephone/PolicePhoneHeartbeatApiRedTest.java) |
| policephone | [PolicePhoneHeartbeatPublishRequestRedTest](../../backend/src/test/java/com/surimap/policephone/PolicePhoneHeartbeatPublishRequestRedTest.java) |
| retention/purge | [PurgeCoordinatorRedTest](../../backend/src/test/java/com/surimap/retention/purge/PurgeCoordinatorRedTest.java) |
| retention/purge | [PurgeOrchestrationHarnessRunnerRedTest](../../backend/src/test/java/com/surimap/retention/purge/PurgeOrchestrationHarnessRunnerRedTest.java) |
| searcharea | [SearchAreaApiServicePersistenceRedTest](../../backend/src/test/java/com/surimap/searcharea/SearchAreaApiServicePersistenceRedTest.java) |
| searcharea | [SearchAreaAssignmentPersistenceRedTest](../../backend/src/test/java/com/surimap/searcharea/SearchAreaAssignmentPersistenceRedTest.java) |
| summary | [SearchHistorySummaryPublishRequestRedTest](../../backend/src/test/java/com/surimap/summary/SearchHistorySummaryPublishRequestRedTest.java) |

## Backend — Mapper·테스트 지원 코드

[Backend 테스트 기준](../../backend/AGENTS.md#테스트-기준)에 따라 검증 대상과 실행 역할을 드러낸다.

| 현재 이름 | 변경 후보·확인할 점 |
|---|---|
| [GeometrySpatialMapperIntegrationTest](../../backend/src/test/java/com/surimap/maparea/geometry/validation/GeometrySpatialMapperIntegrationTest.java) | `GeometrySpatialMapperTest` |
| [OpComparisonAnalysisMapperIntegrationTest](../../backend/src/test/java/com/surimap/opcomparison/OpComparisonAnalysisMapperIntegrationTest.java) | `OpComparisonAnalysisMapperTest` |
| [OpComparisonRegionFactMapperIntegrationTest](../../backend/src/test/java/com/surimap/opcomparison/OpComparisonRegionFactMapperIntegrationTest.java) | `OpComparisonRegionFactMapperTest` |
| [SearchHistorySummaryMapperIntegrationTest](../../backend/src/test/java/com/surimap/summary/SearchHistorySummaryMapperIntegrationTest.java) | `SearchHistorySummaryMapperTest` |
| [AuthPolicePhoneHarnessRunner](../../backend/src/test/java/com/surimap/account/harness/AuthPolicePhoneHarnessRunner.java) | 실제 실행 역할에 맞는 테스트 지원 코드 이름 |
| [Sc02ToSc12BoardConvergenceHarnessRunner](../../backend/src/test/java/com/surimap/board/Sc02ToSc12BoardConvergenceHarnessRunner.java) | 실제 실행 역할에 맞는 테스트 지원 코드 이름 |
| [EventHubHarnessRunner](../../backend/src/test/java/com/surimap/eventhub/harness/EventHubHarnessRunner.java) | 실제 실행 역할에 맞는 테스트 지원 코드 이름 |
| [Sc04SearchAreaHarnessTest](../../backend/src/test/java/com/surimap/harness/sc04/Sc04SearchAreaHarnessTest.java) | 검증 대상·시나리오 확인 후 결정 |
| [Sc07Sc09OfflineReplayHarnessRunner](../../backend/src/test/java/com/surimap/harness/sc09/Sc07Sc09OfflineReplayHarnessRunner.java) | 실제 실행 역할에 맞는 테스트 지원 코드 이름 |
| [Sc10OpHandoverHarnessTest](../../backend/src/test/java/com/surimap/harness/sc10/Sc10OpHandoverHarnessTest.java) | 검증 대상·시나리오 확인 후 결정 |
| [IncidentFlowHarnessMockContractTest](../../backend/src/test/java/com/surimap/incident/IncidentFlowHarnessMockContractTest.java) | 검증 대상·시나리오 확인 후 결정 |
| [Sc03PackageTileHarnessRunner](../../backend/src/test/java/com/surimap/offlinepackage/Sc03PackageTileHarnessRunner.java) | 실제 실행 역할에 맞는 테스트 지원 코드 이름 |
| [PurgeOrchestrationHarnessRunner](../../backend/src/test/java/com/surimap/retention/purge/harness/PurgeOrchestrationHarnessRunner.java) | 실제 실행 역할에 맞는 테스트 지원 코드 이름 |

## Backend — 나머지 시나리오 번호 파일

위 표에 있는 `Sc*` 파일과 함께 정리한다. 최종 이름은 검증 내용과 사용처를 확인한 뒤 정한다.

| 영역 | 변경 대상 |
|---|---|
| harness/sc01 | [Sc01IncidentStartIntegrationTest](../../backend/src/test/java/com/surimap/harness/sc01/Sc01IncidentStartIntegrationTest.java) / [Sc01IncidentStartBridgeTest](../../backend/src/test/java/com/surimap/harness/sc01/Sc01IncidentStartBridgeTest.java) |
| harness/sc04 | [Sc04Fixtures](../../backend/src/test/java/com/surimap/harness/sc04/fixture/Sc04Fixtures.java) |
| harness/sc10 | [Sc10Fixtures](../../backend/src/test/java/com/surimap/harness/sc10/fixture/Sc10Fixtures.java) |
| harness/sc12 | [Sc12IncidentCloseDataPurgeIntegrationTest](../../backend/src/test/java/com/surimap/harness/sc12/Sc12IncidentCloseDataPurgeIntegrationTest.java) |

## Backend — 기존 호환 이름

| 현재 이름 | 확인할 점 |
|---|---|
| [RequireDevice](../../backend/src/main/java/com/surimap/common/auth/RequireDevice.java) / [RequireDeviceRegistered](../../backend/src/main/java/com/surimap/common/auth/RequireDeviceRegistered.java) / [RequireDeviceAssigned](../../backend/src/main/java/com/surimap/common/auth/RequireDeviceAssigned.java) | 기존 PolicePhone 가드와 호환 관계 확인; 애너테이션·오류 코드 변경은 별도 합의 |
| [DeviceRequiredException](../../backend/src/main/java/com/surimap/common/auth/guard/DeviceRequiredException.java) / [DeviceNotRegisteredException](../../backend/src/main/java/com/surimap/common/auth/guard/DeviceNotRegisteredException.java) / [DeviceNotAssignedException](../../backend/src/main/java/com/surimap/common/auth/guard/DeviceNotAssignedException.java) | 위 별칭 가드의 `device_*` 오류 응답과 함께 확인 |

## Frontend

| 현재 이름 | 변경 후보·확인할 점 |
|---|---|
| [Sc11OpSearchHistorySummaryHarnessRedTest.test.tsx](../../frontend/src/features/board/components/Sc11OpSearchHistorySummaryHarnessRedTest.test.tsx) | `Red` 제거, 수색 이력 요약 렌더링 검증 기준으로 명명 |
| [features/board/test/sc11OpSearchHistorySummaryHarnessRunner.ts](../../frontend/src/features/board/test/sc11OpSearchHistorySummaryHarnessRunner.ts) | 렌더링 시나리오 실행 역할 기준으로 명명 |
| [src/test/sc11OpSearchHistorySummaryHarnessRunner.ts](../../frontend/src/test/sc11OpSearchHistorySummaryHarnessRunner.ts) | 위 파일과의 사용 관계 확인 후 결정 |
| [mockAreaEdit.ts / MOCK_PAGE_STATE](../../frontend/src/features/areaEdit/presentation/constants/mockAreaEdit.ts) | 실사용 타입·상수와 예제 데이터 구분; 기본 화면 상태는 `DEFAULT_PAGE_STATE` 후보 |
| [mockSituationBoard.ts](../../frontend/src/features/situationBoard/presentation/constants/mockSituationBoard.ts) | 실제 뷰모델 재수출 파일; `situationBoardViewModel` 직접 참조 검토 |
| [assignedAreaIds / unassignedAreaCount](../../frontend/src/features/areaEdit/presentation/pages/AreaEditPage.tsx) | 담당자 배정이 아닌 구역 도형 작성 완료·미완료에 맞춰 명명 |
| [unassignedPhoneCount](../../frontend/src/features/areaEdit/presentation/components/AreaHierarchyPanel.tsx) | `missingAreaGeometryCount` 후보 — 업무폰 수가 아닌 도형 미지정 구역 수 |
| [createDeviceTitle / createDeviceMeta](../../frontend/src/features/offlinePackage/presentation/model/offlinePackageStatusPageViewModel.ts) | `createPolicePhoneTitle` / `createPolicePhoneMeta` |
| [errorCode()](../../frontend/src/shared/api/client.ts) | `resolveApiErrorCode` — 응답 본문 또는 HTTP 상태로 오류 코드 결정 |
| [incidentBoardQueryKeyParams() / emptyCursor()](../../frontend/src/features/board/api/incidentBoardApi.ts) | `buildIncidentBoardQueryKeyParams` / `createEmptyBoardSourceCursor` |
| [geometryUtils.ts](../../frontend/src/features/areaEdit/presentation/utils/geometryUtils.ts)·[AreaEditMapCanvas.tsx](../../frontend/src/features/areaEdit/presentation/components/AreaEditMapCanvas.tsx)의 signedArea() / isBetween() | 각각 삼각형 부호 면적의 2배 계산·좌표 범위 검사에 맞춰 명명 |

## Android

| 현재 이름 | 변경 후보·확인할 점 |
|---|---|
| [HarnessSyncStatus / EnqueueResult.harnessStatus](../../android/app/src/main/java/com/surimap/core/sync/LocalWriteOperation.kt) | `LocalSyncStatus` / `localSyncStatus` 후보 — 실제 앱의 로컬 동기화 상태 |
| [initialHarnessStatus](../../android/app/src/main/java/com/surimap/core/sync/RoomLocalSyncServices.kt) | `initialLocalSyncStatus` 후보 — 위 상태 이름과 함께 변경 |
| [HarnessStatusMapper](../../android/app/src/main/java/com/surimap/core/sync/OutboxStateMachine.kt) | 상태 변환 역할·실사용 여부 확인 후 결정 |
| [MarkerPhotoPayloadReader / read()](../../android/app/src/main/java/com/surimap/feature/marker/data/MarkerPhotoPayloadReader.kt) | 읽기뿐 아니라 이미지 변환·압축을 하는 역할이 드러나도록 명명 |
| [OutboxHarnessIntegrationTest](../../android/app/src/test/java/com/surimap/core/sync/OutboxHarnessIntegrationTest.kt) | `OutboxSyncIntegrationTest` 후보 |

## mock-112

| 현재 이름 | 변경 후보·확인할 점 |
|---|---|
| [api()](../../mock-112/src/main/resources/static/app.js) | `sendApiRequest` |
| [resetAll()](../../mock-112/src/main/resources/static/app.js) | `resetMock112Data` — 초기화 대상 범위 표시 |

Infra에서는 이번 탐색으로 추가할 후보를 찾지 못했다. 이미 합의한 k6 이름은 유지한다.
