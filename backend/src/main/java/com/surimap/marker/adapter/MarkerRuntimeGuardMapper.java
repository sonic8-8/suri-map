package com.surimap.marker.adapter;

import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MarkerRuntimeGuardMapper {

  Optional<String> findIncidentStatus(@Param("incidentId") UUID incidentId);

  int countActiveAssignmentsByAccountId(@Param("accountId") UUID accountId);

  int countActiveIncidentAssignment(
      @Param("incidentId") UUID incidentId, @Param("accountId") UUID accountId);

  int countRegisteredPolicePhoneForAccount(
      @Param("policePhoneId") UUID policePhoneId, @Param("accountId") UUID accountId);

  int countPolicePhoneAssignmentToIncident(
      @Param("policePhoneId") UUID policePhoneId, @Param("incidentId") UUID incidentId);
}
