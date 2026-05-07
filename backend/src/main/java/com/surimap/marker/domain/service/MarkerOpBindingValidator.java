package com.surimap.marker.domain.service;

import com.surimap.marker.domain.exception.OpMismatchException;
import com.surimap.marker.domain.exception.OpRequiredException;
import com.surimap.marker.domain.port.OperationalPeriodQueryPort;
import java.util.Objects;
import java.util.UUID;

/** Marker write의 request.opId가 S8 current OP와 일치하는지 검증한다. */
public class MarkerOpBindingValidator {

  private final OperationalPeriodQueryPort operationalPeriodQuery;

  public MarkerOpBindingValidator(OperationalPeriodQueryPort operationalPeriodQuery) {
    this.operationalPeriodQuery =
        Objects.requireNonNull(operationalPeriodQuery, "operationalPeriodQuery");
  }

  public UUID validate(UUID incidentId, UUID requestedOpId) {
    if (requestedOpId == null) {
      throw new OpRequiredException();
    }

    UUID currentOpId =
        operationalPeriodQuery.findCurrentOpId(incidentId).orElseThrow(OpRequiredException::new);
    if (!currentOpId.equals(requestedOpId)) {
      throw new OpMismatchException(requestedOpId, currentOpId);
    }
    return currentOpId;
  }
}
