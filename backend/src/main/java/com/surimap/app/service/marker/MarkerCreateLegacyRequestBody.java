package com.surimap.app.service.marker;

import com.surimap.app.service.marker.request.MarkerCreateServiceRequest;
import com.surimap.app.service.photo.request.MarkerCreatePhotoServiceRequest;
import java.util.StringJoiner;

// ponytail: 과거 POST /api/markers 해시 비교 전용이다. 해당 처리 기록이 없음을 확인한 뒤 제거한다.
final class MarkerCreateLegacyRequestBody {

  private MarkerCreateLegacyRequestBody() {}

  static String format(MarkerCreateServiceRequest request) {
    StringJoiner photos = new StringJoiner(", ", "[", "]");
    for (MarkerCreatePhotoServiceRequest photo : request.getPhotos()) {
      photos.add(
          ("MarkerCreatePhotoRequest[photoId=%s, sizeBytes=%s, contentType=%s, "
                  + "width=%s, height=%s, checksumSha256=%s]")
              .formatted(
                  photo.getPhotoId(),
                  photo.getSizeBytes(),
                  photo.getContentType(),
                  photo.getWidth(),
                  photo.getHeight(),
                  photo.getChecksumSha256()));
    }
    return ("create:MarkerCreateRequest[id=%s, incidentId=%s, opId=%s, type=%s, "
            + "location=MarkerGeoJsonPoint[type=%s, coordinates=%s], supportRequestType=%s, "
            + "memo=%s, clientTs=%s, clockOffsetMs=%s, photos=%s]")
        .formatted(
            request.getId(),
            request.getIncidentId(),
            request.getOpId(),
            request.getType(),
            request.getLocation().getType(),
            request.getLocation().getCoordinates(),
            request.getSupportRequestType(),
            request.getMemo(),
            request.getClientTs(),
            request.getClockOffsetMs(),
            photos);
  }
}
