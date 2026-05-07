package com.surimap.marker.photo.support;

import com.surimap.marker.photo.domain.MarkerPhoto;
import com.surimap.marker.photo.domain.PhotoStatus;
import com.surimap.marker.photo.repository.PhotoRepository;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPhotoRepository implements PhotoRepository {

  private final Map<UUID, MarkerPhoto> photos = new ConcurrentHashMap<>();

  @Override
  public MarkerPhoto save(MarkerPhoto photo) {
    photos.put(photo.id(), photo);
    return photo;
  }

  @Override
  public Optional<MarkerPhoto> findById(UUID photoId) {
    return Optional.ofNullable(photos.get(photoId));
  }

  @Override
  public long countByMarkerIdAndStatusIn(UUID markerId, Set<PhotoStatus> statuses) {
    return photos.values().stream()
        .filter(photo -> photo.markerId().equals(markerId))
        .filter(photo -> statuses.contains(photo.status()))
        .count();
  }
}
