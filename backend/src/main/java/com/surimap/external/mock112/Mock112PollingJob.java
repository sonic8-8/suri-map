package com.surimap.external.mock112;

import com.surimap.external.ExternalIncident;
import com.surimap.external.ExternalIncidentAdapter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * mock 112 서버를 주기적으로 polling하여 새 사건과 배정 변경을 감지한다.
 *
 * <p>mock112.enabled=true, dev profile에서만 활성화된다. Suri-Map이 직접 사건을 생성하지 않고, polling으로 감지만 한다.
 */
@Component
@ConditionalOnProperty(name = "mock112.enabled", havingValue = "true")
public class Mock112PollingJob {

  private static final Logger log = LoggerFactory.getLogger(Mock112PollingJob.class);

  private final ExternalIncidentAdapter adapter;
  private final ImportCandidateStore candidateStore;
  private final AssignmentPollingHandler assignmentHandler;

  public Mock112PollingJob(
      ExternalIncidentAdapter adapter,
      ImportCandidateStore candidateStore,
      AssignmentPollingHandler assignmentHandler) {
    this.adapter = adapter;
    this.candidateStore = candidateStore;
    this.assignmentHandler = assignmentHandler;
  }

  /** READY 상태의 새 사건을 감지하여 후보 목록에 추가한다. */
  @Scheduled(fixedDelayString = "${mock112.polling.interval-ms:5000}")
  public void pollNewIncidents() {
    try {
      List<ExternalIncident> readyIncidents = adapter.fetchReadyIncidents();
      if (!readyIncidents.isEmpty()) {
        int before = candidateStore.candidateCount();
        candidateStore.addCandidates(readyIncidents);
        int added = candidateStore.candidateCount() - before;
        if (added > 0) {
          log.info("mock 112 새 사건 {}건 감지 (총 후보 {}건)", added, candidateStore.candidateCount());
        }
      }
    } catch (Exception e) {
      log.debug("mock 112 polling 오류 (비치명적): {}", e.getMessage());
    }
  }

  /** 이미 import된 사건들의 배정 변경을 감지한다. */
  @Scheduled(
      fixedDelayString = "${mock112.polling.interval-ms:5000}",
      initialDelayString = "${mock112.polling.interval-ms:5000}")
  public void pollAssignmentChanges() {
    try {
      List<String> importedIds = candidateStore.getImportedIds();
      for (String sourceIncidentId : importedIds) {
        ExternalIncident incident = adapter.fetchIncident(sourceIncidentId);
        if (incident != null && incident.assignments() != null) {
          assignmentHandler.handleAssignmentChanges(sourceIncidentId, incident.assignments());
        }
      }
    } catch (Exception e) {
      log.debug("mock 112 assignment polling 오류 (비치명적): {}", e.getMessage());
    }
  }
}
