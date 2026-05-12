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
import com.surimap.operationalperiod.query.CurrentOpResult;
import com.surimap.operationalperiod.query.OperationalPeriodQuery;
import com.surimap.path.SearchPathMapper;
import com.surimap.path.SearchPathPersistenceRecord;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.locationtech.jts.geom.Geometry;
import org.springframework.transaction.annotation.Transactional;

public class AppSearchPathCommandService {

  private final OperationalPeriodQuery opQuery;
  private final PolicePhoneGuard policePhoneGuard;
  private final SearchPathEventPublisher eventPublisher;
  private final SearchPathMapper searchPathMapper;
  private final Map<UUID, SearchPath> activePaths = new ConcurrentHashMap<>();

  public AppSearchPathCommandService(
      OperationalPeriodQuery opQuery,
      PolicePhoneGuard policePhoneGuard,
      SearchPathEventPublisher eventPublisher) {
    this(opQuery, policePhoneGuard, eventPublisher, null);
  }

  public AppSearchPathCommandService(
      OperationalPeriodQuery opQuery,
      PolicePhoneGuard policePhoneGuard,
      SearchPathEventPublisher eventPublisher,
      SearchPathMapper searchPathMapper) {
    this.opQuery = opQuery;
    this.policePhoneGuard = policePhoneGuard;
    this.eventPublisher = eventPublisher;
    this.searchPathMapper = searchPathMapper;
  }

  @Transactional
  public SearchPath start(StartSearchPathServiceRequest request) {
    CurrentOpResult currentOp =
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

    persistStartedPath(path);
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

  @Transactional
  public SearchPath end(
      UUID searchPathId, UUID policePhoneId, EndSearchPathServiceRequest request) {
    SearchPath current = activePaths.get(searchPathId);
    if (current == null) {
      current = loadPersistedPath(searchPathId);
    }
    if (current == null) {
      throw new SearchPathGuardException("write_conflict");
    }
    if (!current.policePhoneId().equals(policePhoneId)) {
      throw new SearchPathGuardException("police_phone_not_assigned");
    }
    policePhoneGuard.requireAssigned(policePhoneId, current.opId());
    return end(current, request);
  }

  @Transactional
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

    persistEndedPath(ended);
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

  private void persistStartedPath(SearchPath path) {
    if (searchPathMapper == null) {
      return;
    }
    UUID dutyShiftId =
        searchPathMapper
            .findActiveDutyShiftId(path.opId(), path.policePhoneId())
            .orElseThrow(() -> new SearchPathGuardException("police_phone_not_assigned"));
    searchPathMapper.insertPath(
        new SearchPathPersistenceRecord(
            path.id(),
            dutyShiftId,
            path.status().name(),
            path.startedAt(),
            path.endedAt(),
            (Geometry) null,
            path.version(),
            path.startedAt(),
            path.startedAt()));
  }

  private void persistEndedPath(SearchPath path) {
    if (searchPathMapper == null || searchPathMapper.findPathById(path.id()).isEmpty()) {
      return;
    }
    Instant endedAt = path.endedAt() == null ? Instant.now() : path.endedAt();
    searchPathMapper.endPath(path.id(), endedAt, path.version(), endedAt);
  }

  private SearchPath loadPersistedPath(UUID searchPathId) {
    if (searchPathMapper == null) {
      return null;
    }
    return searchPathMapper
        .findPathById(searchPathId)
        .map(
            row ->
                new SearchPath(
                    row.id(),
                    row.incidentId(),
                    row.opId(),
                    row.policePhoneId(),
                    SearchPathStatus.valueOf(row.status()),
                    row.version(),
                    row.startedAt(),
                    row.endedAt()))
        .orElse(null);
  }
}
