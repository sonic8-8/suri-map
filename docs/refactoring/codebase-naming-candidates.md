# 코드베이스 이름 변경 후보

2026-09-09 1차 탐색. Backend·Frontend·Android·mock-112·Infra의 파일명·선언명을 검색하고, 역할이 모호한 후보는 구현·사용처를 확인했다. 메서드·변수 전수 검토는 아니다.

남은 후보를 먼저 나열하고, 완료한 변경은 마지막의 [반영 이력](#반영-이력-요약)에 요약한다. 후보의 이름은 해당 영역을 정리할 때 실제 역할·사용처를 확인해 확정한다. API·이벤트·DB 필드·fixture ID 같은 계약 이름은 이 목록만으로 바꾸지 않는다.

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

## 마커·알림 — 남은 확인

| 대상 | 확인할 점 |
|---|---|
| [SseEmitterLiveEventSink](../../backend/src/main/java/com/surimap/eventhub/stream/SseEmitterLiveEventSink.java) | 재전송 중 새 이벤트 대기·한도·순서 전환도 맡으므로 `LiveEventSink`만으로는 역할이 좁게 읽힌다. DB 재전송 연결을 마친 뒤 연결별 전송 담당이라는 이름으로 정리할지 확인한다. 이번에는 이름을 바꾸지 않았다. |
| [IncidentHandoverSupportAssignmentScenarioTest](../../backend/src/test/java/com/surimap/incident/IncidentHandoverSupportAssignmentScenarioTest.java) | `ScenarioTest`를 별도 분류로 쓰지 않기로 한 기준에 맞춰 검증 대상별 분리·통합과 이름을 정한다. 실제 DB·FCM 호출 검사와 SSE 직접 호출·고정 자료 조립·probe 행 검사를 구분한다. |
| [MarkerPhotoPurgeHook](../../backend/src/main/java/com/surimap/api/service/photo/MarkerPhotoPurgeHook.java)·[MarkerPhotoPurgeHookAdapter](../../backend/src/main/java/com/surimap/api/service/photo/MarkerPhotoPurgeHookAdapter.java) | 패키지 이동은 끝났지만 실제 삭제 구현·빈 등록은 별도 확인 대상이다. 이름 변경만으로 연결 누락을 해결한 것으로 보지 않는다. |
| [남은 마커 테스트·fixture](../../backend/src/test/java/com/surimap/marker/) | 운영 `com/surimap/marker` 디렉터리는 제거했지만 테스트 디렉터리까지 정리한 것은 아니다. 각 검증의 대상·중복·대역 사용을 확인한다. |

FCM의 실패 기록과 재시도·배정 알림의 전송 시점은 별개다. 마커·배정의 실패 로그 보완은 [로컬 이슈 4](../issues/local/4-fcm-delivery-failure-not-recorded.md), 남은 연결·복구·전달 문제는 [이벤트 전달 문서](../features/event-delivery.md)에서 확인한다. 이름 변경 이력만으로 이 동작들이 검증됐다고 판단하지 않는다.

## Backend — Red 테스트

남아 있는 `RedTest`도 정리 대상이다. 실제 검증 대상과 중복 여부를 확인한 뒤 `<검증 대상>Test`로 변경하거나 기존 테스트에 합친다. 마커·알림 대상의 처리 내역은 마지막의 반영 이력에서 확인한다.

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
| [CoreRuntimeSchemaMigrationIntegrationTest](../../backend/src/test/java/com/surimap/database/CoreRuntimeSchemaMigrationIntegrationTest.java) | 실제 PostgreSQL의 Flyway schema와 옛 ID 전환을 검사한다. `Integration` 분류를 덜고 migration 검증 대상으로 이름을 정리할 후보이며, 이번 SSE 순번 구현에서는 기존 파일을 변경하지 않았다. |
| [OpComparisonAnalysisMapperIntegrationTest](../../backend/src/test/java/com/surimap/opcomparison/OpComparisonAnalysisMapperIntegrationTest.java) | `OpComparisonAnalysisMapperTest` |
| [OpComparisonRegionFactMapperIntegrationTest](../../backend/src/test/java/com/surimap/opcomparison/OpComparisonRegionFactMapperIntegrationTest.java) | `OpComparisonRegionFactMapperTest` |
| [SearchHistorySummaryMapperIntegrationTest](../../backend/src/test/java/com/surimap/summary/SearchHistorySummaryMapperIntegrationTest.java) | `SearchHistorySummaryMapperTest` |
| [AuthPolicePhoneHarnessRunner](../../backend/src/test/java/com/surimap/account/harness/AuthPolicePhoneHarnessRunner.java) | 실제 실행 역할에 맞는 테스트 지원 코드 이름 |
| [Sc02ToSc12BoardConvergenceHarnessRunner](../../backend/src/test/java/com/surimap/board/Sc02ToSc12BoardConvergenceHarnessRunner.java) | 공용 데이터로 응답을 조립하고 고정 시나리오 표를 반환함. 실제 시나리오·SSE 실행기로 오해되지 않도록 필요한 보조 역할부터 정리 |
| [EventHubHarnessRunner](../../backend/src/test/java/com/surimap/eventhub/harness/EventHubHarnessRunner.java) | 실제 실행 역할에 맞는 테스트 지원 코드 이름 |
| [InMemoryS4EventHubContract](../../backend/src/test/java/com/surimap/eventhub/harness/InMemoryS4EventHubContract.java) | `S4`·`Contract` 대신 테스트 입력·기록 대역이라는 역할을 드러낼 후보다. 2026-09-28 메모리 저장소를 `src/test`로 옮겨 이 대역에서만 재사용했으며 실제 DB 재전송 검증과 구분한다. |
| [Sc04SearchAreaHarnessTest](../../backend/src/test/java/com/surimap/harness/sc04/Sc04SearchAreaHarnessTest.java) | 검증 대상·시나리오 확인 후 결정 |
| [Sc07Sc09OfflineReplayHarnessRunner](../../backend/src/test/java/com/surimap/harness/sc09/Sc07Sc09OfflineReplayHarnessRunner.java) | 실제 실행 역할에 맞는 테스트 지원 코드 이름 |
| [Sc10OpHandoverHarnessTest](../../backend/src/test/java/com/surimap/harness/sc10/Sc10OpHandoverHarnessTest.java) | 검증 대상·시나리오 확인 후 결정 |
| [IncidentFlowHarnessMockContractTest](../../backend/src/test/java/com/surimap/incident/IncidentFlowHarnessMockContractTest.java) | 공용 JSON 로딩과 인증·OP1·이벤트 대역 간 호출을 검사함. 실제 가져오기·DB 검증과 구분해 필요한 검사·이름을 결정 |
| [Sc03PackageTileHarnessRunner](../../backend/src/test/java/com/surimap/offlinepackage/Sc03PackageTileHarnessRunner.java) | 실제 실행 역할에 맞는 테스트 지원 코드 이름 |
| [PurgeOrchestrationHarnessRunner](../../backend/src/test/java/com/surimap/retention/purge/harness/PurgeOrchestrationHarnessRunner.java) | 실제 실행 역할에 맞는 테스트 지원 코드 이름 |

## Backend — 시나리오 번호 파일

위 표에 있는 `Sc*` 파일과 함께 정리한다. 최종 이름은 검증 내용과 사용처를 확인한 뒤 정한다.

| 영역 | 변경 대상 |
|---|---|
| harness/sc01 | [Sc01IncidentStartIntegrationTest](../../backend/src/test/java/com/surimap/harness/sc01/Sc01IncidentStartIntegrationTest.java) / [Sc01IncidentStartBridgeTest](../../backend/src/test/java/com/surimap/harness/sc01/Sc01IncidentStartBridgeTest.java) |
| harness/sc04 | [Sc04Fixtures](../../backend/src/test/java/com/surimap/harness/sc04/fixture/Sc04Fixtures.java) |
| harness/sc10 | [Sc10Fixtures](../../backend/src/test/java/com/surimap/harness/sc10/fixture/Sc10Fixtures.java) |
| harness/sc12 | [Sc12IncidentCloseDataPurgeIntegrationTest](../../backend/src/test/java/com/surimap/harness/sc12/Sc12IncidentCloseDataPurgeIntegrationTest.java) |

## 인수인계·수색 차수

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

## 사건 가져오기·배정·종료

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

## 인증·파기·수색 구역·경로·상황판

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

## 지도·AI 연동·GPS 수집

2026-09-21 Architecture를 코드와 대조하며 발견했다. 후보만 등록하며 실제 이름·동작은 바꾸지 않았다.

| 후보 | 확인한 이유 |
|---|---|
| [LocalTileService](../../backend/src/main/java/com/surimap/offlinepackage/service/LocalTileService.java)·[LocalTileServiceTest](../../backend/src/test/java/com/surimap/offlinepackage/LocalTileServiceTest.java) | 로컬 지도 파일을 읽는 구현이 아니라 고정 시험 타일을 제공한다. `Local` 대신 시험 데이터 제공 역할이 드러나도록 함께 검토 |
| [OpenAiComparisonConfig](../../backend/src/main/java/com/surimap/client/openai/OpenAiComparisonConfig.java)·[OpenAiComparisonProperties](../../backend/src/main/java/com/surimap/client/openai/OpenAiComparisonProperties.java) | 차수 비교뿐 아니라 수색 이력 요약에도 쓰는 공통 연동 설정이다. `Comparison`이 책임을 좁혀 보이게 하므로 실제 사용 범위에 맞춰 검토 |
| [SearchPathGpsBatchRecorder.FLUSH_INTERVAL_MS](../../android/app/src/main/java/com/surimap/feature/search/data/SearchPathGpsBatchRecorder.kt) | 고정 HTTP 전송 주기가 아니라 모인 좌표의 측정 시간 범위를 검사하는 값이다. 시간 범위 조건이 드러나도록 검토 |
| [TileManifestFixtureExactnessTest](../../backend/src/test/java/com/surimap/offlinepackage/TileManifestFixtureExactnessTest.java) | 고정 시험 데이터의 값·참조를 검사한다. 실제 타일 준비·서빙 검증과 구분하고 필요한 검사·이름을 함께 정리 |
| [OfflinePackageManifestSourceIntegrationTest](../../backend/src/test/java/com/surimap/offlinepackage/OfflinePackageManifestSourceIntegrationTest.java) | `Integration` 분류 대신 검증 대상에 맞춰 정리할 후보다. 사건·차수·구역·마커 조회와 이벤트가 대역인 구성을 실제 전체 연동 시험과 구분 |

## 이벤트 전송 처리 클래스

2026-09-25 [역할 분리 합의](../features/event-delivery.md#sse-재연결과-중복-처리)에 따라 정한 이름을 2026-09-26 구현에 반영했다. DB 재전송 조회·중단 복구까지 완료한 것은 아니다.

| 변경 | 현재 책임 |
|---|---|
| `EventDispatchJobDispatcher` → [EventDispatchJobService](../../backend/src/main/java/com/surimap/eventhub/adapter/EventDispatchJobService.java) | 작업 선점·순번 확정·처리 결과를 DB 트랜잭션으로 저장한다. SSE 전송이나 FCM 성공 판정은 맡지 않는다. |
| `EventDispatchJobPollingWorker` → [EventDispatchJobWorker](../../backend/src/main/java/com/surimap/eventhub/adapter/EventDispatchJobWorker.java) | 커밋 후 깨우기·주기 조회를 받고 서비스 커밋 → SSE 전송 → 결과 저장을 실행한다. 기존 Dispatcher 테스트도 `EventDispatchJobWorkerTest`로 옮겼다. |

## API 문서 검사

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

## 시연 도구·CI 검사

2026-09-21 문서 정리에서 찾았다. 테스트 4개는 이름·경로를 확인한 후보이며 내부 검증을 모두 검토하거나 실행한 것은 아니다. 이름 변경은 각 대상의 동작·사용처를 정리할 때 진행한다.

| 추가 후보 | 확인할 점 |
|---|---|
| [AndroidUiScenarioCoverageTest](../../android/app/src/test/java/com/surimap/feature/AndroidUiScenarioCoverageTest.kt) | `ScenarioCoverage`가 실제 화면 흐름 검증을 뜻하는지 확인하고 대상·검증 범위로 명명 |
| [PoliComponentVariantContractTest](../../android/app/src/test/java/com/surimap/ui/components/PoliComponentVariantContractTest.kt) | `Contract` 분류 대신 컴포넌트 상태·표시 중 실제 검증 대상을 확인 |
| [NetworkStateFixturesTest](../../android/app/src/test/java/com/surimap/testing/NetworkStateFixturesTest.kt) | 입력 값 검사와 네트워크 상태 전이 검증을 구분 |
| [DemoScenarioFixturesTest](../../backend/src/test/java/com/surimap/demo/DemoScenarioFixturesTest.java) | 고정 입력 검사와 실제 시연 흐름 실행을 구분 |

| 후속 후보 | 확인할 점 |
|---|---|
| [infra/ci/verify-evidence.sh](../../infra/ci/verify-evidence.sh)·[사용 안내](../../infra/ci/README.md) | `RED`·`L2-D01` 표현과 포괄적인 `evidence` 이름이 남아 있다. 현재 Jenkinsfile의 직접 호출은 찾지 못했으며 CI 결과 파일 존재·문구 검사와 실제 품질 판정을 구분해 필요성·이름·안내를 함께 검토 |
| [collect-s4-evidence.sh](../../infra/ci/collect-s4-evidence.sh)·[verify-s4-evidence.sh](../../infra/ci/verify-s4-evidence.sh) | Jenkins에서 호출한다. 2026-09-28 DB 재전송·실시간 전송 테스트 참조를 갱신하고 고정 성공률을 제거했으며 건너뜀·0개 실행·누락 결과를 통과로 처리하지 않게 했다. `S4`·`evidence` 파일명은 CI 호출·산출물 참조와 함께 정리할 후보로 남긴다. |

이미 합의한 k6 이름은 유지한다.

## 반영 이력 요약

2026-09-09~15에 정리한 마커·SSE 이름과 테스트의 요약이다. 파일별 이전·이후 이름과 검증 내역은 [마커·알림 원문](https://github.com/sonic8-8/suri-map/blob/403b383dc2693fd15d1616ee7206e6cb0e420df5/docs/refactoring/codebase-naming-candidates.md#마커알림--먼저-진행), [SSE·인증 원문](https://github.com/sonic8-8/suri-map/blob/403b383dc2693fd15d1616ee7206e6cb0e420df5/docs/refactoring/codebase-naming-candidates.md#sse인증--승인한-이름-반영)에서 확인한다. 2026-09-22 문서 축약 이후의 코드 변경은 아래 날짜를 붙여 구분한다.

2026-09-28: `SseSequenceEnvelopeReplayTest`의 형식·순번 검사를 실제 DB를 사용하는 `SseReplayServiceTest`로 통합하고 옛 파일은 제거했다. 중복 재시도는 `SseStreamServiceTest`에서 DB 이력 하나·같은 순번 재전송으로 확인한다. 메모리 저장소 3개는 옛 테스트 입력으로만 남겨 `src/test`로 이동했다. 삭제·이동 전 코드는 Git 이력에서 복원할 수 있다.

| 정리한 대상 | 반영 결과 |
|---|---|
| SSE HTTP 응답 검증 (2026-09-27) | `EventStreamControllerRedTest`를 [EventStreamControllerTest](../../backend/src/test/java/com/surimap/eventhub/EventStreamControllerTest.java)로 변경했다. 한글 메서드 `DisplayName`·밑줄 메서드명·given/when/then 설명을 적용하고, 응답 준비 후 전송과 시작 실패·종료 처리를 MockMvc에서 검증한다. 종료 사건 재접속 검사는 전송 작업을 실행하도록 기존 Service 테스트에서 옮겼다. |
| 이벤트 전송 작업의 저장·조회 객체 (2026-09-26) | `EventDispatchJobRow`·`EventDispatchJobDispatchRecord`를 [EventDispatchJob](../../backend/src/main/java/com/surimap/eventhub/adapter/EventDispatchJob.java) class 하나로 통합했다. 같은 이벤트 내용과 전송 상태·SSE 순번을 저장·조회한다. 이후 같은 날 Service·worker 책임을 분리했으며 DB 재전송 조회 연결은 남아 있다. |
| 마커 생성 검증·사진 첨부 구분 (2026-09-22) | `validateCreateRequest`·`validateAppAuthentication`·`validateIdempotencyKey`로 검사 대상을 구분했다. `attachPhotosForMarkerCreation`은 초기 사진 첨부, `attachPhotoToExistingMarker`는 생성 후 마커 수정까지 담당한다. 파일 확인·사진 저장은 `attachUploadedPhoto`로 공유한다. [반영 동작과 검증 범위](../features/marker-photo.md#사진을-포함한-마커-생성과-생성-후-사진-추가). |
| 마커 생성·사진·알림의 `RedTest`·`Sc06`·`Sc08`·`ContractTest` | 필요한 검증을 `MarkerTest`·`MarkerWriteAccessValidatorTest`·`AppMarkerServiceTest`·Mapper 테스트에 모았다. SQL 문자열·가짜 표시 결과 검사는 실제 서비스·DB 검증과 구분해 제거했다. |
| 알림 전달·조회 | 전용 FCM 전달 로직은 `MarkerNotificationService`, 조회는 `MarkerNotificationMapper`로 모았다. 미사용 지원 요청·표시 코드는 제거하고, 알림 전용 타입에는 `MarkerNotification`을 드러냈다. 상황판 슬롯은 `marker_notification`을 사용한다. |
| 마커 조회·응답 | `MarkerQuery`·중복 결과 객체를 제거하고 `api/service/marker/MarkerService`로 통합했다. 응답은 `MarkersServiceResponse`의 내부 class로 모으고 응답 변환을 도메인에서 DTO로 옮겼다. |
| 오류·인증·좌표 | 공통 예외 처리로 통합하고 `MarkerAuthenticationResolver`·`GeoJsonPoint.roundToSixDecimals()`로 역할을 드러냈다. 공개 오류·좌표 형식·기존 요청 해시 비교는 유지했다. |
| 기준 마커·운영 패키지 | 초기 등록은 `ReferenceMarkerSeedService`에 모았다. 공용 FCM은 `client/fcm`, 설정은 `config/fcm`, 사진 파기 계약은 `api/service/photo`로 옮기고 운영 `com/surimap/marker` 디렉터리를 제거했다. |
| SSE·인증 이름 | 연결 관리는 `SseConnectionRegistry`, 동작은 `registerForIncident`·`sendToIncident` 등으로 정리했다. 인증 필터와 테스트 메서드도 동작을 나타내는 이름으로 바꿨다. |
| SSE 테스트 | 전달·연결·저장·재전송을 대상별로 모으고 밑줄 메서드명·한글 `DisplayName`·given/when/then 설명을 적용했다. 이후 `EventDispatchJobDispatcherTest`는 `EventDispatchJobWorkerTest`로 변경하고 DB 트랜잭션 검증을 `EventDispatchJobServiceTest`에 추가했다. |
| 옛 단말 복구 도구 | `check_l4_d01_runtime_preflight.py`·`run_l4_d01_deployed_stability.py`는 호출자가 없어 제거했다. 실행하지 않았으며 [복원 근거와 주의점](../test-results/android-network-recovery-2026-05-14/README.md#옛-기록도구의-복원)을 남겼다. |

당시 마커 서비스 검증은 실제 DB와 외부 FCM 대역을 사용했고, SSE 서비스 검증은 실제 Spring 빈과 메모리 재전송 저장소를 사용했다. 이 테스트로 DB 재전송 내구성·실제 네트워크 수신·업무폰 알림 표시를 증명한 것은 아니다. 이름·통합 변경 뒤 관련 검증 88개 통과는 당시 결과이며 이번 문서 정리의 실행 결과가 아니다.

후속 인증 보관 수정은 [로컬 이슈 3](../issues/local/3-authenticated-sse-access-denied-on-disconnect.md)에, 실제 브라우저의 알림·지도 검증과 남은 실패 복구 문제는 [로컬 이슈 2](../issues/local/2-marker-notification-interrupted-by-sse-connection-failure.md)에 기록했다. 사진·실제 Android/FCM·배포 빌드·부하 검증과 구분한다.
