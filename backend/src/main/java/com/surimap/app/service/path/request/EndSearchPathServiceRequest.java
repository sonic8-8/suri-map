package com.surimap.app.service.path.request;

import java.time.Instant;

public record EndSearchPathServiceRequest(Instant endedAt, String idempotencyKey) {}
