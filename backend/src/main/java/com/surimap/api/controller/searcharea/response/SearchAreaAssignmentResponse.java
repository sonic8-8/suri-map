package com.surimap.api.controller.searcharea.response;

import java.util.List;
import java.util.UUID;

public record SearchAreaAssignmentResponse(
    UUID searchAreaId, UUID opId, List<UUID> assignmentIds, long version) {}
