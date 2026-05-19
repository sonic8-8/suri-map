package com.surimap.marker.photo.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.marker.adapter.MarkerRuntimeGuardMapper;
import com.surimap.marker.photo.domain.PhotoMarkerContext;
import com.surimap.marker.photo.exception.PhotoApiException;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import com.surimap.marker.photo.service.PhotoRequestContext;
import com.surimap.marker.repository.MarkerCreateRecord;
import com.surimap.marker.repository.MarkerDeleteRecord;
import com.surimap.marker.repository.MarkerRecord;
import com.surimap.marker.repository.MarkerRepository;
import com.surimap.marker.repository.MarkerSeedRecord;
import com.surimap.marker.repository.MarkerUpdateRecord;
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
  @DisplayName("등록되지 않은 PolicePhone은 photo attach가 거부된다")
  void unregisteredPolicePhoneCannotAttachPhoto() {
    markerRepository.marker = activeMarker();
    guardMapper.currentOpId = OP_ID;
    guardMapper.registeredPolicePhoneCount = 0;

    assertThatThrownBy(() -> adapter.requireAttachAccess(MARKER_ID, PHOTO_ID, requestContext()))
        .isInstanceOf(PhotoApiException.class)
        .extracting("error")
        .isEqualTo("police_phone_not_registered");
  }

  private static PhotoRequestContext requestContext() {
    return new PhotoRequestContext(
        new SuriMapAuthentication(ACCOUNT_ID, "APP", POLICE_PHONE_ID), "idem-photo-upload-url");
  }

  private static MarkerRecord activeMarker() {
    MarkerRecord marker = new MarkerRecord();
    marker.setId(MARKER_ID);
    marker.setIncidentId(INCIDENT_ID);
    marker.setOperationalPeriodId(OP_ID);
    marker.setPolicePhoneId(POLICE_PHONE_ID);
    marker.setStatus("ACTIVE");
    marker.setVersion(1L);
    return marker;
  }

  private static final class FakeMarkerRepository implements MarkerRepository {

    private MarkerRecord marker;

    @Override
    public void insertSeed(MarkerSeedRecord record) {}

    @Override
    public void insertCreate(MarkerCreateRecord record) {}

    @Override
    public Optional<MarkerRecord> findById(UUID markerId) {
      return Optional.ofNullable(marker).filter(record -> record.getId().equals(markerId));
    }

    @Override
    public List<MarkerRecord> findByIds(List<UUID> markerIds) {
      return marker == null ? List.of() : List.of(marker);
    }

    @Override
    public int updateMarker(MarkerUpdateRecord record) {
      return 0;
    }

    @Override
    public int updateMarkerStatusVersion(
        UUID markerId, long expectedVersion, String status, long version) {
      return 0;
    }

    @Override
    public int deleteMarker(MarkerDeleteRecord record) {
      return 0;
    }
  }

  private static final class FakeMarkerRuntimeGuardMapper implements MarkerRuntimeGuardMapper {

    private UUID currentOpId = OP_ID;
    private int activeAssignmentsByAccountCount = 1;
    private int activeIncidentAssignmentCount = 1;
    private int registeredPolicePhoneCount = 1;

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
    public int countRegisteredPolicePhone(UUID policePhoneId) {
      return registeredPolicePhoneCount;
    }
  }
}
