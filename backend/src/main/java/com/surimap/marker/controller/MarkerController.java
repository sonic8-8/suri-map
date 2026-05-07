package com.surimap.marker.controller;

import com.surimap.marker.dto.MarkerCreateRequest;
import com.surimap.marker.dto.MarkerCreateResponse;
import com.surimap.marker.dto.MarkerDeleteRequest;
import com.surimap.marker.dto.MarkerMutationResponse;
import com.surimap.marker.dto.MarkerUpdateRequest;
import com.surimap.marker.service.MarkerCreateService;
import com.surimap.marker.service.MarkerRequestContext;
import com.surimap.marker.service.MarkerUpdateDeleteService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/markers")
public class MarkerController {

  private final MarkerCreateService markerCreateService;
  private final MarkerUpdateDeleteService markerUpdateDeleteService;
  private final MarkerRequestContextResolver contextResolver;

  public MarkerController(
      MarkerCreateService markerCreateService,
      MarkerUpdateDeleteService markerUpdateDeleteService,
      MarkerRequestContextResolver contextResolver) {
    this.markerCreateService = markerCreateService;
    this.markerUpdateDeleteService = markerUpdateDeleteService;
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

  @PatchMapping("/{markerId}")
  public ResponseEntity<MarkerMutationResponse> update(
      @PathVariable UUID markerId,
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestHeader(value = "X-Client-Channel", required = false) String channel,
      @RequestHeader(value = "X-PolicePhone-Id", required = false) String policePhoneId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody(required = false) MarkerUpdateRequest request) {
    MarkerRequestContext context =
        contextResolver.resolveFieldOrWebWrite(
            authorization, channel, policePhoneId, idempotencyKey);
    return ResponseEntity.ok(
        markerUpdateDeleteService.update(markerId, request, context).response());
  }

  @DeleteMapping("/{markerId}")
  public ResponseEntity<MarkerMutationResponse> delete(
      @PathVariable UUID markerId,
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestHeader(value = "X-Client-Channel", required = false) String channel,
      @RequestHeader(value = "X-PolicePhone-Id", required = false) String policePhoneId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody(required = false) MarkerDeleteRequest request) {
    MarkerRequestContext context =
        contextResolver.resolveFieldOrWebWrite(
            authorization, channel, policePhoneId, idempotencyKey);
    return ResponseEntity.ok(
        markerUpdateDeleteService.delete(markerId, request, context).response());
  }
}
