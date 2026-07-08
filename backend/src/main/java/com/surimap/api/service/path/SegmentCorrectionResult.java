package com.surimap.api.service.path;

import com.surimap.domain.path.SearchPathSegment;
import java.util.UUID;

public record SegmentCorrectionResult(
    SearchPathSegment segment, UUID opId, UUID policePhoneId) {}
