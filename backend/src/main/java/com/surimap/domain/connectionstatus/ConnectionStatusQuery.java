package com.surimap.domain.connectionstatus;

import java.util.List;
import java.util.UUID;

/** 상황판 connection_status section에 쓰는 사건별 현장 계정의 통신 상태 조회. */
public interface ConnectionStatusQuery {

  List<ConnectionStatusRow> byIncident(UUID incidentId);
}
