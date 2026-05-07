package com.surimap.offlinepackage;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures.OfflinePackageManifest;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures.PackageItem;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** L6-T05 offline package manifest Phase 0 contract fixture test. */
@DisplayName("L6-T05 offline package manifest contract fixture")
class OfflinePackageManifestContractTest {

  private static final Pattern SHA256_HEX = Pattern.compile("^sha256:[0-9a-f]{64}$");

  @Test
  @DisplayName("manifest root identity and PolicePhone context follow SC-03 handoff")
  void manifest_root_identity_and_police_phone_context_are_fixed() {
    OfflinePackageManifest manifest = OfflinePackageManifestFixtures.manifest();

    assertThat(manifest.manifestId()).isEqualTo("tile-manifest-inc-precinct-001");
    assertThat(manifest.incidentId()).isEqualTo("inc-precinct-first-001");
    assertThat(manifest.manifestVersion()).isEqualTo(1);
    assertThat(manifest.packageHash()).matches(SHA256_HEX);

    assertThat(manifest.policePhoneContext().policePhoneId()).isEqualTo("dev-precinct-phone-01");
    assertThat(manifest.policePhoneContext().accountId()).isEqualTo("acct-precinct-team");
    assertThat(manifest.policePhoneContext().accountType()).isEqualTo("TEAM");
    assertThat(manifest.policePhoneContext().teamId()).isEqualTo("team-precinct-jongno");
    assertThat(manifest.policePhoneContext().role()).isEqualTo("MEMBER");
    assertThat(policePhoneContextFields()).doesNotContain("phoneType", "phone_type");
  }

  @Test
  @DisplayName("manifest includes current incident metadata and mock 112 missingPerson")
  void manifest_includes_incident_metadata_and_missing_person() {
    OfflinePackageManifest manifest = OfflinePackageManifestFixtures.manifest();

    assertThat(manifest.incident().incidentId()).isEqualTo("inc-precinct-first-001");
    assertThat(manifest.incident().status()).isEqualTo("OPEN");
    assertThat(manifest.incident().packageContext()).isEqualTo("CURRENT");
    assertThat(manifest.incident().sourceFixture()).isEqualTo("mock-112-incident-001");

    assertThat(manifest.missingPerson().incidentId()).isEqualTo("inc-precinct-first-001");
    assertThat(manifest.missingPerson().sourceFixture()).isEqualTo("mock-112-incident-001");
    assertThat(manifest.missingPerson().missingPersonId()).isEqualTo("mp-precinct-first-001");
    assertThat(manifest.missingPerson().name()).isEqualTo("가상 실종자 001");
    assertThat(manifestFields()).contains("missingPerson").doesNotContain("missingPersonCache");
  }

  @Test
  @DisplayName("manifest includes OP, assigned area, initial marker, and overall search area")
  void manifest_includes_source_owner_read_contracts() {
    OfflinePackageManifest manifest = OfflinePackageManifestFixtures.manifest();

    assertThat(manifest.operationalPeriods())
        .singleElement()
        .satisfies(
            op -> {
              assertThat(op.opId()).isEqualTo("op-precinct-001-op1");
              assertThat(op.incidentId()).isEqualTo("inc-precinct-first-001");
              assertThat(op.status()).isEqualTo("ACTIVE");
              assertThat(op.sequenceNumber()).isEqualTo(1);
            });

    assertThat(manifest.assignedAreas())
        .singleElement()
        .satisfies(
            area -> {
              assertThat(area.areaId()).isEqualTo("area-precinct-a1");
              assertThat(area.incidentId()).isEqualTo("inc-precinct-first-001");
              assertThat(area.opId()).isEqualTo("op-precinct-001-op1");
              assertThat(area.status()).isEqualTo("ASSIGNED");
            });

    assertThat(manifest.initialMarkers())
        .singleElement()
        .satisfies(
            marker -> {
              assertThat(marker.markerId()).isEqualTo("mk-precinct-clue-001");
              assertThat(marker.opId()).isEqualTo("op-precinct-001-op1");
              assertThat(marker.coordinate()).isEqualTo(point("126.956500", "37.571200"));
            });

    assertThat(manifest.overallSearchArea().areaId()).isEqualTo("osa-precinct-001");
    assertThat(manifest.overallSearchArea().areaLevel()).isEqualTo("OVERALL");
    assertThat(manifest.overallSearchArea().overallAreaHash())
        .isEqualTo("overall-area-hash-precinct-current");
    assertThat(manifest.overallSearchArea().polygon())
        .containsExactly(
            point("126.948000", "37.565000"),
            point("126.968000", "37.565000"),
            point("126.968000", "37.579000"),
            point("126.948000", "37.579000"),
            point("126.948000", "37.565000"));
  }

  @Test
  @DisplayName("packageItems expose exactly the S7 item type set and required fields")
  void package_items_expose_exact_type_set_and_required_fields() {
    OfflinePackageManifest manifest = OfflinePackageManifestFixtures.manifest();

    assertThat(manifest.packageItems())
        .extracting(PackageItem::itemType)
        .containsExactly(
            "INCIDENT_META",
            "MISSING_PERSON_CACHE",
            "OP_LIST",
            "ASSIGNED_AREA",
            "INITIAL_MARKER",
            "OVERALL_SEARCH_AREA",
            "TILE");

    assertThat(manifest.packageItems())
        .allSatisfy(
            item -> {
              assertThat(item.itemKey()).isNotBlank();
              assertThat(item.itemType()).isIn(OfflinePackageManifestFixtures.PACKAGE_ITEM_TYPES);
              assertThat(item.status()).isEqualTo("PENDING");
              assertThat(item.sourceVersion()).isGreaterThanOrEqualTo(0);
              assertThat(item.sourceHash()).matches(SHA256_HEX);
            });

    assertThat(manifestFields()).contains("tileItems").doesNotContain("tileManifest");
  }

  private static List<String> manifestFields() {
    return Arrays.stream(OfflinePackageManifest.class.getRecordComponents())
        .map(RecordComponent::getName)
        .toList();
  }

  private static List<String> policePhoneContextFields() {
    return Arrays.stream(
            OfflinePackageManifestFixtures.PolicePhoneContext.class.getRecordComponents())
        .map(RecordComponent::getName)
        .toList();
  }

  private static List<BigDecimal> point(String lon, String lat) {
    return List.of(new BigDecimal(lon), new BigDecimal(lat));
  }
}
