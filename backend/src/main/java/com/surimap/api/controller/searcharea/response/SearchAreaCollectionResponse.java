package com.surimap.api.controller.searcharea.response;

import java.util.List;
import java.util.UUID;

public record SearchAreaCollectionResponse(
    UUID incidentId, long sourceVersion, List<SearchAreaResponse> areas)
    implements SearchAreaReadResponse {}
