package com.surimap.api.controller.searcharea.response;

import java.util.List;
import java.util.UUID;

public record SearchAreaSplitResponse(
    UUID parentAreaId,
    SearchAreaResponse parent,
    List<UUID> createdAreaIds,
    List<SearchAreaResponse> children) {}
