package com.surimap.app.service.marker.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.surimap.app.service.photo.request.MarkerCreatePhotoServiceRequest;
import com.surimap.global.auth.SuriMapAuthentication;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonPropertyOrder({
  "id",
  "incidentId",
  "opId",
  "type",
  "location",
  "supportRequestType",
  "memo",
  "clientTs",
  "clockOffsetMs",
  "photos"
})
public class MarkerCreateServiceRequest {

  private UUID id;
  private UUID incidentId;
  private UUID opId;
  private String type;
  private MarkerGeoJsonPoint location;
  private String supportRequestType;
  private String memo;
  private Instant clientTs;
  private Long clockOffsetMs;
  private List<MarkerCreatePhotoServiceRequest> photos = List.of();

  // 인증 정보와 요청 키는 HTTP 본문이 아니므로 요청 내용 비교에서 제외한다.
  @JsonIgnore private SuriMapAuthentication authentication;
  @JsonIgnore private String idempotencyKey;

  @Builder(toBuilder = true)
  private MarkerCreateServiceRequest(
      UUID id,
      UUID incidentId,
      UUID opId,
      String type,
      MarkerGeoJsonPoint location,
      String supportRequestType,
      String memo,
      Instant clientTs,
      Long clockOffsetMs,
      List<MarkerCreatePhotoServiceRequest> photos,
      SuriMapAuthentication authentication,
      String idempotencyKey) {
    this.id = id;
    this.incidentId = incidentId;
    this.opId = opId;
    this.type = type;
    this.location = location;
    this.supportRequestType = supportRequestType;
    this.memo = memo;
    this.clientTs = clientTs;
    this.clockOffsetMs = clockOffsetMs;
    this.photos = photos == null ? List.of() : List.copyOf(photos);
    this.authentication = authentication;
    this.idempotencyKey = idempotencyKey;
  }
}
