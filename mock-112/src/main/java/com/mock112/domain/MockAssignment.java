package com.mock112.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.OffsetDateTime;

/**
 * 사건-계정 배정 정보 1건.
 * Suri-Map DB의 incident_assignment 테이블로 매핑된다.
 *
 * externalAssignmentKey는 mock 112 측의 배정 유일 키로,
 * 중복 polling 방지에 사용된다.
 */
public class MockAssignment {

    private String externalAssignmentKey;
    private String accountCode;
    private String incidentRole;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime assignedAt;

    public MockAssignment() {}

    public MockAssignment(String externalAssignmentKey, String accountCode,
                          String incidentRole, OffsetDateTime assignedAt) {
        this.externalAssignmentKey = externalAssignmentKey;
        this.accountCode = accountCode;
        this.incidentRole = incidentRole;
        this.assignedAt = assignedAt;
    }

    // --- getters & setters ---

    public String getExternalAssignmentKey() { return externalAssignmentKey; }
    public void setExternalAssignmentKey(String externalAssignmentKey) { this.externalAssignmentKey = externalAssignmentKey; }

    public String getAccountCode() { return accountCode; }
    public void setAccountCode(String accountCode) { this.accountCode = accountCode; }

    public String getIncidentRole() { return incidentRole; }
    public void setIncidentRole(String incidentRole) { this.incidentRole = incidentRole; }

    public OffsetDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(OffsetDateTime assignedAt) { this.assignedAt = assignedAt; }
}
