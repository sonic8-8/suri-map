package com.mock112.assignment;

public record AssignableAccount(
        String accountId,
        String accountCode,
        String accountType,
        String organizationType,
        String organizationCode,
        String organizationName,
        String displayName,
        String incidentRole) {
}
