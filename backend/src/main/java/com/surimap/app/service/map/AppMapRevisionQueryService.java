package com.surimap.app.service.map;

import com.surimap.app.controller.map.response.AppMapRevisionResponse;
import com.surimap.app.controller.map.response.AppMapRevisionResponse.SourceRevision;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppMapRevisionQueryService {

  static final String SOURCE_INCIDENT_DETAIL = "incident_detail";
  static final String SOURCE_OVERALL_SEARCH_AREA = "overall_search_area";
  static final String SOURCE_OP_SEARCH_AREAS = "op_search_areas";
  static final String SOURCE_SEARCH_PATHS = "search_paths";
  static final String SOURCE_LIVE_MARKERS = "live_markers";
  static final String SOURCE_INITIAL_MARKERS = "initial_markers";

  private static final String EMPTY_REVISION = "0:0:0:d41d8cd98f00b204e9800998ecf8427e";

  private final AppMapRevisionMapper mapper;

  public AppMapRevisionQueryService(AppMapRevisionMapper mapper) {
    this.mapper = mapper;
  }

  @Transactional(readOnly = true)
  public AppMapRevisionResponse revisions(UUID incidentId, UUID opId, UUID policePhoneId) {
    return new AppMapRevisionResponse(
        incidentId,
        opId,
        List.of(
            source(SOURCE_INCIDENT_DETAIL, () -> mapper.incidentDetailRevision(incidentId)),
            source(SOURCE_OVERALL_SEARCH_AREA, () -> mapper.overallSearchAreaRevision(incidentId)),
            source(SOURCE_OP_SEARCH_AREAS, () -> opScoped(opId, () -> mapper.opSearchAreasRevision(incidentId, opId))),
            source(SOURCE_SEARCH_PATHS, () -> opScoped(opId, () -> mapper.searchPathsRevision(incidentId, opId))),
            source(SOURCE_LIVE_MARKERS, () -> opScoped(opId, () -> mapper.liveMarkersRevision(incidentId, opId))),
            source(SOURCE_INITIAL_MARKERS, () -> mapper.initialMarkersRevision(incidentId, opId, policePhoneId))));
  }

  private SourceRevision source(String source, Supplier<String> revision) {
    String value = revision.get();
    return new SourceRevision(source, value == null || value.isBlank() ? EMPTY_REVISION : value);
  }

  private String opScoped(UUID opId, Supplier<String> revision) {
    if (opId == null) {
      return EMPTY_REVISION;
    }
    return revision.get();
  }
}
