package com.surimap.api.controller.path;

import com.surimap.api.controller.path.request.SearchPathChangesQueryRequest;
import com.surimap.api.controller.path.request.SearchPathSegmentsQueryRequest;
import com.surimap.api.controller.path.response.SearchPathChangesQueryResponse;
import com.surimap.api.controller.path.response.SearchPathSegmentsQueryResponse;
import com.surimap.api.service.path.SearchPathBoardService;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequireIncidentAccess;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import com.surimap.retention.purge.RecordLocationAccess;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "surimap.board.search-path.enabled", havingValue = "true")
@RequestMapping("/api/incidents/{incidentId}/board/search-paths")
public class SearchPathBoardController {
  private final SearchPathBoardService service;

  @PostMapping("/segments/query")
  @RequireChannel(Channel.WEB)
  @RequireIncidentAccess
  @RecordLocationAccess(accessPurpose = "BOARD_VIEW")
  public ResponseEntity<SearchPathSegmentsQueryResponse> getSearchPathSegments(
      @PathVariable UUID incidentId, @Valid @RequestBody SearchPathSegmentsQueryRequest request) {
    return ResponseEntity.ok(
        SearchPathSegmentsQueryResponse.from(
            service.getSearchPathSegments(
                request.toServiceRequest(incidentId, currentAuthentication()))));
  }

  @PostMapping("/changes/query")
  @RequireChannel(Channel.WEB)
  @RequireIncidentAccess
  @RecordLocationAccess(accessPurpose = "BOARD_VIEW")
  public ResponseEntity<SearchPathChangesQueryResponse> getSearchPathChanges(
      @PathVariable UUID incidentId, @Valid @RequestBody SearchPathChangesQueryRequest request) {
    return ResponseEntity.ok(
        SearchPathChangesQueryResponse.from(
            service.getSearchPathChanges(
                request.toServiceRequest(incidentId, currentAuthentication()))));
  }

  private SuriMapAuthentication currentAuthentication() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof SuriMapAuthentication suriMapAuthentication) {
      return suriMapAuthentication;
    }
    throw new BusinessException(ErrorCode.CHANNEL_NOT_ALLOWED);
  }
}
