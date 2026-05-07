package com.surimap.marker.repository;

import java.util.List;
import java.util.UUID;

public interface MarkerRepository {

  void insertSeed(MarkerSeedRecord record);

  List<MarkerRecord> findByIds(List<UUID> markerIds);
}
