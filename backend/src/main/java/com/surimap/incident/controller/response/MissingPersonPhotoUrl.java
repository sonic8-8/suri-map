package com.surimap.incident.controller.response;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

@Component
public final class MissingPersonPhotoUrl {

  private static final String MOCK_UPLOAD_PATH = "/mock-upload/";

  private final String provider;
  private final String endpoint;
  private final String publicEndpoint;
  private final String bucket;

  public MissingPersonPhotoUrl(
      @Value("${surimap.object-storage.provider:mock}") String provider,
      @Value("${surimap.object-storage.endpoint:http://localhost:9000}") String endpoint,
      @Value("${surimap.object-storage.public-endpoint:}") String publicEndpoint,
      @Value("${surimap.object-storage.bucket:suri-map-photo}") String bucket) {
    this.provider = normalize(provider, "mock");
    this.endpoint = normalize(endpoint, "http://localhost:9000");
    this.publicEndpoint = normalize(publicEndpoint, "");
    this.bucket = normalize(bucket, "suri-map-photo");
  }

  static MissingPersonPhotoUrl mockStorage() {
    return new MissingPersonPhotoUrl("mock", "http://localhost:9000", "", "suri-map-photo");
  }

  public String fromObjectKey(String objectKey) {
    if (objectKey == null || objectKey.isBlank()) {
      return null;
    }
    String normalized = objectKey.trim();
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    if (normalized.isBlank()) {
      return null;
    }
    if ("mock".equals(provider.toLowerCase(Locale.ROOT))) {
      return MOCK_UPLOAD_PATH + encodePath(normalized);
    }
    return joinUrl(storagePublicBaseUrl(), bucket, normalized);
  }

  private String storagePublicBaseUrl() {
    return publicEndpoint.isBlank() ? endpoint : publicEndpoint;
  }

  private static String joinUrl(String baseUrl, String bucket, String objectKey) {
    String base = trimTrailingSlash(baseUrl);
    return base + "/" + encodeSegment(bucket) + "/" + encodePath(objectKey);
  }

  private static String encodePath(String value) {
    return Arrays.stream(value.split("/", -1))
        .map(segment -> UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
        .collect(Collectors.joining("/"));
  }

  private static String encodeSegment(String value) {
    return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
  }

  private static String trimTrailingSlash(String value) {
    String trimmed = value.trim();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }

  private static String normalize(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }
}
