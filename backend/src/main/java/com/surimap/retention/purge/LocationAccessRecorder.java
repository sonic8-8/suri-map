package com.surimap.retention.purge;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** S1-3 internal/system-only location_data_access_audit append contract. */
@Service
public class LocationAccessRecorder {

  private final LocationAccessAuditMapper mapper;

  public LocationAccessRecorder(LocationAccessAuditMapper mapper) {
    this.mapper = mapper;
  }

  @Transactional
  public LocationAccessAuditRecord record(
      UUID accountId,
      UUID incidentId,
      UUID policePhoneId,
      String accessChannel,
      String accessPurpose,
      Instant serverTs) {
    Objects.requireNonNull(accountId, "accountId는 null일 수 없습니다");
    Objects.requireNonNull(incidentId, "incidentId는 null일 수 없습니다");
    Objects.requireNonNull(accessChannel, "accessChannel은 null일 수 없습니다");
    Objects.requireNonNull(accessPurpose, "accessPurpose는 null일 수 없습니다");
    Objects.requireNonNull(serverTs, "serverTs는 null일 수 없습니다");

    LocationAccessAuditRecord record =
        new LocationAccessAuditRecord(
            UUID.randomUUID(),
            incidentId,
            accountId,
            policePhoneId,
            accessChannel,
            accessPurpose,
            serverTs,
            serverTs.atOffset(ZoneOffset.UTC).plusMonths(6).toInstant());
    mapper.insert(record);
    return record;
  }
}
