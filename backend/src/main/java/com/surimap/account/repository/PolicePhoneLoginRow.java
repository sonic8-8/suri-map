package com.surimap.account.repository;

import java.util.UUID;

public record PolicePhoneLoginRow(UUID id, String phoneCode, UUID accountId) {}
