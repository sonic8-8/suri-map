package com.surimap.offlinepackage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.surimap.incident.domain.IncidentRecord;
import com.surimap.incident.repository.IncidentMapper;
import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import com.surimap.maparea.query.OverallSearchAreaResult;
import com.surimap.maparea.query.SearchAreaAssignmentQuery;
import com.surimap.maparea.query.SearchAreaCollection;
import com.surimap.maparea.query.SearchAreaFilters;
import com.surimap.maparea.query.SearchAreaQuery;
import com.surimap.marker.query.MarkerQuery;
import com.surimap.marker.query.MarkerQueryFilters;
import com.surimap.marker.query.MarkerQueryResult;
import com.surimap.offlinepackage.dto.OfflinePackageManifestResponse;
import com.surimap.offlinepackage.dto.TileBlobResponse;
import com.surimap.offlinepackage.dto.TileStyleResponse;
import com.surimap.offlinepackage.repository.OfflinePackageManifestRecord;
import com.surimap.offlinepackage.repository.OfflinePackageMapper;
import com.surimap.offlinepackage.service.OfflinePackageRepository;
import com.surimap.offlinepackage.service.TileService;
import com.surimap.operationalperiod.query.OperationalPeriodQuery;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;

@DisplayName("OfflinePackageRepository read-only query")
class OfflinePackageRepositoryReadOnlyQueryTest {

  @Test
  @DisplayName("byIncident reads installation statuses without seeding fixture manifests")
  void byIncidentReadsWithoutFixtureSeeding() {
    OfflinePackageMapper mapper = Mockito.mock(OfflinePackageMapper.class);
    OfflinePackageRepository repository = new OfflinePackageRepository(mapper);
    String incidentId = "10000000-0000-4000-8000-000000000001";
    when(mapper.findStatusesByIncident(incidentId)).thenReturn(List.of());

    assertThat(repository.byIncident(incidentId)).isEmpty();

    verify(mapper).findStatusesByIncident(incidentId);
    verifyNoMoreInteractions(mapper);
  }

  @Test
  @DisplayName("manifest tile metadata is computed from TileService downloadable bytes")
  void manifestTileMetadataUsesTileServiceDownloadableBytes() {
    OfflinePackageMapper mapper = Mockito.mock(OfflinePackageMapper.class);
    OfflinePackageRepository repository =
        new OfflinePackageRepository(mapper, new GzipFixtureTileService());
    when(mapper.findCurrentManifestByIncident(OfflinePackageRepository.INCIDENT_ID))
        .thenReturn(
            new OfflinePackageManifestRecord(
                UUID.fromString(OfflinePackageRepository.MANIFEST_ID),
                UUID.fromString(OfflinePackageRepository.INCIDENT_ID),
                OfflinePackageRepository.MANIFEST_VERSION,
                UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb0001"),
                1L,
                "1111111111111111111111111111111111111111111111111111111111111111",
                OffsetDateTime.parse("2026-04-28T03:00:00Z")));

    OfflinePackageManifestResponse manifest =
        repository.manifest(
            OfflinePackageRepository.INCIDENT_ID, OfflinePackageRepository.POLICE_PHONE_ID);

    assertThat(manifest.tileItems())
        .allSatisfy(
            tile -> {
              byte[] expected = tilePayload(tile.styleId(), tile.z(), tile.x(), tile.y());
              assertThat(tile.bytes()).isEqualTo(expected.length);
              assertThat(tile.checksum()).isEqualTo(prefixedSha256(expected));
            });
  }

  @Test
  @DisplayName("source manifest tile items are computed from overall search area coverage")
  void sourceManifestTileItemsUseOverallSearchAreaCoverage() {
    UUID incidentId = UUID.fromString("10000000-0000-4000-8000-000000003101");
    UUID opId = UUID.fromString("88888888-0000-4000-8000-000000003101");
    UUID overallAreaId = UUID.fromString("bbbbbbbb-0000-4000-8000-000000003101");
    OfflinePackageMapper mapper = Mockito.mock(OfflinePackageMapper.class);
    IncidentMapper incidentMapper = Mockito.mock(IncidentMapper.class);
    OperationalPeriodQuery operationalPeriodQuery = Mockito.mock(OperationalPeriodQuery.class);
    SearchAreaQuery searchAreaQuery = Mockito.mock(SearchAreaQuery.class);
    SearchAreaAssignmentQuery assignmentQuery = Mockito.mock(SearchAreaAssignmentQuery.class);
    MarkerQuery markerQuery = Mockito.mock(MarkerQuery.class);
    OfflinePackageRepository repository =
        new OfflinePackageRepository(
            mapper,
            provider(incidentMapper),
            provider(operationalPeriodQuery),
            provider(searchAreaQuery),
            provider(assignmentQuery),
            provider(markerQuery),
            provider(new GzipFixtureTileService()));
    IncidentRecord incident = new IncidentRecord();
    incident.setId(incidentId);
    incident.setSourceIncidentId(UUID.fromString("00000000-0000-4000-8000-000000003101"));
    incident.setStatus("OPEN");
    incident.setVersion(1L);
    GeoJsonPolygon overallSearchArea =
        polygon(
            "126.904000",
            "35.158000",
            "126.923000",
            "35.158000",
            "126.923000",
            "35.173000",
            "126.904000",
            "35.173000");

    when(mapper.findCurrentManifestByIncident(incidentId.toString())).thenReturn(null);
    when(incidentMapper.findByIncidentId(incidentId)).thenReturn(Optional.of(incident));
    when(incidentMapper.findMissingPersonByIncidentId(incidentId)).thenReturn(Optional.empty());
    when(operationalPeriodQuery.list(incidentId))
        .thenReturn(
            List.of(
                new OperationalPeriodRow(
                    opId, incidentId, "ACTIVE", 1, Instant.EPOCH, null, null, 1L)));
    when(searchAreaQuery.overallOf(incidentId))
        .thenReturn(
            Optional.of(
                new OverallSearchAreaResult(
                    overallAreaId,
                    incidentId,
                    "ACTIVE",
                    1L,
                    overallSearchArea,
                    List.of(),
                    Instant.EPOCH)));
    when(searchAreaQuery.byOp(eq(opId), any(SearchAreaFilters.class)))
        .thenReturn(new SearchAreaCollection(incidentId, 0L, List.of()));
    when(assignmentQuery.byOp(opId)).thenReturn(List.of());
    when(markerQuery.byIncident(eq(incidentId), any(MarkerQueryFilters.class)))
        .thenReturn(new MarkerQueryResult(incidentId, List.of()));

    OfflinePackageManifestResponse manifest =
        repository.manifest(incidentId.toString(), OfflinePackageRepository.POLICE_PHONE_ID);

    assertThat(manifest.tileItems()).hasSize(20);
    assertThat(tileKeys(manifest.tileItems()))
        .contains(
            "tile:osm-local:15:27935:12960",
            "tile:osm-local:15:27936:12961",
            "tile:osm-local:16:55870:25920",
            "tile:osm-local:16:55873:25923")
        .doesNotContain("tile:osm-local:16:27925:12681");
    assertThat(manifest.tileItems())
        .allSatisfy(
            tile -> {
              byte[] expected = tilePayload(tile.styleId(), tile.z(), tile.x(), tile.y());
              assertThat(tile.bytes()).isEqualTo(expected.length);
              assertThat(tile.checksum()).isEqualTo(prefixedSha256(expected));
            });
  }

  private static byte[] tilePayload(String style, int z, int x, int y) {
    return "downloaded:%s:%d:%d:%d".formatted(style, z, x, y).getBytes(StandardCharsets.UTF_8);
  }

  private static GeoJsonPolygon polygon(
      String x1, String y1, String x2, String y2, String x3, String y3, String x4, String y4) {
    return new GeoJsonPolygon(
        "Polygon",
        List.of(
            List.of(point(x1, y1), point(x2, y2), point(x3, y3), point(x4, y4), point(x1, y1))));
  }

  private static List<BigDecimal> point(String lon, String lat) {
    return List.of(new BigDecimal(lon), new BigDecimal(lat));
  }

  private static List<String> tileKeys(List<OfflinePackageManifestResponse.TileItem> tiles) {
    return tiles.stream().map(OfflinePackageManifestResponse.TileItem::itemKey).toList();
  }

  private static <T> StaticObjectProvider<T> provider(T value) {
    return new StaticObjectProvider<>(value);
  }

  private static String prefixedSha256(byte[] bytes) {
    try {
      return "sha256:"
          + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest is unavailable", exception);
    }
  }

  private static byte[] gzip(byte[] bytes) {
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
        gzip.write(bytes);
      }
      return output.toByteArray();
    } catch (IOException exception) {
      throw new IllegalStateException("gzip test fixture cannot be encoded", exception);
    }
  }

  private static final class GzipFixtureTileService implements TileService {

    @Override
    public TileStyleResponse getStyle(String styleId) {
      throw new UnsupportedOperationException("style is not used by this test");
    }

    @Override
    public TileBlobResponse getTile(String style, int z, int x, int y) {
      return new TileBlobResponse(
          MediaType.valueOf("application/x-protobuf"), gzip(tilePayload(style, z, x, y)), "gzip");
    }

    @Override
    public TileBlobResponse getGlyph(String fontStack, String range) {
      throw new UnsupportedOperationException("glyph is not used by this test");
    }
  }

  private record StaticObjectProvider<T>(T value) implements ObjectProvider<T> {

    @Override
    public T getObject(Object... args) throws BeansException {
      return value;
    }

    @Override
    public T getObject() throws BeansException {
      return value;
    }

    @Override
    public T getIfAvailable() throws BeansException {
      return value;
    }

    @Override
    public T getIfAvailable(Supplier<T> defaultSupplier) throws BeansException {
      return value == null ? defaultSupplier.get() : value;
    }

    @Override
    public T getIfUnique() throws BeansException {
      return value;
    }

    @Override
    public Stream<T> stream() {
      return value == null ? Stream.empty() : Stream.of(value);
    }

    @Override
    public Iterator<T> iterator() {
      return stream().iterator();
    }
  }
}
