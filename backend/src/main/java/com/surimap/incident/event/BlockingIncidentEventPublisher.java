package com.surimap.incident.event;

import com.surimap.incident.exception.IncidentApiException;
import org.springframework.http.HttpStatus;

/** S4 구현체가 없을 때 event_dispatch_job 없는 import 성공을 막는 fallback. */
public class BlockingIncidentEventPublisher implements IncidentEventPublisher {

  @Override
  public void publishIncidentCreated(IncidentCreatedEvent event) {
    throw new IncidentApiException("write_conflict", HttpStatus.CONFLICT);
  }

  @Override
  public void publishIncidentAssignmentChanged(IncidentAssignmentChangedEvent event) {
    throw new IncidentApiException("write_conflict", HttpStatus.CONFLICT);
  }

  @Override
  public void publishIncidentClosed(IncidentClosedEvent event) {
    throw new IncidentApiException("write_conflict", HttpStatus.CONFLICT);
  }
}
