package com.surimap.account.service;

import com.surimap.account.repository.AccountLoginMapper;
import com.surimap.account.repository.AccountLoginRow;
import com.surimap.account.repository.PolicePhoneLoginRow;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.common.auth.guard.ChannelNotAllowedException;
import com.surimap.policephone.InMemoryPolicePhoneFixtureStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class AuthSessionService {

  private final InMemoryPolicePhoneFixtureStore fixtureStore;
  private final AccountLoginMapper accountLoginMapper;
  private final Map<String, AuthSession> sessionsByAccessToken = new ConcurrentHashMap<>();
  private final Map<UUID, String> accessTokensBySessionId = new ConcurrentHashMap<>();

  public AuthSessionService(
      InMemoryPolicePhoneFixtureStore fixtureStore, AccountLoginMapper accountLoginMapper) {
    this.fixtureStore = fixtureStore;
    this.accountLoginMapper = accountLoginMapper;
  }

  public AuthLoginResult login(AuthLoginCommand command) {
    AccountLoginRow account =
        accountLoginMapper.findActiveAccountByLoginId(command.accountCode()).orElse(null);
    if (account == null || !matchesPassword(command.password(), account.passwordHash())) {
      throw new ChannelNotAllowedException();
    }

    String policePhoneId = resolvePolicePhoneId(account, command);
    UUID sessionId = UUID.randomUUID();
    String accessToken = UUID.randomUUID().toString();
    SecurityContextSnapshot context =
        new SecurityContextSnapshot(
            account.id(),
            account.accountType(),
            account.organizationType(),
            command.channel(),
            policePhoneId,
            authorities(account));
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

  private String resolvePolicePhoneId(AccountLoginRow account, AuthLoginCommand command) {
    if (command.channel() == Channel.WEB) {
      return null;
    }
    if (command.channel() != Channel.APP) {
      throw new ChannelNotAllowedException();
    }
    if (command.policePhoneCode() == null || command.policePhoneCode().isBlank()) {
      return null;
    }
    return accountLoginMapper
        .findActivePolicePhoneByCodeAndAccountId(command.policePhoneCode(), account.id())
        .map(PolicePhoneLoginRow::id)
        .orElseThrow(ChannelNotAllowedException::new);
  }

  private static boolean matchesPassword(String rawPassword, String passwordHash) {
    if (passwordHash == null || !passwordHash.startsWith("{noop}")) {
      return false;
    }
    return passwordHash.substring("{noop}".length()).equals(rawPassword);
  }

  private static List<String> authorities(AccountLoginRow account) {
    List<String> authorities = new ArrayList<>();
    if (account.accountType() == AccountType.COMMAND) {
      if (account.organizationType() == OrganizationType.MISSING_TEAM) {
        authorities.add(Role.MISSING_TEAM_COMMANDER.name());
      }
      authorities.add(Role.FIELD_COMMANDER.name());
    }
    if (authorities.isEmpty()) {
      authorities.add(Role.MEMBER.name());
    }
    return authorities;
  }

  private record AuthSession(
      UUID sessionId, String accessToken, SecurityContextSnapshot securityContext) {}
}
