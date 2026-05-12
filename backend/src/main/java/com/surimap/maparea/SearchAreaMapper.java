package com.surimap.maparea;

import com.surimap.maparea.query.SearchAreaFilters;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.locationtech.jts.geom.Polygon;

@Mapper
public interface SearchAreaMapper {

  void insert(SearchAreaPersistenceRecord record);

  void insertHistory(SearchAreaHistoryPersistenceRecord record);

  Optional<SearchAreaReadRecord> findById(@Param("id") UUID id);

  int updateMutation(
      @Param("id") UUID id,
      @Param("status") String status,
      @Param("geometry") Polygon geometry,
      @Param("version") long version,
      @Param("updatedAt") Instant updatedAt);

  int countActiveOverallByIncident(@Param("incidentId") UUID incidentId);

  Optional<SearchAreaReadRecord> findActiveOverallByIncident(@Param("incidentId") UUID incidentId);

  List<SearchAreaReadRecord> findByIncident(
      @Param("incidentId") UUID incidentId, @Param("filters") SearchAreaFilters filters);

  List<SearchAreaReadRecord> findByOp(
      @Param("opId") UUID opId, @Param("filters") SearchAreaFilters filters);
}
