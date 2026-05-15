package com.surimap.marker;

import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.INCIDENT_ID;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.OP1_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.maparea.testdouble.SearchAreaQueryMock;
import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.domain.exception.InvalidGeometryException;
import com.surimap.marker.domain.service.MarkerLocationValidatorImpl;
import com.surimap.marker.domain.service.MarkerOpBindingValidator;
import com.surimap.marker.dto.MarkerCreateRequest;
import com.surimap.marker.dto.MarkerCreateResult;
import com.surimap.marker.dto.MarkerCreatePhotoRequest;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.dto.MarkerPublishRequest;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.photo.adapter.MockObjectStorageAdapter;
import com.surimap.marker.photo.domain.MarkerPhoto;
import com.surimap.marker.photo.domain.PhotoStatus;
import com.surimap.marker.photo.dto.PublishRequest;
import com.surimap.marker.photo.service.MarkerCreatePhotoAttachmentService;
import com.surimap.marker.photo.support.InMemoryPhotoRepository;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import com.surimap.marker.repository.MarkerRecord;
import com.surimap.marker.seed.support.InMemoryMarkerRepository;
import com.surimap.marker.service.MarkerCreateService;
import com.surimap.marker.service.MarkerMutationContext;
import com.surimap.marker.service.MarkerRequestContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** S14P31C106-71 L5-T01A marker create service RED/GREEN tests. */
@DisplayName("L5-T01A marker create service")
class MarkerCreateServiceTest {

  private static final UUID MARKER_ID = UUID.fromString("55555555-5555-5555-5555-555555550071");
  private static final UUID PHOTO_ID = UUID.fromString("55555555-5555-5555-5555-555555550172");
  private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111110071");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("22222222-2222-2222-2222-222222220071");
  private static final UUID OTHER_OP_ID = UUID.fromString("88888888-8888-8888-8888-888888880072");
  private static final Instant CLIENT_TS = Instant.parse("2026-04-28T00:05:00Z");
  private static final Instant SERVER_TS = Instant.parse("2026-04-28T00:05:03Z");

  private final InMemoryMarkerRepository markerRepository = new InMemoryMarkerRepository();
  private final InMemoryPhotoRepository photoRepository = new InMemoryPhotoRepository();
  private final MockObjectStorageAdapter objectStorage = new MockObjectStorageAdapter();
  private final CapturingMarkerEventPublisher eventPublisher = new CapturingMarkerEventPublisher();
  private final CapturingPhotoEventPublisher photoEventPublisher = new CapturingPhotoEventPublisher();
  private final AllowingMarkerWriteGuard guard = new AllowingMarkerWriteGuard();
  private final MarkerCreateService markerCreateService =
      new MarkerCreateService(
          markerRepository,
          new MarkerLocationValidatorImpl(new SearchAreaQueryMock()),
          new MarkerOpBindingValidator(incidentId -> Optional.of(OP1_ID)),
          guard,
          eventPublisher,
          new MarkerCreatePhotoAttachmentService(
              photoRepository,
              objectStorage,
              markerRepository,
              photoEventPublisher,
              Clock.fixed(SERVER_TS, ZoneOffset.UTC)),
          Clock.fixed(SERVER_TS, ZoneOffset.UTC),
          () -> MARKER_ID);

  private MarkerRequestContext context;

  @BeforeEach
  void setUp() {
    context =
        new MarkerRequestContext(
            new SuriMapAuthentication(ACCOUNT_ID, "APP", POLICE_PHONE_ID),
            "idem-marker-create-001");
  }

  @Test
  @DisplayName("APP marker 생성은 ACTIVE version=1 row와 MARKER_CREATED publish request를 만든다")
  void appMarkerCreatePersistsActiveRowAndPublishesMarkerCreated() {
    MarkerCreateResult result = markerCreateService.create(defaultRequest(), context);

    assertThat(result.response().id()).isEqualTo(MARKER_ID);
    assertThat(result.response().incidentId()).isEqualTo(INCIDENT_ID);
    assertThat(result.response().opId()).isEqualTo(OP1_ID);
    assertThat(result.response().policePhoneId()).isEqualTo(POLICE_PHONE_ID);
    assertThat(result.response().status()).isEqualTo("ACTIVE");
    assertThat(result.response().version()).isEqualTo(1L);

    MarkerRecord row = markerRepository.records().get(0);
    assertThat(row.getId()).isEqualTo(MARKER_ID);
    assertThat(row.getOperationalPeriodId()).isEqualTo(OP1_ID);
    assertThat(row.getMarkerType()).isEqualTo(MarkerType.CLUE.name());
    assertThat(row.getMarkerSource()).isEqualTo(MarkerSource.APP.name());
    assertThat(row.getStatus()).isEqualTo(MarkerStatus.ACTIVE.name());
    assertThat(row.getVersion()).isEqualTo(1L);
    assertThat(row.getCreatedByAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(row.getPolicePhoneId()).isEqualTo(POLICE_PHONE_ID);
    assertThat(row.getOccurredAt()).isEqualTo(CLIENT_TS);
    assertThat(row.getMemo()).isEqualTo("S14P31C106-71 field clue");
    assertThat(row.getLocation().getSRID()).isEqualTo(4326);
    assertThat(row.getLocation().getX()).isEqualTo(126.956500);
    assertThat(row.getLocation().getY()).isEqualTo(37.571200);

    MarkerPublishRequest published = eventPublisher.published().get(0);
    assertThat(published.type()).isEqualTo("MARKER_CREATED");
    assertThat(published.payload().id()).isEqualTo(MARKER_ID);
    assertThat(published.payload().incidentId()).isEqualTo(INCIDENT_ID);
    assertThat(published.payload().opId()).isEqualTo(OP1_ID);
    assertThat(published.payload().policePhoneId()).isEqualTo(POLICE_PHONE_ID);
    assertThat(published.payload().status()).isEqualTo("ACTIVE");
    assertThat(published.payload().version()).isEqualTo(1L);
    assertThat(published.payload().type()).isEqualTo("CLUE");
    assertThat(published.payload().location().coordinates())
        .containsExactly(new BigDecimal("126.956500"), new BigDecimal("37.571200"));
    assertThat(published.payload().clientTs()).isEqualTo(CLIENT_TS);
    assertThat(published.payload().serverTs()).isEqualTo(SERVER_TS);
  }

  @Test
  @DisplayName("marker.location은 저장과 이벤트 전에 소수 6자리 canonical Point로 정규화된다")
  void markerLocationCanonicalizedBeforePersistAndPublish() {
    MarkerCreateRequest request =
        new MarkerCreateRequest(
            INCIDENT_ID,
            OP1_ID,
            "CLUE",
            new MarkerGeoJsonPoint(
                "Point", List.of(new BigDecimal("126.9565007"), new BigDecimal("37.5712007"))),
            null,
            "precision-over-6dp",
            CLIENT_TS,
            0L);

    markerCreateService.create(request, context);

    MarkerRecord row = markerRepository.records().get(0);
    assertThat(row.getLocation().getX()).isEqualTo(126.956501);
    assertThat(row.getLocation().getY()).isEqualTo(37.571201);
    assertThat(eventPublisher.published().get(0).payload().location().coordinates())
        .containsExactly(new BigDecimal("126.956501"), new BigDecimal("37.571201"));
  }

  @Test
  @DisplayName("생성 요청에 staged photo가 있으면 marker create와 photo attach를 같은 write에서 확정한다")
  void markerCreateAttachesStagedPhotosInSameWrite() {
    String objectKey = "markers/" + INCIDENT_ID + "/" + MARKER_ID + "/" + PHOTO_ID + ".jpg";
    objectStorage.generatePresignedUrl(
        objectKey, "image/jpeg", 1_048_576L, "sha256:fixture", Duration.ofMinutes(15));
    photoRepository.save(
        new MarkerPhoto(
            PHOTO_ID,
            MARKER_ID,
            objectKey,
            "image/jpeg",
            1_048_576L,
            "sha256:fixture",
            SERVER_TS.plus(Duration.ofMinutes(15))));
    objectStorage.simulateUpload(objectKey);

    MarkerCreateResult result =
        markerCreateService.create(
            new MarkerCreateRequest(
                MARKER_ID,
                INCIDENT_ID,
                OP1_ID,
                "CLUE",
                new MarkerGeoJsonPoint(
                    "Point", List.of(new BigDecimal("126.956500"), new BigDecimal("37.571200"))),
                null,
                "S14P31C106-340 photo evidence",
                CLIENT_TS,
                0L,
                List.of(
                    new MarkerCreatePhotoRequest(
                        PHOTO_ID, 1_048_576L, "image/jpeg", 640, 480, "sha256:fixture"))),
            context);

    assertThat(result.response().id()).isEqualTo(MARKER_ID);
    assertThat(result.response().status()).isEqualTo("UPDATED");
    assertThat(result.response().version()).isEqualTo(2L);
    assertThat(result.response().photos()).hasSize(1);
    assertThat(result.response().photos().get(0).photoId()).isEqualTo(PHOTO_ID);
    assertThat(result.response().photos().get(0).status()).isEqualTo("ATTACHED");
    assertThat(photoRepository.findById(PHOTO_ID))
        .get()
        .extracting("status", "version", "width", "height")
        .containsExactly(PhotoStatus.ATTACHED, 2L, 640, 480);
    assertThat(markerRepository.records().get(0).getStatus()).isEqualTo("UPDATED");
    assertThat(markerRepository.records().get(0).getVersion()).isEqualTo(2L);
    assertThat(eventPublisher.published()).hasSize(1);
    assertThat(photoEventPublisher.published()).hasSize(1);
    assertThat(photoEventPublisher.published().get(0).payload().photoDelta().photoId())
        .isEqualTo(PHOTO_ID);
  }

  @Nested
  @DisplayName("guards")
  class Guards {

    @Test
    @DisplayName("WEB context는 marker row와 publish request 없이 channel_not_allowed다")
    void webContextRejectedBeforeWrite() {
      MarkerRequestContext webContext =
          new MarkerRequestContext(
              new SuriMapAuthentication(ACCOUNT_ID, "WEB", POLICE_PHONE_ID),
              "idem-marker-create-001");

      assertThatThrownBy(() -> markerCreateService.create(defaultRequest(), webContext))
          .isInstanceOf(MarkerApiException.class)
          .extracting("error")
          .isEqualTo("channel_not_allowed");

      assertThat(markerRepository.records()).isEmpty();
      assertThat(eventPublisher.published()).isEmpty();
    }

    @Test
    @DisplayName("current OP와 request.opId가 다르면 op_mismatch이고 row/event를 만들지 않는다")
    void opMismatchRejectedBeforeWrite() {
      MarkerCreateService service =
          new MarkerCreateService(
              markerRepository,
              new MarkerLocationValidatorImpl(new SearchAreaQueryMock()),
              new MarkerOpBindingValidator(incidentId -> Optional.of(OTHER_OP_ID)),
              guard,
              eventPublisher,
              Clock.fixed(SERVER_TS, ZoneOffset.UTC),
              () -> MARKER_ID);

      assertThatThrownBy(() -> service.create(defaultRequest(), context))
          .isInstanceOf(MarkerApiException.class)
          .extracting("error")
          .isEqualTo("op_mismatch");

      assertThat(markerRepository.records()).isEmpty();
      assertThat(eventPublisher.published()).isEmpty();
    }

    @Test
    @DisplayName("overall_search_area 밖 Point는 invalid_geometry이고 row/event를 만들지 않는다")
    void invalidGeometryRejectedBeforeWrite() {
      MarkerCreateRequest request =
          new MarkerCreateRequest(
              INCIDENT_ID,
              OP1_ID,
              "CLUE",
              new MarkerGeoJsonPoint(
                  "Point", List.of(new BigDecimal("127.200000"), new BigDecimal("37.571200"))),
              null,
              "outside envelope",
              CLIENT_TS,
              0L);

      assertThatThrownBy(() -> markerCreateService.create(request, context))
          .isInstanceOf(InvalidGeometryException.class)
          .extracting("errorCode")
          .isEqualTo("invalid_geometry");

      assertThat(markerRepository.records()).isEmpty();
      assertThat(eventPublisher.published()).isEmpty();
    }
  }

  private static MarkerCreateRequest defaultRequest() {
    return new MarkerCreateRequest(
        INCIDENT_ID,
        OP1_ID,
        "CLUE",
        new MarkerGeoJsonPoint(
            "Point", List.of(new BigDecimal("126.956500"), new BigDecimal("37.571200"))),
        null,
        "S14P31C106-71 field clue",
        CLIENT_TS,
        0L);
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

  private static final class CapturingPhotoEventPublisher
      implements com.surimap.marker.photo.port.PhotoEventPublisher {

    private final List<PublishRequest> published = new ArrayList<>();

    @Override
    public void publish(PublishRequest request) {
      published.add(request);
    }

    List<PublishRequest> published() {
      return published;
    }
  }

  private static final class AllowingMarkerWriteGuard
      implements com.surimap.marker.port.MarkerWriteGuardPort {

    @Override
    public void requireCreateAccess(UUID incidentId, UUID opId, MarkerRequestContext context) {
      if (!"APP".equals(context.authentication().channel())) {
        throw new MarkerApiException(
            "channel_not_allowed", org.springframework.http.HttpStatus.FORBIDDEN);
      }
    }

    @Override
    public MarkerMutationContext requireUpdateAccess(UUID markerId, MarkerRequestContext context) {
      throw new UnsupportedOperationException("create tests do not exercise marker update access");
    }

    @Override
    public MarkerMutationContext requireDeleteAccess(UUID markerId, MarkerRequestContext context) {
      throw new UnsupportedOperationException("create tests do not exercise marker delete access");
    }
  }
}
