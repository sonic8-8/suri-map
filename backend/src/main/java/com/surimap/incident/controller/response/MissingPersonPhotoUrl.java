package com.surimap.incident.controller.response;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.web.util.UriUtils;

final class MissingPersonPhotoUrl {

  private static final String PUBLIC_BUCKET_PATH = "/suri-map-photo/";

  private MissingPersonPhotoUrl() {}

  static String fromObjectKey(String objectKey) {
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
    return PUBLIC_BUCKET_PATH + encodePath(normalized);
  }

  private static String encodePath(String value) {
    return Arrays.stream(value.split("/", -1))
        .map(segment -> UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
        .collect(Collectors.joining("/"));
  }
}
