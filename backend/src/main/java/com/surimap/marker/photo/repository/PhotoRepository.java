package com.surimap.marker.photo.repository;

import com.surimap.marker.photo.domain.MarkerPhoto;
import com.surimap.marker.photo.domain.PhotoStatus;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PhotoRepository {

  MarkerPhoto save(MarkerPhoto photo);

  Optional<MarkerPhoto> findById(UUID photoId);

  long countByMarkerIdAndStatusIn(UUID markerId, Set<PhotoStatus> statuses);
}
