package com.surimap.opcomparison;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OpComparisonRegionFactMapper {

  default List<OpComparisonRegionFact> findRegionFacts(
      List<UUID> opIds, BigDecimal bufferMeters, BigDecimal minimumAreaSquareMeters) {
    if (opIds == null || opIds.size() < 2) {
      return List.of();
    }
    return findRegionFactRows(opIds, bufferMeters, minimumAreaSquareMeters).stream()
        .map(OpComparisonRegionFactRow::toFact)
        .toList();
  }

  List<OpComparisonRegionFactRow> findRegionFactRows(
      @Param("opIds") List<UUID> opIds,
      @Param("bufferMeters") BigDecimal bufferMeters,
      @Param("minimumAreaSquareMeters") BigDecimal minimumAreaSquareMeters);
}
