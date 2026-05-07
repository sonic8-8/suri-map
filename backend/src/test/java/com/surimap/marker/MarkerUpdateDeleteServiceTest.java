package com.surimap.marker;

import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.INCIDENT_ID;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.OP1_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.maparea.testdouble.SearchAreaQueryMock;
import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.domain.service.MarkerLocationValidatorImpl;
import com.surimap.marker.dto.MarkerDeleteRequest;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.dto.MarkerMutationResult;
import com.surimap.marker.dto.MarkerPublishRequest;
import com.surimap.marker.dto.MarkerUpdateRequest;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import com.surimap.marker.repository.MarkerCreateRecord;
import com.surimap.marker.repository.MarkerRecord;
import com.surimap.marker.seed.support.InMemoryMarkerRepository;
import com.surimap.marker.service.MarkerMutationContext;
import com.surimap.marker.service.MarkerRequestContext;
import com.surimap.marker.service.MarkerUpdateDeleteService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** S14P31C106-71 L5-T02 marker update/delete service RED/GREEN tests. */
@DisplayName("L5-T02 marker update/delete service")
class MarkerUpdateDeleteServiceTest {

  private static final UUID MARKER_ID = UUID.fromString("55555555-5555-5555-5555-555555550072");
  private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111110072");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("22222222-2222-2222-2222-222222220072");
  private static final Instant CLIENT_TS = Instant.parse("2026-04-28T00:05:00Z");
  private static final Instant SERVER_TS = Instant.parse("2026-04-28T00:06:03Z");

  private final InMemoryMarkerRepository markerRepository = new InMemoryMarkerRepository();
  private final CapturingMarkerEventPublisher eventPublisher = new CapturingMarkerEventPublisher();
  private final AllowingMarkerWriteGuard guard = new AllowingMarkerWriteGuard();
  private final MarkerUpdateDeleteService service =
      new MarkerUpdateDeleteService(
          markerRepository,
          new MarkerLocationValidatorImpl(new SearchAreaQueryMock()),
          guard,
          eventPublisher,
          Clock.fixed(SERVER_TS, ZoneOffset.UTC));

  private MarkerRequestContext appContext;

  @BeforeEach
  void setUp() {
    appContext =
        new MarkerRequestContext(
            new SuriMapAuthentication(ACCOUNT_ID, "APP", POLICE_PHONE_ID),
            "idem-marker-update-delete-001");
    markerRepository.insertCreate(
        new MarkerCreateRecord(
            INCIDENT_ID,
            MARKER_ID,
            OP1_ID,
            null,
            MarkerType.CLUE,
            null,
            new MarkerGeoJsonPoint(
                    "Point", List.of(new BigDecimal("126.956500"), new BigDecimal("37.571200")))
                .toPoint(),
            "initial clue",
            CLIENT_TS,
            ACCOUNT_ID,
            POLICE_PHONE_ID,
            MarkerSource.APP,
            MarkerStatus.ACTIVE,
            1L));
  }

  @Test
  @DisplayName("PATCH는 UPDATED version+1 row와 MARKER_UPDATED publish request를 만든다")
  void updatePersistsVersionedRowAndPublishesMarkerUpdated() {
    MarkerUpdateRequest request =
        new MarkerUpdateRequest(
            1L,
            new MarkerGeoJsonPoint(
                "Point", List.of(new BigDecimal("126.9567007"), new BigDecimal("37.5714007"))),
            "updated clue memo",
            "NOTE");

    MarkerMutationResult result = service.update(MARKER_ID, request, appContext);

    assertThat(result.response().id()).isEqualTo(MARKER_ID);
    assertThat(result.response().status()).isEqualTo("UPDATED");
    assertThat(result.response().version()).isEqualTo(2L);

    MarkerRecord row = markerRepository.records().get(0);
    assertThat(row.getStatus()).isEqualTo(MarkerStatus.UPDATED.name());
    assertThat(row.getVersion()).isEqualTo(2L);
    assertThat(row.getMarkerType()).isEqualTo(MarkerType.NOTE.name());
    assertThat(row.getMemo()).isEqualTo("updated clue memo");
    assertThat(row.getLocation().getSRID()).isEqualTo(4326);
    assertThat(row.getLocation().getX()).isEqualTo(126.956701);
    assertThat(row.getLocation().getY()).isEqualTo(37.571401);

    MarkerPublishRequest published = eventPublisher.published().get(0);
    assertThat(published.type()).isEqualTo("MARKER_UPDATED");
    assertThat(published.payload().id()).isEqualTo(MARKER_ID);
    assertThat(published.payload().incidentId()).isEqualTo(INCIDENT_ID);
    assertThat(published.payload().opId()).isEqualTo(OP1_ID);
    assertThat(published.payload().policePhoneId()).isEqualTo(POLICE_PHONE_ID);
    assertThat(published.payload().status()).isEqualTo("UPDATED");
    assertThat(published.payload().version()).isEqualTo(2L);
    assertThat(published.payload().type()).isEqualTo("NOTE");
    assertThat(published.payload().location().coordinates())
        .containsExactly(new BigDecimal("126.956701"), new BigDecimal("37.571401"));
    assertThat(published.payload().serverTs()).isEqualTo(SERVER_TS);
  }

  @Test
  @DisplayName("DELETE는 DELETED version+1 row와 MARKER_DELETED publish request를 만든다")
  void deletePersistsVersionedTombstoneAndPublishesMarkerDeleted() {
    MarkerMutationResult result =
        service.delete(MARKER_ID, new MarkerDeleteRequest(1L, "wrong marker"), appContext);

    assertThat(result.response().id()).isEqualTo(MARKER_ID);
    assertThat(result.response().status()).isEqualTo("DELETED");
    assertThat(result.response().version()).isEqualTo(2L);

    MarkerRecord row = markerRepository.records().get(0);
    assertThat(row.getStatus()).isEqualTo(MarkerStatus.DELETED.name());
    assertThat(row.getVersion()).isEqualTo(2L);

    MarkerPublishRequest published = eventPublisher.published().get(0);
    assertThat(published.type()).isEqualTo("MARKER_DELETED");
    assertThat(published.payload().id()).isEqualTo(MARKER_ID);
    assertThat(published.payload().incidentId()).isEqualTo(INCIDENT_ID);
    assertThat(published.payload().opId()).isEqualTo(OP1_ID);
    assertThat(published.payload().policePhoneId()).isEqualTo(POLICE_PHONE_ID);
    assertThat(published.payload().status()).isEqualTo("DELETED");
    assertThat(published.payload().version()).isEqualTo(2L);
  }

  @Nested
  @DisplayName("guards")
  class Guards {

    @Test
    @DisplayName("version이 맞지 않으면 write_conflict이고 row/event를 변경하지 않는다")
    void versionConflictRejectedBeforeWrite() {
      assertThatThrownBy(
              () ->
                  service.update(
                      MARKER_ID,
                      new MarkerUpdateRequest(99L, null, "stale memo", null),
                      appContext))
          .isInstanceOf(MarkerApiException.class)
          .extracting("error")
          .isEqualTo("write_conflict");

      MarkerRecord row = markerRepository.records().get(0);
      assertThat(row.getStatus()).isEqualTo(MarkerStatus.ACTIVE.name());
      assertThat(row.getVersion()).isEqualTo(1L);
      assertThat(row.getMemo()).isEqualTo("initial clue");
      assertThat(eventPublisher.published()).isEmpty();
    }

    @Test
    @DisplayName("S5 marker policy가 role_denied를 반환하면 row/event를 변경하지 않는다")
    void policyDenialRejectedBeforeWrite() {
      guard.error = new MarkerApiException("role_denied", HttpStatus.FORBIDDEN);

      assertThatThrownBy(
              () ->
                  service.delete(
                      MARKER_ID, new MarkerDeleteRequest(1L, "not authorized"), appContext))
          .isInstanceOf(MarkerApiException.class)
          .extracting("error")
          .isEqualTo("role_denied");

      MarkerRecord row = markerRepository.records().get(0);
      assertThat(row.getStatus()).isEqualTo(MarkerStatus.ACTIVE.name());
      assertThat(row.getVersion()).isEqualTo(1L);
      assertThat(eventPublisher.published()).isEmpty();
    }
  }

  private static final class CapturingMarkerEventPublisher
      implements com.surimap.marker.port.MarkerEventPublisher {

    private final List<MarkerPublishRequest> published = new ArrayList<>();

    @Override
    public void publish(MarkerPublishRequest request) {
      published.add(request);
    }

    List<MarkerPublishRequest> published() {
      return published;
    }
  }

  private static final class AllowingMarkerWriteGuard
      implements com.surimap.marker.port.MarkerWriteGuardPort {

    private MarkerApiException error;

    @Override
    public void requireCreateAccess(UUID incidentId, UUID opId, MarkerRequestContext context) {}

    @Override
    public MarkerMutationContext requireUpdateAccess(UUID markerId, MarkerRequestContext context) {
      if (error != null) {
        throw error;
      }
      return new MarkerMutationContext(INCIDENT_ID, markerId, OP1_ID, POLICE_PHONE_ID);
    }

    @Override
    public MarkerMutationContext requireDeleteAccess(UUID markerId, MarkerRequestContext context) {
      if (error != null) {
        throw error;
      }
      return new MarkerMutationContext(INCIDENT_ID, markerId, OP1_ID, POLICE_PHONE_ID);
    }
  }
}
