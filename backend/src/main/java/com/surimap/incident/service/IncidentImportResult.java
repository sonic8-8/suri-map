package com.surimap.incident.service;

import java.util.List;
import java.util.UUID;

/** 사건 가져오기 use case 결과 — service가 반환하면 controller가 ImportIncidentResponse로 변환한다. */
public record IncidentImportResult(
    UUID id, UUID incidentId, String status, long version, List<String> assignmentAccountIds) {}
