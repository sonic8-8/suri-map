package com.surimap.app.controller.marker.response;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.surimap.app.service.marker.response.MarkerCreateServiceResponse;
import com.surimap.marker.dto.MarkerCreatePhotoResponse;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MarkerCreateResponse {

  private UUID id;
  private UUID incidentId;
  private UUID opId;
  private UUID policePhoneId;
  private String status;
  private long version;

  @JsonSetter(nulls = Nulls.AS_EMPTY, contentNulls = Nulls.FAIL)
  private List<MarkerCreatePhotoResponse> photos = List.of();

  @Builder
  private MarkerCreateResponse(
      UUID id,
      UUID incidentId,
      UUID opId,
      UUID policePhoneId,
      String status,
      long version,
      List<MarkerCreatePhotoResponse> photos) {
    this.id = id;
    this.incidentId = incidentId;
    this.opId = opId;
    this.policePhoneId = policePhoneId;
    this.status = status;
    this.version = version;
    this.photos = photos == null ? List.of() : List.copyOf(photos);
  }

  public static MarkerCreateResponse from(MarkerCreateServiceResponse response) {
    return MarkerCreateResponse.builder()
        .id(response.getId())
        .incidentId(response.getIncidentId())
        .opId(response.getOpId())
        .policePhoneId(response.getPolicePhoneId())
        .status(response.getStatus())
        .version(response.getVersion())
        .photos(response.getPhotos())
        .build();
  }
}
