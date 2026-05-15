package com.surimap.offlinepackage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.offlinepackage.controller.TileController;
import com.surimap.offlinepackage.dto.TileBlobResponse;
import com.surimap.offlinepackage.dto.TileStyleResponse;
import com.surimap.offlinepackage.exception.TileExceptionHandler;
import com.surimap.offlinepackage.exception.TileUnavailableException;
import com.surimap.offlinepackage.fixture.OfflinePackageManifestFixtures;
import com.surimap.offlinepackage.service.TileService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TileController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(TileExceptionHandler.class)
@DisplayName("로컬 타일·스타일 API")
class TileControllerTest {

  private static final String AUTHORIZATION = "Bearer web-tile-session";
  private static final String STYLE_ID = OfflinePackageManifestFixtures.STYLE_ID;
  private static final MediaType APPLICATION_X_PROTOBUF =
      MediaType.valueOf("application/x-protobuf");
  private static final byte[] LOCAL_TILE_BYTES = repeatedBytes(0xaa, 18_432);
  private static final byte[] LOCAL_GLYPH_BYTES = repeatedBytes(0xbb, 4_096);
  private static final String ACCOUNT_ID = "11111111-1111-1111-1111-111111110003";
  private static final String POLICE_PHONE_ID = OfflinePackageManifestFixtures.POLICE_PHONE_ID;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TileService tileService;

  @ParameterizedTest
  @EnumSource(
      value = Channel.class,
      names = {"WEB", "APP"})
  @DisplayName("APP/WEB style JSON은 MapLibre 필수 필드와 로컬 vector tile URL만 반환한다")
  void publicSessionStyleJsonReturnsLocalMapLibreStyle(Channel channel) throws Exception {
    when(tileService.getStyle(STYLE_ID)).thenReturn(localStyleResponse());

    mockMvc
        .perform(
            get("/tiles/styles/{styleId}.json", STYLE_ID)
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", channel.name())
                .header("Host", "suri-map.local:8080")
                .principal(authentication(channel)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.version", is(8)))
        .andExpect(jsonPath("$.sources").exists())
        .andExpect(
            jsonPath(
                "$.sources.*.tiles[0]",
                hasItem("http://suri-map.local:8080/tiles/osm-local/{z}/{x}/{y}.pbf")))
        .andExpect(
            jsonPath(
                "$.glyphs",
                is("http://suri-map.local:8080/tiles/fonts/{fontstack}/{range}.pbf")))
        .andExpect(jsonPath("$.layers").isArray())
        .andExpect(jsonPath("$.layers.length()", greaterThan(0)))
        .andExpect(
            jsonPath(
                "$.metadata.attribution",
                anyOf(
                    containsString("OpenStreetMap"),
                    containsString("OpenMapTiles"),
                    containsString("OSM"))))
        .andExpect(content().string(not(containsString("tile.openstreetmap.org"))))
        .andExpect(content().string(not(containsString(".tile.openstreetmap.org"))))
        .andExpect(content().string(not(containsString("mapbox.com"))))
        .andExpect(content().string(not(containsString("googleapis.com"))));
  }

  @Test
  @DisplayName("프록시 뒤 style JSON은 forwarded origin 기준 절대 tile URL을 반환한다")
  void styleJsonUsesForwardedOriginForTileUrls() throws Exception {
    when(tileService.getStyle(STYLE_ID)).thenReturn(localStyleResponse());

    mockMvc
        .perform(
            get("/tiles/styles/{styleId}.json", STYLE_ID)
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "APP")
                .header("Host", "internal-api:8080")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "api.surimap.example")
                .header("X-Forwarded-Port", "443")
                .principal(authentication(Channel.APP)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.sources.*.tiles[0]",
                hasItem("https://api.surimap.example/tiles/osm-local/{z}/{x}/{y}.pbf")));
  }

  @ParameterizedTest
  @EnumSource(
      value = Channel.class,
      names = {"WEB", "APP"})
  @DisplayName("APP/WEB tile pbf는 로컬 fixture bytes와 application/x-protobuf를 반환한다")
  void publicSessionTilePbfReturnsLocalFixtureBytes(Channel channel) throws Exception {
    when(tileService.getTile(STYLE_ID, 15, 27935, 12960))
        .thenReturn(new TileBlobResponse(APPLICATION_X_PROTOBUF, LOCAL_TILE_BYTES));

    mockMvc
        .perform(
            get("/tiles/{style}/{z}/{x}/{y}.pbf", STYLE_ID, 15, 27935, 12960)
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", channel.name())
                .principal(authentication(channel)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_X_PROTOBUF))
        .andExpect(content().bytes(LOCAL_TILE_BYTES))
        .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty());
  }

  @Test
  @DisplayName("tile pbf의 gzip content encoding을 응답 헤더로 보존한다")
  void tilePbfPreservesContentEncoding() throws Exception {
    when(tileService.getTile(STYLE_ID, 16, 55877, 25377))
        .thenReturn(new TileBlobResponse(APPLICATION_X_PROTOBUF, LOCAL_TILE_BYTES, "gzip"));

    mockMvc
        .perform(
            get("/tiles/{style}/{z}/{x}/{y}.pbf", STYLE_ID, 16, 55877, 25377)
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "WEB")
                .principal(authentication(Channel.WEB)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_X_PROTOBUF))
        .andExpect(header().string(HttpHeaders.CONTENT_ENCODING, "gzip"))
        .andExpect(content().bytes(LOCAL_TILE_BYTES));
  }

  @Test
  @DisplayName("APP/WEB glyph pbf는 로컬 폰트 bytes와 application/x-protobuf를 반환한다")
  void publicSessionGlyphPbfReturnsLocalFontBytes() throws Exception {
    when(tileService.getGlyph("Noto Sans CJK KR Regular", "0-255"))
        .thenReturn(new TileBlobResponse(APPLICATION_X_PROTOBUF, LOCAL_GLYPH_BYTES));

    mockMvc
        .perform(
            get("/tiles/fonts/{fontStack}/{range}.pbf", "Noto Sans CJK KR Regular", "0-255")
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "WEB")
                .principal(authentication(Channel.WEB)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_X_PROTOBUF))
        .andExpect(content().bytes(LOCAL_GLYPH_BYTES));
  }

  @Test
  @DisplayName("INTERNAL tile 요청은 channel_not_allowed로 거부한다")
  void internalTileRequestRejected() throws Exception {
    mockMvc
        .perform(
            get("/tiles/{style}/{z}/{x}/{y}.pbf", STYLE_ID, 15, 27935, 12960)
                .header("Authorization", "Bearer internal-tile-session")
                .header("X-Client-Channel", "INTERNAL")
                .principal(authentication(Channel.INTERNAL)))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.error", is("channel_not_allowed")));

    verifyNoInteractions(tileService);
  }

  @Test
  @DisplayName("인증이 없는 tile 요청은 channel_not_allowed로 거부한다")
  void missingAuthenticationRejected() throws Exception {
    mockMvc
        .perform(get("/tiles/{style}/{z}/{x}/{y}.pbf", STYLE_ID, 15, 27935, 12960))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.error", is("channel_not_allowed")));

    verifyNoInteractions(tileService);
  }

  @Test
  @DisplayName("SuriMapAuthentication이 아닌 basic principal은 channel_not_allowed로 거부한다")
  void basicPrincipalRejected() throws Exception {
    var basicAuthentication =
        new UsernamePasswordAuthenticationToken(
            "dev", "dev-password", List.of(new SimpleGrantedAuthority("ROLE_DEVELOPER")));

    mockMvc
        .perform(
            get("/tiles/{style}/{z}/{x}/{y}.pbf", STYLE_ID, 15, 27935, 12960)
                .header("Authorization", "Basic ZGV2OmRldi1wYXNzd29yZA==")
                .principal(basicAuthentication))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.error", is("channel_not_allowed")));

    verifyNoInteractions(tileService);
  }

  @Test
  @DisplayName("fixture에 없는 style은 tile_unavailable 503을 반환한다")
  void unavailableStyleReturnsTileUnavailable() throws Exception {
    when(tileService.getStyle("external-mapbox")).thenThrow(new TileUnavailableException());

    mockMvc
        .perform(
            get("/tiles/styles/{styleId}.json", "external-mapbox")
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "WEB")
                .principal(authentication(Channel.WEB)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.error", is("tile_unavailable")));
  }

  @Test
  @DisplayName("fixture 범위 밖 tile은 tile_unavailable 503을 반환한다")
  void unavailableTileReturnsTileUnavailable() throws Exception {
    when(tileService.getTile(STYLE_ID, 14, 27935, 12960)).thenThrow(new TileUnavailableException());

    mockMvc
        .perform(
            get("/tiles/{style}/{z}/{x}/{y}.pbf", STYLE_ID, 14, 27935, 12960)
                .header("Authorization", AUTHORIZATION)
                .header("X-Client-Channel", "WEB")
                .principal(authentication(Channel.WEB)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.error", is("tile_unavailable")));
  }

  private static SuriMapAuthentication authentication(Channel channel) {
    return new SuriMapAuthentication(
        ACCOUNT_ID,
        accountType(channel),
        organizationType(channel),
        channel,
        policePhoneId(channel),
        List.of(new SimpleGrantedAuthority(Role.MEMBER.name())));
  }

  private static AccountType accountType(Channel channel) {
    return channel == Channel.WEB ? AccountType.COMMAND : AccountType.TEAM;
  }

  private static OrganizationType organizationType(Channel channel) {
    return channel == Channel.WEB
        ? OrganizationType.POLICE_SUBSTATION
        : OrganizationType.MISSING_TEAM;
  }

  private static String policePhoneId(Channel channel) {
    return channel == Channel.APP ? POLICE_PHONE_ID : null;
  }

  private static byte[] repeatedBytes(int value, int size) {
    byte[] bytes = new byte[size];
    Arrays.fill(bytes, (byte) value);
    return bytes;
  }

  private static TileStyleResponse localStyleResponse() {
    return new TileStyleResponse(
        8,
        Map.of(
            "surimap-local",
            Map.of(
                "type",
                "vector",
                "tiles",
                List.of("/tiles/osm-local/{z}/{x}/{y}.pbf"),
                "minzoom",
                OfflinePackageManifestFixtures.MIN_Z,
                "maxzoom",
                OfflinePackageManifestFixtures.MAX_Z)),
        List.of(
            Map.of(
                "id",
                "search-area-fill",
                "type",
                "fill",
                "source",
                "surimap-local",
                "source-layer",
                "landcover")),
        Map.of("attribution", "OpenStreetMap contributors / OpenMapTiles"),
        "/tiles/fonts/{fontstack}/{range}.pbf");
  }
}
