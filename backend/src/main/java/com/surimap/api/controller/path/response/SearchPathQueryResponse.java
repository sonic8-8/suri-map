package com.surimap.api.controller.path.response;

import com.surimap.api.service.path.response.SearchPathQueryServiceResponse;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathQueryResponse {

  private List<SearchPathQueryRowResponse> paths;

  @Builder
  private SearchPathQueryResponse(List<SearchPathQueryRowResponse> paths) {
    this.paths = paths;
  }

  public static SearchPathQueryResponse from(SearchPathQueryServiceResponse response) {
    return SearchPathQueryResponse.builder()
        .paths(response.getPaths().stream().map(SearchPathQueryRowResponse::from).toList())
        .build();
  }
}
