package com.surimap.app.service.policephone;

import com.surimap.app.service.policephone.request.PolicePhoneHeartbeatServiceRequest;
import com.surimap.global.event.EventPublisher;
import com.surimap.policephone.PolicePhoneHeartbeatRecorder;
import com.surimap.policephone.PolicePhoneHeartbeatResult;
import com.surimap.policephone.PolicePhoneHeartbeatUpdatedPublishRequest;
import java.time.Clock;

public class AppPolicePhoneHeartbeatService {

  private final PolicePhoneHeartbeatRecorder heartbeatRecorder;
  private final EventPublisher eventHub;
  private final Clock clock;

  public AppPolicePhoneHeartbeatService(
      PolicePhoneHeartbeatRecorder heartbeatRecorder, EventPublisher eventHub, Clock clock) {
    this.heartbeatRecorder = heartbeatRecorder;
    this.eventHub = eventHub;
    this.clock = clock;
  }

  public PolicePhoneHeartbeatResult heartbeat(PolicePhoneHeartbeatServiceRequest request) {
    PolicePhoneHeartbeatResult result = heartbeatRecorder.recordHeartbeat(request, clock.instant());
    if (result.accepted()) {
      eventHub.publish(
          PolicePhoneHeartbeatUpdatedPublishRequest.from(result)
              .toPublishRequest(result.incidentId()));
    }
    return result;
  }
}
