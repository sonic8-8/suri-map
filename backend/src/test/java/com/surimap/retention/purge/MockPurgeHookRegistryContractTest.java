package com.surimap.retention.purge;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.retention.purge.fixture.PurgeLifecycleFixtures;
import com.surimap.retention.purge.testdouble.MockPurgeHookRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** L2-B03B S1-3 파기 훅 등록기와 생명주기 고정 데이터 실패 우선 계약 테스트. */
@DisplayName("L2-B03B 모의 파기 훅 등록기 계약")
class MockPurgeHookRegistryContractTest {

  private static final UUID INCIDENT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID PURGE_RUN_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
  private static final Instant CLOSED_AT = Instant.parse("2026-05-07T00:10:00Z");
  private static final Instant PURGE_DEADLINE_TS = Instant.parse("2026-05-08T00:10:00Z");

  @Test
  @DisplayName("기본 등록기는 S1-3 소비 훅 네 개를 고정 순서로 제공한다")
  void 기본_등록기는_s1_3_소비_훅_네_개를_고정_순서로_제공한다() {
    MockPurgeHookRegistry registry = PurgeLifecycleFixtures.defaultRegistry();

    assertThat(registry.hooks())
        .extracting(PurgeHook::name)
        .containsExactly(
            PurgeHookName.LOCAL_SYNC,
            PurgeHookName.PATH,
            PurgeHookName.MARKER_PHOTO,
            PurgeHookName.OFFLINE_PACKAGE);
  }

  @Test
  @DisplayName("각 훅은 파기 요청 필드를 바꾸지 않고 관찰한다")
  void 각_훅은_파기_요청_필드를_바꾸지_않고_관찰한다() {
    MockPurgeHookRegistry registry = PurgeLifecycleFixtures.defaultRegistry();
    PurgeHookRequest request = purgeRequest();

    registry.hooks().forEach(hook -> hook.purge(request));

    assertThat(registry.observations())
        .extracting(MockPurgeHookRegistry.Observation::hookName)
        .containsExactly(
            PurgeHookName.LOCAL_SYNC,
            PurgeHookName.PATH,
            PurgeHookName.MARKER_PHOTO,
            PurgeHookName.OFFLINE_PACKAGE);
    assertThat(registry.observations())
        .extracting(MockPurgeHookRegistry.Observation::request)
        .allSatisfy(
            observed -> {
              assertThat(observed.incidentId()).isEqualTo(INCIDENT_ID);
              assertThat(observed.purgeRunId()).isEqualTo(PURGE_RUN_ID);
              assertThat(observed.closedAt()).isEqualTo(CLOSED_AT);
              assertThat(observed.purgeDeadlineTs()).isEqualTo(PURGE_DEADLINE_TS);
            });
  }

  @Test
  @DisplayName("성공 동기화대기 재시도가능실패 고정 데이터는 결과 필드를 보존한다")
  void 주입한_고정_데이터_결과는_상태_건수_오류_코드를_보존한다() {
    MockPurgeHookRegistry registry =
        PurgeLifecycleFixtures.defaultRegistry()
            .withResult(PurgeHookName.PATH, successResult())
            .withResult(PurgeHookName.MARKER_PHOTO, waitingForSyncResult())
            .withResult(PurgeHookName.LOCAL_SYNC, retryableFailureResult());
    PurgeHookRequest request = purgeRequest();

    assertThat(hook(registry, PurgeHookName.PATH).purge(request))
        .satisfies(
            result -> {
              assertThat(result.status()).isEqualTo(PurgeHookStatus.SUCCEEDED);
              assertThat(result.purgedCount()).isEqualTo(12);
              assertThat(result.retainedCount()).isZero();
              assertThat(result.errorCode()).isNull();
            });
    assertThat(hook(registry, PurgeHookName.MARKER_PHOTO).purge(request))
        .satisfies(
            result -> {
              assertThat(result.status()).isEqualTo(PurgeHookStatus.WAITING_FOR_SYNC);
              assertThat(result.purgedCount()).isEqualTo(4);
              assertThat(result.retainedCount()).isEqualTo(2);
              assertThat(result.errorCode()).isEqualTo("sync_pending");
            });
    assertThat(hook(registry, PurgeHookName.LOCAL_SYNC).purge(request))
        .satisfies(
            result -> {
              assertThat(result.status()).isEqualTo(PurgeHookStatus.FAILED_RETRYABLE);
              assertThat(result.purgedCount()).isEqualTo(1);
              assertThat(result.retainedCount()).isEqualTo(3);
              assertThat(result.errorCode()).isEqualTo("outbox_flush_incomplete");
            });
  }

  @Test
  @DisplayName("같은 요청을 반복 호출하면 같은 모의 결과와 관찰값을 재현한다")
  void 같은_요청_반복_호출은_같은_모의_결과와_관찰값을_재현한다() {
    MockPurgeHookRegistry registry =
        PurgeLifecycleFixtures.defaultRegistry().withResult(PurgeHookName.PATH, successResult());
    PurgeHookRequest request = purgeRequest();
    PurgeHook pathHook = hook(registry, PurgeHookName.PATH);

    PurgeHookResult first = pathHook.purge(request);
    PurgeHookResult second = pathHook.purge(request);

    assertThat(second).isEqualTo(first);
    assertThat(registry.observationsFor(PurgeHookName.PATH))
        .containsExactly(
            new MockPurgeHookRegistry.Observation(PurgeHookName.PATH, request),
            new MockPurgeHookRegistry.Observation(PurgeHookName.PATH, request));
  }

  private static PurgeHook hook(MockPurgeHookRegistry registry, PurgeHookName hookName) {
    return registry.hooks().stream()
        .filter(hook -> hook.name() == hookName)
        .findFirst()
        .orElseThrow(() -> new AssertionError("파기 훅이 없습니다: " + hookName));
  }

  private static PurgeHookRequest purgeRequest() {
    return new PurgeHookRequest(INCIDENT_ID, PURGE_RUN_ID, CLOSED_AT, PURGE_DEADLINE_TS);
  }

  private static PurgeHookResult successResult() {
    return new PurgeHookResult(PurgeHookStatus.SUCCEEDED, 12, 0, null);
  }

  private static PurgeHookResult waitingForSyncResult() {
    return new PurgeHookResult(PurgeHookStatus.WAITING_FOR_SYNC, 4, 2, "sync_pending");
  }

  private static PurgeHookResult retryableFailureResult() {
    return new PurgeHookResult(PurgeHookStatus.FAILED_RETRYABLE, 1, 3, "outbox_flush_incomplete");
  }
}
