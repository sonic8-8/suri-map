package com.surimap.marker.seed.support;

import com.surimap.marker.repository.MarkerCreateRecord;
import com.surimap.marker.repository.MarkerRecord;
import com.surimap.marker.repository.MarkerRepository;
import com.surimap.marker.repository.MarkerSeedRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
  public List<MarkerRecord> findByIds(List<UUID> markerIds) {
    return markerIds.stream().map(records::get).filter(java.util.Objects::nonNull).toList();
  }

  public List<MarkerRecord> records() {
    return new ArrayList<>(records.values());
  }
}
