package com.surimap.api.controller.marker;

import com.surimap.api.controller.marker.request.MarkerDeleteRequest;
import com.surimap.api.controller.marker.request.MarkerUpdateRequest;
import com.surimap.api.controller.marker.response.MarkerMutationResponse;
import com.surimap.api.controller.marker.response.MarkersResponse;
import com.surimap.api.service.marker.MarkerService;
import com.surimap.api.service.marker.response.MarkerMutationServiceResponse;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequireIncidentAccess;
import com.surimap.common.auth.RequirePolicePhone;
import com.surimap.common.auth.RequirePolicePhoneRegistered;
import com.surimap.global.auth.MarkerAuthenticationResolver;
import com.surimap.global.auth.SuriMapAuthentication;
import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import com.surimap.retention.purge.RecordLocationAccess;
import jakarta.validation.Valid;
import java.util.UUID;
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

  private final MarkerService markerService;
  private final MarkerAuthenticationResolver markerAuthenticationResolver;

  public MarkerController(
      MarkerService markerService, MarkerAuthenticationResolver markerAuthenticationResolver) {
    this.markerService = markerService;
    this.markerAuthenticationResolver = markerAuthenticationResolver;
  }

  @GetMapping
  @RequireChannel({Channel.APP, Channel.WEB})
  @RequireIncidentAccess
  @RecordLocationAccess(accessPurpose = "MARKER_READ")
  public ResponseEntity<MarkersResponse> list(
      @RequestParam UUID incidentId,
      @RequestParam(required = false) UUID opId,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String status) {
    return ResponseEntity.ok(
        MarkersResponse.from(markerService.list(incidentId, opId, type, status)));
  }

  // APP는 AppMarkerController로 보낸다. 헤더 누락·오류는 기존 guard와 resolver에서 거부한다.
  @PatchMapping(value = "/{markerId}", headers = "X-Client-Channel!=APP")
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
    SuriMapAuthentication authentication =
        markerAuthenticationResolver.resolveForAppOrWebWrite(
            authorization, channel, policePhoneId, idempotencyKey);
    if (request == null || validation.hasErrors()) {
      throw new BusinessException(ErrorCode.WRITE_CONFLICT);
    }
    MarkerMutationServiceResponse response =
        markerService.update(request.toServiceRequest(markerId, authentication, idempotencyKey));
    return ResponseEntity.ok(MarkerMutationResponse.from(response));
  }

  @DeleteMapping(value = "/{markerId}", headers = "X-Client-Channel!=APP")
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
    SuriMapAuthentication authentication =
        markerAuthenticationResolver.resolveForAppOrWebWrite(
            authorization, channel, policePhoneId, idempotencyKey);
    if (request == null || validation.hasErrors()) {
      throw new BusinessException(ErrorCode.WRITE_CONFLICT);
    }
    MarkerMutationServiceResponse response =
        markerService.delete(request.toServiceRequest(markerId, authentication, idempotencyKey));
    return ResponseEntity.ok(MarkerMutationResponse.from(response));
  }
}
