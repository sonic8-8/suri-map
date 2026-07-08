package com.surimap.api.controller.path.response;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.surimap.domain.path.MovementType;
import com.surimap.domain.path.MovementTypeSource;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PathQuerySegmentRow(
    String id,
    long version,
    MovementType movementType,
    MovementTypeSource movementTypeSource,
    @JsonSerialize(converter = LineStringGeometryJsonConverter.class)
    @JsonDeserialize(using = LineStringCoordinatesDeserializer.class)
    List<List<Double>> geometry,
    OffsetDateTime startedAt,
    OffsetDateTime endedAt,
    UUID correctedByAccountId,
    OffsetDateTime correctedAt) {}
