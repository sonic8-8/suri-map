package com.surimap.app.service.photo;

import static com.surimap.account.AccountIdentityCatalog.PRECINCT_TEAM_ID;
import static com.surimap.account.AccountIdentityCatalog.SUPPORT_TEAM_ID;
import static com.surimap.domain.photo.fixture.PhotoFixtures.CHECKSUM_MISMATCH_SHA256;
import static com.surimap.domain.photo.fixture.PhotoFixtures.CHECKSUM_SHA256;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.INCIDENT_ID;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.OP1_ID;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.OP2_ID;
import static com.surimap.policephone.PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

import com.surimap.app.service.photo.request.MarkerCreatePhotoUploadUrlServiceRequest;
import com.surimap.app.service.photo.request.PhotoAttachServiceRequest;
import com.surimap.app.service.photo.request.PhotoUploadUrlServiceRequest;
import com.surimap.app.service.photo.response.PhotoAttachServiceResponse;
import com.surimap.app.service.photo.response.PhotoUploadUrlServiceResponse;
import com.surimap.client.storage.MockObjectStorageAdapter;
import com.surimap.client.storage.ObjectStoragePort.ObjectMetadata;
import com.surimap.domain.marker.Marker;
import com.surimap.domain.marker.MarkerMapper;
import com.surimap.domain.photo.MarkerPhoto;
import com.surimap.domain.photo.PhotoMapper;
import com.surimap.domain.photo.PhotoStatus;
import com.surimap.global.auth.SuriMapAuthentication;
import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.domain.fixture.MarkerGeometryFixtures;
import com.surimap.sync.idempotency.IdempotencyMismatchException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(properties = "surimap.object-storage.provider=mock")
class PhotoServiceTest extends PostGisIntegrationTestSupport {

  private static final UUID BEFORE_CREATION_MARKER_ID =
      UUID.fromString("55555555-5555-5555-5555-555555550340");
  private static final String BEFORE_CREATION_IDEMPOTENCY_KEY =
      "idem-marker-create-photo-upload-001";
  private static final String BEFORE_CREATION_LEGACY_REQUEST_HASH =
      "7a799287a2b1a1bd573cdc694279050f4831113194d0449b609cadb9f5ab4769";

  private static final UUID MARKER_ID = UUID.fromString("4ca60e44-9cfe-410b-8794-2983baac0eac");
  private static final UUID OTHER_MARKER_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000202");
  private static final UUID DUTY_SHIFT_ID = UUID.fromString("33333333-3333-3333-3333-333333330001");
  private static final UUID OTHER_REGISTERED_POLICE_PHONE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000301");
  private static final String IDEMPOTENCY_KEY = "idem-photo-upload-url";
  private static final String ATTACH_IDEMPOTENCY_KEY = "idem-photo-attach";
  private static final String LEGACY_UPLOAD_REQUEST_HASH =
      "755b32930089d3b6c2a682c246a29fdb06aff28ca7d523ffe1df4536bfbb44ed";
  private static final String LEGACY_ATTACH_REQUEST_BODY =
      "PhotoAttachRequest[sizeBytes=1024, contentType=image/jpeg, width=640, height=480, "
          + "checksumSha256=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef]";

  @Autowired private PhotoService photoService;
  @Autowired private MarkerMapper markerMapper;
  @Autowired private PhotoMapper photoMapper;
  @MockitoSpyBean private MockObjectStorageAdapter objectStorage;
  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM photo WHERE marker_id = ?", BEFORE_CREATION_MARKER_ID);
    jdbcTemplate.update("DELETE FROM marker WHERE id = ?", BEFORE_CREATION_MARKER_ID);
    jdbcTemplate.update(
        "DELETE FROM idempotency_record WHERE idempotency_key = ?",
        BEFORE_CREATION_IDEMPOTENCY_KEY);
    // 기존 PostGIS 테스트 DB에서 이번 사건·마커·요청 키에 해당하는 데이터만 준비한다.
    jdbcTemplate.update("DELETE FROM photo WHERE marker_id IN (?, ?)", MARKER_ID, OTHER_MARKER_ID);
    jdbcTemplate.update("DELETE FROM marker WHERE id IN (?, ?)", MARKER_ID, OTHER_MARKER_ID);
    jdbcTemplate.update("DELETE FROM event_dispatch_job WHERE incident_id = ?", INCIDENT_ID);
    jdbcTemplate.update(
        """
        DELETE FROM idempotency_record
        WHERE idempotency_key IN (?, ?) OR idempotency_key LIKE ? OR idempotency_key LIKE ?
        """,
        IDEMPOTENCY_KEY,
        ATTACH_IDEMPOTENCY_KEY,
        IDEMPOTENCY_KEY + "-%",
        ATTACH_IDEMPOTENCY_KEY + "-%");
    jdbcTemplate.update("DELETE FROM duty_shift WHERE operational_period_id = ?", OP1_ID);
    jdbcTemplate.update("DELETE FROM incident_assignment WHERE incident_id = ?", INCIDENT_ID);
    jdbcTemplate.update(
        "UPDATE operational_period SET status = 'ENDED', ended_at = NOW() WHERE id = ?", OP2_ID);
    objectStorage.clear();
    jdbcTemplate.update(
        """
        INSERT INTO incident (id, source_incident_id, title, status, opened_at, version, created_at, updated_at)
        VALUES (?, ?, 'Photo access fixture', 'OPEN', NOW(), 1, NOW(), NOW())
        ON CONFLICT (id) DO UPDATE SET status = 'OPEN', closed_at = NULL, closed_by_account_id = NULL
        """,
        INCIDENT_ID,
        INCIDENT_ID);
    jdbcTemplate.update(
        """
        INSERT INTO operational_period (id, incident_id, sequence_number, status, reason,
            started_by_account_id, started_at, version, created_at, updated_at)
        VALUES (?, ?, 1, 'ACTIVE', 'INITIAL', ?, NOW(), 1, NOW(), NOW())
        ON CONFLICT (id) DO UPDATE SET status = 'ACTIVE', ended_at = NULL
        """,
        OP1_ID,
        INCIDENT_ID,
        PRECINCT_TEAM_ID);
    UUID assignmentId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO incident_assignment (id, incident_id, account_id, incident_role,
            assigned_at, created_at, updated_at)
        VALUES (?, ?, ?, 'MEMBER', NOW(), NOW(), NOW())
        """,
        assignmentId,
        INCIDENT_ID,
        PRECINCT_TEAM_ID);
    jdbcTemplate.update(
        """
        INSERT INTO duty_shift (id, operational_period_id, incident_assignment_id, police_phone_id,
            status, started_by_account_id, started_at, version, created_at, updated_at)
        VALUES (?, ?, ?, ?, 'ACTIVE', ?, NOW(), 1, NOW(), NOW())
        """,
        DUTY_SHIFT_ID,
        OP1_ID,
        assignmentId,
        ASSIGNED_POLICE_PHONE_ID,
        PRECINCT_TEAM_ID);
    insertMarker(MARKER_ID);
  }

  private void insertMarker(UUID markerId) {
    markerMapper.insertCreate(
        Marker.builder()
            .id(markerId)
            .incidentId(INCIDENT_ID)
            .operationalPeriodId(OP1_ID)
            .dutyShiftId(DUTY_SHIFT_ID)
            .createdByAccountId(PRECINCT_TEAM_ID)
            .policePhoneId(ASSIGNED_POLICE_PHONE_ID)
            .markerType(MarkerType.CLUE)
            .location(MarkerGeometryFixtures.VALID_MARKER_POINT)
            .occurredAt(Instant.parse("2026-04-28T00:05:00Z"))
            .markerSource(MarkerSource.APP)
            .status(MarkerStatus.ACTIVE)
            .version(1L)
            .build());
  }

  @ParameterizedTest(name = "파일 형식: {0}, 크기: {1}")
  @CsvSource(
      value = {
        "null, 1024",
        "'', 1024",
        "' ', 1024",
        "image/gif, 1024",
        "IMAGE/JPEG, 1024",
        "null, 0",
        "image/gif, 10485761"
      },
      nullValues = "null")
  @DisplayName("업로드할 사진 형식이 없거나 지원하지 않는 값이면, 입력 오류로 거부하고 사진을 저장하지 않는다")
  void createUploadUrl_invalidContentType_rejectsWithoutSavingPhoto(
      String contentType, long sizeBytes) {
    // given: 사진 형식이 없거나 허용 목록에 없는 값이며, 크기도 잘못될 수 있다.
    PhotoUploadUrlServiceRequest request =
        createUploadRequest().toBuilder().contentType(contentType).sizeBytes(sizeBytes).build();

    // when & then: 실제 서비스에서 형식 입력 오류를 반환한다.
    assertThatThrownBy(() -> photoService.createUploadUrl(request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> {
              assertThat(exception.getErrorCode().getError())
                  .isEqualTo("invalid_photo_content_type");
              assertThat(exception.getErrorCode().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT id FROM photo WHERE marker_id = ?", UUID.class, MARKER_ID))
        .isEmpty();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT idempotency_status FROM idempotency_record WHERE idempotency_key = ?",
                String.class,
                IDEMPOTENCY_KEY))
        .isEmpty();
    assertUnchangedMarkerAndNoAttachRecord();
  }

  @ParameterizedTest(name = "파일 형식: {0}, 크기: {1}")
  @CsvSource(
      value = {
        "null, 1024",
        "'', 1024",
        "' ', 1024",
        "image/gif, 1024",
        "IMAGE/JPEG, 1024",
        "null, 0",
        "image/gif, 10485761"
      },
      nullValues = "null")
  @DisplayName("첨부할 사진 형식이 없거나 지원하지 않는 값이면, 입력 오류로 거부하고 대기 사진을 유지한다")
  void attach_invalidContentType_rejectsWithoutChangingPhoto(String contentType, long sizeBytes) {
    // given: 정상 업로드한 대기 사진이 있지만, 첨부 요청의 형식과 크기가 잘못될 수 있다.
    MarkerPhoto photo = createPendingPhoto();
    objectStorage.simulateUpload(photo.getObjectKey());
    PhotoAttachServiceRequest request =
        PhotoAttachServiceRequest.builder()
            .sizeBytes(sizeBytes)
            .contentType(contentType)
            .width(640)
            .height(480)
            .checksumSha256(CHECKSUM_SHA256)
            .build();

    // when & then: 실제 서비스에서 입력 오류를 반환하고 사진·마커·요청 기록을 변경하지 않는다.
    assertThatThrownBy(
            () ->
                photoService.attach(
                    request.toBuilder()
                        .markerId(MARKER_ID)
                        .photoId(photo.getId())
                        .context(createAttachContext())
                        .build()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> {
              assertThat(exception.getErrorCode().getError())
                  .isEqualTo("invalid_photo_content_type");
              assertThat(exception.getErrorCode().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
    assertPhotoNotAttached(photo, PhotoStatus.PENDING_UPLOAD, 1L);
    assertUnchangedMarkerAndNoAttachRecord();
  }

  @Test
  @DisplayName("현재 수색 차수에서 근무 중인 작성자가 요청하면, 사진 업로드 주소와 대기 중인 사진을 생성한다")
  void createUploadUrl_assignedAuthor_savesPendingPhoto() {
    // given: 배정된 계정의 활성 근무교대와 같은 계정이 만든 현장 마커가 있다.
    Instant requestedAt = Instant.now();

    // when: 실제 사진 서비스에서 업로드 주소를 발급한다.
    PhotoUploadUrlServiceResponse response = photoService.createUploadUrl(createUploadRequest());

    // then: 업로드 대기 사진을 저장하고 마커 자체는 아직 변경하지 않는다.
    MarkerPhoto photo = photoMapper.findById(response.getPhotoId()).orElseThrow();
    assertThat(photo.getMarkerId()).isEqualTo(MARKER_ID);
    assertThat(photo.getStatus()).isEqualTo(PhotoStatus.PENDING_UPLOAD);
    assertThat(photo.getContentType()).isEqualTo("image/jpeg");
    assertThat(photo.getSizeBytes()).isEqualTo(1024L);
    assertThat(photo.getChecksumSha256()).isEqualTo(CHECKSUM_SHA256);
    String expectedObjectKey =
        "markers/" + INCIDENT_ID + "/" + MARKER_ID + "/" + response.getPhotoId() + ".jpg";
    assertThat(photo.getObjectKey()).isEqualTo(expectedObjectKey);
    assertThat(response.getUploadUrl())
        .isEqualTo("http://127.0.0.1:18080/mock-upload/" + expectedObjectKey);
    assertThat(response.getExpiresAt())
        .isBetween(requestedAt.plusSeconds(15 * 60), Instant.now().plusSeconds(15 * 60));
    assertThat(response.getMaxSizeBytes()).isEqualTo(10_485_760L);
    assertThat(response.getVersion()).isEqualTo(1L);
    assertThat(markerMapper.findById(MARKER_ID).orElseThrow().getVersion()).isEqualTo(1L);
  }

  @Test
  @DisplayName("마커에 업로드 대기 사진이 10개 있으면, 11번째 사진의 업로드 주소 발급을 거부한다")
  void createUploadUrl_tenPendingPhotos_rejectsWithoutAddingPhoto() {
    // given: 서로 다른 요청 키로 사진 10개의 업로드 주소를 발급했다.
    SuriMapAuthentication authentication =
        createContext(ASSIGNED_POLICE_PHONE_ID).getAuthentication();
    for (int index = 0; index < 10; index++) {
      photoService.createUploadUrl(
          createUploadRequest().toBuilder()
              .context(new PhotoRequestContext(authentication, IDEMPOTENCY_KEY + "-" + index))
              .build());
    }

    // when: 11번째 사진의 업로드 주소를 새 요청 키로 발급하려 한다.
    assertThatThrownBy(() -> photoService.createUploadUrl(createUploadRequest()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("photo_limit_exceeded", HttpStatus.PAYLOAD_TOO_LARGE);

    // then: 기존 대기 사진 10개만 남고 마커 버전은 바뀌지 않는다.
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT status FROM photo WHERE marker_id = ?", String.class, MARKER_ID))
        .hasSize(10)
        .containsOnly("PENDING_UPLOAD");
    assertThat(markerMapper.findById(MARKER_ID).orElseThrow().getVersion()).isEqualTo(1L);
  }

  @Test
  @DisplayName("사진이 허용 크기 10,485,760바이트를 넘으면, 업로드 주소를 발급하거나 사진을 저장하지 않는다")
  void createUploadUrl_oversizedPhoto_rejectsWithoutSavingPhoto() {
    // given: 파일 크기가 허용값보다 1바이트 큰 사진이다.
    PhotoUploadUrlServiceRequest request =
        createUploadRequest().toBuilder().sizeBytes(10_485_761L).build();

    // when & then: 크기 초과 요청을 거부하고 사진·마커·이벤트를 변경하지 않는다.
    assertUploadRejected(request, "photo_limit_exceeded", HttpStatus.PAYLOAD_TOO_LARGE);
  }

  @Test
  @DisplayName("같은 작성자가 다른 등록 업무폰으로 사진을 첨부하면, 요청한 업무폰을 수정 이벤트에 기록한다")
  void attach_sameAuthorOnAnotherPhone_savesPhotoAndEventWithRequestPhone() {
    // given: 현재 계정은 마커 작성자이며 요청에 다른 등록 업무폰을 사용한다.
    PhotoUploadUrlServiceResponse response =
        photoService.createUploadUrl(
            createUploadRequest().toBuilder()
                .context(createContext(OTHER_REGISTERED_POLICE_PHONE_ID))
                .build());
    MarkerPhoto photo = photoMapper.findById(response.getPhotoId()).orElseThrow();
    objectStorage.simulateUpload(photo.getObjectKey());
    PhotoRequestContext attachContext =
        new PhotoRequestContext(
            new SuriMapAuthentication(PRECINCT_TEAM_ID, "APP", OTHER_REGISTERED_POLICE_PHONE_ID),
            ATTACH_IDEMPOTENCY_KEY);

    // when: 실제 사진 서비스를 통해 업로드한 사진을 마커에 첨부한다.
    PhotoAttachServiceResponse attached =
        photoService.attach(
            PhotoAttachServiceRequest.builder()
                .sizeBytes(1024L)
                .contentType("image/jpeg")
                .width(640)
                .height(480)
                .checksumSha256(CHECKSUM_SHA256)
                .markerId(MARKER_ID)
                .photoId(photo.getId())
                .context(attachContext)
                .build());

    // then: 사진·부모 마커를 함께 변경하고 이번 요청의 업무폰을 이벤트에 기록한다.
    assertThat(attached.getStatus()).isEqualTo("ATTACHED");
    assertThat(attached.getPhotoId()).isEqualTo(photo.getId());
    assertThat(attached.getVersion()).isEqualTo(2L);
    assertThat(attached.getMarkerId()).isEqualTo(MARKER_ID);
    assertThat(attached.getMarkerVersion()).isEqualTo(2L);
    MarkerPhoto savedPhoto = photoMapper.findById(photo.getId()).orElseThrow();
    assertThat(savedPhoto.getStatus()).isEqualTo(PhotoStatus.ATTACHED);
    assertThat(savedPhoto.getVersion()).isEqualTo(2L);
    assertThat(savedPhoto.getObjectKey()).isEqualTo(photo.getObjectKey());
    assertThat(savedPhoto.getWidth()).isEqualTo(640);
    assertThat(savedPhoto.getHeight()).isEqualTo(480);
    assertThat(savedPhoto.getAttachedAt()).isNotNull();
    assertThat(markerMapper.findById(MARKER_ID).orElseThrow().getVersion()).isEqualTo(2L);
    assertThat(
            jdbcTemplate.queryForMap(
                """
                SELECT event_id, event_type, incident_id, source_entity_type, source_entity_id,
                    payload_format_version, payload ->> 'id' AS payload_marker_id,
                    payload ->> 'incidentId' AS payload_incident_id,
                    payload ->> 'opId' AS op_id, payload ->> 'policePhoneId' AS police_phone_id,
                    payload ->> 'status' AS marker_status, payload ->> 'version' AS marker_version,
                    jsonb_typeof(payload -> 'version') AS marker_version_type,
                    payload -> 'photoDelta' ->> 'photoId' AS photo_id,
                    payload -> 'photoDelta' ->> 'status' AS photo_status,
                    payload -> 'photoDelta' ->> 'version' AS photo_version,
                    jsonb_typeof(payload -> 'photoDelta' -> 'version') AS photo_version_type
                FROM event_dispatch_job WHERE source_entity_id = ?
                """,
                MARKER_ID))
        .containsEntry("event_id", UUID.fromString("efc021e0-8e1b-3c84-9f1e-7ef2a508ff52"))
        .containsEntry("event_type", "MARKER_UPDATED")
        .containsEntry("incident_id", INCIDENT_ID)
        .containsEntry("source_entity_type", "marker")
        .containsEntry("source_entity_id", MARKER_ID)
        .containsEntry("payload_format_version", 1)
        .containsEntry("payload_marker_id", MARKER_ID.toString())
        .containsEntry("payload_incident_id", INCIDENT_ID.toString())
        .containsEntry("op_id", OP1_ID.toString())
        .containsEntry("police_phone_id", OTHER_REGISTERED_POLICE_PHONE_ID.toString())
        .containsEntry("marker_status", "UPDATED")
        .containsEntry("marker_version", "2")
        .containsEntry("marker_version_type", "number")
        .containsEntry("photo_id", photo.getId().toString())
        .containsEntry("photo_status", "ATTACHED")
        .containsEntry("photo_version", "2")
        .containsEntry("photo_version_type", "number");
  }

  @ParameterizedTest(name = "{0} 해시 기록")
  @ValueSource(strings = {"JSON", "LEGACY"})
  @DisplayName("같은 업로드 주소 요청을 재전송하면, 저장된 응답을 반환하고 대기 사진을 추가하지 않는다")
  void createUploadUrl_sameRequest_returnsStoredResponseWithoutAnotherPhoto(
      String storedHashFormat) {
    // given: 업로드 주소와 대기 사진이 이미 저장된 요청이다.
    PhotoRequestContext context = createContext(ASSIGNED_POLICE_PHONE_ID);
    PhotoUploadUrlServiceResponse first =
        photoService.createUploadUrl(createUploadRequest().toBuilder().context(context).build());
    assertThat(readRequestBodyHash(IDEMPOTENCY_KEY))
        .isEqualTo("889df90ae1c14800e18bc78211742114a3763c7237ee2c07139ef8358abf1d98");
    if ("LEGACY".equals(storedHashFormat)) {
      replaceStoredRequestHash(IDEMPOTENCY_KEY, LEGACY_UPLOAD_REQUEST_HASH);
    }
    String storedRequestHash = readRequestBodyHash(IDEMPOTENCY_KEY);

    // when: 같은 키와 본문으로 업로드 주소를 다시 요청한다.
    PhotoUploadUrlServiceResponse repeated =
        photoService.createUploadUrl(createUploadRequest().toBuilder().context(context).build());

    // then: 주소·사진 ID·만료 시각을 재사용하고 DB에는 대기 사진 하나만 남는다.
    assertThat(repeated).usingRecursiveComparison().isEqualTo(first);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT id FROM photo WHERE marker_id = ?", UUID.class, MARKER_ID))
        .containsExactly(first.getPhotoId());
    assertThat(photoMapper.findById(first.getPhotoId()).orElseThrow().getStatus())
        .isEqualTo(PhotoStatus.PENDING_UPLOAD);
    assertThat(markerMapper.findById(MARKER_ID).orElseThrow().getVersion()).isEqualTo(1L);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT event_type FROM event_dispatch_job WHERE source_entity_id = ?",
                String.class,
                MARKER_ID))
        .isEmpty();
    assertCompletedRequest(IDEMPOTENCY_KEY);
    assertThat(readRequestBodyHash(IDEMPOTENCY_KEY)).isEqualTo(storedRequestHash);
  }

  @ParameterizedTest(name = "{0} 해시 기록")
  @ValueSource(strings = {"JSON", "LEGACY"})
  @DisplayName("체크섬 없는 업로드 요청을 재전송하면, 기존 해시와 응답을 유지하고 사진을 추가하지 않는다")
  void createUploadUrl_sameRequestWithoutChecksum_returnsStoredResponse(String storedHashFormat) {
    // given: 체크섬이 없는 요청을 현재 또는 과거 해시로 기록했다.
    PhotoUploadUrlServiceRequest request =
        createUploadRequest().toBuilder().checksumSha256(null).build();
    PhotoUploadUrlServiceResponse first = photoService.createUploadUrl(request);
    assertThat(readRequestBodyHash(IDEMPOTENCY_KEY))
        .isEqualTo("baba74fa35c232ab4ab7eef3e4109d7cbd315b036f1e98dcc9765b2104129b0e");
    if ("LEGACY".equals(storedHashFormat)) {
      replaceStoredRequestHash(
          IDEMPOTENCY_KEY, "2055f961b43bc5b19d01765f45acce69f586c3d5ee67bc3061823afd0499164c");
    }
    String storedRequestHash = readRequestBodyHash(IDEMPOTENCY_KEY);

    // when: 새 Service Request 객체로 같은 요청을 다시 전달한다.
    PhotoUploadUrlServiceResponse repeated =
        photoService.createUploadUrl(request.toBuilder().build());

    // then: DTO가 바뀌어도 체크섬의 null 표현과 기존 응답을 유지한다.
    assertThat(repeated).usingRecursiveComparison().isEqualTo(first);
    assertThat(readRequestBodyHash(IDEMPOTENCY_KEY)).isEqualTo(storedRequestHash);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT id FROM photo WHERE marker_id = ?", UUID.class, MARKER_ID))
        .containsExactly(first.getPhotoId());
    assertThat(photoMapper.findById(first.getPhotoId()).orElseThrow().getChecksumSha256()).isNull();
  }

  @ParameterizedTest(name = "{0} 해시 기록")
  @ValueSource(strings = {"JSON", "LEGACY"})
  @DisplayName("처리한 업로드 주소 요청 키에 다른 파일 크기를 보내면, 거부하고 기존 대기 사진을 유지한다")
  void createUploadUrl_sameKeyWithDifferentSize_rejectsWithoutChangingPhoto(
      String storedHashFormat) {
    // given: 현재 또는 과거 해시로 기록된 업로드 주소 요청과 대기 사진이 있다.
    PhotoRequestContext context = createContext(ASSIGNED_POLICE_PHONE_ID);
    PhotoUploadUrlServiceResponse first =
        photoService.createUploadUrl(createUploadRequest().toBuilder().context(context).build());
    MarkerPhoto photo = photoMapper.findById(first.getPhotoId()).orElseThrow();
    if ("LEGACY".equals(storedHashFormat)) {
      replaceStoredRequestHash(IDEMPOTENCY_KEY, LEGACY_UPLOAD_REQUEST_HASH);
    }
    String storedRequestHash = readRequestBodyHash(IDEMPOTENCY_KEY);

    // when: 같은 요청 키를 유지하고 파일 크기만 바꿔 보낸다.
    PhotoUploadUrlServiceRequest changed =
        createUploadRequest().toBuilder().sizeBytes(2048L).context(context).build();
    assertThatThrownBy(() -> photoService.createUploadUrl(changed))
        .isInstanceOf(IdempotencyMismatchException.class);

    // then: 사진을 추가하거나 변경하지 않고 마커·이벤트·기존 요청 기록도 유지한다.
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT id FROM photo WHERE marker_id = ?", UUID.class, MARKER_ID))
        .containsExactly(first.getPhotoId());
    assertPhotoNotAttached(photo, PhotoStatus.PENDING_UPLOAD, 1L);
    assertThat(photoMapper.findById(photo.getId()).orElseThrow().getSizeBytes()).isEqualTo(1024L);
    assertThat(markerMapper.findById(MARKER_ID).orElseThrow().getVersion()).isEqualTo(1L);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT event_type FROM event_dispatch_job WHERE source_entity_id = ?",
                String.class,
                MARKER_ID))
        .isEmpty();
    assertCompletedRequest(IDEMPOTENCY_KEY);
    assertThat(readRequestBodyHash(IDEMPOTENCY_KEY)).isEqualTo(storedRequestHash);
  }

  @ParameterizedTest(name = "{0} 해시 기록")
  @ValueSource(strings = {"JSON", "LEGACY"})
  @DisplayName("같은 사진 첨부 요청을 재전송하면, 기존 응답을 반환하고 사진·마커·이벤트를 중복 변경하지 않는다")
  void attach_sameRequest_returnsStoredResponseWithoutDuplicateChanges(String storedHashFormat)
      throws Exception {
    // given: 업로드된 사진의 첨부 요청을 한 번 처리했다.
    PhotoUploadUrlServiceResponse upload = photoService.createUploadUrl(createUploadRequest());
    MarkerPhoto photo = photoMapper.findById(upload.getPhotoId()).orElseThrow();
    objectStorage.simulateUpload(photo.getObjectKey());
    PhotoRequestContext context =
        new PhotoRequestContext(
            new SuriMapAuthentication(PRECINCT_TEAM_ID, "APP", ASSIGNED_POLICE_PHONE_ID),
            ATTACH_IDEMPOTENCY_KEY);
    PhotoAttachServiceRequest request =
        PhotoAttachServiceRequest.builder()
            .sizeBytes(1024L)
            .contentType("image/jpeg")
            .width(640)
            .height(480)
            .checksumSha256(CHECKSUM_SHA256)
            .build();
    PhotoAttachServiceResponse first =
        photoService.attach(
            request.toBuilder()
                .markerId(MARKER_ID)
                .photoId(photo.getId())
                .context(context)
                .build());
    assertThat(readRequestBodyHash(ATTACH_IDEMPOTENCY_KEY))
        .isEqualTo("9cf76d37f2037dc74968d16e919b179faea21c037cd429367200418468e28eb1");
    if ("LEGACY".equals(storedHashFormat)) {
      replaceStoredAttachRequestHash(photo.getId(), LEGACY_ATTACH_REQUEST_BODY);
    }
    String storedRequestHash = readRequestBodyHash(ATTACH_IDEMPOTENCY_KEY);

    // when: 같은 키와 본문으로 사진 첨부를 다시 요청한다.
    PhotoAttachServiceResponse repeated =
        photoService.attach(
            request.toBuilder()
                .markerId(MARKER_ID)
                .photoId(photo.getId())
                .context(context)
                .build());

    // then: 기존 응답을 재사용하며 사진·마커 버전은 2, 수정 이벤트는 하나로 유지된다.
    assertThat(repeated).usingRecursiveComparison().isEqualTo(first);
    MarkerPhoto savedPhoto = photoMapper.findById(photo.getId()).orElseThrow();
    assertThat(savedPhoto.getStatus()).isEqualTo(PhotoStatus.ATTACHED);
    assertThat(savedPhoto.getVersion()).isEqualTo(2L);
    Marker savedMarker = markerMapper.findById(MARKER_ID).orElseThrow();
    assertThat(savedMarker.getStatus()).isEqualTo("UPDATED");
    assertThat(savedMarker.getVersion()).isEqualTo(2L);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT event_type FROM event_dispatch_job WHERE source_entity_id = ?",
                String.class,
                MARKER_ID))
        .containsExactly("MARKER_UPDATED");
    assertCompletedRequest(ATTACH_IDEMPOTENCY_KEY);
    assertThat(readRequestBodyHash(ATTACH_IDEMPOTENCY_KEY)).isEqualTo(storedRequestHash);
  }

  @ParameterizedTest(name = "{0} 해시 기록")
  @ValueSource(strings = {"JSON", "LEGACY"})
  @DisplayName("처리한 사진 첨부 요청 키에 다른 가로 길이를 보내면, 거부하고 사진·마커·이벤트를 유지한다")
  void attach_sameKeyWithDifferentWidth_rejectsWithoutChangingPhotoMarkerOrEvents(
      String storedHashFormat) throws Exception {
    // given: 사진 첨부가 완료되고 현재 또는 과거 해시와 응답이 저장되어 있다.
    MarkerPhoto photo = createPendingPhoto();
    objectStorage.simulateUpload(photo.getObjectKey());
    PhotoRequestContext context = createAttachContext();
    photoService.attach(
        createAttachRequest().toBuilder()
            .markerId(MARKER_ID)
            .photoId(photo.getId())
            .context(context)
            .build());
    if ("LEGACY".equals(storedHashFormat)) {
      replaceStoredAttachRequestHash(photo.getId(), LEGACY_ATTACH_REQUEST_BODY);
    }
    String storedRequestHash = readRequestBodyHash(ATTACH_IDEMPOTENCY_KEY);

    // when: 같은 요청 키에 가로 길이만 다른 첨부 요청을 보낸다.
    PhotoAttachServiceRequest changed =
        PhotoAttachServiceRequest.builder()
            .sizeBytes(1024L)
            .contentType("image/jpeg")
            .width(641)
            .height(480)
            .checksumSha256(CHECKSUM_SHA256)
            .build();
    assertThatThrownBy(
            () ->
                photoService.attach(
                    changed.toBuilder()
                        .markerId(MARKER_ID)
                        .photoId(photo.getId())
                        .context(context)
                        .build()))
        .isInstanceOf(IdempotencyMismatchException.class);

    // then: 최초 첨부 정보·버전·수정 이벤트와 요청 기록을 그대로 유지한다.
    MarkerPhoto savedPhoto = photoMapper.findById(photo.getId()).orElseThrow();
    assertThat(savedPhoto.getStatus()).isEqualTo(PhotoStatus.ATTACHED);
    assertThat(savedPhoto.getVersion()).isEqualTo(2L);
    assertThat(savedPhoto.getWidth()).isEqualTo(640);
    assertThat(savedPhoto.getHeight()).isEqualTo(480);
    assertThat(markerMapper.findById(MARKER_ID).orElseThrow().getVersion()).isEqualTo(2L);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT event_type FROM event_dispatch_job WHERE source_entity_id = ?",
                String.class,
                MARKER_ID))
        .containsExactly("MARKER_UPDATED");
    assertCompletedRequest(ATTACH_IDEMPOTENCY_KEY);
    assertThat(readRequestBodyHash(ATTACH_IDEMPOTENCY_KEY)).isEqualTo(storedRequestHash);
  }

  @Test
  @DisplayName("가로·세로 길이와 체크섬이 없는 과거 첨부 요청을 재전송하면, 기존 응답을 반환하고 중복 처리하지 않는다")
  void attach_legacyRequestWithoutOptionalMetadata_returnsStoredResponse() throws Exception {
    // given: 선택 항목 없이 첨부한 사진과 과거 형식의 요청 해시가 남아 있다.
    PhotoUploadUrlServiceResponse upload =
        photoService.createUploadUrl(
            createUploadRequest().toBuilder().checksumSha256(null).build());
    MarkerPhoto photo = photoMapper.findById(upload.getPhotoId()).orElseThrow();
    objectStorage.simulateUpload(photo.getObjectKey());
    PhotoAttachServiceRequest request =
        PhotoAttachServiceRequest.builder()
            .sizeBytes(1024L)
            .contentType("image/jpeg")
            .width(null)
            .height(null)
            .checksumSha256(null)
            .build();
    PhotoAttachServiceResponse first =
        photoService.attach(
            request.toBuilder()
                .markerId(MARKER_ID)
                .photoId(photo.getId())
                .context(createAttachContext())
                .build());
    replaceStoredAttachRequestHash(
        photo.getId(),
        "PhotoAttachRequest[sizeBytes=1024, contentType=image/jpeg, width=null, height=null,"
            + " checksumSha256=null]");
    String storedRequestHash = readRequestBodyHash(ATTACH_IDEMPOTENCY_KEY);

    // when: 선택 항목이 없는 같은 요청을 다시 보낸다.
    PhotoAttachServiceResponse repeated =
        photoService.attach(
            request.toBuilder()
                .markerId(MARKER_ID)
                .photoId(photo.getId())
                .context(createAttachContext())
                .build());

    // then: 기존 첨부 결과를 재사용하며 사진·마커 버전과 이벤트를 추가하지 않는다.
    assertThat(repeated).usingRecursiveComparison().isEqualTo(first);
    MarkerPhoto savedPhoto = photoMapper.findById(photo.getId()).orElseThrow();
    assertThat(savedPhoto.getStatus()).isEqualTo(PhotoStatus.ATTACHED);
    assertThat(savedPhoto.getVersion()).isEqualTo(2L);
    assertThat(savedPhoto.getWidth()).isNull();
    assertThat(savedPhoto.getHeight()).isNull();
    assertThat(savedPhoto.getChecksumSha256()).isNull();
    assertThat(markerMapper.findById(MARKER_ID).orElseThrow().getVersion()).isEqualTo(2L);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT event_type FROM event_dispatch_job WHERE source_entity_id = ?",
                String.class,
                MARKER_ID))
        .containsExactly("MARKER_UPDATED");
    assertCompletedRequest(ATTACH_IDEMPOTENCY_KEY);
    assertThat(readRequestBodyHash(ATTACH_IDEMPOTENCY_KEY)).isEqualTo(storedRequestHash);
  }

  @Test
  @DisplayName("종료된 사건에 업로드 주소를 요청하면, 사진을 저장하지 않고 incident_closed 오류를 반환한다")
  void createUploadUrl_closedIncident_rejectsWithoutSavingPhoto() {
    // given: 배정과 마커는 유지되지만 사건은 종료되어 있다.
    jdbcTemplate.update(
        "UPDATE incident SET status = 'CLOSED', closed_at = NOW() WHERE id = ?", INCIDENT_ID);

    // when & then: 실제 사건 상태로 업로드를 거부하고 사진·마커·이벤트를 변경하지 않는다.
    assertUploadRejected("incident_closed", HttpStatus.CONFLICT);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT idempotency_status FROM idempotency_record WHERE idempotency_key = ?",
                String.class,
                IDEMPOTENCY_KEY))
        .isEmpty();
  }

  @Test
  @DisplayName("업로드 주소가 만료된 사진을 첨부하면, 요청을 거부하고 사진을 실패 상태로 저장한다")
  void attach_expiredUploadUrl_savesFailedPhoto() {
    // given: 업로드는 완료됐지만 사진의 업로드 주소가 만료되었다.
    PhotoUploadUrlServiceResponse upload = photoService.createUploadUrl(createUploadRequest());
    MarkerPhoto photo = photoMapper.findById(upload.getPhotoId()).orElseThrow();
    objectStorage.simulateUpload(photo.getObjectKey());
    jdbcTemplate.update(
        "UPDATE photo SET upload_url_expires_at = NOW() - INTERVAL '1 minute' WHERE id = ?",
        photo.getId());
    PhotoRequestContext context =
        new PhotoRequestContext(
            new SuriMapAuthentication(PRECINCT_TEAM_ID, "APP", ASSIGNED_POLICE_PHONE_ID),
            ATTACH_IDEMPOTENCY_KEY);

    // when: 만료된 사진을 첨부하려 한다.
    assertThatThrownBy(
            () ->
                photoService.attach(
                    PhotoAttachServiceRequest.builder()
                        .sizeBytes(1024L)
                        .contentType("image/jpeg")
                        .width(640)
                        .height(480)
                        .checksumSha256(CHECKSUM_SHA256)
                        .markerId(MARKER_ID)
                        .photoId(photo.getId())
                        .context(context)
                        .build()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("write_conflict", HttpStatus.CONFLICT);

    // then: 만료된 사진은 실패 상태로 남겨 업로드 대기 사진과 구분한다.
    assertPhotoNotAttached(photo, PhotoStatus.FAILED, 2L);
    assertUnchangedMarkerAndNoAttachRecord();
  }

  @Test
  @DisplayName("DB에 없는 사진을 첨부하면, 파일이 있어도 요청을 거부하고 마커를 변경하지 않는다")
  void attach_missingPhotoRow_rejectsWithoutChangingMarker() {
    // given: 저장소에 파일만 있고 업로드 주소 발급으로 생성한 사진 행은 없다.
    UUID photoId = UUID.fromString("00000000-0000-0000-0000-000000000303");
    objectStorage.simulateUpload(
        "markers/" + INCIDENT_ID + "/" + MARKER_ID + "/" + photoId + ".jpg");

    // when: DB에 없는 사진을 첨부하려 한다.
    assertThatThrownBy(
            () ->
                photoService.attach(
                    createAttachRequest().toBuilder()
                        .markerId(MARKER_ID)
                        .photoId(photoId)
                        .context(createAttachContext())
                        .build()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("write_conflict", HttpStatus.CONFLICT);

    // then: 사진 행을 새로 만들거나 마커·이벤트·요청 처리 기록을 변경하지 않는다.
    assertThat(photoMapper.findById(photoId)).isEmpty();
    assertUnchangedMarkerAndNoAttachRecord();
  }

  @Test
  @DisplayName("다른 마커에 발급된 사진을 첨부하면, 요청을 거부하고 원래 사진과 두 마커를 유지한다")
  void attach_photoForAnotherMarker_rejectsWithoutChangingPhotoOrMarkers() {
    // given: 같은 작성자의 마커가 둘 있고 사진은 첫 번째 마커에 발급되었다.
    insertMarker(OTHER_MARKER_ID);
    MarkerPhoto photo = createPendingPhoto();
    objectStorage.simulateUpload(photo.getObjectKey());

    // when: 두 번째 마커에 첫 번째 마커의 사진을 첨부하려 한다.
    assertThatThrownBy(
            () ->
                photoService.attach(
                    createAttachRequest().toBuilder()
                        .markerId(OTHER_MARKER_ID)
                        .photoId(photo.getId())
                        .context(createAttachContext())
                        .build()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("write_conflict", HttpStatus.CONFLICT);

    // then: 사진은 원래 마커의 대기 상태로 남고 두 마커 모두 바뀌지 않는다.
    assertPhotoNotAttached(photo, PhotoStatus.PENDING_UPLOAD, 1L);
    Marker otherMarker = markerMapper.findById(OTHER_MARKER_ID).orElseThrow();
    assertThat(otherMarker.getStatus()).isEqualTo("ACTIVE");
    assertThat(otherMarker.getVersion()).isEqualTo(1L);
    assertUnchangedMarkerAndNoAttachRecord();
  }

  @Test
  @DisplayName("업로드 후 현재 수색 차수가 종료되면, 사진을 첨부하지 않고 대기 상태를 유지한다")
  void attach_noCurrentOp_rejectsWithoutChangingPhoto() {
    // given: 사진 업로드는 완료됐지만 현재 수색 차수가 종료되었다.
    MarkerPhoto photo = createPendingPhoto();
    objectStorage.simulateUpload(photo.getObjectKey());
    jdbcTemplate.update(
        "UPDATE operational_period SET status = 'ENDED', ended_at = NOW() WHERE id = ?", OP1_ID);

    // when: 현재 수색 차수가 없는 상태에서 사진을 첨부하려 한다.
    assertThatThrownBy(
            () ->
                photoService.attach(
                    createAttachRequest().toBuilder()
                        .markerId(MARKER_ID)
                        .photoId(photo.getId())
                        .context(createAttachContext())
                        .build()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("op_required", HttpStatus.CONFLICT);

    // then: 파일 자체의 실패로 기록하지 않고 대기 사진과 마커를 유지한다.
    assertPhotoNotAttached(photo, PhotoStatus.PENDING_UPLOAD, 1L);
    assertUnchangedMarkerAndNoAttachRecord();
  }

  @Test
  @DisplayName("첨부가 끝난 사진을 새 요청 키로 다시 첨부하면, 기존 결과를 변경하지 않고 거부한다")
  void attach_alreadyAttachedPhotoWithNewKey_rejectsWithoutDuplicateChanges() {
    // given: 사진 첨부가 이미 완료되었다.
    MarkerPhoto photo = createPendingPhoto();
    objectStorage.simulateUpload(photo.getObjectKey());
    photoService.attach(
        createAttachRequest().toBuilder()
            .markerId(MARKER_ID)
            .photoId(photo.getId())
            .context(createAttachContext())
            .build());
    PhotoRequestContext newContext =
        new PhotoRequestContext(
            createAttachContext().getAuthentication(), ATTACH_IDEMPOTENCY_KEY + "-new");

    // when: 응답 재전송이 아닌 새 요청 키로 같은 사진을 다시 첨부하려 한다.
    assertThatThrownBy(
            () ->
                photoService.attach(
                    createAttachRequest().toBuilder()
                        .markerId(MARKER_ID)
                        .photoId(photo.getId())
                        .context(newContext)
                        .build()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("write_conflict", HttpStatus.CONFLICT);

    // then: 첨부 상태·사진 버전·마커 버전은 유지하고 수정 이벤트를 추가하지 않는다.
    MarkerPhoto savedPhoto = photoMapper.findById(photo.getId()).orElseThrow();
    assertThat(savedPhoto.getStatus()).isEqualTo(PhotoStatus.ATTACHED);
    assertThat(savedPhoto.getVersion()).isEqualTo(2L);
    assertThat(savedPhoto.getObjectKey()).isEqualTo(photo.getObjectKey());
    assertThat(markerMapper.findById(MARKER_ID).orElseThrow().getVersion()).isEqualTo(2L);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT event_type FROM event_dispatch_job WHERE incident_id = ?",
                String.class,
                INCIDENT_ID))
        .containsExactly("MARKER_UPDATED");
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT idempotency_status FROM idempotency_record WHERE idempotency_key = ?",
                String.class,
                newContext.getIdempotencyKey()))
        .isEmpty();
  }

  @Test
  @DisplayName("사진 첨부 요청의 체크섬이 발급 정보와 다르면, 요청을 거부하고 사진을 실패 상태로 저장한다")
  void attach_requestChecksumMismatch_savesFailedPhoto() {
    // given: 업로드 파일은 정상이지만 첨부 요청의 체크섬만 다르다.
    MarkerPhoto photo = createPendingPhoto();
    objectStorage.simulateUpload(photo.getObjectKey());
    PhotoAttachServiceRequest request =
        PhotoAttachServiceRequest.builder()
            .sizeBytes(1024L)
            .contentType("image/jpeg")
            .width(640)
            .height(480)
            .checksumSha256(CHECKSUM_MISMATCH_SHA256)
            .build();

    // when: 발급 정보와 다른 체크섬으로 사진을 첨부하려 한다.
    assertThatThrownBy(
            () ->
                photoService.attach(
                    request.toBuilder()
                        .markerId(MARKER_ID)
                        .photoId(photo.getId())
                        .context(createAttachContext())
                        .build()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("write_conflict", HttpStatus.CONFLICT);

    // then: 실패 상태만 남고 마커·이벤트·요청 처리 기록은 바뀌지 않는다.
    assertPhotoNotAttached(photo, PhotoStatus.FAILED, 2L);
    assertUnchangedMarkerAndNoAttachRecord();
  }

  @ParameterizedTest
  @CsvSource({"image/png, 1024", "image/jpeg, 2048"})
  @DisplayName("업로드된 파일의 형식이나 크기가 발급 정보와 다르면, 사진을 실패 상태로 저장한다")
  void attach_uploadedMetadataMismatch_savesFailedPhoto(String contentType, long sizeBytes) {
    // given: 요청과 발급 정보는 같지만 실제 파일의 형식 또는 크기가 다르다.
    MarkerPhoto photo = createPendingPhoto();
    objectStorage.simulateUpload(photo.getObjectKey(), contentType, sizeBytes);

    // when: 업로드된 파일을 확인하며 사진을 첨부하려 한다.
    assertThatThrownBy(
            () ->
                photoService.attach(
                    createAttachRequest().toBuilder()
                        .markerId(MARKER_ID)
                        .photoId(photo.getId())
                        .context(createAttachContext())
                        .build()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("write_conflict", HttpStatus.CONFLICT);

    // then: 잘못된 파일의 사진은 실패 상태로 남고 마커와 이벤트는 변경되지 않는다.
    assertPhotoNotAttached(photo, PhotoStatus.FAILED, 2L);
    assertUnchangedMarkerAndNoAttachRecord();
  }

  @Test
  @DisplayName("발급 시 체크섬이 없더라도 요청과 업로드 파일의 체크섬이 다르면, 사진을 실패 상태로 저장한다")
  void attach_noIssuedChecksumAndRequestDiffersFromObject_savesFailedPhoto() {
    // given: 발급 체크섬은 없지만 저장소에는 파일의 체크섬이 있고, 요청 값과 다르다.
    MarkerPhoto photo = createPendingPhoto();
    objectStorage.simulateUpload(photo.getObjectKey());
    jdbcTemplate.update("UPDATE photo SET checksum_sha256 = NULL WHERE id = ?", photo.getId());
    PhotoAttachServiceRequest request =
        PhotoAttachServiceRequest.builder()
            .sizeBytes(1024L)
            .contentType("image/jpeg")
            .width(640)
            .height(480)
            .checksumSha256(CHECKSUM_MISMATCH_SHA256)
            .build();

    // when: 업로드 파일과 다른 체크섬으로 첨부를 요청한다.
    assertThatThrownBy(
            () ->
                photoService.attach(
                    request.toBuilder()
                        .markerId(MARKER_ID)
                        .photoId(photo.getId())
                        .context(createAttachContext())
                        .build()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("write_conflict", HttpStatus.CONFLICT);

    // then: 발급 체크섬이 없다는 이유로 검증을 생략하지 않는다.
    assertPhotoNotAttached(photo, PhotoStatus.FAILED, 2L);
    assertUnchangedMarkerAndNoAttachRecord();
  }

  @Test
  @DisplayName("첨부 요청의 사진 크기가 허용값을 넘으면, 요청을 거부하고 대기 사진을 유지한다")
  void attach_oversizedRequest_rejectsWithoutChangingPhoto() {
    // given: 정상 파일을 업로드했지만 요청 크기는 허용값보다 1바이트 크다.
    MarkerPhoto photo = createPendingPhoto();
    objectStorage.simulateUpload(photo.getObjectKey());
    PhotoAttachServiceRequest request =
        PhotoAttachServiceRequest.builder()
            .sizeBytes(10_485_761L)
            .contentType("image/jpeg")
            .width(640)
            .height(480)
            .checksumSha256(CHECKSUM_SHA256)
            .build();

    // when: 사진 크기가 허용값을 넘는 요청을 보낸다.
    assertThatThrownBy(
            () ->
                photoService.attach(
                    request.toBuilder()
                        .markerId(MARKER_ID)
                        .photoId(photo.getId())
                        .context(createAttachContext())
                        .build()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("photo_limit_exceeded", HttpStatus.PAYLOAD_TOO_LARGE);

    // then: 입력 검증에서 거부한 요청은 사진의 저장 상태를 변경하지 않는다.
    assertPhotoNotAttached(photo, PhotoStatus.PENDING_UPLOAD, 1L);
    assertUnchangedMarkerAndNoAttachRecord();
  }

  @Test
  @DisplayName("파일이 없어 첨부에 실패한 뒤 업로드를 완료하면, 같은 키와 사진으로 재시도해 한 번만 첨부한다")
  void attach_missingObjectThenUploaded_retriesWithSamePhotoAndRequestKey() {
    // given: 업로드 주소만 발급했고 저장소에 파일은 아직 없다.
    MarkerPhoto photo = createPendingPhoto();
    PhotoRequestContext context = createAttachContext();

    // when: 파일이 없는 상태에서 첨부를 요청한다.
    assertThatThrownBy(
            () ->
                photoService.attach(
                    createAttachRequest().toBuilder()
                        .markerId(MARKER_ID)
                        .photoId(photo.getId())
                        .context(context)
                        .build()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("write_conflict", HttpStatus.CONFLICT);

    // then: 대기 사진과 파일 키는 유지하고, 재시도할 수 있도록 요청 키를 남기지 않는다.
    assertPhotoNotAttached(photo, PhotoStatus.PENDING_UPLOAD, 1L);
    assertUnchangedMarkerAndNoAttachRecord();

    // when: 파일 업로드를 완료한 뒤 같은 요청 키로 다시 첨부하고 응답도 재요청한다.
    objectStorage.simulateUpload(photo.getObjectKey());
    PhotoAttachServiceResponse attached =
        photoService.attach(
            createAttachRequest().toBuilder()
                .markerId(MARKER_ID)
                .photoId(photo.getId())
                .context(context)
                .build());
    PhotoAttachServiceResponse repeated =
        photoService.attach(
            createAttachRequest().toBuilder()
                .markerId(MARKER_ID)
                .photoId(photo.getId())
                .context(context)
                .build());

    // then: 같은 사진과 파일 키를 사용하며 첨부와 수정 이벤트는 한 번만 반영한다.
    assertThat(repeated).usingRecursiveComparison().isEqualTo(attached);
    MarkerPhoto savedPhoto = photoMapper.findById(photo.getId()).orElseThrow();
    assertThat(savedPhoto.getStatus()).isEqualTo(PhotoStatus.ATTACHED);
    assertThat(savedPhoto.getVersion()).isEqualTo(2L);
    assertThat(savedPhoto.getObjectKey()).isEqualTo(photo.getObjectKey());
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT id FROM photo WHERE marker_id = ?", UUID.class, MARKER_ID))
        .containsExactly(photo.getId());
    assertThat(markerMapper.findById(MARKER_ID).orElseThrow().getVersion()).isEqualTo(2L);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT event_type FROM event_dispatch_job WHERE incident_id = ?",
                String.class,
                INCIDENT_ID))
        .containsExactly("MARKER_UPDATED");
    assertCompletedRequest(ATTACH_IDEMPOTENCY_KEY);
  }

  @Test
  @DisplayName("파일 확인 중 다른 요청이 사진을 실패 상태로 바꾸면, 첨부 요청이 그 상태를 덮어쓰지 않는다")
  void attach_photoFailedByConcurrentRequest_rejectsWithoutOverwritingFailure() {
    // given: 파일 확인 시점에 다른 트랜잭션이 먼저 이 사진의 실패 상태를 확정한다.
    MarkerPhoto photo = createPendingPhoto();
    objectStorage.simulateUpload(photo.getObjectKey());
    doAnswer(
            invocation -> {
              TransactionTemplate concurrentTransaction =
                  new TransactionTemplate(transactionManager);
              concurrentTransaction.setPropagationBehavior(
                  TransactionDefinition.PROPAGATION_REQUIRES_NEW);
              concurrentTransaction.executeWithoutResult(
                  transaction ->
                      jdbcTemplate.update(
                          "UPDATE photo SET status = 'FAILED', version = 2, updated_at = NOW()"
                              + " WHERE id = ?",
                          photo.getId()));
              return invocation.callRealMethod();
            })
        .when(objectStorage)
        .headObject(photo.getObjectKey());

    // when: 이전 대기 상태를 읽었던 요청이 뒤늦게 사진 첨부를 시도한다.
    assertThatThrownBy(
            () ->
                photoService.attach(
                    createAttachRequest().toBuilder()
                        .markerId(MARKER_ID)
                        .photoId(photo.getId())
                        .context(createAttachContext())
                        .build()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("write_conflict", HttpStatus.CONFLICT);

    // then: 먼저 확정된 실패 상태를 유지하고 이번 요청의 마커·이벤트는 저장하지 않는다.
    assertPhotoNotAttached(photo, PhotoStatus.FAILED, 2L);
    assertUnchangedMarkerAndNoAttachRecord();
  }

  @Test
  @DisplayName("작성 권한이 없는 계정이 만료된 사진을 첨부하면, 권한 오류만 반환하고 사진을 변경하지 않는다")
  void attach_expiredPhotoWithoutAuthorAccess_rejectsWithoutFailingPhoto() {
    // given: 만료된 사진이 있지만 현재 계정은 이 마커의 작성자가 아니다.
    MarkerPhoto photo = createPendingPhoto();
    jdbcTemplate.update(
        "UPDATE photo SET upload_url_expires_at = NOW() - INTERVAL '1 minute' WHERE id = ?",
        photo.getId());
    jdbcTemplate.update(
        "UPDATE marker SET created_by_account_id = ? WHERE id = ?", SUPPORT_TEAM_ID, MARKER_ID);

    // when: 권한이 없는 계정으로 만료된 사진을 첨부하려 한다.
    assertThatThrownBy(
            () ->
                photoService.attach(
                    createAttachRequest().toBuilder()
                        .markerId(MARKER_ID)
                        .photoId(photo.getId())
                        .context(createAttachContext())
                        .build()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("incident_access_denied", HttpStatus.FORBIDDEN);

    // then: 사진 검사보다 권한 검사를 먼저 하므로 실패 상태 저장도 실행하지 않는다.
    assertPhotoNotAttached(photo, PhotoStatus.PENDING_UPLOAD, 1L);
    assertUnchangedMarkerAndNoAttachRecord();
  }

  @ParameterizedTest
  @CsvSource({"ATTACHED, 2", "PENDING_UPLOAD, 2"})
  @DisplayName("파일 확인 중 사진의 상태나 버전이 바뀌면, 이전 정보로 실패 상태를 덮어쓰지 않는다")
  void attach_photoChangedByConcurrentRequest_doesNotOverwriteWithFailure(
      String status, long version) {
    // given: 파일 확인 중 다른 트랜잭션이 사진의 상태 또는 버전을 먼저 바꾼다.
    MarkerPhoto photo = createPendingPhoto();
    doAnswer(
            invocation -> {
              TransactionTemplate concurrentTransaction =
                  new TransactionTemplate(transactionManager);
              concurrentTransaction.setPropagationBehavior(
                  TransactionDefinition.PROPAGATION_REQUIRES_NEW);
              concurrentTransaction.executeWithoutResult(
                  transaction ->
                      jdbcTemplate.update(
                          """
                          UPDATE photo SET status = ?, version = ?, updated_at = NOW(),
                              attached_at = CASE WHEN ? = 'ATTACHED' THEN NOW() ELSE NULL END
                          WHERE id = ?
                          """,
                          status,
                          version,
                          status,
                          photo.getId()));
              return Optional.of(
                  new ObjectMetadata(photo.getObjectKey(), "image/png", 1024L, CHECKSUM_SHA256));
            })
        .when(objectStorage)
        .headObject(photo.getObjectKey());

    // when: 이전 대기 상태를 읽었던 요청이 파일 정보 불일치로 첨부를 거부한다.
    assertThatThrownBy(
            () ->
                photoService.attach(
                    createAttachRequest().toBuilder()
                        .markerId(MARKER_ID)
                        .photoId(photo.getId())
                        .context(createAttachContext())
                        .build()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("write_conflict", HttpStatus.CONFLICT);

    // then: 이번 요청은 다른 트랜잭션에서 저장한 상태·버전을 변경하지 않는다.
    MarkerPhoto savedPhoto = photoMapper.findById(photo.getId()).orElseThrow();
    assertThat(savedPhoto.getStatus().name()).isEqualTo(status);
    assertThat(savedPhoto.getVersion()).isEqualTo(version);
    assertUnchangedMarkerAndNoAttachRecord();
  }

  @Test
  @DisplayName("사진 수정 이벤트 저장에 실패하면, 사진 첨부·마커 변경·요청 처리 기록을 함께 롤백한다")
  void attach_eventStorageFails_rollsBackPhotoMarkerAndRequestRecord() {
    // given: 정상 사진을 업로드했지만 이 마커의 수정 이벤트만 DB에서 거부한다.
    MarkerPhoto photo = createPendingPhoto();
    objectStorage.simulateUpload(photo.getObjectKey());
    jdbcTemplate.execute(
        """
        ALTER TABLE event_dispatch_job ADD CONSTRAINT test_photo_attach_event_failure
        CHECK (source_entity_id <> '4ca60e44-9cfe-410b-8794-2983baac0eac'::uuid
               OR event_type <> 'MARKER_UPDATED') NOT VALID
        """);
    try {
      // when: 사진과 마커를 갱신한 뒤 수정 이벤트 저장에 실패한다.
      assertThatThrownBy(
              () ->
                  photoService.attach(
                      createAttachRequest().toBuilder()
                          .markerId(MARKER_ID)
                          .photoId(photo.getId())
                          .context(createAttachContext())
                          .build()))
          .isInstanceOf(DataAccessException.class);

      // then: 일부 변경만 남지 않으며, 정상 파일을 실패 상태로 바꾸지도 않는다.
      assertPhotoNotAttached(photo, PhotoStatus.PENDING_UPLOAD, 1L);
      assertUnchangedMarkerAndNoAttachRecord();
    } finally {
      jdbcTemplate.execute(
          "ALTER TABLE event_dispatch_job DROP CONSTRAINT test_photo_attach_event_failure");
    }

    // when & then: 이벤트 저장이 복구되면 같은 요청 키로 첨부할 수 있다.
    assertThat(
            photoService
                .attach(
                    createAttachRequest().toBuilder()
                        .markerId(MARKER_ID)
                        .photoId(photo.getId())
                        .context(createAttachContext())
                        .build())
                .getStatus())
        .isEqualTo("ATTACHED");
    assertCompletedRequest(ATTACH_IDEMPOTENCY_KEY);
  }

  @Test
  @DisplayName("업로드 후 사건이 종료되면, 사진 첨부를 거부하고 대기 사진·마커·이벤트를 그대로 유지한다")
  void attach_incidentClosedAfterUpload_rejectsWithoutChangingPhotoMarkerOrEvents() {
    // given: 사진 업로드는 완료됐지만 첨부 요청 전에 사건이 종료되었다.
    PhotoUploadUrlServiceResponse upload = photoService.createUploadUrl(createUploadRequest());
    MarkerPhoto uploadedPhoto = photoMapper.findById(upload.getPhotoId()).orElseThrow();
    objectStorage.simulateUpload(uploadedPhoto.getObjectKey());
    jdbcTemplate.update(
        "UPDATE incident SET status = 'CLOSED', closed_at = NOW() WHERE id = ?", INCIDENT_ID);
    PhotoRequestContext context =
        new PhotoRequestContext(
            new SuriMapAuthentication(PRECINCT_TEAM_ID, "APP", ASSIGNED_POLICE_PHONE_ID),
            ATTACH_IDEMPOTENCY_KEY);
    PhotoAttachServiceRequest request =
        PhotoAttachServiceRequest.builder()
            .sizeBytes(1024L)
            .contentType("image/jpeg")
            .width(640)
            .height(480)
            .checksumSha256(CHECKSUM_SHA256)
            .build();

    // when: 종료된 사건의 마커에 사진을 첨부하려 한다.
    assertThatThrownBy(
            () ->
                photoService.attach(
                    request.toBuilder()
                        .markerId(MARKER_ID)
                        .photoId(upload.getPhotoId())
                        .context(context)
                        .build()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("incident_closed", HttpStatus.CONFLICT);

    // then: 사진은 대기 상태·버전 1로 남고 마커와 수정 이벤트도 변경되지 않는다.
    MarkerPhoto savedPhoto = photoMapper.findById(upload.getPhotoId()).orElseThrow();
    assertThat(savedPhoto.getStatus()).isEqualTo(PhotoStatus.PENDING_UPLOAD);
    assertThat(savedPhoto.getVersion()).isEqualTo(1L);
    assertThat(savedPhoto.getAttachedAt()).isNull();
    assertThat(savedPhoto.getWidth()).isNull();
    assertThat(savedPhoto.getHeight()).isNull();
    Marker marker = markerMapper.findById(MARKER_ID).orElseThrow();
    assertThat(marker.getStatus()).isEqualTo("ACTIVE");
    assertThat(marker.getVersion()).isEqualTo(1L);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT event_type FROM event_dispatch_job WHERE source_entity_id = ?",
                String.class,
                MARKER_ID))
        .isEmpty();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT idempotency_status FROM idempotency_record WHERE idempotency_key = ?",
                String.class,
                ATTACH_IDEMPOTENCY_KEY))
        .isEmpty();
  }

  @Test
  @DisplayName("현재 수색 차수가 없으면, op_required 오류를 반환하고 사진을 저장하지 않는다")
  void createUploadUrl_noCurrentOp_rejectsWithoutSavingPhoto() {
    // given: 마커의 수색 차수가 종료되어 활성 수색 차수가 없다.
    jdbcTemplate.update(
        "UPDATE operational_period SET status = 'ENDED', ended_at = NOW() WHERE id = ?", OP1_ID);

    // when & then: 실제 DB의 수색 차수 상태로 업로드를 거부한다.
    assertUploadRejected("op_required", HttpStatus.CONFLICT);
  }

  @Test
  @DisplayName("이전 수색 차수의 마커에 요청하면, op_mismatch 오류를 반환하고 사진을 저장하지 않는다")
  void createUploadUrl_markerFromPreviousOp_rejectsWithoutSavingPhoto() {
    // given: 마커가 속한 OP1은 종료되었고 현재 수색 차수는 OP2다.
    jdbcTemplate.update(
        "UPDATE operational_period SET status = 'ENDED', ended_at = NOW() WHERE id = ?", OP1_ID);
    jdbcTemplate.update(
        """
        INSERT INTO operational_period (id, incident_id, sequence_number, status, reason,
            started_by_account_id, started_at, version, created_at, updated_at)
        VALUES (?, ?, 2, 'ACTIVE', 'INITIAL', ?, NOW(), 1, NOW(), NOW())
        ON CONFLICT (id) DO UPDATE SET status = 'ACTIVE', ended_at = NULL
        """,
        OP2_ID,
        INCIDENT_ID,
        PRECINCT_TEAM_ID);

    // when & then: 실제 마커와 현재 수색 차수를 비교해 요청을 거부한다.
    assertUploadRejected("op_mismatch", HttpStatus.CONFLICT);
  }

  @Test
  @DisplayName("마커가 존재하지 않으면, write_conflict 오류를 반환하고 사진을 저장하지 않는다")
  void createUploadUrl_missingMarker_rejectsWithoutSavingPhoto() {
    // given: 업로드 주소를 요청할 마커가 DB에 없다.
    jdbcTemplate.update("DELETE FROM marker WHERE id = ?", MARKER_ID);

    // when: 존재하지 않는 마커의 업로드 주소를 요청한다.
    assertThatThrownBy(() -> photoService.createUploadUrl(createUploadRequest()))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly("write_conflict", HttpStatus.CONFLICT);

    // then: 사진과 마커 수정 이벤트를 만들지 않는다.
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM photo WHERE marker_id = ?", Integer.class, MARKER_ID))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM event_dispatch_job WHERE incident_id = ?",
                Integer.class,
                INCIDENT_ID))
        .isZero();
  }

  @Test
  @DisplayName("작성자의 활성 근무교대가 없으면, police_phone_not_assigned 오류를 반환하고 사진을 저장하지 않는다")
  void createUploadUrl_noActiveDutyShift_rejectsWithoutSavingPhoto() {
    // given: 사건 배정은 유지하지만 현재 계정의 근무교대가 종료되었다.
    jdbcTemplate.update(
        "UPDATE duty_shift SET status = 'ENDED', ended_at = NOW() WHERE id = ?", DUTY_SHIFT_ID);

    // when & then: 배정과 활성 근무교대의 실제 SQL 조회 결과를 구분한다.
    assertUploadRejected("police_phone_not_assigned", HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("다른 계정이 만든 마커에 요청하면, incident_access_denied 오류를 반환하고 사진을 저장하지 않는다")
  void createUploadUrl_anotherAuthor_rejectsWithoutSavingPhoto() {
    // given: 사건에 배정되어 있지만 마커 작성자는 다른 계정이다.
    jdbcTemplate.update(
        "UPDATE marker SET created_by_account_id = ? WHERE id = ?", SUPPORT_TEAM_ID, MARKER_ID);

    // when & then: 실제 마커의 작성 계정으로 사진 수정 권한을 판단한다.
    assertUploadRejected("incident_access_denied", HttpStatus.FORBIDDEN);
  }

  private void assertUploadRejected(String error, HttpStatus status) {
    assertUploadRejected(createUploadRequest(), error, status);
  }

  private void assertUploadRejected(
      PhotoUploadUrlServiceRequest request, String error, HttpStatus status) {
    assertThatThrownBy(() -> photoService.createUploadUrl(request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode.error", "errorCode.status")
        .containsExactly(error, status);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM photo WHERE marker_id = ?", Integer.class, MARKER_ID))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM event_dispatch_job WHERE incident_id = ?",
                Integer.class,
                INCIDENT_ID))
        .isZero();
    Marker marker = markerMapper.findById(MARKER_ID).orElseThrow();
    assertThat(marker.getStatus()).isEqualTo("ACTIVE");
    assertThat(marker.getVersion()).isEqualTo(1L);
  }

  private PhotoUploadUrlServiceRequest createUploadRequest() {
    return PhotoUploadUrlServiceRequest.builder()
        .markerId(MARKER_ID)
        .context(createContext(ASSIGNED_POLICE_PHONE_ID))
        .contentType("image/jpeg")
        .sizeBytes(1024L)
        .checksumSha256(CHECKSUM_SHA256)
        .build();
  }

  private MarkerPhoto createPendingPhoto() {
    PhotoUploadUrlServiceResponse upload = photoService.createUploadUrl(createUploadRequest());
    return photoMapper.findById(upload.getPhotoId()).orElseThrow();
  }

  private PhotoAttachServiceRequest createAttachRequest() {
    return PhotoAttachServiceRequest.builder()
        .sizeBytes(1024L)
        .contentType("image/jpeg")
        .width(640)
        .height(480)
        .checksumSha256(CHECKSUM_SHA256)
        .build();
  }

  private PhotoRequestContext createAttachContext() {
    return new PhotoRequestContext(
        createContext(ASSIGNED_POLICE_PHONE_ID).getAuthentication(), ATTACH_IDEMPOTENCY_KEY);
  }

  private void assertPhotoNotAttached(MarkerPhoto originalPhoto, PhotoStatus status, long version) {
    MarkerPhoto savedPhoto = photoMapper.findById(originalPhoto.getId()).orElseThrow();
    assertThat(savedPhoto.getStatus()).isEqualTo(status);
    assertThat(savedPhoto.getVersion()).isEqualTo(version);
    assertThat(savedPhoto.getMarkerId()).isEqualTo(originalPhoto.getMarkerId());
    assertThat(savedPhoto.getObjectKey()).isEqualTo(originalPhoto.getObjectKey());
    assertThat(savedPhoto.getAttachedAt()).isNull();
    assertThat(savedPhoto.getWidth()).isNull();
    assertThat(savedPhoto.getHeight()).isNull();
  }

  private void assertUnchangedMarkerAndNoAttachRecord() {
    Marker marker = markerMapper.findById(MARKER_ID).orElseThrow();
    assertThat(marker.getStatus()).isEqualTo("ACTIVE");
    assertThat(marker.getVersion()).isEqualTo(1L);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT event_type FROM event_dispatch_job WHERE incident_id = ?",
                String.class,
                INCIDENT_ID))
        .isEmpty();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT idempotency_status FROM idempotency_record WHERE idempotency_key = ?",
                String.class,
                ATTACH_IDEMPOTENCY_KEY))
        .isEmpty();
  }

  private void assertCompletedRequest(String idempotencyKey) {
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT idempotency_status FROM idempotency_record WHERE idempotency_key = ?",
                String.class,
                idempotencyKey))
        .containsExactly("COMPLETED");
  }

  private String readRequestBodyHash(String idempotencyKey) {
    return jdbcTemplate.queryForObject(
        "SELECT request_body_hash FROM idempotency_record WHERE idempotency_key = ?",
        String.class,
        idempotencyKey);
  }

  private void replaceStoredRequestHash(String idempotencyKey, String bodyHash) {
    assertThat(
            jdbcTemplate.update(
                "UPDATE idempotency_record SET request_body_hash = ? WHERE idempotency_key = ?",
                bodyHash,
                idempotencyKey))
        .isEqualTo(1);
  }

  private void replaceStoredAttachRequestHash(UUID photoId, String legacyRequestBody)
      throws Exception {
    // 서비스의 포맷 함수를 쓰지 않고, 이전 record DTO의 고정 문자열로 과거 기록을 준비한다.
    String legacyBody = "attach:" + MARKER_ID + ":" + photoId + ":" + legacyRequestBody;
    byte[] hash =
        MessageDigest.getInstance("SHA-256").digest(legacyBody.getBytes(StandardCharsets.UTF_8));
    replaceStoredRequestHash(ATTACH_IDEMPOTENCY_KEY, HexFormat.of().formatHex(hash));
  }

  private PhotoRequestContext createContext(UUID policePhoneId) {
    return new PhotoRequestContext(
        new SuriMapAuthentication(PRECINCT_TEAM_ID, "APP", policePhoneId), IDEMPOTENCY_KEY);
  }

  @ParameterizedTest(name = "파일 형식: {0}, 크기: {1}")
  @CsvSource(
      value = {
        "null, 1024",
        "'', 1024",
        "' ', 1024",
        "image/gif, 1024",
        "IMAGE/JPEG, 1024",
        "null, 0",
        "image/gif, 10485761"
      },
      nullValues = "null")
  @DisplayName("마커 생성 전 업로드할 사진 형식이 잘못되면, 입력 오류로 거부하고 대기 사진을 만들지 않는다")
  void createUploadUrlBeforeMarkerCreation_invalidContentType_rejectsWithoutSavingPhoto(
      String contentType, long sizeBytes) {
    // given: 마커 ID·사건·수색 차수와 인증은 유효하지만 사진 형식과 크기는 잘못될 수 있다.
    MarkerCreatePhotoUploadUrlServiceRequest request =
        MarkerCreatePhotoUploadUrlServiceRequest.builder()
            .markerId(BEFORE_CREATION_MARKER_ID)
            .incidentId(INCIDENT_ID)
            .opId(OP1_ID)
            .contentType(contentType)
            .sizeBytes(sizeBytes)
            .checksumSha256(CHECKSUM_SHA256)
            .build();

    // when & then: 실제 서비스가 입력 오류를 반환하고 사진·마커·요청 기록을 만들지 않는다.
    assertThatThrownBy(
            () ->
                photoService.createUploadUrlBeforeMarkerCreation(
                    request.toBuilder().context(createUploadBeforeMarkerCreationContext()).build()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> {
              assertThat(exception.getErrorCode().getError())
                  .isEqualTo("invalid_photo_content_type");
              assertThat(exception.getErrorCode().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT id FROM photo WHERE marker_id = ?", UUID.class, BEFORE_CREATION_MARKER_ID))
        .isEmpty();
    assertThat(markerMapper.findById(BEFORE_CREATION_MARKER_ID)).isEmpty();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT event_type FROM event_dispatch_job WHERE incident_id = ?",
                String.class,
                INCIDENT_ID))
        .isEmpty();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT idempotency_status FROM idempotency_record WHERE idempotency_key = ?",
                String.class,
                BEFORE_CREATION_IDEMPOTENCY_KEY))
        .isEmpty();
  }

  @Test
  @DisplayName("마커 생성 전에 사진 업로드 주소를 요청하면, 앱이 지정한 마커 ID로 대기 사진을 저장한다")
  void createUploadUrlBeforeMarkerCreation_assignedAccount_savesPendingPhoto() {
    // given: 배정된 계정이 근무 중이고, 앱이 정한 마커 ID는 아직 DB에 없다.
    assertThat(markerMapper.findById(BEFORE_CREATION_MARKER_ID)).isEmpty();
    Instant requestedAt = Instant.now();

    // when: 실제 사진 업로드 주소 발급 서비스를 호출한다.
    PhotoUploadUrlServiceResponse response =
        photoService.createUploadUrlBeforeMarkerCreation(
            createUploadBeforeMarkerCreationRequest().toBuilder()
                .context(createUploadBeforeMarkerCreationContext())
                .build());

    // then: 15분 동안 유효한 주소와 대기 사진만 만들고, 마커와 이벤트는 만들지 않는다.
    MarkerPhoto photo = photoMapper.findById(response.getPhotoId()).orElseThrow();
    String expectedObjectKey =
        "markers/"
            + INCIDENT_ID
            + "/"
            + BEFORE_CREATION_MARKER_ID
            + "/"
            + response.getPhotoId()
            + ".jpg";
    assertThat(response.getUploadUrl())
        .isEqualTo("http://127.0.0.1:18080/mock-upload/" + expectedObjectKey);
    assertThat(response.getExpiresAt())
        .isBetween(requestedAt.plusSeconds(15 * 60), Instant.now().plusSeconds(15 * 60));
    assertThat(response.getMaxSizeBytes()).isEqualTo(10_485_760L);
    assertThat(response.getVersion()).isEqualTo(1L);
    assertThat(photo.getMarkerId()).isEqualTo(BEFORE_CREATION_MARKER_ID);
    assertThat(photo.getObjectKey()).isEqualTo(expectedObjectKey);
    assertThat(photo.getContentType()).isEqualTo("image/jpeg");
    assertThat(photo.getSizeBytes()).isEqualTo(1_048_576L);
    assertThat(photo.getChecksumSha256()).isEqualTo(CHECKSUM_SHA256);
    assertThat(photo.getStatus()).isEqualTo(PhotoStatus.PENDING_UPLOAD);
    assertThat(photo.getVersion()).isEqualTo(1L);
    assertThat(photo.getAttachedAt()).isNull();
    assertThat(markerMapper.findById(BEFORE_CREATION_MARKER_ID)).isEmpty();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT event_type FROM event_dispatch_job WHERE incident_id = ?",
                String.class,
                INCIDENT_ID))
        .isEmpty();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT idempotency_status FROM idempotency_record WHERE idempotency_key = ?",
                String.class,
                BEFORE_CREATION_IDEMPOTENCY_KEY))
        .containsExactly("COMPLETED");
  }

  @ParameterizedTest(name = "{0} 해시 기록")
  @ValueSource(strings = {"JSON", "LEGACY"})
  @DisplayName("마커 생성 전 같은 업로드 주소 요청을 재전송하면, 기존 응답을 반환하고 대기 사진을 추가하지 않는다")
  void createUploadUrlBeforeMarkerCreation_sameRequest_returnsStoredResponseWithoutAnotherPhoto(
      String storedHashFormat) {
    // given: 마커 생성 전에 사진 업로드 주소를 한 번 발급받았다.
    PhotoUploadUrlServiceResponse first =
        photoService.createUploadUrlBeforeMarkerCreation(
            createUploadBeforeMarkerCreationRequest().toBuilder()
                .context(createUploadBeforeMarkerCreationContext())
                .build());
    assertThat(readRequestBodyHash(BEFORE_CREATION_IDEMPOTENCY_KEY))
        .isEqualTo("a391ad0e4d94e24f997a29ef3881ba8303120950426bd1fd9acc8e5b5def41bc");
    if ("LEGACY".equals(storedHashFormat)) {
      replaceStoredRequestHash(
          BEFORE_CREATION_IDEMPOTENCY_KEY, BEFORE_CREATION_LEGACY_REQUEST_HASH);
    }
    String storedRequestHash = readRequestBodyHash(BEFORE_CREATION_IDEMPOTENCY_KEY);

    // when: 같은 요청 키와 본문으로 업로드 주소를 다시 요청한다.
    PhotoUploadUrlServiceResponse repeated =
        photoService.createUploadUrlBeforeMarkerCreation(
            createUploadBeforeMarkerCreationRequest().toBuilder()
                .context(createUploadBeforeMarkerCreationContext())
                .build());

    // then: 사진 ID·주소·만료 시각을 재사용하고 DB에는 대기 사진 하나만 남는다.
    assertThat(repeated).usingRecursiveComparison().isEqualTo(first);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT id FROM photo WHERE marker_id = ?", UUID.class, BEFORE_CREATION_MARKER_ID))
        .containsExactly(first.getPhotoId());
    assertThat(photoMapper.findById(first.getPhotoId()).orElseThrow().getStatus())
        .isEqualTo(PhotoStatus.PENDING_UPLOAD);
    assertThat(markerMapper.findById(BEFORE_CREATION_MARKER_ID)).isEmpty();
    assertThat(readRequestBodyHash(BEFORE_CREATION_IDEMPOTENCY_KEY)).isEqualTo(storedRequestHash);
  }

  @ParameterizedTest(name = "{0} 해시 기록")
  @ValueSource(strings = {"JSON", "LEGACY"})
  @DisplayName("마커 생성 전 사용한 업로드 요청 키에 다른 파일 크기를 보내면, 거부하고 기존 대기 사진을 유지한다")
  void createUploadUrlBeforeMarkerCreation_sameKeyWithDifferentSize_rejectsWithoutChangingPhoto(
      String storedHashFormat) {
    // given: 마커 생성 전 발급한 주소와 현재 또는 과거 해시의 요청 기록이 있다.
    PhotoUploadUrlServiceResponse first =
        photoService.createUploadUrlBeforeMarkerCreation(
            createUploadBeforeMarkerCreationRequest().toBuilder()
                .context(createUploadBeforeMarkerCreationContext())
                .build());
    if ("LEGACY".equals(storedHashFormat)) {
      replaceStoredRequestHash(
          BEFORE_CREATION_IDEMPOTENCY_KEY, BEFORE_CREATION_LEGACY_REQUEST_HASH);
    }
    String storedRequestHash = readRequestBodyHash(BEFORE_CREATION_IDEMPOTENCY_KEY);

    // when: 같은 키에 파일 크기만 다른 업로드 주소 요청을 보낸다.
    MarkerCreatePhotoUploadUrlServiceRequest changed =
        MarkerCreatePhotoUploadUrlServiceRequest.builder()
            .markerId(BEFORE_CREATION_MARKER_ID)
            .incidentId(INCIDENT_ID)
            .opId(OP1_ID)
            .contentType("image/jpeg")
            .sizeBytes(2048L)
            .checksumSha256(CHECKSUM_SHA256)
            .build();
    assertThatThrownBy(
            () ->
                photoService.createUploadUrlBeforeMarkerCreation(
                    changed.toBuilder().context(createUploadBeforeMarkerCreationContext()).build()))
        .isInstanceOf(IdempotencyMismatchException.class);

    // then: 원래 대기 사진과 요청 기록을 유지하고 마커·이벤트는 만들지 않는다.
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT id FROM photo WHERE marker_id = ?", UUID.class, BEFORE_CREATION_MARKER_ID))
        .containsExactly(first.getPhotoId());
    MarkerPhoto photo = photoMapper.findById(first.getPhotoId()).orElseThrow();
    assertThat(photo.getStatus()).isEqualTo(PhotoStatus.PENDING_UPLOAD);
    assertThat(photo.getVersion()).isEqualTo(1L);
    assertThat(photo.getSizeBytes()).isEqualTo(1_048_576L);
    assertThat(markerMapper.findById(BEFORE_CREATION_MARKER_ID)).isEmpty();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT event_type FROM event_dispatch_job WHERE incident_id = ?",
                String.class,
                INCIDENT_ID))
        .isEmpty();
    assertThat(readRequestBodyHash(BEFORE_CREATION_IDEMPOTENCY_KEY)).isEqualTo(storedRequestHash);
  }

  @Test
  @DisplayName("사건 종료와 현재 수색 차수 부재가 겹치면, 생성 전 사진 업로드는 차수 오류를 먼저 반환한다")
  void createUploadUrlBeforeMarkerCreation_closedIncidentWithoutOp_rejectsOpFirst() {
    // given: 사건이 종료되었고 활성 수색 차수도 없다.
    jdbcTemplate.update(
        "UPDATE incident SET status = 'CLOSED', closed_at = NOW() WHERE id = ?", INCIDENT_ID);
    jdbcTemplate.update(
        "UPDATE operational_period SET status = 'ENDED', ended_at = NOW() WHERE incident_id = ?",
        INCIDENT_ID);
    MarkerCreatePhotoUploadUrlServiceRequest request =
        createUploadBeforeMarkerCreationRequest().toBuilder()
            .context(createUploadBeforeMarkerCreationContext())
            .build();

    // when & then: 마커 생성과 달리, 생성 전 사진 업로드의 기존 차수 검사 순서를 유지한다.
    assertThatThrownBy(() -> photoService.createUploadUrlBeforeMarkerCreation(request))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.OP_REQUIRED);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT id FROM photo WHERE marker_id = ?", UUID.class, BEFORE_CREATION_MARKER_ID))
        .isEmpty();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT idempotency_key FROM idempotency_record WHERE idempotency_key = ?",
                String.class,
                BEFORE_CREATION_IDEMPOTENCY_KEY))
        .isEmpty();
  }

  private MarkerCreatePhotoUploadUrlServiceRequest createUploadBeforeMarkerCreationRequest() {
    return MarkerCreatePhotoUploadUrlServiceRequest.builder()
        .markerId(BEFORE_CREATION_MARKER_ID)
        .incidentId(INCIDENT_ID)
        .opId(OP1_ID)
        .contentType("image/jpeg")
        .sizeBytes(1_048_576L)
        .checksumSha256(CHECKSUM_SHA256)
        .build();
  }

  private PhotoRequestContext createUploadBeforeMarkerCreationContext() {
    return new PhotoRequestContext(
        new SuriMapAuthentication(PRECINCT_TEAM_ID, "APP", ASSIGNED_POLICE_PHONE_ID),
        BEFORE_CREATION_IDEMPOTENCY_KEY);
  }
}
