package com.surimap.incident.domain;

import java.util.UUID;

/**
 * idempotency_record 테이블 row 표현. {@code POST /api/incidents/import}의 중복 호출을 같은 응답으로 replay하기 위한
 * idempotency key·request body hash·결과 entity 식별자·status·version을 보관한다.
 */
public class IncidentImportIdempotencyRecord {

  private UUID id;
  private String idempotencyKey;
  private String requestBodyHash;
  private String requestPath;
  private String requestMethod;
  private String idempotencyStatus;
  private UUID resultEntityId;
  private String resultEntityStatus;
  private Long resultEntityVersion;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
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
}
