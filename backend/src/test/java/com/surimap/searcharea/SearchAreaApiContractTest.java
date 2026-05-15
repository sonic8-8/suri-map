package com.surimap.searcharea;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.api.controller.searcharea.SearchAreaController;
import com.surimap.api.service.searcharea.SearchAreaApiService;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.config.GeometryConfig;
import com.surimap.config.GuardConfig;
import com.surimap.support.auth.GuardPortTestStubs;
import com.surimap.support.auth.WithMockAccount;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SearchAreaController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
  GuardConfig.class,
  GuardPortTestStubs.class,
  GeometryConfig.class,
  SearchAreaApiService.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@DisplayName("P2-A SearchArea public API contract")
class SearchAreaApiContractTest {

  private static final UUID INCIDENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID OP_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
  private static final UUID ASSIGNEE_ID = UUID.fromString("11111111-1111-1111-1111-111111110010");

  @Autowired private MockMvc mockMvc;

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      roles = {Role.MISSING_TEAM_COMMANDER})
  @DisplayName("WEB can create and read the active OVERALL search area through canonical URL")
  void createsAndReadsActiveOverallSearchArea() throws Exception {
    mockMvc
        .perform(
            post("/api/search-areas")
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .header("Idempotency-Key", "idem-area-overall-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(overallCreateBody()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.incidentId", is(INCIDENT_ID.toString())))
        .andExpect(jsonPath("$.areaLevel", is("OVERALL")))
        .andExpect(jsonPath("$.status", is("ACTIVE")))
        .andExpect(jsonPath("$.historyCount", is(1)))
        .andExpect(jsonPath("$.version", is(1)))
        .andExpect(jsonPath("$.geometry.type", is("Polygon")));

    mockMvc
        .perform(
            get("/api/search-areas")
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .param("incidentId", INCIDENT_ID.toString())
                .param("areaLevel", "OVERALL")
                .param("status", "ACTIVE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.incidentId", is(INCIDENT_ID.toString())))
        .andExpect(jsonPath("$.areaLevel", is("OVERALL")))
        .andExpect(jsonPath("$.status", is("ACTIVE")))
        .andExpect(jsonPath("$.version", is(1)));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      roles = {Role.MISSING_TEAM_COMMANDER})
  @DisplayName("UNIT area write without active OVERALL returns overall_search_area_required")
  void normalAreaRequiresActiveOverall() throws Exception {
    mockMvc
        .perform(
            post("/api/search-areas")
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .header("Idempotency-Key", "idem-area-unit-no-overall")
                .contentType(MediaType.APPLICATION_JSON)
                .content(unitCreateBody()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("overall_search_area_required")));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      roles = {Role.MISSING_TEAM_COMMANDER})
  @DisplayName("active OVERALL read without area returns overall_search_area_required")
  void activeOverallReadRequiresExistingOverall() throws Exception {
    mockMvc
        .perform(
            get("/api/search-areas")
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .param("incidentId", INCIDENT_ID.toString())
                .param("areaLevel", "OVERALL")
                .param("status", "ACTIVE"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("overall_search_area_required")));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      roles = {Role.MISSING_TEAM_COMMANDER})
  @DisplayName("write without Idempotency-Key returns write_conflict")
  void writeRequiresIdempotencyKey() throws Exception {
    mockMvc
        .perform(
            post("/api/search-areas")
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .contentType(MediaType.APPLICATION_JSON)
                .content(overallCreateBody()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("write_conflict")));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      roles = {Role.MISSING_TEAM_COMMANDER})
  @DisplayName("status PATCH requires opId even for OVERALL areas")
  void overallStatusPatchRequiresOperationalPeriod() throws Exception {
    String areaId =
        mockMvc
            .perform(
                post("/api/search-areas")
                    .header("Authorization", "Bearer commander")
                    .header("X-Client-Channel", "WEB")
                    .header("Idempotency-Key", "idem-area-overall-status-conflict")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(overallCreateBody()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString()
            .replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

    mockMvc
        .perform(
            patch("/api/search-areas/{searchAreaId}", areaId)
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .header("Idempotency-Key", "idem-area-overall-status-conflict-patch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "nextStatus": "COMPLETED",
                      "expectedVersion": 1,
                      "clientTs": "2026-05-11T09:32:00+09:00"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("write_conflict")));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      roles = {Role.MISSING_TEAM_COMMANDER})
  @DisplayName("non-Polygon geometry returns invalid_geometry")
  void rejectsNonPolygonGeometry() throws Exception {
    mockMvc
        .perform(
            post("/api/search-areas")
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .header("Idempotency-Key", "idem-area-invalid-geometry")
                .contentType(MediaType.APPLICATION_JSON)
                .content(overallCreateBody().replace("\"type\": \"Polygon\"", "\"type\": \"Point\"")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("invalid_geometry")));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.APP,
      roles = {Role.MISSING_TEAM_COMMANDER})
  @DisplayName("APP channel cannot invoke WEB search area commands")
  void appChannelCannotWriteSearchArea() throws Exception {
    mockMvc
        .perform(
            post("/api/search-areas")
                .header("Authorization", "Bearer field")
                .header("X-Client-Channel", "APP")
                .header("Idempotency-Key", "idem-area-app-rejected")
                .contentType(MediaType.APPLICATION_JSON)
                .content(overallCreateBody()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", is("channel_not_allowed")));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      roles = {Role.MISSING_TEAM_COMMANDER})
  @DisplayName("PATCH /api/search-areas/{searchAreaId} changes status without /state alias")
  void patchesStatusOnCanonicalSearchAreaPath() throws Exception {
    createOverall();
    String areaId =
        createUnitArea("idem-area-unit-001")
            .andReturn()
            .getResponse()
            .getContentAsString()
            .replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

    mockMvc
        .perform(
            patch("/api/search-areas/{searchAreaId}", areaId)
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .header("Idempotency-Key", "idem-area-complete-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "opId": "70000000-0000-0000-0000-000000000001",
                      "nextStatus": "COMPLETED",
                      "memo": "구역 수색 완료",
                      "expectedVersion": 1,
                      "clientTs": "2026-05-11T09:30:00+09:00"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(areaId)))
        .andExpect(jsonPath("$.status", is("COMPLETED")))
        .andExpect(jsonPath("$.historyCount", is(2)))
        .andExpect(jsonPath("$.version", is(2)));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      roles = {Role.MISSING_TEAM_COMMANDER})
  @DisplayName("status PATCH requires matching operational period")
  void statusPatchRequiresMatchingOperationalPeriod() throws Exception {
    createOverall();
    String areaId =
        createUnitArea("idem-area-unit-status-conflict")
            .andReturn()
            .getResponse()
            .getContentAsString()
            .replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

    mockMvc
        .perform(
            patch("/api/search-areas/{searchAreaId}", areaId)
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .header("Idempotency-Key", "idem-area-status-conflict")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "nextStatus": "COMPLETED",
                      "expectedVersion": 1,
                      "clientTs": "2026-05-11T09:31:00+09:00"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error", is("write_conflict")));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      roles = {Role.MISSING_TEAM_COMMANDER})
  @DisplayName("split cancels parent and creates ACTIVE children")
  void splitsSearchAreaOnCanonicalSplitPath() throws Exception {
    createOverall();
    String areaId =
        createUnitArea("idem-area-unit-for-split")
            .andReturn()
            .getResponse()
            .getContentAsString()
            .replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

    mockMvc
        .perform(
            post("/api/search-areas/{searchAreaId}/split", areaId)
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .header("Idempotency-Key", "idem-area-split-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(splitBody()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.parentAreaId", is(areaId)))
        .andExpect(jsonPath("$.parent.status", is("CANCELLED")))
        .andExpect(jsonPath("$.createdAreaIds", hasSize(2)))
        .andExpect(jsonPath("$.children", hasSize(2)))
        .andExpect(jsonPath("$.children[0].parentAreaId", is(areaId)))
        .andExpect(jsonPath("$.children[0].status", is("ACTIVE")));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      roles = {Role.MISSING_TEAM_COMMANDER})
  @DisplayName("explicit CANCELLED status query returns cancelled search areas")
  void listsCancelledSearchAreasWhenStatusIsRequested() throws Exception {
    createOverall();
    String areaId =
        createUnitArea("idem-area-unit-cancelled-query")
            .andReturn()
            .getResponse()
            .getContentAsString()
            .replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

    mockMvc
        .perform(
            post("/api/search-areas/{searchAreaId}/split", areaId)
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .header("Idempotency-Key", "idem-area-cancelled-query-split")
                .contentType(MediaType.APPLICATION_JSON)
                .content(splitBody()))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/search-areas")
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .param("incidentId", INCIDENT_ID.toString())
                .param("status", "CANCELLED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.incidentId", is(INCIDENT_ID.toString())))
        .andExpect(jsonPath("$.areas", hasSize(1)))
        .andExpect(jsonPath("$.areas[0].id", is(areaId)))
        .andExpect(jsonPath("$.areas[0].status", is("CANCELLED")));
  }

  @Test
  @WithMockAccount(
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.MISSING_TEAM,
      channel = Channel.WEB,
      roles = {Role.MISSING_TEAM_COMMANDER})
  @DisplayName("assignment command returns assignment ids and bumps area version")
  void assignsSearchAreaToAccounts() throws Exception {
    createOverall();
    String areaId =
        createUnitArea("idem-area-unit-for-assignment")
            .andReturn()
            .getResponse()
            .getContentAsString()
            .replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

    mockMvc
        .perform(
            post("/api/search-areas/{searchAreaId}/assignments", areaId)
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .header("Idempotency-Key", "idem-area-assignment-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "incidentId": "10000000-0000-0000-0000-000000000001",
                      "opId": "70000000-0000-0000-0000-000000000001",
                      "assigneeAccountIds": ["11111111-1111-1111-1111-111111110010"],
                      "memo": "1팀 배정",
                      "clientTs": "2026-05-11T09:40:00+09:00"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.searchAreaId", is(areaId)))
        .andExpect(jsonPath("$.opId", is(OP_ID.toString())))
        .andExpect(jsonPath("$.assignmentIds", hasSize(1)))
        .andExpect(jsonPath("$.version", is(2)));
  }

  private void createOverall() throws Exception {
    mockMvc
        .perform(
            post("/api/search-areas")
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .header("Idempotency-Key", "idem-area-overall-seed")
                .contentType(MediaType.APPLICATION_JSON)
                .content(overallCreateBody()))
        .andExpect(status().isCreated());
  }

  private org.springframework.test.web.servlet.ResultActions createUnitArea(String idempotencyKey)
      throws Exception {
    return mockMvc
        .perform(
            post("/api/search-areas")
                .header("Authorization", "Bearer commander")
                .header("X-Client-Channel", "WEB")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(unitCreateBody()))
        .andExpect(status().isCreated());
  }

  private static String overallCreateBody() {
    return """
        {
          "incidentId": "10000000-0000-0000-0000-000000000001",
          "areaLevel": "OVERALL",
          "geometry": {
            "type": "Polygon",
            "coordinates": [[
              [126.904000, 35.158000],
              [126.930000, 35.158000],
              [126.930000, 35.180000],
              [126.904000, 35.180000],
              [126.904000, 35.158000]
            ]]
          },
          "clientTs": "2026-05-11T09:00:00+09:00"
        }
        """;
  }

  private static String unitCreateBody() {
    return """
        {
          "incidentId": "10000000-0000-0000-0000-000000000001",
          "opId": "70000000-0000-0000-0000-000000000001",
          "areaLevel": "UNIT",
          "geometry": {
            "type": "Polygon",
            "coordinates": [[
              [126.9045000, 35.158000],
              [126.915000, 35.158000],
              [126.915000, 35.166000],
              [126.9045000, 35.166000],
              [126.9045000, 35.158000]
            ]]
          },
          "memo": "1구역",
          "clientTs": "2026-05-11T09:10:00+09:00"
        }
        """;
  }

  private static String splitBody() {
    return """
        {
          "opId": "70000000-0000-0000-0000-000000000001",
          "expectedVersion": 1,
          "children": [
            {
              "type": "Polygon",
              "coordinates": [[
                [126.9045000, 35.158000],
                [126.910000, 35.158000],
                [126.910000, 35.162000],
                [126.9045000, 35.162000],
                [126.9045000, 35.158000]
              ]]
            },
            {
              "type": "Polygon",
              "coordinates": [[
                [126.910000, 35.162000],
                [126.915000, 35.162000],
                [126.915000, 35.166000],
                [126.910000, 35.166000],
                [126.910000, 35.162000]
              ]]
            }
          ],
          "memo": "2개 구역으로 분할",
          "clientTs": "2026-05-11T09:35:00+09:00"
        }
        """;
  }
}
