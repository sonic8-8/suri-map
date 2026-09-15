package com.surimap;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.surimap.account.security.KeycloakJwtAuthenticationConverter;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.common.health.HealthController;
import com.surimap.config.SecurityConfig;
import com.surimap.support.auth.WithMockAccount;
import jakarta.servlet.DispatcherType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(controllers = HealthController.class)
@Import({
  SecurityConfig.class,
  KeycloakJwtAuthenticationConverter.class,
  SecurityConfigTest.SecurityTestController.class
})
class SecurityConfigTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private SecurityTestController securityTestController;
  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  @DisplayName("인증 정보가 없어도, 상태 확인 API는 호출할 수 있다")
  void health_endpoint_without_authentication_is_accessible() throws Exception {
    // given: 인증 정보가 없는 요청이다.
    // when: 상태 확인 API를 호출한다.
    mockMvc
        .perform(get("/api/health"))
        // then: 인증 없이도 정상 응답한다.
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("인증 정보 없이 보호 API를 호출하면, 인증 요구 헤더 없이 401로 거부한다")
  void protected_api_without_authentication_is_rejected() throws Exception {
    // given: 인증 정보가 없는 요청이다.
    // when: 보호 API를 호출한다.
    mockMvc
        .perform(get("/api/auth-harness/protected"))
        // then: 인증 실패 응답을 반환하고 인증 요구 헤더는 추가하지 않는다.
        .andExpect(status().isUnauthorized())
        .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
        .andExpect(jsonPath("$.error").value("unauthorized"));
  }

  @Test
  @DisplayName("허용된 웹 출처에서 사전 요청을 보내면, CORS 허용 헤더로 응답한다")
  void configured_web_origin_is_allowed_for_api_cors_preflight() throws Exception {
    // given: 보안 설정에서 허용한 웹 출처다.
    // when: POST 요청에 앞서 CORS 사전 요청을 보낸다.
    mockMvc
        .perform(
            options("/api/incidents")
                .header("Origin", "https://k14c106.p.ssafy.io")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-client-channel"))
        // then: 요청 출처를 허용하는 헤더와 정상 상태 코드를 반환한다.
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "https://k14c106.p.ssafy.io"));
  }

  @Test
  @DisplayName("로그인·로그아웃 경로에 인증 없이 요청하면, 401로 거부한다")
  void login_and_logout_requests_without_authentication_are_rejected() throws Exception {
    // given: 인증 정보가 없는 웹 요청이다.
    // when: 기존 로그인 경로로 요청한다.
    mockMvc
        .perform(
            post("/api/auth/login")
                .header("X-Client-Channel", "WEB")
                .contentType("application/json")
                .content("{}"))
        // then: 인증되지 않은 요청을 거부한다.
        .andExpect(status().isUnauthorized());

    // when: 기존 로그아웃 경로로 요청한다.
    mockMvc
        .perform(
            post("/api/auth/logout")
                .header("X-Client-Channel", "WEB")
                .contentType("application/json")
                .content("{}"))
        // then: 이 경로도 인증되지 않은 요청을 거부한다.
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Basic 인증으로 요청하면, 인증 요구 헤더 없이 401로 거부한다")
  void basic_authentication_is_rejected_without_authentication_challenge() throws Exception {
    // given: 지원하지 않는 Basic 인증 헤더다.
    // when: 보호 API를 호출한다.
    mockMvc
        .perform(
            get("/api/auth-harness/protected")
                .header(HttpHeaders.AUTHORIZATION, "Basic ZGV2OmRldi1wYXNzd29yZA=="))
        // then: 인증 실패 응답을 반환하고 인증 요구 헤더는 추가하지 않는다.
        .andExpect(status().isUnauthorized())
        .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
        .andExpect(jsonPath("$.error").value("unauthorized"));
  }

  @Test
  @DisplayName("앱 계정의 인증 정보가 있으면, 계정·채널·업무폰·권한을 요청에서 확인할 수 있다")
  @WithMockAccount(
      channel = Channel.APP,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      policePhoneId = "dev-precinct-phone-01")
  void app_account_authentication_is_available_to_the_request() throws Exception {
    // given: 업무폰 정보가 포함된 앱 팀원 계정의 인증 상태다.
    // when: 현재 요청의 인증 정보를 조회한다.
    mockMvc
        .perform(get("/api/auth-harness/context"))
        // then: 계정·채널·업무폰·권한 정보가 유지된다.
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value("11111111-1111-1111-1111-111111110003"))
        .andExpect(jsonPath("$.accountType").value("TEAM"))
        .andExpect(jsonPath("$.organizationType").value("MISSING_TEAM"))
        .andExpect(jsonPath("$.channel").value("APP"))
        .andExpect(jsonPath("$.policePhoneId").value("dev-precinct-phone-01"))
        .andExpect(jsonPath("$.authorities[0]").value("MEMBER"));
  }

  @Test
  @DisplayName("웹 채널로 JWT를 전달하면, 계정과 지휘 권한이 인증 정보에 담긴다")
  void jwt_claims_are_converted_to_web_account_authentication() throws Exception {
    // given: JWT 해석 결과에 파출소 지휘 계정 정보가 담겨 있다.
    String accessToken = "header.payload.signature";
    when(jwtDecoder.decode(accessToken))
        .thenReturn(
            Jwt.withTokenValue(accessToken)
                .header("alg", "RS256")
                .claim("accountId", "11111111-1111-1111-1111-111111110001")
                .claim("accountType", "COMMAND")
                .claim("organizationType", "POLICE_SUBSTATION")
                .build());

    // when: 웹 채널과 Bearer 토큰으로 인증 정보를 조회한다.
    mockMvc
        .perform(
            get("/api/auth-harness/context")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Client-Channel", "WEB"))
        // then: 웹 계정과 지휘 권한은 담기고 업무폰 정보는 없다.
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value("11111111-1111-1111-1111-111111110001"))
        .andExpect(jsonPath("$.accountType").value("COMMAND"))
        .andExpect(jsonPath("$.organizationType").value("POLICE_SUBSTATION"))
        .andExpect(jsonPath("$.channel").value("WEB"))
        .andExpect(jsonPath("$.policePhoneId").isEmpty())
        .andExpect(jsonPath("$.authorities[0]").value("FIELD_COMMANDER"));
  }

  @Test
  @DisplayName("인증된 SSE 연결을 종료하면, 같은 요청의 비동기 종료 처리도 허용한다")
  void authenticated_sse_completion_preserves_authentication_during_async_dispatch()
      throws Exception {
    // given: 실제 보안 필터가 웹 계정의 JWT를 해석해 SSE 요청을 인증한다.
    String accessToken = "header.payload.signature";
    when(jwtDecoder.decode(accessToken)).thenReturn(createWebAccountJwt(accessToken));
    var opened =
        mockMvc
            .perform(
                get("/api/auth-harness/events")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header("X-Client-Channel", "WEB"))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted())
            .andReturn();

    // when: SSE를 종료하고 같은 HTTP 요청의 비동기 처리를 다시 진행한다.
    securityTestController.completeEventStream();

    // then: 최초 요청에서 확인한 인증 정보로 종료 처리도 통과한다.
    mockMvc.perform(asyncDispatch(opened)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("인증된 SSE 요청을 끝내도, 같은 세션의 다음 요청에는 토큰이 필요하다")
  void sse_authentication_is_not_reused_for_another_request_in_the_same_session() throws Exception {
    // given: 기존 HTTP 세션이 있는 상태에서 토큰으로 SSE 연결을 인증한다.
    String accessToken = "header.payload.signature";
    when(jwtDecoder.decode(accessToken)).thenReturn(createWebAccountJwt(accessToken));
    var session = new MockHttpSession();
    var opened =
        mockMvc
            .perform(
                get("/api/auth-harness/events")
                    .session(session)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header("X-Client-Channel", "WEB"))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted())
            .andReturn();
    securityTestController.completeEventStream();
    mockMvc.perform(asyncDispatch(opened)).andExpect(status().isOk());

    // when: 같은 세션으로 새 SSE 요청을 보내되 토큰은 생략한다.
    mockMvc
        .perform(get("/api/auth-harness/events").session(session).header("X-Client-Channel", "WEB"))
        // then: 이전 요청의 인증 정보를 재사용하지 않고 거부한다.
        .andExpect(status().isUnauthorized())
        .andExpect(request().asyncNotStarted());
  }

  @Test
  @DisplayName("토큰 검증에 실패하면, SSE 연결을 시작하지 않는다")
  void invalid_jwt_cannot_open_sse_stream() throws Exception {
    // given: 외부 JWT 검증기가 거부하는 토큰이다.
    String accessToken = "header.invalid.signature";
    when(jwtDecoder.decode(accessToken)).thenThrow(new BadJwtException("invalid token"));

    // when: 거부된 토큰으로 SSE 연결을 요청한다.
    mockMvc
        .perform(
            get("/api/auth-harness/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header("X-Client-Channel", "WEB"))
        // then: 인증 실패로 응답하고 비동기 연결은 시작하지 않는다.
        .andExpect(status().isUnauthorized())
        .andExpect(request().asyncNotStarted());
  }

  @ParameterizedTest(name = "채널: {0}")
  @NullAndEmptySource
  @ValueSource(strings = "UNKNOWN")
  @DisplayName("토큰이 유효해도 채널이 없거나 알 수 없는 값이면, SSE 연결을 거부한다")
  void valid_jwt_with_missing_or_unknown_channel_cannot_open_sse_stream(String channel)
      throws Exception {
    // given: 웹 계정 토큰은 유효하지만 채널 헤더가 올바르지 않다.
    String accessToken = "header.payload.signature";
    when(jwtDecoder.decode(accessToken)).thenReturn(createWebAccountJwt(accessToken));
    var connectionRequest =
        get("/api/auth-harness/events").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    if (channel != null) {
      connectionRequest.header("X-Client-Channel", channel);
    }

    // when: 채널 정보가 잘못된 SSE 연결을 요청한다.
    mockMvc
        .perform(connectionRequest)
        // then: 토큰만으로 연결을 허용하지 않는다.
        .andExpect(status().isUnauthorized())
        .andExpect(request().asyncNotStarted());
  }

  @Test
  @DisplayName("인증 정보가 없으면, 비동기 처리라는 이유만으로 보호 API를 허용하지 않는다")
  void async_dispatch_without_authentication_is_rejected() throws Exception {
    // given: 복원할 인증 정보가 없는 비동기 요청이다.
    // when: 보호 API의 비동기 처리를 요청한다.
    mockMvc
        .perform(
            get("/api/auth-harness/protected")
                .with(
                    asyncRequest -> {
                      asyncRequest.setDispatcherType(DispatcherType.ASYNC);
                      return asyncRequest;
                    }))
        // then: 일반 요청과 마찬가지로 인증되지 않은 접근을 거부한다.
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("앱 채널로 JWT와 업무폰 ID를 전달하면, 두 정보가 인증 정보에 담긴다")
  void jwt_claims_and_police_phone_id_are_converted_to_app_authentication() throws Exception {
    // given: JWT 해석 결과에 파출소 팀 계정 정보가 담겨 있다.
    String accessToken = "header.app.signature";
    when(jwtDecoder.decode(accessToken))
        .thenReturn(
            Jwt.withTokenValue(accessToken)
                .header("alg", "RS256")
                .claim("accountId", "11111111-1111-1111-1111-111111110003")
                .claim("accountType", "TEAM")
                .claim("organizationType", "POLICE_SUBSTATION")
                .build());

    // when: 앱 채널·업무폰 ID·Bearer 토큰으로 인증 정보를 조회한다.
    mockMvc
        .perform(
            get("/api/auth-harness/context")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-Client-Channel", "APP")
                .header("X-PolicePhone-Id", "00000000-0000-0000-0000-000000000101"))
        // then: 앱 계정·업무폰 ID·팀원 권한이 담긴다.
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value("11111111-1111-1111-1111-111111110003"))
        .andExpect(jsonPath("$.accountType").value("TEAM"))
        .andExpect(jsonPath("$.organizationType").value("POLICE_SUBSTATION"))
        .andExpect(jsonPath("$.channel").value("APP"))
        .andExpect(jsonPath("$.policePhoneId").value("00000000-0000-0000-0000-000000000101"))
        .andExpect(jsonPath("$.authorities[0]").value("MEMBER"));
  }

  @Test
  @DisplayName("현장 지휘 권한이 있는 파출소 지휘 계정은, 웹에서 지휘 API를 호출할 수 있다")
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      roles = Role.FIELD_COMMANDER)
  void web_command_account_with_field_commander_role_can_access_command_endpoint()
      throws Exception {
    // given: 현장 지휘 권한이 있는 파출소 지휘 계정의 웹 인증 상태다.
    // when: 현장 지휘 API를 호출한다.
    mockMvc
        .perform(get("/api/auth-harness/field-command"))
        // then: 요청을 허용한다.
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("일반 팀원 계정으로 요청하면, 현장 지휘 API 접근을 거부한다")
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.MEMBER)
  void member_account_is_forbidden_from_command_endpoint() throws Exception {
    // given: 일반 팀원 계정의 웹 인증 상태다.
    // when: 현장 지휘 API를 호출한다.
    mockMvc
        .perform(get("/api/auth-harness/field-command"))
        // then: 지휘 권한이 없는 요청을 거부한다.
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("현장 지휘 권한이 있어도 앱 채널에서 요청하면, 웹 지휘 API 접근을 거부한다")
  @WithMockAccount(
      channel = Channel.APP,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.POLICE_SUBSTATION,
      roles = Role.FIELD_COMMANDER)
  void app_field_commander_is_forbidden_from_web_command_endpoint() throws Exception {
    // given: 현장 지휘 권한이 있는 파출소 지휘 계정의 앱 인증 상태다.
    // when: 웹 현장 지휘 API를 호출한다.
    mockMvc
        .perform(get("/api/auth-harness/field-command"))
        // then: 앱 채널의 접근을 거부한다.
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("현장 지휘 권한이 있어도 팀 계정으로 요청하면, 지휘 API 접근을 거부한다")
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.TEAM,
      organizationType = OrganizationType.MISSING_TEAM,
      roles = Role.FIELD_COMMANDER)
  void team_account_with_field_commander_role_is_forbidden_from_command_endpoint()
      throws Exception {
    // given: 현장 지휘 권한은 있지만 지휘 계정이 아닌 팀 계정이다.
    // when: 현장 지휘 API를 호출한다.
    mockMvc
        .perform(get("/api/auth-harness/field-command"))
        // then: 지휘 계정 조건을 충족하지 않는 요청을 거부한다.
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("로컬 개발용 토큰으로 요청해도, 보호 API 인증을 우회할 수 없다")
  void local_development_bearer_token_cannot_bypass_authentication() throws Exception {
    // given: JWT가 아닌 로컬 개발용 토큰이다.
    // when: localhost에서 요청한 것처럼 보호 API를 호출한다.
    mockMvc
        .perform(
            get("/api/auth-harness/protected")
                .header("Authorization", "Bearer dev-local-access-token")
                .header("Host", "localhost:8080")
                .header("X-Client-Channel", "WEB"))
        // then: 로컬 요청이어도 인증되지 않은 토큰을 거부한다.
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("현장 지휘 권한이 있어도 지원 부대 계정이면, 파출소 지휘 API 접근을 거부한다")
  @WithMockAccount(
      channel = Channel.WEB,
      accountType = AccountType.COMMAND,
      organizationType = OrganizationType.SUPPORT_UNIT,
      roles = Role.FIELD_COMMANDER)
  void support_unit_field_commander_is_forbidden_from_command_endpoint() throws Exception {
    // given: 현장 지휘 권한이 있는 지원 부대 지휘 계정의 웹 인증 상태다.
    // when: 파출소 현장 지휘 API를 호출한다.
    mockMvc
        .perform(get("/api/auth-harness/field-command"))
        // then: 조직 조건을 충족하지 않는 요청을 거부한다.
        .andExpect(status().isForbidden());
  }

  private static Jwt createWebAccountJwt(String accessToken) {
    return Jwt.withTokenValue(accessToken)
        .header("alg", "RS256")
        .claim("accountId", "11111111-1111-1111-1111-111111110001")
        .claim("accountType", "COMMAND")
        .claim("organizationType", "POLICE_SUBSTATION")
        .build();
  }

  @RestController
  public static class SecurityTestController {

    private SseEmitter eventStream;

    @GetMapping("/api/auth-harness/events")
    SseEmitter openEventStream() {
      eventStream = new SseEmitter(0L);
      return eventStream;
    }

    void completeEventStream() {
      eventStream.complete();
    }

    // Test-only fail-closed baseline until S1-1 incident_assignment access is available.
    @GetMapping("/api/auth-harness/protected")
    String protectedApi() {
      return "ok";
    }

    @GetMapping("/api/auth-harness/context")
    AuthenticationResponse context(Authentication authentication) {
      var auth = (SuriMapAuthentication) authentication;
      return new AuthenticationResponse(
          auth.getAccountId(),
          auth.getAccountType(),
          auth.getOrganizationType(),
          auth.getChannel(),
          auth.getPolicePhoneId(),
          auth.getAuthorities().stream().map(Object::toString).toList());
    }

    @GetMapping("/api/auth-harness/field-command")
    @PreAuthorize(
        "authentication instanceof T(com.surimap.common.auth.SuriMapAuthentication) "
            + "and authentication.channel == T(com.surimap.common.auth.Channel).WEB "
            + "and authentication.accountType == T(com.surimap.common.auth.AccountType).COMMAND "
            + "and authentication.organizationType == "
            + "T(com.surimap.common.auth.OrganizationType).POLICE_SUBSTATION "
            + "and hasAuthority('FIELD_COMMANDER')")
    AuthenticationResponse fieldCommand(Authentication authentication) {
      return context(authentication);
    }
  }

  record AuthenticationResponse(
      String accountId,
      AccountType accountType,
      OrganizationType organizationType,
      Channel channel,
      String policePhoneId,
      List<String> authorities) {}
}
