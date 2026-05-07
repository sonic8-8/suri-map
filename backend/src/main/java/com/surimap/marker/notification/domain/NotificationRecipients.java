package com.surimap.marker.notification.domain;

import java.util.List;
import java.util.Objects;

public record NotificationRecipients(
    NotificationRecipientPolicy policy, List<String> accountIds, List<String> policePhoneIds) {

  public NotificationRecipients {
    Objects.requireNonNull(policy, "policy must not be null");
    accountIds = List.copyOf(Objects.requireNonNull(accountIds, "accountIds must not be null"));
    policePhoneIds =
        List.copyOf(Objects.requireNonNull(policePhoneIds, "policePhoneIds must not be null"));
  }
}
