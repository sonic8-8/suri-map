package com.surimap.external;

import java.util.List;

/**
 * 외부 사건 원천 시스템 어댑터 인터페이스.
 *
 * mock 112뿐 아니라 실제 112 시스템이 연동될 때도
 * 이 인터페이스를 구현하면 된다.
 */
public interface ExternalIncidentAdapter {

    /**
     * READY 상태(아직 import 안 된) 사건 목록을 조회한다.
     */
    List<ExternalIncident> fetchReadyIncidents();

    /**
     * 특정 사건의 상세 정보를 조회한다.
     */
    ExternalIncident fetchIncident(String sourceIncidentId);

    /**
     * 사건을 IMPORTED로 마킹한다 (import 완료 통보).
     */
    void markImported(String sourceIncidentId);
}
