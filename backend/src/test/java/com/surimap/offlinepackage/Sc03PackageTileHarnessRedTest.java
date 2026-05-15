package com.surimap.offlinepackage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.offlinepackage.dto.TileBlobResponse;
import com.surimap.offlinepackage.dto.TileStyleResponse;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures.TileItem;
import com.surimap.offlinepackage.service.LocalTileService;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * L6-T10A RED: SC-03 package/tile harness closure.
 *
 * <p>The runner is intentionally absent in RED. GREEN should implement only test-local harness
 * behavior that proves mocked source contracts, package manifest groups, local tile service/style
 * probes, and tile fixture exactness without adding production behavior.
 */
@DisplayName("L6-T10A SC-03 package/tile harness RED")
class Sc03PackageTileHarnessRedTest {

  private static final String RUNNER_CLASS =
      "com.surimap.offlinepackage.Sc03PackageTileHarnessRunner";

  @Test
  @DisplayName("package/tile harness returns fixture exactness report for SC-03 closure")
  void packageTileHarnessReturnsFixtureExactnessReport() {
    Object result = run("runPackageTileClosure");

    assertThat(value(result, "scenarioId")).isEqualTo("SC-03");
    assertThat(value(result, "incidentId")).isEqualTo(OfflinePackageManifestFixtures.INCIDENT_ID);
    assertThat(value(result, "tileManifestId"))
        .isEqualTo(OfflinePackageManifestFixtures.MANIFEST_ID);

    Object contracts = call(result, "mockedSourceContracts");
    assertThat(listValue(contracts, "present"))
        .containsExactlyInAnyOrder(
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
    assertThat(booleanValue(contracts, "allPresent")).isTrue();
    assertThat(booleanValue(contracts, "externalTileHostCalled")).isFalse();

    Object manifest = call(result, "packageManifest");
    assertThat(listValue(manifest, "itemGroups"))
        .containsExactlyElementsOf(OfflinePackageManifestFixtures.PACKAGE_ITEM_TYPES);
    assertThat(value(manifest, "incidentId")).isEqualTo(OfflinePackageManifestFixtures.INCIDENT_ID);
    assertThat(value(manifest, "missingPersonIncidentId"))
        .isEqualTo(OfflinePackageManifestFixtures.INCIDENT_ID);
    assertThat(value(manifest, "opId")).isEqualTo(OfflinePackageManifestFixtures.OP_ID);
    assertThat(value(manifest, "assignedAreaId"))
        .isEqualTo(OfflinePackageManifestFixtures.ASSIGNED_AREA_ID);
    assertThat(value(manifest, "initialMarkerId"))
        .isEqualTo(OfflinePackageManifestFixtures.INITIAL_MARKER_ID);
    assertThat(value(manifest, "overallSearchAreaId"))
        .isEqualTo(OfflinePackageManifestFixtures.OVERALL_SEARCH_AREA_ID);
    assertThat(value(manifest, "tileGroupManifestId"))
        .isEqualTo(OfflinePackageManifestFixtures.MANIFEST_ID);

    Object tileProbe = call(result, "localTileProbe");
    assertThat(value(tileProbe, "styleId")).isEqualTo(OfflinePackageManifestFixtures.STYLE_ID);
    assertThat(value(tileProbe, "stylePath")).isEqualTo("/tiles/styles/osm-local.json");
    assertThat(value(tileProbe, "tilePathTemplate")).isEqualTo("/tiles/osm-local/{z}/{x}/{y}.pbf");
    assertThat(value(tileProbe, "minZoom"))
        .isEqualTo(String.valueOf(OfflinePackageManifestFixtures.MIN_Z));
    assertThat(value(tileProbe, "maxZoom"))
        .isEqualTo(String.valueOf(OfflinePackageManifestFixtures.MAX_Z));
    assertThat(value(tileProbe, "maxTileItems")).isEqualTo("8");
    assertThat(booleanValue(tileProbe, "styleUsesLocalTileSource")).isTrue();
    assertThat(booleanValue(tileProbe, "tileBlobUsesLocalFixture")).isTrue();
    assertThat(booleanValue(tileProbe, "matchesS7HarnessConstants")).isTrue();

    Object report = call(result, "tileFixtureExactnessReport");
    assertThat(value(report, "tileManifestId"))
        .isEqualTo(OfflinePackageManifestFixtures.MANIFEST_ID);
    assertThat(value(report, "zoomRange")).isEqualTo("15..16");
    assertThat(value(report, "tileKeyRange")).isEqualTo("z=15..16,x=27935..55873,y=12960..25923");
    assertThat(value(report, "blobUriTemplate"))
        .isEqualTo(OfflinePackageManifestFixtures.BLOB_URI_TEMPLATE);
    assertThat(listValue(report, "localTileUris"))
        .containsExactly(
            "local://tiles/inc-precinct-first-001/15/27935/12960.pbf",
            "local://tiles/inc-precinct-first-001/15/27936/12960.pbf",
            "local://tiles/inc-precinct-first-001/16/55870/25920.pbf");
    assertThat(listValue(report, "failureKeys"))
        .containsExactlyInAnyOrderElementsOf(OfflinePackageManifestFixtures.FAILURE_KEYS);
    assertThat(booleanValue(report, "alignedWithHarnessScenarios")).isTrue();
    assertThat(booleanValue(report, "alignedWithS7")).isTrue();
    assertThat(booleanValue(report, "alignedWithCommonFixtures")).isTrue();
  }

  @Test
  @DisplayName("style probe fails when any tile source URL points to an external tile host")
  void externalTileHostInStyleSourceIsDetected() {
    Object result =
        new Sc03PackageTileHarnessRunner(new ExternalHostTileService()).runPackageTileClosure();

    Object contracts = call(result, "mockedSourceContracts");
    assertThat(booleanValue(contracts, "externalTileHostCalled")).isTrue();

    Object tileProbe = call(result, "localTileProbe");
    assertThat(booleanValue(tileProbe, "styleUsesLocalTileSource")).isFalse();
    assertThat(booleanValue(tileProbe, "matchesS7HarnessConstants")).isFalse();
  }

  @Test
  @DisplayName("tile probe rejects same-length corrupt blob instead of accepting length only")
  void sameLengthCorruptTileBlobIsRejectedByChecksum() {
    Object result =
        new Sc03PackageTileHarnessRunner(new SameLengthCorruptTileService())
            .runPackageTileClosure();

    Object tileProbe = call(result, "localTileProbe");
    assertThat(booleanValue(tileProbe, "tileBlobUsesLocalFixture")).isFalse();
    assertThat(booleanValue(tileProbe, "matchesS7HarnessConstants")).isFalse();
  }

  @Test
  @DisplayName("exactness report exposes source-document constants behind alignment booleans")
  void alignmentEvidenceComesFromHarnessS7AndCommonFixtureSources() throws IOException {
    SourceConstants constants = SourceConstants.load();
    Object result = run("runPackageTileClosure");
    Object report = call(result, "tileFixtureExactnessReport");

    assertThat(value(report, "harnessScenariosTileManifestId"))
        .isEqualTo(constants.harnessScenariosTileManifestId());
    assertThat(value(report, "harnessScenariosZoomRange"))
        .isEqualTo(constants.harnessScenariosZoomRange());
    assertThat(value(report, "harnessScenariosTileKeyRange"))
        .isEqualTo(constants.harnessScenariosTileKeyRange());
    assertThat(value(report, "harnessScenariosBlobUriTemplate"))
        .isEqualTo(constants.harnessScenariosBlobUriTemplate());

    assertThat(value(report, "s7TileManifestId")).isEqualTo(constants.s7TileManifestId());
    assertThat(value(report, "s7ZoomRange")).isEqualTo(constants.s7ZoomRange());
    assertThat(value(report, "s7TileKeyRange")).isEqualTo(constants.s7TileKeyRange());
    assertThat(value(report, "s7BlobUriTemplate")).isEqualTo(constants.s7BlobUriTemplate());

    assertThat(value(report, "commonFixturesTileManifestId"))
        .isEqualTo(constants.commonFixturesTileManifestId());
    assertThat(value(report, "commonFixturesZoomRange"))
        .isEqualTo(constants.commonFixturesZoomRange());
    assertThat(value(report, "commonFixturesTileKeyRange"))
        .isEqualTo(constants.commonFixturesTileKeyRange());
    assertThat(value(report, "commonFixturesBlobUriTemplate"))
        .isEqualTo(constants.commonFixturesBlobUriTemplate());
  }

  private static Object run(String methodName) {
    Object runner = newRunner();
    return call(runner, methodName);
  }

  private static Object newRunner() {
    try {
      return Class.forName(RUNNER_CLASS).getDeclaredConstructor().newInstance();
    } catch (ClassNotFoundException exception) {
      fail("Missing SC-03 package/tile harness runner: " + RUNNER_CLASS, exception);
    } catch (ReflectiveOperationException exception) {
      fail("SC-03 package/tile harness runner must expose a no-arg constructor", exception);
    }
    throw new IllegalStateException("unreachable");
  }

  private static Object call(Object target, String methodName) {
    try {
      Method method = target.getClass().getMethod(methodName);
      return method.invoke(target);
    } catch (NoSuchMethodException exception) {
      fail("Missing SC-03 harness evidence method: " + methodName, exception);
    } catch (IllegalAccessException exception) {
      fail("SC-03 harness evidence method must be public: " + methodName, exception);
    } catch (InvocationTargetException exception) {
      fail("SC-03 harness evidence method threw: " + methodName, exception.getCause());
    }
    throw new IllegalStateException("unreachable");
  }

  private static String value(Object target, String methodName) {
    Object value = call(target, methodName);
    return String.valueOf(value);
  }

  private static boolean booleanValue(Object target, String methodName) {
    Object value = call(target, methodName);
    assertThat(value).isInstanceOf(Boolean.class);
    return (Boolean) value;
  }

  private static List<String> listValue(Object target, String methodName) {
    Object value = call(target, methodName);
    assertThat(value).isInstanceOf(List.class);
    return ((List<?>) value).stream().map(String::valueOf).toList();
  }

  private static final class ExternalHostTileService extends LocalTileService {

    @Override
    public TileStyleResponse getStyle(String styleId) {
      return new TileStyleResponse(
          8,
          Map.of(
              styleId,
              Map.of(
                  "type",
                  "vector",
                  "tiles",
                  List.of(
                      "/tiles/osm-local/{z}/{x}/{y}.pbf",
                      "https://api.mapbox.com/v4/mapbox.mapbox-streets-v8/{z}/{x}/{y}.pbf"),
                  "minzoom",
                  OfflinePackageManifestFixtures.MIN_Z,
                  "maxzoom",
                  OfflinePackageManifestFixtures.MAX_Z,
                  "attribution",
                  "OpenStreetMap contributors / OpenMapTiles")),
          List.of(Map.of("id", "landcover-fill", "type", "fill", "source", styleId)),
          Map.of("attribution", "OpenStreetMap contributors / OpenMapTiles"));
    }
  }

  private static final class SameLengthCorruptTileService extends LocalTileService {

    private static final MediaType APPLICATION_X_PROTOBUF =
        MediaType.valueOf("application/x-protobuf");

    @Override
    public TileBlobResponse getTile(String style, int z, int x, int y) {
      TileItem tile =
          OfflinePackageManifestFixtures.tileManifest().tiles().stream()
              .filter(candidate -> candidate.styleId().equals(style))
              .filter(candidate -> candidate.z() == z)
              .filter(candidate -> candidate.x() == x)
              .filter(candidate -> candidate.y() == y)
              .findFirst()
              .orElseThrow();
      byte[] corruptSameLength = new byte[tile.bytes()];
      Arrays.fill(corruptSameLength, (byte) 0x7f);
      return new TileBlobResponse(APPLICATION_X_PROTOBUF, corruptSameLength);
    }
  }

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
      String commonFixturesBlobUriTemplate) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static SourceConstants load() throws IOException {
      Path root = repoRoot();
      String harness =
          Files.readString(root.resolve("docs/spec/harness-scenarios.md"));
      JsonNode s7 =
          OBJECT_MAPPER
              .readTree(root.resolve("docs/spec/specs/S7.json").toFile())
              .at("/harness_fixtures/tile_manifest_fixture");
      JsonNode common =
          OBJECT_MAPPER
              .readTree(root.resolve("docs/spec/fixtures/common-fixtures.json").toFile())
              .at("/confirmed/tileManifest");

      return new SourceConstants(
          extract(harness, "tileManifestId=([^`,]+)"),
          extractZoomRange(harness),
          extractTileKeyRange(harness),
          extract(harness, "blob URI `([^`]+)`"),
          s7.path("manifestId").asText(),
          zoomRange(
              s7.path("zoomRange").path("min").asInt(),
              s7.path("zoomRange").path("max").asInt()),
          tileKeyRange(s7.path("tileKeyRange")),
          s7.path("blobUriTemplate").asText(),
          common.path("manifestId").asText(),
          zoomRange(
              common.path("tileKeyRange").path("minZ").asInt(),
              common.path("tileKeyRange").path("maxZ").asInt()),
          tileKeyRange(common.path("tileKeyRange")),
          common.path("blobUriTemplate").asText());
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
      assertThat(matcher.find()).as("source document regex: %s", regex).isTrue();
      return matcher.group(1);
    }

    private static String extractZoomRange(String harness) {
      return extract(harness, "z=(\\d+\\.\\.\\d+)");
    }

    private static String extractTileKeyRange(String harness) {
      return "z="
          + extract(harness, "z=(\\d+\\.\\.\\d+)")
          + ",x="
          + extract(harness, "x=(\\d+\\.\\.\\d+)")
          + ",y="
          + extract(harness, "y=(\\d+\\.\\.\\d+)");
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
  }
}
