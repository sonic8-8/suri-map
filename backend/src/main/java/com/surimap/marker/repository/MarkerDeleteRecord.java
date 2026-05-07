package com.surimap.marker.repository;

import com.surimap.marker.domain.MarkerStatus;
import java.util.UUID;

public record MarkerDeleteRecord(
    UUID id, long expectedVersion, MarkerStatus status, long version) {}
