package com.surimap.app.service.path.response;

import com.surimap.domain.path.SearchPath;
import com.surimap.domain.path.SearchPathStatus;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathStatusUpdateServiceResponse {

  private UUID id;
  private long version;
  private SearchPathStatus status;

  @Builder
  private SearchPathStatusUpdateServiceResponse(UUID id, long version, SearchPathStatus status) {
    this.id = id;
    this.version = version;
    this.status = status;
  }

  public static SearchPathStatusUpdateServiceResponse from(SearchPath path) {
    return SearchPathStatusUpdateServiceResponse.builder()
        .id(path.getId())
        .version(path.getVersion())
        .status(path.getStatus())
        .build();
  }
}
