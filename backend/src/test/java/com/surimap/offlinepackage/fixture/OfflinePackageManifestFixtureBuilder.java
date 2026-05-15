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
          "무등산 서측 탐방로 입구",
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
              point("126.913400", "35.163100"),
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
              27935,
              12960,
              "1631a7b03c6924b5f966d85597afc394c602dd1fa214efc3e6ea0d666817b9fe",
              18432),
          tile(
              15,
              27936,
              12960,
              "3012bcff12c3416907e05ead8236a213f5067da1c00f44c014fea5a803f46c05",
              20480),
          tile(
              16,
              55870,
              25920,
              "f9965c1686fa1d08356d3fee122b95cbbe9b2f76f1764108c4bf48e31f00cd60",
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
        point("126.904000", "35.158000"),
        point("126.923000", "35.158000"),
        point("126.923000", "35.173000"),
        point("126.904000", "35.173000"),
        point("126.904000", "35.158000"));
  }

  private static List<BigDecimal> point(String lon, String lat) {
    return List.of(new BigDecimal(lon), new BigDecimal(lat));
  }
}
