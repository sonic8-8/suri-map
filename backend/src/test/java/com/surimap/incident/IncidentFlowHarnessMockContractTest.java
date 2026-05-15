package com.surimap.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.incident.fixture.IncidentSeedFixtureIds;
import com.surimap.incident.fixture.IncidentSeedFixtureLoader;
import com.surimap.incident.testdouble.IncidentPublishRequest;
import com.surimap.incident.testdouble.MockIncidentAuthAdapter;
import com.surimap.incident.testdouble.MockIncidentEventHub;
import com.surimap.incident.testdouble.MockIncidentPurgeHook;
import com.surimap.incident.testdouble.MockReferenceMarkerSeedAdapter;
import com.surimap.operationalperiod.fixture.OperationalPeriodFixtures;
import com.surimap.operationalperiod.testdouble.InitialOperationalPeriodCreatorMock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L1-T07 SC-01/02/12 사건 흐름 하네스 mock 계약 테스트.
 *
 * <p>본 계약은 Phase 0 골격이다. mock 간 상호작용 자체로 fixture·adapter 계약을 고정하고, 실제 실패 검증은 Phase 1의 L1-T01 사건
 * 가져오기 API 테스트가 본 계약을 consume하면서 수행한다.
 */
@DisplayName("L1-T07 사건 흐름 mock adapter와 시드 fixture 로더 계약")
class IncidentFlowHarnessMockContractTest {

  @Test
  @DisplayName("시드 로더가 common-fixtures.json에서 SC-01/02/12 canonical 사건 ID를 읽어온다")
  void seed_loader_loads_sc010212_canonical_incident_ids_from_common_fixtures() {
    IncidentSeedFixtureIds seed = IncidentSeedFixtureLoader.loadPrecinctFirst();

    assertThat(seed.sourceIncidentId()).isEqualTo("00000000-0000-0000-0000-000000000001");
    assertThat(seed.incidentAlias()).isEqualTo("inc-precinct-first-001");
    assertThat(seed.incidentId()).isEqualTo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
    assertThat(seed.opAlias()).isEqualTo("op-precinct-001-op1");
    assertThat(seed.opId()).isEqualTo("88888888-8888-8888-8888-888888880001");
    assertThat(seed.markerIds()).containsExactly("55555555-5555-5555-5555-555555550001");
    assertThat(seed.pathVehicleId()).isEqualTo("ffffffff-ffff-ffff-ffff-ffffffff0001");
    assertThat(seed.pathFootId()).isEqualTo("ffffffff-ffff-ffff-ffff-ffffffff0002");
    assertThat(seed.memoId()).isEqualTo("eeeeeeee-eeee-eeee-eeee-eeeeeeee0001");
    assertThat(seed.beforeHandoverAssignmentIds())
        .containsExactly("ia-precinct-cmd-001", "ia-precinct-car-001", "ia-precinct-team-001");
    assertThat(seed.afterHandoverAssignmentIds())
        .containsExactly("ia-precinct-alpha-cmd-001", "ia-precinct-alpha-team-001");
    assertThat(seed.afterSupportAssignmentIds())
        .containsExactly(
            "ia-precinct-support-cmd-001",
            "ia-precinct-support-car-001",
            "ia-precinct-support-team-001");
    assertThat(seed.seedMarkers())
        .singleElement()
        .satisfies(
            marker -> {
              assertThat(marker.type()).isEqualTo("CLUE");
              assertThat(marker.source()).isEqualTo("MOCK_SEED");
              assertThat(marker.memo()).isEqualTo("신고자 진술 위치");
              assertThat(marker.lon()).isEqualTo(126.9134);
              assertThat(marker.lat()).isEqualTo(35.1631);
            });
  }

  @Test
  @DisplayName("SC-01 사건 가져오기 흐름이 권한·이벤트·마커·OP1 mock으로 실행된다")
  void sc01_import_harness_can_run_with_auth_event_marker_and_op1_mocks() {
    IncidentSeedFixtureIds seed = IncidentSeedFixtureLoader.loadPrecinctFirst();
    MockIncidentAuthAdapter auth = new MockIncidentAuthAdapter(seed);
    MockIncidentEventHub eventHub = new MockIncidentEventHub();
    MockReferenceMarkerSeedAdapter markerSeed = new MockReferenceMarkerSeedAdapter(seed);
    InitialOperationalPeriodCreatorMock opCreator = new InitialOperationalPeriodCreatorMock();

    auth.requireImportAllowed(auth.webPrecinctCommander());
    assertThatThrownBy(() -> auth.requireImportAllowed(auth.webSupportCommander()))
        .isInstanceOf(SecurityException.class)
        .hasMessage("role_denied");
    assertThat(OperationalPeriodFixtures.INCIDENT_ALIAS).isEqualTo(seed.incidentAlias());
    assertThat(OperationalPeriodFixtures.OP1_ALIAS).isEqualTo(seed.opAlias());
    var op1 = opCreator.createOp1(OperationalPeriodFixtures.INCIDENT_ID).operationalPeriod();
    markerSeed.createForIncident(seed.incidentId(), seed.seedMarkers());
    IncidentPublishRequest created =
        IncidentPublishRequest.incidentCreated(seed.incidentId(), "OPEN", 1L);
    eventHub.publish(created);

    assertThat(op1.opId()).isEqualTo(OperationalPeriodFixtures.OP1_ID);
    assertThat(markerSeed.createdMarkers())
        .singleElement()
        .satisfies(
            capture -> {
              assertThat(capture.incidentId()).isEqualTo(seed.incidentId());
              assertThat(capture.seedMarkers()).hasSize(1);
            });
    assertThat(eventHub.publishedRequests())
        .containsExactly(created)
        .allSatisfy(request -> assertThat(request.eventId()).isNotBlank());
  }

  @Test
  @DisplayName("SC-02 인계·지원 계정이 권한 fixture를 통해 사건 배정 명단에 추가된다")
  void sc02_handover_and_support_accounts_become_incident_assigned_via_auth_fixture() {
    IncidentSeedFixtureIds seed = IncidentSeedFixtureLoader.loadPrecinctFirst();
    MockIncidentAuthAdapter auth = new MockIncidentAuthAdapter(seed);
    MockIncidentEventHub eventHub = new MockIncidentEventHub();

    auth.assignHandoverAccounts();
    auth.assignSupportAccounts();
    IncidentPublishRequest assignmentChanged =
        IncidentPublishRequest.assignmentChanged(seed.incidentId(), "ACTIVE", 2L);
    eventHub.publish(assignmentChanged);

    assertThat(auth.assignedAccountIds())
        .containsAll(seed.accountCodes())
        .contains("acct-cmd-alpha", "acct-team-alpha", "acct-support-cmd");
    assertThat(eventHub.publishedRequests())
        .containsExactly(assignmentChanged)
        .allSatisfy(request -> assertThat(request.eventId()).isNotBlank());
  }

  @Test
  @DisplayName(
      "SC-12 종료 흐름이 INCIDENT_CLOSED를 S1-3 purge handoff로 인계하되 purge orchestration은 직접 실행하지 않는다")
  void sc12_close_harness_captures_incident_closed_for_purge_without_direct_purge_run() {
    IncidentSeedFixtureIds seed = IncidentSeedFixtureLoader.loadPrecinctFirst();
    MockIncidentEventHub eventHub = new MockIncidentEventHub();
    MockIncidentPurgeHook purgeHook = new MockIncidentPurgeHook();

    IncidentPublishRequest closed =
        IncidentPublishRequest.incidentClosed(seed.incidentId(), "CLOSED", 3L);

    eventHub.publish(closed);
    purgeHook.captureIncidentClosed(closed);

    assertThat(eventHub.publishedRequests())
        .containsExactly(closed)
        .allSatisfy(request -> assertThat(request.eventId()).isNotBlank());
    assertThat(purgeHook.closedEvents()).containsExactly(closed);
    assertThat(purgeHook.directPurgeRuns()).isZero();
    assertThatThrownBy(() -> purgeHook.runDirectPurge(seed.incidentId()))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("S1-1은 S1-3 purge orchestration을 직접 실행하면 안 됩니다");
  }
}
