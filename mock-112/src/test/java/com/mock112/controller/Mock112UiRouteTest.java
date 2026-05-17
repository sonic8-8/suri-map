package com.mock112.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mock112.seed.SeedDataLoader;
import com.mock112.store.MockIncidentStore;
import com.mock112.webhook.SuriMapWebhookDispatcher;
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
            "spring.security.oauth2.client.provider.keycloak.user-name-attribute=preferred_username"
        },
        controllers = {
            Mock112UiController.class,
            MockScenarioController.class
        })
@Import(com.mock112.config.SecurityConfig.class)
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
                .andExpect(content().string(containsString("loadIncidents")));

        mockMvc.perform(get("/mock-112/style.css").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("mock 112 Admin")));
    }

    @Test
    @DisplayName("mock-112 health는 로그인 없이 확인할 수 있다")
    void mock112ApiRoutesStayMappedToControllers() throws Exception {
        mockMvc.perform(get("/mock-112/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
