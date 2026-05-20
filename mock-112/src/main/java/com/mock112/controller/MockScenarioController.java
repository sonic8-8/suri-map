package com.mock112.controller;

import com.mock112.domain.MockAssignment;
import com.mock112.domain.MockIncident;
import com.mock112.seed.SeedDataLoader;
import com.mock112.store.MockIncidentStore;
import com.mock112.webhook.SuriMapWebhookDispatcher;
import com.mock112.webhook.WebhookDeliveryResult;
import com.mock112.webhook.WebhookOutboxStore;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 시연용 시나리오·리셋 API.
 *
 * - 대표 시나리오 seed 1클릭 적재
 * - 실종팀 인계 / 지원 부대 배정 1클릭
 * - 전체 초기화
 */
@RestController
@RequestMapping("/mock-112")
public class MockScenarioController {

    private final MockIncidentStore store;
    private final SeedDataLoader seedDataLoader;
    private final SuriMapWebhookDispatcher webhookDispatcher;
    private final WebhookOutboxStore webhookOutboxStore;

    public MockScenarioController(
            MockIncidentStore store,
            SeedDataLoader seedDataLoader,
            SuriMapWebhookDispatcher webhookDispatcher,
            WebhookOutboxStore webhookOutboxStore) {
        this.store = store;
        this.seedDataLoader = seedDataLoader;
        this.webhookDispatcher = webhookDispatcher;
        this.webhookOutboxStore = webhookOutboxStore;
    }

    /**
     * 대표 시연 시나리오를 적재한다.
     * POST /mock-112/scenarios/precinct-first
     */
    @PostMapping("/scenarios/precinct-first")
    public ResponseEntity<Map<String, Object>> loadPrecinctFirst() {
        try {
            MockIncident incident = seedDataLoader.loadPrecinctFirstScenario(store);
            WebhookDeliveryResult delivery = webhookDispatcher.sendIncidentReady(incident);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "precinct-first 시나리오가 적재되었습니다.");
            body.put("sourceIncidentId", incident.getSourceIncidentId());
            body.put("caseNumber", incident.getCaseNumber());
            body.put("assignmentCount", incident.getAssignments().size());
            body.put("seedMarkerCount", incident.getSeedMarkers().size());
            body.put("webhookDelivery", delivery);
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "already_loaded", "message", e.getMessage()));
        }
    }

    /**
     * 실종팀 인계 배정을 추가한다.
     * POST /mock-112/incidents/{sourceIncidentId}/handover-to-missing-team
     */
    @PostMapping("/incidents/{sourceIncidentId}/handover-to-missing-team")
    public ResponseEntity<Map<String, Object>> handoverToMissingTeam(
            @PathVariable String sourceIncidentId) {
        try {
            List<MockAssignment> added = seedDataLoader.loadHandoverAssignments(store, sourceIncidentId);
            WebhookDeliveryResult delivery = webhookDispatcher.sendAssignmentChanged(sourceIncidentId, added);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "실종팀 인계 배정이 추가되었습니다.");
            body.put("sourceIncidentId", sourceIncidentId);
            body.put("addedCount", added.size());
            body.put("assignments", added);
            body.put("webhookDelivery", delivery);
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "failed", "message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "incident_closed", "message", e.getMessage()));
        }
    }

    /**
     * 지원 부대 배정을 추가한다.
     * POST /mock-112/incidents/{sourceIncidentId}/add-support-unit
     */
    @PostMapping("/incidents/{sourceIncidentId}/add-support-unit")
    public ResponseEntity<Map<String, Object>> addSupportUnit(
            @PathVariable String sourceIncidentId) {
        try {
            List<MockAssignment> added = seedDataLoader.loadSupportAssignments(store, sourceIncidentId);
            WebhookDeliveryResult delivery = webhookDispatcher.sendAssignmentChanged(sourceIncidentId, added);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", "지원 부대 배정이 추가되었습니다.");
            body.put("sourceIncidentId", sourceIncidentId);
            body.put("addedCount", added.size());
            body.put("assignments", added);
            body.put("webhookDelivery", delivery);
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "failed", "message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "incident_closed", "message", e.getMessage()));
        }
    }

    /**
     * 전체 상태를 초기화한다.
     * POST /mock-112/reset
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> reset() {
        store.reset();
        webhookOutboxStore.reset();
        return ResponseEntity.ok(Map.of("message", "mock 112 상태가 초기화되었습니다."));
    }

    /**
     * 서버 상태 확인.
     * GET /mock-112/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("incidentCount", store.size());
        body.put("readyCount", store.findByStatus("READY").size());
        body.put("importedCount", store.findByStatus("IMPORTED").size());
        body.put("closedCount", store.findByStatus("CLOSED").size());
        body.put("webhookOutbox", webhookOutboxStore.countByStatus());
        return ResponseEntity.ok(body);
    }
}
