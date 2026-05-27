package com.surimap.app.controller.map.response;

import java.util.List;
import java.util.UUID;

public record AppMapRevisionResponse(
    UUID incidentId, UUID opId, List<SourceRevision> sources) {

  public record SourceRevision(String source, String revision) {}
}
