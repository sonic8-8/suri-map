package com.surimap.client.fcm;

import java.util.List;
import java.util.Map;

public interface FirebaseFcmSender {

  FirebaseFcmBatchResult sendMulticast(
      List<String> tokens, Map<String, String> data, boolean dryRun);
}
