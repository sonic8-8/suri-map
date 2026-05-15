package com.surimap.offlinepackage.fixture;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/** L6-T05 SC-03 offline package manifest contract fixture. */
public final class OfflinePackageManifestFixtures {

  public static final String INCIDENT_ALIAS = "inc-precinct-first-001";
  public static final String INCIDENT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001";
  public static final String MOCK_112_SOURCE_INCIDENT_ID = "mock-112-incident-001";
  public static final String MANIFEST_ALIAS = "tile-manifest-inc-precinct-001";
  public static final String MANIFEST_ID = "77777777-0000-4000-8000-000000000701";
  public static final int MANIFEST_VERSION = 1;
  public static final String POLICE_PHONE_CODE = "dev-precinct-phone-01";
  public static final String POLICE_PHONE_ID = "00000000-0000-0000-0000-000000000101";
  public static final String ACCOUNT_ID = "11111111-1111-1111-1111-111111110003";
  public static final String TEAM_ID = "team-precinct-jongno";
  public static final String OP_ALIAS = "op-precinct-001-op1";
  public static final String OP_ID = "88888888-8888-8888-8888-888888880001";
  public static final String OVERALL_SEARCH_AREA_ALIAS = "osa-precinct-001";
  public static final String OVERALL_SEARCH_AREA_ID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001";
  public static final String ASSIGNED_AREA_ALIAS = "area-precinct-a1";
  public static final String ASSIGNED_AREA_ID = "cccccccc-cccc-cccc-cccc-cccccccc0001";
  public static final String INITIAL_MARKER_ID = "mk-precinct-clue-001";
  public static final String STYLE_ID = "osm-local";
  public static final String OVERALL_AREA_HASH = "overall-area-hash-precinct-current";
  public static final String BLOB_URI_TEMPLATE =
      "local://tiles/inc-precinct-first-001/{z}/{x}/{y}.pbf";
  public static final OffsetDateTime EXPIRES_AT = OffsetDateTime.parse("2026-04-28T12:00:00+09:00");

  public static final int MIN_Z = 15;
  public static final int MAX_Z = 16;
  public static final int MIN_X = 27935;
  public static final int MAX_X = 55873;
  public static final int MIN_Y = 12960;
  public static final int MAX_Y = 25923;

  public static final List<String> PACKAGE_ITEM_TYPES =
      List.of(
          "INCIDENT_META",
          "MISSING_PERSON_CACHE",
          "OP_LIST",
          "ASSIGNED_AREA",
          "INITIAL_MARKER",
          "OVERALL_SEARCH_AREA",
          "TILE");

  public static final Set<String> FAILURE_KEYS =
      Set.of(
          "manifest-expired",
          "manifest-overall-area-stale",
          "tile-404",
          "tile-timeout",
          "tile-checksum-mismatch",
          "tile-corrupt-blob");

  public static final List<String> EXTERNAL_TILE_URLS =
      List.of(
          "https://a.tile.openstreetmap.org/15/27935/12960.pbf",
          "https://api.mapbox.com/v4/mapbox.mapbox-streets-v8/15/27935/12960.vector.pbf",
          "https://maps.googleapis.com/maps/api/tile/15/27935/12960");

  private OfflinePackageManifestFixtures() {}

  public static OfflinePackageManifest manifest() {
    return OfflinePackageManifestFixtureBuilder.currentPrecinctManifest().build();
  }

  public static TileManifest tileManifest() {
    return OfflinePackageManifestFixtureBuilder.currentPrecinctManifest().buildTileManifest();
  }

  public static boolean isLocalTileUri(String uri) {
    return uri.startsWith("local://tiles/");
  }

  public static boolean isExternalTileUrlRejected(String uri) {
    String lower = uri.toLowerCase();
    return lower.contains(".tile.openstreetmap.org")
        || lower.contains("tile.openstreetmap.org")
        || lower.contains(".mapbox.com")
        || lower.contains("mapbox.com")
        || lower.contains(".googleapis.com")
        || lower.contains("googleapis.com");
  }

  public record OfflinePackageManifest(
      String manifestId,
      String incidentId,
      int manifestVersion,
      OffsetDateTime expiresAt,
      String packageHash,
      PolicePhoneContext policePhoneContext,
      IncidentMetadata incident,
      MissingPerson missingPerson,
      List<OperationalPeriod> operationalPeriods,
      List<AssignedArea> assignedAreas,
      List<InitialMarker> initialMarkers,
      OverallSearchArea overallSearchArea,
      List<TileItem> tileItems,
      List<PackageItem> packageItems) {}

  public record PolicePhoneContext(
      String policePhoneId, String accountId, String accountType, String teamId, String role) {}

  public record IncidentMetadata(
      String incidentId, String status, String packageContext, String sourceFixture) {}

  public record MissingPerson(
      String incidentId,
      String displayName,
      String photoObjectKey,
      String appearanceText,
      String lastSeenLocationText,
      OffsetDateTime lastSeenAt) {}

  public record OperationalPeriod(
      String opId, String incidentId, int sequenceNumber, String status, long version) {}

  public record AssignedArea(
      String areaId, String incidentId, String opId, String status, long version) {}

  public record InitialMarker(
      String markerId,
      String incidentId,
      String opId,
      List<BigDecimal> coordinate,
      String status) {}

  public record OverallSearchArea(
      String areaId,
      String incidentId,
      String areaLevel,
      String status,
      String overallAreaHash,
      List<List<BigDecimal>> polygon) {}

  public record TileManifest(
      String manifestId,
      String styleId,
      String overallAreaHash,
      String blobUriTemplate,
      TileKeyRange tileKeyRange,
      List<TileItem> tiles,
      Set<String> failureKeys) {}

  public record TileKeyRange(int minZ, int maxZ, int minX, int maxX, int minY, int maxY) {}

  public record TileItem(
      String itemKey,
      String styleId,
      int z,
      int x,
      int y,
      String url,
      String checksum,
      int bytes) {}

  public record PackageItem(
      String itemKey, String itemType, String status, long sourceVersion, String sourceHash) {}
}
