package com.surimap.account.repository;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.OrganizationType;

public record AccountLoginRow(
    String id,
    String loginId,
    String passwordHash,
    AccountType accountType,
    OrganizationType organizationType,
    String status) {}
