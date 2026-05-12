package com.surimap.maparea;

import com.surimap.maparea.query.SearchAreaAssignmentRow;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SearchAreaAssignmentMapper {

  void insert(SearchAreaAssignmentPersistenceRecord record);

  int revokeActiveByArea(
      @Param("searchAreaId") UUID searchAreaId,
      @Param("revokedAt") Instant revokedAt,
      @Param("updatedAt") Instant updatedAt);

  List<SearchAreaAssignmentRow> findActiveByOp(@Param("opId") UUID opId);

  List<SearchAreaAssignmentRow> findByArea(@Param("searchAreaId") UUID searchAreaId);
}
