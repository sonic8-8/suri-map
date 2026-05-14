package com.surimap.marker.seed.support;

import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.repository.MarkerCreateRecord;
import com.surimap.marker.repository.MarkerDeleteRecord;
import com.surimap.marker.repository.MarkerRecord;
import com.surimap.marker.repository.MarkerRepository;
import com.surimap.marker.repository.MarkerSeedRecord;
import com.surimap.marker.repository.MarkerUpdateRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** L5-T05B marker seed repository test double. */
public final class InMemoryMarkerRepository implements MarkerRepository {

  private final Map<UUID, MarkerRecord> records = new LinkedHashMap<>();

  @Override
  public void insertSeed(MarkerSeedRecord record) {
    records.putIfAbsent(record.id(), MarkerRecord.fromSeedRecord(record));
  }

  @Override
  public void insertCreate(MarkerCreateRecord record) {
    records.put(record.id(), MarkerRecord.fromCreateRecord(record));
  }

  @Override
  public Optional<MarkerRecord> findById(UUID markerId) {
    return Optional.ofNullable(records.get(markerId));
  }

  @Override
  public List<MarkerRecord> findByIds(List<UUID> markerIds) {
    return markerIds.stream().map(records::get).filter(java.util.Objects::nonNull).toList();
  }

  @Override
  public int updateMarker(MarkerUpdateRecord record) {
    MarkerRecord marker = records.get(record.id());
    if (marker == null || marker.getVersion() != record.expectedVersion()) {
      return 0;
    }
    if (MarkerStatus.DELETED.name().equals(marker.getStatus())) {
      return 0;
    }
    marker.setMarkerType(record.markerType().name());
    marker.setLocation(record.location());
    marker.setMemo(record.memo());
    marker.setStatus(record.status().name());
    marker.setVersion(record.version());
    return 1;
  }

  @Override
  public int updateMarkerStatusVersion(
      UUID markerId, long expectedVersion, String status, long version) {
    MarkerRecord marker = records.get(markerId);
    if (marker == null || marker.getVersion() != expectedVersion) {
      return 0;
    }
    if (MarkerStatus.DELETED.name().equals(marker.getStatus())) {
      return 0;
    }
    marker.setStatus(status);
    marker.setVersion(version);
    return 1;
  }

  @Override
  public int deleteMarker(MarkerDeleteRecord record) {
    MarkerRecord marker = records.get(record.id());
    if (marker == null || marker.getVersion() != record.expectedVersion()) {
      return 0;
    }
    if (MarkerStatus.DELETED.name().equals(marker.getStatus())) {
      return 0;
    }
    marker.setStatus(record.status().name());
    marker.setVersion(record.version());
    return 1;
  }

  public List<MarkerRecord> records() {
    return new ArrayList<>(records.values());
  }
}
