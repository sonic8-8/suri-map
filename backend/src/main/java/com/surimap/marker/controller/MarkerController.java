package com.surimap.marker.controller;

import com.surimap.marker.dto.MarkerCreateRequest;
import com.surimap.marker.dto.MarkerCreateResponse;
import com.surimap.marker.service.MarkerCreateService;
import com.surimap.marker.service.MarkerRequestContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/markers")
public class MarkerController {

  private final MarkerCreateService markerCreateService;
  private final MarkerRequestContextResolver contextResolver;

  public MarkerController(
      MarkerCreateService markerCreateService, MarkerRequestContextResolver contextResolver) {
    this.markerCreateService = markerCreateService;
    this.contextResolver = contextResolver;
  }

  @PostMapping
  public ResponseEntity<MarkerCreateResponse> create(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestHeader(value = "X-Client-Channel", required = false) String channel,
      @RequestHeader(value = "X-PolicePhone-Id", required = false) String policePhoneId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody MarkerCreateRequest request) {
    MarkerRequestContext context =
        contextResolver.resolve(authorization, channel, policePhoneId, idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(markerCreateService.create(request, context).response());
  }
}
