package com.surimap.maparea.boundary;

import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SearchAreaBoundaryAlertMapper {

  Optional<SearchAreaBoundaryAlertContextRow> findAssignedTeamAreaContext(
      @Param("searchAreaId") UUID searchAreaId, @Param("accountId") UUID accountId);

  void insert(SearchAreaBoundaryAlertPersistenceRecord record);
}
