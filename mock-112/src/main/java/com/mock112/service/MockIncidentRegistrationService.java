package com.mock112.service;

import com.mock112.assignment.AssignableOrganizationCatalog;
import com.mock112.controller.request.CreateMockIncidentRequest;
import com.mock112.controller.request.UpdateMockIncidentRequest;
import com.mock112.domain.MockAssignment;
import com.mock112.domain.MockIncident;
import com.mock112.domain.MockMissingPerson;
import com.mock112.store.MockIncidentStore;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MockIncidentRegistrationService {

    private static final DateTimeFormatter CASE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MockIncidentStore store;
    private final AssignableOrganizationCatalog organizationCatalog;

    public MockIncidentRegistrationService(
            MockIncidentStore store,
            AssignableOrganizationCatalog organizationCatalog) {
        this.store = store;
        this.organizationCatalog = organizationCatalog;
    }

    public MockIncident register(CreateMockIncidentRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime openedAt = request.getOpenedAt() != null ? request.getOpenedAt() : now;
        String sourceIncidentId = UUID.randomUUID().toString();

        MockIncident incident = new MockIncident();
        incident.setSourceIncidentId(sourceIncidentId);
        incident.setCaseNumber(caseNumberFor(sourceIncidentId, openedAt));
        incident.setTitle(requiredTitle(request.getTitle()));
        incident.setOpenedAt(openedAt);
        incident.setStatus("READY");
        incident.setCreatedAt(now);
        incident.setMissingPerson(normalizeMissingPerson(request.getMissingPerson()));
        incident.setAssignments(organizationCatalog.assignmentsFor(
                sourceIncidentId,
                request.getInitialOrganizationCode(),
                openedAt));
        incident.setSeedMarkers(request.getSeedMarkers());
        return store.save(incident);
    }

    public List<MockAssignment> assignOrganization(
            String sourceIncidentId,
            String organizationCode,
            OffsetDateTime assignedAt) {
        OffsetDateTime effectiveAssignedAt = assignedAt != null ? assignedAt : OffsetDateTime.now();
        List<MockAssignment> assignments =
                organizationCatalog.assignmentsFor(sourceIncidentId, organizationCode, effectiveAssignedAt);
        return assignments.stream()
                .filter(assignment -> store.addAssignment(sourceIncidentId, assignment))
                .toList();
    }

    public MockIncident correctReadyIncident(String sourceIncidentId, UpdateMockIncidentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        return store.updateReadySourceFacts(
                sourceIncidentId,
                requiredTitle(request.getTitle()),
                normalizeMissingPerson(request.getMissingPerson()));
    }

    private String requiredTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        return title.trim();
    }

    private MockMissingPerson normalizeMissingPerson(MockMissingPerson missingPerson) {
        if (missingPerson == null) {
            return null;
        }
        if (missingPerson.getDisplayName() == null || missingPerson.getDisplayName().isBlank()) {
            missingPerson.setDisplayName("미입력");
        } else {
            missingPerson.setDisplayName(missingPerson.getDisplayName().trim());
        }
        missingPerson.setPhotoObjectKey(optionalText(missingPerson.getPhotoObjectKey()));
        missingPerson.setAppearanceText(optionalText(missingPerson.getAppearanceText()));
        missingPerson.setLastSeenLocationText(optionalText(missingPerson.getLastSeenLocationText()));
        return missingPerson;
    }

    private String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String caseNumberFor(String sourceIncidentId, OffsetDateTime openedAt) {
        String suffix = sourceIncidentId.replace("-", "")
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);
        return "112-" + CASE_DATE.format(openedAt) + "-" + suffix;
    }
}
