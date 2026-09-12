package com.surimap.app.controller.marker.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.surimap.app.service.marker.request.MarkerCreateServiceRequest;
import com.surimap.global.auth.SuriMapAuthentication;
import com.surimap.global.geometry.GeoJsonPoint;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MarkerCreateRequest {

  private UUID id;
  @NotNull private UUID incidentId;
  @NotNull private UUID opId;
  @NotNull private String type;
  @NotNull private GeoJsonPoint location;
  private String supportRequestType;
  private String memo;
  @NotNull private Instant clientTs;
  private Long clockOffsetMs;

  @JsonSetter(nulls = Nulls.AS_EMPTY, contentNulls = Nulls.FAIL)
  private List<MarkerCreatePhotoRequest> photos = List.of();

  @Builder
  private MarkerCreateRequest(
      UUID id,
      UUID incidentId,
      UUID opId,
      String type,
      GeoJsonPoint location,
      String supportRequestType,
      String memo,
      Instant clientTs,
      Long clockOffsetMs,
      List<MarkerCreatePhotoRequest> photos) {
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
  }

  public MarkerCreateServiceRequest toServiceRequest(
      SuriMapAuthentication authentication, String idempotencyKey) {
    return MarkerCreateServiceRequest.builder()
        .id(id)
        .incidentId(incidentId)
        .opId(opId)
        .type(type)
        .location(location)
        .supportRequestType(supportRequestType)
        .memo(memo)
        .clientTs(clientTs)
        .clockOffsetMs(clockOffsetMs)
        .photos(photos.stream().map(MarkerCreatePhotoRequest::toServiceRequest).toList())
        .authentication(authentication)
        .idempotencyKey(idempotencyKey)
        .build();
  }
}
