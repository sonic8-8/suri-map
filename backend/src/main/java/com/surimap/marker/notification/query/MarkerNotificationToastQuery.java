package com.surimap.marker.notification.query;

import java.util.List;
import java.util.UUID;

public interface MarkerNotificationToastQuery {

  List<MarkerNotificationToastRow> byIncident(UUID incidentId);
}
