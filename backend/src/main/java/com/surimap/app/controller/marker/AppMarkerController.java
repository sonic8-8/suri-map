package com.surimap.app.controller.marker;

import com.surimap.app.controller.marker.request.MarkerCreateRequest;
import com.surimap.app.controller.marker.response.MarkerCreateResponse;
import com.surimap.app.service.marker.AppMarkerService;
import com.surimap.app.service.marker.response.MarkerCreateServiceResponse;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequirePolicePhone;
import com.surimap.common.auth.RequirePolicePhoneRegistered;
import com.surimap.marker.controller.MarkerRequestContextResolver;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.service.MarkerRequestContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
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
}
