package com.surimap.marker.photo.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PhotoMapper {

  void upsert(
      @Param("id") UUID id,
      @Param("markerId") UUID markerId,
      @Param("objectKey") String objectKey,
      @Param("status") String status,
      @Param("attachedAt") Instant attachedAt,
      @Param("contentType") String contentType,
      @Param("sizeBytes") long sizeBytes,
      @Param("width") Integer width,
      @Param("height") Integer height,
      @Param("checksumSha256") String checksumSha256,
      @Param("uploadUrlExpiresAt") Instant uploadUrlExpiresAt,
      @Param("version") long version);

  Optional<PhotoRecord> findById(@Param("photoId") UUID photoId);

  long countByMarkerIdAndStatusIn(
      @Param("markerId") UUID markerId, @Param("statuses") Set<String> statuses);
}
