package com.surimap.opcomparison;

import com.surimap.api.service.opcomparison.OpComparisonApiException;
import com.surimap.api.service.path.SearchPathService;
import com.surimap.domain.path.SearchPath;
import com.surimap.handover.query.HandoverMemoQuery;
import com.surimap.handover.query.HandoverMemoRow;
import com.surimap.marker.query.MarkerQuery;
import com.surimap.marker.query.MarkerQueryFilters;
import com.surimap.marker.query.MarkerView;
import com.surimap.operationalperiod.OperationalPeriod;
import com.surimap.operationalperiod.OperationalPeriodMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpComparisonSourceCollector {

  private final OperationalPeriodMapper operationalPeriodMapper;
  private final SearchPathService searchPathService;
  private final MarkerQuery markerQuery;
  private final HandoverMemoQuery handoverMemoQuery;

  public OpComparisonSourceCollector(
      OperationalPeriodMapper operationalPeriodMapper,
      SearchPathService searchPathService,
      MarkerQuery markerQuery,
      HandoverMemoQuery handoverMemoQuery) {
    this.operationalPeriodMapper = operationalPeriodMapper;
    this.searchPathService = searchPathService;
    this.markerQuery = markerQuery;
    this.handoverMemoQuery = handoverMemoQuery;
  }

  @Transactional(readOnly = true)
  public OpComparisonSourceSnapshot collect(UUID incidentId, List<UUID> requestedOpIds) {
    if (incidentId == null || requestedOpIds == null || requestedOpIds.size() < 2) {
      throw OpComparisonApiException.invalidComparison();
    }
    Set<UUID> uniqueRequested = new LinkedHashSet<>(requestedOpIds);
    if (uniqueRequested.size() != requestedOpIds.size() || uniqueRequested.size() < 2) {
      throw OpComparisonApiException.invalidComparison();
    }

    List<OperationalPeriod> selectedOps =
        operationalPeriodMapper.findAllByIncidentOrderBySequence(incidentId).stream()
            .filter(op -> uniqueRequested.contains(op.getId()))
            .toList();
    if (selectedOps.size() != uniqueRequested.size()) {
      throw OpComparisonApiException.invalidComparison();
    }

    List<SearchPath> incidentPaths =
        searchPathService.findAll().stream()
            .filter(path -> incidentId.equals(path.getIncidentId()))
            .toList();
    List<String> fingerprintParts = new ArrayList<>();
    fingerprintParts.add("incident:" + incidentId);

    List<OpComparisonMetricsSource> metricsSources = new ArrayList<>();
    for (OperationalPeriod op : selectedOps) {
      List<SearchPath> paths = pathsForOp(incidentPaths, op.getId());
      List<MarkerView> markers =
          markerQuery
              .byIncident(incidentId, new MarkerQueryFilters(op.getId(), null, null))
              .markers();
      List<HandoverMemoRow> memos = handoverMemoQuery.byContext(incidentId, op.getId(), null, null);

      fingerprintParts.add("op:%s:%s:%d".formatted(op.getId(), op.getStatus(), op.getVersion()));
      paths.stream()
          .sorted(Comparator.comparing(SearchPath::getId))
          .forEach(path -> fingerprintParts.add("path:%s:%d".formatted(path.getId(), path.getVersion())));
      markers.stream()
          .sorted(Comparator.comparing(MarkerView::id))
          .forEach(
              marker ->
                  fingerprintParts.add("marker:%s:%d".formatted(marker.id(), marker.version())));
      memos.stream()
          .sorted(Comparator.comparing(HandoverMemoRow::memoId))
          .forEach(
              memo -> fingerprintParts.add("memo:%s:%d".formatted(memo.memoId(), memo.version())));

      metricsSources.add(
          new OpComparisonMetricsSource(
              op.getId(),
              op.getSequenceNumber(),
              op.getStartedAt(),
              op.getEndedAt(),
              paths,
              markers.size(),
              memos.size()));
    }

    List<UUID> sortedOpIds = selectedOps.stream().map(OperationalPeriod::getId).toList();
    return new OpComparisonSourceSnapshot(
        incidentId,
        selectedOps,
        sortedOpIds,
        metricsSources,
        sha256(String.join("|", fingerprintParts)));
  }

  private static List<SearchPath> pathsForOp(
      List<SearchPath> paths, UUID operationalPeriodId) {
    return paths.stream().filter(path -> operationalPeriodId.equals(path.getOpId())).toList();
  }

  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }
}
