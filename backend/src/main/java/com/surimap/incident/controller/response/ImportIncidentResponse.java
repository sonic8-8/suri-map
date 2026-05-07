package com.surimap.incident.controller.response;

import java.util.List;
import java.util.UUID;

public record ImportIncidentResponse(
    UUID id, UUID incidentId, String status, long version, List<String> assignmentAccountIds) {}
