package com.surimap.account.service;

import com.surimap.account.repository.AccountLoginMapper;
import com.surimap.account.repository.AccountLoginRow;
import com.surimap.account.repository.AuthSessionRow;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.common.auth.guard.ChannelNotAllowedException;
import com.surimap.policephone.PolicePhonePersistenceService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

public class AuthSessionService {

  private static final Duration SESSION_TTL = Duration.ofDays(30);

  private final AccountLoginMapper accountLoginMapper;
  private final PolicePhonePersistenceService policePhonePersistenceService;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public AuthSessionService(
      AccountLoginMapper accountLoginMapper,
      PolicePhonePersistenceService policePhonePersistenceService,
      PasswordEncoder passwordEncoder,
      Clock clock) {
    this.accountLoginMapper = accountLoginMapper;
    this.policePhonePersistenceService = policePhonePersistenceService;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  @Transactional
  public AuthLoginResult login(AuthLoginCommand command) {
    AccountLoginRow account =
        accountLoginMapper
            .findActiveAccountByLoginId(command.accountCode())
            .orElseThrow(ChannelNotAllowedException::new);
    if (!passwordEncoder.matches(command.password(), account.passwordHash())) {
      throw new ChannelNotAllowedException();
    }

    UUID policePhoneId = resolvePolicePhoneId(command, account.id());
    UUID sessionId = UUID.randomUUID();
    String accessToken = UUID.randomUUID().toString();
    Instant now = clock.instant();
    accountLoginMapper.insertRefreshToken(
        sessionId,
        account.id(),
        policePhoneId,
        command.channel(),
        hashToken(accessToken),
        now.plus(SESSION_TTL),
        now);
    SecurityContextSnapshot context =
        new SecurityContextSnapshot(
            account.id().toString(),
            account.accountType(),
            account.organizationType(),
            command.channel(),
            policePhoneId == null ? null : policePhoneId.toString(),
            authorities(account.accountType(), account.organizationType()));
    return new AuthLoginResult(sessionId, accessToken, context);
  }

  @Transactional
  public void logout(String accessToken, String sessionId) {
    AuthSessionRow session = revokeSession(accessToken, sessionId);
    if (session == null) {
      return;
    }
    if (session.policePhoneId() != null) {
      policePhonePersistenceService.revokeActiveTokensForLogout(
          session.policePhoneId(), session.accountId().toString());
    }
  }

  @Transactional(readOnly = true)
  public Optional<SuriMapAuthentication> authenticate(String accessToken) {
    if (accessToken == null || accessToken.isBlank()) {
      return Optional.empty();
    }
    return accountLoginMapper
        .findActiveSessionByTokenHash(hashToken(accessToken), clock.instant())
        .map(AuthSessionService::toSecurityContext)
        .map(AuthSessionService::toAuthentication);
  }

  private AuthSessionRow revokeSession(String accessToken, String sessionId) {
    AuthSessionRow session = null;
    if (accessToken != null && !accessToken.isBlank()) {
      String tokenHash = hashToken(accessToken);
      Instant now = clock.instant();
      session = accountLoginMapper.findActiveSessionByTokenHash(tokenHash, now).orElse(null);
      if (session != null) {
        accountLoginMapper.revokeByTokenHash(tokenHash, now);
      }
    }

    if (session == null && sessionId != null && !sessionId.isBlank()) {
      try {
        UUID parsedSessionId = UUID.fromString(sessionId);
        Instant now = clock.instant();
        session = accountLoginMapper.findActiveSessionBySessionId(parsedSessionId, now).orElse(null);
        if (session != null) {
          accountLoginMapper.revokeBySessionId(parsedSessionId, now);
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

  private UUID resolvePolicePhoneId(AuthLoginCommand command, UUID accountId) {
    if (command.channel() == Channel.WEB) {
      return null;
    }
    if (command.channel() != Channel.APP) {
      throw new ChannelNotAllowedException();
    }
    if (command.policePhoneCode() == null || command.policePhoneCode().isBlank()) {
      return null;
    }
    var policePhone =
        accountLoginMapper
            .findActivePolicePhoneByCode(command.policePhoneCode())
            .orElseThrow(ChannelNotAllowedException::new);
    if (!accountId.equals(policePhone.accountId())) {
      throw new ChannelNotAllowedException();
    }
    return policePhone.id();
  }

  private static SecurityContextSnapshot toSecurityContext(AuthSessionRow row) {
    return new SecurityContextSnapshot(
        row.accountId().toString(),
        row.accountType(),
        row.organizationType(),
        row.channel(),
        row.policePhoneId() == null ? null : row.policePhoneId().toString(),
        authorities(row.accountType(), row.organizationType()));
  }

  private static List<String> authorities(
      AccountType accountType, OrganizationType organizationType) {
    if (accountType == AccountType.COMMAND && organizationType == OrganizationType.MISSING_TEAM) {
      return List.of(Role.MISSING_TEAM_COMMANDER.name(), Role.FIELD_COMMANDER.name());
    }
    if (accountType == AccountType.COMMAND) {
      return List.of(Role.FIELD_COMMANDER.name());
    }
    return List.of(Role.MEMBER.name());
  }

  private static String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 must be available", exception);
    }
  }
}
