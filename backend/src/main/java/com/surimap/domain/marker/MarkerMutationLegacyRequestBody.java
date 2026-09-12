package com.surimap.domain.marker;

import com.surimap.global.geometry.GeoJsonPoint;
import java.util.UUID;

// ponytail: 과거 PATCH/DELETE 요청 해시 비교 전용이다. 해당 처리 기록이 없음을 확인한 뒤 제거한다.
public final class MarkerMutationLegacyRequestBody {

  private MarkerMutationLegacyRequestBody() {}

  public static String formatUpdate(
      UUID markerId, Long version, GeoJsonPoint location, String memo, String type) {
    return "update:%s:MarkerUpdateRequest[version=%s, location=%s, memo=%s, type=%s]"
        .formatted(markerId, version, formatLocation(location), memo, type);
  }

  public static String formatDelete(UUID markerId, Long version, String reason) {
    return "delete:%s:MarkerDeleteRequest[version=%s, reason=%s]"
        .formatted(markerId, version, reason);
  }

  private static String formatLocation(GeoJsonPoint location) {
    if (location == null) {
      return "null";
    }
    return "MarkerGeoJsonPoint[type=%s, coordinates=%s]"
        .formatted(location.getType(), location.getCoordinates());
  }
}
