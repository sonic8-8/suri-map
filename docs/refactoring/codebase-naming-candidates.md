# 코드베이스 이름 변경 후보

2026-09-09 1차 탐색. Backend·Frontend·Android·mock-112·Infra의 파일명·선언명을 검색하고, 역할이 모호한 후보는 구현·사용처를 확인했다. 메서드·변수 전수 검토는 아니다.

후보의 이름은 해당 영역을 정리할 때 실제 역할·사용처를 확인해 확정한다. API·이벤트·DB 필드·fixture ID 같은 계약 이름은 이 목록만으로 바꾸지 않는다.

## 발견 시 후보 등록

문서·코드를 검토하는 영역에서 아래 이름을 발견하면 별도 요청 없이 후보로 등록한다(2026-09-18 사용자 합의). 매번 저장소 전체를 다시 조사하지 않고, 현재 대상과 연결된 파일을 확인한다.

- 파일 경로와 후보로 보는 이유를 해당 영역에 짧게 기록한다. 이미 등록된 파일은 기존 항목을 갱신한다.
- 실제 검증 대상·대역 사용·중복을 확인해 이름 변경·통합·제거를 정한다. 후보 등록만으로 코드를 바꾸거나 필요한 검증을 제거하지 않는다.
- 시나리오 번호·fixture ID 등 데이터 식별자는 유지한다. 문서 정리 중 파일·클래스를 일괄 재명명하지 않는다.

| 대상 패턴 | 확인할 대표 파일 | 정리 방향 |
|---|---|---|
| 시나리오·Spec 번호가 붙은 이름 | `Sc03PackageTileHarnessRunner`, `S8HandoverApiContractTest` | 번호 대신 검증하는 동작이나 데이터 준비 역할을 드러낸다. `Sc`/`sc` + 번호 등 표기 차이도 확인 |
| `*HarnessRunner`·역할이 불명확한 `*Runner` | `Sc03PackageTileHarnessRunner`, `EventHubHarnessRunner` | 무엇을 실행하는지와 실제 호출 필요성을 확인. 프레임워크 실행기 등 역할이 명확한 이름까지 일괄 제거하지 않음 |
| `*RedTest` | `Sc03PackageTileHarnessRedTest`, `OpTransitionRedTest` | TDD 단계 대신 실제 검증 대상을 드러내거나 기존 테스트에 통합 |
| `*ContractTest` | `OfflinePackageManifestContractTest`, `OutboxRequeueContractTest`, `PolicePhoneNavigationContractTest`, `KeycloakThemeContractTest` | Backend·Android·mock-112를 포함해 실제 검증 대상과 연결 범위를 확인한 뒤 `<검증 대상>Test`로 정리 |
| `*FixtureTest` | `OutboxRetryDiagnosticsFixtureTest`, `HandoverMemoOutboxWriteOperationFixtureTest` | 데이터 자체의 형식·참조 검사와 제품 동작 검증을 구분하고, 필요한 검사만 적절한 대상의 테스트에 유지 |

## 마커·알림 — 먼저 진행

`MarkerLocationValidatorRedTest`는 좌표 검증 규칙을 공통 도메인으로 옮기면서 [MarkerTest](../../backend/src/test/java/com/surimap/domain/marker/MarkerTest.java)에 통합했다.

`MarkerOpBindingRedTest`는 중복 수색 차수 검사를 제거하면서 [MarkerWriteAccessValidatorTest](../../backend/src/test/java/com/surimap/domain/marker/MarkerWriteAccessValidatorTest.java)에 통합했다. 차수 일치·부재·불일치·요청 차수 누락을 검증하고, 생성 전 사진 업로드의 차수 우선 오류 순서를 함께 확인한다. 실제 DB의 검사 흐름은 기존 `AppMarkerServiceTest`·`PhotoServiceTest`에서 검증한다.

`Sc06MarkerPhotoHarnessRedTest`·`Sc06MarkerPhotoHarnessRunner`는 [AppMarkerServiceTest](../../backend/src/test/java/com/surimap/app/service/marker/AppMarkerServiceTest.java)에 실제 DB 기반 마커 생성 후 사진 첨부·잘못된 좌표 거부 검증을 보강한 뒤 제거했다. 테스트 내부에서 흉내 낸 SSE 수신·상황판 갱신은 실제 연동 검증으로 옮기지 않았다.

`Sc08NotificationHarnessRedTest`·`Sc08NotificationHarnessRunner`도 기존 `AppMarkerServiceTest`에 알림 저장·DB 커밋 후 FCM 호출·재전송 중복 방지·전송 실패 시 저장 결과 유지 검증을 보강한 뒤 제거했다. DB와 서비스는 실제 구현을 사용하고 외부 FCM만 Mock으로 대체한다. 실제 SSE 수신·브라우저 표시·업무폰 알림 수신은 별도 확인 대상이다.

`SupportRequestNotificationDispatchService`·`BoardToastEvidence`와 전용 테스트 2개는 서버에서 사용하지 않아 제거했다. 실제 알림 저장·전달 검증은 기존 `AppMarkerServiceTest`에 남아 있다. 미사용 코드의 Map 검사와 테스트용 표시 결과를 실제 서버·상황판 동작으로 옮기지는 않았다.

`MarkerNotificationFcmDispatchService`의 토큰 조회·커밋 후 전송은 [MarkerNotificationService](../../backend/src/main/java/com/surimap/app/service/marker/MarkerNotificationService.java)에 합쳤다. 별도 전송 클래스와 전용 테스트는 제거하고, 실제 DB 알림 저장·FCM 전달은 기존 `AppMarkerServiceTest`, 고정 이벤트 ID는 [MarkerEventIdsTest](../../backend/src/test/java/com/surimap/global/event/MarkerEventIdsTest.java)에서 검증한다. 다른 기능에서도 사용하는 Firebase 전송 구현은 유지했다.

`MarkerNotificationToastQuery`를 제거하고 [MarkerNotificationMapper](../../backend/src/main/java/com/surimap/domain/marker/MarkerNotificationMapper.java)의 `findNotificationRowsByIncidentId`로 조회 선언을 모았다. 조회 결과는 Mapper 내부의 `NotificationRow` class로 옮겼다. 기존 알림 조회 테스트는 [MarkerNotificationMapperTest](../../backend/src/test/java/com/surimap/domain/marker/MarkerNotificationMapperTest.java)로 이름·위치를 정리했다. 상황판 슬롯 이름은 `marker_notification`을 사용한다.

`Sc02SupportAssignmentFcmHarnessRunner`·`Sc02SupportAssignmentFcmHarnessRedTest`는 테스트 안에서 배정·수신자 선택을 재구현하고 DB와 무관한 카운터를 검사하므로 제거했다. 기존 `Sc02HandoverSupportAssignmentIntegrationTest`는 [IncidentHandoverSupportAssignmentScenarioTest](../../backend/src/test/java/com/surimap/incident/IncidentHandoverSupportAssignmentScenarioTest.java)로 옮겼다. 실제 배정 서비스가 DB 토큰을 조회해 호출한 FCM 기록과 `marker_notification` 미저장을 검증한다. 테스트가 직접 만드는 FCM payload·수신자 목록은 제거했다. 남은 SSE 직접 호출·고정 자료의 상황판 조립·probe 행 검사는 실제 자동 전달·화면 표시·경로 보존 검증과 구분한다.

현재의 `IncidentHandoverSupportAssignmentScenarioTest`도 이름 정리 후보로 남긴다(2026-09-21). `ScenarioTest`를 별도 분류로 쓰지 않기로 한 이후의 기준에 맞춰, 검증 대상별 분리·통합과 최종 이름을 함께 정한다.

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

남아 있는 `RedTest`도 정리 대상이다. 실제 검증 대상과 중복 여부를 확인한 뒤 `<검증 대상>Test`로 변경하거나 기존 테스트에 합친다. 마커·알림 대상의 처리 내역은 위 절에 있다.

S4·S3-2 문서 정리에서 수정한 `BoardApiSseConvergenceHarnessRedTest`도 포함한다. 실행 입력은 공용 fixture와 실패 사례 테스트 리소스로 전환했다. 이름·검증 책임 정리는 상황판 테스트를 다룰 때 진행한다. 기존 5개 검증은 조립·대역 기반이며 실제 SSE 수신·브라우저 표시 시험이 아니다.

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
| [Sc02ToSc12BoardConvergenceHarnessRunner](../../backend/src/test/java/com/surimap/board/Sc02ToSc12BoardConvergenceHarnessRunner.java) | 공용 데이터로 응답을 조립하고 고정 시나리오 표를 반환함. 실제 시나리오·SSE 실행기로 오해되지 않도록 필요한 보조 역할부터 정리 |
| [EventHubHarnessRunner](../../backend/src/test/java/com/surimap/eventhub/harness/EventHubHarnessRunner.java) | 실제 실행 역할에 맞는 테스트 지원 코드 이름 |
| [Sc04SearchAreaHarnessTest](../../backend/src/test/java/com/surimap/harness/sc04/Sc04SearchAreaHarnessTest.java) | 검증 대상·시나리오 확인 후 결정 |
| [Sc07Sc09OfflineReplayHarnessRunner](../../backend/src/test/java/com/surimap/harness/sc09/Sc07Sc09OfflineReplayHarnessRunner.java) | 실제 실행 역할에 맞는 테스트 지원 코드 이름 |
| [Sc10OpHandoverHarnessTest](../../backend/src/test/java/com/surimap/harness/sc10/Sc10OpHandoverHarnessTest.java) | 검증 대상·시나리오 확인 후 결정 |
| [IncidentFlowHarnessMockContractTest](../../backend/src/test/java/com/surimap/incident/IncidentFlowHarnessMockContractTest.java) | 공용 JSON 로딩과 인증·OP1·이벤트 대역 간 호출을 검사함. 실제 가져오기·DB 검증과 구분해 필요한 검사·이름을 결정 |
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

## S8 사전 확인 — 추가 후보

2026-09-18 다음 문서 정리 대상의 파일명을 확인했다. 아래 파일은 `ContractTest`라는 분류만으로 검증 대상·범위를 알기 어려워 추가했다. 최종 이름과 통합 여부는 테스트 내용을 확인한 뒤 정한다. 기존 목록의 `Sc10`·`Sc11`·`RedTest`·`Runner`·`FixtureTest`는 중복 등록하지 않았다.

| 영역 | 추가 후보 |
|---|---|
| handover | [HandoverMemoQueryContractTest](../../backend/src/test/java/com/surimap/handover/HandoverMemoQueryContractTest.java) |
| handover | [SearchHistorySummaryReadOnlyApiContractTest](../../backend/src/test/java/com/surimap/handover/SearchHistorySummaryReadOnlyApiContractTest.java) |
| handover | [S8HandoverApiContractTest](../../backend/src/test/java/com/surimap/handover/S8HandoverApiContractTest.java) |
| handover | [HandoverTimelineApiContractTest](../../backend/src/test/java/com/surimap/handover/HandoverTimelineApiContractTest.java) |
| operationalperiod | [OperationalPeriodQueryCurrentContractTest](../../backend/src/test/java/com/surimap/operationalperiod/OperationalPeriodQueryCurrentContractTest.java) |
| operationalperiod | [CurrentOpGuardMockContractTest](../../backend/src/test/java/com/surimap/operationalperiod/CurrentOpGuardMockContractTest.java) |
| operationalperiod | [CurrentOpConsumerContractTest](../../backend/src/test/java/com/surimap/operationalperiod/CurrentOpConsumerContractTest.java) |
| operationalperiod | [InitialOperationalPeriodCreatorMockContractTest](../../backend/src/test/java/com/surimap/operationalperiod/InitialOperationalPeriodCreatorMockContractTest.java) |
| operationalperiod | [CurrentOpGuardContractTest](../../backend/src/test/java/com/surimap/operationalperiod/CurrentOpGuardContractTest.java) |
| operationalperiod | [Op1BootstrapContractTest](../../backend/src/test/java/com/surimap/operationalperiod/Op1BootstrapContractTest.java) |
| operationalperiod | [OperationalPeriodApiContractTest](../../backend/src/test/java/com/surimap/operationalperiod/OperationalPeriodApiContractTest.java) |

S8 내용·연결 검토 중 다음 후보도 추가했다. 이름만 바꾸면 실제 동작을 숨길 수 있는 경우에는 구현 범위부터 확인한다.

| 추가 후보 | 확인한 이유 |
|---|---|
| [OpComparisonApiContractTest](../../backend/src/test/java/com/surimap/opcomparison/OpComparisonApiContractTest.java) | `Contract` 분류 대신 실제 Controller·Service 등 검증 대상을 드러낼 필요가 있음 |
| [SearchHistorySummaryGenerationJob](../../backend/src/main/java/com/surimap/summary/SearchHistorySummaryGenerationJob.java)의 `enqueue*` | 영속 작업 큐에 넣지 않고 커밋 후 콜백에서 동기 호출함. `Job`·`enqueue`가 암시하는 비동기 실행·복구와 다름 |
| [features/board/components/s8OpHandoverSlotTypes.tsx](../../frontend/src/features/board/components/s8OpHandoverSlotTypes.tsx) | Spec 번호 대신 상황판의 차수·인수인계 표시 타입과 보조 컴포넌트라는 역할을 드러낼 필요가 있음 |
| [src/components/s8OpHandoverSlotTypes.ts](../../frontend/src/components/s8OpHandoverSlotTypes.ts) | 위 타입 파일과의 사용 관계·중복을 확인한 뒤 Spec 번호를 제거 |
| [S8OpHandoverSlots.test.tsx](../../frontend/src/features/board/components/S8OpHandoverSlots.test.tsx) | Spec 번호 대신 검증하는 표시 동작을 드러낼 필요가 있음 |
| [S8IdempotencyFixtures](../../backend/src/test/java/com/surimap/operationalperiod/fixture/S8IdempotencyFixtures.java) | Spec 번호 대신 차수 전환의 중복 요청·충돌 입력이라는 역할을 드러낼 필요가 있음 |
| [OperationalPeriodFixtureExactnessTest](../../backend/src/test/java/com/surimap/operationalperiod/fixturetest/OperationalPeriodFixtureExactnessTest.java) | 고정값 대조와 실제 차수 전환 검증을 구분. 옛 상태·URL을 고정한 검사의 필요성을 함께 확인 |
| [IncidentHandoverSupportAssignmentContractTest](../../backend/src/test/java/com/surimap/incident/IncidentHandoverSupportAssignmentContractTest.java) | `Contract` 분류 대신 인계·지원 배정의 실제 검증 대상을 드러낼 필요가 있음 |

## 사건 가져오기·배정·종료 — 추가 후보

2026-09-21 S1-1의 구현·소비 테스트를 확인하며 추가했다. 아래 테스트는 `Contract`·`Fixture`·`Integration` 분류 대신 검증 대상을 드러낼 필요가 있다. 최종 이름·통합 여부는 실제 연결 범위와 중복을 확인한 뒤 정한다. 기존 `Sc01`·`Sc12`·사건 흐름·인계 테스트는 위 항목을 유지하거나 갱신했다.

| 추가 후보 | 확인할 점 |
|---|---|
| [IncidentActiveReadDtoContractTest](../../backend/src/test/java/com/surimap/incident/IncidentActiveReadDtoContractTest.java) | 진행 중 사건의 DTO 필드 검사와 실제 조회 검증을 구분 |
| [IncidentTerminalReadDtoContractTest](../../backend/src/test/java/com/surimap/incident/IncidentTerminalReadDtoContractTest.java) | 종료 응답의 개인정보 제외 검사와 실제 정보 파기 검증을 구분 |
| [MissingPersonConsumerAllowlistContractTest](../../backend/src/test/java/com/surimap/incident/MissingPersonConsumerAllowlistContractTest.java) | 응답·조회 모델의 허용 필드 검사와 실제 개인정보 접근 제어 검증을 구분 |
| [IncidentAssignmentImportContractTest](../../backend/src/test/java/com/surimap/incident/IncidentAssignmentImportContractTest.java) | 배정 반영 Service의 검증 내용·연결 범위를 확인해 명명 |
| [IncidentLifecycleFixtureContractTest](../../backend/src/test/java/com/surimap/incident/IncidentLifecycleFixtureContractTest.java) | 공용 데이터의 종료 후 재전송 기대값 검사이며 실제 사건 종료 시험이 아님 |
| [IncidentCloseCommandContractTest](../../backend/src/test/java/com/surimap/incident/IncidentCloseCommandContractTest.java) | 종료 Controller·Service·DB 검증을 구분해 명명 |
| [IncidentImportApiContractTest](../../backend/src/test/java/com/surimap/incident/IncidentImportApiContractTest.java) | 가져오기 Controller·Service·DB 검증을 구분해 명명 |
| [Mock112WebhookIncidentCloseIntegrationTest](../../backend/src/test/java/com/surimap/incident/Mock112WebhookIncidentCloseIntegrationTest.java) | 웹훅 종료 처리의 실제 검증 대상·외부 대역 범위를 드러내도록 명명 |
| [IncidentAssignmentAccessGuard](../../backend/src/main/java/com/surimap/incident/service/IncidentAssignmentAccessGuard.java) | 계정에 활성 배정이 하나라도 있는지만 검사함. 선택한 사건의 접근 권한까지 확인하는 이름으로 오해되지 않도록 책임부터 확인 |

## 인증·파기·수색 구역·경로·상황판 — 추가 후보

2026-09-21 S1-2 → S1-3 → S2 → S3-1 → S3-2를 확인하며 추가했다. 아래 테스트·지원 파일은 이름 패턴으로 찾은 후보이며, 이번에 모두 실행하거나 내부 검증을 검토한 것은 아니다. 기존 `RedTest`·`Sc*`·Runner 항목은 중복 등록하지 않았다. 테스트 지원 환경이 분명한 `PostGisIntegrationTestSupport`까지 일괄 변경하지 않는다.

| 영역 | 추가 후보·확인할 점 |
|---|---|
| 인증 | [WithMockAccountFixtureContractTest](../../backend/src/test/java/com/surimap/account/WithMockAccountFixtureContractTest.java) — `Fixture`·`Contract` 대신 인증 설정의 검증 대상 확인 |
| 인증 | [AuthPhoneApiIntegrationTest](../../backend/src/test/java/com/surimap/account/AuthPhoneApiIntegrationTest.java) — API·인증·DB의 실제 검증 경계를 확인해 명명 |
| 인증 | [AccountIdentityPersistenceIntegrationTest](../../backend/src/test/java/com/surimap/account/AccountIdentityPersistenceIntegrationTest.java) — 저장·Mapper 검증 대상 확인 |
| 인증 | [AuthPolicePhoneHarnessFixtures](../../backend/src/test/java/com/surimap/account/harness/AuthPolicePhoneHarnessFixtures.java) — `Harness` 대신 인증·업무폰 테스트 데이터라는 역할 검토 |
| 인증 | [RealS1_2AuthPolicePhoneContract](../../backend/src/test/java/com/surimap/account/harness/RealS1_2AuthPolicePhoneContract.java) — `Real`·Spec 번호·`Contract` 대신 실제 서비스 연결 범위 확인 |
| 업무폰 | [PolicePhonePersistenceIntegrationTest](../../backend/src/test/java/com/surimap/policephone/PolicePhonePersistenceIntegrationTest.java) — Service·Mapper 중 검증 책임 확인 |
| 업무폰 | [PolicePhoneHeartbeatIntegrationTest](../../backend/src/test/java/com/surimap/policephone/PolicePhoneHeartbeatIntegrationTest.java) — heartbeat 저장·이벤트 검증 범위 확인 |
| Android 인증 | [AuthBootstrapContractTest](../../android/app/src/test/java/com/surimap/feature/bootstrap/AuthBootstrapContractTest.kt) — 인증 초기화의 검증 동작 기준으로 명명 |
| Android 업무폰 | [PolicePhoneNavigationContractTest](../../android/app/src/test/java/com/surimap/ui/navigation/PolicePhoneNavigationContractTest.kt) — 기존 대표 패턴의 실제 경로 등록. 탐색·복귀 동작의 검증 대상 확인 |
| 데이터 파기 | [InMemoryS1_3PurgeOrchestrationContract](../../backend/src/test/java/com/surimap/retention/purge/harness/InMemoryS1_3PurgeOrchestrationContract.java) — Spec 번호·`Contract` 대신 대역 역할 확인 |
| 데이터 파기 | [PurgeOrchestrationHarnessFixtures](../../backend/src/test/java/com/surimap/retention/purge/harness/PurgeOrchestrationHarnessFixtures.java) — 파기 단계·실패 입력을 준비하는 범위 확인 |
| 데이터 파기 | [MockPurgeHookRegistryContractTest](../../backend/src/test/java/com/surimap/retention/purge/MockPurgeHookRegistryContractTest.java) — 대역 등록 검사와 실제 파기 연결 검증을 구분 |
| 수색 구역 | [SearchAreaBoundaryAlertApiIntegrationTest](../../backend/src/test/java/com/surimap/searcharea/SearchAreaBoundaryAlertApiIntegrationTest.java) — 구역 경계 API의 실제 검증 대상 확인 |
| 수색 구역 | [SearchAreaApiContractTest](../../backend/src/test/java/com/surimap/searcharea/SearchAreaApiContractTest.java) — Controller·Service 검증 책임 확인 |
| 수색 구역 | [SearchAreaQueryByOpContractTest](../../backend/src/test/java/com/surimap/maparea/SearchAreaQueryByOpContractTest.java) — 차수별 구역 조회의 대상·대역 범위 확인 |
| 수색 구역 | [SearchAreaAssignmentQueryMockContractTest](../../backend/src/test/java/com/surimap/maparea/SearchAreaAssignmentQueryMockContractTest.java) — 배정 조회 대역과 실제 SQL 검증 구분 |
| 수색 구역 | [SearchAreaQueryByIncidentContractTest](../../backend/src/test/java/com/surimap/maparea/SearchAreaQueryByIncidentContractTest.java) — 사건별 조회 대상에 맞춰 명명 |
| 수색 구역 | [SearchAreaQueryOverallOfContractTest](../../backend/src/test/java/com/surimap/maparea/SearchAreaQueryOverallOfContractTest.java) — 전체 수색 범위 조회 대상에 맞춰 명명 |
| 도형 저장 | [PostGisExtensionIntegrationTest](../../backend/src/test/java/com/surimap/maparea/geometry/validation/PostGisExtensionIntegrationTest.java) — 확장 설치·실제 공간 SQL의 검증 범위 확인 |
| 도형 저장 | [JtsGeometryTypeHandlerIntegrationTest](../../backend/src/test/java/com/surimap/maparea/geometry/validation/JtsGeometryTypeHandlerIntegrationTest.java) — 타입 변환·DB 왕복 검증 범위 확인 |
| 수색 경로 | [GpsPointValidationFixturesTest](../../backend/src/test/java/com/surimap/domain/path/validation/GpsPointValidationFixturesTest.java) — 데이터 일치와 GPS 품질 동작 검증을 구분 |
| 상황판 | [IncidentBoardApiContractTest](../../backend/src/test/java/com/surimap/board/IncidentBoardApiContractTest.java) — HTTP 응답·조립 검증 경계 확인 |
| 상황판 | [IncidentBoardSourceRowCollectorIntegrationTest](../../backend/src/test/java/com/surimap/board/IncidentBoardSourceRowCollectorIntegrationTest.java) — 여러 원본 조회·조립의 실제 연결 범위 확인 |
| Frontend 상황판 | [S3PathMarkerSlots.test.tsx](../../frontend/src/features/board/components/S3PathMarkerSlots.test.tsx) — Spec 번호 대신 경로·마커 표시의 검증 동작 확인 |

다음 이름은 코드를 읽고 예상 동작과의 차이를 확인했다. 이름만 바꿔 구현 차이를 숨기지 않는다.

| 후보 | 확인한 차이 |
|---|---|
| [PolicePhonePersistenceService.encryptToken](../../backend/src/main/java/com/surimap/policephone/PolicePhonePersistenceService.java) | 암호화하지 않고 `cipher:`만 붙임. 필요한 토큰 보호 방식을 먼저 정하고 이름을 맞출 것 |
| [GeometryPolicy.s2HarnessDefault](../../backend/src/main/java/com/surimap/maparea/geometry/policy/GeometryPolicy.java) | 운영 검증 설정에서도 쓰는 값에 Spec 번호·시험용 기본값 이름이 남음. 적용 범위 확인 |
| [DefaultIncidentBoardSourceRowCollector](../../backend/src/main/java/com/surimap/board/DefaultIncidentBoardSourceRowCollector.java)의 `geometryHash`·`latestEventId` 생성 | 좌표 해시·실제 이벤트 조회로 오해할 수 있으나 ID·상태·버전으로 값을 만듦. 공개 필드 호환성과 실제 추적 요구를 함께 검토 |

## 시스템 구조 대조 — 추가 후보

2026-09-21 Architecture를 코드와 대조하며 발견했다. 후보만 등록하며 실제 이름·동작은 바꾸지 않았다.

| 후보 | 확인한 이유 |
|---|---|
| [LocalTileService](../../backend/src/main/java/com/surimap/offlinepackage/service/LocalTileService.java)·[LocalTileServiceTest](../../backend/src/test/java/com/surimap/offlinepackage/LocalTileServiceTest.java) | 로컬 지도 파일을 읽는 구현이 아니라 고정 시험 타일을 제공한다. `Local` 대신 시험 데이터 제공 역할이 드러나도록 함께 검토 |
| [OpenAiComparisonConfig](../../backend/src/main/java/com/surimap/client/openai/OpenAiComparisonConfig.java)·[OpenAiComparisonProperties](../../backend/src/main/java/com/surimap/client/openai/OpenAiComparisonProperties.java) | 차수 비교뿐 아니라 수색 이력 요약에도 쓰는 공통 연동 설정이다. `Comparison`이 책임을 좁혀 보이게 하므로 실제 사용 범위에 맞춰 검토 |
| [SearchPathGpsBatchRecorder.FLUSH_INTERVAL_MS](../../android/app/src/main/java/com/surimap/feature/search/data/SearchPathGpsBatchRecorder.kt) | 고정 HTTP 전송 주기가 아니라 모인 좌표의 측정 시간 범위를 검사하는 값이다. 시간 범위 조건이 드러나도록 검토 |
| [TileManifestFixtureExactnessTest](../../backend/src/test/java/com/surimap/offlinepackage/TileManifestFixtureExactnessTest.java) | 고정 시험 데이터의 값·참조를 검사한다. 실제 타일 준비·서빙 검증과 구분하고 필요한 검사·이름을 함께 정리 |
| [OfflinePackageManifestSourceIntegrationTest](../../backend/src/test/java/com/surimap/offlinepackage/OfflinePackageManifestSourceIntegrationTest.java) | `Integration` 분류 대신 검증 대상에 맞춰 정리할 후보다. 사건·차수·구역·마커 조회와 이벤트가 대역인 구성을 실제 전체 연동 시험과 구분 |

## DB 문서 대조 — 추가 후보

2026-09-21 저장·조회 SQL과 연결 객체를 대조하며 발견했다. 실제 클래스·Mapper는 변경하지 않았다.

| 후보 | 확인한 이유 |
|---|---|
| [EventDispatchJobRow](../../backend/src/main/java/com/surimap/eventhub/adapter/EventDispatchJobRow.java)·[EventDispatchJobDispatchRecord](../../backend/src/main/java/com/surimap/eventhub/adapter/EventDispatchJobDispatchRecord.java) | 같은 전송 작업의 동일한 9개 필드를 INSERT용·조회용 record로 나눴고 `Dispatch`도 중복된다. SSE 후속 정리에서 저장·조회 역할을 하나의 업무 객체로 합칠 수 있는지 확인한 뒤 이름을 정한다. |

## API 문서 검사 — 추가 후보

| API 문서 검사 후보 | 확인한 이유 |
|---|---|
| [ApiImplementationStatusCoverageTest](../../backend/src/test/java/com/surimap/architecture/ApiImplementationStatusCoverageTest.java) | 이름과 달리 API 동작 전체가 아니라 Markdown URL 포함 관계와 Spring 경로 등록만 확인한다. REST Docs 전환에서 대체·제거 여부를 먼저 정하고, 남길 경우 검증 대상을 드러내는 이름·한글 DisplayName·밑줄 메서드명으로 정리한다. 현재 Java 코드는 유지했다. |

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
| [resolveInitialMapView의 overall-ready·overallSearchArea](../../frontend/src/features/situationBoard/presentation/components/map/searchMapCanvasData.ts) | 전체 구역이 없어도 다른 Polygon 범위가 있으면 이 상태·필드로 반환한다. 실제 포함 구역과 초기 지도 표시 상태의 의미를 확인해 명명. 공개 API 필드 변경과 구분 |
| [createDeviceTitle / createDeviceMeta](../../frontend/src/features/offlinePackage/presentation/model/offlinePackageStatusPageViewModel.ts) | `createPolicePhoneTitle` / `createPolicePhoneMeta` |
| [errorCode()](../../frontend/src/shared/api/client.ts) | `resolveApiErrorCode` — 응답 본문 또는 HTTP 상태로 오류 코드 결정 |
| [incidentBoardQueryKeyParams() / emptyCursor()](../../frontend/src/features/board/api/incidentBoardApi.ts) | `buildIncidentBoardQueryKeyParams` / `createEmptyBoardSourceCursor` |
| [geometryUtils.ts](../../frontend/src/features/areaEdit/presentation/utils/geometryUtils.ts)·[AreaEditMapCanvas.tsx](../../frontend/src/features/areaEdit/presentation/components/AreaEditMapCanvas.tsx)의 signedArea() / isBetween() | 각각 삼각형 부호 면적의 2배 계산·좌표 범위 검사에 맞춰 명명 |

## Android

| 현재 이름 | 변경 후보·확인할 점 |
|---|---|
| [AndroidHarnessFixtureCatalog / AndroidHarnessFixtureEntry](../../android/app/src/test/java/com/surimap/testing/AndroidHarnessFixtureCatalog.kt) | 공용 JSON의 catalog를 읽는 테스트 지원 코드다. 시나리오 실행기처럼 보이는 `Harness`를 덜고 데이터 조회 역할 기준으로 명명 검토 |
| [AndroidHarnessFixtureCatalogTest](../../android/app/src/test/java/com/surimap/testing/AndroidHarnessFixtureCatalogTest.kt) | 리소스 로딩·ID·사용 시나리오 검증이다. 위 로더 이름과 함께 정리하며 실제 앱 흐름 검증과 구분 |
| [HarnessSyncStatus / EnqueueResult.harnessStatus](../../android/app/src/main/java/com/surimap/core/sync/LocalWriteOperation.kt) | `LocalSyncStatus` / `localSyncStatus` 후보 — 실제 앱의 로컬 동기화 상태 |
| [initialHarnessStatus](../../android/app/src/main/java/com/surimap/core/sync/RoomLocalSyncServices.kt) | `initialLocalSyncStatus` 후보 — 위 상태 이름과 함께 변경 |
| [HarnessStatusMapper](../../android/app/src/main/java/com/surimap/core/sync/OutboxStateMachine.kt) | 상태 변환 역할·실사용 여부 확인 후 결정 |
| [MarkerPhotoPayloadReader / read()](../../android/app/src/main/java/com/surimap/feature/marker/data/MarkerPhotoPayloadReader.kt) | 읽기뿐 아니라 이미지 변환·압축을 하는 역할이 드러나도록 명명 |
| [OutboxHarnessIntegrationTest](../../android/app/src/test/java/com/surimap/core/sync/OutboxHarnessIntegrationTest.kt) | 공용 catalog와 Outbox 대역 흐름을 확인한다. `Harness`·`Integration` 대신 실제 검증 대상을 확인해 `<대상>Test`로 정리 |

## mock-112

| 현재 이름 | 변경 후보·확인할 점 |
|---|---|
| [api()](../../mock-112/src/main/resources/static/app.js) | `sendApiRequest` |
| [resetAll()](../../mock-112/src/main/resources/static/app.js) | `resetMock112Data` — 초기화 대상 범위 표시 |
| [KeycloakThemeContractTest](../../mock-112/src/test/java/com/mock112/config/KeycloakThemeContractTest.java) | S1-1 연결 범위 탐색에서 경로 등록. `Contract` 분류 대신 테마의 실제 검증 대상·범위를 확인해 명명 |
| [S3CompatibleMissingPersonPhotoStorageIT](../../mock-112/src/test/java/com/mock112/photo/S3CompatibleMissingPersonPhotoStorageIT.java) | 같은 탐색에서 발견. `IT` 분류 대신 저장 동작의 검증 대상을 드러내되 빌드의 테스트 선택 규칙도 함께 확인 |

Infra에서는 이번 탐색으로 추가할 후보를 찾지 못했다. 이미 합의한 k6 이름은 유지한다.

## Tasks·시연 기록 검토 — 추가 후보

2026-09-21 문서 정리에서 찾았다. 테스트 4개는 이름·경로를 확인한 후보이며 내부 검증을 모두 검토하거나 실행한 것은 아니다. 이름 변경은 각 대상의 동작·사용처를 정리할 때 진행한다.

| 추가 후보 | 확인할 점 |
|---|---|
| [AndroidUiScenarioCoverageTest](../../android/app/src/test/java/com/surimap/feature/AndroidUiScenarioCoverageTest.kt) | `ScenarioCoverage`가 실제 화면 흐름 검증을 뜻하는지 확인하고 대상·검증 범위로 명명 |
| [PoliComponentVariantContractTest](../../android/app/src/test/java/com/surimap/ui/components/PoliComponentVariantContractTest.kt) | `Contract` 분류 대신 컴포넌트 상태·표시 중 실제 검증 대상을 확인 |
| [NetworkStateFixturesTest](../../android/app/src/test/java/com/surimap/testing/NetworkStateFixturesTest.kt) | 입력 값 검사와 네트워크 상태 전이 검증을 구분 |
| [DemoScenarioFixturesTest](../../backend/src/test/java/com/surimap/demo/DemoScenarioFixturesTest.java) | 고정 입력 검사와 실제 시연 흐름 실행을 구분 |

후보였던 `check_l4_d01_runtime_preflight.py`·`run_l4_d01_deployed_stability.py`는 현재 호출자가 없어 이름 변경 대신 제거했다. 실제 단말·서버에서 실행하지 않았으며 [과거 복구 기록](../evidence/android-network-recovery-2026-05-14/README.md#옛-기록도구의-복원)에 코드 원문과 재사용 전 확인 사항을 남겼다.

| 후속 후보 | 확인할 점 |
|---|---|
| [infra/ci/verify-evidence.sh](../../infra/ci/verify-evidence.sh)·[사용 안내](../../infra/ci/README.md) | `RED`·`L2-D01` 표현과 포괄적인 `evidence` 이름이 남아 있다. 현재 Jenkinsfile의 직접 호출은 찾지 못했으며 CI 결과 파일 존재·문구 검사와 실제 품질 판정을 구분해 필요성·이름·안내를 함께 검토 |
