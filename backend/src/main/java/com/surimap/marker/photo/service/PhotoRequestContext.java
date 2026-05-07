package com.surimap.marker.photo.service;

import com.surimap.marker.photo.security.SuriMapAuthentication;

public record PhotoRequestContext(SuriMapAuthentication authentication, String idempotencyKey) {}
