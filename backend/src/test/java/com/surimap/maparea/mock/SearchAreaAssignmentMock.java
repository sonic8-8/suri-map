package com.surimap.maparea.mock;

import com.surimap.maparea.assignment.SearchAreaAssignmentCommand;
import com.surimap.maparea.assignment.SearchAreaAssignmentChangedEvent;
import com.surimap.maparea.assignment.SearchAreaAssignmentRequest;
import com.surimap.maparea.assignment.SearchAreaAssignmentResult;
import com.surimap.maparea.query.SearchAreaAssignmentRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * SearchAreaAssignmentCommand 테스트 이중 (S8, Phase 2).
 *
 * <p>실제 DB 구현 없이 소비 Lane이 write contract를 검증할 수 있게 한다.
 * docs/spec/specs/S8.json §api_contracts.
 */
public class SearchAreaAssignmentMock implements SearchAreaAssignmentCommand {

  private final List<SearchAreaAssignmentRequest> capturedRequests = new ArrayList<>();
  private SearchAreaAssignmentResult stubbedResult;

  public SearchAreaAssignmentMock stubResult(SearchAreaAssignmentResult result) {
    this.stubbedResult = result;
    return this;
  }

  @Override
  public SearchAreaAssignmentResult assign(SearchAreaAssignmentRequest request) {
    capturedRequests.add(request);
    if (stubbedResult != null) {
      return stubbedResult;
    }
    // default: build a minimal ACTIVE result from the request
    var row = new SearchAreaAssignmentRow(
        java.util.UUID.randomUUID(),
        request.searchAreaId(),
        request.assigneeAccountIds().isEmpty() ? null : request.assigneeAccountIds().get(0),
        request.assignedByAccountId(),
        Instant.now(),
        null,
        "ACTIVE",
        1L);
    var event = new SearchAreaAssignmentChangedEvent(
        "SEARCH_AREA_ASSIGNMENT_CHANGED",
        row.id(),
        request.incidentId(),
        request.opId(),
        request.searchAreaId(),
        request.assigneeAccountIds(),
        "ACTIVE",
        1L,
        Instant.now());
    return new SearchAreaAssignmentResult(row, event);
  }

  public List<SearchAreaAssignmentRequest> capturedRequests() {
    return List.copyOf(capturedRequests);
  }
}
