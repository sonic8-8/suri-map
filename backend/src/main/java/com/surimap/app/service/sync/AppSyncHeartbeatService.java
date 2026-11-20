package com.surimap.app.service.sync;

import com.surimap.app.service.sync.request.SyncHeartbeatServiceRequest;
import com.surimap.app.service.sync.response.SyncHeartbeatServiceResponse;
import com.surimap.domain.connectionstatus.AccountConnectionStatus;
import com.surimap.domain.connectionstatus.AccountConnectionStatusMapper;
import com.surimap.domain.connectionstatus.AccountHeartbeatEventPublisher;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppSyncHeartbeatService {

  private final AccountConnectionStatusMapper mapper;
  private final AccountHeartbeatEventPublisher eventPublisher;
  private final Clock clock;

  public AppSyncHeartbeatService(
      AccountConnectionStatusMapper mapper,
      AccountHeartbeatEventPublisher eventPublisher,
      Clock clock) {
    this.mapper = mapper;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  @Transactional
  public SyncHeartbeatServiceResponse heartbeat(SyncHeartbeatServiceRequest request) {
    mapper.upsertHeartbeat(
        request.getAccountId(), request.getPolicePhoneId(), clock.instant(), request.getLastSyncAt());
    AccountConnectionStatus status = mapper.findByAccountId(request.getAccountId());
    eventPublisher.publish(status, mapper.findOpenIncidentIdsByAccount(request.getAccountId()));
    return SyncHeartbeatServiceResponse.from(status);
  }
}
