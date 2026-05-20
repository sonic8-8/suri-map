package com.mock112.store;

import com.mock112.domain.MockAssignment;
import com.mock112.domain.MockIncident;
import com.mock112.domain.MockMissingPerson;

import java.util.List;
import java.util.Optional;

public interface MockIncidentStore {

    MockIncident save(MockIncident incident);

    Optional<MockIncident> findById(String sourceIncidentId);

    List<MockIncident> findByStatus(String status);

    List<MockIncident> findAll();

    boolean addAssignment(String sourceIncidentId, MockAssignment assignment);

    MockIncident updateReadySourceFacts(
            String sourceIncidentId,
            String title,
            MockMissingPerson missingPerson);

    void markImported(String sourceIncidentId);

    MockIncident closeIncident(String sourceIncidentId);

    void reset();

    int size();
}
