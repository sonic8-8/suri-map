package com.mock112.controller;

import com.mock112.domain.MockAssignment;
import com.mock112.domain.MockIncident;
import com.mock112.store.InMemoryIncidentStore;
import com.mock112.webhook.SuriMapWebhookDispatcher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * mock 112 사건 등록·조회·배정 API.
 *
 * Suri-Map은 이 API를 polling하여 새 배정 사건을 감지하고,
 * POST /api/incidents/import로 내부 사건을 생성한다.
 */
@RestController
@RequestMapping("/mock-112/incidents")
public class MockIncidentController {

    private final InMemoryIncidentStore store;
    private final SuriMapWebhookDispatcher webhookDispatcher;

    public MockIncidentController(
            InMemoryIncidentStore store,
            SuriMapWebhookDispatcher webhookDispatcher) {
        this.store = store;
        this.webhookDispatcher = webhookDispatcher;
    }

    /**
     * 사건 등록.
     * POST /mock-112/incidents
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createIncident(@RequestBody MockIncident incident) {
        try {
            store.save(incident);
            webhookDispatcher.sendIncidentReady(incident);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("sourceIncidentId", incident.getSourceIncidentId());
            body.put("status", incident.getStatus());
            body.put("message", "사건이 등록되었습니다.");
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (IllegalArgumentException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "duplicate_incident");
            body.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }
    }

    /**
     * 사건 목록 조회. status 파라미터로 필터 가능.
     * GET /mock-112/incidents
     * GET /mock-112/incidents?status=READY
     */
    @GetMapping
    public ResponseEntity<List<MockIncident>> listIncidents(
            @RequestParam(required = false) String status) {
        List<MockIncident> result;
        if (status != null && !status.isBlank()) {
            result = store.findByStatus(status);
        } else {
            result = store.findAll();
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 사건 상세 조회.
     * GET /mock-112/incidents/{sourceIncidentId}
     */
    @GetMapping("/{sourceIncidentId}")
    public ResponseEntity<?> getIncident(@PathVariable String sourceIncidentId) {
        return store.findById(sourceIncidentId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> {
                    Map<String, String> body = Map.of(
                            "error", "not_found",
                            "message", "사건을 찾을 수 없습니다: " + sourceIncidentId);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
                });
    }

    /**
     * Suri-Map이 import 완료를 알린다.
     * POST /mock-112/incidents/{sourceIncidentId}/mark-imported
     */
    @PostMapping("/{sourceIncidentId}/mark-imported")
    public ResponseEntity<Map<String, String>> markImported(@PathVariable String sourceIncidentId) {
        try {
            store.markImported(sourceIncidentId);
            return ResponseEntity.ok(Map.of(
                    "sourceIncidentId", sourceIncidentId,
                    "status", "IMPORTED"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "not_found", "message", e.getMessage()));
        }
    }

    /**
     * 사건에 배정을 추가한다 (범용).
     * POST /mock-112/incidents/{sourceIncidentId}/assignments
     */
    @PostMapping("/{sourceIncidentId}/assignments")
    public ResponseEntity<Map<String, Object>> addAssignment(
            @PathVariable String sourceIncidentId,
            @RequestBody MockAssignment assignment) {
        try {
            boolean added = store.addAssignment(sourceIncidentId, assignment);
            if (added) {
                webhookDispatcher.sendAssignmentChanged(sourceIncidentId, List.of(assignment));
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("sourceIncidentId", sourceIncidentId);
            body.put("externalAssignmentKey", assignment.getExternalAssignmentKey());
            body.put("added", added);
            return ResponseEntity.status(added ? HttpStatus.CREATED : HttpStatus.OK).body(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "not_found", "message", e.getMessage()));
        }
    }
}
