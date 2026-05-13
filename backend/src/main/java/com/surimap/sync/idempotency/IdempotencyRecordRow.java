package com.surimap.sync.idempotency;

import java.time.Instant;
import java.util.UUID;

public class IdempotencyRecordRow {

  private UUID id;
  private String clientOperationId;
  private String idempotencyKey;
  private String requestBodyHash;
  private String requestPath;
  private String requestMethod;
  private String idempotencyStatus;
  private Integer responseStatusCode;
  private String responseBodyJson;
  private String responseContentType;
  private Integer responseBodyFormatVersion;
  private UUID resultEntityId;
  private String resultEntityStatus;
  private Long resultEntityVersion;
  private Long resultEntitySequence;
  private Boolean replayRecovered;
  private Instant createdAt;
  private Instant updatedAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getClientOperationId() {
    return clientOperationId;
  }

  public void setClientOperationId(String clientOperationId) {
    this.clientOperationId = clientOperationId;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public String getRequestBodyHash() {
    return requestBodyHash;
  }

  public void setRequestBodyHash(String requestBodyHash) {
    this.requestBodyHash = requestBodyHash;
  }

  public String getRequestPath() {
    return requestPath;
  }

  public void setRequestPath(String requestPath) {
    this.requestPath = requestPath;
  }

  public String getRequestMethod() {
    return requestMethod;
  }

  public void setRequestMethod(String requestMethod) {
    this.requestMethod = requestMethod;
  }

  public String getIdempotencyStatus() {
    return idempotencyStatus;
  }

  public void setIdempotencyStatus(String idempotencyStatus) {
    this.idempotencyStatus = idempotencyStatus;
  }

  public Integer getResponseStatusCode() {
    return responseStatusCode;
  }

  public void setResponseStatusCode(Integer responseStatusCode) {
    this.responseStatusCode = responseStatusCode;
  }

  public String getResponseBodyJson() {
    return responseBodyJson;
  }

  public void setResponseBodyJson(String responseBodyJson) {
    this.responseBodyJson = responseBodyJson;
  }

  public String getResponseContentType() {
    return responseContentType;
  }

  public void setResponseContentType(String responseContentType) {
    this.responseContentType = responseContentType;
  }

  public Integer getResponseBodyFormatVersion() {
    return responseBodyFormatVersion;
  }

  public void setResponseBodyFormatVersion(Integer responseBodyFormatVersion) {
    this.responseBodyFormatVersion = responseBodyFormatVersion;
  }

  public UUID getResultEntityId() {
    return resultEntityId;
  }

  public void setResultEntityId(UUID resultEntityId) {
    this.resultEntityId = resultEntityId;
  }

  public String getResultEntityStatus() {
    return resultEntityStatus;
  }

  public void setResultEntityStatus(String resultEntityStatus) {
    this.resultEntityStatus = resultEntityStatus;
  }

  public Long getResultEntityVersion() {
    return resultEntityVersion;
  }

  public void setResultEntityVersion(Long resultEntityVersion) {
    this.resultEntityVersion = resultEntityVersion;
  }

  public Long getResultEntitySequence() {
    return resultEntitySequence;
  }

  public void setResultEntitySequence(Long resultEntitySequence) {
    this.resultEntitySequence = resultEntitySequence;
  }

  public Boolean getReplayRecovered() {
    return replayRecovered;
  }

  public void setReplayRecovered(Boolean replayRecovered) {
    this.replayRecovered = replayRecovered;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
