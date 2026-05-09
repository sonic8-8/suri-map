package com.surimap.sync.idempotency;

import java.util.Optional;

public interface OwnerReplayRecoveryPort {

  Optional<IdempotentWriteResponse> recover(String endpoint, String operationId, String entityId);
}
