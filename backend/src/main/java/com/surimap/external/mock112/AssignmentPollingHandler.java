package com.surimap.external.mock112;

import com.surimap.external.ExternalAssignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * mock 112 배정 변경을 감지하여 incident_assignment에 반영한다.
 *
 * 중복 처리: externalAssignmentKey 기준.
 * 실제 DB write는 IncidentImportService(또는 해당 서비스)를 통해 수행된다.
 * 현재는 감지·로깅만 수행하고, DB 연동은 L1-T04에서 완성한다.
 */
@Component
public class AssignmentPollingHandler {

    private static final Logger log = LoggerFactory.getLogger(AssignmentPollingHandler.class);

    /**
     * 이미 처리된 externalAssignmentKey를 추적.
     * 서버 재시작 시 초기화되므로 DB 기반 중복 체크가 최종 방어선.
     */
    private final Set<String> processedKeys = ConcurrentHashMap.newKeySet();

    /**
     * 배정 변경을 처리한다.
     *
     * @param sourceIncidentId mock 112 사건 ID
     * @param assignments 현재 전체 배정 목록
     */
    public void handleAssignmentChanges(String sourceIncidentId,
                                         List<ExternalAssignment> assignments) {
        for (ExternalAssignment assignment : assignments) {
            String key = assignment.externalAssignmentKey();
            if (processedKeys.add(key)) {
                // 새로 감지된 배정
                log.info("새 배정 감지: incident={}, account={}, role={}, key={}",
                        sourceIncidentId,
                        assignment.accountId(),
                        assignment.incidentRole(),
                        key);

                // TODO(L1-T04): 여기서 실제 incident_assignment INSERT +
                //   INCIDENT_ASSIGNMENT_CHANGED 이벤트 발행을 수행한다.
                //   현재는 감지·로깅만 수행.
            }
        }
    }

    /**
     * 특정 사건의 초기 배정을 일괄 등록한다 (import 시 사용).
     */
    public void registerInitialAssignments(List<ExternalAssignment> assignments) {
        for (ExternalAssignment a : assignments) {
            processedKeys.add(a.externalAssignmentKey());
        }
    }

    public void reset() {
        processedKeys.clear();
    }
}
