package com.surimap.path;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SearchPathMapper {

  Optional<UUID> findActiveDutyShiftId(
      @Param("opId") UUID opId, @Param("policePhoneId") UUID policePhoneId);

  void insertPath(SearchPathPersistenceRecord record);

  int updatePath(SearchPathPersistenceRecord record);

  int endPath(
      @Param("id") UUID id,
      @Param("endedAt") Instant endedAt,
      @Param("version") long version,
      @Param("updatedAt") Instant updatedAt);

  Optional<SearchPathReadRecord> findPathById(@Param("id") UUID id);

  List<SearchPathReadRecord> findAllPaths();

  void deleteSegments(@Param("pathId") UUID pathId);

  void insertSegment(SearchPathSegmentPersistenceRecord record);

  List<SearchPathSegmentReadRecord> findSegmentsByPathId(@Param("pathId") UUID pathId);

  void deleteExcludedPoints(@Param("pathId") UUID pathId);

  void insertExcludedPoint(SearchPathExcludedPointPersistenceRecord record);

  List<SearchPathExcludedPointReadRecord> findExcludedPointsByPathId(@Param("pathId") UUID pathId);
}
