package com.surimap.eventhub.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 이벤트 발행 요청 DTO.
 *
 * <p>boundaries.md §9.1 BaseEvent envelope 형식을 구성하는 모든 필드를 포함한다:
 * eventId, incidentId, type, payloadFormatVersion(=schemaVersion), occurredAt(=serverTs),
 * sourceEntityType, sourceEntityId, payload.
 */
public record PublishRequest(
    UUID eventId,
    UUID incidentId,
    String type,
    int payloadFormatVersion,
    String sourceEntityType,
    UUID sourceEntityId,
    Instant occurredAt,
    Map<String, Object> payload) {}
