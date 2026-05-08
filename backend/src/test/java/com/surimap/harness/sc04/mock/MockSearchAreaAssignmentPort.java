package com.surimap.harness.sc04.mock;

import com.surimap.harness.sc04.fixture.Sc04Fixtures;
import com.surimap.maparea.assignment.SearchAreaAssignmentChangedEvent;
import com.surimap.maparea.assignment.SearchAreaAssignmentCommand;
import com.surimap.maparea.assignment.SearchAreaAssignmentRequest;
import com.surimap.maparea.assignment.SearchAreaAssignmentResult;
import com.surimap.maparea.query.SearchAreaAssignmentQuery;
import com.surimap.maparea.query.SearchAreaAssignmentRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SC-04 harness search_area_assignment mock port.
 *
 * <p>SearchAreaAssignmentCommand 와 SearchAreaAssignmentQuery 를 모두 구현하여
 * 배정 write + 배정 query(board convergence) 를 단일 mock 으로 검증한다.
 *
 * <p>기준 문서: docs/spec/specs/S2.json §api_contracts,
 * docs/spec/specs/S8.json §harness_fixtures.sc04,
 * docs/spec/harness-scenarios.md §2 SC-04 then 3·4항.
 */
public class MockSearchAreaAssignmentPort
    implements SearchAreaAssignmentCommand, SearchAreaAssignmentQuery {

  private final List<SearchAreaAssignmentRequest> capturedRequests = new ArrayList<>();
  private final List<SearchAreaAssignmentRow> committedRows = new ArrayList<>();
  private final List<SearchAreaAssignmentChangedEvent> publishedEvents = new ArrayList<>();

  // ── SearchAreaAssignmentCommand ─────────────────────────────────────────

  @Override
  public SearchAreaAssignmentResult assign(SearchAreaAssignmentRequest request) {
    capturedRequests.add(request);

    UUID firstAssignee =
        request.assigneeAccountIds().isEmpty()
            ? null
            : request.assigneeAccountIds().get(0);

    SearchAreaAssignmentRow row =
        new SearchAreaAssignmentRow(
            Sc04Fixtures.ASSIGNMENT_ID,
            request.searchAreaId(),
            firstAssignee,
            request.assignedByAccountId(),
            Instant.now(),
            /* revokedAt= */ null,
            "ACTIVE",
            Sc04Fixtures.ASSIGNMENT_VERSION);

    SearchAreaAssignmentChangedEvent event =
        new SearchAreaAssignmentChangedEvent(
            Sc04Fixtures.ASSIGNMENT_CHANGED_EVENT_ID,
            Sc04Fixtures.ASSIGNMENT_CHANGED_EVENT_SEQUENCE,
            "SEARCH_AREA_ASSIGNMENT_CHANGED",
            row.id(),
            request.incidentId(),
            request.opId(),
            request.searchAreaId(),
            request.assigneeAccountIds(),
            "ACTIVE",
            Sc04Fixtures.ASSIGNMENT_VERSION,
            Instant.now());

    committedRows.add(row);
    publishedEvents.add(event);
    return new SearchAreaAssignmentResult(row, event);
  }

  // ── SearchAreaAssignmentQuery ────────────────────────────────────────────

  @Override
  public List<SearchAreaAssignmentRow> byOp(UUID opId) {
    if (Sc04Fixtures.OP1_ID.equals(opId)) {
      return List.copyOf(committedRows);
    }
    return List.of();
  }

  @Override
  public List<SearchAreaAssignmentRow> byArea(UUID searchAreaId) {
    if (Sc04Fixtures.AREA_ID.equals(searchAreaId)) {
      return List.copyOf(committedRows);
    }
    return List.of();
  }

  // ── inspection helpers ───────────────────────────────────────────────────

  public List<SearchAreaAssignmentRequest> capturedRequests() {
    return List.copyOf(capturedRequests);
  }

  public List<SearchAreaAssignmentChangedEvent> publishedEvents() {
    return List.copyOf(publishedEvents);
  }

  public List<SearchAreaAssignmentRow> committedRows() {
    return List.copyOf(committedRows);
  }
}
