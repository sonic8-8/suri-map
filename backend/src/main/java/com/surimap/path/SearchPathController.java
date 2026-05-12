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
  private final IdempotentResponseCache idempotentResponseCache;
  private final Map<String, IdempotencyEntry<PathBatchAppendResponse>> idempotencyEntries =
      new ConcurrentHashMap<>();

  public SearchPathController(
      SearchPathService searchPathService,
      ObjectProvider<IdempotentResponseCache> idempotentResponseCacheProvider) {
    this.searchPathService = searchPathService;
    this.idempotentResponseCache = idempotentResponseCacheProvider.getIfAvailable();
  }

  @PostMapping("/batch")
  public ResponseEntity<PathBatchAppendResponse> appendBatch(
      @RequestHeader(value = "X-PolicePhone-Id", required = false) String policePhoneIdHeader,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody PathBatchAppendRequest request) {
    requireIdempotencyKey(idempotencyKey);
    UUID policePhoneId = parsePolicePhoneId(policePhoneIdHeader);
    String fingerprint = fingerprint("append-batch:" + policePhoneId, request);
    PathBatchAppendResponse response =
        replayOrRun(
            idempotencyKey,
            fingerprint,
            "POST /api/search-paths/batch",
            PathBatchAppendResponse.class,
            () -> searchPathService.appendBatch(request, policePhoneId),
            this::metadataForBatch);
    return ResponseEntity.ok(response);
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
    IdempotencyEntry<PathBatchAppendResponse> existing = idempotencyEntries.get(idempotencyKey);
    if (existing != null) {
      if (!existing.fingerprint().equals(fingerprint)) {
        throw new SearchPathApiException("idempotency_mismatch");
      }
      return responseType.cast(existing.response());
    }
    T response = operation.get();
    idempotencyEntries.put(
        idempotencyKey,
        new IdempotencyEntry<>(fingerprint, (PathBatchAppendResponse) response));
    return response;
  }

  private ResponseMetadata metadataForBatch(PathBatchAppendResponse response) {
    return new ResponseMetadata(
        response.id().toString(), response.status().name(), response.version(), response.version());
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
