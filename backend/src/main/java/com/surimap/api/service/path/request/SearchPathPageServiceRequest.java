package com.surimap.api.service.path.request;

import com.surimap.common.auth.SuriMapAuthentication;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SearchPathPageServiceRequest {
  private UUID incidentId;
  private SuriMapAuthentication authentication;
  private List<UUID> opIds;
  private List<PathProgress> paths;
  private UUID nextSearchPathId;

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PathProgress {
    private UUID id;
    private Long baselineVersion;
    private Long appliedVersion;
    private Long targetVersion;
    private Integer beforeStartPointOrder;
    // null: 아직 서버에서 진행 객체를 받지 않았다.
    private Boolean completed;
  }
}
