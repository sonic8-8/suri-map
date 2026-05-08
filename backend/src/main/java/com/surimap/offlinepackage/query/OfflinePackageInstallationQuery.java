package com.surimap.offlinepackage.query;

import java.util.List;

public interface OfflinePackageInstallationQuery {

  List<OfflinePackageInstallationStatus> byIncident(String incidentId);
}
