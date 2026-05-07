package com.surimap.path;

import java.util.List;
import java.util.UUID;

public record PathBatchAppendRequest(
    UUID incidentId, UUID opId, UUID pathId, List<PathBatchPointRequest> points, Long clockOffsetMs) {}
