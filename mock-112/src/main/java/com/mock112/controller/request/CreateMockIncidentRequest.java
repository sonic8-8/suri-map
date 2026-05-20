package com.mock112.controller.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mock112.domain.MockMissingPerson;
import com.mock112.domain.MockSeedMarker;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateMockIncidentRequest {

    private String title;
    private OffsetDateTime openedAt;
    private MockMissingPerson missingPerson;
    private String initialOrganizationCode;
    private List<MockSeedMarker> seedMarkers = new ArrayList<>();

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public OffsetDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(OffsetDateTime openedAt) { this.openedAt = openedAt; }

    public MockMissingPerson getMissingPerson() { return missingPerson; }
    public void setMissingPerson(MockMissingPerson missingPerson) { this.missingPerson = missingPerson; }

    public String getInitialOrganizationCode() { return initialOrganizationCode; }
    public void setInitialOrganizationCode(String initialOrganizationCode) {
        this.initialOrganizationCode = initialOrganizationCode;
    }

    public List<MockSeedMarker> getSeedMarkers() { return seedMarkers; }
    public void setSeedMarkers(List<MockSeedMarker> seedMarkers) {
        this.seedMarkers = seedMarkers != null ? seedMarkers : new ArrayList<>();
    }
}
