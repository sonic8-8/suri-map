package com.surimap.marker.photo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.marker.photo.adapter.MockObjectStorageAdapter;
import com.surimap.marker.photo.domain.PhotoMarkerContext;
import com.surimap.marker.photo.domain.PhotoStatus;
import com.surimap.marker.photo.dto.PhotoAttachRequest;
import com.surimap.marker.photo.dto.PhotoAttachResult;
import com.surimap.marker.photo.dto.PhotoUploadUrlRequest;
import com.surimap.marker.photo.dto.PhotoUploadUrlResponse;
import com.surimap.marker.photo.exception.PhotoApiException;
import com.surimap.marker.photo.port.ObjectStoragePort;
import com.surimap.marker.photo.port.PhotoEventPublisher;
import com.surimap.marker.photo.port.PhotoWriteGuardPort;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import com.surimap.marker.photo.service.PhotoRequestContext;
import com.surimap.marker.photo.service.PhotoService;
import com.surimap.marker.photo.support.InMemoryPhotoRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@DisplayName("사진 upload-url/attach 서비스")
class PhotoServiceTest {

  private static final UUID MARKER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final UUID OTHER_MARKER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000202");
  private static final UUID INCIDENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
  private static final UUID OP_ID = UUID.fromString("00000000-0000-0000-0000-000000000401");
  private static final UUID OTHER_OP_ID = UUID.fromString("00000000-0000-0000-0000-000000000402");
  private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000501");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000601");
  private static final Instant NOW = Instant.parse("2026-04-28T00:00:00Z");

  private MockObjectStorageAdapter storage;
  private InMemoryPhotoRepository repository;
  private FakePhotoWriteGuard guard;
  private CapturingPhotoEventPublisher eventPublisher;
  private MutableClock clock;
  private PhotoService photoService;
  private PhotoRequestContext requestContext;

  @BeforeEach
  void setUp() {
    storage = new MockObjectStorageAdapter();
    repository = new InMemoryPhotoRepository();
    guard = new FakePhotoWriteGuard();
    eventPublisher = new CapturingPhotoEventPublisher();
    clock = new MutableClock(NOW);
    guard.allow(
        new PhotoMarkerContext(INCIDENT_ID, MARKER_ID, OP_ID, POLICE_PHONE_ID, "UPDATED", 1L));
    guard.useCurrentOp(OP_ID);
    photoService = new PhotoService(storage, repository, guard, eventPublisher, clock);
    requestContext =
        new PhotoRequestContext(
            new SuriMapAuthentication(ACCOUNT_ID, "APP", POLICE_PHONE_ID), "idem-photo-write-001");
  }

  @Nested
  @DisplayName("upload-url")
  class UploadUrl {

    @Test
    @DisplayName("정상 요청은 mock uploadUrl, 15분 만료, 초기 version=1을 반환한다")
    void validRequestReturnsUploadUrlContract() {
      var request = new PhotoUploadUrlRequest("image/jpeg", 1_048_576L, "sha256:fixture");

      PhotoUploadUrlResponse response =
          photoService.createUploadUrl(MARKER_ID, request, requestContext);

      assertThat(response.photoId()).isNotNull();
      assertThat(response.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
      assertThat(response.maxSizeBytes()).isEqualTo(10_485_760L);
      assertThat(response.version()).isEqualTo(1L);
      String expectedObjectKey =
          "markers/" + INCIDENT_ID + "/" + MARKER_ID + "/" + response.photoId() + ".jpg";
      assertThat(repository.findById(response.photoId()))
          .get()
          .extracting("objectKey")
          .isEqualTo(expectedObjectKey);
      assertThat(response.uploadUrl())
          .isEqualTo("http://127.0.0.1:18080/mock-upload/" + expectedObjectKey);
    }

    @Test
    @DisplayName("마커당 11번째 사진 upload-url은 photo_limit_exceeded로 거부한다")
    void eleventhPhotoRejected() {
      for (int index = 0; index < 10; index++) {
        photoService.createUploadUrl(
            MARKER_ID, new PhotoUploadUrlRequest("image/jpeg", 1_048_576L, null), requestContext);
      }

      assertThatThrownBy(
              () ->
                  photoService.createUploadUrl(
                      MARKER_ID,
                      new PhotoUploadUrlRequest("image/jpeg", 1_048_576L, null),
                      requestContext))
          .isInstanceOf(PhotoApiException.class)
          .extracting("error")
          .isEqualTo("photo_limit_exceeded");
    }

    @Test
    @DisplayName("10MB 초과 upload-url은 photo_limit_exceeded로 거부한다")
    void oversizedPhotoRejected() {
      assertThatThrownBy(
              () ->
                  photoService.createUploadUrl(
                      MARKER_ID,
                      new PhotoUploadUrlRequest("image/jpeg", 10_485_761L, null),
                      requestContext))
          .isInstanceOf(PhotoApiException.class)
          .extracting("error")
          .isEqualTo("photo_limit_exceeded");
    }

    @Test
    @DisplayName("미등록 PolicePhone은 photo row를 만들지 않고 police_phone_not_registered로 실패한다")
    void unregisteredPolicePhoneRejected() {
      guard.fail(MARKER_ID, "police_phone_not_registered", HttpStatus.FORBIDDEN);

      assertThatThrownBy(
              () ->
                  photoService.createUploadUrl(
                      MARKER_ID,
                      new PhotoUploadUrlRequest("image/jpeg", 1_048_576L, null),
                      requestContext))
          .isInstanceOf(PhotoApiException.class)
          .extracting("error")
          .isEqualTo("police_phone_not_registered");
      assertThat(repository.countByMarkerIdAndStatusIn(MARKER_ID, PhotoStatus.countedStatuses()))
          .isZero();
    }

    @Test
    @DisplayName("미배정 PolicePhone은 photo row를 만들지 않고 police_phone_not_assigned로 실패한다")
    void unassignedPolicePhoneRejected() {
      guard.fail(MARKER_ID, "police_phone_not_assigned", HttpStatus.FORBIDDEN);

      assertThatThrownBy(
              () ->
                  photoService.createUploadUrl(
                      MARKER_ID,
                      new PhotoUploadUrlRequest("image/jpeg", 1_048_576L, null),
                      requestContext))
          .isInstanceOf(PhotoApiException.class)
          .extracting("error")
          .isEqualTo("police_phone_not_assigned");
      assertThat(repository.countByMarkerIdAndStatusIn(MARKER_ID, PhotoStatus.countedStatuses()))
          .isZero();
    }

    @Test
    @DisplayName("사건 접근 권한이 없는 계정은 incident_access_denied로 실패한다")
    void incidentAccessDeniedRejected() {
      guard.fail(MARKER_ID, "incident_access_denied", HttpStatus.FORBIDDEN);

      assertThatThrownBy(
              () ->
                  photoService.createUploadUrl(
                      MARKER_ID,
                      new PhotoUploadUrlRequest("image/jpeg", 1_048_576L, null),
                      requestContext))
          .isInstanceOf(PhotoApiException.class)
          .extracting("error")
          .isEqualTo("incident_access_denied");
      assertThat(repository.countByMarkerIdAndStatusIn(MARKER_ID, PhotoStatus.countedStatuses()))
          .isZero();
    }

    @Test
    @DisplayName("종료된 사건의 upload-url 요청은 photo row를 만들지 않고 incident_closed로 실패한다")
    void closedIncidentUploadUrlRejected() {
      guard.fail(MARKER_ID, "incident_closed", HttpStatus.CONFLICT);

      assertThatThrownBy(
              () ->
                  photoService.createUploadUrl(
                      MARKER_ID,
                      new PhotoUploadUrlRequest("image/jpeg", 1_048_576L, null),
                      requestContext))
          .isInstanceOf(PhotoApiException.class)
          .extracting("error")
          .isEqualTo("incident_closed");
      assertThat(repository.countByMarkerIdAndStatusIn(MARKER_ID, PhotoStatus.countedStatuses()))
          .isZero();
    }

    @Test
    @DisplayName("marker OP가 current OP와 다르면 upload-url은 photo row를 만들지 않고 op_mismatch로 실패한다")
    void currentOpMismatchBlocksUploadUrl() {
      guard.useCurrentOp(OTHER_OP_ID);

      assertThatThrownBy(
              () ->
                  photoService.createUploadUrl(
                      MARKER_ID,
                      new PhotoUploadUrlRequest("image/jpeg", 1_048_576L, null),
                      requestContext))
          .isInstanceOf(PhotoApiException.class)
          .extracting("error")
          .isEqualTo("op_mismatch");
      assertThat(repository.countByMarkerIdAndStatusIn(MARKER_ID, PhotoStatus.countedStatuses()))
          .isZero();
    }

    @Test
    @DisplayName("존재하지 않는 markerId는 write_conflict로 실패한다")
    void nonexistentMarkerRejected() {
      assertThatThrownBy(
              () ->
                  photoService.createUploadUrl(
                      OTHER_MARKER_ID,
                      new PhotoUploadUrlRequest("image/jpeg", 1_048_576L, null),
                      requestContext))
          .isInstanceOf(PhotoApiException.class)
          .extracting("error")
          .isEqualTo("write_conflict");
      assertThat(
              repository.countByMarkerIdAndStatusIn(OTHER_MARKER_ID, PhotoStatus.countedStatuses()))
          .isZero();
    }
  }

  @Nested
  @DisplayName("attach")
  class Attach {

    @Test
    @DisplayName("업로드 완료 사진 attach는 ATTACHED version=2와 MARKER_UPDATED.photoDelta를 반환한다")
    void uploadedPhotoAttachReturnsPhotoDeltaContract() {
      var upload =
          photoService.createUploadUrl(
              MARKER_ID,
              new PhotoUploadUrlRequest("image/jpeg", 1_048_576L, "sha256:fixture"),
              requestContext);
      storage.simulateUpload(pendingObjectKey(upload.photoId()));

      PhotoAttachResult result =
          photoService.attach(
              MARKER_ID,
              upload.photoId(),
              new PhotoAttachRequest(1_048_576L, "image/jpeg", 640, 480, "sha256:fixture"),
              requestContext);

      assertThat(result.response().status()).isEqualTo("ATTACHED");
      assertThat(result.response().version()).isEqualTo(2L);
      assertThat(result.response().markerId()).isEqualTo(MARKER_ID);
      assertThat(result.response().markerVersion()).isEqualTo(2L);
      assertThat(result.publishRequest().type()).isEqualTo("MARKER_UPDATED");
      assertThat(result.publishRequest().payload().id()).isEqualTo(MARKER_ID);
      assertThat(result.publishRequest().payload().incidentId()).isEqualTo(INCIDENT_ID);
      assertThat(result.publishRequest().payload().opId()).isEqualTo(OP_ID);
      assertThat(result.publishRequest().payload().policePhoneId()).isEqualTo(POLICE_PHONE_ID);
      assertThat(result.publishRequest().payload().status()).isEqualTo("UPDATED");
      assertThat(result.publishRequest().payload().version()).isEqualTo(2L);
      assertThat(result.publishRequest().payload().photoDelta().photoId())
          .isEqualTo(upload.photoId());
      assertThat(result.publishRequest().payload().photoDelta().status()).isEqualTo("ATTACHED");
      assertThat(result.publishRequest().payload().photoDelta().version()).isEqualTo(2L);
      assertThat(eventPublisher.published()).containsExactly(result.publishRequest());
    }

    @Test
    @DisplayName("upload-url row가 없는 orphan photo attach는 write_conflict로 거부한다")
    void orphanPhotoRejected() {
      UUID orphanPhotoId = UUID.fromString("00000000-0000-0000-0000-000000000303");
      storage.simulateUpload(INCIDENT_ID + "/" + MARKER_ID + "/" + orphanPhotoId);

      assertThatThrownBy(
              () ->
                  photoService.attach(
                      MARKER_ID,
                      orphanPhotoId,
                      new PhotoAttachRequest(1_048_576L, "image/jpeg", null, null, null),
                      requestContext))
          .isInstanceOf(PhotoApiException.class)
          .extracting("error")
          .isEqualTo("write_conflict");
      assertThat(eventPublisher.published()).isEmpty();
    }

    @Test
    @DisplayName("다른 markerId로 발급된 photo attach는 write_conflict로 거부한다")
    void mismatchedMarkerPhotoRejected() {
      var upload =
          photoService.createUploadUrl(
              MARKER_ID, new PhotoUploadUrlRequest("image/jpeg", 1_048_576L, null), requestContext);
      guard.allow(
          new PhotoMarkerContext(
              INCIDENT_ID, OTHER_MARKER_ID, OP_ID, POLICE_PHONE_ID, "UPDATED", 1L));
      storage.simulateUpload(pendingObjectKey(upload.photoId()));

      assertThatThrownBy(
              () ->
                  photoService.attach(
                      OTHER_MARKER_ID,
                      upload.photoId(),
                      new PhotoAttachRequest(1_048_576L, "image/jpeg", null, null, null),
                      requestContext))
          .isInstanceOf(PhotoApiException.class)
          .extracting("error")
          .isEqualTo("write_conflict");
      assertThat(eventPublisher.published()).isEmpty();
    }

    @Test
    @DisplayName("종료된 사건의 photo attach는 incident_closed로 실패하고 event를 발행하지 않는다")
    void closedIncidentAttachRejected() {
      var upload =
          photoService.createUploadUrl(
              MARKER_ID, new PhotoUploadUrlRequest("image/jpeg", 1_048_576L, null), requestContext);
      storage.simulateUpload(pendingObjectKey(upload.photoId()));
      guard.fail(MARKER_ID, "incident_closed", HttpStatus.CONFLICT);

      assertThatThrownBy(
              () ->
                  photoService.attach(
                      MARKER_ID,
                      upload.photoId(),
                      new PhotoAttachRequest(1_048_576L, "image/jpeg", null, null, null),
                      requestContext))
          .isInstanceOf(PhotoApiException.class)
          .extracting("error")
          .isEqualTo("incident_closed");
      assertThat(eventPublisher.published()).isEmpty();
    }

    @Test
    @DisplayName("current OP가 없으면 photo attach는 op_required로 실패하고 event를 발행하지 않는다")
    void missingCurrentOpBlocksAttach() {
      var upload =
          photoService.createUploadUrl(
              MARKER_ID, new PhotoUploadUrlRequest("image/jpeg", 1_048_576L, null), requestContext);
      storage.simulateUpload(pendingObjectKey(upload.photoId()));
      guard.useCurrentOp(null);

      assertThatThrownBy(
              () ->
                  photoService.attach(
                      MARKER_ID,
                      upload.photoId(),
                      new PhotoAttachRequest(1_048_576L, "image/jpeg", null, null, null),
                      requestContext))
          .isInstanceOf(PhotoApiException.class)
          .extracting("error")
          .isEqualTo("op_required");
      assertThat(repository.findById(upload.photoId()))
          .get()
          .extracting("status")
          .isEqualTo(PhotoStatus.PENDING_UPLOAD);
      assertThat(eventPublisher.published()).isEmpty();
    }

    @Test
    @DisplayName("동일 photo attach 재시도는 두 번째 markerVersion 또는 event를 만들지 않는다")
    void duplicateAttachRejectedWithoutSecondEvent() {
      var upload =
          photoService.createUploadUrl(
              MARKER_ID, new PhotoUploadUrlRequest("image/jpeg", 1_048_576L, null), requestContext);
      storage.simulateUpload(pendingObjectKey(upload.photoId()));
      photoService.attach(
          MARKER_ID,
          upload.photoId(),
          new PhotoAttachRequest(1_048_576L, "image/jpeg", null, null, null),
          requestContext);

      assertThatThrownBy(
              () ->
                  photoService.attach(
                      MARKER_ID,
                      upload.photoId(),
                      new PhotoAttachRequest(1_048_576L, "image/jpeg", null, null, null),
                      requestContext))
          .isInstanceOf(PhotoApiException.class)
          .extracting("error")
          .isEqualTo("write_conflict");
      assertThat(eventPublisher.published()).hasSize(1);
      assertThat(repository.countByMarkerIdAndStatusIn(MARKER_ID, Set.of(PhotoStatus.ATTACHED)))
          .isEqualTo(1);
      assertThat(repository.findById(upload.photoId()))
          .get()
          .extracting("objectKey")
          .isEqualTo("markers/" + INCIDENT_ID + "/" + MARKER_ID + "/" + upload.photoId() + ".jpg");
    }

    @Test
    @DisplayName("upload URL TTL이 지난 attach는 photo를 FAILED로 닫고 write_conflict를 반환한다")
    void expiredUploadUrlRejectedAndMarkedFailed() {
      var upload =
          photoService.createUploadUrl(
              MARKER_ID, new PhotoUploadUrlRequest("image/jpeg", 1_048_576L, null), requestContext);
      storage.simulateUpload(pendingObjectKey(upload.photoId()));
      clock.advance(Duration.ofMinutes(16));

      assertThatThrownBy(
              () ->
                  photoService.attach(
                      MARKER_ID,
                      upload.photoId(),
                      new PhotoAttachRequest(1_048_576L, "image/jpeg", null, null, null),
                      requestContext))
          .isInstanceOf(PhotoApiException.class)
          .extracting("error")
          .isEqualTo("write_conflict");

      assertThat(repository.findById(upload.photoId()))
          .get()
          .extracting("status")
          .isEqualTo(PhotoStatus.FAILED);
      assertThat(eventPublisher.published()).isEmpty();
    }

    @Test
    @DisplayName("checksum 불일치 attach는 photo를 FAILED로 닫고 write_conflict를 반환한다")
    void checksumMismatchRejectedAndMarkedFailed() {
      var upload =
          photoService.createUploadUrl(
              MARKER_ID,
              new PhotoUploadUrlRequest("image/jpeg", 1_048_576L, "sha256:expected"),
              requestContext);
      storage.simulateUpload(pendingObjectKey(upload.photoId()));

      assertThatThrownBy(
              () ->
                  photoService.attach(
                      MARKER_ID,
                      upload.photoId(),
                      new PhotoAttachRequest(1_048_576L, "image/jpeg", null, null, "sha256:actual"),
                      requestContext))
          .isInstanceOf(PhotoApiException.class)
          .extracting("error")
          .isEqualTo("write_conflict");

      assertThat(repository.findById(upload.photoId()))
          .get()
          .extracting("status")
          .isEqualTo(PhotoStatus.FAILED);
      assertThat(eventPublisher.published()).isEmpty();
    }

    @Test
    @DisplayName("object storage blob이 없으면 attach는 write_conflict로 실패한다")
    void missingBlobRejected() {
      var upload =
          photoService.createUploadUrl(
              MARKER_ID, new PhotoUploadUrlRequest("image/jpeg", 1_048_576L, null), requestContext);

      assertThatThrownBy(
              () ->
                  photoService.attach(
                      MARKER_ID,
                      upload.photoId(),
                      new PhotoAttachRequest(1_048_576L, "image/jpeg", null, null, null),
                      requestContext))
          .isInstanceOf(PhotoApiException.class)
          .extracting("error")
          .isEqualTo("write_conflict");
      assertThat(eventPublisher.published()).isEmpty();
    }

    @Test
    @DisplayName("10MB 초과 attach는 photo_limit_exceeded로 실패한다")
    void oversizedAttachRejected() {
      var upload =
          photoService.createUploadUrl(
              MARKER_ID, new PhotoUploadUrlRequest("image/jpeg", 1_048_576L, null), requestContext);
      storage.simulateUpload(pendingObjectKey(upload.photoId()));

      assertThatThrownBy(
              () ->
                  photoService.attach(
                      MARKER_ID,
                      upload.photoId(),
                      new PhotoAttachRequest(10_485_761L, "image/jpeg", null, null, null),
                      requestContext))
          .isInstanceOf(PhotoApiException.class)
          .extracting("error")
          .isEqualTo("photo_limit_exceeded");
      assertThat(eventPublisher.published()).isEmpty();
    }

    @Test
    @DisplayName("object storage metadata가 photo row와 다르면 attach는 실패하고 event를 발행하지 않는다")
    void objectStorageMetadataMismatchRejectedWithoutPublish() {
      var metadataAwareStorage = new MetadataAwareObjectStorage();
      var service =
          new PhotoService(metadataAwareStorage, repository, guard, eventPublisher, clock);
      var upload =
          service.createUploadUrl(
              MARKER_ID,
              new PhotoUploadUrlRequest("image/jpeg", 1_048_576L, "sha256:expected"),
              requestContext);
      String objectKey = pendingObjectKey(upload.photoId());
      metadataAwareStorage.simulateUpload(objectKey, "image/png", 10_485_760L, "sha256:different");

      assertThatThrownBy(
              () ->
                  service.attach(
                      MARKER_ID,
                      upload.photoId(),
                      new PhotoAttachRequest(
                          1_048_576L, "image/jpeg", null, null, "sha256:expected"),
                      requestContext))
          .isInstanceOf(PhotoApiException.class)
          .extracting("error")
          .isEqualTo("write_conflict");

      assertThat(repository.findById(upload.photoId()))
          .get()
          .extracting("status")
          .isEqualTo(PhotoStatus.FAILED);
      assertThat(repository.countByMarkerIdAndStatusIn(MARKER_ID, Set.of(PhotoStatus.ATTACHED)))
          .isZero();
      assertThat(eventPublisher.published()).isEmpty();
    }

    @Test
    @DisplayName("row checksum이 없어도 attach 요청과 object checksum이 다르면 실패한다")
    void requestAndObjectChecksumMismatchRejectedWhenRowChecksumIsNull() {
      var metadataAwareStorage = new MetadataAwareObjectStorage();
      var service =
          new PhotoService(metadataAwareStorage, repository, guard, eventPublisher, clock);
      var upload =
          service.createUploadUrl(
              MARKER_ID, new PhotoUploadUrlRequest("image/jpeg", 1_048_576L, null), requestContext);
      String objectKey = pendingObjectKey(upload.photoId());
      metadataAwareStorage.simulateUpload(objectKey, "image/jpeg", 1_048_576L, "sha256:uploaded");

      assertThatThrownBy(
              () ->
                  service.attach(
                      MARKER_ID,
                      upload.photoId(),
                      new PhotoAttachRequest(1_048_576L, "image/jpeg", null, null, "sha256:client"),
                      requestContext))
          .isInstanceOf(PhotoApiException.class)
          .extracting("error")
          .isEqualTo("write_conflict");

      assertThat(repository.findById(upload.photoId()))
          .get()
          .extracting("status")
          .isEqualTo(PhotoStatus.FAILED);
      assertThat(eventPublisher.published()).isEmpty();
    }

    @Test
    @DisplayName("offline retry는 missing object 후 같은 pending row/objectKey로 1회만 attach한다")
    void offlineRetryAfterMissingObjectReusesPendingObjectKeyAndAttachesOnce() {
      var upload =
          photoService.createUploadUrl(
              MARKER_ID,
              new PhotoUploadUrlRequest("image/jpeg", 1_048_576L, "sha256:fixture"),
              requestContext);
      String objectKeyBeforeRetry = pendingObjectKey(upload.photoId());

      assertThatThrownBy(
              () ->
                  photoService.attach(
                      MARKER_ID,
                      upload.photoId(),
                      new PhotoAttachRequest(
                          1_048_576L, "image/jpeg", null, null, "sha256:fixture"),
                      requestContext))
          .isInstanceOf(PhotoApiException.class)
          .extracting("error")
          .isEqualTo("write_conflict");
      assertThat(repository.findById(upload.photoId()))
          .get()
          .extracting("status", "objectKey")
          .containsExactly(PhotoStatus.PENDING_UPLOAD, objectKeyBeforeRetry);
      assertThat(eventPublisher.published()).isEmpty();

      storage.simulateUpload(objectKeyBeforeRetry);
      photoService.attach(
          MARKER_ID,
          upload.photoId(),
          new PhotoAttachRequest(1_048_576L, "image/jpeg", null, null, "sha256:fixture"),
          requestContext);

      assertThat(repository.findById(upload.photoId()))
          .get()
          .extracting("status", "objectKey")
          .containsExactly(PhotoStatus.ATTACHED, objectKeyBeforeRetry);
      assertThat(repository.countByMarkerIdAndStatusIn(MARKER_ID, Set.of(PhotoStatus.ATTACHED)))
          .isEqualTo(1);
      assertThat(eventPublisher.published()).hasSize(1);
    }
  }

  private String pendingObjectKey(UUID photoId) {
    return repository.findById(photoId).orElseThrow().objectKey();
  }

  private static final class FakePhotoWriteGuard implements PhotoWriteGuardPort {

    private final Map<UUID, PhotoMarkerContext> contexts = new HashMap<>();
    private final Map<UUID, PhotoApiException> failures = new HashMap<>();
    private UUID currentOpId;

    void allow(PhotoMarkerContext context) {
      contexts.put(context.markerId(), context);
      failures.remove(context.markerId());
    }

    void useCurrentOp(UUID currentOpId) {
      this.currentOpId = currentOpId;
    }

    void fail(UUID markerId, String error, HttpStatus status) {
      failures.put(markerId, new PhotoApiException(error, status));
    }

    @Override
    public PhotoMarkerContext requireUploadUrlAccess(UUID markerId, PhotoRequestContext context) {
      return contextFor(markerId);
    }

    @Override
    public PhotoMarkerContext requireAttachAccess(
        UUID markerId, UUID photoId, PhotoRequestContext context) {
      return contextFor(markerId);
    }

    private PhotoMarkerContext contextFor(UUID markerId) {
      if (failures.containsKey(markerId)) {
        throw failures.get(markerId);
      }
      PhotoMarkerContext context = contexts.get(markerId);
      if (context == null) {
        throw new PhotoApiException("write_conflict", HttpStatus.CONFLICT);
      }
      if (currentOpId == null) {
        throw new PhotoApiException("op_required", HttpStatus.CONFLICT);
      }
      if (!currentOpId.equals(context.opId())) {
        throw new PhotoApiException("op_mismatch", HttpStatus.CONFLICT);
      }
      return context;
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

  private static final class MetadataAwareObjectStorage implements ObjectStoragePort {

    private static final String MOCK_BASE_URL = "http://127.0.0.1:18080/mock-upload/";

    private final Map<String, ObjectMetadata> uploaded = new HashMap<>();

    @Override
    public PresignedUploadResult generatePresignedUrl(
        String objectKey, String contentType, long sizeBytes, String checksumSha256, Duration ttl) {
      return new PresignedUploadResult(
          MOCK_BASE_URL + objectKey,
          objectKey,
          "mock://object-storage/suri-map-harness",
          Instant.now().plus(ttl),
          sizeBytes,
          contentType,
          checksumSha256);
    }

    @Override
    public String generateUploadUrl(String objectKey, String contentType, long sizeBytes) {
      return MOCK_BASE_URL + objectKey;
    }

    @Override
    public Optional<ObjectMetadata> headObject(String objectKey) {
      return Optional.ofNullable(uploaded.get(objectKey));
    }

    @Override
    public void deleteObject(String objectKey) {
      uploaded.remove(objectKey);
    }

    void simulateUpload(
        String objectKey, String contentType, long sizeBytes, String checksumSha256) {
      uploaded.put(
          objectKey, new ObjectMetadata(objectKey, contentType, sizeBytes, checksumSha256));
    }
  }

  private static final class MutableClock extends Clock {

    private Instant instant;

    MutableClock(Instant instant) {
      this.instant = instant;
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
