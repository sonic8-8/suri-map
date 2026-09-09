# 코드베이스 이름 변경 후보

2026-09-09 1차 탐색. Backend·Frontend·Android·mock-112·Infra의 파일명·선언명을 검색하고, 역할이 모호한 후보는 구현·사용처를 확인했다. 메서드·변수 전수 검토는 아니다.

현재 이름은 유지한다. 마커·알림부터 함께 변경하고, 나머지는 해당 영역을 정리할 때 이름을 확정한다. API·이벤트·DB 필드·fixture ID 같은 계약 이름은 이 목록만으로 바꾸지 않는다.

`Sc08` 같은 시나리오 번호도 파일·클래스 이름의 변경 대상이다. 번호 대신 검증하는 동작이나 실행·데이터 준비 역할이 드러나도록 정한다. 문서의 시나리오 번호와 fixture ID는 유지한다.

## 마커·알림 — 먼저 진행

`MarkerLocationValidatorRedTest`는 좌표 검증 규칙을 공통 도메인으로 옮기면서 [MarkerTest](../../backend/src/test/java/com/surimap/domain/marker/MarkerTest.java)에 통합했다.

`Sc06MarkerPhotoHarnessRedTest`·`Sc06MarkerPhotoHarnessRunner`는 [AppMarkerServiceTest](../../backend/src/test/java/com/surimap/app/service/marker/AppMarkerServiceTest.java)에 실제 DB 기반 마커 생성 후 사진 첨부·잘못된 좌표 거부 검증을 보강한 뒤 제거했다. 테스트 내부에서 흉내 낸 SSE 수신·상황판 갱신은 실제 연동 검증으로 옮기지 않았다.

`Sc08NotificationHarnessRedTest`·`Sc08NotificationHarnessRunner`도 기존 `AppMarkerServiceTest`에 알림 저장·DB 커밋 후 FCM 호출·재전송 중복 방지·전송 실패 시 저장 결과 유지 검증을 보강한 뒤 제거했다. DB와 서비스는 실제 구현을 사용하고 외부 FCM만 Mock으로 대체한다. 실제 SSE 수신·브라우저 표시·업무폰 알림 수신은 별도 확인 대상이다.

`SupportRequestNotificationDispatchService`·`BoardToastEvidence`와 전용 테스트 2개는 서버에서 사용하지 않아 제거했다. 실제 알림 저장·전달 검증은 기존 `AppMarkerServiceTest`에 남아 있다. 미사용 코드의 Map 검사와 테스트용 표시 결과를 실제 서버·상황판 동작으로 옮기지는 않았다.

| 현재 이름 | 변경 후보 |
|---|---|
| [MarkerOpBindingRedTest](../../backend/src/test/java/com/surimap/marker/domain/MarkerOpBindingRedTest.java) | `MarkerOpBindingValidatorTest` |
| [MarkerNotificationToastQueryIntegrationTest](../../backend/src/test/java/com/surimap/marker/notification/MarkerNotificationToastQueryIntegrationTest.java) | `MarkerNotificationMapperTest` |
| [MarkerNotificationContractTest](../../backend/src/test/java/com/surimap/marker/notification/MarkerNotificationContractTest.java) | 검증 범위 확인 후 결정 |

## Backend — 나머지 Red 테스트

`Red`를 제거하고 실제 검증 대상에 맞춰 이름을 정한다. 마커·알림 대상은 위 표에 있다.

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
| eventhub | [LiveSseFanoutRedTest](../../backend/src/test/java/com/surimap/eventhub/LiveSseFanoutRedTest.java) |
| eventhub | [OwnerPayloadSchemaValidationRedTest](../../backend/src/test/java/com/surimap/eventhub/OwnerPayloadSchemaValidationRedTest.java) |
| eventhub | [SseTerminalStreamReleaseRedTest](../../backend/src/test/java/com/surimap/eventhub/SseTerminalStreamReleaseRedTest.java) |
| handover | [HandoverMemoContextBindingRedTest](../../backend/src/test/java/com/surimap/handover/HandoverMemoContextBindingRedTest.java) |
| handover | [HandoverMemoCreatePublishRequestRedTest](../../backend/src/test/java/com/surimap/handover/HandoverMemoCreatePublishRequestRedTest.java) |
| harness/sc02 | [Sc02SupportAssignmentFcmHarnessRedTest](../../backend/src/test/java/com/surimap/harness/sc02/Sc02SupportAssignmentFcmHarnessRedTest.java) |
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
| [MarkerQueryMapperIntegrationTest](../../backend/src/test/java/com/surimap/marker/repository/MarkerQueryMapperIntegrationTest.java) | `MarkerMapperTest`로 조회 검증 통합 검토 |
| [OpComparisonAnalysisMapperIntegrationTest](../../backend/src/test/java/com/surimap/opcomparison/OpComparisonAnalysisMapperIntegrationTest.java) | `OpComparisonAnalysisMapperTest` |
| [OpComparisonRegionFactMapperIntegrationTest](../../backend/src/test/java/com/surimap/opcomparison/OpComparisonRegionFactMapperIntegrationTest.java) | `OpComparisonRegionFactMapperTest` |
| [SearchHistorySummaryMapperIntegrationTest](../../backend/src/test/java/com/surimap/summary/SearchHistorySummaryMapperIntegrationTest.java) | `SearchHistorySummaryMapperTest` |
| [AuthPolicePhoneHarnessRunner](../../backend/src/test/java/com/surimap/account/harness/AuthPolicePhoneHarnessRunner.java) | 실제 실행 역할에 맞는 테스트 지원 코드 이름 |
| [Sc02ToSc12BoardConvergenceHarnessRunner](../../backend/src/test/java/com/surimap/board/Sc02ToSc12BoardConvergenceHarnessRunner.java) | 실제 실행 역할에 맞는 테스트 지원 코드 이름 |
| [EventHubHarnessRunner](../../backend/src/test/java/com/surimap/eventhub/harness/EventHubHarnessRunner.java) | 실제 실행 역할에 맞는 테스트 지원 코드 이름 |
| [Sc02SupportAssignmentFcmHarnessRunner](../../backend/src/test/java/com/surimap/harness/sc02/Sc02SupportAssignmentFcmHarnessRunner.java) | 실제 실행 역할에 맞는 테스트 지원 코드 이름 |
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
| harness/sc02 | [Sc02HandoverSupportAssignmentIntegrationTest](../../backend/src/test/java/com/surimap/harness/sc02/Sc02HandoverSupportAssignmentIntegrationTest.java) |
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
