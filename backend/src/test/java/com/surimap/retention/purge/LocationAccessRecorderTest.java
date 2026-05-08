package com.surimap.retention.purge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("S1-3 위치정보 접근기록 append 계약")
class LocationAccessRecorderTest {

  @Test
  @DisplayName("record는 internal audit row를 저장하고 retentionUntil을 accessedAt 이후 최소 6개월로 계산한다")
  void recordStoresInternalAuditRowWithMinimumSixMonthRetention() {
    CapturingLocationAccessAuditMapper mapper = new CapturingLocationAccessAuditMapper();
    LocationAccessRecorder recorder = new LocationAccessRecorder(mapper);
    UUID accountId = UUID.fromString("11111111-1111-1111-1111-111111110010");
    UUID incidentId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
    UUID policePhoneId = UUID.fromString("33333333-3333-4333-8333-333333330001");
    Instant accessedAt = Instant.parse("2026-04-28T01:30:00Z");

    LocationAccessAuditRecord record =
        recorder.record(accountId, incidentId, policePhoneId, "WEB", "BOARD_VIEW", accessedAt);

    assertThat(mapper.captured).isEqualTo(record);
    assertThat(record.accountId()).isEqualTo(accountId);
    assertThat(record.incidentId()).isEqualTo(incidentId);
    assertThat(record.policePhoneId()).isEqualTo(policePhoneId);
    assertThat(record.accessChannel()).isEqualTo("WEB");
    assertThat(record.accessPurpose()).isEqualTo("BOARD_VIEW");
    assertThat(record.accessedAt()).isEqualTo(accessedAt);
    assertThat(record.retentionUntil()).isEqualTo(Instant.parse("2026-10-28T01:30:00Z"));
  }

  private static final class CapturingLocationAccessAuditMapper
      implements LocationAccessAuditMapper {

    private LocationAccessAuditRecord captured;

    @Override
    public void insert(LocationAccessAuditRecord record) {
      this.captured = record;
    }
  }
}
