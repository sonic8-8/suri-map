package com.surimap.summary;

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
}
