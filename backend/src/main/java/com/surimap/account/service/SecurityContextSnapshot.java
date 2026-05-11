package com.surimap.account.service;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import java.util.List;

public record SecurityContextSnapshot(
    String accountId,
    AccountType accountType,
    OrganizationType organizationType,
    Channel channel,
    String policePhoneId,
    List<String> authorities) {

  public SecurityContextSnapshot {
    authorities = List.copyOf(authorities);
  }
}
