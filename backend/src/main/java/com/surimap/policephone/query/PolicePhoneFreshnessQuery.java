package com.surimap.policephone.query;

import java.util.List;
import java.util.UUID;

public interface PolicePhoneFreshnessQuery {
  List<PolicePhoneFreshnessRow> byIncident(UUID incidentId);
}
