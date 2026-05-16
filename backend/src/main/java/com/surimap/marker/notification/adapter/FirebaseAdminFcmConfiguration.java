package com.surimap.marker.notification.adapter;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

@Configuration
@ConditionalOnProperty(name = "fcm.provider", havingValue = "firebase")
@EnableConfigurationProperties(FirebaseFcmProperties.class)
public class FirebaseAdminFcmConfiguration {

  private static final String FIREBASE_APP_NAME = "suri-map-fcm";

  private final ResourceLoader resourceLoader;

  public FirebaseAdminFcmConfiguration(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }

  @Bean
  FirebaseApp firebaseApp(FirebaseFcmProperties properties) throws IOException {
    return FirebaseApp.getApps().stream()
        .filter(app -> FIREBASE_APP_NAME.equals(app.getName()))
        .findFirst()
        .orElseGet(() -> FirebaseApp.initializeApp(firebaseOptions(properties), FIREBASE_APP_NAME));
  }

  @Bean
  FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
    return FirebaseMessaging.getInstance(firebaseApp);
  }

  private FirebaseOptions firebaseOptions(FirebaseFcmProperties properties) {
    try {
      FirebaseOptions.Builder builder =
          FirebaseOptions.builder().setCredentials(loadCredentials(properties));
      if (properties.hasProjectId()) {
        builder.setProjectId(properties.getProjectId());
      }
      return builder.build();
    } catch (IOException exception) {
      throw new IllegalStateException(
          "failed to load Firebase service account credentials", exception);
    }
  }

  private GoogleCredentials loadCredentials(FirebaseFcmProperties properties) throws IOException {
    if (properties.hasCredentialsJsonBase64()) {
      byte[] decoded = Base64.getDecoder().decode(properties.getCredentialsJsonBase64());
      try (InputStream input = new ByteArrayInputStream(decoded)) {
        return GoogleCredentials.fromStream(input);
      }
    }

    if (properties.hasCredentialsLocation()) {
      try (InputStream input = openCredentialLocation(properties.getCredentialsLocation())) {
        return GoogleCredentials.fromStream(input);
      }
    }

    throw new IllegalStateException(
        "fcm.provider=firebase requires fcm.firebase.credentials-location or "
            + "fcm.firebase.credentials-json-base64");
  }

  private InputStream openCredentialLocation(String location) throws IOException {
    if (location.startsWith("classpath:") || location.startsWith("file:")) {
      Resource resource = resourceLoader.getResource(location);
      if (!resource.exists()) {
        throw new IllegalStateException("Firebase credentials resource not found: " + location);
      }
      return resource.getInputStream();
    }
    return Files.newInputStream(Path.of(location));
  }
}
