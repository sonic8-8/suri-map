package com.surimap.support.auth;

import com.surimap.common.auth.SuriMapAuthentication;
import java.util.Arrays;
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

    String policePhoneId = annotation.policePhoneId().isBlank() ? null : annotation.policePhoneId();

    var auth =
        new SuriMapAuthentication(
            annotation.accountId(),
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
