package com.surimap.api.controller.path;

import com.surimap.api.controller.path.request.SearchPathPointsAppendRequest;
import com.surimap.api.controller.path.response.SearchPathPointsAppendResponse;
import com.surimap.api.controller.path.response.SearchPathQueryResponse;
import com.surimap.api.service.path.SearchPathService;
import com.surimap.api.service.path.request.SearchPathQueryServiceRequest;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequirePolicePhone;
import com.surimap.common.auth.RequirePolicePhoneRegistered;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.domain.path.SearchPathApiException;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search-paths")
public class SearchPathController {

  private final SearchPathService searchPathService;

  public SearchPathController(SearchPathService searchPathService) {
    this.searchPathService = searchPathService;
  }

  @PostMapping("/batch")
  @RequireChannel(Channel.APP)
  @RequirePolicePhone
  @RequirePolicePhoneRegistered
  public ResponseEntity<SearchPathPointsAppendResponse> appendPoints(
      @RequestHeader(value = "X-PolicePhone-Id", required = false) String policePhoneIdHeader,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody SearchPathPointsAppendRequest request) {
    requireIdempotencyKey(idempotencyKey);
    UUID policePhoneId = parsePolicePhoneId(policePhoneIdHeader);
    return ResponseEntity.ok(
        SearchPathPointsAppendResponse.from(
            searchPathService.appendPoints(
                request.toServiceRequest(
                    policePhoneId, currentAppAccountId(policePhoneId), idempotencyKey))));
  }

  @GetMapping
  public ResponseEntity<SearchPathQueryResponse> query(
      @RequestParam UUID incidentId,
      @RequestParam(required = false) UUID opId,
      @RequestParam(required = false) UUID policePhoneId,
      @RequestParam(required = false) UUID accountId) {
    return ResponseEntity.ok(
        SearchPathQueryResponse.from(
            searchPathService.query(
                SearchPathQueryServiceRequest.builder()
                    .incidentId(incidentId)
                    .opId(opId)
                    .policePhoneId(policePhoneId)
                    .accountId(accountId)
                    .build())));
  }

  private UUID parsePolicePhoneId(String header) {
    if (header == null || header.isBlank()) {
      throw new SearchPathApiException("police_phone_required");
    }
    try {
      return UUID.fromString(header);
    } catch (IllegalArgumentException exception) {
      throw new SearchPathApiException("police_phone_required");
    }
  }

  private void requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new SearchPathApiException("write_conflict");
    }
  }

  private UUID currentAppAccountId(UUID policePhoneId) {
    var current = SecurityContextHolder.getContext().getAuthentication();
    if (current instanceof SuriMapAuthentication authentication) {
      if (authentication.getChannel() == Channel.WEB) {
        throw new SearchPathApiException("channel_not_allowed");
      }
      validatePolicePhoneBinding(policePhoneId, authentication);
      return UUID.fromString(authentication.getAccountId());
    }
    throw new SearchPathApiException("channel_not_allowed");
  }

  private void validatePolicePhoneBinding(
      UUID policePhoneId, SuriMapAuthentication authentication) {
    try {
      if (policePhoneId.equals(UUID.fromString(authentication.getPolicePhoneId()))) {
        return;
      }
    } catch (RuntimeException exception) {
      throw new SearchPathApiException("police_phone_required");
    }
    throw new SearchPathApiException("police_phone_required");
  }
}
