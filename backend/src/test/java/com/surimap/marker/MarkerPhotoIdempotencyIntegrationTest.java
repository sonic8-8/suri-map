package com.surimap.marker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.app.service.marker.AppMarkerService;
import com.surimap.app.service.marker.request.MarkerCreateServiceRequest;
import com.surimap.app.service.marker.request.MarkerDeleteServiceRequest;
import com.surimap.app.service.marker.request.MarkerUpdateServiceRequest;
import com.surimap.app.service.marker.response.MarkerCreateServiceResponse;
import com.surimap.app.service.marker.response.MarkerMutationServiceResponse;
import com.surimap.domain.marker.Marker;
import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.domain.port.MarkerLocationValidator;
import com.surimap.marker.domain.service.MarkerOpBindingValidator;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.dto.MarkerPublishRequest;
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
import com.surimap.marker.seed.support.InMemoryMarkerRepository;
import com.surimap.marker.service.MarkerMutationContext;
import com.surimap.marker.service.MarkerRequestContext;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
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
  private AppMarkerService appMarkerService;
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
    appMarkerService =
        new AppMarkerService(
            markerRepository,
            markerLocationValidator,
            new MarkerOpBindingValidator(incidentId -> Optional.of(OP_ID)),
            new AllowingMarkerWriteGuard(),
            markerEventPublisher,
            null,
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

    MarkerCreateServiceResponse created =
        appMarkerService.create(markerRequest().toBuilder().context(context).build());
    MarkerCreateServiceResponse replayed =
        appMarkerService.create(markerRequest().toBuilder().context(context).build());

    assertThat(replayed).usingRecursiveComparison().isEqualTo(created);
    assertThat(markerRepository.records()).hasSize(1);
    assertThat(markerEventPublisher.published()).hasSize(1);
    assertThat(idempotencyStatus("idem-s5-marker-create-db")).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("same marker create key with different body is rejected")
  void markerCreateSameKeyDifferentBodyIsRejected() {
    MarkerRequestContext context = markerContext("idem-s5-marker-create-mismatch");
    appMarkerService.create(markerRequest().toBuilder().context(context).build());

    assertThatThrownBy(
            () ->
                appMarkerService.create(
                    MarkerCreateServiceRequest.builder()
                        .incidentId(INCIDENT_ID)
                        .opId(OP_ID)
                        .type(MarkerType.NOTE.name())
                        .location(point())
                        .memo("changed memo")
                        .clientTs(CLIENT_TS)
                        .clockOffsetMs(0L)
                        .context(context)
                        .build()))
        .isInstanceOf(IdempotencyMismatchException.class);
    assertThat(markerRepository.records()).hasSize(1);
  }

  @Test
  @DisplayName("같은 수정 요청을 다시 보내면, 기존 응답을 반환하고 마커·이벤트를 중복 변경하지 않는다")
  void updateMarker_sameKeyAndBody_returnsStoredResponseWithoutDuplicates() {
    // given: 저장된 마커와 같은 키로 반복할 수정 요청을 준비한다.
    seedMarker();
    MarkerRequestContext context = markerContext("idem-s5-marker-update-db");
    MarkerUpdateServiceRequest request = createUpdateRequest(context);

    // when: 같은 키와 본문으로 두 번 수정한다.
    MarkerMutationServiceResponse updated = appMarkerService.update(request);
    assertThat(requestBodyHash("idem-s5-marker-update-db"))
        .isEqualTo("ee82990a08aec3357c7e06eb1743929cf95f1138d4b556044daa02e5815c903d");
    MarkerMutationServiceResponse replayed = appMarkerService.update(request);

    // then: 첫 응답을 재사용하고 수정·이벤트 발행은 한 번만 한다.
    assertThat(replayed).usingRecursiveComparison().isEqualTo(updated);
    assertThat(updated.getVersion()).isEqualTo(2L);
    assertThat(markerRepository.findById(MARKER_ID).orElseThrow().getMemo())
        .isEqualTo("updated durable marker");
    assertThat(markerEventPublisher.published()).hasSize(1);
    assertThat(idempotencyStatus("idem-s5-marker-update-db")).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("변경 전 해시로 기록된 수정 요청을 다시 보내면, 기존 응답을 반환하고 중복 수정하지 않는다")
  void updateMarker_legacyRequestHash_returnsStoredResponseWithoutDuplicates() {
    // given: 이전 서버가 처리한 수정 요청의 해시와 응답이 남아 있다.
    seedMarker();
    MarkerRequestContext context = markerContext("idem-s5-marker-update-db");
    MarkerUpdateServiceRequest request = createUpdateRequest(context);
    MarkerMutationServiceResponse updated = appMarkerService.update(request);
    replaceStoredRequestHash(
        context.idempotencyKey(),
        "0f9374acb0c8578e46c7b9e1c3959a494316a8651d5e889f644fcbf5b97ab84a");

    // when: 같은 키와 본문을 재전송한다.
    MarkerMutationServiceResponse replayed = appMarkerService.update(request);

    // then: 저장된 응답을 반환하고 수정과 이벤트 발행은 한 번만 한다.
    assertThat(replayed).usingRecursiveComparison().isEqualTo(updated);
    assertThat(markerRepository.findById(MARKER_ID).orElseThrow().getVersion()).isEqualTo(2L);
    assertThat(markerEventPublisher.published()).hasSize(1);
  }

  @Test
  @DisplayName("좌표가 포함된 과거 수정 요청을 다시 보내면, 기존 응답을 반환하고 중복 수정하지 않는다")
  void updateMarker_legacyRequestWithLocation_returnsStoredResponseWithoutDuplicates() {
    // given: 좌표만 바꾼 과거 요청의 해시와 응답이 남아 있다.
    seedMarker();
    MarkerRequestContext context = markerContext("idem-s5-marker-update-db");
    MarkerUpdateServiceRequest request =
        createUpdateRequest(context).toBuilder().location(point()).memo(null).type(null).build();
    MarkerMutationServiceResponse updated = appMarkerService.update(request);
    replaceStoredRequestHash(
        context.idempotencyKey(),
        "f5c8248db29432930ec45ab14b1c3787da80b5f8806c0fd15afc890f181ad748");

    // when: 같은 좌표를 포함한 요청을 재전송한다.
    MarkerMutationServiceResponse replayed = appMarkerService.update(request);

    // then: 과거 좌표 표현을 동일하게 비교하고 마커·이벤트를 추가로 변경하지 않는다.
    assertThat(replayed).usingRecursiveComparison().isEqualTo(updated);
    assertThat(markerRepository.findById(MARKER_ID).orElseThrow().getVersion()).isEqualTo(2L);
    assertThat(markerEventPublisher.published()).hasSize(1);
  }

  @ParameterizedTest(name = "{0} 해시 기록")
  @ValueSource(strings = {"JSON", "LEGACY"})
  @DisplayName("같은 수정 요청 키에 다른 메모를 보내면, 기존 마커·이벤트를 변경하지 않고 거부한다")
  void updateMarker_sameKeyWithDifferentMemo_rejectsWithoutChangingMarkerOrEvents(
      String storedHashFormat) {
    // given: 새 방식 또는 과거 방식으로 처리된 수정 요청이 있다.
    seedMarker();
    MarkerRequestContext context = markerContext("idem-s5-marker-update-db");
    MarkerUpdateServiceRequest request = createUpdateRequest(context);
    appMarkerService.update(request);
    if ("LEGACY".equals(storedHashFormat)) {
      replaceStoredRequestHash(
          context.idempotencyKey(),
          "0f9374acb0c8578e46c7b9e1c3959a494316a8651d5e889f644fcbf5b97ab84a");
    }

    // when & then: 같은 키에 다른 메모를 담아 보내면 본문 불일치로 거부한다.
    assertThatThrownBy(
            () -> appMarkerService.update(request.toBuilder().memo("changed memo").build()))
        .isInstanceOf(IdempotencyMismatchException.class);
    assertThat(markerRepository.findById(MARKER_ID).orElseThrow().getMemo())
        .isEqualTo("updated durable marker");
    assertThat(markerRepository.findById(MARKER_ID).orElseThrow().getVersion()).isEqualTo(2L);
    assertThat(markerEventPublisher.published()).hasSize(1);
  }

  @Test
  @DisplayName("같은 삭제 요청을 다시 보내면, 기존 응답을 반환하고 마커·이벤트를 중복 변경하지 않는다")
  void deleteMarker_sameKeyAndBody_returnsStoredResponseWithoutDuplicates() {
    // given: 저장된 마커와 같은 키로 반복할 삭제 요청을 준비한다.
    seedMarker();
    MarkerRequestContext context = markerContext("idem-s5-marker-delete-db");
    MarkerDeleteServiceRequest request = createDeleteRequest(context);

    // when: 같은 키와 본문으로 두 번 삭제한다.
    MarkerMutationServiceResponse deleted = appMarkerService.delete(request);
    assertThat(requestBodyHash("idem-s5-marker-delete-db"))
        .isEqualTo("45e854b54a52a25efec7e881c38e87e42f91fc6d8548bbb315daad75bc7e0509");
    MarkerMutationServiceResponse replayed = appMarkerService.delete(request);

    // then: 첫 응답을 재사용하고 삭제·이벤트 발행은 한 번만 한다.
    assertThat(replayed).usingRecursiveComparison().isEqualTo(deleted);
    assertThat(deleted.getVersion()).isEqualTo(2L);
    assertThat(markerRepository.findById(MARKER_ID).orElseThrow().getStatus())
        .isEqualTo(MarkerStatus.DELETED.name());
    assertThat(markerEventPublisher.published()).hasSize(1);
    assertThat(idempotencyStatus("idem-s5-marker-delete-db")).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("변경 전 해시로 기록된 삭제 요청을 다시 보내면, 기존 응답을 반환하고 중복 삭제하지 않는다")
  void deleteMarker_legacyRequestHash_returnsStoredResponseWithoutDuplicates() {
    // given: 이전 서버가 삭제를 처리한 해시와 응답이 남아 있다.
    seedMarker();
    MarkerRequestContext context = markerContext("idem-s5-marker-delete-db");
    MarkerDeleteServiceRequest request = createDeleteRequest(context);
    MarkerMutationServiceResponse deleted = appMarkerService.delete(request);
    replaceStoredRequestHash(
        context.idempotencyKey(),
        "9a48917e665529861efdd50c08e262a63ad8bf6573651f4897808d9cd3797c1f");

    // when: 같은 삭제 요청을 재전송한다.
    MarkerMutationServiceResponse replayed = appMarkerService.delete(request);

    // then: 삭제 결과를 재사용하고 버전과 이벤트를 추가로 변경하지 않는다.
    assertThat(replayed).usingRecursiveComparison().isEqualTo(deleted);
    assertThat(markerRepository.findById(MARKER_ID).orElseThrow().getStatus()).isEqualTo("DELETED");
    assertThat(markerRepository.findById(MARKER_ID).orElseThrow().getVersion()).isEqualTo(2L);
    assertThat(markerEventPublisher.published()).hasSize(1);
  }

  @ParameterizedTest(name = "{0} 해시 기록")
  @ValueSource(strings = {"JSON", "LEGACY"})
  @DisplayName("같은 삭제 요청 키에 다른 사유를 보내면, 기존 마커·이벤트를 변경하지 않고 거부한다")
  void deleteMarker_sameKeyWithDifferentReason_rejectsWithoutChangingMarkerOrEvents(
      String storedHashFormat) {
    // given: 새 방식 또는 과거 방식으로 처리된 삭제 요청이 있다.
    seedMarker();
    MarkerRequestContext context = markerContext("idem-s5-marker-delete-db");
    MarkerDeleteServiceRequest request = createDeleteRequest(context);
    appMarkerService.delete(request);
    if ("LEGACY".equals(storedHashFormat)) {
      replaceStoredRequestHash(
          context.idempotencyKey(),
          "9a48917e665529861efdd50c08e262a63ad8bf6573651f4897808d9cd3797c1f");
    }

    // when & then: 같은 키에 다른 삭제 사유를 보내면 본문 불일치로 거부한다.
    assertThatThrownBy(
            () -> appMarkerService.delete(request.toBuilder().reason("changed reason").build()))
        .isInstanceOf(IdempotencyMismatchException.class);
    assertThat(markerRepository.findById(MARKER_ID).orElseThrow().getStatus()).isEqualTo("DELETED");
    assertThat(markerRepository.findById(MARKER_ID).orElseThrow().getVersion()).isEqualTo(2L);
    assertThat(markerEventPublisher.published()).hasSize(1);
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
    assertThat(
            photoRepository.countByMarkerIdAndStatusIn(
                MARKER_ID, com.surimap.marker.photo.domain.PhotoStatus.countedStatuses()))
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

  private MarkerUpdateServiceRequest createUpdateRequest(MarkerRequestContext context) {
    return MarkerUpdateServiceRequest.builder()
        .markerId(MARKER_ID)
        .version(1L)
        .memo("updated durable marker")
        .type("NOTE")
        .context(context)
        .build();
  }

  private MarkerDeleteServiceRequest createDeleteRequest(MarkerRequestContext context) {
    return MarkerDeleteServiceRequest.builder()
        .markerId(MARKER_ID)
        .version(1L)
        .reason("duplicate delete")
        .context(context)
        .build();
  }

  private void replaceStoredRequestHash(String idempotencyKey, String bodyHash) {
    jdbcTemplate.update(
        "UPDATE idempotency_record SET request_body_hash = ? WHERE idempotency_key = ?",
        bodyHash,
        idempotencyKey);
  }

  private MarkerCreateServiceRequest markerRequest() {
    return MarkerCreateServiceRequest.builder()
        .incidentId(INCIDENT_ID)
        .opId(OP_ID)
        .type(MarkerType.CLUE.name())
        .location(point())
        .memo("durable marker create")
        .clientTs(CLIENT_TS)
        .clockOffsetMs(0L)
        .build();
  }

  private void seedMarker() {
    markerRepository.insertCreate(
        Marker.builder()
            .incidentId(INCIDENT_ID)
            .id(MARKER_ID)
            .operationalPeriodId(OP_ID)
            .dutyShiftId(null)
            .markerType(MarkerType.CLUE)
            .supportRequestType(null)
            .location(point().toPoint())
            .memo("durable marker")
            .occurredAt(CLIENT_TS)
            .createdByAccountId(ACCOUNT_ID)
            .policePhoneId(POLICE_PHONE_ID)
            .markerSource(MarkerSource.APP)
            .status(MarkerStatus.ACTIVE)
            .version(1L)
            .build());
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

  private String requestBodyHash(String idempotencyKey) {
    return jdbcTemplate
        .queryForObject(
            "SELECT request_body_hash FROM idempotency_record WHERE idempotency_key = ?",
            String.class,
            idempotencyKey)
        .trim();
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
    public UUID requireCreateAccess(UUID incidentId, UUID opId, MarkerRequestContext context) {
      return null;
    }

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
    public PhotoMarkerContext requireUploadUrlAccess(UUID markerId, PhotoRequestContext context) {
      return photoContext();
    }

    @Override
    public PhotoMarkerContext requireAttachAccess(
        UUID markerId, UUID photoId, PhotoRequestContext context) {
      return photoContext();
    }

    private static PhotoMarkerContext photoContext() {
      return new PhotoMarkerContext(INCIDENT_ID, MARKER_ID, OP_ID, POLICE_PHONE_ID, "UPDATED", 1L);
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
