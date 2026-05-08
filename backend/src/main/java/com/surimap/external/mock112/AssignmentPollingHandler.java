package com.surimap.external.mock112;

import com.surimap.external.ExternalAssignment;
import com.surimap.incident.service.IncidentAssignmentImportService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * mock 112 배정 변경을 감지한다.
 *
 * <p>중복 처리: externalAssignmentKey 기준. 실제 DB write는 IncidentImportService(또는 해당 서비스)를 통해 수행된다. 현재는
 * 감지·로깅만 수행하고, DB 연동은 L1-T04에서 완성한다.
 */
@Component
public class AssignmentPollingHandler {

  private static final Logger log = LoggerFactory.getLogger(AssignmentPollingHandler.class);

  /** 이미 처리된 externalAssignmentKey를 추적. 서버 재시작 시 초기화되므로 DB 기반 중복 체크가 최종 방어선. */
  private final Set<String> processedKeys = ConcurrentHashMap.newKeySet();

  private final IncidentAssignmentImportService incidentAssignmentImportService;

  public AssignmentPollingHandler(IncidentAssignmentImportService incidentAssignmentImportService) {
    this.incidentAssignmentImportService = incidentAssignmentImportService;
  }

  /**
   * 배정 변경을 처리한다.
   *
   * @param sourceIncidentId mock 112 사건 ID
   * @param assignments 현재 전체 배정 목록
   */
  public void handleAssignmentChanges(
      String sourceIncidentId, List<ExternalAssignment> assignments) {
    List<ExternalAssignment> candidates = new ArrayList<>();
    for (ExternalAssignment assignment : assignments) {
      String key = assignmentKey(assignment);
      if (!processedKeys.contains(key)) {
        // 새로 감지된 배정
        log.info(
            "새 배정 감지: incident={}, account={}, role={}, key={}",
            sourceIncidentId,
            assignment.accountId(),
            assignment.incidentRole(),
            key);
        candidates.add(assignment);
      }
    }
    incidentAssignmentImportService
        .importAssignmentChanges(sourceIncidentId, candidates)
        .ifPresentOrElse(
            result -> {
              for (ExternalAssignment assignment : candidates) {
                processedKeys.add(assignmentKey(assignment));
              }
              log.info(
                  "incident_assignment 반영 완료: incident={}, changedAccounts={}, version={}",
                  sourceIncidentId,
                  result.changedAccountIds(),
                  result.version());
            },
            () -> {
              for (ExternalAssignment assignment : candidates) {
                processedKeys.add(assignmentKey(assignment));
              }
            });
  }

  /** 특정 사건의 초기 배정을 일괄 등록한다 (import 시 사용). */
  public void registerInitialAssignments(List<ExternalAssignment> assignments) {
    for (ExternalAssignment a : assignments) {
      processedKeys.add(assignmentKey(a));
    }
  }

  public void reset() {
    processedKeys.clear();
  }

  private String assignmentKey(ExternalAssignment assignment) {
    return assignment.externalAssignmentKey() == null
        ? assignment.accountId()
        : assignment.externalAssignmentKey();
  }
}
