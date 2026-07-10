package com.surimap.api.service.path.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathQueryServiceResponse {

  private List<SearchPathQueryRowServiceResponse> paths;

  @Builder
  private SearchPathQueryServiceResponse(List<SearchPathQueryRowServiceResponse> paths) {
    this.paths = paths;
  }
}
