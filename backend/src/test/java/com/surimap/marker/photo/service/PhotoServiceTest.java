package com.surimap.marker.photo.service;

import static com.surimap.account.AccountIdentityCatalog.PRECINCT_TEAM_ID;
import static com.surimap.account.AccountIdentityCatalog.SUPPORT_TEAM_ID;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.INCIDENT_ID;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.OP1_ID;
import static com.surimap.marker.photo.fixture.PhotoFixtures.CHECKSUM_SHA256;
import static com.surimap.policephone.PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.domain.marker.Marker;
import com.surimap.domain.marker.MarkerMapper;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.domain.fixture.MarkerGeometryFixtures;
import com.surimap.marker.photo.adapter.MockObjectStorageAdapter;
import com.surimap.marker.photo.domain.MarkerPhoto;
import com.surimap.marker.photo.domain.PhotoStatus;
import com.surimap.marker.photo.dto.PhotoAttachRequest;
import com.surimap.marker.photo.dto.PhotoAttachResult;
import com.surimap.marker.photo.dto.PhotoUploadUrlRequest;
import com.surimap.marker.photo.dto.PhotoUploadUrlResponse;
import com.surimap.marker.photo.exception.PhotoApiException;
import com.surimap.marker.photo.repository.PhotoRepository;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "surimap.object-storage.provider=mock")
class PhotoServiceTest extends PostGisIntegrationTestSupport {

  private static final UUID MARKER_ID = UUID.fromString("4ca60e44-9cfe-410b-8794-2983baac0eac");
  private static final UUID DUTY_SHIFT_ID = UUID.fromString("33333333-3333-3333-3333-333333330001");
  private static final UUID OTHER_REGISTERED_POLICE_PHONE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000301");
  private static final String IDEMPOTENCY_KEY = "idem-photo-upload-url";
  private static final String ATTACH_IDEMPOTENCY_KEY = "idem-photo-attach";

  @Autowired private PhotoService photoService;
  @Autowired private MarkerMapper markerMapper;
  @Autowired private PhotoRepository photoRepository;
  @Autowired private MockObjectStorageAdapter objectStorage;

  @BeforeEach
  void setUp() {
    // 기존 PostGIS 테스트 DB에서 이번 사건·마커·요청 키에 해당하는 데이터만 준비한다.
    jdbcTemplate.update("DELETE FROM photo WHERE marker_id = ?", MARKER_ID);
    jdbcTemplate.update("DELETE FROM marker WHERE id = ?", MARKER_ID);
    jdbcTemplate.update("DELETE FROM event_dispatch_job WHERE incident_id = ?", INCIDENT_ID);
    jdbcTemplate.update(
        "DELETE FROM idempotency_record WHERE idempotency_key IN (?, ?)",
        IDEMPOTENCY_KEY,
        ATTACH_IDEMPOTENCY_KEY);
    jdbcTemplate.update("DELETE FROM duty_shift WHERE operational_period_id = ?", OP1_ID);
    jdbcTemplate.update("DELETE FROM incident_assignment WHERE incident_id = ?", INCIDENT_ID);
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
    markerMapper.insertCreate(
        Marker.builder()
            .id(MARKER_ID)
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

  @Test
  @DisplayName("현재 수색 차수에서 근무 중인 작성자가 요청하면, 사진 업로드 주소와 대기 중인 사진을 생성한다")
  void createUploadUrl_assignedAuthor_savesPendingPhoto() {
    // given: 배정된 계정의 활성 근무교대와 같은 계정이 만든 현장 마커가 있다.
    // when: 실제 사진 서비스에서 업로드 주소를 발급한다.
    PhotoUploadUrlResponse response =
        photoService.createUploadUrl(
            MARKER_ID, createUploadRequest(), createContext(ASSIGNED_POLICE_PHONE_ID));

    // then: 업로드 대기 사진을 저장하고 마커 자체는 아직 변경하지 않는다.
    MarkerPhoto photo = photoRepository.findById(response.photoId()).orElseThrow();
    assertThat(photo.markerId()).isEqualTo(MARKER_ID);
    assertThat(photo.status()).isEqualTo(PhotoStatus.PENDING_UPLOAD);
    assertThat(photo.contentType()).isEqualTo("image/jpeg");
    assertThat(photo.sizeBytes()).isEqualTo(1024L);
    assertThat(photo.checksumSha256()).isEqualTo(CHECKSUM_SHA256);
    assertThat(response.uploadUrl()).endsWith(photo.objectKey());
    assertThat(response.version()).isEqualTo(1L);
    assertThat(markerMapper.findById(MARKER_ID).orElseThrow().getVersion()).isEqualTo(1L);
  }

  @Test
  @DisplayName("같은 작성자가 다른 등록 업무폰으로 사진을 첨부하면, 요청한 업무폰을 수정 이벤트에 기록한다")
  void attach_sameAuthorOnAnotherPhone_savesPhotoAndEventWithRequestPhone() {
    // given: 현재 계정은 마커 작성자이며 요청에 다른 등록 업무폰을 사용한다.
    PhotoUploadUrlResponse response =
        photoService.createUploadUrl(
            MARKER_ID, createUploadRequest(), createContext(OTHER_REGISTERED_POLICE_PHONE_ID));
    MarkerPhoto photo = photoRepository.findById(response.photoId()).orElseThrow();
    objectStorage.simulateUpload(photo.objectKey());
    PhotoRequestContext attachContext =
        new PhotoRequestContext(
            new SuriMapAuthentication(PRECINCT_TEAM_ID, "APP", OTHER_REGISTERED_POLICE_PHONE_ID),
            ATTACH_IDEMPOTENCY_KEY);

    // when: 실제 사진 서비스를 통해 업로드한 사진을 마커에 첨부한다.
    PhotoAttachResult attached =
        photoService.attach(
            MARKER_ID,
            photo.id(),
            new PhotoAttachRequest(1024L, "image/jpeg", 640, 480, CHECKSUM_SHA256),
            attachContext);

    // then: 사진·부모 마커를 함께 변경하고 이번 요청의 업무폰을 이벤트에 기록한다.
    assertThat(attached.response().status()).isEqualTo("ATTACHED");
    assertThat(attached.response().markerVersion()).isEqualTo(2L);
    assertThat(photoRepository.findById(photo.id()).orElseThrow().status())
        .isEqualTo(PhotoStatus.ATTACHED);
    assertThat(markerMapper.findById(MARKER_ID).orElseThrow().getVersion()).isEqualTo(2L);
    assertThat(
            jdbcTemplate.queryForMap(
                """
        SELECT event_type, incident_id, source_entity_id,
            payload ->> 'opId' AS op_id, payload ->> 'policePhoneId' AS police_phone_id
        FROM event_dispatch_job WHERE source_entity_id = ?
        """,
                MARKER_ID))
        .containsEntry("event_type", "MARKER_UPDATED")
        .containsEntry("incident_id", INCIDENT_ID)
        .containsEntry("source_entity_id", MARKER_ID)
        .containsEntry("op_id", OP1_ID.toString())
        .containsEntry("police_phone_id", OTHER_REGISTERED_POLICE_PHONE_ID.toString());
  }

  @Test
  @DisplayName("같은 업로드 주소 요청을 재전송하면, 저장된 응답을 반환하고 대기 사진을 추가하지 않는다")
  void createUploadUrl_sameRequest_returnsStoredResponseWithoutAnotherPhoto() {
    // given: 업로드 주소와 대기 사진이 이미 저장된 요청이다.
    PhotoRequestContext context = createContext(ASSIGNED_POLICE_PHONE_ID);
    PhotoUploadUrlResponse first =
        photoService.createUploadUrl(MARKER_ID, createUploadRequest(), context);

    // when: 같은 키와 본문으로 업로드 주소를 다시 요청한다.
    PhotoUploadUrlResponse repeated =
        photoService.createUploadUrl(MARKER_ID, createUploadRequest(), context);

    // then: 주소·사진 ID·만료 시각을 재사용하고 DB에는 대기 사진 하나만 남는다.
    assertThat(repeated).isEqualTo(first);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT id FROM photo WHERE marker_id = ?", UUID.class, MARKER_ID))
        .containsExactly(first.photoId());
    assertThat(photoRepository.findById(first.photoId()).orElseThrow().status())
        .isEqualTo(PhotoStatus.PENDING_UPLOAD);
    assertThat(markerMapper.findById(MARKER_ID).orElseThrow().getVersion()).isEqualTo(1L);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT event_type FROM event_dispatch_job WHERE source_entity_id = ?",
                String.class,
                MARKER_ID))
        .isEmpty();
    assertCompletedRequest(IDEMPOTENCY_KEY);
  }

  @Test
  @DisplayName("같은 사진 첨부 요청을 재전송하면, 기존 응답을 반환하고 사진·마커·이벤트를 중복 변경하지 않는다")
  void attach_sameRequest_returnsStoredResponseWithoutDuplicateChanges() {
    // given: 업로드된 사진의 첨부 요청을 한 번 처리했다.
    PhotoUploadUrlResponse upload =
        photoService.createUploadUrl(
            MARKER_ID, createUploadRequest(), createContext(ASSIGNED_POLICE_PHONE_ID));
    MarkerPhoto photo = photoRepository.findById(upload.photoId()).orElseThrow();
    objectStorage.simulateUpload(photo.objectKey());
    PhotoRequestContext context =
        new PhotoRequestContext(
            new SuriMapAuthentication(PRECINCT_TEAM_ID, "APP", ASSIGNED_POLICE_PHONE_ID),
            ATTACH_IDEMPOTENCY_KEY);
    PhotoAttachRequest request =
        new PhotoAttachRequest(1024L, "image/jpeg", 640, 480, CHECKSUM_SHA256);
    PhotoAttachResult first = photoService.attach(MARKER_ID, photo.id(), request, context);

    // when: 같은 키와 본문으로 사진 첨부를 다시 요청한다.
    PhotoAttachResult repeated = photoService.attach(MARKER_ID, photo.id(), request, context);

    // then: 기존 응답을 재사용하며 사진·마커 버전은 2, 수정 이벤트는 하나로 유지된다.
    assertThat(repeated.response()).isEqualTo(first.response());
    assertThat(first.publishRequest()).isNotNull();
    assertThat(repeated.publishRequest()).isNull();
    MarkerPhoto savedPhoto = photoRepository.findById(photo.id()).orElseThrow();
    assertThat(savedPhoto.status()).isEqualTo(PhotoStatus.ATTACHED);
    assertThat(savedPhoto.version()).isEqualTo(2L);
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
  }

  @Test
  @DisplayName("현재 수색 차수가 없으면, op_required 오류를 반환하고 사진을 저장하지 않는다")
  void createUploadUrl_noCurrentOp_rejectsWithoutSavingPhoto() {
    // given: 마커의 수색 차수가 종료되어 활성 수색 차수가 없다.
    jdbcTemplate.update(
        "UPDATE operational_period SET status = 'ENDED', ended_at = NOW() WHERE id = ?", OP1_ID);

    // when & then: 실제 DB의 수색 차수 상태로 업로드를 거부한다.
    assertUploadRejected("op_required");
  }

  @Test
  @DisplayName("작성자의 활성 근무교대가 없으면, police_phone_not_assigned 오류를 반환하고 사진을 저장하지 않는다")
  void createUploadUrl_noActiveDutyShift_rejectsWithoutSavingPhoto() {
    // given: 사건 배정은 유지하지만 현재 계정의 근무교대가 종료되었다.
    jdbcTemplate.update(
        "UPDATE duty_shift SET status = 'ENDED', ended_at = NOW() WHERE id = ?", DUTY_SHIFT_ID);

    // when & then: 배정과 활성 근무교대의 실제 SQL 조회 결과를 구분한다.
    assertUploadRejected("police_phone_not_assigned");
  }

  @Test
  @DisplayName("다른 계정이 만든 마커에 요청하면, incident_access_denied 오류를 반환하고 사진을 저장하지 않는다")
  void createUploadUrl_anotherAuthor_rejectsWithoutSavingPhoto() {
    // given: 사건에 배정되어 있지만 마커 작성자는 다른 계정이다.
    jdbcTemplate.update(
        "UPDATE marker SET created_by_account_id = ? WHERE id = ?", SUPPORT_TEAM_ID, MARKER_ID);

    // when & then: 실제 마커의 작성 계정으로 사진 수정 권한을 판단한다.
    assertUploadRejected("incident_access_denied");
  }

  private void assertUploadRejected(String error) {
    assertThatThrownBy(
            () ->
                photoService.createUploadUrl(
                    MARKER_ID, createUploadRequest(), createContext(ASSIGNED_POLICE_PHONE_ID)))
        .isInstanceOf(PhotoApiException.class)
        .extracting("error")
        .isEqualTo(error);
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

  private PhotoUploadUrlRequest createUploadRequest() {
    return new PhotoUploadUrlRequest("image/jpeg", 1024L, CHECKSUM_SHA256);
  }

  private void assertCompletedRequest(String idempotencyKey) {
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT idempotency_status FROM idempotency_record WHERE idempotency_key = ?",
                String.class,
                idempotencyKey))
        .containsExactly("COMPLETED");
  }

  private PhotoRequestContext createContext(UUID policePhoneId) {
    return new PhotoRequestContext(
        new SuriMapAuthentication(PRECINCT_TEAM_ID, "APP", policePhoneId), IDEMPOTENCY_KEY);
  }
}
