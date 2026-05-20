package com.mock112.controller.request;

import java.time.OffsetDateTime;

public class OrganizationAssignmentRequest {

    private String organizationCode;
    private OffsetDateTime assignedAt;

    public String getOrganizationCode() { return organizationCode; }
    public void setOrganizationCode(String organizationCode) { this.organizationCode = organizationCode; }

    public OffsetDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(OffsetDateTime assignedAt) { this.assignedAt = assignedAt; }
}
