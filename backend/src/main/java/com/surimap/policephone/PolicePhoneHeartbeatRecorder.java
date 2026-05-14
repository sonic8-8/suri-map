package com.surimap.policephone;

import com.surimap.app.service.policephone.request.PolicePhoneHeartbeatServiceRequest;
import java.time.Instant;

public interface PolicePhoneHeartbeatRecorder {
  PolicePhoneHeartbeatResult recordHeartbeat(
      PolicePhoneHeartbeatServiceRequest request, Instant receivedAt);
}
