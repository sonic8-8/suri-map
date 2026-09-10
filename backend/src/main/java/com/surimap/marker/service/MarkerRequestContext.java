package com.surimap.marker.service;

import com.surimap.global.auth.SuriMapAuthentication;

public record MarkerRequestContext(SuriMapAuthentication authentication, String idempotencyKey) {}
