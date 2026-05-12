package com.surimap.account.service;

import com.surimap.account.AccountIdentityCatalog;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.common.auth.guard.ChannelNotAllowedException;
import com.surimap.policephone.InMemoryPolicePhoneFixtureStore;
import com.surimap.policephone.PolicePhoneFixtures;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class AuthSessionService {

  private final InMemoryPolicePhoneFixtureStore fixtureStore;
  private final Map<String, AccountFixture> accountsByCode;
  private final Map<String, AuthSession> sessionsByAccessToken = new ConcurrentHashMap<>();
  private final Map<UUID, String> accessTokensBySessionId = new ConcurrentHashMap<>();

  public AuthSessionService(InMemoryPolicePhoneFixtureStore fixtureStore) {
    this.fixtureStore = fixtureStore;
    this.accountsByCode = seedAccounts();
  }

  public AuthLoginResult login(AuthLoginCommand command) {
    AccountFixture account = accountsByCode.get(command.accountCode());
    if (account == null || !account.password().equals(command.password())) {
      throw new ChannelNotAllowedException();
    }

    String policePhoneId = resolvePolicePhoneId(account, command);
    UUID sessionId = UUID.randomUUID();
    String accessToken = UUID.randomUUID().toString();
    SecurityContextSnapshot context =
        new SecurityContextSnapshot(
            account.accountId().toString(),
            account.accountType(),
            account.organizationType(),
            command.channel(),
            policePhoneId,
            account.authorities());
    AuthSession session = new AuthSession(sessionId, accessToken, context);
    sessionsByAccessToken.put(accessToken, session);
    accessTokensBySessionId.put(sessionId, accessToken);
    return new AuthLoginResult(sessionId, accessToken, context);
  }

  public void logout(String accessToken, String sessionId) {
    AuthSession session = removeSession(accessToken, sessionId);
    if (session == null) {
      return;
    }
    String policePhoneId = session.securityContext().policePhoneId();
    if (policePhoneId != null && session.securityContext().channel() == Channel.APP) {
      fixtureStore.revokeActiveTokensForLogout(
          UUID.fromString(policePhoneId), session.securityContext().accountId());
    }
  }

  public Optional<SuriMapAuthentication> authenticate(String accessToken) {
    if (accessToken == null || accessToken.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(sessionsByAccessToken.get(accessToken))
        .map(AuthSession::securityContext)
        .map(AuthSessionService::toAuthentication);
  }

  private AuthSession removeSession(String accessToken, String sessionId) {
    AuthSession session = null;
    if (accessToken != null && !accessToken.isBlank()) {
      session = sessionsByAccessToken.remove(accessToken);
      if (session != null) {
        accessTokensBySessionId.remove(session.sessionId());
      }
    }

    if (session == null && sessionId != null && !sessionId.isBlank()) {
      try {
        UUID parsedSessionId = UUID.fromString(sessionId);
        String token = accessTokensBySessionId.remove(parsedSessionId);
        if (token != null) {
          session = sessionsByAccessToken.remove(token);
        }
      } catch (IllegalArgumentException ignored) {
        return null;
      }
    }
    return session;
  }

  private static SuriMapAuthentication toAuthentication(SecurityContextSnapshot context) {
    var authorities =
        context.authorities().stream().map(SimpleGrantedAuthority::new).toList();
    return new SuriMapAuthentication(
        context.accountId(),
        context.accountType(),
        context.organizationType(),
        context.channel(),
        context.policePhoneId(),
        authorities);
  }

  private String resolvePolicePhoneId(AccountFixture account, AuthLoginCommand command) {
    if (command.channel() == Channel.WEB) {
      return null;
    }
    if (command.channel() != Channel.APP) {
      throw new ChannelNotAllowedException();
    }
    if (command.policePhoneCode() == null || command.policePhoneCode().isBlank()) {
      return null;
    }
    UUID policePhoneId = account.policePhoneIdsByCode().get(command.policePhoneCode());
    if (policePhoneId == null) {
      throw new ChannelNotAllowedException();
    }
    return policePhoneId.toString();
  }

  private static Map<String, AccountFixture> seedAccounts() {
    return Map.of(
        "acct-precinct-team",
        new AccountFixture(
            AccountIdentityCatalog.PRECINCT_TEAM_ID,
            "fixture",
            AccountType.TEAM,
            PolicePhoneFixtures.ASSIGNED_ORGANIZATION_TYPE,
            List.of(Role.MEMBER.name()),
            Map.of("dev-precinct-phone-01", PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID)),
        "acct-cmd-alpha",
        new AccountFixture(
            AccountIdentityCatalog.ALPHA_COMMANDER_ID,
            "fixture",
            AccountType.COMMAND,
            OrganizationType.MISSING_TEAM,
            List.of(Role.MISSING_TEAM_COMMANDER.name(), Role.FIELD_COMMANDER.name()),
            Map.of()));
  }

  private record AuthSession(
      UUID sessionId, String accessToken, SecurityContextSnapshot securityContext) {}

  private record AccountFixture(
      UUID accountId,
      String password,
      AccountType accountType,
      OrganizationType organizationType,
      List<String> authorities,
      Map<String, UUID> policePhoneIdsByCode) {

    private AccountFixture {
      authorities = List.copyOf(authorities);
      policePhoneIdsByCode = Map.copyOf(policePhoneIdsByCode);
    }
  }
}
