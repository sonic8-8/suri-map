package com.surimap.offlinepackage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.surimap.offlinepackage.fixture.OfflinePackageInstallationFixtures;
import com.surimap.offlinepackage.fixture.OfflinePackageInstallationFixtures.OfflinePackageInstallationStatus;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** L6-T06A OfflinePackageInstallationQuery contract test for S3-2 package_badge consumers. */
@DisplayName("L6-T06A OfflinePackageInstallationQuery contract")
class OfflinePackageInstallationQueryContractTest {

  @Test
  @DisplayName("fixture exposes READY/PARTIAL/STALE/FAILED status rows")
  void fixture_exposes_package_installation_status_rows() {
    List<OfflinePackageInstallationStatus> rows =
        OfflinePackageInstallationFixtures.byIncidentQueryRows();

    assertThat(rows)
        .extracting(OfflinePackageInstallationStatus::status)
        .containsExactly("READY", "PARTIAL", "STALE", "FAILED");
    assertThat(recordComponentNames(OfflinePackageInstallationStatus.class))
        .contains(
            "id",
            "incidentId",
            "policePhoneId",
            "policePhoneCode",
            "policePhoneName",
            "status",
            "version",
            "sequence",
            "manifestVersion",
            "readyForOfflineUse");
  }

  @Test
  @DisplayName("production query port declares byIncident and status-bearing row contract")
  void production_query_port_declares_by_incident_status_contract() throws Exception {
    Class<?> queryType =
        classForName("com.surimap.offlinepackage.query.OfflinePackageInstallationQuery");
    Class<?> rowType =
        classForName("com.surimap.offlinepackage.query.OfflinePackageInstallationStatus");

    assertThat(Arrays.stream(queryType.getMethods()).map(Method::getName)).contains("byIncident");
    assertThat(recordComponentNames(rowType))
        .contains(
            "id",
            "incidentId",
            "policePhoneId",
            "policePhoneCode",
            "policePhoneName",
            "status",
            "version",
            "sequence",
            "manifestVersion",
            "readyForOfflineUse");
  }

  private static Class<?> classForName(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException exception) {
      fail("Missing S7 query contract type: %s".formatted(className));
      throw new IllegalStateException(exception);
    }
  }

  private static List<String> recordComponentNames(Class<?> recordType) {
    assertThat(recordType.isRecord()).isTrue();
    return Arrays.stream(recordType.getRecordComponents()).map(RecordComponent::getName).toList();
  }
}
