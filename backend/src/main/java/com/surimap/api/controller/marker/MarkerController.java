package com.surimap.api.controller.marker;

import com.surimap.api.controller.marker.request.MarkerDeleteRequest;
import com.surimap.api.controller.marker.request.MarkerUpdateRequest;
import com.surimap.api.controller.marker.response.MarkerListResponse;
import com.surimap.api.controller.marker.response.MarkerMutationResponse;
import com.surimap.api.service.marker.MarkerService;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequireIncidentAccess;
import com.surimap.common.auth.RequirePolicePhone;
import com.surimap.common.auth.RequirePolicePhoneRegistered;
import com.surimap.marker.controller.MarkerRequestContextResolver;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.service.MarkerRequestContext;
import com.surimap.marker.service.MarkerUpdateDeleteService;
import com.surimap.marker.service.response.MarkerMutationServiceResponse;
import com.surimap.retention.purge.RecordLocationAccess;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/markers")
public class MarkerController {

  private final MarkerUpdateDeleteService markerUpdateDeleteService;
  private final MarkerService markerService;
  private final MarkerRequestContextResolver contextResolver;

  public MarkerController(
      MarkerUpdateDeleteService markerUpdateDeleteService,
      MarkerService markerService,
      MarkerRequestContextResolver contextResolver) {
    this.markerUpdateDeleteService = markerUpdateDeleteService;
    this.markerService = markerService;
    this.contextResolver = contextResolver;
  }

  @GetMapping
  @RequireChannel({Channel.APP, Channel.WEB})
  @RequireIncidentAccess
  @RecordLocationAccess(accessPurpose = "MARKER_READ")
  public ResponseEntity<MarkerListResponse> list(
      @RequestParam UUID incidentId,
      @RequestParam(required = false) UUID opId,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String status) {
    return ResponseEntity.ok(
        MarkerListResponse.from(markerService.list(incidentId, opId, type, status)));
  }

  @PatchMapping("/{markerId}")
  @RequireChannel({Channel.APP, Channel.WEB})
  @RequirePolicePhone
  @RequirePolicePhoneRegistered
  public ResponseEntity<MarkerMutationResponse> update(
      @PathVariable UUID markerId,
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestHeader(value = "X-Client-Channel", required = false) String channel,
      @RequestHeader(value = "X-PolicePhone-Id", required = false) String policePhoneId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody(required = false) MarkerUpdateRequest request,
      BindingResult validation) {
    MarkerRequestContext context =
        contextResolver.resolveFieldOrWebWrite(
            authorization, channel, policePhoneId, idempotencyKey);
    if (request == null || validation.hasErrors()) {
      throw new MarkerApiException("write_conflict", HttpStatus.CONFLICT);
    }
    MarkerMutationServiceResponse response =
        markerUpdateDeleteService.update(request.toServiceRequest(markerId, context));
    return ResponseEntity.ok(MarkerMutationResponse.from(response));
  }

  @DeleteMapping("/{markerId}")
  @RequireChannel({Channel.APP, Channel.WEB})
  @RequirePolicePhone
  @RequirePolicePhoneRegistered
  public ResponseEntity<MarkerMutationResponse> delete(
      @PathVariable UUID markerId,
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestHeader(value = "X-Client-Channel", required = false) String channel,
      @RequestHeader(value = "X-PolicePhone-Id", required = false) String policePhoneId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody(required = false) MarkerDeleteRequest request,
      BindingResult validation) {
    MarkerRequestContext context =
        contextResolver.resolveFieldOrWebWrite(
            authorization, channel, policePhoneId, idempotencyKey);
    if (request == null || validation.hasErrors()) {
      throw new MarkerApiException("write_conflict", HttpStatus.CONFLICT);
    }
    MarkerMutationServiceResponse response =
        markerUpdateDeleteService.delete(request.toServiceRequest(markerId, context));
    return ResponseEntity.ok(MarkerMutationResponse.from(response));
  }
}
