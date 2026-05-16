package com.surimap.account.security;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import com.surimap.common.auth.SuriMapAuthentication;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class KeycloakJwtAuthenticationConverter implements OidcIdentityAuthenticationConverter {

  @Override
  public Optional<SuriMapAuthentication> convert(Jwt jwt, String channelHeader) {
    Optional<Channel> channel = parseEnum(Channel.class, channelHeader);
    Optional<String> accountId = requiredString(jwt, "accountId");
    Optional<AccountType> accountType =
        requiredString(jwt, "accountType").flatMap(value -> parseEnum(AccountType.class, value));
    Optional<OrganizationType> organizationType =
        requiredString(jwt, "organizationType")
            .flatMap(value -> parseEnum(OrganizationType.class, value));
    if (channel.isEmpty()
        || accountId.isEmpty()
        || accountType.isEmpty()
        || organizationType.isEmpty()) {
      return Optional.empty();
    }

    Optional<String> policePhoneId =
        channel.orElseThrow() == Channel.APP
            ? requiredString(jwt, "policePhoneId")
            : Optional.empty();
    if (channel.orElseThrow() == Channel.APP && policePhoneId.isEmpty()) {
      return Optional.empty();
    }
    List<SimpleGrantedAuthority> authorities =
        roles(jwt).stream().map(role -> new SimpleGrantedAuthority(role.name())).toList();
    if (authorities.isEmpty()) {
      authorities =
          derivedRoles(accountType.orElseThrow(), organizationType.orElseThrow()).stream()
              .map(role -> new SimpleGrantedAuthority(role.name()))
              .toList();
    }

    return Optional.of(
        new SuriMapAuthentication(
            accountId.orElseThrow(),
            accountType.orElseThrow(),
            organizationType.orElseThrow(),
            channel.orElseThrow(),
            policePhoneId.orElse(null),
            authorities));
  }

  private static Optional<String> requiredString(Jwt jwt, String claimName) {
    Object value = jwt.getClaims().get(claimName);
    if (value instanceof String text && !text.isBlank()) {
      return Optional.of(text.trim());
    }
    if (value instanceof Collection<?> collection) {
      return collection.stream()
          .filter(String.class::isInstance)
          .map(String.class::cast)
          .map(String::trim)
          .filter(item -> !item.isBlank())
          .findFirst();
    }
    return Optional.empty();
  }

  private static List<Role> roles(Jwt jwt) {
    Object realmAccess = jwt.getClaims().get("realm_access");
    if (!(realmAccess instanceof Map<?, ?> realmAccessMap)) {
      return List.of();
    }
    Object roles = realmAccessMap.get("roles");
    if (!(roles instanceof Collection<?> values)) {
      return List.of();
    }
    return values.stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .map(value -> parseEnum(Role.class, value))
        .flatMap(Optional::stream)
        .toList();
  }

  private static List<Role> derivedRoles(
      AccountType accountType, OrganizationType organizationType) {
    if (accountType == AccountType.COMMAND && organizationType == OrganizationType.MISSING_TEAM) {
      return List.of(Role.MISSING_TEAM_COMMANDER, Role.FIELD_COMMANDER);
    }
    if (accountType == AccountType.COMMAND) {
      return List.of(Role.FIELD_COMMANDER);
    }
    return List.of(Role.MEMBER);
  }

  private static <T extends Enum<T>> Optional<T> parseEnum(Class<T> enumType, String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(Enum.valueOf(enumType, value.trim()));
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }
}
