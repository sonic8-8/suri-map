package com.mock112.store;

import com.mock112.domain.MockAssignment;
import com.mock112.domain.MockIncident;

import java.util.List;
import java.util.Optional;

public interface MockIncidentStore {

    MockIncident save(MockIncident incident);

    Optional<MockIncident> findById(String sourceIncidentId);

    List<MockIncident> findByStatus(String status);

    List<MockIncident> findAll();

    boolean addAssignment(String sourceIncidentId, MockAssignment assignment);

    void markImported(String sourceIncidentId);

    void reset();

    int size();
}
