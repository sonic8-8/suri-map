package com.surimap.app.controller.marker;

import com.surimap.app.controller.marker.request.MarkerCreateRequest;
import com.surimap.app.controller.marker.request.MarkerDeleteRequest;
import com.surimap.app.controller.marker.request.MarkerUpdateRequest;
import com.surimap.app.controller.marker.response.MarkerCreateResponse;
import com.surimap.app.controller.marker.response.MarkerMutationResponse;
import com.surimap.app.service.marker.AppMarkerService;
import com.surimap.app.service.marker.response.MarkerCreateServiceResponse;
import com.surimap.app.service.marker.response.MarkerMutationServiceResponse;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequirePolicePhone;
import com.surimap.common.auth.RequirePolicePhoneRegistered;
import com.surimap.marker.controller.MarkerRequestContextResolver;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.service.MarkerRequestContext;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/markers")
public class AppMarkerController {

  private final AppMarkerService appMarkerService;
  private final MarkerRequestContextResolver contextResolver;

  public AppMarkerController(
      AppMarkerService appMarkerService, MarkerRequestContextResolver contextResolver) {
    this.appMarkerService = appMarkerService;
    this.contextResolver = contextResolver;
  }

  @PostMapping
  @RequireChannel(Channel.APP)
  @RequirePolicePhone
  @RequirePolicePhoneRegistered
  public ResponseEntity<MarkerCreateResponse> create(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestHeader(value = "X-Client-Channel", required = false) String channel,
      @RequestHeader(value = "X-PolicePhone-Id", required = false) String policePhoneId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody MarkerCreateRequest request,
      BindingResult validation) {
    MarkerRequestContext context =
        contextResolver.resolve(authorization, channel, policePhoneId, idempotencyKey);
    if (validation.hasErrors()) {
      throw new MarkerApiException("write_conflict", HttpStatus.CONFLICT);
    }
    MarkerCreateServiceResponse response =
        appMarkerService.create(request.toServiceRequest(context));
    return ResponseEntity.status(HttpStatus.CREATED).body(MarkerCreateResponse.from(response));
  }

  // 헤더는 라우팅에만 사용한다. 기존 guard와 resolver가 인증 채널·업무폰을 검사한다.
  @PatchMapping(value = "/{markerId}", headers = "X-Client-Channel=APP")
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
        appMarkerService.update(request.toServiceRequest(markerId, context));
    return ResponseEntity.ok(MarkerMutationResponse.from(response));
  }

  @DeleteMapping(value = "/{markerId}", headers = "X-Client-Channel=APP")
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
        appMarkerService.delete(request.toServiceRequest(markerId, context));
    return ResponseEntity.ok(MarkerMutationResponse.from(response));
  }
}
