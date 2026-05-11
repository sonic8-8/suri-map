package com.surimap.path;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search-path-segments")
public class SearchPathSegmentController {

  private final SearchPathService searchPathService;

  public SearchPathSegmentController(SearchPathService searchPathService) {
    this.searchPathService = searchPathService;
  }

  @PatchMapping("/{searchPathSegmentId}")
  public ResponseEntity<PathSegmentCorrectionResponse> correctSegment(
      @PathVariable String searchPathSegmentId,
      @RequestHeader(value = "X-Account-Id", required = false) String accountIdHeader,
      @RequestBody PathSegmentCorrectionRequest request) {
    if (request == null || request.movementType() == null) {
      throw new SearchPathApiException("write_conflict");
    }
    UUID accountId = parseAccountId(accountIdHeader);
    SegmentCorrectionResult corrected =
        searchPathService.correctSegment(searchPathSegmentId, request.movementType(), accountId);
    return ResponseEntity.ok(
        new PathSegmentCorrectionResponse(
            corrected.segment().id(),
            corrected.segment().movementType(),
            corrected.segment().movementTypeSource(),
            corrected.opId(),
            corrected.policePhoneId(),
            corrected.segment().correctedByAccountId(),
            corrected.segment().correctedAt(),
            corrected.segment().version()));
  }

  private UUID parseAccountId(String accountIdHeader) {
    if (accountIdHeader == null || accountIdHeader.isBlank()) {
      throw new SearchPathApiException("incident_access_denied");
    }
    try {
      return UUID.fromString(accountIdHeader);
    } catch (IllegalArgumentException exception) {
      throw new SearchPathApiException("incident_access_denied");
    }
  }
}
