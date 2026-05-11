package com.surimap.api.controller.summary;

import com.surimap.api.controller.summary.response.SearchHistorySummaryListResponse;
import com.surimap.api.service.summary.SearchHistorySummaryApiService;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequireIncidentAccess;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchHistorySummaryController {

  private final SearchHistorySummaryApiService service;

  public SearchHistorySummaryController(SearchHistorySummaryApiService service) {
    this.service = service;
  }

  @GetMapping("/api/operational-periods/{operationalPeriodId}/search-history-summaries")
  @RequireChannel({Channel.APP, Channel.WEB})
  @RequireIncidentAccess
  public ResponseEntity<SearchHistorySummaryListResponse> list(
      @PathVariable UUID operationalPeriodId,
      @RequestParam UUID incidentId,
      @RequestParam(required = false) String scopeType,
      @RequestParam(required = false) UUID scopeId,
      @RequestParam(required = false) UUID dutyShiftId,
      @RequestParam(required = false) String status) {
    return ResponseEntity.ok(
        service.list(operationalPeriodId, incidentId, scopeType, scopeId, dutyShiftId, status));
  }
}
