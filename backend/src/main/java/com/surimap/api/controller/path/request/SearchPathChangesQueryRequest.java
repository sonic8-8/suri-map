package com.surimap.api.controller.path.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.surimap.api.service.path.request.SearchPathPageServiceRequest;
import com.surimap.api.service.path.request.SearchPathPageServiceRequest.PathProgress;
import com.surimap.common.auth.SuriMapAuthentication;
import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
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
public class SearchPathChangesQueryRequest {
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

    @JsonDeserialize(using = SearchPathQueryDeserializers.Version.class)
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String baselineVersion;

    @Valid private Progress changesProgress;

    public PathProgress toServiceProgress() {
      PathProgress.PathProgressBuilder builder =
          PathProgress.builder().id(id).baselineVersion(parseVersion(baselineVersion));
      if (changesProgress != null) {
        builder
            .beforeStartPointOrder(changesProgress.getBeforeStartPointOrder())
            .completed(changesProgress.getCompleted())
            .appliedVersion(parseVersion(changesProgress.getAppliedVersion()))
            .targetVersion(parseVersion(changesProgress.getTargetVersion()));
      }
      return builder.build();
    }
  }

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Progress {
    @JsonDeserialize(using = SearchPathQueryDeserializers.Version.class)
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String appliedVersion;

    @JsonDeserialize(using = SearchPathQueryDeserializers.Version.class)
    @NotNull
    @Pattern(regexp = "[1-9][0-9]{0,18}")
    private String targetVersion;

    @JsonDeserialize(using = SearchPathQueryDeserializers.PointOrder.class)
    @PositiveOrZero
    private Integer beforeStartPointOrder;

    @NotNull private Boolean completed;
  }

  private static Long parseVersion(String version) {
    if (version == null) {
      return null;
    }
    try {
      return Long.parseLong(version);
    } catch (NumberFormatException exception) {
      throw new BusinessException(ErrorCode.INVALID_SEARCH_PATH_QUERY);
    }
  }
}
