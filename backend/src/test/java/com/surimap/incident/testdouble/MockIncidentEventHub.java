package com.surimap.incident.testdouble;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** S4 EventHub capture mock. eventId와 payload id/status/version 공통 필드를 검증한다. */
public final class MockIncidentEventHub {

  private final List<IncidentPublishRequest> publishedRequests = new ArrayList<>();
  private RuntimeException nextDispatchFailure;
  private RuntimeException nextSseFailure;

  public void publish(IncidentPublishRequest request) {
    Objects.requireNonNull(request, "request는 null일 수 없습니다");
    validate(request);
    if (nextDispatchFailure != null) {
      RuntimeException failure = nextDispatchFailure;
      nextDispatchFailure = null;
      throw failure;
    }
    publishedRequests.add(request);
    if (nextSseFailure != null) {
      RuntimeException failure = nextSseFailure;
      nextSseFailure = null;
      throw failure;
    }
  }

  public void failNextDispatchJob(String failureKey) {
    nextDispatchFailure = new IllegalStateException(failureKey);
  }

  public void failNextSseDelivery(String failureKey) {
    nextSseFailure = new IllegalStateException(failureKey);
  }

  public List<IncidentPublishRequest> publishedRequests() {
    return List.copyOf(publishedRequests);
  }

  private static void validate(IncidentPublishRequest request) {
    requireText(request.eventId(), "eventId");
    requireText(request.type(), "type");
    requireText(request.incidentId(), "incidentId");
    requireText(request.payloadId(), "payloadId");
    requireText(request.status(), "status");
    if (!request.incidentId().equals(request.payloadId())) {
      throw new IllegalArgumentException("payloadId는 incidentId와 같아야 합니다");
    }
    if (request.version() <= 0) {
      throw new IllegalArgumentException("version은 양수여야 합니다");
    }
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("PublishRequest 필드가 비어 있습니다: " + field);
    }
  }
}
