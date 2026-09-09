package com.surimap.app.service.marker;

import static com.surimap.account.AccountIdentityCatalog.PRECINCT_TEAM_ID;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.INCIDENT_ID;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.OP1_ID;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.OP2_ID;
import static com.surimap.marker.photo.fixture.PhotoFixtures.CHECKSUM_SHA256;
import static com.surimap.policephone.PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.app.service.photo.PhotoRequestContext;
import com.surimap.domain.marker.MarkerMapper;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import com.surimap.marker.photo.domain.MarkerPhoto;
import com.surimap.marker.photo.domain.PhotoStatus;
import com.surimap.marker.photo.dto.MarkerCreatePhotoUploadUrlRequest;
import com.surimap.marker.photo.dto.PhotoUploadUrlResponse;
import com.surimap.marker.photo.repository.PhotoMapper;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "surimap.object-storage.provider=mock")
class MarkerPhotoDraftServiceTest extends PostGisIntegrationTestSupport {

  private static final UUID MARKER_ID = UUID.fromString("55555555-5555-5555-5555-555555550340");
  private static final UUID DUTY_SHIFT_ID = UUID.fromString("33333333-3333-3333-3333-333333330001");
  private static final String IDEMPOTENCY_KEY = "idem-marker-create-photo-upload-001";

  @Autowired private MarkerPhotoDraftService markerPhotoDraftService;
  @Autowired private PhotoMapper photoMapper;
  @Autowired private MarkerMapper markerMapper;

  @BeforeEach
  void setUp() {
    // 실제 사건 배정과 근무교대를 준비하되, 생성 전인 마커는 DB에 넣지 않는다.
    jdbcTemplate.update("DELETE FROM photo WHERE marker_id = ?", MARKER_ID);
    jdbcTemplate.update("DELETE FROM marker WHERE id = ?", MARKER_ID);
    jdbcTemplate.update("DELETE FROM event_dispatch_job WHERE incident_id = ?", INCIDENT_ID);
    jdbcTemplate.update(
        "DELETE FROM idempotency_record WHERE idempotency_key = ?", IDEMPOTENCY_KEY);
    jdbcTemplate.update("DELETE FROM duty_shift WHERE operational_period_id = ?", OP1_ID);
    jdbcTemplate.update("DELETE FROM incident_assignment WHERE incident_id = ?", INCIDENT_ID);
    jdbcTemplate.update(
        "UPDATE operational_period SET status = 'ENDED', ended_at = NOW() WHERE id = ?", OP2_ID);
    jdbcTemplate.update(
        """
        INSERT INTO incident (id, source_incident_id, title, status, opened_at, version, created_at, updated_at)
        VALUES (?, ?, 'Marker photo upload fixture', 'OPEN', NOW(), 1, NOW(), NOW())
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
  }

  @Test
  @DisplayName("마커 생성 전에 사진 업로드 주소를 요청하면, 앱이 지정한 마커 ID로 대기 사진을 저장한다")
  void createUploadUrl_beforeMarkerCreation_savesPendingPhotoForRequestedMarker() {
    // given: 배정된 계정이 근무 중이고, 앱이 정한 마커 ID는 아직 DB에 없다.
    assertThat(markerMapper.findById(MARKER_ID)).isEmpty();
    Instant requestedAt = Instant.now();

    // when: 실제 사진 업로드 주소 발급 서비스를 호출한다.
    PhotoUploadUrlResponse response =
        markerPhotoDraftService.createUploadUrl(createRequest(), createContext());

    // then: 15분 동안 유효한 주소와 대기 사진만 만들고, 마커와 이벤트는 만들지 않는다.
    MarkerPhoto photo = photoMapper.findById(response.photoId()).orElseThrow();
    String expectedObjectKey =
        "markers/" + INCIDENT_ID + "/" + MARKER_ID + "/" + response.photoId() + ".jpg";
    assertThat(response.uploadUrl())
        .isEqualTo("http://127.0.0.1:18080/mock-upload/" + expectedObjectKey);
    assertThat(response.expiresAt())
        .isBetween(requestedAt.plusSeconds(15 * 60), Instant.now().plusSeconds(15 * 60));
    assertThat(response.maxSizeBytes()).isEqualTo(10_485_760L);
    assertThat(response.version()).isEqualTo(1L);
    assertThat(photo.getMarkerId()).isEqualTo(MARKER_ID);
    assertThat(photo.getObjectKey()).isEqualTo(expectedObjectKey);
    assertThat(photo.getContentType()).isEqualTo("image/jpeg");
    assertThat(photo.getSizeBytes()).isEqualTo(1_048_576L);
    assertThat(photo.getChecksumSha256()).isEqualTo(CHECKSUM_SHA256);
    assertThat(photo.getStatus()).isEqualTo(PhotoStatus.PENDING_UPLOAD);
    assertThat(photo.getVersion()).isEqualTo(1L);
    assertThat(photo.getAttachedAt()).isNull();
    assertThat(markerMapper.findById(MARKER_ID)).isEmpty();
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
                IDEMPOTENCY_KEY))
        .containsExactly("COMPLETED");
  }

  @Test
  @DisplayName("마커 생성 전 같은 업로드 주소 요청을 재전송하면, 기존 응답을 반환하고 대기 사진을 추가하지 않는다")
  void createUploadUrl_sameRequest_returnsStoredResponseWithoutAnotherPhoto() {
    // given: 마커 생성 전에 사진 업로드 주소를 한 번 발급받았다.
    PhotoUploadUrlResponse first =
        markerPhotoDraftService.createUploadUrl(createRequest(), createContext());

    // when: 같은 요청 키와 본문으로 업로드 주소를 다시 요청한다.
    PhotoUploadUrlResponse repeated =
        markerPhotoDraftService.createUploadUrl(createRequest(), createContext());

    // then: 사진 ID·주소·만료 시각을 재사용하고 DB에는 대기 사진 하나만 남는다.
    assertThat(repeated).isEqualTo(first);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT id FROM photo WHERE marker_id = ?", UUID.class, MARKER_ID))
        .containsExactly(first.photoId());
    assertThat(photoMapper.findById(first.photoId()).orElseThrow().getStatus())
        .isEqualTo(PhotoStatus.PENDING_UPLOAD);
    assertThat(markerMapper.findById(MARKER_ID)).isEmpty();
  }

  private MarkerCreatePhotoUploadUrlRequest createRequest() {
    return new MarkerCreatePhotoUploadUrlRequest(
        MARKER_ID, INCIDENT_ID, OP1_ID, "image/jpeg", 1_048_576L, CHECKSUM_SHA256);
  }

  private PhotoRequestContext createContext() {
    return new PhotoRequestContext(
        new SuriMapAuthentication(PRECINCT_TEAM_ID, "APP", ASSIGNED_POLICE_PHONE_ID),
        IDEMPOTENCY_KEY);
  }
}
