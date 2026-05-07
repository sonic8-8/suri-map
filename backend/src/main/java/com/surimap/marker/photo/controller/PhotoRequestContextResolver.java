package com.surimap.marker.photo.controller;

import com.surimap.marker.photo.exception.PhotoApiException;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import com.surimap.marker.photo.security.SuriMapAuthenticationResolver;
import com.surimap.marker.photo.service.PhotoRequestContext;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PhotoRequestContextResolver {

  private final SuriMapAuthenticationResolver authenticationResolver;

  public PhotoRequestContextResolver(SuriMapAuthenticationResolver authenticationResolver) {
    this.authenticationResolver = authenticationResolver;
  }

  public PhotoRequestContext resolve(
      String authorization, String channel, String policePhoneId, String idempotencyKey) {
    requireAppChannel(channel);
    requireIdempotencyKey(idempotencyKey);
    requireAuthorization(authorization);

    UUID headerPolicePhoneId = parsePolicePhoneId(policePhoneId);
    SuriMapAuthentication authentication = authenticationResolver.resolve(authorization, channel);
    requireMatchingPolicePhone(authentication, headerPolicePhoneId);

    return new PhotoRequestContext(authentication, idempotencyKey);
  }

  private void requireAppChannel(String channel) {
    if ("APP".equals(channel)) {
      return;
    }
    throw new PhotoApiException("channel_not_allowed", HttpStatus.FORBIDDEN);
  }

  private void requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      return;
    }
    throw new PhotoApiException("write_conflict", HttpStatus.CONFLICT);
  }

  private void requireAuthorization(String authorization) {
    if (authorization != null && !authorization.isBlank()) {
      return;
    }
    throw new PhotoApiException("incident_access_denied", HttpStatus.FORBIDDEN);
  }

  private UUID parsePolicePhoneId(String policePhoneId) {
    if (policePhoneId == null || policePhoneId.isBlank()) {
      throw new PhotoApiException("police_phone_required", HttpStatus.BAD_REQUEST);
    }
    try {
      return UUID.fromString(policePhoneId);
    } catch (IllegalArgumentException exception) {
      throw new PhotoApiException("police_phone_required", HttpStatus.BAD_REQUEST);
    }
  }

  private void requireMatchingPolicePhone(
      SuriMapAuthentication authentication, UUID headerPolicePhoneId) {
    if (!"APP".equals(authentication.channel())) {
      throw new PhotoApiException("channel_not_allowed", HttpStatus.FORBIDDEN);
    }
    if (headerPolicePhoneId.equals(authentication.policePhoneId())) {
      return;
    }
    throw new PhotoApiException("police_phone_not_registered", HttpStatus.FORBIDDEN);
  }
}
