package com.mock112.store;

import com.mock112.domain.MockAssignment;
import com.mock112.domain.MockIncident;
import com.mock112.domain.MockMissingPerson;
import com.mock112.domain.MockSeedMarker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class JdbcIncidentStore implements MockIncidentStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcIncidentStore.class);

    private final JdbcTemplate jdbcTemplate;

    public JdbcIncidentStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public MockIncident save(MockIncident incident) {
        try {
            OffsetDateTime createdAt = valueOrNow(incident.getCreatedAt());
            String status = valueOrDefault(incident.getStatus(), "READY");
            jdbcTemplate.update(
                    """
                    INSERT INTO mock_incident
                        (source_incident_id, case_number, title, opened_at, status, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    incident.getSourceIncidentId(),
                    incident.getCaseNumber(),
                    incident.getTitle(),
                    incident.getOpenedAt(),
                    status,
                    createdAt);
            incident.setStatus(status);
            incident.setCreatedAt(createdAt);
            insertMissingPerson(incident);
            insertAssignments(incident.getSourceIncidentId(), incident.getAssignments());
            insertSeedMarkers(incident.getSourceIncidentId(), incident.getSeedMarkers());
            log.info("Incident saved in DB: {}", incident.getSourceIncidentId());
            return incident;
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException(
                    "sourceIncidentId already exists: " + incident.getSourceIncidentId(), e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MockIncident> findById(String sourceIncidentId) {
        List<MockIncident> incidents = jdbcTemplate.query(
                """
                SELECT source_incident_id, case_number, title, opened_at, status, created_at
                FROM mock_incident
                WHERE source_incident_id = ?
                """,
                (rs, rowNum) -> mapIncident(rs),
                sourceIncidentId);
        if (incidents.isEmpty()) {
            return Optional.empty();
        }
        MockIncident incident = incidents.get(0);
        hydrate(incident);
        return Optional.of(incident);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MockIncident> findByStatus(String status) {
        return hydrateAll(jdbcTemplate.query(
                """
                SELECT source_incident_id, case_number, title, opened_at, status, created_at
                FROM mock_incident
                WHERE UPPER(status) = UPPER(?)
                ORDER BY created_at DESC, source_incident_id
                """,
                (rs, rowNum) -> mapIncident(rs),
                status));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MockIncident> findAll() {
        return hydrateAll(jdbcTemplate.query(
                """
                SELECT source_incident_id, case_number, title, opened_at, status, created_at
                FROM mock_incident
                ORDER BY created_at DESC, source_incident_id
                """,
                (rs, rowNum) -> mapIncident(rs)));
    }

    @Override
    @Transactional
    public boolean addAssignment(String sourceIncidentId, MockAssignment assignment) {
        assertIncidentExists(sourceIncidentId);
        normalizeAssignment(sourceIncidentId, assignment);
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO mock_assignment
                        (external_assignment_key, source_incident_id, account_code, incident_role, assigned_at)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    assignment.getExternalAssignmentKey(),
                    sourceIncidentId,
                    assignment.getAccountCode(),
                    assignment.getIncidentRole(),
                    assignment.getAssignedAt());
            log.info("Assignment saved in DB: {} -> {}", sourceIncidentId, assignment.getAccountCode());
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override
    @Transactional
    public MockIncident updateReadySourceFacts(
            String sourceIncidentId,
            String title,
            MockMissingPerson missingPerson) {
        String status = statusOf(sourceIncidentId);
        if (!"READY".equalsIgnoreCase(status)) {
            throw new IllegalStateException("Incident is " + status + " and source facts are read-only: "
                    + sourceIncidentId);
        }
        jdbcTemplate.update(
                """
                UPDATE mock_incident
                SET title = ?
                WHERE source_incident_id = ?
                """,
                title,
                sourceIncidentId);
        jdbcTemplate.update(
                "DELETE FROM mock_missing_person WHERE source_incident_id = ?",
                sourceIncidentId);
        if (missingPerson != null) {
            MockIncident incident = new MockIncident();
            incident.setSourceIncidentId(sourceIncidentId);
            incident.setMissingPerson(missingPerson);
            insertMissingPerson(incident);
        }
        log.info("READY incident source facts updated in DB: {}", sourceIncidentId);
        return findById(sourceIncidentId).orElseThrow();
    }

    @Override
    @Transactional
    public void markImported(String sourceIncidentId) {
        int updated = jdbcTemplate.update(
                """
                UPDATE mock_incident
                SET status = 'IMPORTED'
                WHERE source_incident_id = ?
                """,
                sourceIncidentId);
        if (updated == 0) {
            throw new IllegalArgumentException("Incident not found: " + sourceIncidentId);
        }
        log.info("Incident marked as IMPORTED in DB: {}", sourceIncidentId);
    }

    @Override
    @Transactional
    public void reset() {
        jdbcTemplate.update("DELETE FROM mock_incident");
        log.info("DB store reset");
    }

    @Override
    @Transactional(readOnly = true)
    public int size() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mock_incident", Integer.class);
        return count == null ? 0 : count;
    }

    private void insertMissingPerson(MockIncident incident) {
        MockMissingPerson missingPerson = incident.getMissingPerson();
        if (missingPerson == null) {
            return;
        }
        jdbcTemplate.update(
                """
                INSERT INTO mock_missing_person
                    (source_incident_id, display_name, photo_object_key, appearance_text,
                     last_seen_location_text, last_seen_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                incident.getSourceIncidentId(),
                missingPerson.getDisplayName(),
                missingPerson.getPhotoObjectKey(),
                missingPerson.getAppearanceText(),
                missingPerson.getLastSeenLocationText(),
                missingPerson.getLastSeenAt());
    }

    private void insertAssignments(String sourceIncidentId, List<MockAssignment> assignments) {
        for (MockAssignment assignment : assignments) {
            addAssignment(sourceIncidentId, assignment);
        }
    }

    private void insertSeedMarkers(String sourceIncidentId, List<MockSeedMarker> seedMarkers) {
        for (int i = 0; i < seedMarkers.size(); i++) {
            MockSeedMarker marker = seedMarkers.get(i);
            jdbcTemplate.update(
                    """
                    INSERT INTO mock_seed_marker
                        (source_incident_id, type, source, memo, lon, lat, sort_order)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    sourceIncidentId,
                    marker.getType(),
                    marker.getSource(),
                    marker.getMemo(),
                    marker.getLon(),
                    marker.getLat(),
                    i);
        }
    }

    private void assertIncidentExists(String sourceIncidentId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mock_incident WHERE source_incident_id = ?",
                Integer.class,
                sourceIncidentId);
        if (count == null || count == 0) {
            throw new IllegalArgumentException("Incident not found: " + sourceIncidentId);
        }
    }

    private String statusOf(String sourceIncidentId) {
        List<String> statuses = jdbcTemplate.query(
                """
                SELECT status
                FROM mock_incident
                WHERE source_incident_id = ?
                """,
                (rs, rowNum) -> rs.getString("status"),
                sourceIncidentId);
        if (statuses.isEmpty()) {
            throw new IllegalArgumentException("Incident not found: " + sourceIncidentId);
        }
        return statuses.get(0);
    }

    private List<MockIncident> hydrateAll(List<MockIncident> incidents) {
        for (MockIncident incident : incidents) {
            hydrate(incident);
        }
        return incidents;
    }

    private void hydrate(MockIncident incident) {
        incident.setMissingPerson(findMissingPerson(incident.getSourceIncidentId()).orElse(null));
        incident.setAssignments(findAssignments(incident.getSourceIncidentId()));
        incident.setSeedMarkers(findSeedMarkers(incident.getSourceIncidentId()));
    }

    private Optional<MockMissingPerson> findMissingPerson(String sourceIncidentId) {
        List<MockMissingPerson> people = jdbcTemplate.query(
                """
                SELECT display_name, photo_object_key, appearance_text,
                       last_seen_location_text, last_seen_at
                FROM mock_missing_person
                WHERE source_incident_id = ?
                """,
                (rs, rowNum) -> {
                    MockMissingPerson missingPerson = new MockMissingPerson();
                    missingPerson.setDisplayName(rs.getString("display_name"));
                    missingPerson.setPhotoObjectKey(rs.getString("photo_object_key"));
                    missingPerson.setAppearanceText(rs.getString("appearance_text"));
                    missingPerson.setLastSeenLocationText(rs.getString("last_seen_location_text"));
                    missingPerson.setLastSeenAt(offsetDateTime(rs, "last_seen_at"));
                    return missingPerson;
                },
                sourceIncidentId);
        return people.stream().findFirst();
    }

    private List<MockAssignment> findAssignments(String sourceIncidentId) {
        return jdbcTemplate.query(
                """
                SELECT external_assignment_key, account_code, incident_role, assigned_at
                FROM mock_assignment
                WHERE source_incident_id = ?
                ORDER BY assigned_at, external_assignment_key
                """,
                (rs, rowNum) -> {
                    MockAssignment assignment = new MockAssignment();
                    assignment.setExternalAssignmentKey(rs.getString("external_assignment_key"));
                    assignment.setAccountCode(rs.getString("account_code"));
                    assignment.setIncidentRole(rs.getString("incident_role"));
                    assignment.setAssignedAt(offsetDateTime(rs, "assigned_at"));
                    return assignment;
                },
                sourceIncidentId);
    }

    private List<MockSeedMarker> findSeedMarkers(String sourceIncidentId) {
        return jdbcTemplate.query(
                """
                SELECT type, source, memo, lon, lat
                FROM mock_seed_marker
                WHERE source_incident_id = ?
                ORDER BY sort_order, id
                """,
                (rs, rowNum) -> {
                    MockSeedMarker marker = new MockSeedMarker();
                    marker.setType(rs.getString("type"));
                    marker.setSource(rs.getString("source"));
                    marker.setMemo(rs.getString("memo"));
                    marker.setLon(rs.getDouble("lon"));
                    marker.setLat(rs.getDouble("lat"));
                    return marker;
                },
                sourceIncidentId);
    }

    private static MockIncident mapIncident(ResultSet rs) throws SQLException {
        MockIncident incident = new MockIncident();
        incident.setSourceIncidentId(rs.getString("source_incident_id"));
        incident.setCaseNumber(rs.getString("case_number"));
        incident.setTitle(rs.getString("title"));
        incident.setOpenedAt(offsetDateTime(rs, "opened_at"));
        incident.setStatus(rs.getString("status"));
        incident.setCreatedAt(offsetDateTime(rs, "created_at"));
        return incident;
    }

    private static OffsetDateTime offsetDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class);
    }

    private static OffsetDateTime valueOrNow(OffsetDateTime value) {
        return value == null ? OffsetDateTime.now() : value;
    }

    private static void normalizeAssignment(String sourceIncidentId, MockAssignment assignment) {
        if (assignment.getAssignedAt() == null) {
            assignment.setAssignedAt(OffsetDateTime.now());
        }
        if ((assignment.getExternalAssignmentKey() == null || assignment.getExternalAssignmentKey().isBlank())
                && assignment.getAccountCode() != null
                && !assignment.getAccountCode().isBlank()) {
            assignment.setExternalAssignmentKey(sourceIncidentId + ":" + assignment.getAccountCode().trim());
        }
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
