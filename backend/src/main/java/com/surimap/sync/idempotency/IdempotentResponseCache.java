package com.surimap.sync.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class IdempotentResponseCache {

  private final IdempotentWriteService idempotentWriteService;
  private final ObjectMapper objectMapper;

  public IdempotentResponseCache(
      IdempotentWriteService idempotentWriteService, ObjectMapper objectMapper) {
    this.idempotentWriteService =
        Objects.requireNonNull(idempotentWriteService, "idempotentWriteService must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
  }

  public <T> T replayOrRun(
      String endpoint,
      String idempotencyKey,
      String bodyHash,
      int responseStatusCode,
      Class<T> responseType,
      Supplier<T> ownerOperation,
      ResponseMetadataExtractor<T> metadataExtractor) {
    return replayOrRun(
        endpoint,
        idempotencyKey,
        bodyHash,
        () -> null,
        responseStatusCode,
        responseType,
        ownerOperation,
        metadataExtractor);
  }

  private <T> T replayOrRun(
      String endpoint,
      String idempotencyKey,
      String bodyHash,
      Supplier<String> legacyBodyHash,
      int responseStatusCode,
      Class<T> responseType,
      Supplier<T> ownerOperation,
      ResponseMetadataExtractor<T> metadataExtractor) {
    IdempotentWriteResponse response =
        idempotentWriteService.reserveAndReplay(
            new IdempotentWriteRequest(endpoint, null, null, idempotencyKey, bodyHash),
            () -> toIdempotentResponse(responseStatusCode, ownerOperation.get(), metadataExtractor),
            legacyBodyHash);
    if ("idempotency_mismatch".equals(response.error())) {
      throw new IdempotencyMismatchException();
    }
    if ("write_conflict".equals(response.error())) {
      throw new WriteConflictException();
    }
    return fromJson(response.bodyJson(), responseType);
  }

  public <T> T replayOrRun(
      String endpoint,
      String idempotencyKey,
      Object request,
      int responseStatusCode,
      Class<T> responseType,
      Supplier<T> ownerOperation,
      ResponseMetadataExtractor<T> metadataExtractor) {
    return replayOrRun(
        endpoint,
        idempotencyKey,
        hashRequestBody(toJson(request)),
        responseStatusCode,
        responseType,
        ownerOperation,
        metadataExtractor);
  }

  public <T> T replayOrRun(
      String endpoint,
      String idempotencyKey,
      Object request,
      Supplier<String> legacyRequestBody,
      int responseStatusCode,
      Class<T> responseType,
      Supplier<T> ownerOperation,
      ResponseMetadataExtractor<T> metadataExtractor) {
    return replayOrRun(
        endpoint,
        idempotencyKey,
        hashRequestBody(toJson(request)),
        () -> hashRequestBody(legacyRequestBody.get()),
        responseStatusCode,
        responseType,
        ownerOperation,
        metadataExtractor);
  }

  private <T> IdempotentWriteResponse toIdempotentResponse(
      int statusCode, T ownerResponse, ResponseMetadataExtractor<T> metadataExtractor) {
    ResponseMetadata metadata = metadataExtractor.extract(ownerResponse);
    return new IdempotentWriteResponse(
        statusCode,
        toJson(ownerResponse),
        "application/json",
        1,
        metadata.entityId(),
        metadata.entityStatus(),
        metadata.entityVersion(),
        metadata.entitySequence(),
        false,
        false,
        null);
  }

  private String toJson(Object response) {
    try {
      return objectMapper.writeValueAsString(response);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialize idempotent response", exception);
    }
  }

  private String hashRequestBody(String requestBody) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256").digest(requestBody.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  private <T> T fromJson(String bodyJson, Class<T> responseType) {
    try {
      return objectMapper.readValue(bodyJson, responseType);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to deserialize idempotent response", exception);
    }
  }

  @FunctionalInterface
  public interface ResponseMetadataExtractor<T> {
    ResponseMetadata extract(T response);
  }

  public record ResponseMetadata(
      String entityId, String entityStatus, long entityVersion, long entitySequence) {}
}
