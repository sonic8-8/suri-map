package com.surimap.path;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SearchPathSegmentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SearchPathExceptionHandler.class)
class SearchPathSegmentControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private SearchPathService searchPathService;

  @Test
  @DisplayName("PATCH /api/search-path-segments/{id} returns manual correction response")
  void correctionContract() throws Exception {
    UUID accountId = UUID.fromString("30000000-0000-0000-0000-000000000001");
    when(searchPathService.correctSegment(eq("seg-001"), eq(MovementType.FOOT), eq(accountId)))
        .thenReturn(
            new SearchPathSegment(
                "seg-001",
                2L,
                MovementType.FOOT,
                MovementTypeSource.MANUAL,
                0,
                3,
                "gps-precinct-001",
                "gps-precinct-004",
                accountId,
                OffsetDateTime.parse("2026-04-28T09:12:00+09:00")));

    mockMvc
        .perform(
            patch("/api/search-path-segments/{id}", "seg-001")
                .header("X-Account-Id", accountId.toString())
                .contentType("application/json")
                .content("{\"movementType\":\"FOOT\",\"reason\":\"manual correction\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is("seg-001")))
        .andExpect(jsonPath("$.movementType", is("FOOT")))
        .andExpect(jsonPath("$.movementTypeSource", is("MANUAL")))
        .andExpect(jsonPath("$.correctedByAccountId", is(accountId.toString())))
        .andExpect(jsonPath("$.version", is(2)));
  }
}
