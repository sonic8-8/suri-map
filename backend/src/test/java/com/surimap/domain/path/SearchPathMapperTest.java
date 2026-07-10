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
  private static final UUID OTHER_DUTY_SHIFT_ID =
      UUID.fromString("71000000-0000-0000-0000-000000002622");
  private static final UUID OTHER_ACCOUNT_ID =
      UUID.fromString("63000000-0000-0000-0000-000000002621");
  private static final UUID OTHER_POLICE_PHONE_ID =
      UUID.fromString("50000000-0000-0000-0000-000000002622");
  private static final UUID OTHER_PATH_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
  private static final UUID NONMATCHING_ID =
      UUID.fromString("00000000-0000-0000-0000-000000009999");
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

  @Test
  @DisplayName("SearchPath 목록은 사건, OP, 업무폰, 계정 조건으로 필터링한다")
  @Sql(scripts = {"/sql/path/search-path-context.sql", "/sql/path/search-path-other-phone.sql"})
  void findPathsFiltersByIncidentOpPolicePhoneAndAccount() {
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
    SearchPath otherPath =
        path.toBuilder()
            .id(OTHER_PATH_ID)
            .dutyShiftId(OTHER_DUTY_SHIFT_ID)
            .policePhoneId(OTHER_POLICE_PHONE_ID)
            .accountId(OTHER_ACCOUNT_ID)
            .build();
    searchPathMapper.insertPath(path);
    searchPathMapper.insertPath(otherPath);

    assertThat(searchPathMapper.findPaths(INCIDENT_ID, null, null, null))
        .extracting(SearchPath::getId)
        .containsExactlyInAnyOrder(PATH_ID, OTHER_PATH_ID);
    assertThat(searchPathMapper.findPaths(null, OP_ID, null, null))
        .extracting(SearchPath::getId)
        .containsExactlyInAnyOrder(PATH_ID, OTHER_PATH_ID);
    assertThat(searchPathMapper.findPaths(null, null, POLICE_PHONE_ID, null))
        .extracting(SearchPath::getId)
        .containsExactly(PATH_ID);
    assertThat(searchPathMapper.findPaths(null, null, null, ACCOUNT_ID))
        .extracting(SearchPath::getId)
        .containsExactly(PATH_ID);

    assertThat(searchPathMapper.findPaths(NONMATCHING_ID, null, null, null)).isEmpty();
    assertThat(searchPathMapper.findPaths(null, NONMATCHING_ID, null, null)).isEmpty();
    assertThat(searchPathMapper.findPaths(null, null, NONMATCHING_ID, null)).isEmpty();
    assertThat(searchPathMapper.findPaths(null, null, null, NONMATCHING_ID)).isEmpty();
  }
}
