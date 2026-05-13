package com.surimap.policephone;

import com.surimap.policephone.query.FcmTokenRow;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PolicePhoneMapper {

  Optional<PolicePhoneStateRow> findActiveById(@Param("policePhoneId") UUID policePhoneId);

  Optional<PolicePhoneAssignmentRow> findActiveAssignmentByPolicePhone(
      @Param("policePhoneId") UUID policePhoneId);

  List<PolicePhoneFreshnessStateRow> findFreshnessByIncident(@Param("incidentId") UUID incidentId);

  int updateHeartbeatIfNewer(
      @Param("policePhoneId") UUID policePhoneId,
      @Param("eventId") UUID eventId,
      @Param("sequence") long sequence,
      @Param("receivedAt") Instant receivedAt,
      @Param("lastSyncAt") Instant lastSyncAt);

  Optional<Long> findActiveTokenVersion(
      @Param("policePhoneId") UUID policePhoneId, @Param("appInstanceId") String appInstanceId);

  void revokeActiveTokenForInstance(
      @Param("policePhoneId") UUID policePhoneId,
      @Param("appInstanceId") String appInstanceId,
      @Param("revokedAt") Instant revokedAt);

  void revokeActiveTokensForLogout(
      @Param("policePhoneId") UUID policePhoneId,
      @Param("accountId") UUID accountId,
      @Param("revokedAt") Instant revokedAt);

  void insertFcmToken(
      @Param("id") UUID id,
      @Param("accountId") UUID accountId,
      @Param("policePhoneId") UUID policePhoneId,
      @Param("appInstanceId") String appInstanceId,
      @Param("tokenHash") String tokenHash,
      @Param("tokenCiphertext") String tokenCiphertext,
      @Param("status") FcmTokenStatus status,
      @Param("registeredAt") Instant registeredAt,
      @Param("version") long version);

  List<FcmTokenRow> findActiveTokensByPolicePhone(@Param("policePhoneId") UUID policePhoneId);
}
