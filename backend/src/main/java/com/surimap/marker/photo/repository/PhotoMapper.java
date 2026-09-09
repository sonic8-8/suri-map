package com.surimap.marker.photo.repository;

import com.surimap.marker.photo.domain.MarkerPhoto;
import com.surimap.marker.photo.domain.PhotoStatus;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PhotoMapper {

  void upsert(@Param("photo") MarkerPhoto photo);

  Optional<MarkerPhoto> findById(@Param("photoId") UUID photoId);

  int failPendingPhoto(
      @Param("photoId") UUID photoId, @Param("expectedVersion") long expectedVersion);

  int attachPendingPhoto(
      @Param("photo") MarkerPhoto photo, @Param("expectedVersion") long expectedVersion);

  long countByMarkerIdAndStatusIn(
      @Param("markerId") UUID markerId, @Param("statuses") Set<PhotoStatus> statuses);
}
