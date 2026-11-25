package com.surimap.app.service.marker.response;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.surimap.app.service.photo.response.PhotoAttachServiceResponse;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MarkerCreateServiceResponse {

  private UUID id;
  private UUID incidentId;
  private UUID opId;
  private String status;
  private long version;

  @JsonSetter(nulls = Nulls.AS_EMPTY, contentNulls = Nulls.FAIL)
  private List<PhotoAttachServiceResponse> photos = List.of();

  @Builder
  private MarkerCreateServiceResponse(
      UUID id,
      UUID incidentId,
      UUID opId,
      String status,
      long version,
      List<PhotoAttachServiceResponse> photos) {
    this.id = id;
    this.incidentId = incidentId;
    this.opId = opId;
    this.status = status;
    this.version = version;
    this.photos = photos == null ? List.of() : List.copyOf(photos);
  }
}
