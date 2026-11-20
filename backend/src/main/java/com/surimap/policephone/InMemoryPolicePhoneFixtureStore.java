package com.surimap.policephone;

import com.surimap.common.auth.guard.PolicePhoneNotAssignedException;
import com.surimap.common.auth.guard.PolicePhoneNotRegisteredException;
import com.surimap.common.auth.guard.PolicePhoneValidationPort;
import com.surimap.policephone.query.FcmTokenQuery;
import com.surimap.policephone.query.FcmTokenRow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal S1-2 fixture store for registered and assigned PolicePhone state.
 *
 * <p>This keeps the vertical slice local to L2-T03 until the production police_phone persistence
 * is introduced.
 */
public class InMemoryPolicePhoneFixtureStore implements PolicePhoneValidationPort, FcmTokenQuery {

  private final Clock clock;
  private final Map<UUID, FixtureState> fixtures = new ConcurrentHashMap<>();
  private final Map<UUID, FcmTokenFixtureState> activeTokensById = new ConcurrentHashMap<>();
  private final Map<String, UUID> activeTokenIdsByPhoneAndInstance = new ConcurrentHashMap<>();

  public InMemoryPolicePhoneFixtureStore(Clock clock) {
    this.clock = clock;
    seedFixtures();
  }

  public synchronized void reset() {
    fixtures.clear();
    activeTokensById.clear();
    activeTokenIdsByPhoneAndInstance.clear();
    seedFixtures();
  }

  @Override
  public void checkAssigned(UUID policePhoneId) {
    FixtureState fixture = fixtures.get(policePhoneId);
    if (fixture == null || !fixture.assigned()) {
      throw new PolicePhoneNotAssignedException();
    }
  }

  @Override
  public List<FcmTokenRow> activeByPolicePhone(UUID policePhoneId) {
    return activeTokensById.values().stream()
        .filter(token -> token.policePhoneId().equals(policePhoneId))
        .filter(token -> token.status() == FcmTokenStatus.ACTIVE)
        .sorted((left, right) -> left.appInstanceId().compareTo(right.appInstanceId()))
        .map(FcmTokenFixtureState::toRow)
        .toList();
  }

  @Override
  public List<FcmTokenRow> activeByAccounts(List<UUID> accountIds) {
    if (accountIds == null || accountIds.isEmpty()) {
      return List.of();
    }
    return activeTokensById.values().stream()
        .filter(token -> accountIds.contains(token.accountId()))
        .filter(token -> token.status() == FcmTokenStatus.ACTIVE)
        .sorted((left, right) -> left.policePhoneId().compareTo(right.policePhoneId()))
        .map(FcmTokenFixtureState::toRow)
        .toList();
  }

  public FcmTokenRow registerFcmToken(UUID policePhoneId, String appInstanceId, String token) {
    return registerFcmToken(policePhoneId, appInstanceId, token, clock.instant());
  }

  public synchronized FcmTokenRow registerFcmToken(
      UUID policePhoneId, String appInstanceId, String token, Instant registeredAt) {
    FixtureState fixture = fixtures.get(policePhoneId);
    if (fixture == null) {
      throw new PolicePhoneNotRegisteredException();
    }

    String key = activeTokenKey(policePhoneId, appInstanceId);
    UUID existingId = activeTokenIdsByPhoneAndInstance.remove(key);
    long nextVersion = 1L;
    if (existingId != null) {
      FcmTokenFixtureState existing = activeTokensById.remove(existingId);
      if (existing != null) {
        existing.revoke(registeredAt);
        nextVersion = existing.version() + 1;
      }
    }

    FcmTokenFixtureState active =
        new FcmTokenFixtureState(
            buildTokenId(policePhoneId, appInstanceId, nextVersion),
            fixture.accountId(),
            policePhoneId,
            appInstanceId,
            encryptToken(token),
            hashToken(token),
            FcmTokenStatus.ACTIVE,
            registeredAt,
            registeredAt,
            null,
            nextVersion);
    activeTokensById.put(active.id(), active);
    activeTokenIdsByPhoneAndInstance.put(key, active.id());
    return active.toRow();
  }

  public synchronized void revokeActiveTokensForLogout(UUID policePhoneId, String accountId) {
    revokeActiveTokensForLogout(policePhoneId, accountId, clock.instant());
  }

  public synchronized void revokeActiveTokensForLogout(
      UUID policePhoneId, String accountId, Instant revokedAt) {
    activeTokensById.values().stream()
        .filter(token -> token.policePhoneId().equals(policePhoneId))
        .filter(token -> token.accountId().equals(accountId))
        .toList()
        .forEach(
            token -> {
              token.revoke(revokedAt);
              activeTokensById.remove(token.id());
              activeTokenIdsByPhoneAndInstance.remove(
                  activeTokenKey(token.policePhoneId(), token.appInstanceId()));
            });
  }

  private void seedFixtures() {
    Instant seededAt = clock.instant().minusSeconds(30);
    fixtures.put(
        PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID,
        new FixtureState(PolicePhoneFixtures.ASSIGNED_ACCOUNT_ID, true));
    fixtures.put(
        PolicePhoneFixtures.ASSIGNED_PATH_POLICE_PHONE_ID,
        new FixtureState(PolicePhoneFixtures.ASSIGNED_ACCOUNT_ID, true));
    fixtures.put(
        PolicePhoneFixtures.REGISTERED_UNASSIGNED_POLICE_PHONE_ID,
        new FixtureState(PolicePhoneFixtures.ASSIGNED_ACCOUNT_ID, false));
    registerFcmToken(
        PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID,
        PolicePhoneFixtures.ASSIGNED_APP_INSTANCE_ID,
        PolicePhoneFixtures.ASSIGNED_APP_TOKEN,
        seededAt);
    registerFcmToken(
        PolicePhoneFixtures.ASSIGNED_PATH_POLICE_PHONE_ID,
        PolicePhoneFixtures.PATH_APP_INSTANCE_ID,
        PolicePhoneFixtures.PATH_APP_TOKEN,
        seededAt);
  }

  private static String activeTokenKey(UUID policePhoneId, String appInstanceId) {
    return policePhoneId + ":" + appInstanceId;
  }

  private static UUID buildTokenId(UUID policePhoneId, String appInstanceId, long version) {
    return UUID.nameUUIDFromBytes(
        (policePhoneId + ":" + appInstanceId + ":" + version).getBytes(StandardCharsets.UTF_8));
  }

  private static String encryptToken(String token) {
    return "cipher:" + token;
  }

  private static String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 must be available", exception);
    }
  }

  private static final class FixtureState {
    private final String accountId;
    private final boolean assigned;

    private FixtureState(String accountId, boolean assigned) {
      this.accountId = accountId;
      this.assigned = assigned;
    }

    private String accountId() {
      return accountId;
    }

    private boolean assigned() {
      return assigned;
    }
  }

  private static final class FcmTokenFixtureState {
    private final UUID id;
    private final String accountId;
    private final UUID policePhoneId;
    private final String appInstanceId;
    private final String tokenCiphertext;
    private final String tokenHash;
    private FcmTokenStatus status;
    private final Instant createdAt;
    private Instant lastRegisteredAt;
    private Instant revokedAt;
    private long version;

    private FcmTokenFixtureState(
        UUID id,
        String accountId,
        UUID policePhoneId,
        String appInstanceId,
        String tokenCiphertext,
        String tokenHash,
        FcmTokenStatus status,
        Instant createdAt,
        Instant lastRegisteredAt,
        Instant revokedAt,
        long version) {
      this.id = id;
      this.accountId = accountId;
      this.policePhoneId = policePhoneId;
      this.appInstanceId = appInstanceId;
      this.tokenCiphertext = tokenCiphertext;
      this.tokenHash = tokenHash;
      this.status = status;
      this.createdAt = createdAt;
      this.lastRegisteredAt = lastRegisteredAt;
      this.revokedAt = revokedAt;
      this.version = version;
    }

    private UUID id() {
      return id;
    }

    private String accountId() {
      return accountId;
    }

    private UUID policePhoneId() {
      return policePhoneId;
    }

    private String appInstanceId() {
      return appInstanceId;
    }

    private long version() {
      return version;
    }

    private FcmTokenStatus status() {
      return status;
    }

    private void revoke(Instant revokedAt) {
      this.status = FcmTokenStatus.REVOKED;
      this.revokedAt = revokedAt;
    }

    private FcmTokenRow toRow() {
      return new FcmTokenRow(
          id, policePhoneId, appInstanceId, tokenCiphertext, tokenHash, status, version);
    }
  }
}
