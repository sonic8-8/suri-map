package com.surimap.offlinepackage.fixture;

import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures.AssignedArea;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures.IncidentMetadata;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures.InitialMarker;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures.MissingPerson;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures.OfflinePackageManifest;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures.OperationalPeriod;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures.OverallSearchArea;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures.PackageItem;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures.PolicePhoneContext;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures.TileItem;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures.TileKeyRange;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures.TileManifest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Builder for the Phase 0 offline package manifest fixture. */
public final class OfflinePackageManifestFixtureBuilder {

  private String manifestId = OfflinePackageManifestFixtures.MANIFEST_ID;
  private String incidentId = OfflinePackageManifestFixtures.INCIDENT_ID;
  private int manifestVersion = OfflinePackageManifestFixtures.MANIFEST_VERSION;
  private PolicePhoneContext policePhoneContext =
      new PolicePhoneContext(
          OfflinePackageManifestFixtures.POLICE_PHONE_ID,
          OfflinePackageManifestFixtures.ACCOUNT_ID,
          "TEAM",
          OfflinePackageManifestFixtures.TEAM_ID,
          "MEMBER");
  private IncidentMetadata incident =
      new IncidentMetadata(
          OfflinePackageManifestFixtures.INCIDENT_ID,
          "OPEN",
          "CURRENT",
          OfflinePackageManifestFixtures.MOCK_112_SOURCE_INCIDENT_ID);
  private MissingPerson missingPerson =
      new MissingPerson(
          OfflinePackageManifestFixtures.INCIDENT_ID,
          "가상 실종자 001",
          null,
          "남색 점퍼, 회색 등산화",
          "인왕산 북측 산책로 입구",
          java.time.OffsetDateTime.parse("2026-04-28T08:30:00+09:00"));
  private List<OperationalPeriod> operationalPeriods =
      List.of(
          new OperationalPeriod(
              OfflinePackageManifestFixtures.OP_ID,
              OfflinePackageManifestFixtures.INCIDENT_ID,
              1,
              "ACTIVE",
              1L));
  private List<AssignedArea> assignedAreas =
      List.of(
          new AssignedArea(
              OfflinePackageManifestFixtures.ASSIGNED_AREA_ID,
              OfflinePackageManifestFixtures.INCIDENT_ID,
              OfflinePackageManifestFixtures.OP_ID,
              "ASSIGNED",
              1L));
  private List<InitialMarker> initialMarkers =
      List.of(
          new InitialMarker(
              OfflinePackageManifestFixtures.INITIAL_MARKER_ID,
              OfflinePackageManifestFixtures.INCIDENT_ID,
              OfflinePackageManifestFixtures.OP_ID,
              point("126.956500", "37.571200"),
              "ACTIVE"));
  private OverallSearchArea overallSearchArea =
      new OverallSearchArea(
          OfflinePackageManifestFixtures.OVERALL_SEARCH_AREA_ID,
          OfflinePackageManifestFixtures.INCIDENT_ID,
          "OVERALL",
          "ACTIVE",
          OfflinePackageManifestFixtures.OVERALL_AREA_HASH,
          overallSearchAreaPolygon());
  private List<TileItem> tileItems =
      List.of(
          tile(
              15,
              27925,
              12680,
              "354260e6043ab9b70662016952da6cdc783319deae611490d0803d5b417b000c",
              18432),
          tile(
              15,
              27926,
              12680,
              "ffa729767ab0dd0add127c19b0b1243f553dadaf7f796a593d180d00552ea977",
              20480),
          tile(
              16,
              27925,
              12681,
              "64fc20008bd026acb2cc672812de4fa0f1928fc763f894c82c5ef89c2beb6165",
              24576));

  private OfflinePackageManifestFixtureBuilder() {}

  public static OfflinePackageManifestFixtureBuilder currentPrecinctManifest() {
    return new OfflinePackageManifestFixtureBuilder();
  }

  public OfflinePackageManifestFixtureBuilder withManifestVersion(int manifestVersion) {
    this.manifestVersion = manifestVersion;
    return this;
  }

  public OfflinePackageManifestFixtureBuilder withTileItems(List<TileItem> tileItems) {
    this.tileItems = List.copyOf(tileItems);
    return this;
  }

  public TileManifest buildTileManifest() {
    List<TileItem> tiles = List.copyOf(tileItems);
    return new TileManifest(
        manifestId,
        OfflinePackageManifestFixtures.STYLE_ID,
        OfflinePackageManifestFixtures.OVERALL_AREA_HASH,
        OfflinePackageManifestFixtures.BLOB_URI_TEMPLATE,
        new TileKeyRange(
            OfflinePackageManifestFixtures.MIN_Z,
            OfflinePackageManifestFixtures.MAX_Z,
            OfflinePackageManifestFixtures.MIN_X,
            OfflinePackageManifestFixtures.MAX_X,
            OfflinePackageManifestFixtures.MIN_Y,
            OfflinePackageManifestFixtures.MAX_Y),
        tiles,
        OfflinePackageManifestFixtures.FAILURE_KEYS);
  }

  public OfflinePackageManifest build() {
    List<TileItem> tiles = List.copyOf(tileItems);

    return new OfflinePackageManifest(
        manifestId,
        incidentId,
        manifestVersion,
        OfflinePackageManifestFixtures.EXPIRES_AT,
        "sha256:1111111111111111111111111111111111111111111111111111111111111111",
        policePhoneContext,
        incident,
        missingPerson,
        List.copyOf(operationalPeriods),
        List.copyOf(assignedAreas),
        List.copyOf(initialMarkers),
        overallSearchArea,
        tiles,
        packageItems());
  }

  private static List<PackageItem> packageItems() {
    List<PackageItem> items = new ArrayList<>();
    items.add(
        item(
            "incident:inc-precinct-first-001",
            "INCIDENT_META",
            1,
            "sha256:2222222222222222222222222222222222222222222222222222222222222222"));
    items.add(
        item(
            "missing-person:inc-precinct-first-001",
            "MISSING_PERSON_CACHE",
            1,
            "sha256:3333333333333333333333333333333333333333333333333333333333333333"));
    items.add(
        item(
            "op-list:inc-precinct-first-001",
            "OP_LIST",
            1,
            "sha256:4444444444444444444444444444444444444444444444444444444444444444"));
    items.add(
        item(
            "assigned-area:area-precinct-a1",
            "ASSIGNED_AREA",
            1,
            "sha256:5555555555555555555555555555555555555555555555555555555555555555"));
    items.add(
        item(
            "initial-marker:mk-precinct-clue-001",
            "INITIAL_MARKER",
            1,
            "sha256:6666666666666666666666666666666666666666666666666666666666666666"));
    items.add(
        item(
            "overall-search-area:overall-area-hash-precinct-current",
            "OVERALL_SEARCH_AREA",
            1,
            "sha256:7777777777777777777777777777777777777777777777777777777777777777"));
    items.add(
        item(
            "tile-manifest:tile-manifest-inc-precinct-001",
            "TILE",
            1,
            "sha256:8888888888888888888888888888888888888888888888888888888888888888"));
    return List.copyOf(items);
  }

  private static PackageItem item(
      String itemKey, String itemType, long sourceVersion, String sourceHash) {
    return new PackageItem(itemKey, itemType, "PENDING", sourceVersion, sourceHash);
  }

  private static TileItem tile(int z, int x, int y, String sha256, int bytes) {
    return new TileItem(
        "tile:%s:%d:%d:%d".formatted(OfflinePackageManifestFixtures.STYLE_ID, z, x, y),
        OfflinePackageManifestFixtures.STYLE_ID,
        z,
        x,
        y,
        "local://tiles/%s/%d/%d/%d.pbf"
            .formatted(OfflinePackageManifestFixtures.INCIDENT_ALIAS, z, x, y),
        "sha256:" + sha256,
        bytes);
  }

  private static List<List<BigDecimal>> overallSearchAreaPolygon() {
    return List.of(
        point("126.948000", "37.565000"),
        point("126.968000", "37.565000"),
        point("126.968000", "37.579000"),
        point("126.948000", "37.579000"),
        point("126.948000", "37.565000"));
  }

  private static List<BigDecimal> point(String lon, String lat) {
    return List.of(new BigDecimal(lon), new BigDecimal(lat));
  }
}
