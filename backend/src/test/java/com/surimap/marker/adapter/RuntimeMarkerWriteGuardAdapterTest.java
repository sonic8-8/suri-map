package com.surimap.marker.adapter;

import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.INCIDENT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import com.surimap.marker.service.MarkerMutationContext;
import com.surimap.marker.service.MarkerRequestContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("S5 runtime marker write guard")
class RuntimeMarkerWriteGuardAdapterTest {

  private static final UUID MARKER_ID = UUID.fromString("55555555-5555-5555-5555-555555550476");
  private static final UUID OP_ID = UUID.fromString("88888888-8888-8888-8888-888888880476");
  private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111110476");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("22222222-2222-2222-2222-222222220476");

  private final FakeMarkerRuntimeGuardMapper mapper = new FakeMarkerRuntimeGuardMapper();
  private final RuntimeMarkerWriteGuardAdapter guard = new RuntimeMarkerWriteGuardAdapter(mapper);
  private MarkerRequestContext webContext;
  private MarkerRequestContext appContext;

  @BeforeEach
  void setUp() {
    webContext =
        new MarkerRequestContext(
            new SuriMapAuthentication(ACCOUNT_ID, "WEB", null), "idem-web-marker-patch");
    appContext =
        new MarkerRequestContext(
            new SuriMapAuthentication(ACCOUNT_ID, "APP", POLICE_PHONE_ID), "idem-app-marker-patch");
  }

  @Test
  @DisplayName("WEB PATCH는 MOCK_SEED 초기 기준점 마커 보정을 허용한다")
  void webUpdateAllowsMockSeedReferenceMarkerCorrection() {
    mapper.markerSource = MarkerSource.MOCK_SEED;
    mapper.markerPolicePhoneId = null;

    MarkerMutationContext mutationContext = guard.requireUpdateAccess(MARKER_ID, webContext);

    assertThat(mutationContext.incidentId()).isEqualTo(INCIDENT_ID);
    assertThat(mutationContext.markerId()).isEqualTo(MARKER_ID);
    assertThat(mutationContext.opId()).isEqualTo(OP_ID);
    assertThat(mutationContext.policePhoneId()).isNull();
  }

  @Test
  @DisplayName("WEB PATCH는 APP 현장 생성 마커 보정을 거부한다")
  void webUpdateRejectsAppFieldMarker() {
    mapper.markerSource = MarkerSource.APP;

    assertThatThrownBy(() -> guard.requireUpdateAccess(MARKER_ID, webContext))
        .isInstanceOfSatisfying(
            MarkerApiException.class,
            exception -> assertThat(exception.getError()).isEqualTo("incident_access_denied"));
  }

  @Test
  @DisplayName("APP PATCH는 작성 단말 현장 생성 마커 수정을 허용한다")
  void appUpdateAllowsOwnFieldMarker() {
    mapper.markerSource = MarkerSource.APP;
    mapper.markerAccountId = ACCOUNT_ID;
    mapper.markerPolicePhoneId = POLICE_PHONE_ID;

    MarkerMutationContext mutationContext = guard.requireUpdateAccess(MARKER_ID, appContext);

    assertThat(mutationContext.policePhoneId()).isEqualTo(POLICE_PHONE_ID);
  }

  @Test
  @DisplayName("APP PATCH는 다른 단말 현장 생성 마커 수정을 거부한다")
  void appUpdateRejectsOtherPolicePhoneMarker() {
    mapper.markerSource = MarkerSource.APP;
    mapper.markerAccountId = ACCOUNT_ID;
    mapper.markerPolicePhoneId = UUID.fromString("22222222-2222-2222-2222-222222229999");

    assertThatThrownBy(() -> guard.requireUpdateAccess(MARKER_ID, appContext))
        .isInstanceOfSatisfying(
            MarkerApiException.class,
            exception -> assertThat(exception.getError()).isEqualTo("incident_access_denied"));
  }

  private static final class FakeMarkerRuntimeGuardMapper implements MarkerRuntimeGuardMapper {

    private MarkerSource markerSource = MarkerSource.MOCK_SEED;
    private UUID markerAccountId = ACCOUNT_ID;
    private UUID markerPolicePhoneId = POLICE_PHONE_ID;

    @Override
    public Optional<String> findIncidentStatus(UUID incidentId) {
      return INCIDENT_ID.equals(incidentId) ? Optional.of("OPEN") : Optional.empty();
    }

    @Override
    public Optional<UUID> findCurrentOpId(UUID incidentId) {
      return INCIDENT_ID.equals(incidentId) ? Optional.of(OP_ID) : Optional.empty();
    }

    @Override
    public Optional<MarkerRuntimeGuardMapper.MarkerGuardRow> findMarkerGuardRow(UUID markerId) {
      if (!MARKER_ID.equals(markerId)) {
        return Optional.empty();
      }
      return Optional.of(
          new MarkerRuntimeGuardMapper.MarkerGuardRow(
              MARKER_ID, INCIDENT_ID, OP_ID, markerAccountId, markerPolicePhoneId, markerSource));
    }

    @Override
    public int countActiveAssignmentsByAccountId(UUID accountId) {
      return ACCOUNT_ID.equals(accountId) ? 1 : 0;
    }

    @Override
    public int countActiveIncidentAssignment(UUID incidentId, UUID accountId) {
      return INCIDENT_ID.equals(incidentId) && ACCOUNT_ID.equals(accountId) ? 1 : 0;
    }

    @Override
    public int countRegisteredPolicePhone(UUID policePhoneId) {
      return POLICE_PHONE_ID.equals(policePhoneId) ? 1 : 0;
    }
  }
}
