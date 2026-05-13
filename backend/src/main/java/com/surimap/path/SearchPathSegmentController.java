package com.surimap.path;

import com.surimap.sync.idempotency.IdempotentResponseCache;
import com.surimap.sync.idempotency.IdempotentResponseCache.ResponseMetadata;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
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
  private final IdempotentResponseCache idempotentResponseCache;
  private final Map<String, IdempotencyEntry<PathSegmentCorrectionResponse>> idempotencyEntries =
      new ConcurrentHashMap<>();

  public SearchPathSegmentController(
      SearchPathService searchPathService,
      ObjectProvider<IdempotentResponseCache> idempotentResponseCacheProvider) {
    this.searchPathService = searchPathService;
    this.idempotentResponseCache = idempotentResponseCacheProvider.getIfAvailable();
  }

  @PatchMapping("/{searchPathSegmentId}")
  public ResponseEntity<PathSegmentCorrectionResponse> correctSegment(
      @PathVariable String searchPathSegmentId,
      @RequestHeader(value = "X-Account-Id", required = false) String accountIdHeader,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody PathSegmentCorrectionRequest request) {
    requireIdempotencyKey(idempotencyKey);
    if (request == null || request.movementType() == null) {
      throw new SearchPathApiException("write_conflict");
    }
    UUID accountId = parseAccountId(accountIdHeader);
    String fingerprint = fingerprint("correct-segment:" + searchPathSegmentId + ":" + accountId, request);
    PathSegmentCorrectionResponse response =
        replayOrRun(
            idempotencyKey,
            fingerprint,
            "PATCH /api/search-path-segments/" + searchPathSegmentId,
            PathSegmentCorrectionResponse.class,
            () -> {
              SegmentCorrectionResult corrected =
                  searchPathService.correctSegment(searchPathSegmentId, request.movementType(), accountId);
              return new PathSegmentCorrectionResponse(
                  corrected.segment().id(),
                  corrected.segment().movementType(),
                  corrected.segment().movementTypeSource(),
                  corrected.opId(),
                  corrected.policePhoneId(),
                  corrected.segment().correctedByAccountId(),
                  corrected.segment().correctedAt(),
                  corrected.segment().version());
            },
            this::metadataForCorrection);
    return ResponseEntity.ok(response);
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

  private void requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new SearchPathApiException("write_conflict");
    }
  }

  private <T> T replayOrRun(
      String idempotencyKey,
      String fingerprint,
      String endpoint,
      Class<T> responseType,
      Supplier<T> operation,
      Function<T, ResponseMetadata> metadataExtractor) {
    if (idempotentResponseCache != null) {
      return idempotentResponseCache.replayOrRun(
          endpoint,
          idempotencyKey,
          fingerprint,
          200,
          responseType,
          operation,
          metadataExtractor::apply);
    }
    IdempotencyEntry<PathSegmentCorrectionResponse> existing = idempotencyEntries.get(idempotencyKey);
    if (existing != null) {
      if (!existing.fingerprint().equals(fingerprint)) {
        throw new SearchPathApiException("idempotency_mismatch");
      }
      return responseType.cast(existing.response());
    }
    T response = operation.get();
    idempotencyEntries.put(
        idempotencyKey,
        new IdempotencyEntry<>(fingerprint, (PathSegmentCorrectionResponse) response));
    return response;
  }

  private ResponseMetadata metadataForCorrection(PathSegmentCorrectionResponse response) {
    return new ResponseMetadata(
        null, response.movementType().name(), response.version(), response.version());
  }

  private String fingerprint(String operation, Object request) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed =
          digest.digest(
              (operation + ":" + String.valueOf(request)).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  private record IdempotencyEntry<T>(String fingerprint, T response) {}
}
