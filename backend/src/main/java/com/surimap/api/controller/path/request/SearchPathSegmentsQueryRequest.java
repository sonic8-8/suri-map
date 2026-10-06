package com.surimap.api.controller.path.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.surimap.api.service.path.request.SearchPathPageServiceRequest;
import com.surimap.api.service.path.request.SearchPathPageServiceRequest.PathProgress;
import com.surimap.common.auth.SuriMapAuthentication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
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
public class SearchPathSegmentsQueryRequest {
  @NotNull private List<@NotNull UUID> opIds;
  @NotNull @Valid private List<@NotNull Path> paths;
  private UUID nextSearchPathId;

  public SearchPathPageServiceRequest toServiceRequest(
      UUID incidentId, SuriMapAuthentication authentication) {
    return SearchPathPageServiceRequest.builder()
        .incidentId(incidentId)
        .authentication(authentication)
        .opIds(opIds)
        .paths(paths.stream().map(Path::toServiceProgress).toList())
        .nextSearchPathId(nextSearchPathId)
        .build();
  }

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Path {
    @NotNull private UUID id;
    @Valid private Progress segmentsProgress;

    public PathProgress toServiceProgress() {
      PathProgress.PathProgressBuilder builder = PathProgress.builder().id(id);
      if (segmentsProgress != null) {
        builder
            .beforeStartPointOrder(segmentsProgress.getBeforeStartPointOrder())
            .completed(segmentsProgress.getCompleted());
      }
      return builder.build();
    }
  }

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Progress {
    @JsonDeserialize(using = SearchPathQueryDeserializers.PointOrder.class)
    @PositiveOrZero
    private Integer beforeStartPointOrder;

    @NotNull private Boolean completed;
  }
}
