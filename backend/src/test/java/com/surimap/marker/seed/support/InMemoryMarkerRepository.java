package com.surimap.marker.seed.support;

import com.surimap.domain.marker.Marker;
import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerSupportRequestType;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.repository.MarkerRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** L5-T05B marker seed repository test double. */
public final class InMemoryMarkerRepository implements MarkerRepository {

  private final Map<UUID, Marker> records = new LinkedHashMap<>();

  @Override
  public void insertSeed(Marker record) {
    records.putIfAbsent(record.getId(), copy(record));
  }

  @Override
  public void insertCreate(Marker record) {
    records.put(record.getId(), copy(record));
  }

  @Override
  public Optional<Marker> findById(UUID markerId) {
    return Optional.ofNullable(records.get(markerId)).map(this::copy);
  }

  @Override
  public List<Marker> findByIds(List<UUID> markerIds) {
    return markerIds.stream()
        .map(records::get)
        .filter(java.util.Objects::nonNull)
        .map(this::copy)
        .toList();
  }

  @Override
  public int updateMarker(Marker record, long expectedVersion) {
    Marker marker = records.get(record.getId());
    if (marker == null || marker.getVersion() != expectedVersion) {
      return 0;
    }
    if (MarkerStatus.DELETED.name().equals(marker.getStatus())) {
      return 0;
    }
    records.put(record.getId(), copy(record));
    return 1;
  }

  @Override
  public int updateMarkerStatusVersion(
      UUID markerId, long expectedVersion, String status, long version) {
    Marker marker = records.get(markerId);
    if (marker == null || marker.getVersion() != expectedVersion) {
      return 0;
    }
    if (MarkerStatus.DELETED.name().equals(marker.getStatus())) {
      return 0;
    }
    records.put(markerId, copy(marker, status, version));
    return 1;
  }

  @Override
  public int deleteMarker(Marker record, long expectedVersion) {
    Marker marker = records.get(record.getId());
    if (marker == null || marker.getVersion() != expectedVersion) {
      return 0;
    }
    if (MarkerStatus.DELETED.name().equals(marker.getStatus())) {
      return 0;
    }
    records.put(record.getId(), copy(marker, record.getStatus(), record.getVersion()));
    return 1;
  }

  public List<Marker> records() {
    return records.values().stream().map(this::copy).toList();
  }

  private Marker copy(Marker marker) {
    return copy(marker, marker.getStatus(), marker.getVersion());
  }

  private Marker copy(Marker marker, String status, long version) {
    return Marker.builder()
        .id(marker.getId())
        .incidentId(marker.getIncidentId())
        .operationalPeriodId(marker.getOperationalPeriodId())
        .dutyShiftId(marker.getDutyShiftId())
        .markerType(MarkerType.valueOf(marker.getMarkerType()))
        .supportRequestType(
            marker.getSupportRequestType() == null
                ? null
                : MarkerSupportRequestType.valueOf(marker.getSupportRequestType()))
        .location((org.locationtech.jts.geom.Point) marker.getLocation().copy())
        .memo(marker.getMemo())
        .occurredAt(marker.getOccurredAt())
        .createdByAccountId(marker.getCreatedByAccountId())
        .policePhoneId(marker.getPolicePhoneId())
        .markerSource(MarkerSource.valueOf(marker.getMarkerSource()))
        .status(MarkerStatus.valueOf(status))
        .version(version)
        .build();
  }
}
