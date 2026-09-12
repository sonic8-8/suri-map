package com.surimap.marker.controller;

import com.surimap.global.auth.SuriMapAuthentication;
import com.surimap.global.auth.SuriMapAuthenticationResolver;
import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
import com.surimap.marker.service.MarkerRequestContext;
import java.util.UUID;
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
    throw new BusinessException(ErrorCode.CHANNEL_NOT_ALLOWED);
  }

  private void requireFieldOrWebWriteChannel(String channel) {
    if ("APP".equals(channel) || "WEB".equals(channel)) {
      return;
    }
    throw new BusinessException(ErrorCode.CHANNEL_NOT_ALLOWED);
  }

  private void requireIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      return;
    }
    throw new BusinessException(ErrorCode.WRITE_CONFLICT);
  }

  private void requireAuthorization(String authorization) {
    if (authorization != null && !authorization.isBlank()) {
      return;
    }
    throw new BusinessException(ErrorCode.INCIDENT_ACCESS_DENIED);
  }

  private UUID parsePolicePhoneId(String policePhoneId) {
    if (policePhoneId == null || policePhoneId.isBlank()) {
      throw new BusinessException(ErrorCode.POLICE_PHONE_REQUIRED);
    }
    try {
      return UUID.fromString(policePhoneId);
    } catch (IllegalArgumentException exception) {
      throw new BusinessException(ErrorCode.POLICE_PHONE_REQUIRED);
    }
  }

  private void requireMatchingPolicePhone(
      SuriMapAuthentication authentication, UUID headerPolicePhoneId) {
    if (!"APP".equals(authentication.channel())) {
      throw new BusinessException(ErrorCode.CHANNEL_NOT_ALLOWED);
    }
    if (headerPolicePhoneId.equals(authentication.policePhoneId())) {
      return;
    }
    throw new BusinessException(ErrorCode.POLICE_PHONE_NOT_REGISTERED);
  }

  private void requireMatchingChannel(SuriMapAuthentication authentication, String channel) {
    if (channel.equals(authentication.channel())) {
      return;
    }
    throw new BusinessException(ErrorCode.CHANNEL_NOT_ALLOWED);
  }
}
