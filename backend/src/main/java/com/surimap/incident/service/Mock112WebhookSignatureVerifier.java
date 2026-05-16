package com.surimap.incident.service;

import com.surimap.incident.exception.IncidentApiException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class Mock112WebhookSignatureVerifier {

  private final String secret;

  public Mock112WebhookSignatureVerifier(
      @Value("${mock112.webhook.secret:}") String secret) {
    this.secret = secret == null ? "" : secret;
  }

  public void verify(String rawBody, String signatureHeader) {
    if (secret.isBlank()) {
      return;
    }
    if (signatureHeader == null || signatureHeader.isBlank()) {
      throw new IncidentApiException("invalid_signature", HttpStatus.FORBIDDEN);
    }
    String expected = hmacSha256(rawBody);
    String actual = signatureHeader.startsWith("sha256=")
        ? signatureHeader.substring("sha256=".length())
        : signatureHeader;
    if (!MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8),
        actual.getBytes(StandardCharsets.UTF_8))) {
      throw new IncidentApiException("invalid_signature", HttpStatus.FORBIDDEN);
    }
  }

  private String hmacSha256(String rawBody) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
      throw new IllegalStateException("HmacSHA256 is unavailable", exception);
    }
  }
}
