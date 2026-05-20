package com.mock112.controller;

import com.mock112.domain.MockAssignment;
import com.mock112.domain.MockIncident;
import com.mock112.controller.request.CreateMockIncidentRequest;
import com.mock112.controller.request.OrganizationAssignmentRequest;
import com.mock112.controller.request.UpdateMockIncidentRequest;
import com.mock112.service.MockIncidentRegistrationService;
import com.mock112.store.MockIncidentStore;
import com.mock112.webhook.WebhookDeliveryResult;
import com.mock112.webhook.SuriMapWebhookDispatcher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
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

    private final MockIncidentStore store;
    private final MockIncidentRegistrationService registrationService;
    private final SuriMapWebhookDispatcher webhookDispatcher;

    public MockIncidentController(
            MockIncidentStore store,
            MockIncidentRegistrationService registrationService,
            SuriMapWebhookDispatcher webhookDispatcher) {
        this.store = store;
        this.registrationService = registrationService;
        this.webhookDispatcher = webhookDispatcher;
    }

    /**
     * 사건 등록.
     * POST /mock-112/incidents
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createIncident(@RequestBody CreateMockIncidentRequest request) {
        try {
            MockIncident incident = registrationService.register(request);
            WebhookDeliveryResult delivery = webhookDispatcher.sendIncidentReady(incident);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("sourceIncidentId", incident.getSourceIncidentId());
            body.put("caseNumber", incident.getCaseNumber());
            body.put("status", incident.getStatus());
            body.put("assignmentCount", incident.getAssignments().size());
            body.put("webhookDelivery", delivery);
            body.put("message", "사건이 등록되었습니다.");
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (IllegalArgumentException e) {
            HttpStatus status = e.getMessage() != null && e.getMessage().contains("already exists")
                    ? HttpStatus.CONFLICT
                    : HttpStatus.BAD_REQUEST;
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", status == HttpStatus.CONFLICT ? "duplicate_incident" : "invalid_request");
            body.put("message", e.getMessage());
            return ResponseEntity.status(status).body(body);
        }
    }

    /**
     * READY 사건 원천 정보 정정.
     * PUT /mock-112/incidents/{sourceIncidentId}
     */
    @PutMapping("/{sourceIncidentId}")
    public ResponseEntity<Map<String, Object>> updateReadyIncident(
            @PathVariable String sourceIncidentId,
            @RequestBody UpdateMockIncidentRequest request) {
        try {
            MockIncident incident = registrationService.correctReadyIncident(sourceIncidentId, request);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("sourceIncidentId", incident.getSourceIncidentId());
            body.put("caseNumber", incident.getCaseNumber());
            body.put("status", incident.getStatus());
            body.put("incident", incident);
            body.put("webhookDelivery", WebhookDeliveryResult.none());
            body.put("message", "READY 사건 원천 정보가 정정되었습니다.");
            return ResponseEntity.ok(body);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "incident_imported_read_only",
                            "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            HttpStatus status = e.getMessage() != null && e.getMessage().startsWith("Incident not found")
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status)
                    .body(Map.of(
                            "error", status == HttpStatus.NOT_FOUND ? "not_found" : "invalid_request",
                            "message", e.getMessage()));
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
            normalizeAssignment(sourceIncidentId, assignment);
            boolean added = store.addAssignment(sourceIncidentId, assignment);
            WebhookDeliveryResult delivery = WebhookDeliveryResult.none();
            if (added) {
                delivery = webhookDispatcher.sendAssignmentChanged(sourceIncidentId, List.of(assignment));
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("sourceIncidentId", sourceIncidentId);
            body.put("externalAssignmentKey", assignment.getExternalAssignmentKey());
            body.put("added", added);
            body.put("webhookDelivery", delivery);
            return ResponseEntity.status(added ? HttpStatus.CREATED : HttpStatus.OK).body(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "not_found", "message", e.getMessage()));
        }
    }

    /**
     * 사건에 조직 단위 배정을 추가한다.
     * POST /mock-112/incidents/{sourceIncidentId}/assignment-organizations
     */
    @PostMapping("/{sourceIncidentId}/assignment-organizations")
    public ResponseEntity<Map<String, Object>> addAssignmentOrganization(
            @PathVariable String sourceIncidentId,
            @RequestBody OrganizationAssignmentRequest request) {
        try {
            List<MockAssignment> added = registrationService.assignOrganization(
                    sourceIncidentId,
                    request.getOrganizationCode(),
                    request.getAssignedAt());
            if (!added.isEmpty()) {
                WebhookDeliveryResult delivery = webhookDispatcher.sendAssignmentChanged(sourceIncidentId, added);
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("sourceIncidentId", sourceIncidentId);
                body.put("organizationCode", request.getOrganizationCode());
                body.put("addedCount", added.size());
                body.put("assignments", added);
                body.put("webhookDelivery", delivery);
                return ResponseEntity.status(HttpStatus.CREATED).body(body);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("sourceIncidentId", sourceIncidentId);
            body.put("organizationCode", request.getOrganizationCode());
            body.put("addedCount", added.size());
            body.put("assignments", added);
            body.put("webhookDelivery", WebhookDeliveryResult.none());
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            HttpStatus status = e.getMessage() != null && e.getMessage().startsWith("Incident not found")
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status)
                    .body(Map.of(
                            "error", status == HttpStatus.NOT_FOUND ? "not_found" : "invalid_request",
                            "message", e.getMessage()));
        }
    }

    private void normalizeAssignment(String sourceIncidentId, MockAssignment assignment) {
        if (assignment.getAssignedAt() == null) {
            assignment.setAssignedAt(OffsetDateTime.now());
        }
        if ((assignment.getExternalAssignmentKey() == null || assignment.getExternalAssignmentKey().isBlank())
                && assignment.getAccountCode() != null
                && !assignment.getAccountCode().isBlank()) {
            assignment.setExternalAssignmentKey(sourceIncidentId + ":" + assignment.getAccountCode().trim());
        }
    }
}
