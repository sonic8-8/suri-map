package com.mock112.store;

import com.mock112.domain.MockAssignment;
import com.mock112.domain.MockIncident;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * mock 112 사건 저장소 (in-memory).
 * 서버 재시작 시 초기화된다. Suri-Map DB에 직접 접근하지 않는다.
 */
public class InMemoryIncidentStore implements MockIncidentStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryIncidentStore.class);

    private final ConcurrentHashMap<String, MockIncident> store = new ConcurrentHashMap<>();

    /**
     * 사건을 저장한다. 같은 sourceIncidentId가 이미 있으면 예외를 던진다.
     */
    @Override
    public MockIncident save(MockIncident incident) {
        MockIncident existing = store.putIfAbsent(incident.getSourceIncidentId(), incident);
        if (existing != null) {
            throw new IllegalArgumentException(
                    "sourceIncidentId already exists: " + incident.getSourceIncidentId());
        }
        log.info("Incident saved: {}", incident.getSourceIncidentId());
        return incident;
    }

    @Override
    public Optional<MockIncident> findById(String sourceIncidentId) {
        return Optional.ofNullable(store.get(sourceIncidentId));
    }

    @Override
    public List<MockIncident> findByStatus(String status) {
        return store.values().stream()
                .filter(i -> i.getStatus().equalsIgnoreCase(status))
                .collect(Collectors.toList());
    }

    @Override
    public List<MockIncident> findAll() {
        return new ArrayList<>(store.values());
    }

    /**
     * 사건에 배정을 추가한다.
     * @return true if newly added, false if already existed
     */
    @Override
    public boolean addAssignment(String sourceIncidentId, MockAssignment assignment) {
        MockIncident incident = store.get(sourceIncidentId);
        if (incident == null) {
            throw new IllegalArgumentException("Incident not found: " + sourceIncidentId);
        }
        boolean added = incident.addAssignment(assignment);
        if (added) {
            log.info("Assignment added to {}: {}", sourceIncidentId, assignment.getAccountCode());
        }
        return added;
    }

    /**
     * 사건을 IMPORTED 상태로 변경한다.
     */
    @Override
    public void markImported(String sourceIncidentId) {
        MockIncident incident = store.get(sourceIncidentId);
        if (incident == null) {
            throw new IllegalArgumentException("Incident not found: " + sourceIncidentId);
        }
        incident.markImported();
        log.info("Incident marked as IMPORTED: {}", sourceIncidentId);
    }

    /**
     * 전체 상태를 초기화한다.
     */
    @Override
    public void reset() {
        store.clear();
        log.info("Store reset");
    }

    @Override
    public int size() {
        return store.size();
    }
}
