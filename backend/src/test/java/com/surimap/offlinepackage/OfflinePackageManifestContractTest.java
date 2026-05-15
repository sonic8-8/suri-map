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
  private static final List<String> S1_1_ALLOWED_MISSING_PERSON_FIELDS =
      List.of(
          "incidentId",
          "displayName",
          "photoObjectKey",
          "appearanceText",
          "lastSeenLocationText",
          "lastSeenAt");
  private static final List<String> FORBIDDEN_MISSING_PERSON_FIELDS =
      List.of(
          "importedAt",
          "sourceFixture",
          "missingPersonId",
          "name",
          "sex",
          "gender",
          "age",
          "lastSeenSummary",
          "residentRegistrationNumber",
          "rrn",
          "socialSecurityNumber",
          "phone",
          "phoneNumber",
          "mobilePhone",
          "contactNumber",
          "address",
          "homeAddress",
          "roadAddress",
          "detailAddress");

  @Test
  @DisplayName("manifest root identity and PolicePhone context follow SC-03 handoff")
  void manifest_root_identity_and_police_phone_context_are_fixed() {
    OfflinePackageManifest manifest = OfflinePackageManifestFixtures.manifest();

    assertThat(manifest.manifestId()).isEqualTo(OfflinePackageManifestFixtures.MANIFEST_ID);
    assertThat(manifest.incidentId()).isEqualTo(OfflinePackageManifestFixtures.INCIDENT_ID);
    assertThat(manifest.manifestVersion()).isEqualTo(1);
    assertThat(manifest.packageHash()).matches(SHA256_HEX);

    assertThat(manifest.policePhoneContext().policePhoneId())
        .isEqualTo(OfflinePackageManifestFixtures.POLICE_PHONE_ID);
    assertThat(manifest.policePhoneContext().accountId())
        .isEqualTo("11111111-1111-1111-1111-111111110003");
    assertThat(manifest.policePhoneContext().accountType()).isEqualTo("TEAM");
    assertThat(manifest.policePhoneContext().teamId()).isEqualTo("team-precinct-jongno");
    assertThat(manifest.policePhoneContext().role()).isEqualTo("MEMBER");
    assertThat(policePhoneContextFields()).doesNotContain("phoneType", "phone_type");
  }

  @Test
  @DisplayName("manifest는 현재 사건 메타데이터와 실종자 기본 정보를 포함한다")
  void manifest_includes_incident_metadata_and_missing_person() {
    OfflinePackageManifest manifest = OfflinePackageManifestFixtures.manifest();

    assertThat(manifest.incident().incidentId()).isEqualTo(OfflinePackageManifestFixtures.INCIDENT_ID);
    assertThat(manifest.incident().status()).isEqualTo("OPEN");
    assertThat(manifest.incident().packageContext()).isEqualTo("CURRENT");
    assertThat(manifest.incident().sourceFixture()).isEqualTo("mock-112-incident-001");

    assertThat(manifest.missingPerson().incidentId())
        .isEqualTo(OfflinePackageManifestFixtures.INCIDENT_ID);
    assertThat(manifest.missingPerson().displayName()).isEqualTo("가상 실종자 001");
    assertThat(manifest.missingPerson().appearanceText()).isEqualTo("남색 점퍼, 회색 등산화");
    assertThat(manifest.missingPerson().lastSeenLocationText()).isEqualTo("무등산 서측 탐방로 입구");
    assertThat(manifestFields()).contains("missingPerson").doesNotContain("missingPersonCache");
  }

  @Test
  @DisplayName("manifest missingPerson은 S1-1 허용 필드만 소비한다")
  void manifest_missing_person_consumes_only_s1_1_allowlist() {
    OfflinePackageManifest manifest = OfflinePackageManifestFixtures.manifest();

    assertThat(manifest.missingPerson().incidentId())
        .isEqualTo(OfflinePackageManifestFixtures.INCIDENT_ID);
    assertThat(missingPersonFields())
        .containsExactlyInAnyOrderElementsOf(S1_1_ALLOWED_MISSING_PERSON_FIELDS)
        .doesNotContainAnyElementsOf(FORBIDDEN_MISSING_PERSON_FIELDS);
  }

  @Test
  @DisplayName("manifest includes OP, assigned area, initial marker, and overall search area")
  void manifest_includes_source_owner_read_contracts() {
    OfflinePackageManifest manifest = OfflinePackageManifestFixtures.manifest();

    assertThat(manifest.operationalPeriods())
        .singleElement()
        .satisfies(
            op -> {
              assertThat(op.opId()).isEqualTo(OfflinePackageManifestFixtures.OP_ID);
              assertThat(op.incidentId()).isEqualTo(OfflinePackageManifestFixtures.INCIDENT_ID);
              assertThat(op.status()).isEqualTo("ACTIVE");
              assertThat(op.sequenceNumber()).isEqualTo(1);
            });

    assertThat(manifest.assignedAreas())
        .singleElement()
        .satisfies(
            area -> {
              assertThat(area.areaId()).isEqualTo(OfflinePackageManifestFixtures.ASSIGNED_AREA_ID);
              assertThat(area.incidentId()).isEqualTo(OfflinePackageManifestFixtures.INCIDENT_ID);
              assertThat(area.opId()).isEqualTo(OfflinePackageManifestFixtures.OP_ID);
              assertThat(area.status()).isEqualTo("ASSIGNED");
            });

    assertThat(manifest.initialMarkers())
        .singleElement()
        .satisfies(
            marker -> {
              assertThat(marker.markerId()).isEqualTo("mk-precinct-clue-001");
              assertThat(marker.opId()).isEqualTo(OfflinePackageManifestFixtures.OP_ID);
              assertThat(marker.coordinate()).isEqualTo(point("126.913400", "35.163100"));
            });

    assertThat(manifest.overallSearchArea().areaId())
        .isEqualTo(OfflinePackageManifestFixtures.OVERALL_SEARCH_AREA_ID);
    assertThat(manifest.overallSearchArea().areaLevel()).isEqualTo("OVERALL");
    assertThat(manifest.overallSearchArea().overallAreaHash())
        .isEqualTo("overall-area-hash-precinct-current");
    assertThat(manifest.overallSearchArea().polygon())
        .containsExactly(
            point("126.904000", "35.158000"),
            point("126.923000", "35.158000"),
            point("126.923000", "35.173000"),
            point("126.904000", "35.173000"),
            point("126.904000", "35.158000"));
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

  private static List<String> missingPersonFields() {
    return Arrays.stream(OfflinePackageManifestFixtures.MissingPerson.class.getRecordComponents())
        .map(RecordComponent::getName)
        .toList();
  }

  private static List<BigDecimal> point(String lon, String lat) {
    return List.of(new BigDecimal(lon), new BigDecimal(lat));
  }
}
