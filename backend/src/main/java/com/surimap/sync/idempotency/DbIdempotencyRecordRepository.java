package com.surimap.sync.idempotency;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class DbIdempotencyRecordRepository implements IdempotencyRecordRepository {

  private static final String APPLICATION_JSON = "application/json";
  private static final int DEFAULT_SCHEMA_VERSION = 1;
  private static final String DB_STATUS_COMPLETED = "COMPLETED";

  private final IdempotencyRecordMapper mapper;

  public DbIdempotencyRecordRepository(IdempotencyRecordMapper mapper) {
    this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
  }

  @Override
  public Optional<IdempotencyRecord> find(String idempotencyKey) {
    return mapper.findByIdempotencyKey(idempotencyKey).map(this::toRecord);
  }

  @Override
  public void save(IdempotencyRecord record) {
    IdempotencyRecordRow row = toRow(record);
    if (mapper.updateByIdempotencyKey(row) == 0) {
      mapper.insert(row);
    }
  }

  @Override
  public void delete(String idempotencyKey) {
    mapper.deleteByIdempotencyKey(idempotencyKey);
  }

  private IdempotencyRecord toRecord(IdempotencyRecordRow row) {
    IdempotentWriteResponse response =
        row.getResponseBodyJson() == null
            ? null
            : new IdempotentWriteResponse(
                nullToZero(row.getResponseStatusCode()),
                row.getResponseBodyJson(),
                valueOrDefault(row.getResponseContentType(), APPLICATION_JSON),
                valueOrDefault(row.getResponseBodyFormatVersion(), DEFAULT_SCHEMA_VERSION),
                row.getResultEntityId() == null ? null : row.getResultEntityId().toString(),
                row.getResultEntityStatus(),
                valueOrDefault(row.getResultEntityVersion(), 0L),
                valueOrDefault(row.getResultEntitySequence(), 0L),
                false,
                Boolean.TRUE.equals(row.getReplayRecovered()),
                null);
    return new IdempotencyRecord(
        endpoint(row.getRequestMethod(), row.getRequestPath()),
        row.getClientOperationId(),
        row.getResultEntityId() == null ? null : row.getResultEntityId().toString(),
        row.getIdempotencyKey(),
        normalizedBodyHash(row.getRequestBodyHash()),
        statusFromDb(row.getIdempotencyStatus()),
        nullToZero(row.getResponseStatusCode()),
        valueOrDefault(row.getResultEntityVersion(), 0L),
        valueOrDefault(row.getResultEntitySequence(), 0L),
        response,
        Boolean.TRUE.equals(row.getReplayRecovered()));
  }

  private IdempotencyRecordRow toRow(IdempotencyRecord record) {
    Endpoint endpoint = Endpoint.parse(record.endpoint());
    IdempotentWriteResponse response = record.response();
    Instant now = Instant.now();
    IdempotencyRecordRow row = new IdempotencyRecordRow();
    row.setId(stableId(record));
    row.setClientOperationId(record.operationId());
    row.setIdempotencyKey(record.idempotencyKey());
    row.setRequestBodyHash(record.bodyHash());
    row.setRequestPath(endpoint.path());
    row.setRequestMethod(endpoint.method());
    row.setIdempotencyStatus(statusToDb(record.status()));
    row.setResponseStatusCode(record.responseStatus() == 0 ? null : record.responseStatus());
    row.setResponseBodyJson(response == null ? null : response.bodyJson());
    row.setResponseContentType(
        response == null ? null : valueOrDefault(response.contentType(), APPLICATION_JSON));
    row.setResponseBodyFormatVersion(response == null ? null : response.responseSchemaVersion());
    row.setResultEntityId(responseEntityId(record, response));
    row.setResultEntityStatus(response == null ? null : response.entityStatus());
    row.setResultEntityVersion(zeroToNull(record.entityVersion()));
    row.setResultEntitySequence(zeroToNull(record.entitySequence()));
    row.setReplayRecovered(record.replayRecovered());
    row.setCreatedAt(now);
    row.setUpdatedAt(now);
    return row;
  }

  private static UUID responseEntityId(IdempotencyRecord record, IdempotentWriteResponse response) {
    if (response != null && response.entityId() != null) {
      return parseUuidOrNull(response.entityId());
    }
    return parseUuidOrNull(record.entityId());
  }

  private static UUID parseUuidOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  private static UUID stableId(IdempotencyRecord record) {
    return UUID.nameUUIDFromBytes(
        ("idempotency_record:" + record.endpoint() + ":" + record.idempotencyKey())
            .getBytes(StandardCharsets.UTF_8));
  }

  private static IdempotencyRecordStatus statusFromDb(String status) {
    if (DB_STATUS_COMPLETED.equals(status) || "COMMITTED".equals(status)) {
      return IdempotencyRecordStatus.COMMITTED;
    }
    return IdempotencyRecordStatus.RESERVED;
  }

  private static String statusToDb(IdempotencyRecordStatus status) {
    return status == IdempotencyRecordStatus.COMMITTED ? DB_STATUS_COMPLETED : status.name();
  }

  private static String normalizedBodyHash(String bodyHash) {
    return bodyHash == null ? null : bodyHash.trim();
  }

  private static String endpoint(String method, String path) {
    return method + " " + path;
  }

  private static int nullToZero(Integer value) {
    return value == null ? 0 : value;
  }

  private static long valueOrDefault(Long value, long fallback) {
    return value == null ? fallback : value;
  }

  private static int valueOrDefault(Integer value, int fallback) {
    return value == null ? fallback : value;
  }

  private static String valueOrDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private static Long zeroToNull(long value) {
    return value == 0L ? null : value;
  }

  private record Endpoint(String method, String path) {

    private static Endpoint parse(String endpoint) {
      int separator = endpoint.indexOf(' ');
      if (separator <= 0 || separator == endpoint.length() - 1) {
        throw new IllegalArgumentException(
            "idempotency endpoint must be '<METHOD> <PATH>': " + endpoint);
      }
      return new Endpoint(endpoint.substring(0, separator), endpoint.substring(separator + 1));
    }
  }
}
