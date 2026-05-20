package com.surimap.app.controller.path;

import com.surimap.app.controller.path.request.PatchSearchPathRequest;
import com.surimap.app.controller.path.request.StartSearchPathRequest;
import com.surimap.app.controller.path.response.PatchSearchPathResponse;
import com.surimap.app.controller.path.response.StartSearchPathResponse;
import com.surimap.app.service.path.AppSearchPathCommandService;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.domain.path.exception.SearchPathGuardException;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search-paths")
public class PathController {

  private final AppSearchPathCommandService service;

  public PathController(AppSearchPathCommandService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<StartSearchPathResponse> start(
      @RequestHeader(value = "X-PolicePhone-Id", required = false) String policePhoneIdHeader,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody StartSearchPathRequest request) {
    requireIdempotencyKey(idempotencyKey);
    UUID policePhoneId = HeaderParsers.parsePolicePhoneId(policePhoneIdHeader);
    UUID accountId = currentAccountId();
    var created = service.start(request.toServiceRequest(policePhoneId, accountId, idempotencyKey));
    return ResponseEntity.status(HttpStatus.CREATED).body(StartSearchPathResponse.from(created));
  }

  @PatchMapping("/{searchPathId}")
  public ResponseEntity<PatchSearchPathResponse> patch(
      @PathVariable UUID searchPathId,
      @RequestHeader(value = "X-PolicePhone-Id", required = false) String policePhoneIdHeader,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody PatchSearchPathRequest request) {
    requireIdempotencyKey(idempotencyKey);
    UUID policePhoneId = HeaderParsers.parsePolicePhoneId(policePhoneIdHeader);
    UUID accountId = currentAccountId();
    var patched =
        service.patch(searchPathId, policePhoneId, accountId, request.toServiceRequest(idempotencyKey));
    return ResponseEntity.ok(PatchSearchPathResponse.from(patched));
  }

  private void requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new SearchPathGuardException("write_conflict");
    }
  }

  private UUID currentAccountId() {
    var current = SecurityContextHolder.getContext().getAuthentication();
    if (current instanceof SuriMapAuthentication authentication) {
      return UUID.fromString(authentication.getAccountId());
    }
    throw new SearchPathGuardException("channel_not_allowed");
  }
}
