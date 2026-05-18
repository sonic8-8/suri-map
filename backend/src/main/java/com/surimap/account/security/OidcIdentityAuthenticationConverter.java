package com.surimap.account.security;

import com.surimap.common.auth.SuriMapAuthentication;
import java.util.Optional;
import org.springframework.security.oauth2.jwt.Jwt;

public interface OidcIdentityAuthenticationConverter {

  Optional<SuriMapAuthentication> convert(Jwt jwt, String channelHeader, String policePhoneHeader);
}
