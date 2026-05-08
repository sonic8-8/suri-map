package com.surimap.incident.event;

/** L1 사건 변경을 S4 EventHub 경계로 넘기는 publish port. */
public interface IncidentEventPublisher {

  /** INCIDENT_CREATED publish 요청. event_dispatch_job 저장과 fanout은 S4 구현체 책임이다. */
  void publishIncidentCreated(IncidentCreatedEvent event);

  /** INCIDENT_ASSIGNMENT_CHANGED publish 요청. event_dispatch_job 저장과 fanout은 S4 구현체 책임이다. */
  void publishIncidentAssignmentChanged(IncidentAssignmentChangedEvent event);
}
