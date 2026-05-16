package com.surimap.policephone;

import com.surimap.app.service.policephone.request.PolicePhoneHeartbeatServiceRequest;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.guard.PolicePhoneNotAssignedException;
import com.surimap.common.auth.guard.PolicePhoneNotRegisteredException;
import com.surimap.common.auth.guard.PolicePhoneValidationPort;
import com.surimap.policephone.query.FcmTokenQuery;
import com.surimap.policephone.query.FcmTokenRow;
import com.surimap.policephone.query.PolicePhoneFreshnessQuery;
import com.surimap.policephone.query.PolicePhoneFreshnessRow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
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
public class InMemoryPolicePhoneFixtureStore
    implements PolicePhoneHeartbeatRecorder,
        PolicePhoneValidationPort,
        PolicePhoneFreshnessQuery,
        FcmTokenQuery {

  private static final Duration STALE_THRESHOLD = Duration.ofSeconds(60);
  private static final Duration LOST_THRESHOLD = Duration.ofMinutes(5);

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
  public void checkRegistered(UUID policePhoneId) {
    if (!fixtures.containsKey(policePhoneId)) {
      throw new PolicePhoneNotRegisteredException();
    }
  }

  @Override
  public void checkAssigned(UUID policePhoneId) {
    FixtureState fixture = fixtures.get(policePhoneId);
    if (fixture == null) {
      throw new PolicePhoneNotRegisteredException();
    }
    if (!fixture.assigned()) {
      throw new PolicePhoneNotAssignedException();
    }
  }

  public PolicePhoneHeartbeatResult recordHeartbeat(
      PolicePhoneHeartbeatServiceRequest request, Instant receivedAt) {
    FixtureState fixture = fixtures.get(request.policePhoneId());
    if (fixture == null) {
      throw new PolicePhoneNotRegisteredException();
    }
    if (!fixture.assigned()) {
      throw new PolicePhoneNotAssignedException();
    }
    if (!fixture.accountId().equals(request.accountId())
        || fixture.accountType() != request.accountType()
        || fixture.organizationType() != request.organizationType()) {
      throw new PolicePhoneNotAssignedException();
    }

    if (request.sequence() <= fixture.sequence()) {
      return fixture.toHeartbeatResult(false);
    }

    fixture.sequence = request.sequence();
    fixture.version = fixture.version() + 1;
    fixture.lastEventId = UUID.randomUUID();
    fixture.lastHeartbeatAt = receivedAt;
    if (request.lastSyncAt() != null) {
      fixture.lastSyncAt = request.lastSyncAt();
    }

    return fixture.toHeartbeatResult(true);
  }

  @Override
  public List<PolicePhoneFreshnessRow> byIncident(UUID incidentId) {
    Instant now = clock.instant();
    return fixtures.values().stream()
        .filter(FixtureState::assigned)
        .filter(fixture -> fixture.incidentId().equals(incidentId))
        .map(fixture -> fixture.toFreshnessRow(deriveFreshness(fixture.lastHeartbeatAt(), now)))
        .toList();
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

  private PolicePhoneFreshnessStatus deriveFreshness(Instant lastHeartbeatAt, Instant now) {
    if (lastHeartbeatAt == null) {
      return PolicePhoneFreshnessStatus.LOST;
    }

    Duration age = Duration.between(lastHeartbeatAt, now);
    if (age.compareTo(STALE_THRESHOLD) < 0) {
      return PolicePhoneFreshnessStatus.ONLINE;
    }
    if (age.compareTo(LOST_THRESHOLD) < 0) {
      return PolicePhoneFreshnessStatus.STALE;
    }
    return PolicePhoneFreshnessStatus.LOST;
  }

  private void seedFixtures() {
    Instant seededAt = clock.instant().minusSeconds(30);
    fixtures.put(
        PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID,
        new FixtureState(
            PolicePhoneFixtures.ASSIGNED_POLICE_PHONE_ID,
            PolicePhoneFixtures.ASSIGNED_ACCOUNT_ID,
            PolicePhoneFixtures.ASSIGNED_ACCOUNT_TYPE,
            PolicePhoneFixtures.ASSIGNED_ORGANIZATION_TYPE,
            PolicePhoneFixtures.INCIDENT_ID,
            PolicePhoneFixtures.OP_ID,
            true,
            UUID.nameUUIDFromBytes("heartbeat-seed-101".getBytes()),
            0L,
            0L,
            seededAt,
            seededAt));
    fixtures.put(
        PolicePhoneFixtures.ASSIGNED_PATH_POLICE_PHONE_ID,
        new FixtureState(
            PolicePhoneFixtures.ASSIGNED_PATH_POLICE_PHONE_ID,
            PolicePhoneFixtures.ASSIGNED_ACCOUNT_ID,
            PolicePhoneFixtures.ASSIGNED_ACCOUNT_TYPE,
            PolicePhoneFixtures.ASSIGNED_ORGANIZATION_TYPE,
            PolicePhoneFixtures.INCIDENT_ID,
            PolicePhoneFixtures.OP_ID,
            true,
            UUID.nameUUIDFromBytes("heartbeat-seed-500".getBytes()),
            0L,
            0L,
            seededAt,
            seededAt));
    fixtures.put(
        PolicePhoneFixtures.REGISTERED_UNASSIGNED_POLICE_PHONE_ID,
        new FixtureState(
            PolicePhoneFixtures.REGISTERED_UNASSIGNED_POLICE_PHONE_ID,
            PolicePhoneFixtures.ASSIGNED_ACCOUNT_ID,
            PolicePhoneFixtures.ASSIGNED_ACCOUNT_TYPE,
            PolicePhoneFixtures.ASSIGNED_ORGANIZATION_TYPE,
            PolicePhoneFixtures.INCIDENT_ID,
            null,
            false,
            UUID.nameUUIDFromBytes("heartbeat-seed-301".getBytes()),
            0L,
            0L,
            null,
            null));
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
    private final UUID policePhoneId;
    private final String accountId;
    private final AccountType accountType;
    private final OrganizationType organizationType;
    private final UUID incidentId;
    private final UUID opId;
    private final boolean assigned;
    private UUID lastEventId;
    private long sequence;
    private long version;
    private Instant lastHeartbeatAt;
    private Instant lastSyncAt;

    private FixtureState(
        UUID policePhoneId,
        String accountId,
        AccountType accountType,
        OrganizationType organizationType,
        UUID incidentId,
        UUID opId,
        boolean assigned,
        UUID lastEventId,
        long sequence,
        long version,
        Instant lastHeartbeatAt,
        Instant lastSyncAt) {
      this.policePhoneId = policePhoneId;
      this.accountId = accountId;
      this.accountType = accountType;
      this.organizationType = organizationType;
      this.incidentId = incidentId;
      this.opId = opId;
      this.assigned = assigned;
      this.lastEventId = lastEventId;
      this.sequence = sequence;
      this.version = version;
      this.lastHeartbeatAt = lastHeartbeatAt;
      this.lastSyncAt = lastSyncAt;
    }

    private UUID policePhoneId() {
      return policePhoneId;
    }

    private String accountId() {
      return accountId;
    }

    private AccountType accountType() {
      return accountType;
    }

    private OrganizationType organizationType() {
      return organizationType;
    }

    private UUID incidentId() {
      return incidentId;
    }

    private boolean assigned() {
      return assigned;
    }

    private long sequence() {
      return sequence;
    }

    private long version() {
      return version;
    }

    private Instant lastHeartbeatAt() {
      return lastHeartbeatAt;
    }

    private PolicePhoneHeartbeatResult toHeartbeatResult(boolean accepted) {
      return new PolicePhoneHeartbeatResult(
          lastEventId,
          PolicePhoneFreshnessStatus.ONLINE,
          version,
          incidentId,
          policePhoneId,
          sequence,
          lastHeartbeatAt,
          lastSyncAt,
          accepted);
    }

    private PolicePhoneFreshnessRow toFreshnessRow(PolicePhoneFreshnessStatus freshness) {
      return new PolicePhoneFreshnessRow(
          policePhoneId,
          accountId,
          accountType,
          organizationType,
          incidentId,
          opId,
          lastEventId,
          lastHeartbeatAt,
          lastSyncAt,
          version,
          sequence,
          freshness);
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
