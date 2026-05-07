package com.surimap.external;

import java.util.List;

/**
 * 외부 사건 원천 시스템 어댑터 인터페이스.
 *
 * <p>mock 112뿐 아니라 실제 112 시스템이 연동될 때도 이 인터페이스를 구현하면 된다.
 */
public interface ExternalIncidentAdapter {

  /** polling 대상인 READY 상태 사건 목록을 조회한다. */
  List<ExternalIncident> fetchReadyIncidents();

  /** import 요청의 sourceIncidentId에 해당하는 외부 사건 상세를 조회한다. */
  ExternalIncident fetchIncident(String sourceIncidentId);

  /** 외부 원천 시스템에 import 완료를 통보한다. */
  void markImported(String sourceIncidentId);
}
