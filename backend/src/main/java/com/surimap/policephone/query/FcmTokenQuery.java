package com.surimap.policephone.query;

import java.util.List;
import java.util.UUID;

public interface FcmTokenQuery {
  List<FcmTokenRow> activeByPolicePhone(UUID policePhoneId);

  List<FcmTokenRow> activeByAccounts(List<UUID> accountIds);
}
