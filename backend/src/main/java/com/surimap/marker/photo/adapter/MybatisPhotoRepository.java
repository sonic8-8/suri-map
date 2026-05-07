package com.surimap.marker.photo.adapter;

import com.surimap.marker.photo.domain.MarkerPhoto;
import com.surimap.marker.photo.domain.PhotoStatus;
import com.surimap.marker.photo.repository.PhotoMapper;
import com.surimap.marker.photo.repository.PhotoRecord;
import com.surimap.marker.photo.repository.PhotoRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisPhotoRepository implements PhotoRepository {

  private final PhotoMapper photoMapper;

  public MybatisPhotoRepository(PhotoMapper photoMapper) {
    this.photoMapper = photoMapper;
  }

  @Override
  public MarkerPhoto save(MarkerPhoto photo) {
    photoMapper.upsert(
        photo.id(),
        photo.markerId(),
        photo.objectKey(),
        photo.status().name(),
        photo.attachedAt(),
        photo.contentType(),
        photo.sizeBytes(),
        photo.width(),
        photo.height(),
        photo.checksumSha256(),
        photo.uploadUrlExpiresAt(),
        photo.version());
    return photo;
  }

  @Override
  public Optional<MarkerPhoto> findById(UUID photoId) {
    return photoMapper.findById(photoId).map(PhotoRecord::toDomain);
  }

  @Override
  public long countByMarkerIdAndStatusIn(UUID markerId, Set<PhotoStatus> statuses) {
    Set<String> statusNames = statuses.stream().map(PhotoStatus::name).collect(Collectors.toSet());
    return photoMapper.countByMarkerIdAndStatusIn(markerId, statusNames);
  }
}
