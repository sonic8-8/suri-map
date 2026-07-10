package com.surimap.app.controller.path.response;

import com.surimap.domain.path.SearchPath;
import com.surimap.domain.path.SearchPathStatus;
import java.util.UUID;

public record PatchSearchPathResponse(UUID id, long version, SearchPathStatus status) {

  public static PatchSearchPathResponse from(SearchPath path) {
    return new PatchSearchPathResponse(path.getId(), path.getVersion(), path.getStatus());
  }
}
