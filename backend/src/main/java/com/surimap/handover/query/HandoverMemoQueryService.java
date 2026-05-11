package com.surimap.handover.query;

import com.surimap.handover.HandoverMemoMapper;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HandoverMemoQueryService implements HandoverMemoQuery {

  private final HandoverMemoMapper mapper;

  public HandoverMemoQueryService(HandoverMemoMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
  }

  @Override
  @Transactional(readOnly = true)
  public List<HandoverMemoRow> byContext(
      UUID incidentId, UUID opId, String targetType, UUID targetId) {
    return mapper.findByContext(incidentId, opId, targetType, targetId);
  }
}
