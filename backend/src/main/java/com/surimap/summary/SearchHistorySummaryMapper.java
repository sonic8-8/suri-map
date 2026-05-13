package com.surimap.summary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SearchHistorySummaryMapper {

  List<SearchHistorySummaryRow> findByOp(
      @Param("opId") UUID opId,
      @Param("incidentId") UUID incidentId,
      @Param("scopeType") String scopeType,
      @Param("scopeId") UUID scopeId,
      @Param("dutyShiftId") UUID dutyShiftId,
      @Param("status") String status);

  String sourceFingerprintForScope(
      @Param("opId") UUID opId, @Param("dutyShiftId") UUID dutyShiftId);

  String sourceEvidenceForScope(@Param("opId") UUID opId, @Param("dutyShiftId") UUID dutyShiftId);

  List<SearchHistorySummaryRow> findReadyOrFailedByScopeWithDifferentHash(
      @Param("opId") UUID opId,
      @Param("dutyShiftId") UUID dutyShiftId,
      @Param("sourceDataHash") String sourceDataHash);

  int insertGenerationRequest(
      @Param("summaryId") UUID summaryId,
      @Param("opId") UUID opId,
      @Param("dutyShiftId") UUID dutyShiftId,
      @Param("generationStatus") String generationStatus,
      @Param("content") String content,
      @Param("sourceDataHash") String sourceDataHash,
      @Param("sourceReadiness") String sourceReadiness,
      @Param("requestedByAccountId") UUID requestedByAccountId,
      @Param("generatedAt") Instant generatedAt,
      @Param("version") long version,
      @Param("createdAt") Instant createdAt,
      @Param("updatedAt") Instant updatedAt);

  void updateGenerationResult(
      @Param("summaryId") UUID summaryId,
      @Param("generationStatus") String generationStatus,
      @Param("content") String content,
      @Param("sourceReadiness") String sourceReadiness,
      @Param("generatedAt") Instant generatedAt,
      @Param("updatedAt") Instant updatedAt);

  void markStaleByIds(@Param("summaryIds") List<UUID> summaryIds, @Param("updatedAt") Instant updatedAt);
}
