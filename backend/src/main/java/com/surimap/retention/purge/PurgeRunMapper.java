package com.surimap.retention.purge;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PurgeRunMapper {

  int insertIfAbsent(
      @Param("id") UUID id,
      @Param("incidentId") UUID incidentId,
      @Param("status") IncidentDataPurgeStatus status,
      @Param("closedAt") Instant closedAt,
      @Param("purgeDueAt") Instant purgeDueAt,
      @Param("environmentPolicy") PurgeEnvironmentPolicy environmentPolicy,
      @Param("now") Instant now);

  Optional<PurgeRunRecord> findByIncidentId(UUID incidentId);

  int transition(
      @Param("purgeRunId") UUID purgeRunId,
      @Param("status") IncidentDataPurgeStatus status,
      @Param("lastErrorCode") String lastErrorCode,
      @Param("completedAt") Instant completedAt,
      @Param("environmentPolicy") PurgeEnvironmentPolicy environmentPolicy,
      @Param("version") long version,
      @Param("now") Instant now);

  List<PurgeHookStepRecord> findHookSteps(UUID purgeRunId);

  void upsertHookStep(
      @Param("purgeRunId") UUID purgeRunId,
      @Param("hookName") PurgeHookName hookName,
      @Param("status") PurgeHookStatus status,
      @Param("purgedCount") long purgedCount,
      @Param("retainedCount") long retainedCount,
      @Param("errorCode") String errorCode,
      @Param("completedAt") Instant completedAt,
      @Param("now") Instant now);
}
