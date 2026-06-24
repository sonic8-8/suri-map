package com.surimap.app.controller.searcharea;

import com.surimap.app.controller.searcharea.request.SearchAreaBoundaryAlertRequest;
import com.surimap.app.controller.searcharea.response.SearchAreaBoundaryAlertResponse;
import com.surimap.app.service.searcharea.AppSearchAreaBoundaryAlertService;
import com.surimap.app.service.searcharea.SearchAreaBoundaryAlertException;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequirePolicePhone;
import com.surimap.common.auth.RequirePolicePhoneRegistered;
import com.surimap.common.auth.SuriMapAuthentication;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search-area-boundary-alerts")
public class SearchAreaBoundaryAlertController {

  private final AppSearchAreaBoundaryAlertService service;

  public SearchAreaBoundaryAlertController(AppSearchAreaBoundaryAlertService service) {
    this.service = service;
  }

  @PostMapping
  @RequireChannel(Channel.APP)
  @RequirePolicePhone
  @RequirePolicePhoneRegistered
  public ResponseEntity<SearchAreaBoundaryAlertResponse> create(
      @RequestHeader(value = "X-PolicePhone-Id", required = false) String policePhoneIdHeader,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody SearchAreaBoundaryAlertRequest request) {
    requireIdempotencyKey(idempotencyKey);
    UUID policePhoneId = parsePolicePhoneId(policePhoneIdHeader);
    UUID accountId = currentAppAccountId(policePhoneId);
    var result = service.create(request.toServiceRequest(policePhoneId, accountId, idempotencyKey));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(SearchAreaBoundaryAlertResponse.from(result));
  }

  private UUID parsePolicePhoneId(String header) {
    if (header == null || header.isBlank()) {
      throw new SearchAreaBoundaryAlertException("police_phone_required");
    }
    try {
      return UUID.fromString(header);
    } catch (IllegalArgumentException exception) {
      throw new SearchAreaBoundaryAlertException("police_phone_required");
    }
  }

  private void requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new SearchAreaBoundaryAlertException("write_conflict");
    }
  }

  private UUID currentAppAccountId(UUID policePhoneId) {
    var current = SecurityContextHolder.getContext().getAuthentication();
    if (current instanceof SuriMapAuthentication authentication) {
      if (authentication.getChannel() != Channel.APP) {
        throw new SearchAreaBoundaryAlertException("channel_not_allowed");
      }
      validatePolicePhoneBinding(policePhoneId, authentication);
      return UUID.fromString(authentication.getAccountId());
    }
    throw new SearchAreaBoundaryAlertException("channel_not_allowed");
  }

  private void validatePolicePhoneBinding(
      UUID policePhoneId, SuriMapAuthentication authentication) {
    try {
      if (policePhoneId.equals(UUID.fromString(authentication.getPolicePhoneId()))) {
        return;
      }
    } catch (RuntimeException exception) {
      throw new SearchAreaBoundaryAlertException("police_phone_required");
    }
    throw new SearchAreaBoundaryAlertException("police_phone_required");
  }
}
