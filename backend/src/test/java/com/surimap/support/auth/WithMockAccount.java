package com.surimap.support.auth;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import com.surimap.common.auth.Role;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.test.context.support.WithSecurityContext;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@WithSecurityContext(factory = WithMockAccountFactory.class)
public @interface WithMockAccount {
  AccountType accountType() default AccountType.TEAM;

  OrganizationType organizationType() default OrganizationType.MISSING_TEAM;

  Channel channel() default Channel.APP;

  String accountId() default "acct-precinct-team";

  String policePhoneId() default "";

  Role[] roles() default {Role.MEMBER};
}
