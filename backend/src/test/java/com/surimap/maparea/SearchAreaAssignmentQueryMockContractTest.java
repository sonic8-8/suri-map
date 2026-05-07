package com.surimap.maparea;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.maparea.fixture.BoundaryAreaFixtures;
import com.surimap.maparea.fixture.SearchAreaAssignmentFixtures;
import com.surimap.maparea.fixture.SearchAreaAssignmentFixtures.AssignmentRow;
import com.surimap.maparea.mock.MockSearchAreaAssignmentQuery;
import com.surimap.maparea.query.SearchAreaAssignmentQuery;
import com.surimap.maparea.query.SearchAreaAssignmentRow;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SearchAreaAssignmentQuery mock contract test (S2.json §service_contracts).
 *
 * <p>기준 문서: docs/spec/specs/S2.json SearchAreaAssignmentQuery.byOp / byArea.
 * docs/spec/harness-scenarios.md §2 SC-04.
 *
 * <p>이 테스트는 real DB 구현 없이 소비 Lane이 query contract를 검증할 수 있음을 보장하는 RED test다.
 * 구현이 없으므로 컴파일·링크 오류로 실패한다.
 */
@DisplayName("SC-04 SearchAreaAssignmentQuery mock contract (S2)")
class SearchAreaAssignmentQueryMockContractTest {

  private final SearchAreaAssignmentQuery query =
      new MockSearchAreaAssignmentQuery()
          .stubByOp(List.of(toRow(SearchAreaAssignmentFixtures.activeAssignment())));

  @Test
  @DisplayName("byOp는 OP1 기준 ACTIVE assignment를 반환한다")
  void byOp_returnsActiveAssignmentForOp1() {
    var rows = query.byOp(BoundaryAreaFixtures.OP1_ID);

    assertThat(rows).hasSize(1);
    var row = rows.get(0);
    assertThat(row.id()).isEqualTo(SearchAreaAssignmentFixtures.ASSIGNMENT_ID);
    assertThat(row.searchAreaId()).isEqualTo(BoundaryAreaFixtures.AREA_ID);
    assertThat(row.assignedAccountId())
        .isEqualTo(SearchAreaAssignmentFixtures.ASSIGNEE_ACCOUNT_ID);
    assertThat(row.status()).isEqualTo("ACTIVE");
    assertThat(row.revokedAt()).isNull();
  }

  @Test
  @DisplayName("byOp 결과 row는 id/status/version/searchAreaId를 포함한다")
  void byOp_rowContainsIdStatusVersionSearchAreaId() {
    var rows = query.byOp(BoundaryAreaFixtures.OP1_ID);

    assertThat(rows).allSatisfy(row -> {
      assertThat(row.id()).isNotNull();
      assertThat(row.searchAreaId()).isNotNull();
      assertThat(row.assignedAccountId()).isNotNull();
      assertThat(row.status()).isIn(SearchAreaAssignmentFixtures.ASSIGNMENT_STATUSES);
      assertThat(row.version()).isGreaterThan(0);
    });
  }

  @Test
  @DisplayName("byArea는 searchAreaId 기준 assignment 이력을 반환한다")
  void byArea_returnsAssignmentHistoryForArea() {
    var queryWithAreaStub =
        new MockSearchAreaAssignmentQuery()
            .stubByArea(List.of(toRow(SearchAreaAssignmentFixtures.activeAssignment())));

    var rows = queryWithAreaStub.byArea(BoundaryAreaFixtures.AREA_ID);

    assertThat(rows).isNotEmpty();
    assertThat(rows.get(0).searchAreaId()).isEqualTo(BoundaryAreaFixtures.AREA_ID);
  }

  @Test
  @DisplayName("OP 없는 구역은 byOp가 빈 리스트를 반환한다")
  void byOp_returnsEmptyWhenNoAssignment() {
    var emptyQuery = new MockSearchAreaAssignmentQuery();

    var rows = emptyQuery.byOp(BoundaryAreaFixtures.OP2_ID);

    assertThat(rows).isEmpty();
  }

  private static SearchAreaAssignmentRow toRow(AssignmentRow fixture) {
    return new SearchAreaAssignmentRow(
        fixture.id(),
        fixture.searchAreaId(),
        fixture.assignedAccountId(),
        fixture.assignedByAccountId(),
        fixture.assignedAt(),
        fixture.revokedAt(),
        fixture.status(),
        fixture.version());
  }
}
