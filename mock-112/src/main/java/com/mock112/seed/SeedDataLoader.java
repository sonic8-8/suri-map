package com.mock112.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mock112.domain.MockAssignment;
import com.mock112.domain.MockIncident;
import com.mock112.store.InMemoryIncidentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON seed 파일을 읽어 MockIncident로 변환한다.
 * 시나리오 API 호출 시 사용되며, 자동 적재는 하지 않는다.
 */
@Component
public class SeedDataLoader {

    private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);
    private final ObjectMapper mapper;

    public SeedDataLoader() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    /**
     * precinct-first 시나리오를 적재한다.
     * 초동 배정만 포함하고, 인계/지원 배정은 별도 API로 추가한다.
     */
    public MockIncident loadPrecinctFirstScenario(InMemoryIncidentStore store) {
        try {
            JsonNode root = loadSeedJson("seed/precinct-first-scenario.json");
            MockIncident incident = mapper.treeToValue(root, MockIncident.class);
            incident.setStatus("READY");
            store.save(incident);
            log.info("Precinct-first scenario loaded: {}", incident.getSourceIncidentId());
            return incident;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load precinct-first scenario seed", e);
        }
    }

    /**
     * 실종팀 인계 배정을 사건에 추가한다.
     */
    public List<MockAssignment> loadHandoverAssignments(InMemoryIncidentStore store, String sourceIncidentId) {
        try {
            JsonNode root = loadSeedJson("seed/precinct-first-scenario.json");
            JsonNode handoverNode = root.get("handoverAssignments");
            List<MockAssignment> assignments = new ArrayList<>();
            if (handoverNode != null && handoverNode.isArray()) {
                for (JsonNode node : handoverNode) {
                    MockAssignment a = mapper.treeToValue(node, MockAssignment.class);
                    store.addAssignment(sourceIncidentId, a);
                    assignments.add(a);
                }
            }
            log.info("Handover assignments loaded for {}: {} entries", sourceIncidentId, assignments.size());
            return assignments;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load handover assignments", e);
        }
    }

    /**
     * 지원 부대 배정을 사건에 추가한다.
     */
    public List<MockAssignment> loadSupportAssignments(InMemoryIncidentStore store, String sourceIncidentId) {
        try {
            JsonNode root = loadSeedJson("seed/precinct-first-scenario.json");
            JsonNode supportNode = root.get("supportAssignments");
            List<MockAssignment> assignments = new ArrayList<>();
            if (supportNode != null && supportNode.isArray()) {
                for (JsonNode node : supportNode) {
                    MockAssignment a = mapper.treeToValue(node, MockAssignment.class);
                    store.addAssignment(sourceIncidentId, a);
                    assignments.add(a);
                }
            }
            log.info("Support assignments loaded for {}: {} entries", sourceIncidentId, assignments.size());
            return assignments;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load support assignments", e);
        }
    }

    private JsonNode loadSeedJson(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream is = resource.getInputStream()) {
            return mapper.readTree(is);
        }
    }
}
