package com.surimap.incident.controller.response;

import java.nio.charset.StandardCharsets;
import org.springframework.web.util.UriUtils;

final class MissingPersonPhotoUrls {

  private static final String MOCK_UPLOAD_PREFIX = "/mock-upload/";
  private static final String HTTP_PREFIX = "http://";
  private static final String HTTPS_PREFIX = "https://";

  private MissingPersonPhotoUrls() {}

  static String fromObjectKey(String photoObjectKey) {
    if (photoObjectKey == null || photoObjectKey.isBlank()) {
      return null;
    }
    if (photoObjectKey.startsWith(HTTP_PREFIX) || photoObjectKey.startsWith(HTTPS_PREFIX)) {
      return photoObjectKey;
    }
    return MOCK_UPLOAD_PREFIX + UriUtils.encodePath(photoObjectKey, StandardCharsets.UTF_8);
  }
}
