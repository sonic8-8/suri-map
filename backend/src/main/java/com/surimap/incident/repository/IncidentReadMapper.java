package com.surimap.incident.repository;

import com.surimap.incident.repository.IncidentReadRows.AssignmentRow;
import com.surimap.incident.repository.IncidentReadRows.AssignmentTargetRow;
import com.surimap.incident.repository.IncidentReadRows.DetailRow;
import com.surimap.incident.repository.IncidentReadRows.ListRow;
import com.surimap.incident.repository.IncidentReadRows.MissingPersonRow;
import com.surimap.incident.repository.IncidentReadRows.TerminalDetailRow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 진행 중 사건 목록·상세 DTO를 조립하기 위한 read-only MyBatis mapper. */
@Mapper
public interface IncidentReadMapper {

  List<ListRow> findActiveListByAccountId(
      @Param("accountId") String accountId, @Param("status") String status);

  Optional<DetailRow> findActiveDetailByIncidentIdAndAccountId(
      @Param("incidentId") UUID incidentId, @Param("accountId") String accountId);

  Optional<TerminalDetailRow> findTerminalDetailByIncidentIdAndAccountId(
      @Param("incidentId") UUID incidentId, @Param("accountId") String accountId);

  int countIncidentById(@Param("incidentId") UUID incidentId);

  int countActiveAssignmentsByAccountId(@Param("accountId") String accountId);

  Optional<MissingPersonRow> findMissingPersonByIncidentId(@Param("incidentId") UUID incidentId);

  List<AssignmentRow> findActiveAssignmentsByIncidentId(@Param("incidentId") UUID incidentId);

  List<AssignmentTargetRow> findActiveAssignmentTargetsByIncidentId(
      @Param("incidentId") UUID incidentId);
}
