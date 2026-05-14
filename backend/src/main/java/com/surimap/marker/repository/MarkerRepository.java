package com.surimap.marker.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarkerRepository {

  void insertSeed(MarkerSeedRecord record);

  void insertCreate(MarkerCreateRecord record);

  Optional<MarkerRecord> findById(UUID markerId);

  List<MarkerRecord> findByIds(List<UUID> markerIds);

  int updateMarker(MarkerUpdateRecord record);

  int updateMarkerStatusVersion(UUID markerId, long expectedVersion, String status, long version);

  int deleteMarker(MarkerDeleteRecord record);
}
