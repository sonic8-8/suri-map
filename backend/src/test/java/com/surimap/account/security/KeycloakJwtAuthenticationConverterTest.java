package com.surimap.account.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakJwtAuthenticationConverterTest {

  private final KeycloakJwtAuthenticationConverter converter =
      new KeycloakJwtAuthenticationConverter();

  @Test
  void convertsKeycloakWebClaimsToSuriMapAuthentication() {
    var authentication =
        converter
            .convert(
                Jwt.withTokenValue("header.payload.signature")
                    .header("alg", "RS256")
                    .claim("accountId", "11111111-1111-1111-1111-111111110001")
                    .claim("accountType", "COMMAND")
                    .claim("organizationType", "POLICE_SUBSTATION")
                    .claim("realm_access", Map.of("roles", List.of("FIELD_COMMANDER")))
                    .build(),
                "WEB")
            .orElseThrow();

    assertThat(authentication.getAccountId()).isEqualTo("11111111-1111-1111-1111-111111110001");
    assertThat(authentication.getAccountType()).isEqualTo(AccountType.COMMAND);
    assertThat(authentication.getOrganizationType()).isEqualTo(OrganizationType.POLICE_SUBSTATION);
    assertThat(authentication.getChannel()).isEqualTo(Channel.WEB);
    assertThat(authentication.getPolicePhoneId()).isNull();
    assertThat(authentication.getAuthorities())
        .extracting(Object::toString)
        .containsExactly("FIELD_COMMANDER");
  }

  @Test
  void rejectsAppJwtWithoutPolicePhoneBinding() {
    var authentication =
        converter.convert(
            Jwt.withTokenValue("header.payload.signature")
                .header("alg", "RS256")
                .claim("accountId", "11111111-1111-1111-1111-111111110003")
                .claim("accountType", "TEAM")
                .claim("organizationType", "POLICE_SUBSTATION")
                .claim("realm_access", Map.of("roles", List.of("MEMBER")))
                .build(),
            "APP");

    assertThat(authentication).isEmpty();
  }
}
