package com.surimap.marker.adapter;

import com.surimap.marker.domain.MarkerSource;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MarkerRuntimeGuardMapper {

  Optional<String> findIncidentStatus(@Param("incidentId") UUID incidentId);

  Optional<UUID> findCurrentOpId(@Param("incidentId") UUID incidentId);

  Optional<MarkerGuardRow> findMarkerGuardRow(@Param("markerId") UUID markerId);

  int countActiveAssignmentsByAccountId(@Param("accountId") UUID accountId);

  int countActiveIncidentAssignment(
      @Param("incidentId") UUID incidentId, @Param("accountId") UUID accountId);

  Optional<UUID> findActiveDutyShiftIdByAccount(
      @Param("opId") UUID opId, @Param("accountId") UUID accountId);

  record MarkerGuardRow(
      UUID id,
      UUID incidentId,
      UUID operationalPeriodId,
      UUID createdByAccountId,
      UUID policePhoneId,
      MarkerSource markerSource) {}
}
