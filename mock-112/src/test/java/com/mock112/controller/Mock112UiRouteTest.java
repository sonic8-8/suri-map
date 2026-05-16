package com.mock112.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mock112.seed.SeedDataLoader;
import com.mock112.store.MockIncidentStore;
import com.mock112.webhook.SuriMapWebhookDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = {
            Mock112UiController.class,
            MockScenarioController.class
        })
class Mock112UiRouteTest {

    private final MockMvc mockMvc;

    @MockitoBean
    private MockIncidentStore store;

    @MockitoBean
    private SeedDataLoader seedDataLoader;

    @MockitoBean
    private SuriMapWebhookDispatcher webhookDispatcher;

    @Autowired
    Mock112UiRouteTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    @DisplayName("/mock-112/는 시뮬레이터 UI index를 반환한다")
    void mock112RootServesUiIndex() throws Exception {
        mockMvc.perform(get("/mock-112/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("mock 112 관제 시스템")))
                .andExpect(content().string(containsString("app.js")));
    }

    @Test
    @DisplayName("/mock-112 정규화 전 경로도 UI index를 반환한다")
    void mock112RootWithoutSlashServesUiIndex() throws Exception {
        mockMvc.perform(get("/mock-112"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("mock 112 관제 시스템")));
    }

    @Test
    @DisplayName("/mock-112/{asset}는 static asset을 반환한다")
    void mock112PrefixServesStaticAssets() throws Exception {
        mockMvc.perform(get("/mock-112/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("loadIncidents")));

        mockMvc.perform(get("/mock-112/style.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("mock 112 Admin")));
    }

    @Test
    @DisplayName("기존 mock-112 API 라우팅은 static 매핑보다 우선한다")
    void mock112ApiRoutesStayMappedToControllers() throws Exception {
        mockMvc.perform(get("/mock-112/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
