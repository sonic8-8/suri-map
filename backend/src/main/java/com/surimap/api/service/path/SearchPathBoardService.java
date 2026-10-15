package com.surimap.api.service.path;

import com.surimap.api.service.path.request.SearchPathPageServiceRequest;
import com.surimap.api.service.path.request.SearchPathPageServiceRequest.PathProgress;
import com.surimap.api.service.path.response.SearchPathPageServiceResponse;
import com.surimap.api.service.path.response.SearchPathPageServiceResponse.Path;
import com.surimap.api.service.path.response.SearchPathPageServiceResponse.Segment;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.domain.path.SearchPathBoardPoint;
import com.surimap.domain.path.SearchPathBoardSegmentQuery;
import com.surimap.domain.path.SearchPath;
import com.surimap.domain.path.SearchPathMapper;
import com.surimap.domain.path.SearchPathSegment;
import com.surimap.domain.path.validation.GpsPointValidationCriteria;
import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import com.surimap.incident.repository.IncidentReadMapper;
import com.surimap.retention.purge.IncidentDataPurgeStatus;
import com.surimap.retention.purge.PurgeRunMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SearchPathBoardService {
  private final IncidentReadMapper incidentReadMapper;
  private final PurgeRunMapper purgeRunMapper;
  private final SearchPathMapper searchPathMapper;

  // 좌표 범위 하나에 바인딩 인자 3개를 쓴다. PostgreSQL JDBC 한 문장의 상한 32,767개 안에 둔다.
  private static final int MAX_POINT_RANGES = 5000;

  // 0은 미설정이다. 측정 전 임의 기본값으로 새 조회를 활성화하지 않는다.
  @Value("${surimap.board.search-path.max-paths:0}")
  private int maxPaths;

  @Value("${surimap.board.search-path.max-segments:0}")
  private int maxSegments;

  @Value("${surimap.board.search-path.max-coordinates:0}")
  private int maxCoordinates;

  @Value("${surimap.board.search-path.segments-per-path:0}")
  private int segmentsPerPath;

  @Value("${surimap.board.search-path.max-known-paths:0}")
  private int maxKnownPaths;

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public SearchPathPageServiceResponse getSearchPathSegments(SearchPathPageServiceRequest request) {
    return queryPage(request, false);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public SearchPathPageServiceResponse getSearchPathChanges(SearchPathPageServiceRequest request) {
    return queryPage(request, true);
  }

  private SearchPathPageServiceResponse queryPage(
      SearchPathPageServiceRequest request, boolean changesQuery) {
    validateAccess(request);
    validateRequest(request);
    List<SearchPath> paths =
        searchPathMapper.findBoardPathMetadata(
            request.getIncidentId(), request.getOpIds(), maxKnownPaths + 1);
    if (paths.size() > maxKnownPaths) {
      throw new BusinessException(ErrorCode.INVALID_SEARCH_PATH_QUERY);
    }
    Map<UUID, PathProgress> progress = validateProgress(request, paths, changesQuery);
    if (searchPathMapper.hasUnpreparedBoardPaths(request.getIncidentId(), request.getOpIds())) {
      throw new BusinessException(ErrorCode.SEARCH_PATH_QUERY_NOT_READY);
    }
    List<SearchPath> ordered = rotatePaths(paths, request.getNextSearchPathId());
    Map<UUID, List<SearchPathSegment>> candidatesByPath =
        findCandidateSegments(ordered, progress, changesQuery);
    List<PendingPath> pending = new ArrayList<>();
    int usedSegments = 0;
    long usedCoordinates = 0;
    UUID nextId = null;
    for (SearchPath path : ordered) {
      PathProgress previousProgress = progress.get(path.getId());
      if (!needsPage(path, previousProgress, changesQuery)) {
        continue;
      }
      if (pending.size() >= maxPaths || usedSegments >= maxSegments) {
        nextId = path.getId();
        break;
      }
      if (changesQuery
          && (previousProgress == null || previousProgress.getBaselineVersion() == null)) {
        pending.add(new PendingPath(path, PagePlan.undiscovered(), null, List.of()));
        progress.put(path.getId(), PathProgress.builder().id(path.getId()).build());
        continue;
      }
      PagePlan plan = planPage(path, previousProgress, changesQuery);
      Integer beforeStartPointOrder = plan.beforeStartPointOrder;
      Long appliedVersion = plan.appliedVersion;
      int available = Math.min(segmentsPerPath, maxSegments - usedSegments);
      // 경로마다 받은 후보는 원래 LIMIT available + 1 조회와 같은 순서·앞부분이다.
      List<SearchPathSegment> allCandidates = candidatesByPath.getOrDefault(path.getId(), List.of());
      List<SearchPathSegment> candidates =
          allCandidates.subList(0, Math.min(allCandidates.size(), available + 1));
      List<SearchPathSegment> segments = new ArrayList<>();
      for (SearchPathSegment segment : candidates) {
        if (segments.size() == available) {
          break;
        }
        long coordinates = (long) segment.getEndIndex() - segment.getStartIndex() + 1;
        if (coordinates > maxCoordinates) {
          // 방문 순서를 지켜, 앞서 고른 구간의 좌표 누락을 크기 오류보다 먼저 알린다.
          List<PendingPath> visited = new ArrayList<>(pending);
          visited.add(new PendingPath(path, plan, false, segments));
          readPendingPaths(visited);
          throw new BusinessException(ErrorCode.SEARCH_PATH_SEGMENT_TOO_LARGE);
        }
        if (usedCoordinates + coordinates > maxCoordinates) {
          break;
        }
        segments.add(segment);
        usedCoordinates += coordinates;
        usedSegments++;
        beforeStartPointOrder = segment.getStartIndex();
      }
      if (!candidates.isEmpty() && segments.isEmpty()) {
        nextId = path.getId();
        break;
      }
      boolean completed = candidates.size() == segments.size();
      if (changesQuery && completed) {
        appliedVersion = plan.targetVersion;
      }
      PagePlan updated = plan.after(appliedVersion, beforeStartPointOrder);
      pending.add(new PendingPath(path, updated, completed, segments));
      progress.put(
          path.getId(),
          PathProgress.builder()
              .id(path.getId())
              .baselineVersion(updated.baselineVersion)
              .appliedVersion(updated.appliedVersion)
              .targetVersion(updated.targetVersion)
              .beforeStartPointOrder(updated.beforeStartPointOrder)
              .completed(completed)
              .build());
    }
    List<Path> result = readPendingPaths(pending);
    boolean hasMore =
        paths.stream().anyMatch(path -> needsPage(path, progress.get(path.getId()), changesQuery));
    if (hasMore && nextId == null) {
      nextId =
          ordered.stream()
              .filter(path -> needsPage(path, progress.get(path.getId()), changesQuery))
              .map(SearchPath::getId)
              .findFirst()
              .orElse(null);
    }
    return SearchPathPageServiceResponse.builder()
        .paths(result)
        .nextSearchPathId(nextId)
        .hasMore(hasMore)
        .build();
  }

  private void validateAccess(SearchPathPageServiceRequest request) {
    if (request.getAuthentication() == null
        || request.getAuthentication().getChannel() != Channel.WEB) {
      throw new BusinessException(ErrorCode.CHANNEL_NOT_ALLOWED);
    }
    SuriMapAuthentication authentication = request.getAuthentication();
    UUID incidentId = request.getIncidentId();
    boolean commandAccount = authentication.getAccountType() == AccountType.COMMAND;
    boolean active;
    boolean terminal;
    // 기존 사건 조회와 같은 조직/계정 범위를 사용하되 실종자·배정 목록은 조립하지 않는다.
    if (commandAccount) {
      active =
          incidentReadMapper
              .findActiveDetailByIncidentIdAndOrganizationType(
                  incidentId, authentication.getOrganizationType().name())
              .isPresent();
      terminal =
          !active
              && incidentReadMapper
                  .findTerminalDetailByIncidentIdAndOrganizationType(
                      incidentId, authentication.getOrganizationType().name())
                  .isPresent();
    } else {
      UUID accountId = UUID.fromString(authentication.getAccountId());
      active =
          incidentReadMapper
              .findActiveDetailByIncidentIdAndAccountId(incidentId, accountId)
              .isPresent();
      terminal =
          !active
              && incidentReadMapper
                  .findTerminalDetailByIncidentIdAndAccountId(incidentId, accountId)
                  .isPresent();
    }
    if (!active && !terminal) {
      throw new BusinessException(ErrorCode.INCIDENT_ACCESS_DENIED);
    }
    if (terminal
        || purgeRunMapper
            .findByIncidentId(request.getIncidentId())
            .filter(run -> run.getStatus() == IncidentDataPurgeStatus.COMPLETED)
            .isPresent()) {
      throw new BusinessException(ErrorCode.INCIDENT_CLOSED);
    }
  }

  private void validateRequest(SearchPathPageServiceRequest request) {
    if (maxPaths <= 0
        || maxSegments <= 0
        || maxCoordinates < GpsPointValidationCriteria.MAX_POINTS_PER_BATCH
        || segmentsPerPath <= 0
        || maxKnownPaths <= 0
        || maxKnownPaths == Integer.MAX_VALUE
        || segmentsPerPath == Integer.MAX_VALUE) {
      throw new BusinessException(ErrorCode.SEARCH_PATH_QUERY_NOT_READY);
    }
    if (request.getOpIds() == null
        || request.getPaths() == null
        || request.getOpIds().size() > maxKnownPaths
        || request.getPaths().size() > maxKnownPaths
        || request.getOpIds().stream().anyMatch(java.util.Objects::isNull)
        || new HashSet<>(request.getOpIds()).size() != request.getOpIds().size()) {
      throw new BusinessException(ErrorCode.INVALID_SEARCH_PATH_QUERY);
    }
  }

  private Map<UUID, PathProgress> validateProgress(
      SearchPathPageServiceRequest request, List<SearchPath> paths, boolean changesQuery) {
    Map<UUID, SearchPath> byId = new HashMap<>();
    paths.forEach(path -> byId.put(path.getId(), path));
    Map<UUID, PathProgress> progress = new HashMap<>();
    List<SearchPathSegment> cursors = new ArrayList<>();
    for (PathProgress item : request.getPaths()) {
      if (item == null
          || item.getId() == null
          || !byId.containsKey(item.getId())
          || progress.putIfAbsent(item.getId(), item) != null) {
        throw new BusinessException(ErrorCode.INVALID_SEARCH_PATH_QUERY);
      }
      long currentVersion = byId.get(item.getId()).getVersion();
      if (!isValidVersion(item.getBaselineVersion(), currentVersion)
          || !isValidVersion(item.getAppliedVersion(), currentVersion)
          || !isValidVersion(item.getTargetVersion(), currentVersion)
          || (item.getBeforeStartPointOrder() != null && item.getBeforeStartPointOrder() < 0)) {
        throw new BusinessException(ErrorCode.INVALID_SEARCH_PATH_QUERY);
      }
      if (item.getCompleted() == null) {
        if (item.getBeforeStartPointOrder() != null
            || item.getAppliedVersion() != null
            || item.getTargetVersion() != null) {
          throw new BusinessException(ErrorCode.INVALID_SEARCH_PATH_QUERY);
        }
      } else if (changesQuery) {
        Long baselineVersion = item.getBaselineVersion();
        Long appliedVersion = item.getAppliedVersion();
        Long targetVersion = item.getTargetVersion();
        if (baselineVersion == null
            || targetVersion == null
            || (appliedVersion != null && appliedVersion < baselineVersion)
            || targetVersion < (appliedVersion == null ? baselineVersion : appliedVersion)
            || (item.getCompleted() && !targetVersion.equals(appliedVersion))) {
          throw new BusinessException(ErrorCode.INVALID_SEARCH_PATH_QUERY);
        }
      } else if (item.getBaselineVersion() != null
          || item.getAppliedVersion() != null
          || item.getTargetVersion() != null) {
        throw new BusinessException(ErrorCode.INVALID_SEARCH_PATH_QUERY);
      }
      if (item.getBeforeStartPointOrder() != null) {
        cursors.add(
            SearchPathSegment.builder()
                .searchPathId(item.getId())
                .startIndex(item.getBeforeStartPointOrder())
                .build());
      }
    }
    if (!cursors.isEmpty() && searchPathMapper.hasInvalidBoardCursors(cursors)) {
      throw new BusinessException(ErrorCode.INVALID_SEARCH_PATH_QUERY);
    }
    return progress;
  }

  private Map<UUID, List<SearchPathSegment>> findCandidateSegments(
      List<SearchPath> ordered, Map<UUID, PathProgress> progress, boolean changesQuery) {
    // 한 페이지가 방문할 수 있는 경로는 필요한 경로 중 앞의 maxPaths개다.
    int limit = Math.min(segmentsPerPath, maxSegments) + 1;
    List<SearchPathBoardSegmentQuery> queries =
        ordered.stream()
            .filter(path -> needsPage(path, progress.get(path.getId()), changesQuery))
            .limit(maxPaths)
            .filter(
                path ->
                    !changesQuery
                        || (progress.get(path.getId()) != null
                            && progress.get(path.getId()).getBaselineVersion() != null))
            .map(
                path -> {
                  PagePlan plan = planPage(path, progress.get(path.getId()), changesQuery);
                  return SearchPathBoardSegmentQuery.builder()
                      .pathId(path.getId())
                      .before(plan.beforeStartPointOrder)
                      .lowerVersion(plan.lowerVersion)
                      .upperVersion(plan.lowerVersion == null ? null : plan.targetVersion)
                      .limit(limit)
                      .build();
                })
            .toList();
    Map<UUID, List<SearchPathSegment>> result = new HashMap<>();
    if (queries.isEmpty()) {
      return result;
    }
    for (SearchPathSegment segment : searchPathMapper.findBoardSegments(queries)) {
      result.computeIfAbsent(segment.getSearchPathId(), id -> new ArrayList<>()).add(segment);
    }
    return result;
  }

  private PagePlan planPage(SearchPath path, PathProgress previousProgress, boolean changesQuery) {
    Long baselineVersion = null;
    Long appliedVersion = null;
    Long targetVersion = null;
    Long lowerVersion = null;
    Integer beforeStartPointOrder =
        previousProgress == null ? null : previousProgress.getBeforeStartPointOrder();
    if (changesQuery) {
      baselineVersion = previousProgress.getBaselineVersion();
      appliedVersion = previousProgress.getAppliedVersion();
      lowerVersion = appliedVersion == null ? baselineVersion : appliedVersion;
      if (previousProgress.getCompleted() == null || previousProgress.getCompleted()) {
        targetVersion = path.getVersion();
        beforeStartPointOrder = null;
      } else {
        targetVersion = previousProgress.getTargetVersion();
      }
    } else if (previousProgress == null || previousProgress.getCompleted() == null) {
      baselineVersion = path.getVersion();
    }
    return new PagePlan(
        baselineVersion, appliedVersion, targetVersion, lowerVersion, beforeStartPointOrder);
  }

  private List<Path> readPendingPaths(List<PendingPath> pending) {
    // 응답에 담을 구간이 정해진 뒤, 이어지는 구간의 순번 범위로 좌표를 한 번에 읽는다.
    Map<UUID, List<SearchPathBoardPoint>> pointsByPath = new HashMap<>();
    List<SearchPathSegment> ranges = pointRanges(pending);
    if (!ranges.isEmpty()) {
      for (SearchPathBoardPoint point : searchPathMapper.findBoardPoints(ranges)) {
        pointsByPath.computeIfAbsent(point.getSearchPathId(), id -> new ArrayList<>()).add(point);
      }
    }
    List<Path> result = new ArrayList<>();
    for (PendingPath entry : pending) {
      List<SearchPathBoardPoint> points = pointsByPath.getOrDefault(entry.path.getId(), List.of());
      List<Segment> segments =
          entry.segments.stream().map(segment -> readSegment(segment, points)).toList();
      PagePlan plan = entry.plan;
      result.add(
          createPathResponse(
              entry.path,
              plan.baselineVersion,
              plan.appliedVersion,
              plan.targetVersion,
              plan.beforeStartPointOrder,
              entry.completed,
              segments));
    }
    return result;
  }

  private boolean isValidVersion(Long version, long currentVersion) {
    return version == null || (version >= 1 && version <= currentVersion);
  }

  private List<SearchPath> rotatePaths(List<SearchPath> paths, UUID nextId) {
    if (nextId == null) {
      return paths;
    }
    List<SearchPath> result = new ArrayList<>();
    paths.stream()
        .filter(path -> path.getId().toString().compareTo(nextId.toString()) >= 0)
        .forEach(result::add);
    paths.stream()
        .filter(path -> path.getId().toString().compareTo(nextId.toString()) < 0)
        .forEach(result::add);
    return result;
  }

  private boolean needsPage(SearchPath path, PathProgress progress, boolean changesQuery) {
    if (progress == null) {
      return true;
    }
    if (changesQuery && progress.getBaselineVersion() == null) {
      return false;
    }
    // 기준 버전 이후 바뀌지 않은 경로는 진행 정보를 채우려고 응답 한도를 쓰지 않는다.
    if (changesQuery
        && progress.getCompleted() == null
        && progress.getBaselineVersion() == path.getVersion()) {
      return false;
    }
    if (progress.getCompleted() == null || !progress.getCompleted()) {
      return true;
    }
    return changesQuery && progress.getAppliedVersion() < path.getVersion();
  }

  private Path createPathResponse(
      SearchPath path,
      Long baselineVersion,
      Long appliedVersion,
      Long targetVersion,
      Integer beforeStartPointOrder,
      Boolean completed,
      List<Segment> segments) {
    return Path.builder()
        .id(path.getId())
        .accountId(path.getAccountId())
        .opId(path.getOpId())
        .status(path.getStatus())
        .version(path.getVersion())
        .baselineVersion(baselineVersion)
        .appliedVersion(appliedVersion)
        .targetVersion(targetVersion)
        .beforeStartPointOrder(beforeStartPointOrder)
        .completed(completed)
        .segments(segments)
        .build();
  }

  private List<SearchPathSegment> pointRanges(List<PendingPath> pending) {
    // 떨어진 변경 구간 사이의 과거 좌표는 읽지 않는다. 범위가 많으면 바인딩 상한을 지키려고 경로별로 합친다.
    Map<UUID, List<SearchPathSegment>> segmentsByPath = new LinkedHashMap<>();
    for (PendingPath entry : pending) {
      segmentsByPath
          .computeIfAbsent(entry.path.getId(), id -> new ArrayList<>())
          .addAll(entry.segments);
    }
    List<SearchPathSegment> runs = new ArrayList<>();
    List<SearchPathSegment> wholePaths = new ArrayList<>();
    segmentsByPath.forEach(
        (pathId, segments) -> {
          if (segments.isEmpty()) {
            return;
          }
          List<SearchPathSegment> sorted =
              segments.stream()
                  .sorted(Comparator.comparingInt(SearchPathSegment::getStartIndex))
                  .toList();
          int start = sorted.get(0).getStartIndex();
          int end = sorted.get(0).getEndIndex();
          for (SearchPathSegment segment : sorted.subList(1, sorted.size())) {
            if (segment.getStartIndex() > end + 1) {
              runs.add(pointRange(pathId, start, end));
              start = segment.getStartIndex();
            }
            end = Math.max(end, segment.getEndIndex());
          }
          runs.add(pointRange(pathId, start, end));
          wholePaths.add(pointRange(pathId, sorted.get(0).getStartIndex(), end));
        });
    return runs.size() <= MAX_POINT_RANGES ? runs : wholePaths;
  }

  private SearchPathSegment pointRange(UUID pathId, int start, int end) {
    return SearchPathSegment.builder().searchPathId(pathId).startIndex(start).endIndex(end).build();
  }

  private Segment readSegment(SearchPathSegment segment, List<SearchPathBoardPoint> pathPoints) {
    int first = firstPointIndex(pathPoints, segment.getStartIndex());
    int count = segment.getEndIndex() - segment.getStartIndex() + 1;
    if (first < 0 || first + count > pathPoints.size()) {
      throw new BusinessException(ErrorCode.SEARCH_PATH_QUERY_NOT_READY);
    }
    List<SearchPathBoardPoint> points = pathPoints.subList(first, first + count);
    for (int i = 0; i < count; i++) {
      if (points.get(i).getPointOrder() != segment.getStartIndex() + i) {
        throw new BusinessException(ErrorCode.SEARCH_PATH_QUERY_NOT_READY);
      }
    }
    return Segment.builder()
        .id(segment.getId())
        .version(segment.getVersion())
        .startPointOrder(segment.getStartIndex())
        .endPointOrder(segment.getEndIndex())
        .movementType(segment.getMovementType())
        .coordinates(
            points.stream()
                .map(point -> List.of(point.getLon().doubleValue(), point.getLat().doubleValue()))
                .toList())
        .startedAt(points.get(0).getClientTs().toInstant())
        .endedAt(points.get(points.size() - 1).getClientTs().toInstant())
        .build();
  }

  private int firstPointIndex(List<SearchPathBoardPoint> points, int pointOrder) {
    int low = 0;
    int high = points.size() - 1;
    while (low <= high) {
      int middle = (low + high) >>> 1;
      int order = points.get(middle).getPointOrder();
      if (order < pointOrder) {
        low = middle + 1;
      } else if (order > pointOrder) {
        high = middle - 1;
      } else {
        return middle;
      }
    }
    return -1;
  }

  private static final class PagePlan {
    private final Long baselineVersion;
    private final Long appliedVersion;
    private final Long targetVersion;
    private final Long lowerVersion;
    private final Integer beforeStartPointOrder;

    private PagePlan(
        Long baselineVersion,
        Long appliedVersion,
        Long targetVersion,
        Long lowerVersion,
        Integer beforeStartPointOrder) {
      this.baselineVersion = baselineVersion;
      this.appliedVersion = appliedVersion;
      this.targetVersion = targetVersion;
      this.lowerVersion = lowerVersion;
      this.beforeStartPointOrder = beforeStartPointOrder;
    }

    private static PagePlan undiscovered() {
      return new PagePlan(null, null, null, null, null);
    }

    private PagePlan after(Long appliedVersion, Integer beforeStartPointOrder) {
      return new PagePlan(
          baselineVersion, appliedVersion, targetVersion, lowerVersion, beforeStartPointOrder);
    }
  }

  private static final class PendingPath {
    private final SearchPath path;
    private final PagePlan plan;
    private final Boolean completed;
    private final List<SearchPathSegment> segments;

    private PendingPath(
        SearchPath path, PagePlan plan, Boolean completed, List<SearchPathSegment> segments) {
      this.path = path;
      this.plan = plan;
      this.completed = completed;
      this.segments = segments;
    }
  }
}
