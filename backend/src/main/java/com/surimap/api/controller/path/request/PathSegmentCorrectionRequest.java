package com.surimap.api.controller.path.request;

import com.surimap.domain.path.MovementType;

public record PathSegmentCorrectionRequest(MovementType movementType, String reason) {}
