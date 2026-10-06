package com.surimap.api.service.path;

import com.surimap.api.service.path.request.SearchPathPageServiceRequest;
import com.surimap.api.service.path.request.SearchPathPageServiceRequest.PathProgress;
import com.surimap.api.service.path.response.SearchPathPageServiceResponse;
import com.surimap.api.service.path.response.SearchPathPageServiceResponse.Path;
import com.surimap.api.service.path.response.SearchPathPageServiceResponse.Segment;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.domain.path.GpsPoint;
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
import java.util.HashMap;
import java.util.HashSet;
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
    List<Path> result = new ArrayList<>();
    int usedSegments = 0;
    long usedCoordinates = 0;
    UUID nextId = null;
    for (SearchPath path : ordered) {
      PathProgress previousProgress = progress.get(path.getId());
      if (!needsPage(path, previousProgress, changesQuery)) {
        continue;
      }
      if (result.size() >= maxPaths || usedSegments >= maxSegments) {
        nextId = path.getId();
        break;
      }
      if (changesQuery
          && (previousProgress == null || previousProgress.getBaselineVersion() == null)) {
        result.add(createPathResponse(path, null, null, null, null, null, List.of()));
        progress.put(path.getId(), PathProgress.builder().id(path.getId()).build());
        continue;
      }
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
      int available = Math.min(segmentsPerPath, maxSegments - usedSegments);
      List<SearchPathSegment> candidates =
          searchPathMapper.findBoardSegments(
              path.getId(), beforeStartPointOrder, lowerVersion, targetVersion, available + 1);
      List<Segment> segments = new ArrayList<>();
      for (SearchPathSegment segment : candidates) {
        if (segments.size() == available) {
          break;
        }
        long coordinates = (long) segment.getEndIndex() - segment.getStartIndex() + 1;
        if (coordinates > maxCoordinates) {
          throw new BusinessException(ErrorCode.SEARCH_PATH_SEGMENT_TOO_LARGE);
        }
        if (usedCoordinates + coordinates > maxCoordinates) {
          break;
        }
        segments.add(readSegment(segment));
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
        appliedVersion = targetVersion;
      }
      result.add(
          createPathResponse(
              path,
              baselineVersion,
              appliedVersion,
              targetVersion,
              beforeStartPointOrder,
              completed,
              segments));
      progress.put(
          path.getId(),
          PathProgress.builder()
              .id(path.getId())
              .baselineVersion(baselineVersion)
              .appliedVersion(appliedVersion)
              .targetVersion(targetVersion)
              .beforeStartPointOrder(beforeStartPointOrder)
              .completed(completed)
              .build());
    }
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

  private Segment readSegment(SearchPathSegment segment) {
    List<GpsPoint> points =
        searchPathMapper.findGpsPointsInRange(
            segment.getSearchPathId(), segment.getStartIndex(), segment.getEndIndex());
    if (points.size() != (long) segment.getEndIndex() - segment.getStartIndex() + 1) {
      throw new BusinessException(ErrorCode.SEARCH_PATH_QUERY_NOT_READY);
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
}
