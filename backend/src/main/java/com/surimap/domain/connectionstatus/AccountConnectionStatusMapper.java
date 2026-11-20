package com.surimap.domain.connectionstatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountConnectionStatusMapper {

  void upsertHeartbeat(
      @Param("accountId") UUID accountId,
      @Param("policePhoneId") UUID policePhoneId,
      @Param("receivedAt") Instant receivedAt,
      @Param("lastSyncAt") Instant lastSyncAt);

  AccountConnectionStatus findByAccountId(@Param("accountId") UUID accountId);

  List<UUID> findOpenIncidentIdsByAccount(@Param("accountId") UUID accountId);

  List<ConnectionStatusRow> findFieldAccountsByIncident(@Param("incidentId") UUID incidentId);
}
