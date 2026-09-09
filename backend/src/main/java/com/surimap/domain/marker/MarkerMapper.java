package com.surimap.domain.marker;

import com.surimap.marker.query.MarkerQueryFilters;
import com.surimap.marker.repository.MarkerPhotoSummaryRow;
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
      @Param("incidentId") UUID incidentId, @Param("filters") MarkerQueryFilters filters);

  List<MarkerPhotoSummaryRow> findAttachedPhotoSummariesByMarkerIds(
      @Param("markerIds") List<UUID> markerIds);
}
