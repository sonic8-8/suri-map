package com.surimap.path;

import java.util.UUID;

public record SegmentCorrectionResult(
    SearchPathSegment segment, UUID opId, UUID policePhoneId) {}
