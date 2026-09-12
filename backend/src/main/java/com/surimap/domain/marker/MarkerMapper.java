package com.surimap.domain.marker;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MarkerMapper {

  void insertSeed(@Param("marker") Marker marker);

  void insertCreate(@Param("marker") Marker marker);

  Optional<Marker> findById(@Param("markerId") UUID markerId);

  List<Marker> findByIds(@Param("markerIds") List<UUID> markerIds);

  int updateMarker(@Param("marker") Marker marker, @Param("expectedVersion") long expectedVersion);

  int updateMarkerStatusVersion(
      @Param("markerId") UUID markerId,
      @Param("expectedVersion") long expectedVersion,
      @Param("status") String status,
      @Param("version") long version);

  int deleteMarker(@Param("marker") Marker marker, @Param("expectedVersion") long expectedVersion);

  List<Marker> findByIncident(
      @Param("incidentId") UUID incidentId,
      @Param("opId") UUID opId,
      @Param("type") MarkerType type,
      @Param("status") MarkerStatus status);

  List<AttachedPhotoRow> findAttachedPhotoSummariesByMarkerIds(
      @Param("markerIds") List<UUID> markerIds);

  record AttachedPhotoRow(
      UUID markerId,
      UUID photoId,
      String objectKey,
      String status,
      Long version,
      String contentType,
      Long sizeBytes,
      Instant attachedAt) {}
}
