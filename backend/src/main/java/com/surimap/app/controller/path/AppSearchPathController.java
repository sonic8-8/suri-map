package com.surimap.app.controller.path;

import com.surimap.app.controller.path.request.SearchPathStartRequest;
import com.surimap.app.controller.path.request.SearchPathStatusUpdateRequest;
import com.surimap.app.controller.path.response.SearchPathStartResponse;
import com.surimap.app.controller.path.response.SearchPathStatusUpdateResponse;
import com.surimap.app.service.path.AppSearchPathService;
import com.surimap.app.service.path.response.SearchPathStartServiceResponse;
import com.surimap.app.service.path.response.SearchPathStatusUpdateServiceResponse;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequirePolicePhone;
import com.surimap.common.auth.RequirePolicePhoneRegistered;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.domain.path.exception.SearchPathGuardException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search-paths")
public class AppSearchPathController {

  private final AppSearchPathService appSearchPathService;

  public AppSearchPathController(AppSearchPathService appSearchPathService) {
    this.appSearchPathService = appSearchPathService;
  }

  @PostMapping
  @RequireChannel(Channel.APP)
  @RequirePolicePhone
  @RequirePolicePhoneRegistered
  public ResponseEntity<SearchPathStartResponse> start(
      @RequestHeader(value = "X-PolicePhone-Id", required = false) String policePhoneIdHeader,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody SearchPathStartRequest request) {
    requireIdempotencyKey(idempotencyKey);
    UUID policePhoneId = parsePolicePhoneId(policePhoneIdHeader);
    UUID accountId = currentAppAccountId(policePhoneId);
    SearchPathStartServiceResponse response =
        appSearchPathService.start(
            request.toServiceRequest(policePhoneId, accountId, idempotencyKey));
    return ResponseEntity.status(HttpStatus.CREATED).body(SearchPathStartResponse.from(response));
  }

  @PatchMapping("/{searchPathId}")
  @RequireChannel(Channel.APP)
  @RequirePolicePhone
  @RequirePolicePhoneRegistered
  public ResponseEntity<SearchPathStatusUpdateResponse> updateStatus(
      @PathVariable UUID searchPathId,
      @RequestHeader(value = "X-PolicePhone-Id", required = false) String policePhoneIdHeader,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody SearchPathStatusUpdateRequest request) {
    requireIdempotencyKey(idempotencyKey);
    UUID policePhoneId = parsePolicePhoneId(policePhoneIdHeader);
    UUID accountId = currentAppAccountId(policePhoneId);
    SearchPathStatusUpdateServiceResponse response =
        appSearchPathService.updateStatus(
            request.toServiceRequest(searchPathId, policePhoneId, accountId, idempotencyKey));
    return ResponseEntity.ok(SearchPathStatusUpdateResponse.from(response));
  }

  private UUID parsePolicePhoneId(String header) {
    if (header == null || header.isBlank()) {
      throw new SearchPathGuardException("police_phone_required");
    }
    try {
      return UUID.fromString(header);
    } catch (IllegalArgumentException exception) {
      throw new SearchPathGuardException("police_phone_required");
    }
  }

  private void requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new SearchPathGuardException("write_conflict");
    }
  }

  private UUID currentAppAccountId(UUID policePhoneId) {
    var current = SecurityContextHolder.getContext().getAuthentication();
    if (current instanceof SuriMapAuthentication authentication) {
      if (authentication.getChannel() == Channel.WEB) {
        throw new SearchPathGuardException("channel_not_allowed");
      }
      validatePolicePhoneBinding(policePhoneId, authentication);
      return UUID.fromString(authentication.getAccountId());
    }
    throw new SearchPathGuardException("channel_not_allowed");
  }

  private void validatePolicePhoneBinding(
      UUID policePhoneId, SuriMapAuthentication authentication) {
    try {
      if (policePhoneId.equals(UUID.fromString(authentication.getPolicePhoneId()))) {
        return;
      }
    } catch (RuntimeException exception) {
      throw new SearchPathGuardException("police_phone_required");
    }
    throw new SearchPathGuardException("police_phone_required");
  }
}
