package com.surimap.external;

import java.util.Collections;
import java.util.List;

/** mock112.enabled=false 상태에서 polling은 빈 목록으로 두고, 명시적 import는 실패시키는 fallback. */
public class UnavailableExternalIncidentAdapter implements ExternalIncidentAdapter {

  @Override
  public List<ExternalIncident> fetchReadyIncidents() {
    return Collections.emptyList();
  }

  @Override
  public ExternalIncident fetchIncident(String sourceIncidentId) {
    throw new IllegalStateException("external_incident_adapter_unavailable");
  }

  @Override
  public void markImported(String sourceIncidentId) {}
}
