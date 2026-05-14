package com.surimap.account.security;

import com.surimap.account.service.AuthSessionService;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.common.auth.SuriMapAuthentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AuthSessionAuthenticationFilter extends OncePerRequestFilter {

  private static final boolean ACCEPT_MOCK_AUTH_TOKEN = false;
  private static final String MOCK_AUTH_PREFIX = "mock-auth:";
  private static final Map<String, MockAccount> MOCK_ACCOUNTS =
      Map.ofEntries(
          Map.entry(
              "11111111-1111-1111-1111-111111110009",
              new MockAccount(AccountType.TEAM, OrganizationType.SUPPORT_UNIT)),
          Map.entry(
              "11111111-1111-1111-1111-111111110001",
              new MockAccount(AccountType.COMMAND, OrganizationType.POLICE_SUBSTATION)),
          Map.entry(
              "11111111-1111-1111-1111-111111110004",
              new MockAccount(AccountType.COMMAND, OrganizationType.MISSING_TEAM)),
          Map.entry(
              "11111111-1111-1111-1111-111111110006",
              new MockAccount(AccountType.COMMAND, OrganizationType.SUPPORT_UNIT)),
          Map.entry(
              "11111111-1111-1111-1111-111111110003",
              new MockAccount(AccountType.TEAM, OrganizationType.POLICE_SUBSTATION)),
          Map.entry(
              "11111111-1111-1111-1111-111111110008",
              new MockAccount(AccountType.TEAM, OrganizationType.SUPPORT_UNIT)),
          Map.entry(
              "11111111-1111-1111-1111-111111110007",
              new MockAccount(AccountType.PATROL_CAR, OrganizationType.SUPPORT_UNIT)),
          Map.entry(
              "11111111-1111-1111-1111-111111110002",
              new MockAccount(AccountType.PATROL_CAR, OrganizationType.POLICE_SUBSTATION)),
          Map.entry(
              "11111111-1111-1111-1111-111111110005",
              new MockAccount(AccountType.TEAM, OrganizationType.MISSING_TEAM)));

  private final AuthSessionService authSessionService;

  public AuthSessionAuthenticationFilter(AuthSessionService authSessionService) {
    this.authSessionService = authSessionService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String accessToken = accessToken(request.getHeader("Authorization"));
    if (accessToken != null) {
      authenticate(accessToken)
          .ifPresent(authentication -> SecurityContextHolder.getContext().setAuthentication(authentication));
    }
    filterChain.doFilter(request, response);
  }

  private Optional<SuriMapAuthentication> authenticate(String accessToken) {
    if (ACCEPT_MOCK_AUTH_TOKEN && accessToken.startsWith(MOCK_AUTH_PREFIX)) {
      return mockAuthentication(accessToken);
    }

    // TODO: Add a security filter test that proves WEB Bearer sessions can access protected APIs.
    return authSessionService.authenticate(accessToken);
  }

  private static Optional<SuriMapAuthentication> mockAuthentication(String accessToken) {
    String accountId = accessToken.substring(MOCK_AUTH_PREFIX.length()).split(":", 2)[0];
    MockAccount account = MOCK_ACCOUNTS.get(accountId);
    if (account == null) {
      return Optional.empty();
    }

    return Optional.of(
        new SuriMapAuthentication(
            accountId,
            account.accountType(),
            account.organizationType(),
            Channel.WEB,
            null,
            authorities(account.accountType(), account.organizationType())));
  }

  private static List<SimpleGrantedAuthority> authorities(
      AccountType accountType, OrganizationType organizationType) {
    if (accountType == AccountType.COMMAND && organizationType == OrganizationType.MISSING_TEAM) {
      return List.of(
          new SimpleGrantedAuthority(Role.MISSING_TEAM_COMMANDER.name()),
          new SimpleGrantedAuthority(Role.FIELD_COMMANDER.name()));
    }
    if (accountType == AccountType.COMMAND) {
      return List.of(new SimpleGrantedAuthority(Role.FIELD_COMMANDER.name()));
    }
    return List.of(new SimpleGrantedAuthority(Role.MEMBER.name()));
  }

  private static String accessToken(String authorization) {
    if (authorization == null || authorization.isBlank()) {
      return null;
    }
    String value = authorization.trim();
    if (value.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
      return value.substring("Bearer ".length()).trim();
    }
    return null;
  }

  private record MockAccount(AccountType accountType, OrganizationType organizationType) {}
}
