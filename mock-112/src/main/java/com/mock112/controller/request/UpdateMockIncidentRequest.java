package com.mock112.controller.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mock112.domain.MockMissingPerson;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateMockIncidentRequest {

    private String title;
    private MockMissingPerson missingPerson;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public MockMissingPerson getMissingPerson() { return missingPerson; }
    public void setMissingPerson(MockMissingPerson missingPerson) { this.missingPerson = missingPerson; }
}
