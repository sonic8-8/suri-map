package com.surimap.handover;

import com.surimap.handover.query.HandoverMemoRow;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HandoverMemoMapper {

  void insert(HandoverMemo memo);

  List<HandoverMemoRow> findByContext(
      @Param("incidentId") UUID incidentId,
      @Param("opId") UUID opId,
      @Param("memoTargetType") String memoTargetType,
      @Param("memoTargetId") UUID memoTargetId);
}
