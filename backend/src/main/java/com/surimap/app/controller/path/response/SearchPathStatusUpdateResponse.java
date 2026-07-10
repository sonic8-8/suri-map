package com.surimap.app.controller.path.response;

import com.surimap.app.service.path.response.SearchPathStatusUpdateServiceResponse;
import com.surimap.domain.path.SearchPathStatus;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathStatusUpdateResponse {

  private UUID id;
  private long version;
  private SearchPathStatus status;

  @Builder
  private SearchPathStatusUpdateResponse(UUID id, long version, SearchPathStatus status) {
    this.id = id;
    this.version = version;
    this.status = status;
  }

  public static SearchPathStatusUpdateResponse from(
      SearchPathStatusUpdateServiceResponse response) {
    return SearchPathStatusUpdateResponse.builder()
        .id(response.getId())
        .version(response.getVersion())
        .status(response.getStatus())
        .build();
  }
}
