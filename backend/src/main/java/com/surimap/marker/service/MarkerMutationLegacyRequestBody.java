package com.surimap.marker.service;

import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.service.request.MarkerDeleteServiceRequest;
import com.surimap.marker.service.request.MarkerUpdateServiceRequest;

// ponytail: 과거 PATCH/DELETE 요청 해시 비교 전용이다. 해당 처리 기록이 없음을 확인한 뒤 제거한다.
final class MarkerMutationLegacyRequestBody {

  private MarkerMutationLegacyRequestBody() {}

  static String formatUpdate(MarkerUpdateServiceRequest request) {
    return "update:%s:MarkerUpdateRequest[version=%s, location=%s, memo=%s, type=%s]"
        .formatted(
            request.getMarkerId(),
            request.getVersion(),
            formatLocation(request.getLocation()),
            request.getMemo(),
            request.getType());
  }

  static String formatDelete(MarkerDeleteServiceRequest request) {
    return "delete:%s:MarkerDeleteRequest[version=%s, reason=%s]"
        .formatted(request.getMarkerId(), request.getVersion(), request.getReason());
  }

  private static String formatLocation(MarkerGeoJsonPoint location) {
    if (location == null) {
      return "null";
    }
    return "MarkerGeoJsonPoint[type=%s, coordinates=%s]"
        .formatted(location.type(), location.coordinates());
  }
}
