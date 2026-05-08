package com.surimap.offlinepackage.fixture;

import java.util.List;

/** L6-T06B S6 PACKAGE_MISSING local warning input cases from S7 package status. */
public final class OfflinePackageLocalWarningInputFixtures {

  public static final String WARNING_TYPE = "PACKAGE_MISSING";
  public static final String RAISE_WHEN =
      "S7 package status is MISSING, STALE, EXPIRED, or any required item failed";
  public static final String CLEAR_WHEN =
      "S7 package status is COMPLETE for the active manifestVersion";

  private OfflinePackageLocalWarningInputFixtures() {}

  public static List<PackageWarningCase> packageMissingCases() {
    return List.of(
        new PackageWarningCase("READY", false, CLEAR_WHEN),
        new PackageWarningCase("PARTIAL", true, RAISE_WHEN),
        new PackageWarningCase("STALE", true, RAISE_WHEN),
        new PackageWarningCase("FAILED", true, RAISE_WHEN));
  }

  public record PackageWarningCase(String packageStatus, boolean raised, String reason) {}
}
