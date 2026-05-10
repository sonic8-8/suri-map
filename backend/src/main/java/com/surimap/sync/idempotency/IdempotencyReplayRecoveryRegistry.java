package com.surimap.sync.idempotency;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyReplayRecoveryRegistry implements OwnerReplayRecoveryPort {

  private final Map<String, OwnerReplayRecoveryPort> portsByEndpoint = new ConcurrentHashMap<>();

  public void register(String endpoint, OwnerReplayRecoveryPort port) {
    portsByEndpoint.put(endpoint, port);
  }

  @Override
  public Optional<IdempotentWriteResponse> recover(
      String endpoint, String operationId, String entityId) {
    OwnerReplayRecoveryPort port = portsByEndpoint.get(endpoint);
    if (port == null) {
      return Optional.empty();
    }
    return port.recover(endpoint, operationId, entityId);
  }
}
