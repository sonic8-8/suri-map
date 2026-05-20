package com.mock112.assignment;

import java.util.List;

public record AssignableOrganization(
        String organizationCode,
        String organizationName,
        String organizationType,
        List<AssignableAccount> accounts) {

    public AssignableOrganization {
        accounts = List.copyOf(accounts);
    }
}
