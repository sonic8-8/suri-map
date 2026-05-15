package com.surimap.marker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.port.MarkerLocationValidator;
import com.surimap.marker.domain.service.MarkerOpBindingValidator;
import com.surimap.marker.dto.MarkerCreateRequest;
import com.surimap.marker.dto.MarkerCreateResponse;
import com.surimap.marker.dto.MarkerDeleteRequest;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.dto.MarkerMutationResponse;
import com.surimap.marker.dto.MarkerPublishRequest;
import com.surimap.marker.dto.MarkerUpdateRequest;
import com.surimap.marker.photo.adapter.MockObjectStorageAdapter;
import com.surimap.marker.photo.domain.PhotoMarkerContext;
import com.surimap.marker.photo.dto.PhotoAttachRequest;
import com.surimap.marker.photo.dto.PhotoAttachResponse;
import com.surimap.marker.photo.dto.PhotoUploadUrlRequest;
import com.surimap.marker.photo.dto.PhotoUploadUrlResponse;
import com.surimap.marker.photo.port.PhotoEventPublisher;
import com.surimap.marker.photo.port.PhotoWriteGuardPort;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import com.surimap.marker.photo.service.PhotoRequestContext;
import com.surimap.marker.photo.service.PhotoService;
import com.surimap.marker.photo.support.InMemoryPhotoRepository;
import com.surimap.marker.port.MarkerEventPublisher;
import com.surimap.marker.port.MarkerWriteGuardPort;
import com.surimap.marker.repository.MarkerCreateRecord;
import com.surimap.marker.seed.support.InMemoryMarkerRepository;
import com.surimap.marker.service.MarkerCreateService;
import com.surimap.marker.service.MarkerMutationContext;
import com.surimap.marker.service.MarkerRequestContext;
import com.surimap.marker.service.MarkerUpdateDeleteService;
import com.surimap.sync.idempotency.IdempotencyMismatchException;
import com.surimap.sync.idempotency.IdempotentResponseCache;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("S5 marker/photo durable idempotency")
class MarkerPhotoIdempotencyIntegrationTest {

  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0505");
  private static final UUID OP_ID = UUID.fromString("88888888-8888-8888-8888-888888880505");
  private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111110505");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("22222222-2222-2222-2222-222222220505");
  private static final UUID MARKER_ID = UUID.fromString("55555555-5555-5555-5555-555555550505");
  private static final Instant CLIENT_TS = Instant.parse("2026-04-28T00:05:00Z");

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private IdempotentResponseCache idempotentResponseCache;

  private InMemoryMarkerRepository markerRepository;
  private CapturingMarkerEventPublisher markerEventPublisher;
  private MarkerCreateService markerCreateService;
  private MarkerUpdateDeleteService markerUpdateDeleteService;
  private InMemoryPhotoRepository photoRepository;
  private MockObjectStorageAdapter objectStorage;
  private CapturingPhotoEventPublisher photoEventPublisher;
  private PhotoService photoService;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute("TRUNCATE TABLE idempotency_record");
    ObjectProvider<IdempotentResponseCache> cacheProvider =
        new FixedObjectProvider<>(idempotentResponseCache);

    markerRepository = new InMemoryMarkerRepository();
    markerEventPublisher = new CapturingMarkerEventPublisher();
    MarkerLocationValidator markerLocationValidator = (incidentId, location) -> {};
    markerCreateService =
        new MarkerCreateService(
            markerRepository,
            markerLocationValidator,
            new MarkerOpBindingValidator(incidentId -> Optional.of(OP_ID)),
            new AllowingMarkerWriteGuard(),
            markerEventPublisher,
            null,
            cacheProvider);
    markerUpdateDeleteService =
        new MarkerUpdateDeleteService(
            markerRepository,
            markerLocationValidator,
            new AllowingMarkerWriteGuard(),
            markerEventPublisher,
            cacheProvider);

    photoRepository = new InMemoryPhotoRepository();
    objectStorage = new MockObjectStorageAdapter();
    photoEventPublisher = new CapturingPhotoEventPublisher();
    photoService =
        new PhotoService(
            objectStorage,
            photoRepository,
            new AllowingPhotoWriteGuard(),
            photoEventPublisher,
            cacheProvider);
  }

  @Test
  @DisplayName("marker create replays from idempotency_record without creating another marker")
  void markerCreateReplayUsesDurableRecord() {
    MarkerRequestContext context = markerContext("idem-s5-marker-create-db");

    MarkerCreateResponse created = markerCreateService.create(markerRequest(), context).response();
    MarkerCreateResponse replayed = markerCreateService.create(markerRequest(), context).response();

    assertThat(replayed).isEqualTo(created);
    assertThat(markerRepository.records()).hasSize(1);
    assertThat(markerEventPublisher.published()).hasSize(1);
    assertThat(idempotencyStatus("idem-s5-marker-create-db")).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("same marker create key with different body is rejected")
  void markerCreateSameKeyDifferentBodyIsRejected() {
    MarkerRequestContext context = markerContext("idem-s5-marker-create-mismatch");
    markerCreateService.create(markerRequest(), context);

    assertThatThrownBy(
            () ->
                markerCreateService.create(
                    new MarkerCreateRequest(
                        INCIDENT_ID,
                        OP_ID,
                        MarkerType.NOTE.name(),
                        point(),
                        null,
                        "changed memo",
                        CLIENT_TS,
                        0L),
                    context))
        .isInstanceOf(IdempotencyMismatchException.class);
    assertThat(markerRepository.records()).hasSize(1);
  }

  @Test
  @DisplayName("marker update replay does not mutate marker or publish twice")
  void markerUpdateReplayUsesDurableRecord() {
    seedMarker();
    MarkerRequestContext context = markerContext("idem-s5-marker-update-db");
    MarkerUpdateRequest request = new MarkerUpdateRequest(1L, null, "updated durable marker", "NOTE");

    MarkerMutationResponse updated =
        markerUpdateDeleteService.update(MARKER_ID, request, context).response();
    MarkerMutationResponse replayed =
        markerUpdateDeleteService.update(MARKER_ID, request, context).response();

    assertThat(replayed).isEqualTo(updated);
    assertThat(updated.version()).isEqualTo(2L);
    assertThat(markerRepository.findById(MARKER_ID).orElseThrow().getMemo())
        .isEqualTo("updated durable marker");
    assertThat(markerEventPublisher.published()).hasSize(1);
    assertThat(idempotencyStatus("idem-s5-marker-update-db")).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("marker delete replay does not delete marker or publish twice")
  void markerDeleteReplayUsesDurableRecord() {
    seedMarker();
    MarkerRequestContext context = markerContext("idem-s5-marker-delete-db");
    MarkerDeleteRequest request = new MarkerDeleteRequest(1L, "duplicate delete");

    MarkerMutationResponse deleted =
        markerUpdateDeleteService.delete(MARKER_ID, request, context).response();
    MarkerMutationResponse replayed =
        markerUpdateDeleteService.delete(MARKER_ID, request, context).response();

    assertThat(replayed).isEqualTo(deleted);
    assertThat(deleted.version()).isEqualTo(2L);
    assertThat(markerRepository.findById(MARKER_ID).orElseThrow().getStatus())
        .isEqualTo(MarkerStatus.DELETED.name());
    assertThat(markerEventPublisher.published()).hasSize(1);
    assertThat(idempotencyStatus("idem-s5-marker-delete-db")).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("photo upload-url replay keeps one pending photo row")
  void photoUploadUrlReplayUsesDurableRecord() {
    PhotoRequestContext context = photoContext("idem-s5-photo-upload-db");

    PhotoUploadUrlResponse created =
        photoService.createUploadUrl(MARKER_ID, uploadRequest(), context);
    PhotoUploadUrlResponse replayed =
        photoService.createUploadUrl(MARKER_ID, uploadRequest(), context);

    assertThat(replayed).isEqualTo(created);
    assertThat(photoRepository.countByMarkerIdAndStatusIn(MARKER_ID, com.surimap.marker.photo.domain.PhotoStatus.countedStatuses()))
        .isEqualTo(1L);
    assertThat(idempotencyStatus("idem-s5-photo-upload-db")).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("photo attach replay does not publish another marker update")
  void photoAttachReplayUsesDurableRecord() {
    PhotoUploadUrlResponse upload =
        photoService.createUploadUrl(
            MARKER_ID, uploadRequest(), photoContext("idem-s5-photo-upload-attach"));
    objectStorage.simulateUpload(objectKey(upload.photoId()));
    PhotoRequestContext context = photoContext("idem-s5-photo-attach-db");

    PhotoAttachResponse attached =
        photoService.attach(MARKER_ID, upload.photoId(), attachRequest(), context).response();
    PhotoAttachResponse replayed =
        photoService.attach(MARKER_ID, upload.photoId(), attachRequest(), context).response();

    assertThat(replayed).isEqualTo(attached);
    assertThat(attached.version()).isEqualTo(2L);
    assertThat(photoEventPublisher.published()).hasSize(1);
    assertThat(idempotencyStatus("idem-s5-photo-attach-db")).isEqualTo("COMPLETED");
  }

  private MarkerCreateRequest markerRequest() {
    return new MarkerCreateRequest(
        INCIDENT_ID, OP_ID, MarkerType.CLUE.name(), point(), null, "durable marker create", CLIENT_TS, 0L);
  }

  private void seedMarker() {
    markerRepository.insertCreate(
        new MarkerCreateRecord(
            INCIDENT_ID,
            MARKER_ID,
            OP_ID,
            null,
            MarkerType.CLUE,
            null,
            point().toPoint(),
            "durable marker",
            CLIENT_TS,
            ACCOUNT_ID,
            POLICE_PHONE_ID,
            MarkerSource.APP,
            MarkerStatus.ACTIVE,
            1L));
  }

  private PhotoUploadUrlRequest uploadRequest() {
    return new PhotoUploadUrlRequest("image/jpeg", 1_048_576L, "sha256:fixture");
  }

  private PhotoAttachRequest attachRequest() {
    return new PhotoAttachRequest(1_048_576L, "image/jpeg", null, null, "sha256:fixture");
  }

  private MarkerGeoJsonPoint point() {
    return new MarkerGeoJsonPoint(
        "Point", List.of(new BigDecimal("126.913400"), new BigDecimal("35.163100")));
  }

  private MarkerRequestContext markerContext(String idempotencyKey) {
    return new MarkerRequestContext(
        new SuriMapAuthentication(ACCOUNT_ID, "APP", POLICE_PHONE_ID), idempotencyKey);
  }

  private PhotoRequestContext photoContext(String idempotencyKey) {
    return new PhotoRequestContext(
        new SuriMapAuthentication(ACCOUNT_ID, "APP", POLICE_PHONE_ID), idempotencyKey);
  }

  private String objectKey(UUID photoId) {
    return "markers/" + INCIDENT_ID + "/" + MARKER_ID + "/" + photoId + ".jpg";
  }

  private String idempotencyStatus(String idempotencyKey) {
    return jdbcTemplate.queryForObject(
        """
        SELECT idempotency_status
        FROM idempotency_record
        WHERE idempotency_key = ?
        """,
        String.class,
        idempotencyKey);
  }

  private record FixedObjectProvider<T>(T value) implements ObjectProvider<T> {
    @Override
    public T getObject(Object... args) {
      return value;
    }

    @Override
    public T getObject() {
      return value;
    }

    @Override
    public T getIfAvailable() {
      return value;
    }

    @Override
    public T getIfUnique() {
      return value;
    }

    @Override
    public Iterator<T> iterator() {
      return List.of(value).iterator();
    }

    @Override
    public Stream<T> stream() {
      return Stream.of(value);
    }

    @Override
    public Stream<T> orderedStream() {
      return stream();
    }
  }

  private static final class AllowingMarkerWriteGuard implements MarkerWriteGuardPort {
    @Override
    public void requireCreateAccess(UUID incidentId, UUID opId, MarkerRequestContext context) {}

    @Override
    public MarkerMutationContext requireUpdateAccess(UUID markerId, MarkerRequestContext context) {
      return new MarkerMutationContext(INCIDENT_ID, markerId, OP_ID, POLICE_PHONE_ID);
    }

    @Override
    public MarkerMutationContext requireDeleteAccess(UUID markerId, MarkerRequestContext context) {
      return new MarkerMutationContext(INCIDENT_ID, markerId, OP_ID, POLICE_PHONE_ID);
    }
  }

  private static final class AllowingPhotoWriteGuard implements PhotoWriteGuardPort {
    @Override
    public PhotoMarkerContext requireUploadUrlAccess(
        UUID markerId, PhotoRequestContext context) {
      return photoContext();
    }

    @Override
    public PhotoMarkerContext requireAttachAccess(
        UUID markerId, UUID photoId, PhotoRequestContext context) {
      return photoContext();
    }

    private static PhotoMarkerContext photoContext() {
      return new PhotoMarkerContext(
          INCIDENT_ID, MARKER_ID, OP_ID, POLICE_PHONE_ID, "UPDATED", 1L);
    }
  }

  private static final class CapturingMarkerEventPublisher implements MarkerEventPublisher {
    private final List<MarkerPublishRequest> published = new ArrayList<>();

    @Override
    public void publish(MarkerPublishRequest request) {
      published.add(request);
    }

    List<MarkerPublishRequest> published() {
      return published;
    }
  }

  private static final class CapturingPhotoEventPublisher implements PhotoEventPublisher {
    private final List<com.surimap.marker.photo.dto.PublishRequest> published = new ArrayList<>();

    @Override
    public void publish(com.surimap.marker.photo.dto.PublishRequest request) {
      published.add(request);
    }

    List<com.surimap.marker.photo.dto.PublishRequest> published() {
      return published;
    }
  }
}
