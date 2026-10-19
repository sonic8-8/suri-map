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
import com.surimap.domain.path.SearchPathBoardPointRanges;
import com.surimap.domain.path.SearchPathBoardPointRun;
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
import java.util.stream.IntStream;
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
    List<SearchPath> paths = findKnownPaths(request);
    Map<UUID, PathProgress> progress = validateProgress(request, paths, changesQuery);
    validatePathsPrepared(request);
    List<SearchPath> ordered = rotatePaths(paths, request.getNextSearchPathId());
    PageSelection selection = selectPage(ordered, progress, changesQuery);
    List<Path> result = readPendingPaths(selection.pending);
    boolean hasMore =
        paths.stream().anyMatch(path -> needsPage(path, progress.get(path.getId()), changesQuery));
    UUID nextId = selection.nextId;
    if (hasMore && nextId == null) {
      nextId = firstPathNeedingPage(ordered, progress, changesQuery);
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

  private List<SearchPath> findKnownPaths(SearchPathPageServiceRequest request) {
    List<SearchPath> paths =
        searchPathMapper.findBoardPathMetadata(
            request.getIncidentId(), request.getOpIds(), maxKnownPaths + 1);
    if (paths.size() > maxKnownPaths) {
      throw new BusinessException(ErrorCode.INVALID_SEARCH_PATH_QUERY);
    }
    return paths;
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

  private boolean isValidVersion(Long version, long currentVersion) {
    return version == null || (version >= 1 && version <= currentVersion);
  }

  private void validatePathsPrepared(SearchPathPageServiceRequest request) {
    if (searchPathMapper.hasUnpreparedBoardPaths(request.getIncidentId(), request.getOpIds())) {
      throw new BusinessException(ErrorCode.SEARCH_PATH_QUERY_NOT_READY);
    }
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

  // 응답 한도 안에서 이번 페이지에 담을 경로와 구간을 고르고, 고른 경로의 진행 정보를 progress에 반영한다.
  private PageSelection selectPage(
      List<SearchPath> ordered, Map<UUID, PathProgress> progress, boolean changesQuery) {
    Map<UUID, List<SearchPathSegment>> candidatesByPath =
        findCandidateSegments(ordered, progress, changesQuery);
    PageSelection selection = new PageSelection();
    for (SearchPath path : ordered) {
      PathProgress previousProgress = progress.get(path.getId());
      if (!needsPage(path, previousProgress, changesQuery)) {
        continue;
      }
      if (selection.pending.size() >= maxPaths || selection.usedSegments >= maxSegments) {
        selection.nextId = path.getId();
        break;
      }
      PendingPath entry;
      if (changesQuery
          && (previousProgress == null || previousProgress.getBaselineVersion() == null)) {
        entry = new PendingPath(path, PagePlan.undiscovered(), null, List.of());
      } else {
        PagePlan plan = planPage(path, previousProgress, changesQuery);
        int available = Math.min(segmentsPerPath, maxSegments - selection.usedSegments);
        // 경로마다 받은 후보는 원래 LIMIT available + 1 조회와 순서와 앞부분이 같다.
        List<SearchPathSegment> allCandidates =
            candidatesByPath.getOrDefault(path.getId(), List.of());
        List<SearchPathSegment> candidates =
            allCandidates.subList(0, Math.min(allCandidates.size(), available + 1));
        List<SearchPathSegment> segments =
            selectSegments(
                path,
                plan,
                candidates,
                available,
                maxCoordinates - selection.usedCoordinates,
                selection.pending);
        if (!candidates.isEmpty() && segments.isEmpty()) {
          selection.nextId = path.getId();
          break;
        }
        entry = toPendingPath(path, plan, candidates, segments, changesQuery);
      }
      selection.add(entry);
      progress.put(path.getId(), toProgress(entry));
    }
    return selection;
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

  private List<SearchPathSegment> selectSegments(
      SearchPath path,
      PagePlan plan,
      List<SearchPathSegment> candidates,
      int available,
      long remainingCoordinates,
      List<PendingPath> visited) {
    List<SearchPathSegment> segments = new ArrayList<>();
    for (SearchPathSegment segment : candidates) {
      if (segments.size() == available) {
        break;
      }
      long coordinates = coordinateCount(segment);
      if (coordinates > maxCoordinates) {
        // 방문 순서를 지켜, 앞서 고른 구간의 좌표 누락을 크기 오류보다 먼저 알린다.
        List<PendingPath> visitedWithCurrent = new ArrayList<>(visited);
        visitedWithCurrent.add(new PendingPath(path, plan, false, segments));
        readPendingPaths(visitedWithCurrent);
        throw new BusinessException(ErrorCode.SEARCH_PATH_SEGMENT_TOO_LARGE);
      }
      if (coordinates > remainingCoordinates) {
        break;
      }
      segments.add(segment);
      remainingCoordinates -= coordinates;
    }
    return segments;
  }

  private static long coordinateCount(SearchPathSegment segment) {
    return (long) segment.getEndIndex() - segment.getStartIndex() + 1;
  }

  private List<Path> readPendingPaths(List<PendingPath> pending) {
    // 응답에 담을 구간이 정해진 뒤, 이어지는 구간의 순번 범위마다 좌표를 배열로 한 번에 읽는다.
    List<SearchPathSegment> ranges = pointRanges(pending);
    Map<UUID, List<SearchPathBoardPointRun>> runsByPath = new HashMap<>();
    for (SearchPathBoardPointRun run : readPointRuns(ranges)) {
      UUID pathId = ranges.get(run.getRangeOrder() - 1).getSearchPathId();
      runsByPath.computeIfAbsent(pathId, id -> new ArrayList<>()).add(run);
    }
    List<Path> result = new ArrayList<>();
    for (PendingPath entry : pending) {
      List<SearchPathBoardPointRun> runs = runsByPath.getOrDefault(entry.path.getId(), List.of());
      List<Segment> segments =
          entry.segments.stream().map(segment -> readSegment(segment, runs)).toList();
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

  private List<SearchPathSegment> pointRanges(List<PendingPath> pending) {
    // 떨어진 변경 구간 사이의 과거 좌표는 읽지 않는다. 경로마다 순번이 이어지는 구간끼리만 범위로 합친다.
    Map<UUID, List<SearchPathSegment>> segmentsByPath = new LinkedHashMap<>();
    for (PendingPath entry : pending) {
      segmentsByPath
          .computeIfAbsent(entry.path.getId(), id -> new ArrayList<>())
          .addAll(entry.segments);
    }
    List<SearchPathSegment> runs = new ArrayList<>();
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
            if (segment.getStartIndex() > (long) end + 1) {
              runs.add(pointRange(pathId, start, end));
              start = segment.getStartIndex();
            }
            end = Math.max(end, segment.getEndIndex());
          }
          runs.add(pointRange(pathId, start, end));
        });
    return runs;
  }

  private SearchPathSegment pointRange(UUID pathId, int start, int end) {
    return SearchPathSegment.builder().searchPathId(pathId).startIndex(start).endIndex(end).build();
  }

  private List<SearchPathBoardPointRun> readPointRuns(List<SearchPathSegment> ranges) {
    if (ranges.isEmpty()) {
      return List.of();
    }
    List<SearchPathBoardPointRun> runs =
        new ArrayList<>(
            searchPathMapper.findBoardPointRuns(SearchPathBoardPointRanges.from(ranges)));
    replaceOutOfRangeRuns(ranges, runs);
    // 좌표가 없는 범위는 행이 없다. 범위마다 좌표가 빠짐없어야 구간 좌표를 채울 수 있다.
    if (runs.size() != ranges.size()) {
      throw new BusinessException(ErrorCode.SEARCH_PATH_QUERY_NOT_READY);
    }
    for (SearchPathBoardPointRun run : runs) {
      if (!run.hasAllPointsThrough(ranges.get(run.getRangeOrder() - 1).getEndIndex())) {
        throw new BusinessException(ErrorCode.SEARCH_PATH_QUERY_NOT_READY);
      }
    }
    return runs;
  }

  private void replaceOutOfRangeRuns(
      List<SearchPathSegment> ranges, List<SearchPathBoardPointRun> runs) {
    // 시각 배열은 294247-01-01 이상 시각과 무한 시각을 담지 못한다. 그런 범위만 좌표마다 다시 읽는다.
    List<Integer> positions = new ArrayList<>();
    for (int i = 0; i < runs.size(); i++) {
      if (runs.get(i).isHasOutOfRangeClientTs()) {
        positions.add(i);
      }
    }
    if (positions.isEmpty()) {
      return;
    }
    List<SearchPathSegment> rereadRanges =
        positions.stream().map(i -> ranges.get(runs.get(i).getRangeOrder() - 1)).toList();
    Map<Integer, List<SearchPathBoardPoint>> pointsByRange = new HashMap<>();
    for (SearchPathBoardPoint point :
        searchPathMapper.findBoardPoints(SearchPathBoardPointRanges.from(rereadRanges))) {
      pointsByRange.computeIfAbsent(point.getRangeOrder(), order -> new ArrayList<>()).add(point);
    }
    for (int k = 0; k < positions.size(); k++) {
      SearchPathBoardPointRun run = runs.get(positions.get(k));
      runs.set(
          positions.get(k),
          SearchPathBoardPointRun.fromPoints(
              run.getRangeOrder(),
              run.getStartPointOrder(),
              pointsByRange.getOrDefault(k + 1, List.of())));
    }
  }

  private Segment readSegment(SearchPathSegment segment, List<SearchPathBoardPointRun> pathRuns) {
    SearchPathBoardPointRun run = findRun(pathRuns, segment.getStartIndex());
    if (run == null || !run.contains(segment.getStartIndex(), segment.getEndIndex())) {
      throw new BusinessException(ErrorCode.SEARCH_PATH_QUERY_NOT_READY);
    }
    int first = segment.getStartIndex() - run.getStartPointOrder();
    int last = segment.getEndIndex() - run.getStartPointOrder();
    Double[] lons = run.getLons();
    Double[] lats = run.getLats();
    return Segment.builder()
        .id(segment.getId())
        .version(segment.getVersion())
        .startPointOrder(segment.getStartIndex())
        .endPointOrder(segment.getEndIndex())
        .movementType(segment.getMovementType())
        .coordinates(
            IntStream.rangeClosed(first, last).mapToObj(i -> List.of(lons[i], lats[i])).toList())
        .startedAt(run.getClientTs(first))
        .endedAt(run.getClientTs(last))
        .build();
  }

  private SearchPathBoardPointRun findRun(List<SearchPathBoardPointRun> runs, int pointOrder) {
    // 경로의 범위는 시작 순번 오름차순이다. 순번을 포함할 수 있는 마지막 범위를 찾는다.
    int low = 0;
    int high = runs.size() - 1;
    SearchPathBoardPointRun found = null;
    while (low <= high) {
      int middle = (low + high) >>> 1;
      if (runs.get(middle).getStartPointOrder() <= pointOrder) {
        found = runs.get(middle);
        low = middle + 1;
      } else {
        high = middle - 1;
      }
    }
    return found;
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

  private PendingPath toPendingPath(
      SearchPath path,
      PagePlan plan,
      List<SearchPathSegment> candidates,
      List<SearchPathSegment> segments,
      boolean changesQuery) {
    boolean completed = candidates.size() == segments.size();
    Long appliedVersion = changesQuery && completed ? plan.targetVersion : plan.appliedVersion;
    Integer beforeStartPointOrder = plan.beforeStartPointOrder;
    if (!segments.isEmpty()) {
      beforeStartPointOrder = segments.get(segments.size() - 1).getStartIndex();
    }
    return new PendingPath(
        path, plan.after(appliedVersion, beforeStartPointOrder), completed, segments);
  }

  private PathProgress toProgress(PendingPath entry) {
    return PathProgress.builder()
        .id(entry.path.getId())
        .baselineVersion(entry.plan.baselineVersion)
        .appliedVersion(entry.plan.appliedVersion)
        .targetVersion(entry.plan.targetVersion)
        .beforeStartPointOrder(entry.plan.beforeStartPointOrder)
        .completed(entry.completed)
        .build();
  }

  private UUID firstPathNeedingPage(
      List<SearchPath> ordered, Map<UUID, PathProgress> progress, boolean changesQuery) {
    return ordered.stream()
        .filter(path -> needsPage(path, progress.get(path.getId()), changesQuery))
        .map(SearchPath::getId)
        .findFirst()
        .orElse(null);
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

  // 이번 페이지에 담을 경로, 사용한 구간 수와 좌표 수, 다음 조회를 시작할 경로를 기록한다.
  private static final class PageSelection {
    private final List<PendingPath> pending = new ArrayList<>();
    private int usedSegments;
    private long usedCoordinates;
    private UUID nextId;

    private void add(PendingPath entry) {
      pending.add(entry);
      usedSegments += entry.segments.size();
      for (SearchPathSegment segment : entry.segments) {
        usedCoordinates += coordinateCount(segment);
      }
    }
  }
}
