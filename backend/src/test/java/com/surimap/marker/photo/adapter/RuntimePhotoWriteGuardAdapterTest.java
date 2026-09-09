package com.surimap.marker.photo.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.domain.marker.Marker;
import com.surimap.marker.adapter.MarkerRuntimeGuardMapper;
import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.domain.fixture.MarkerGeometryFixtures;
import com.surimap.marker.photo.domain.PhotoMarkerContext;
import com.surimap.marker.photo.exception.PhotoApiException;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import com.surimap.marker.photo.service.PhotoRequestContext;
import com.surimap.marker.repository.MarkerRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuntimePhotoWriteGuardAdapterTest {

  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final UUID MARKER_ID = UUID.fromString("4ca60e44-9cfe-410b-8794-2983baac0eac");
  private static final UUID PHOTO_ID = UUID.fromString("55555555-5555-5555-5555-555555550001");
  private static final UUID OP_ID = UUID.fromString("88888888-8888-8888-8888-888888880001");
  private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111110003");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000101");
  private static final UUID OTHER_REGISTERED_POLICE_PHONE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000301");
  private static final UUID DUTY_SHIFT_ID = UUID.fromString("33333333-3333-3333-3333-333333330001");

  private final FakeMarkerRepository markerRepository = new FakeMarkerRepository();
  private final FakeMarkerRuntimeGuardMapper guardMapper = new FakeMarkerRuntimeGuardMapper();
  private final RuntimePhotoWriteGuardAdapter adapter =
      new RuntimePhotoWriteGuardAdapter(markerRepository, guardMapper);

  @Test
  @DisplayName("APP 배정 계정과 등록 단말은 current OP marker의 upload-url 권한을 얻는다")
  void assignedAppAccountWithRegisteredPolicePhoneCanRequestUploadUrlForCurrentOpMarker() {
    markerRepository.marker = activeMarker();
    guardMapper.currentOpId = OP_ID;

    PhotoMarkerContext context = adapter.requireUploadUrlAccess(MARKER_ID, requestContext());

    assertThat(context)
        .isEqualTo(
            new PhotoMarkerContext(INCIDENT_ID, MARKER_ID, OP_ID, POLICE_PHONE_ID, "ACTIVE", 1L));
  }

  @Test
  @DisplayName("current OP가 없으면 photo upload-url은 op_required로 실패한다")
  void missingCurrentOpBlocksUploadUrl() {
    markerRepository.marker = activeMarker();
    guardMapper.currentOpId = null;

    assertThatThrownBy(() -> adapter.requireUploadUrlAccess(MARKER_ID, requestContext()))
        .isInstanceOf(PhotoApiException.class)
        .extracting("error")
        .isEqualTo("op_required");
  }

  @Test
  @DisplayName("같은 계정이 만든 marker라면 다른 등록 업무폰에서도 photo upload-url을 허용한다")
  void uploadUrlAllowsAnotherRegisteredPhoneForSameAccountMarker() {
    markerRepository.marker = activeMarker();
    guardMapper.currentOpId = OP_ID;

    PhotoMarkerContext context =
        adapter.requireUploadUrlAccess(MARKER_ID, requestContext(OTHER_REGISTERED_POLICE_PHONE_ID));

    assertThat(context)
        .isEqualTo(
            new PhotoMarkerContext(
                INCIDENT_ID, MARKER_ID, OP_ID, OTHER_REGISTERED_POLICE_PHONE_ID, "ACTIVE", 1L));
  }

  @Test
  @DisplayName("현재 계정의 활성 근무교대가 없으면 photo upload-url을 거부한다")
  void uploadUrlRejectsMissingActiveDutyShift() {
    markerRepository.marker = activeMarker();
    guardMapper.currentOpId = OP_ID;
    guardMapper.activeDutyShiftId = null;

    assertThatThrownBy(() -> adapter.requireUploadUrlAccess(MARKER_ID, requestContext()))
        .isInstanceOf(PhotoApiException.class)
        .extracting("error")
        .isEqualTo("police_phone_not_assigned");
  }

  @Test
  @DisplayName("APP photo write는 자신이 생성한 현장 마커에만 허용한다")
  void uploadUrlRejectsOtherAccountMarker() {
    markerRepository.marker = activeMarker(UUID.fromString("11111111-1111-1111-1111-111111119999"));
    guardMapper.currentOpId = OP_ID;

    assertThatThrownBy(() -> adapter.requireUploadUrlAccess(MARKER_ID, requestContext()))
        .isInstanceOf(PhotoApiException.class)
        .extracting("error")
        .isEqualTo("incident_access_denied");
  }

  private static PhotoRequestContext requestContext() {
    return requestContext(POLICE_PHONE_ID);
  }

  private static PhotoRequestContext requestContext(UUID policePhoneId) {
    return new PhotoRequestContext(
        new SuriMapAuthentication(ACCOUNT_ID, "APP", policePhoneId), "idem-photo-upload-url");
  }

  private static Marker activeMarker() {
    return activeMarker(ACCOUNT_ID);
  }

  private static Marker activeMarker(UUID accountId) {
    return Marker.builder()
        .id(MARKER_ID)
        .incidentId(INCIDENT_ID)
        .operationalPeriodId(OP_ID)
        .dutyShiftId(DUTY_SHIFT_ID)
        .createdByAccountId(accountId)
        .policePhoneId(POLICE_PHONE_ID)
        .markerType(MarkerType.CLUE)
        .location(MarkerGeometryFixtures.VALID_MARKER_POINT)
        .occurredAt(Instant.parse("2026-04-28T00:05:00Z"))
        .markerSource(MarkerSource.APP)
        .status(MarkerStatus.ACTIVE)
        .version(1L)
        .build();
  }

  private static final class FakeMarkerRepository implements MarkerRepository {

    private Marker marker;

    @Override
    public void insertSeed(Marker record) {}

    @Override
    public void insertCreate(Marker record) {}

    @Override
    public Optional<Marker> findById(UUID markerId) {
      return Optional.ofNullable(marker).filter(record -> record.getId().equals(markerId));
    }

    @Override
    public List<Marker> findByIds(List<UUID> markerIds) {
      return marker == null ? List.of() : List.of(marker);
    }

    @Override
    public int updateMarker(Marker record, long expectedVersion) {
      return 0;
    }

    @Override
    public int updateMarkerStatusVersion(
        UUID markerId, long expectedVersion, String status, long version) {
      return 0;
    }

    @Override
    public int deleteMarker(Marker record, long expectedVersion) {
      return 0;
    }
  }

  private static final class FakeMarkerRuntimeGuardMapper implements MarkerRuntimeGuardMapper {

    private UUID currentOpId = OP_ID;
    private UUID activeDutyShiftId = DUTY_SHIFT_ID;
    private int activeAssignmentsByAccountCount = 1;
    private int activeIncidentAssignmentCount = 1;

    @Override
    public Optional<String> findIncidentStatus(UUID incidentId) {
      return INCIDENT_ID.equals(incidentId) ? Optional.of("OPEN") : Optional.empty();
    }

    @Override
    public Optional<UUID> findCurrentOpId(UUID incidentId) {
      return INCIDENT_ID.equals(incidentId) ? Optional.ofNullable(currentOpId) : Optional.empty();
    }

    @Override
    public Optional<MarkerRuntimeGuardMapper.MarkerGuardRow> findMarkerGuardRow(UUID markerId) {
      return Optional.empty();
    }

    @Override
    public int countActiveAssignmentsByAccountId(UUID accountId) {
      return activeAssignmentsByAccountCount;
    }

    @Override
    public int countActiveIncidentAssignment(UUID incidentId, UUID accountId) {
      return activeIncidentAssignmentCount;
    }

    @Override
    public Optional<UUID> findActiveDutyShiftIdByAccount(UUID opId, UUID accountId) {
      if (OP_ID.equals(opId) && ACCOUNT_ID.equals(accountId)) {
        return Optional.ofNullable(activeDutyShiftId);
      }
      return Optional.empty();
    }
  }
}
