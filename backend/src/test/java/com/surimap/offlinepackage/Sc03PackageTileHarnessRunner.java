package com.surimap.offlinepackage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.offlinepackage.dto.TileBlobResponse;
import com.surimap.offlinepackage.dto.TileStyleResponse;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures.OfflinePackageManifest;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures.TileItem;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures.TileManifest;
import com.surimap.offlinepackage.service.LocalTileService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Test-local SC-03 package/tile harness closure runner. */
public class Sc03PackageTileHarnessRunner {

  private static final String SCENARIO_ID = "SC-03";
  private static final int MAX_TILE_ITEMS = 8;
  private static final String STYLE_PATH = "/tiles/styles/%s.json";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final List<String> MOCKED_SOURCE_CONTRACTS =
      List.of(
          "S1-1:incident",
          "S1-1:missing_person",
          "S1-2:police_phone_assignment",
          "S2:assigned_area",
          "S2:overall_search_area",
          "S5:initial_marker",
          "S8:operational_period",
          "S4:offline_package_event",
          "S6:outbox_status_write",
          "S3-2:package_badge",
          "S7:mock_tile_catalog",
          "S7:mock_tile_server_adapter",
          "S7:maplibre_render_stub");

  private final LocalTileService localTileService;

  public Sc03PackageTileHarnessRunner() {
    this(new LocalTileService());
  }

  Sc03PackageTileHarnessRunner(LocalTileService localTileService) {
    this.localTileService = localTileService;
  }

  public Sc03PackageTileHarnessReport runPackageTileClosure() {
    OfflinePackageManifest manifest = OfflinePackageManifestFixtures.manifest();
    TileManifest tileManifest = OfflinePackageManifestFixtures.tileManifest();
    SourceConstants sourceConstants = SourceConstants.load();
    TileStyleResponse style = localTileService.getStyle(tileManifest.styleId());
    StyleProbe styleProbe = styleProbe(style, tileManifest.styleId());

    return new Sc03PackageTileHarnessReport(
        SCENARIO_ID,
        manifest.incidentId(),
        tileManifest.manifestId(),
        mockedSourceContracts(manifest, tileManifest, styleProbe, sourceConstants),
        packageManifest(manifest),
        localTileProbe(tileManifest, styleProbe, sourceConstants),
        tileFixtureExactnessReport(tileManifest, sourceConstants));
  }

  private MockedSourceContractsReport mockedSourceContracts(
      OfflinePackageManifest manifest,
      TileManifest tileManifest,
      StyleProbe styleProbe,
      SourceConstants sourceConstants) {
    Map<String, Boolean> evidence = new LinkedHashMap<>();
    evidence.put(
        "S1-1:incident",
        Objects.equals(manifest.incident().incidentId(), OfflinePackageManifestFixtures.INCIDENT_ID)
            && Objects.equals(manifest.incident().status(), "OPEN"));
    evidence.put(
        "S1-1:missing_person",
        Objects.equals(
            manifest.missingPerson().incidentId(), OfflinePackageManifestFixtures.INCIDENT_ID));
    evidence.put(
        "S1-2:police_phone_assignment",
        Objects.equals(
                manifest.policePhoneContext().policePhoneId(),
                OfflinePackageManifestFixtures.POLICE_PHONE_ID)
            && Objects.equals(
                manifest.policePhoneContext().accountId(),
                OfflinePackageManifestFixtures.ACCOUNT_ID));
    evidence.put(
        "S2:assigned_area",
        manifest.assignedAreas().stream()
            .anyMatch(
                area ->
                    Objects.equals(
                        area.areaId(), OfflinePackageManifestFixtures.ASSIGNED_AREA_ID)));
    evidence.put(
        "S2:overall_search_area",
        Objects.equals(
                manifest.overallSearchArea().areaId(),
                OfflinePackageManifestFixtures.OVERALL_SEARCH_AREA_ID)
            && Objects.equals(
                manifest.overallSearchArea().overallAreaHash(),
                sourceConstants.commonFixturesOverallAreaHash()));
    evidence.put(
        "S5:initial_marker",
        manifest.initialMarkers().stream()
            .anyMatch(
                marker ->
                    Objects.equals(
                        marker.markerId(), OfflinePackageManifestFixtures.INITIAL_MARKER_ID)));
    evidence.put(
        "S8:operational_period",
        manifest.operationalPeriods().stream()
            .anyMatch(op -> Objects.equals(op.opId(), OfflinePackageManifestFixtures.OP_ID)));
    evidence.put(
        "S4:offline_package_event",
        Objects.equals(
                sourceConstants.commonPackageEventType(), "OFFLINE_PACKAGE_INSTALLATION_CHANGED")
            && Objects.equals(
                sourceConstants.commonPackageEventIncidentId(),
                OfflinePackageManifestFixtures.INCIDENT_ID));
    evidence.put(
        "S6:outbox_status_write",
        Objects.equals(sourceConstants.commonOutboxDependencyGroup(), "PACKAGE_INSTALLATION")
            && Objects.equals(
                sourceConstants.commonOutboxEntityType(), "offline_package_installation"));
    evidence.put(
        "S3-2:package_badge",
        Objects.equals(sourceConstants.commonBoardSlot(), "package_badge"));
    evidence.put(
        "S7:mock_tile_catalog",
        Objects.equals(tileManifest.manifestId(), sourceConstants.s7TileManifestId())
            && Objects.equals(tileManifest.blobUriTemplate(), sourceConstants.s7BlobUriTemplate())
            && tileManifest.tiles().stream()
                .allMatch(tile -> sourceConstants.s7TileChecksums().containsKey(tile.itemKey())));
    evidence.put(
        "S7:mock_tile_server_adapter",
        !styleProbe.externalTileHostCalled()
            && tileManifest.tiles().stream()
                .allMatch(tile -> OfflinePackageManifestFixtures.isLocalTileUri(tile.url())));
    evidence.put(
        "S7:maplibre_render_stub",
        styleProbe.styleUsesLocalTileSource()
            && Objects.equals(styleProbe.tilePathTemplate(), "/tiles/osm-local/{z}/{x}/{y}.pbf"));

    List<String> present =
        evidence.entrySet().stream()
            .filter(Map.Entry::getValue)
            .map(Map.Entry::getKey)
            .toList();
    return new MockedSourceContractsReport(
        present,
        present.containsAll(MOCKED_SOURCE_CONTRACTS)
            && MOCKED_SOURCE_CONTRACTS.containsAll(present)
            && !styleProbe.externalTileHostCalled(),
        styleProbe.externalTileHostCalled());
  }

  private PackageManifestReport packageManifest(OfflinePackageManifest manifest) {
    return new PackageManifestReport(
        manifest.packageItems().stream()
            .map(OfflinePackageManifestFixtures.PackageItem::itemType)
            .toList(),
        manifest.incident().incidentId(),
        manifest.missingPerson().incidentId(),
        manifest.operationalPeriods().get(0).opId(),
        manifest.assignedAreas().get(0).areaId(),
        manifest.initialMarkers().get(0).markerId(),
        manifest.overallSearchArea().areaId(),
        manifest.tileItems().stream()
            .map(TileItem::url)
            .allMatch(OfflinePackageManifestFixtures::isLocalTileUri)
            ? manifest.manifestId()
            : "");
  }

  private LocalTileProbeReport localTileProbe(
      TileManifest tileManifest, StyleProbe styleProbe, SourceConstants sourceConstants) {
    boolean tileBlobUsesLocalFixture =
        tileManifest.tiles().stream()
            .allMatch(tile -> tileBlobMatchesFixtureItem(tile, sourceConstants));
    boolean matchesS7HarnessConstants =
        Objects.equals(tileManifest.manifestId(), OfflinePackageManifestFixtures.MANIFEST_ID)
            && tileManifest.tileKeyRange().minZ() == OfflinePackageManifestFixtures.MIN_Z
            && tileManifest.tileKeyRange().maxZ() == OfflinePackageManifestFixtures.MAX_Z
            && tileManifest.tiles().size() <= MAX_TILE_ITEMS
            && styleProbe.styleUsesLocalTileSource()
            && tileBlobUsesLocalFixture;

    return new LocalTileProbeReport(
        tileManifest.styleId(),
        STYLE_PATH.formatted(tileManifest.styleId()),
        styleProbe.tilePathTemplate(),
        tileManifest.tileKeyRange().minZ(),
        tileManifest.tileKeyRange().maxZ(),
        MAX_TILE_ITEMS,
        styleProbe.styleUsesLocalTileSource(),
        tileBlobUsesLocalFixture,
        matchesS7HarnessConstants);
  }

  private TileFixtureExactnessReport tileFixtureExactnessReport(
      TileManifest tileManifest, SourceConstants sourceConstants) {
    String zoomRange =
        "%d..%d"
            .formatted(tileManifest.tileKeyRange().minZ(), tileManifest.tileKeyRange().maxZ());
    String tileKeyRange =
        "z=%d..%d,x=%d..%d,y=%d..%d"
            .formatted(
                tileManifest.tileKeyRange().minZ(),
                tileManifest.tileKeyRange().maxZ(),
                tileManifest.tileKeyRange().minX(),
                tileManifest.tileKeyRange().maxX(),
                tileManifest.tileKeyRange().minY(),
                tileManifest.tileKeyRange().maxY());
    List<String> localTileUris = tileManifest.tiles().stream().map(TileItem::url).toList();
    boolean alignedWithHarnessScenarios =
        Objects.equals(
                tileManifest.manifestId(), sourceConstants.harnessScenariosTileManifestId())
            && Objects.equals(zoomRange, sourceConstants.harnessScenariosZoomRange())
            && Objects.equals(tileKeyRange, sourceConstants.harnessScenariosTileKeyRange())
            && Objects.equals(
                tileManifest.blobUriTemplate(),
                sourceConstants.harnessScenariosBlobUriTemplate());
    boolean alignedWithS7 =
        Objects.equals(tileManifest.manifestId(), sourceConstants.s7TileManifestId())
            && Objects.equals(zoomRange, sourceConstants.s7ZoomRange())
            && Objects.equals(tileKeyRange, sourceConstants.s7TileKeyRange())
            && Objects.equals(
                tileManifest.blobUriTemplate(), sourceConstants.s7BlobUriTemplate())
            && tileManifest.tiles().size() <= MAX_TILE_ITEMS
            && tileManifest.failureKeys().equals(OfflinePackageManifestFixtures.FAILURE_KEYS);
    boolean alignedWithCommonFixtures =
        Objects.equals(tileManifest.manifestId(), sourceConstants.commonFixturesTileManifestId())
            && Objects.equals(zoomRange, sourceConstants.commonFixturesZoomRange())
            && Objects.equals(tileKeyRange, sourceConstants.commonFixturesTileKeyRange())
            && Objects.equals(
                tileManifest.blobUriTemplate(), sourceConstants.commonFixturesBlobUriTemplate())
            && tileManifest.tiles().stream()
            .allMatch(
                tile ->
                    tile.z() >= OfflinePackageManifestFixtures.MIN_Z
                        && tile.z() <= OfflinePackageManifestFixtures.MAX_Z
                        && tile.x() >= OfflinePackageManifestFixtures.MIN_X
                        && tile.x() <= OfflinePackageManifestFixtures.MAX_X
                        && tile.y() >= OfflinePackageManifestFixtures.MIN_Y
                        && tile.y() <= OfflinePackageManifestFixtures.MAX_Y
                        && OfflinePackageManifestFixtures.isLocalTileUri(tile.url()));

    return new TileFixtureExactnessReport(
        tileManifest.manifestId(),
        zoomRange,
        tileKeyRange,
        tileManifest.blobUriTemplate(),
        localTileUris,
        List.copyOf(tileManifest.failureKeys()),
        alignedWithHarnessScenarios,
        alignedWithS7,
        alignedWithCommonFixtures,
        sourceConstants.harnessScenariosTileManifestId(),
        sourceConstants.harnessScenariosZoomRange(),
        sourceConstants.harnessScenariosTileKeyRange(),
        sourceConstants.harnessScenariosBlobUriTemplate(),
        sourceConstants.s7TileManifestId(),
        sourceConstants.s7ZoomRange(),
        sourceConstants.s7TileKeyRange(),
        sourceConstants.s7BlobUriTemplate(),
        sourceConstants.commonFixturesTileManifestId(),
        sourceConstants.commonFixturesZoomRange(),
        sourceConstants.commonFixturesTileKeyRange(),
        sourceConstants.commonFixturesBlobUriTemplate());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> styleSource(TileStyleResponse style, String styleId) {
    Object source = style.sources().get(styleId);
    if (!(source instanceof Map<?, ?> sourceMap)) {
      return Map.of();
    }
    return (Map<String, Object>) sourceMap;
  }

  private static StyleProbe styleProbe(TileStyleResponse style, String styleId) {
    Map<String, Object> source = styleSource(style, styleId);
    List<String> tileUrls = tileUrls(source);
    boolean hasOnlyLocalTileSources =
        !tileUrls.isEmpty() && tileUrls.stream().allMatch(url -> url.startsWith("/tiles/"));
    boolean externalTileHostCalled =
        tileUrls.stream().anyMatch(Sc03PackageTileHarnessRunner::isExternalTileHost);
    String tilePathTemplate = tileUrls.isEmpty() ? "" : tileUrls.get(0);
    return new StyleProbe(tilePathTemplate, hasOnlyLocalTileSources, externalTileHostCalled);
  }

  private static List<String> tileUrls(Map<String, Object> source) {
    Object tiles = source.get("tiles");
    if (!(tiles instanceof List<?> tileUrls) || tileUrls.isEmpty()) {
      return List.of();
    }
    return tileUrls.stream().map(String::valueOf).toList();
  }

  private static boolean isExternalTileHost(String tileUrl) {
    String lower = tileUrl.toLowerCase();
    return lower.startsWith("http://")
        || lower.startsWith("https://")
        || OfflinePackageManifestFixtures.isExternalTileUrlRejected(tileUrl);
  }

  private boolean tileBlobMatchesFixtureItem(TileItem tile, SourceConstants sourceConstants) {
    TileBlobResponse blob =
        localTileService.getTile(tile.styleId(), tile.z(), tile.x(), tile.y());
    String actualChecksum = sha256(blob.bytes());
    String documentedChecksum = sourceConstants.s7TileChecksums().get(tile.itemKey());
    return blob.bytes().length == tile.bytes()
        && Objects.equals(tile.checksum(), documentedChecksum)
        && Objects.equals(actualChecksum, documentedChecksum)
        && OfflinePackageManifestFixtures.isLocalTileUri(tile.url());
  }

  private static String sha256(byte[] bytes) {
    try {
      return "sha256:"
          + java.util.HexFormat.of()
              .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest is unavailable", exception);
    }
  }

  private record StyleProbe(
      String tilePathTemplate, boolean styleUsesLocalTileSource, boolean externalTileHostCalled) {}

  private record SourceConstants(
      String harnessScenariosTileManifestId,
      String harnessScenariosZoomRange,
      String harnessScenariosTileKeyRange,
      String harnessScenariosBlobUriTemplate,
      String s7TileManifestId,
      String s7ZoomRange,
      String s7TileKeyRange,
      String s7BlobUriTemplate,
      String commonFixturesTileManifestId,
      String commonFixturesZoomRange,
      String commonFixturesTileKeyRange,
      String commonFixturesBlobUriTemplate,
      String commonFixturesOverallAreaHash,
      String commonPackageEventType,
      String commonPackageEventIncidentId,
      String commonOutboxDependencyGroup,
      String commonOutboxEntityType,
      String commonBoardSlot,
      Map<String, String> s7TileChecksums) {

    static SourceConstants load() {
      try {
        Path root = repoRoot();
        String harnessScenarios =
            Files.readString(root.resolve("docs/spec/harness-scenarios.md"));
        JsonNode s7 =
            OBJECT_MAPPER
                .readTree(root.resolve("docs/spec/specs/S7.json").toFile())
                .at("/harness_fixtures/tile_manifest_fixture");
        JsonNode commonRoot =
            OBJECT_MAPPER.readTree(root.resolve("docs/spec/fixtures/common-fixtures.json").toFile());
        JsonNode commonFixtures =
            commonRoot.at("/confirmed/tileManifest");
        JsonNode packageReplay =
            commonRoot.at("/confirmed/outboxReplay/sc09PackageReplay");
        JsonNode expectedS4Event = packageReplay.path("expectedS4Event");
        JsonNode writeOperation = packageReplay.path("writeOperation");
        JsonNode expectedBoardProbe = packageReplay.path("expectedBoardProbe");

        return new SourceConstants(
            extract(harnessScenarios, "tileManifestId=([^`,]+)"),
            extractZoomRange(harnessScenarios),
            extractTileKeyRange(harnessScenarios),
            extract(harnessScenarios, "blob URI `([^`]+)`"),
            s7.path("manifestId").asText(),
            zoomRange(
                s7.path("zoomRange").path("min").asInt(),
                s7.path("zoomRange").path("max").asInt()),
            tileKeyRange(s7.path("tileKeyRange")),
            s7.path("blobUriTemplate").asText(),
            commonFixtures.path("manifestId").asText(),
            zoomRange(
                commonFixtures.path("tileKeyRange").path("minZ").asInt(),
                commonFixtures.path("tileKeyRange").path("maxZ").asInt()),
            tileKeyRange(commonFixtures.path("tileKeyRange")),
            commonFixtures.path("blobUriTemplate").asText(),
            commonFixtures.path("overallAreaHash").asText(),
            expectedS4Event.path("type").asText(),
            expectedS4Event.path("incidentId").asText(),
            packageReplay.path("dependencyGroup").asText(),
            writeOperation.path("entityType").asText(),
            expectedBoardProbe.path("slot").asText(),
            tileChecksums(s7.path("tiles")));
      } catch (IOException exception) {
        throw new UncheckedIOException("Failed to read SC-03 source constants", exception);
      }
    }

    private static Path repoRoot() {
      Path cwd = Path.of("").toAbsolutePath().normalize();
      if (Files.isDirectory(cwd.resolve("docs"))) {
        return cwd;
      }
      return cwd.getParent();
    }

    private static String extract(String text, String regex) {
      var matcher = Pattern.compile(regex).matcher(text);
      if (!matcher.find()) {
        throw new IllegalStateException("Missing source document value for regex: " + regex);
      }
      return matcher.group(1);
    }

    private static String extractZoomRange(String harnessScenarios) {
      return extract(harnessScenarios, "z=(\\d+\\.\\.\\d+)");
    }

    private static String extractTileKeyRange(String harnessScenarios) {
      return "z="
          + extract(harnessScenarios, "z=(\\d+\\.\\.\\d+)")
          + ",x="
          + extract(harnessScenarios, "x=(\\d+\\.\\.\\d+)")
          + ",y="
          + extract(harnessScenarios, "y=(\\d+\\.\\.\\d+)");
    }

    private static String zoomRange(int min, int max) {
      return "%d..%d".formatted(min, max);
    }

    private static String tileKeyRange(JsonNode tileKeyRange) {
      return "z=%d..%d,x=%d..%d,y=%d..%d"
          .formatted(
              tileKeyRange.path("minZ").asInt(),
              tileKeyRange.path("maxZ").asInt(),
              tileKeyRange.path("minX").asInt(),
              tileKeyRange.path("maxX").asInt(),
              tileKeyRange.path("minY").asInt(),
              tileKeyRange.path("maxY").asInt());
    }

    private static Map<String, String> tileChecksums(JsonNode tiles) {
      Map<String, String> checksums = new LinkedHashMap<>();
      for (JsonNode tile : tiles) {
        checksums.put(tile.path("itemKey").asText(), tile.path("checksum").asText());
      }
      return Map.copyOf(checksums);
    }
  }

  public record Sc03PackageTileHarnessReport(
      String scenarioId,
      String incidentId,
      String tileManifestId,
      MockedSourceContractsReport mockedSourceContracts,
      PackageManifestReport packageManifest,
      LocalTileProbeReport localTileProbe,
      TileFixtureExactnessReport tileFixtureExactnessReport) {}

  public record MockedSourceContractsReport(
      List<String> present, boolean allPresent, boolean externalTileHostCalled) {}

  public record PackageManifestReport(
      List<String> itemGroups,
      String incidentId,
      String missingPersonIncidentId,
      String opId,
      String assignedAreaId,
      String initialMarkerId,
      String overallSearchAreaId,
      String tileGroupManifestId) {}

  public record LocalTileProbeReport(
      String styleId,
      String stylePath,
      String tilePathTemplate,
      int minZoom,
      int maxZoom,
      int maxTileItems,
      boolean styleUsesLocalTileSource,
      boolean tileBlobUsesLocalFixture,
      boolean matchesS7HarnessConstants) {}

  public record TileFixtureExactnessReport(
      String tileManifestId,
      String zoomRange,
      String tileKeyRange,
      String blobUriTemplate,
      List<String> localTileUris,
      List<String> failureKeys,
      boolean alignedWithHarnessScenarios,
      boolean alignedWithS7,
      boolean alignedWithCommonFixtures,
      String harnessScenariosTileManifestId,
      String harnessScenariosZoomRange,
      String harnessScenariosTileKeyRange,
      String harnessScenariosBlobUriTemplate,
      String s7TileManifestId,
      String s7ZoomRange,
      String s7TileKeyRange,
      String s7BlobUriTemplate,
      String commonFixturesTileManifestId,
      String commonFixturesZoomRange,
      String commonFixturesTileKeyRange,
      String commonFixturesBlobUriTemplate) {}
}
