package com.surimap.maparea.mock;

import com.surimap.maparea.query.SearchAreaAssignmentQuery;
import com.surimap.maparea.query.SearchAreaAssignmentRow;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SearchAreaAssignmentQuery 테스트 이중 (mock contract).
 *
 * <p>실제 DB 구현 없이 소비 Lane이 query contract를 검증할 수 있게 한다.
 * S2.json §consumed 에서 S8이 SearchAreaAssignmentQuery.byOp를 소비하는 계약을 따른다.
 */
public class MockSearchAreaAssignmentQuery implements SearchAreaAssignmentQuery {

  private final List<SearchAreaAssignmentRow> rowsByOp = new ArrayList<>();
  private final List<SearchAreaAssignmentRow> rowsByArea = new ArrayList<>();

  public MockSearchAreaAssignmentQuery stubByOp(List<SearchAreaAssignmentRow> rows) {
    rowsByOp.clear();
    rowsByOp.addAll(rows);
    return this;
  }

  public MockSearchAreaAssignmentQuery stubByArea(List<SearchAreaAssignmentRow> rows) {
    rowsByArea.clear();
    rowsByArea.addAll(rows);
    return this;
  }

  @Override
  public List<SearchAreaAssignmentRow> byOp(UUID opId) {
    return List.copyOf(rowsByOp);
  }

  @Override
  public List<SearchAreaAssignmentRow> byArea(UUID searchAreaId) {
    return List.copyOf(rowsByArea);
  }
}
