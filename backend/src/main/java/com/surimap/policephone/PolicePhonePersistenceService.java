package com.surimap.policephone;

import com.surimap.app.service.policephone.request.PolicePhoneHeartbeatServiceRequest;
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
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicePhonePersistenceService
    implements PolicePhoneHeartbeatRecorder,
        PolicePhoneValidationPort,
        PolicePhoneFreshnessQuery,
        FcmTokenQuery {

  private static final Duration STALE_THRESHOLD = Duration.ofSeconds(60);
  private static final Duration LOST_THRESHOLD = Duration.ofMinutes(5);

  private final PolicePhoneMapper mapper;
  private final Clock clock;

  public PolicePhonePersistenceService(PolicePhoneMapper mapper, Clock clock) {
    this.mapper = mapper;
    this.clock = clock;
  }

  @Override
  @Transactional(readOnly = true)
  public void checkRegistered(UUID policePhoneId) {
    registeredPhone(policePhoneId);
  }

  @Override
  @Transactional(readOnly = true)
  public void checkAssigned(UUID policePhoneId) {
    registeredPhone(policePhoneId);
    activeAssignment(policePhoneId);
  }

  @Transactional
  public PolicePhoneHeartbeatResult recordHeartbeat(
      PolicePhoneHeartbeatServiceRequest request, Instant receivedAt) {
    PolicePhoneStateRow before = registeredPhone(request.policePhoneId());
    validateAccountBinding(before, request);
    PolicePhoneAssignmentRow assignment = activeAssignment(request.policePhoneId());

    if (request.sequence() <= before.heartbeatSequence()) {
      return toHeartbeatResult(before, assignment.incidentId(), false);
    }

    mapper.updateHeartbeatIfNewer(
        request.policePhoneId(),
        UUID.randomUUID(),
        request.sequence(),
        receivedAt,
        request.lastSyncAt());
    PolicePhoneStateRow after = registeredPhone(request.policePhoneId());
    return toHeartbeatResult(after, assignment.incidentId(), true);
  }

  @Transactional
  public FcmTokenRow registerFcmToken(
      UUID policePhoneId, String accountId, String appInstanceId, String token) {
    PolicePhoneStateRow phone = registeredPhone(policePhoneId);
    if (!phone.accountId().toString().equals(accountId)) {
      throw new PolicePhoneNotAssignedException();
    }

    Instant now = clock.instant();
    long nextVersion =
        mapper.findActiveTokenVersion(policePhoneId, appInstanceId).map(version -> version + 1).orElse(1L);
    mapper.revokeActiveTokenForInstance(policePhoneId, appInstanceId, now);
    UUID tokenId = UUID.randomUUID();
    mapper.insertFcmToken(
        tokenId,
        phone.accountId(),
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

  @Override
  @Transactional(readOnly = true)
  public List<PolicePhoneFreshnessRow> byIncident(UUID incidentId) {
    Instant now = clock.instant();
    return mapper.findFreshnessByIncident(incidentId).stream()
        .map(row -> toFreshnessRow(row, now))
        .toList();
  }

  private PolicePhoneStateRow registeredPhone(UUID policePhoneId) {
    PolicePhoneStateRow phone =
        mapper.findActiveById(policePhoneId).orElseThrow(PolicePhoneNotRegisteredException::new);
    if (!phone.registered()) {
      throw new PolicePhoneNotRegisteredException();
    }
    return phone;
  }

  private PolicePhoneAssignmentRow activeAssignment(UUID policePhoneId) {
    return mapper
        .findActiveAssignmentByPolicePhone(policePhoneId)
        .orElseThrow(PolicePhoneNotAssignedException::new);
  }

  private static void validateAccountBinding(
      PolicePhoneStateRow phone, PolicePhoneHeartbeatServiceRequest request) {
    if (!phone.accountId().toString().equals(request.accountId())
        || phone.accountType() != request.accountType()
        || phone.organizationType() != request.organizationType()) {
      throw new PolicePhoneNotAssignedException();
    }
  }

  private static PolicePhoneHeartbeatResult toHeartbeatResult(
      PolicePhoneStateRow phone, UUID incidentId, boolean accepted) {
    return new PolicePhoneHeartbeatResult(
        heartbeatEventId(phone),
        PolicePhoneFreshnessStatus.ONLINE,
        phone.version(),
        incidentId,
        phone.id(),
        phone.heartbeatSequence(),
        phone.lastHeartbeatAt(),
        phone.lastSyncAt(),
        accepted);
  }

  private PolicePhoneFreshnessRow toFreshnessRow(
      PolicePhoneFreshnessStateRow row, Instant now) {
    return new PolicePhoneFreshnessRow(
        row.policePhoneId(),
        row.accountId().toString(),
        row.accountType(),
        row.organizationType(),
        row.incidentId(),
        null,
        row.lastHeartbeatEventId(),
        row.lastHeartbeatAt(),
        row.lastSyncAt(),
        row.version(),
        row.heartbeatSequence(),
        deriveFreshness(row.lastHeartbeatAt(), now));
  }

  private static UUID heartbeatEventId(PolicePhoneStateRow phone) {
    if (phone.lastHeartbeatEventId() != null) {
      return phone.lastHeartbeatEventId();
    }
    return UUID.nameUUIDFromBytes(
        ("police-phone-heartbeat:"
                + phone.id()
                + ":"
                + phone.heartbeatSequence()
                + ":"
                + phone.version())
            .getBytes(StandardCharsets.UTF_8));
  }

  private static PolicePhoneFreshnessStatus deriveFreshness(Instant lastHeartbeatAt, Instant now) {
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
