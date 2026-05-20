package com.surimap.dutyshift;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DutyShiftMapper {

  void insert(DutyShift dutyShift);

  Optional<DutyShift> findById(@Param("id") UUID id);

  List<DutyShift> findByFilters(
      @Param("incidentId") UUID incidentId,
      @Param("opId") UUID opId,
      @Param("policePhoneId") UUID policePhoneId,
      @Param("accountId") UUID accountId,
      @Param("status") String status);

  Optional<UUID> findActiveAssignmentId(
      @Param("incidentId") UUID incidentId, @Param("accountId") UUID accountId);

  void end(
      @Param("id") UUID id,
      @Param("endedByAccountId") UUID endedByAccountId,
      @Param("endedAt") Instant endedAt,
      @Param("version") long version);
}
