package com.surimap.support.auth;

import com.surimap.common.auth.SuriMapAuthentication;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

public class WithMockAccountFactory implements WithSecurityContextFactory<WithMockAccount> {

  @Override
  public SecurityContext createSecurityContext(WithMockAccount annotation) {
    var authorities =
        Arrays.stream(annotation.roles())
            .map(role -> new SimpleGrantedAuthority(role.name()))
            .toList();

    UUID policePhoneId =
        annotation.policePhoneId().isBlank() ? null : UUID.fromString(annotation.policePhoneId());

    var auth =
        new SuriMapAuthentication(
            UUID.fromString(annotation.accountId()),
            annotation.accountType(),
            annotation.organizationType(),
            annotation.channel(),
            policePhoneId,
            authorities);

    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(auth);
    return context;
  }
}
