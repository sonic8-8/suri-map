package com.surimap.api.controller.opcomparison.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public class CreateOpComparisonRequest {

  @NotNull private UUID incidentId;

  @NotEmpty
  @Size(min = 2)
  private List<@NotNull UUID> operationalPeriodIds;

  public UUID incidentId() {
    return incidentId;
  }

  public List<UUID> operationalPeriodIds() {
    return operationalPeriodIds;
  }

  public void setIncidentId(UUID incidentId) {
    this.incidentId = incidentId;
  }

  public void setOperationalPeriodIds(List<UUID> operationalPeriodIds) {
    this.operationalPeriodIds = operationalPeriodIds;
  }
}
