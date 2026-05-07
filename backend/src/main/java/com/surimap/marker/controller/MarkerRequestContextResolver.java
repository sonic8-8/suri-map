package com.surimap.marker.controller;

import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import com.surimap.marker.photo.security.SuriMapAuthenticationResolver;
import com.surimap.marker.service.MarkerRequestContext;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class MarkerRequestContextResolver {

  private final SuriMapAuthenticationResolver authenticationResolver;

  public MarkerRequestContextResolver(SuriMapAuthenticationResolver authenticationResolver) {
    this.authenticationResolver = authenticationResolver;
  }

  public MarkerRequestContext resolve(
      String authorization, String channel, String policePhoneId, String idempotencyKey) {
    requireAppChannel(channel);
    requireIdempotencyKey(idempotencyKey);
    requireAuthorization(authorization);

    UUID headerPolicePhoneId = parsePolicePhoneId(policePhoneId);
    SuriMapAuthentication authentication = authenticationResolver.resolve(authorization, channel);
    requireMatchingPolicePhone(authentication, headerPolicePhoneId);

    return new MarkerRequestContext(authentication, idempotencyKey);
  }

  public MarkerRequestContext resolveFieldOrWebWrite(
      String authorization, String channel, String policePhoneId, String idempotencyKey) {
    requireFieldOrWebWriteChannel(channel);
    requireIdempotencyKey(idempotencyKey);
    requireAuthorization(authorization);

    UUID headerPolicePhoneId = null;
    if ("APP".equals(channel)) {
      headerPolicePhoneId = parsePolicePhoneId(policePhoneId);
    }
    SuriMapAuthentication authentication = authenticationResolver.resolve(authorization, channel);
    requireMatchingChannel(authentication, channel);
    if ("APP".equals(channel)) {
      requireMatchingPolicePhone(authentication, headerPolicePhoneId);
    }

    return new MarkerRequestContext(authentication, idempotencyKey);
  }

  private void requireAppChannel(String channel) {
    if ("APP".equals(channel)) {
      return;
    }
    throw new MarkerApiException("channel_not_allowed", HttpStatus.FORBIDDEN);
  }

  private void requireFieldOrWebWriteChannel(String channel) {
    if ("APP".equals(channel) || "WEB".equals(channel)) {
      return;
    }
    throw new MarkerApiException("channel_not_allowed", HttpStatus.FORBIDDEN);
  }

  private void requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      return;
    }
    throw new MarkerApiException("write_conflict", HttpStatus.CONFLICT);
  }

  private void requireAuthorization(String authorization) {
    if (authorization != null && !authorization.isBlank()) {
      return;
    }
    throw new MarkerApiException("incident_access_denied", HttpStatus.FORBIDDEN);
  }

  private UUID parsePolicePhoneId(String policePhoneId) {
    if (policePhoneId == null || policePhoneId.isBlank()) {
      throw new MarkerApiException("police_phone_required", HttpStatus.BAD_REQUEST);
    }
    try {
      return UUID.fromString(policePhoneId);
    } catch (IllegalArgumentException exception) {
      throw new MarkerApiException("police_phone_required", HttpStatus.BAD_REQUEST);
    }
  }

  private void requireMatchingPolicePhone(
      SuriMapAuthentication authentication, UUID headerPolicePhoneId) {
    if (!"APP".equals(authentication.channel())) {
      throw new MarkerApiException("channel_not_allowed", HttpStatus.FORBIDDEN);
    }
    if (headerPolicePhoneId.equals(authentication.policePhoneId())) {
      return;
    }
    throw new MarkerApiException("police_phone_not_registered", HttpStatus.FORBIDDEN);
  }

  private void requireMatchingChannel(SuriMapAuthentication authentication, String channel) {
    if (channel.equals(authentication.channel())) {
      return;
    }
    throw new MarkerApiException("channel_not_allowed", HttpStatus.FORBIDDEN);
  }
}
