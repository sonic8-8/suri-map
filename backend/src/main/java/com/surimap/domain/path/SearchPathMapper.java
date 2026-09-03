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

  int updatePathVersion(
      @Param("id") UUID id,
      @Param("expectedVersion") long expectedVersion,
      @Param("nextVersion") long nextVersion,
      @Param("updatedAt") Instant updatedAt);

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

  Optional<SearchPath> findPathMetadataForUpdate(@Param("id") UUID id);

  List<SearchPath> findAllPaths();

  List<SearchPath> findPaths(
      @Param("incidentId") UUID incidentId,
      @Param("opId") UUID opId,
      @Param("accountId") UUID accountId);

  void insertGpsPoints(
      @Param("pathId") UUID pathId,
      @Param("pointOffset") int pointOffset,
      @Param("points") List<GpsPoint> points,
      @Param("createdAt") Instant createdAt);

  int findNextGpsPointOrder(@Param("pathId") UUID pathId);

  List<GpsPoint> findGpsPointsByPathId(@Param("pathId") UUID pathId);

  void insertLifecycleEvent(SearchPathLifecycleEvent event);

  List<SearchPathLifecycleEvent> findLifecycleEventsByPathId(@Param("pathId") UUID pathId);

  void insertSegments(@Param("segments") List<SearchPathSegment> segments);

  Optional<SearchPathSegment> findSegmentById(@Param("id") UUID id);

  int updateSegmentCorrection(
      @Param("segment") SearchPathSegment segment,
      @Param("expectedVersion") long expectedVersion,
      @Param("updatedAt") Instant updatedAt);

  List<SearchPathSegment> findSegmentsByPathId(@Param("pathId") UUID pathId);

  void insertExcludedPoints(@Param("excludedPoints") List<SearchPathExcludedPoint> excludedPoints);

  List<SearchPathExcludedPoint> findExcludedPointsByPathId(@Param("pathId") UUID pathId);
}
