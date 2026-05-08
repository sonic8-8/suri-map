package com.surimap.offlinepackage.controller;

import com.surimap.offlinepackage.dto.OfflinePackageInstallationReportRequest;
import com.surimap.offlinepackage.dto.OfflinePackageInstallationResponse;
import com.surimap.offlinepackage.dto.OfflinePackageManifestResponse;
import com.surimap.offlinepackage.exception.OfflinePackageApiException;
import com.surimap.offlinepackage.service.OfflinePackageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents/{incidentId}/offline-package")
public class OfflinePackageController {

  private final OfflinePackageService service;

  public OfflinePackageController(OfflinePackageService service) {
    this.service = service;
  }

  @GetMapping("/manifest")
  public ResponseEntity<OfflinePackageManifestResponse> manifest(
      @PathVariable String incidentId,
      @RequestParam(value = "policePhoneId", required = false) String policePhoneId) {
    return ResponseEntity.ok(service.manifest(incidentId, policePhoneId));
  }

  @PostMapping("/installations")
  public ResponseEntity<OfflinePackageInstallationResponse> reportInstallation(
      @PathVariable String incidentId,
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestHeader(value = "X-Client-Channel", required = false) String clientChannel,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestHeader(value = "X-PolicePhone-Id", required = false) String policePhoneId,
      @RequestBody OfflinePackageInstallationReportRequest request) {
    requireAppWriteHeaders(authorization, clientChannel, idempotencyKey, policePhoneId, request);
    return ResponseEntity.ok(service.reportInstallation(incidentId, idempotencyKey, request));
  }

  private static void requireAppWriteHeaders(
      String authorization,
      String clientChannel,
      String idempotencyKey,
      String policePhoneId,
      OfflinePackageInstallationReportRequest request) {
    if (isBlank(authorization)) {
      throw new OfflinePackageApiException("incident_access_denied", HttpStatus.FORBIDDEN);
    }
    if (!"APP".equals(clientChannel)) {
      throw new OfflinePackageApiException("channel_not_allowed", HttpStatus.FORBIDDEN);
    }
    if (isBlank(idempotencyKey)) {
      throw new OfflinePackageApiException("write_conflict", HttpStatus.CONFLICT);
    }
    if (isBlank(policePhoneId) || isBlank(request.policePhoneId())) {
      throw new OfflinePackageApiException("police_phone_required", HttpStatus.BAD_REQUEST);
    }
    if (!policePhoneId.equals(request.policePhoneId())) {
      throw new OfflinePackageApiException("police_phone_not_assigned", HttpStatus.FORBIDDEN);
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
