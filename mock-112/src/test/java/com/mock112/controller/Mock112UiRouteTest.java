package com.mock112.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

import com.mock112.domain.MockIncident;
import com.mock112.seed.SeedDataLoader;
import com.mock112.service.MockIncidentRegistrationService;
import com.mock112.store.MockIncidentStore;
import com.mock112.webhook.SuriMapWebhookDispatcher;
import com.mock112.webhook.WebhookOutboxStore;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        properties = {
            "spring.security.oauth2.client.registration.keycloak.client-id=suri-map-mock112",
            "spring.security.oauth2.client.registration.keycloak.client-authentication-method=none",
            "spring.security.oauth2.client.registration.keycloak.authorization-grant-type=authorization_code",
            "spring.security.oauth2.client.registration.keycloak.redirect-uri={baseUrl}/mock-112/login/oauth2/code/{registrationId}",
            "spring.security.oauth2.client.registration.keycloak.scope=openid,profile",
            "spring.security.oauth2.client.provider.keycloak.authorization-uri=http://keycloak/keycloak/realms/suri-map/protocol/openid-connect/auth",
            "spring.security.oauth2.client.provider.keycloak.token-uri=http://keycloak/keycloak/realms/suri-map/protocol/openid-connect/token",
            "spring.security.oauth2.client.provider.keycloak.jwk-set-uri=http://keycloak/keycloak/realms/suri-map/protocol/openid-connect/certs",
            "spring.security.oauth2.client.provider.keycloak.user-info-uri=http://keycloak/keycloak/realms/suri-map/protocol/openid-connect/userinfo",
            "spring.security.oauth2.client.provider.keycloak.user-name-attribute=preferred_username",
            "mock112.internal-api.token=test-internal-token"
        },
        controllers = {
            Mock112UiController.class,
            MockScenarioController.class,
            MockIncidentController.class,
            AssignableOrganizationController.class
        })
@Import({
        com.mock112.config.SecurityConfig.class,
        com.mock112.assignment.AssignableOrganizationCatalog.class
})
class Mock112UiRouteTest {

    private final MockMvc mockMvc;

    @MockitoBean
    private MockIncidentStore store;

    @MockitoBean
    private SeedDataLoader seedDataLoader;

    @MockitoBean
    private MockIncidentRegistrationService registrationService;

    @MockitoBean
    private SuriMapWebhookDispatcher webhookDispatcher;

    @MockitoBean
    private WebhookOutboxStore webhookOutboxStore;

    @Autowired
    Mock112UiRouteTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    @DisplayName("미인증 /mock-112/ 접근은 Keycloak 로그인 시작 경로로 보낸다")
    void mock112RootRequiresKeycloakLogin() throws Exception {
        mockMvc.perform(get("/mock-112/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/mock-112/oauth2/authorization/keycloak"));
    }

    @Test
    @DisplayName("인증된 /mock-112/ 접근은 시뮬레이터 UI index를 반환한다")
    void authenticatedMock112RootServesUiIndex() throws Exception {
        mockMvc.perform(get("/mock-112/").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("mock 112 관제 시스템")))
                .andExpect(content().string(containsString("inputInitialAssignmentGroup")))
                .andExpect(content().string(not(containsString("inputSourceId"))))
                .andExpect(content().string(containsString("app.js")));
    }

    @Test
    @DisplayName("/mock-112 정규화 전 경로도 UI index를 반환한다")
    void mock112RootWithoutSlashServesUiIndex() throws Exception {
        mockMvc.perform(get("/mock-112").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("mock 112 관제 시스템")));
    }

    @Test
    @DisplayName("/mock-112/{asset}는 static asset을 반환한다")
    void mock112PrefixServesStaticAssets() throws Exception {
        mockMvc.perform(get("/mock-112/app.js").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("loadIncidents")))
                .andExpect(content().string(containsString("loadAssignableOrganizations")))
                .andExpect(content().string(containsString("assignGroup")))
                .andExpect(content().string(not(containsString("inc.status === 'IMPORTED' ? 'disabled'"))));

        mockMvc.perform(get("/mock-112/style.css").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("mock 112 Admin")));
    }

    @Test
    @DisplayName("인증된 사용자는 인증 서버 fixture 기준 배정 가능 조직을 조회할 수 있다")
    void authenticatedUserCanReadAssignableOrganizations() throws Exception {
        mockMvc.perform(get("/mock-112/assignable-organizations").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].organizationCode")
                        .value("GWANGJU_GWANGSAN_SUWAN_PATROL_DIVISION"))
                .andExpect(jsonPath("$[0].accounts[0].accountCode").value("acct-precinct-cmd"));
    }

    @Test
    @DisplayName("mock-112 health는 로그인 없이 확인할 수 있다")
    void mock112ApiRoutesStayMappedToControllers() throws Exception {
        when(webhookOutboxStore.countByStatus()).thenReturn(Map.of("PENDING", 0, "SENT", 0, "FAILED", 0));

        mockMvc.perform(get("/mock-112/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.webhookOutbox.PENDING").value(0));
    }

    @Test
    @DisplayName("미인증 mock-112 사건 API 접근은 Keycloak 로그인으로 보낸다")
    void mock112IncidentApiRequiresAuthenticationWithoutInternalToken() throws Exception {
        mockMvc.perform(get("/mock-112/incidents").queryParam("status", "READY"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/mock-112/oauth2/authorization/keycloak"));
    }

    @Test
    @DisplayName("backend 내부 토큰이 있으면 mock-112 사건 API를 조회할 수 있다")
    void internalTokenAllowsMock112IncidentApi() throws Exception {
        when(store.findByStatus("READY")).thenReturn(List.of());

        mockMvc.perform(get("/mock-112/incidents")
                        .queryParam("status", "READY")
                        .header("X-Internal-Service-Token", "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("backend 내부 토큰이 있으면 mock-112 시나리오 seed API를 실행할 수 있다")
    void internalTokenAllowsMock112ScenarioSeedApi() throws Exception {
        MockIncident incident = new MockIncident();
        incident.setSourceIncidentId("00000000-0000-0000-0000-000000000001");
        incident.setStatus("READY");
        when(seedDataLoader.loadPrecinctFirstScenario(store)).thenReturn(incident);

        mockMvc.perform(post("/mock-112/scenarios/precinct-first")
                        .header("X-Internal-Service-Token", "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceIncidentId").value("00000000-0000-0000-0000-000000000001"));
    }

    @Test
    @DisplayName("시나리오 seed 중복은 500이 아니라 bad_request로 응답한다")
    void duplicateScenarioSeedReturnsBadRequest() throws Exception {
        when(seedDataLoader.loadPrecinctFirstScenario(store))
                .thenThrow(new IllegalArgumentException("sourceIncidentId already exists"));

        mockMvc.perform(post("/mock-112/scenarios/precinct-first").with(oauth2Login()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("already_loaded"));
    }
}
