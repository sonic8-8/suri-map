package com.surimap.marker.photo.security;

public interface SuriMapAuthenticationResolver {

  SuriMapAuthentication resolve(String authorization, String channel);
}
