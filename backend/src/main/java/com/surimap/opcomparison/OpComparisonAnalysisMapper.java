package com.surimap.opcomparison;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OpComparisonAnalysisMapper {

  int insert(@Param("record") OpComparisonAnalysisRecord record);

  Optional<OpComparisonAnalysisRecord> findById(@Param("id") UUID id);

  Optional<OpComparisonAnalysisRecord> findByRequestHash(@Param("requestHash") String requestHash);

  int markDeterministicReady(
      @Param("id") UUID id,
      @Param("metricsJson") String metricsJson,
      @Param("diffFactsJson") String diffFactsJson,
      @Param("commonRegionsGeojson") String commonRegionsGeojson,
      @Param("narrativeStatus") OpComparisonNarrativeStatus narrativeStatus,
      @Param("failureReason") String failureReason,
      @Param("generatedAt") Instant generatedAt,
      @Param("updatedAt") Instant updatedAt);

  int updateNarrativeResult(
      @Param("id") UUID id,
      @Param("status") OpComparisonAnalysisStatus status,
      @Param("narrativeStatus") OpComparisonNarrativeStatus narrativeStatus,
      @Param("observationsJson") String observationsJson,
      @Param("failureReason") String failureReason,
      @Param("generatedAt") Instant generatedAt,
      @Param("updatedAt") Instant updatedAt);
}
