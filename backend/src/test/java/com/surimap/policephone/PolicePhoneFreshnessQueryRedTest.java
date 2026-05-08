package com.surimap.policephone;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * L2-T03 RED: PolicePhoneFreshnessQuery.byIncident source contract for the police_phone_freshness
 * slot.
 */
@DisplayName("L2-T03 PolicePhoneFreshnessQuery.byIncident RED")
class PolicePhoneFreshnessQueryRedTest {

  @Test
  @DisplayName("PolicePhoneFreshnessQuery.byIncident 포트는 incidentId를 입력으로 받는다")
  void police_phone_freshness_query_by_incident_port_exists() throws Exception {
    Class<?> query = Class.forName("com.surimap.policephone.query.PolicePhoneFreshnessQuery");

    Method byIncident = query.getMethod("byIncident", UUID.class);

    assertThat(byIncident.getReturnType()).isEqualTo(java.util.List.class);
  }

  @Test
  @DisplayName("PolicePhoneFreshness row shape는 board slot canonical fields를 노출한다")
  void police_phone_freshness_row_shape_exposes_canonical_fields() throws Exception {
    Class<?> row = Class.forName("com.surimap.policephone.query.PolicePhoneFreshnessRow");

    assertThat(row.getRecordComponents())
        .extracting(component -> component.getName())
        .contains(
            "policePhoneId",
            "accountId",
            "accountType",
            "organizationType",
            "incidentId",
            "opId",
            "lastHeartbeatAt",
            "lastSyncAt",
            "version",
            "derivedFreshness");
  }

  @Test
  @DisplayName("PolicePhoneFreshness row는 ONLINE STALE LOST 파생 기준을 위한 derivedFreshness 필드를 고정한다")
  void police_phone_freshness_row_exposes_derived_freshness_field() throws Exception {
    Class<?> row = Class.forName("com.surimap.policephone.query.PolicePhoneFreshnessRow");

    assertThat(row.getRecordComponents())
        .extracting(component -> component.getName())
        .contains("derivedFreshness");
  }
}
