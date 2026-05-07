package com.surimap.marker.service;

import com.surimap.marker.photo.security.SuriMapAuthentication;

public record MarkerRequestContext(SuriMapAuthentication authentication, String idempotencyKey) {}
