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

  void insertGenerationRequest(
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
}
