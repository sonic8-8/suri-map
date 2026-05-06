package com.surimap.external.mock112;

import com.surimap.external.ExternalAssignment;
import com.surimap.external.ExternalIncident;
import com.surimap.external.ExternalIncidentAdapter;
import com.surimap.external.ExternalMissingPerson;
import com.surimap.external.ExternalSeedMarker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * mock 112 서버 HTTP 클라이언트.
 *
 * mock112.enabled=true 일 때만 활성화된다.
 * MOCK_112_BASE_URL 환경변수로 mock 112 서버 주소를 설정한다.
 */
@Component
@ConditionalOnProperty(name = "mock112.enabled", havingValue = "true")
public class Mock112HttpAdapter implements ExternalIncidentAdapter {

    private static final Logger log = LoggerFactory.getLogger(Mock112HttpAdapter.class);

    private final RestTemplate restTemplate;
    private final Mock112Config.Mock112Properties properties;

    public Mock112HttpAdapter(RestTemplate mock112RestTemplate,
                               Mock112Config.Mock112Properties mock112Properties) {
        this.restTemplate = mock112RestTemplate;
        this.properties = mock112Properties;
    }

    @Override
    public List<ExternalIncident> fetchReadyIncidents() {
        String url = properties.getBaseUrl() + "/mock-112/incidents?status=READY";
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {});

            List<Map<String, Object>> body = response.getBody();
            if (body == null || body.isEmpty()) {
                return Collections.emptyList();
            }

            return body.stream()
                    .map(this::mapToExternalIncident)
                    .toList();
        } catch (Exception e) {
            log.warn("mock 112 polling 실패: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public ExternalIncident fetchIncident(String sourceIncidentId) {
        String url = properties.getBaseUrl() + "/mock-112/incidents/" + sourceIncidentId;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restTemplate.getForObject(url, Map.class);
            if (body == null) {
                throw new RuntimeException("mock 112 사건 조회 실패: " + sourceIncidentId);
            }
            return mapToExternalIncident(body);
        } catch (Exception e) {
            log.error("mock 112 사건 상세 조회 실패: {}", sourceIncidentId, e);
            throw new RuntimeException("mock 112 사건 조회 실패: " + sourceIncidentId, e);
        }
    }

    @Override
    public void markImported(String sourceIncidentId) {
        String url = properties.getBaseUrl() + "/mock-112/incidents/" + sourceIncidentId + "/mark-imported";
        try {
            restTemplate.postForObject(url, null, Map.class);
            log.info("mock 112 사건 IMPORTED 마킹 완료: {}", sourceIncidentId);
        } catch (Exception e) {
            log.warn("mock 112 IMPORTED 마킹 실패 (비치명적): {}", e.getMessage());
        }
    }

    // ── 내부 매핑 ──

    @SuppressWarnings("unchecked")
    private ExternalIncident mapToExternalIncident(Map<String, Object> raw) {
        Map<String, Object> mp = (Map<String, Object>) raw.get("missingPerson");
        ExternalMissingPerson missingPerson = mp != null
                ? new ExternalMissingPerson(
                    (String) mp.get("displayName"),
                    (String) mp.get("photoObjectKey"),
                    (String) mp.get("appearanceText"),
                    (String) mp.get("lastSeenLocationText"),
                    parseDateTime(mp.get("lastSeenAt")))
                : null;

        List<Map<String, Object>> assignmentsRaw = (List<Map<String, Object>>) raw.get("assignments");
        List<ExternalAssignment> assignments = assignmentsRaw != null
                ? assignmentsRaw.stream().map(a -> new ExternalAssignment(
                    (String) a.get("externalAssignmentKey"),
                    (String) a.get("accountId"),
                    (String) a.get("incidentRole"),
                    parseDateTime(a.get("assignedAt")))).toList()
                : Collections.emptyList();

        List<Map<String, Object>> markersRaw = (List<Map<String, Object>>) raw.get("seedMarkers");
        List<ExternalSeedMarker> seedMarkers = markersRaw != null
                ? markersRaw.stream().map(m -> new ExternalSeedMarker(
                    (String) m.get("type"),
                    (String) m.get("source"),
                    (String) m.get("memo"),
                    ((Number) m.get("lon")).doubleValue(),
                    ((Number) m.get("lat")).doubleValue())).toList()
                : Collections.emptyList();

        return new ExternalIncident(
                (String) raw.get("sourceIncidentId"),
                (String) raw.get("title"),
                parseDateTime(raw.get("openedAt")),
                (String) raw.get("status"),
                missingPerson,
                assignments,
                seedMarkers);
    }

    private OffsetDateTime parseDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof String s) return OffsetDateTime.parse(s);
        return null;
    }
}
