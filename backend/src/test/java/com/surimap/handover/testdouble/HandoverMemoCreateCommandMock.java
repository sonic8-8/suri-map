package com.surimap.handover.testdouble;

import com.surimap.handover.command.HandoverMemoCreateCommand;
import com.surimap.handover.command.HandoverMemoCreateRequest;
import com.surimap.handover.command.HandoverMemoCreateResult;
import com.surimap.handover.fixture.HandoverMemoFixtures;
import com.surimap.handover.fixture.HandoverMemoFixtures.ExpectedHandoverMemoEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * L3-T07 HandoverMemoCreateCommand mock test double.
 *
 * <p>실제 DB write와 EventHub 없이 HANDOVER_MEMO_CREATED PublishRequest capture와 context binding을
 * 검증한다.
 */
public final class HandoverMemoCreateCommandMock implements HandoverMemoCreateCommand {

  private final List<HandoverMemoCreateRequest> receivedRequests = new ArrayList<>();
  private final List<ExpectedHandoverMemoEvent> publishedEvents = new ArrayList<>();
  private RuntimeException nextFailure = null;

  @Override
  public HandoverMemoCreateResult create(HandoverMemoCreateRequest request) {
    if (!HandoverMemoFixtures.INCIDENT_ID.equals(request.incidentId())) {
      throw new IllegalArgumentException("unknown incidentId: " + request.incidentId());
    }
    if (nextFailure != null) {
      RuntimeException f = nextFailure;
      nextFailure = null;
      throw f;
    }
    receivedRequests.add(request);
    publishedEvents.add(
        HandoverMemoFixtures.handoverMemoCreatedEvent(
            request.memoTargetType(), request.memoTargetId()));
    return new HandoverMemoCreateResult(
        HandoverMemoFixtures.MEMO_ID,
        HandoverMemoFixtures.OP2_ID,
        HandoverMemoFixtures.MEMO_VERSION,
        request.memoTargetType(),
        request.memoTargetId());
  }

  public void failNextWith(RuntimeException failure) {
    this.nextFailure = failure;
  }

  public List<HandoverMemoCreateRequest> receivedRequests() {
    return Collections.unmodifiableList(receivedRequests);
  }

  public List<ExpectedHandoverMemoEvent> publishedEvents() {
    return Collections.unmodifiableList(publishedEvents);
  }
}
