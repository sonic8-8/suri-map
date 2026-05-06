package com.surimap.external.mock112;

import com.surimap.external.ExternalIncident;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** polling으로 감지된 import 후보 사건을 관리한다. dev/시연 전용 — 웹에서 "새 배정 사건 N건 도착" 표시에 사용. */
@Component
public class ImportCandidateStore {

  private static final Logger log = LoggerFactory.getLogger(ImportCandidateStore.class);

  /** sourceIncidentId → ExternalIncident */
  private final ConcurrentHashMap<String, ExternalIncident> candidates = new ConcurrentHashMap<>();

  /** 이미 Suri-Map으로 import된 sourceIncidentId 목록 */
  private final ConcurrentHashMap<String, Boolean> imported = new ConcurrentHashMap<>();

  public void addCandidate(ExternalIncident incident) {
    if (!imported.containsKey(incident.sourceIncidentId())) {
      candidates.put(incident.sourceIncidentId(), incident);
    }
  }

  public void addCandidates(List<ExternalIncident> incidents) {
    incidents.forEach(this::addCandidate);
  }

  public List<ExternalIncident> getCandidates() {
    return List.copyOf(candidates.values());
  }

  public ExternalIncident getCandidate(String sourceIncidentId) {
    return candidates.get(sourceIncidentId);
  }

  public void markImported(String sourceIncidentId) {
    candidates.remove(sourceIncidentId);
    imported.put(sourceIncidentId, true);
    log.info("후보 사건 imported 처리: {}", sourceIncidentId);
  }

  public boolean isImported(String sourceIncidentId) {
    return imported.containsKey(sourceIncidentId);
  }

  /** 이미 import된 사건들의 sourceIncidentId 목록 */
  public List<String> getImportedIds() {
    return List.copyOf(imported.keySet());
  }

  public int candidateCount() {
    return candidates.size();
  }

  public void reset() {
    candidates.clear();
    imported.clear();
  }
}
