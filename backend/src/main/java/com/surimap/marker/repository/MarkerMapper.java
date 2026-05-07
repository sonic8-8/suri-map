package com.surimap.marker.repository;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MarkerMapper extends MarkerRepository {

  @Override
  void insertSeed(@Param("record") MarkerSeedRecord record);

  @Override
  List<MarkerRecord> findByIds(@Param("markerIds") List<UUID> markerIds);
}
