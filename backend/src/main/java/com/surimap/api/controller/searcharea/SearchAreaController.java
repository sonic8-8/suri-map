package com.surimap.api.controller.searcharea;

import com.surimap.api.controller.searcharea.request.AssignSearchAreaRequest;
import com.surimap.api.controller.searcharea.request.CreateSearchAreaRequest;
import com.surimap.api.controller.searcharea.request.PatchSearchAreaRequest;
import com.surimap.api.controller.searcharea.request.SplitSearchAreaRequest;
import com.surimap.api.controller.searcharea.response.SearchAreaAssignmentResponse;
import com.surimap.api.controller.searcharea.response.SearchAreaReadResponse;
import com.surimap.api.controller.searcharea.response.SearchAreaResponse;
import com.surimap.api.controller.searcharea.response.SearchAreaSplitResponse;
import com.surimap.api.service.searcharea.SearchAreaApiService;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequireIncidentAccess;
import com.surimap.common.auth.RequireRole;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search-areas")
public class SearchAreaController {

  private final SearchAreaApiService searchAreaApiService;

  public SearchAreaController(SearchAreaApiService searchAreaApiService) {
    this.searchAreaApiService = searchAreaApiService;
  }

  @PostMapping
  @RequireChannel(Channel.WEB)
  @RequireRole
  @RequireIncidentAccess
  public ResponseEntity<SearchAreaResponse> create(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CreateSearchAreaRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(searchAreaApiService.create(request, idempotencyKey));
  }

  @GetMapping
  @RequireChannel({Channel.APP, Channel.WEB})
  @RequireIncidentAccess
  public ResponseEntity<SearchAreaReadResponse> list(
      @RequestParam UUID incidentId,
      @RequestParam(required = false) UUID opId,
      @RequestParam(required = false) String areaLevel,
      @RequestParam(required = false) String status) {
    return ResponseEntity.ok(searchAreaApiService.list(incidentId, opId, areaLevel, status));
  }

  @PatchMapping("/{searchAreaId}")
  @RequireChannel(Channel.WEB)
  @RequireRole
  @RequireIncidentAccess
  public ResponseEntity<SearchAreaResponse> patch(
      @PathVariable UUID searchAreaId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody PatchSearchAreaRequest request) {
    return ResponseEntity.ok(searchAreaApiService.patch(searchAreaId, request, idempotencyKey));
  }

  @PostMapping("/{searchAreaId}/split")
  @RequireChannel(Channel.WEB)
  @RequireRole
  @RequireIncidentAccess
  public ResponseEntity<SearchAreaSplitResponse> split(
      @PathVariable UUID searchAreaId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody SplitSearchAreaRequest request) {
    return ResponseEntity.ok(searchAreaApiService.split(searchAreaId, request, idempotencyKey));
  }

  @PostMapping("/{searchAreaId}/assignments")
  @RequireChannel(Channel.WEB)
  @RequireRole
  @RequireIncidentAccess
  public ResponseEntity<SearchAreaAssignmentResponse> assign(
      @PathVariable UUID searchAreaId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody AssignSearchAreaRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(searchAreaApiService.assign(searchAreaId, request, idempotencyKey));
  }
}
