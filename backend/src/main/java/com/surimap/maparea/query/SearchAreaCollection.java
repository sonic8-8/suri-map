package com.surimap.maparea.query;

import java.util.List;
import java.util.UUID;

/**
 * SearchAreaQuery.byIncident / byOp 응답 컬렉션 (S2.json §service_contracts).
 *
 * <p>sourceVersion은 결과 내 area.version의 최댓값이다.
 */
public record SearchAreaCollection(
    UUID incidentId, long sourceVersion, List<SearchAreaRow> areas) {}
