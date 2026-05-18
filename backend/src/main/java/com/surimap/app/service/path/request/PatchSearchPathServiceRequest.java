package com.surimap.app.service.path.request;

import java.time.Instant;

public record PatchSearchPathServiceRequest(
    SearchPathLifecycleAction action, Instant clientTs, String idempotencyKey) {}
