package com.surimap.domain.path;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.domain.path.fixture.SearchPathFixtures;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@DisplayName("SearchPath mapper")
@Sql(scripts = "/sql/path/search-path-context.sql")
class SearchPathMapperTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_ID = SearchPathFixtures.INCIDENT_ID;
  private static final UUID OP_ID = UUID.fromString("65000000-0000-0000-0000-000000002621");
  private static final UUID DUTY_SHIFT_ID = UUID.fromString("60000000-0000-0000-0000-000000002621");
  private static final UUID ACCOUNT_ID = UUID.fromString("62000000-0000-0000-0000-000000002621");
  private static final UUID POLICE_PHONE_ID = SearchPathFixtures.POLICE_PHONE_ID;
  private static final UUID PATH_ID = SearchPathFixtures.PATH_ID;
  private static final Instant STARTED_AT = Instant.parse("2026-04-28T00:00:00Z");

  @Autowired private SearchPathMapper searchPathMapper;

  @Test
  @DisplayName("SearchPath를 저장하면 같은 값으로 조회한다")
  void insertPathAndFindPathById() {
    SearchPath path =
        SearchPath.builder()
            .id(PATH_ID)
            .dutyShiftId(DUTY_SHIFT_ID)
            .incidentId(INCIDENT_ID)
            .opId(OP_ID)
            .policePhoneId(POLICE_PHONE_ID)
            .accountId(ACCOUNT_ID)
            .startedAt(STARTED_AT)
            .createdAt(STARTED_AT)
            .updatedAt(STARTED_AT)
            .build();

    searchPathMapper.insertPath(path);

    SearchPath found = searchPathMapper.findPathById(PATH_ID).orElseThrow();
    assertThat(found.getId()).isEqualTo(PATH_ID);
    assertThat(found.getIncidentId()).isEqualTo(INCIDENT_ID);
    assertThat(found.getOpId()).isEqualTo(OP_ID);
    assertThat(found.getDutyShiftId()).isEqualTo(DUTY_SHIFT_ID);
    assertThat(found.getPolicePhoneId()).isEqualTo(POLICE_PHONE_ID);
    assertThat(found.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(found.getStatus()).isEqualTo(SearchPathStatus.RECORDING);
    assertThat(found.getVersion()).isEqualTo(1L);
    assertThat(found.getStartedAt()).isEqualTo(STARTED_AT);
    assertThat(found.getCreatedAt()).isEqualTo(STARTED_AT);
    assertThat(found.getUpdatedAt()).isEqualTo(STARTED_AT);
  }
}
