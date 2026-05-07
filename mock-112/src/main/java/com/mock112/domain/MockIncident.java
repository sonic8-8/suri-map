package com.mock112.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * mock 112 배정 사건 전체 모델.
 *
 * status 흐름: READY → IMPORTED
 * - READY: 등록 직후, Suri-Map polling 대상
 * - IMPORTED: Suri-Map이 POST /api/incidents/import 완료 후 mark
 */
public class MockIncident {

    private String sourceIncidentId;
    private String title;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime openedAt;

    private String status; // READY | IMPORTED

    private MockMissingPerson missingPerson;
    private List<MockAssignment> assignments = new ArrayList<>();
    private List<MockSeedMarker> seedMarkers = new ArrayList<>();

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime createdAt;

    public MockIncident() {
        this.status = "READY";
        this.createdAt = OffsetDateTime.now();
    }

    // --- getters & setters ---

    public String getSourceIncidentId() { return sourceIncidentId; }
    public void setSourceIncidentId(String sourceIncidentId) { this.sourceIncidentId = sourceIncidentId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public OffsetDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(OffsetDateTime openedAt) { this.openedAt = openedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public MockMissingPerson getMissingPerson() { return missingPerson; }
    public void setMissingPerson(MockMissingPerson missingPerson) { this.missingPerson = missingPerson; }

    public List<MockAssignment> getAssignments() { return assignments; }
    public void setAssignments(List<MockAssignment> assignments) { this.assignments = assignments != null ? assignments : new ArrayList<>(); }

    public List<MockSeedMarker> getSeedMarkers() { return seedMarkers; }
    public void setSeedMarkers(List<MockSeedMarker> seedMarkers) { this.seedMarkers = seedMarkers != null ? seedMarkers : new ArrayList<>(); }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    /**
     * 배정을 추가한다. 같은 externalAssignmentKey가 이미 있으면 건너뛴다.
     */
    public boolean addAssignment(MockAssignment assignment) {
        boolean exists = assignments.stream()
                .anyMatch(a -> a.getExternalAssignmentKey().equals(assignment.getExternalAssignmentKey()));
        if (exists) return false;
        assignments.add(assignment);
        return true;
    }

    public void markImported() {
        this.status = "IMPORTED";
    }
}
