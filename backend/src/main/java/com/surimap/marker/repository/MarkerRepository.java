package com.surimap.marker.repository;

import com.surimap.domain.marker.Marker;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarkerRepository {

  void insertSeed(Marker marker);

  void insertCreate(Marker marker);

  Optional<Marker> findById(UUID markerId);

  List<Marker> findByIds(List<UUID> markerIds);

  int updateMarker(Marker marker, long expectedVersion);

  int updateMarkerStatusVersion(UUID markerId, long expectedVersion, String status, long version);

  int deleteMarker(Marker marker, long expectedVersion);
}
