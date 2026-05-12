package com.surimap.account.repository;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.OrganizationType;
import java.util.UUID;

public record AuthSessionRow(
    UUID sessionId,
    UUID accountId,
    UUID policePhoneId,
    Channel channel,
    AccountType accountType,
    OrganizationType organizationType) {}
