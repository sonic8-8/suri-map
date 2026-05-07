package com.surimap.marker.repository;

import com.surimap.marker.query.MarkerQueryFilters;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MarkerMapper extends MarkerRepository {

  @Override
  void insertSeed(@Param("record") MarkerSeedRecord record);

  @Override
  void insertCreate(@Param("record") MarkerCreateRecord record);

  @Override
  Optional<MarkerRecord> findById(@Param("markerId") UUID markerId);

  @Override
  List<MarkerRecord> findByIds(@Param("markerIds") List<UUID> markerIds);

  @Override
  int updateMarker(@Param("record") MarkerUpdateRecord record);

  @Override
  int deleteMarker(@Param("record") MarkerDeleteRecord record);

  List<MarkerRecord> findByIncident(
      @Param("incidentId") UUID incidentId, @Param("filters") MarkerQueryFilters filters);

  List<MarkerPhotoSummaryRow> findAttachedPhotoSummariesByMarkerIds(
      @Param("markerIds") List<UUID> markerIds);
}
