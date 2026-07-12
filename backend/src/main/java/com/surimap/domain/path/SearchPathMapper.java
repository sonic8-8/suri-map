package com.surimap.domain.path;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SearchPathMapper {

  Optional<UUID> findActiveDutyShiftIdByAccount(
      @Param("opId") UUID opId, @Param("accountId") UUID accountId);

  int insertPath(SearchPath path);

  int updatePath(SearchPath path);

  int endPath(
      @Param("id") UUID id,
      @Param("endedAt") Instant endedAt,
      @Param("version") long version,
      @Param("updatedAt") Instant updatedAt);

  int updateLifecycleStatus(
      @Param("id") UUID id,
      @Param("status") String status,
      @Param("endedAt") Instant endedAt,
      @Param("version") long version,
      @Param("updatedAt") Instant updatedAt);

  Optional<SearchPath> findPathById(@Param("id") UUID id);

  List<SearchPath> findAllPaths();

  List<SearchPath> findPaths(
      @Param("incidentId") UUID incidentId,
      @Param("opId") UUID opId,
      @Param("accountId") UUID accountId);

  void insertLifecycleEvent(SearchPathLifecycleEvent event);

  List<SearchPathLifecycleEvent> findLifecycleEventsByPathId(@Param("pathId") UUID pathId);

  void deleteSegments(@Param("pathId") UUID pathId);

  void insertSegment(SearchPathSegment segment);

  List<SearchPathSegment> findSegmentsByPathId(@Param("pathId") UUID pathId);

  void deleteExcludedPoints(@Param("pathId") UUID pathId);

  void insertExcludedPoint(SearchPathExcludedPoint point);

  List<SearchPathExcludedPoint> findExcludedPointsByPathId(@Param("pathId") UUID pathId);
}
