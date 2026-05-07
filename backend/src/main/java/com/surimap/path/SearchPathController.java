package com.surimap.path;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
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
  public PathBatchAppendResponse appendBatch(
      @RequestHeader(value = "X-PolicePhone-Id", required = false) String policePhoneIdHeader,
      @RequestBody PathBatchAppendRequest request) {
    UUID policePhoneId = parsePolicePhoneId(policePhoneIdHeader);
    return searchPathService.appendBatch(request, policePhoneId);
  }

  @GetMapping
  public ResponseEntity<PathQueryResponse> query(
      @RequestParam UUID incidentId,
      @RequestParam(required = false) UUID opId,
      @RequestParam(required = false) UUID policePhoneId) {
    return ResponseEntity.ok(searchPathService.query(incidentId, opId, policePhoneId));
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
}
