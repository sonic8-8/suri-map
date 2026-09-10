package com.surimap.domain.marker;

import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MarkerAccessMapper {

  MarkerWriteAccessData findWriteAccessData(
      @Param("incidentId") UUID incidentId,
      @Param("opId") UUID opId,
      @Param("accountId") UUID accountId);
}
