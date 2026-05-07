package com.surimap.app.service.path;

import com.surimap.app.service.path.request.EndSearchPathServiceRequest;
import com.surimap.app.service.path.request.StartSearchPathServiceRequest;
import com.surimap.domain.path.SearchPath;
import com.surimap.domain.path.SearchPathEventType;
import com.surimap.domain.path.SearchPathPublishRequest;
import com.surimap.domain.path.SearchPathStatus;
import com.surimap.domain.path.exception.SearchPathGuardException;
import com.surimap.domain.path.port.PolicePhoneGuard;
import com.surimap.domain.path.port.SearchPathEventPublisher;
import com.surimap.operationalperiod.query.OperationalPeriodQuery;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AppSearchPathCommandService {

  private final OperationalPeriodQuery opQuery;
  private final PolicePhoneGuard policePhoneGuard;
  private final SearchPathEventPublisher eventPublisher;
  private final Map<UUID, SearchPath> activePaths = new ConcurrentHashMap<>();

  public AppSearchPathCommandService(
      OperationalPeriodQuery opQuery,
      PolicePhoneGuard policePhoneGuard,
      SearchPathEventPublisher eventPublisher) {
    this.opQuery = opQuery;
    this.policePhoneGuard = policePhoneGuard;
    this.eventPublisher = eventPublisher;
  }

  public SearchPath start(StartSearchPathServiceRequest request) {
    OperationalPeriodRow currentOp =
        opQuery
            .current(request.incidentId())
            .orElseThrow(() -> new SearchPathGuardException("op_required"));
    if (!currentOp.opId().equals(request.opId())) {
      throw new SearchPathGuardException("op_mismatch");
    }
    policePhoneGuard.requireAssigned(request.policePhoneId(), request.opId());

    SearchPath path =
        new SearchPath(
            UUID.randomUUID(),
            request.incidentId(),
            request.opId(),
            request.policePhoneId(),
            SearchPathStatus.RECORDING,
            1L,
            request.startedAt(),
            null);

    eventPublisher.publish(
        new SearchPathPublishRequest(
            SearchPathEventType.SEARCH_PATH_STARTED,
            path.id(),
            path.opId(),
            path.policePhoneId(),
            path.status(),
            path.version()));
    activePaths.put(path.id(), path);

    return path;
  }

  public SearchPath end(
      UUID searchPathId, UUID policePhoneId, EndSearchPathServiceRequest request) {
    SearchPath current = activePaths.get(searchPathId);
    if (current == null) {
      throw new SearchPathGuardException("write_conflict");
    }
    if (!current.policePhoneId().equals(policePhoneId)) {
      throw new SearchPathGuardException("police_phone_not_assigned");
    }
    policePhoneGuard.requireAssigned(policePhoneId, current.opId());
    return end(current, request);
  }

  public SearchPath end(SearchPath current, EndSearchPathServiceRequest request) {
    if (current.status() == SearchPathStatus.ENDED) {
      throw new SearchPathGuardException("write_conflict");
    }
    SearchPath ended =
        new SearchPath(
            current.id(),
            current.incidentId(),
            current.opId(),
            current.policePhoneId(),
            SearchPathStatus.ENDED,
            current.version() + 1,
            current.startedAt(),
            request.endedAt());

    eventPublisher.publish(
        new SearchPathPublishRequest(
            SearchPathEventType.SEARCH_PATH_ENDED,
            ended.id(),
            ended.opId(),
            ended.policePhoneId(),
            ended.status(),
            ended.version()));
    activePaths.put(ended.id(), ended);

    return ended;
  }
}
