package com.surimap.account.repository;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.OrganizationType;
import java.util.UUID;

public record AccountLoginRow(
    UUID id,
    String loginId,
    String passwordHash,
    AccountType accountType,
    OrganizationType organizationType) {}
