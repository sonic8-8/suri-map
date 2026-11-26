package com.surimap.policephone;

import com.surimap.common.auth.guard.TeamNotAssignedException;
import com.surimap.policephone.query.FcmTokenQuery;
import com.surimap.policephone.query.FcmTokenRow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicePhonePersistenceService implements FcmTokenQuery {

  private final PolicePhoneMapper mapper;
  private final Clock clock;

  public PolicePhonePersistenceService(PolicePhoneMapper mapper, Clock clock) {
    this.mapper = mapper;
    this.clock = clock;
  }

  @Transactional
  public FcmTokenRow registerFcmToken(
      UUID policePhoneId, String accountId, String appInstanceId, String token) {
    UUID actorAccountId = parseAccountId(accountId);

    Instant now = clock.instant();
    long nextVersion =
        mapper.findActiveTokenVersion(policePhoneId, appInstanceId).map(version -> version + 1).orElse(1L);
    mapper.revokeActiveTokenForInstance(policePhoneId, appInstanceId, now);
    UUID tokenId = UUID.randomUUID();
    mapper.insertFcmToken(
        tokenId,
        actorAccountId,
        policePhoneId,
        appInstanceId,
        hashToken(token),
        encryptToken(token),
        FcmTokenStatus.ACTIVE,
        now,
        nextVersion);
    return mapper.findActiveTokensByPolicePhone(policePhoneId).stream()
        .filter(row -> row.id().equals(tokenId))
        .findFirst()
        .orElseThrow();
  }

  @Transactional
  public void revokeActiveTokensForLogout(UUID policePhoneId, String accountId) {
    try {
      mapper.revokeActiveTokensForLogout(policePhoneId, UUID.fromString(accountId), clock.instant());
    } catch (IllegalArgumentException ignored) {
      // Invalid legacy session values cannot match UUID-backed token rows.
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<FcmTokenRow> activeByPolicePhone(UUID policePhoneId) {
    return mapper.findActiveTokensByPolicePhone(policePhoneId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<FcmTokenRow> activeByAccounts(List<UUID> accountIds) {
    if (accountIds == null || accountIds.isEmpty()) {
      return List.of();
    }
    return mapper.findActiveTokensByAccounts(accountIds);
  }

  private UUID parseAccountId(String accountId) {
    try {
      return UUID.fromString(accountId);
    } catch (RuntimeException ignored) {
      throw new TeamNotAssignedException();
    }
  }

  private static String encryptToken(String token) {
    return "cipher:" + token;
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
