package com.surimap.marker.repository;

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
}
