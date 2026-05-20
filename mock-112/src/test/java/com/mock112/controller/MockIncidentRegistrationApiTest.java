package com.mock112.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock112.domain.MockAssignment;
import com.mock112.domain.MockIncident;
import com.mock112.store.MockIncidentStore;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mock112-registration-api-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "suri-map.webhook.enabled=false"
})
@AutoConfigureMockMvc
class MockIncidentRegistrationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockIncidentStore store;

    @AfterEach
    void tearDown() {
        store.reset();
    }

    @Test
    @DisplayName("사건 등록 API는 sourceIncidentId를 서버에서 생성하고 조직 계정을 일괄 배정한다")
    void createIncidentGeneratesSourceIncidentIdAndOrganizationAssignments() throws Exception {
        MvcResult result = mockMvc.perform(post("/mock-112/incidents")
                        .with(oauth2Login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceIncidentId": "operator-typed-id",
                                  "title": "수완동 산책로 실종 신고",
                                  "openedAt": "2026-05-20T09:10:11.123+09:00",
                                  "initialOrganizationCode": "GWANGJU_GWANGSAN_SUWAN_PATROL_DIVISION",
                                  "missingPerson": {
                                    "displayName": "김수리",
                                    "photoObjectKey": "mock-112/missing-person/manual-001.jpg",
                                    "appearanceText": "남색 점퍼",
                                    "lastSeenLocationText": "수완호수공원 산책로",
                                    "lastSeenAt": "2026-05-20T08:40:00.000+09:00"
                                  },
                                  "assignments": [
                                    {
                                      "externalAssignmentKey": "client-generated",
                                      "accountCode": "acct-support-cmd",
                                      "incidentRole": "FIELD_COMMANDER",
                                      "assignedAt": "2026-05-20T09:10:11.123+09:00"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceIncidentId", matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
                .andExpect(jsonPath("$.caseNumber", matchesPattern("112-20260520-[0-9A-F]{8}")))
                .andExpect(jsonPath("$.assignmentCount").value(3))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String sourceIncidentId = body.path("sourceIncidentId").asText();

        assertThat(sourceIncidentId).isNotEqualTo("operator-typed-id");
        MockIncident incident = store.findById(sourceIncidentId).orElseThrow();
        assertThat(incident.getCaseNumber()).startsWith("112-20260520-");
        assertThat(incident.getOpenedAt().getNano()).isEqualTo(123_000_000);
        assertThat(incident.getMissingPerson().getPhotoObjectKey())
                .isEqualTo("mock-112/missing-person/manual-001.jpg");
        assertThat(incident.getMissingPerson().getLastSeenAt())
                .isEqualTo(OffsetDateTime.parse("2026-05-20T08:40:00+09:00"));
        assertThat(incident.getAssignments())
                .extracting(MockAssignment::getAccountCode)
                .containsExactlyInAnyOrder(
                        "acct-precinct-cmd",
                        "acct-precinct-car",
                        "acct-precinct-team");
    }

    @Test
    @DisplayName("조직 배정 API는 소속 계정을 추가하고 중복 호출은 멱등 처리한다")
    void organizationAssignmentAddsAccountsIdempotently() throws Exception {
        String sourceIncidentId = createIncident();

        mockMvc.perform(post("/mock-112/incidents/{sourceIncidentId}/assignment-organizations", sourceIncidentId)
                        .with(oauth2Login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "GWANGJU_POLICE_WOMEN_JUVENILE_MISSING_TEAM",
                                  "assignedAt": "2026-05-20T10:00:00.500+09:00"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.addedCount").value(2));

        mockMvc.perform(post("/mock-112/incidents/{sourceIncidentId}/assignment-organizations", sourceIncidentId)
                        .with(oauth2Login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationCode": "GWANGJU_POLICE_WOMEN_JUVENILE_MISSING_TEAM",
                                  "assignedAt": "2026-05-20T10:00:00.500+09:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addedCount").value(0));

        List<String> accountCodes = store.findById(sourceIncidentId).orElseThrow()
                .getAssignments()
                .stream()
                .map(MockAssignment::getAccountCode)
                .toList();
        assertThat(accountCodes)
                .containsExactlyInAnyOrder(
                        "acct-precinct-cmd",
                        "acct-precinct-car",
                        "acct-precinct-team",
                        "acct-cmd-alpha",
                        "acct-team-alpha");
    }

    @Test
    @DisplayName("READY 사건은 원천 제목과 실종자 정보를 정정할 수 있고 webhook 이벤트를 만들지 않는다")
    void updateReadyIncidentSourceFacts() throws Exception {
        String sourceIncidentId = createIncident();

        mockMvc.perform(put("/mock-112/incidents/{sourceIncidentId}", sourceIncidentId)
                        .with(oauth2Login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "수완동 호수공원 실종 정정",
                                  "missingPerson": {
                                    "displayName": "김수리",
                                    "photoObjectKey": "mock-112/missing-person/corrected-001.jpg",
                                    "appearanceText": "초록색 점퍼, 검은 배낭",
                                    "lastSeenLocationText": "수완호수공원 북문",
                                    "lastSeenAt": "2026-05-20T08:30:00.000+09:00"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceIncidentId").value(sourceIncidentId))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.webhookDelivery.status").value("NO_EVENTS"));

        MockIncident incident = store.findById(sourceIncidentId).orElseThrow();
        assertThat(incident.getTitle()).isEqualTo("수완동 호수공원 실종 정정");
        assertThat(incident.getMissingPerson().getPhotoObjectKey())
                .isEqualTo("mock-112/missing-person/corrected-001.jpg");
        assertThat(incident.getMissingPerson().getLastSeenAt())
                .isEqualTo(OffsetDateTime.parse("2026-05-20T08:30:00+09:00"));
    }

    @Test
    @DisplayName("IMPORTED 사건은 Suri-Map 반영 이후 원천 정보 정정을 거부한다")
    void importedIncidentSourceFactsAreReadOnly() throws Exception {
        String sourceIncidentId = createIncident();
        store.markImported(sourceIncidentId);

        mockMvc.perform(put("/mock-112/incidents/{sourceIncidentId}", sourceIncidentId)
                        .with(oauth2Login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "이미 반영된 사건 정정",
                                  "missingPerson": {
                                    "displayName": "김수리"
                                  }
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("incident_imported_read_only"));

        assertThat(store.findById(sourceIncidentId).orElseThrow().getTitle())
                .isEqualTo("수완동 산책로 실종 신고");
    }

    private String createIncident() throws Exception {
        MvcResult result = mockMvc.perform(post("/mock-112/incidents")
                        .with(oauth2Login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "수완동 산책로 실종 신고",
                                  "openedAt": "2026-05-20T09:10:11.123+09:00",
                                  "initialOrganizationCode": "GWANGJU_GWANGSAN_SUWAN_PATROL_DIVISION"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("sourceIncidentId")
                .asText();
    }
}
