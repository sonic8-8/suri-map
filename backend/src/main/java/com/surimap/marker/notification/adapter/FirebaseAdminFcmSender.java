package com.surimap.marker.notification.adapter;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "fcm.provider", havingValue = "firebase")
public class FirebaseAdminFcmSender implements FirebaseFcmSender {

  private static final Logger log = LoggerFactory.getLogger(FirebaseAdminFcmSender.class);
  private static final int FIREBASE_MULTICAST_LIMIT = 500;

  private final FirebaseMessaging firebaseMessaging;

  public FirebaseAdminFcmSender(FirebaseMessaging firebaseMessaging) {
    this.firebaseMessaging = firebaseMessaging;
  }

  @Override
  public FirebaseFcmBatchResult sendMulticast(
      List<String> tokens, Map<String, String> data, boolean dryRun) {
    int successCount = 0;
    List<String> failedRecipients = new ArrayList<>();

    for (int start = 0; start < tokens.size(); start += FIREBASE_MULTICAST_LIMIT) {
      List<String> chunk =
          tokens.subList(start, Math.min(tokens.size(), start + FIREBASE_MULTICAST_LIMIT));
      try {
        BatchResponse response =
            firebaseMessaging.sendEachForMulticast(message(chunk, data), dryRun);
        successCount += response.getSuccessCount();
        collectFailures(chunk, response.getResponses(), failedRecipients);
      } catch (FirebaseMessagingException exception) {
        failedRecipients.addAll(chunk);
        log.warn("Firebase multicast FCM dispatch failed chunkSize={}", chunk.size(), exception);
      }
    }

    return new FirebaseFcmBatchResult(
        successCount, failedRecipients.size(), List.copyOf(failedRecipients));
  }

  private MulticastMessage message(List<String> tokens, Map<String, String> data) {
    return MulticastMessage.builder()
        .putAllData(data)
        .addAllTokens(tokens)
        .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
        .build();
  }

  private void collectFailures(
      List<String> chunk, List<SendResponse> responses, List<String> failedRecipients) {
    for (int index = 0; index < responses.size(); index++) {
      SendResponse response = responses.get(index);
      if (!response.isSuccessful()) {
        failedRecipients.add(chunk.get(index));
      }
    }
  }
}
