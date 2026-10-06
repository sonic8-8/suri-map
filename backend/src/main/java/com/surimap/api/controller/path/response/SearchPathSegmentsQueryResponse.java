package com.surimap.api.controller.path.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.surimap.api.service.path.response.SearchPathPageServiceResponse;
import com.surimap.domain.path.SearchPathStatus;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchPathSegmentsQueryResponse {
  private List<Path> paths;
  private UUID nextSearchPathId;
  private boolean hasMore;

  public static SearchPathSegmentsQueryResponse from(SearchPathPageServiceResponse response) {
    return SearchPathSegmentsQueryResponse.builder()
        .paths(response.getPaths().stream().map(Path::from).toList())
        .nextSearchPathId(response.getNextSearchPathId())
        .hasMore(response.isHasMore())
        .build();
  }

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Path {
    private UUID id;
    private UUID accountId;
    private UUID opId;
    private SearchPathStatus status;
    private String version;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String baselineVersion;

    private List<SearchPathPageSegmentResponse> segments;
    private Progress segmentsProgress;

    public static Path from(SearchPathPageServiceResponse.Path path) {
      Progress progress = null;
      if (path.getCompleted() != null) {
        progress =
            Progress.builder()
                .beforeStartPointOrder(path.getBeforeStartPointOrder())
                .completed(path.getCompleted())
                .build();
      }
      return Path.builder()
          .id(path.getId())
          .accountId(path.getAccountId())
          .opId(path.getOpId())
          .status(path.getStatus())
          .version(Long.toString(path.getVersion()))
          .baselineVersion(
              path.getBaselineVersion() == null ? null : path.getBaselineVersion().toString())
          .segments(path.getSegments().stream().map(SearchPathPageSegmentResponse::from).toList())
          .segmentsProgress(progress)
          .build();
    }
  }

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Progress {
    private Integer beforeStartPointOrder;
    private boolean completed;
  }
}
