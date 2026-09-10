package com.surimap.global.auth;

public interface SuriMapAuthenticationResolver {

  SuriMapAuthentication resolve(String authorization, String channel);
}
