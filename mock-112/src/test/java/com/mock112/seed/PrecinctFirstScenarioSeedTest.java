package com.mock112.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mock112.domain.MockIncident;
import com.mock112.domain.MockSeedMarker;
import com.mock112.store.InMemoryIncidentStore;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * L1-B01 mock-112 대표 seed fixture lock test.
 *
 * <p>mock-112는 §6 canonical marker ID(`mk-*`)를 노출하지 않는 외부 원천 시뮬레이터다.
 * 본 테스트는 대표 사건 `mock-112-incident-001`의 seed marker 원천값과 raw JSON 내부 ID 부재를 고정한다.
 * §6 매핑과 N개 marker import 변환은 L1-T01 import 테스트의 책임이다.
 */
@DisplayName("L1-B01 mock 112 precinct-first seed contract")
class PrecinctFirstScenarioSeedTest {

    private static final String EXPECTED_SOURCE_INCIDENT_ID =
            "00000000-0000-0000-0000-000000000001";
    private static final String EXPECTED_MARKER_SOURCE = "MOCK_SEED";
    private static final String SEED_RESOURCE_PATH = "seed/precinct-first-scenario.json";

    @Test
    @DisplayName("대표 시나리오의 초기 기준 마커 원천값은 신고자 진술 위치 1개다 (§6 mk-precinct-clue-001 매핑 키)")
    void precinctFirstReferenceSeedMarkerSourceMatchesWitnessReportLocation() {
        SeedDataLoader loader = new SeedDataLoader();
        InMemoryIncidentStore store = new InMemoryIncidentStore();

        MockIncident incident = loader.loadPrecinctFirstScenario(store);

        assertThat(incident.getSourceIncidentId()).isEqualTo(EXPECTED_SOURCE_INCIDENT_ID);
        assertThat(incident.getSeedMarkers()).hasSize(1);

        MockSeedMarker first = incident.getSeedMarkers().get(0);
        assertThat(first.getType()).isEqualTo("CLUE");
        assertThat(first.getSource()).isEqualTo(EXPECTED_MARKER_SOURCE);
        assertThat(first.getMemo()).isEqualTo("신고자 진술 위치");
        assertThat(first.getLon()).isEqualTo(126.9134);
        assertThat(first.getLat()).isEqualTo(35.1631);
    }

    @Test
    @DisplayName("mock-112 raw seed JSON에는 Suri-Map 내부 marker ID(markerId/markerSeedId)가 없다")
    void rawJsonSeedMarkersDoNotCarryInternalMarkerIds() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root;
        try (InputStream is = new ClassPathResource(SEED_RESOURCE_PATH).getInputStream()) {
            root = mapper.readTree(is);
        }

        JsonNode seedMarkers = root.get("seedMarkers");
        assertThat(seedMarkers).as("seedMarkers array must exist in mock-112 seed JSON").isNotNull();
        assertThat(seedMarkers.isArray()).isTrue();
        assertThat(seedMarkers).isNotEmpty();

        for (JsonNode marker : seedMarkers) {
            assertThat(marker.has("markerSeedId"))
                    .as(
                        "mock-112 raw seed must not carry Suri-Map internal markerSeedId field; "
                            + "internal canonical IDs belong to L1 import/fixture layer (entry: %s)",
                        marker.toString())
                    .isFalse();
            assertThat(marker.has("markerId"))
                    .as(
                        "mock-112 raw seed must not carry Suri-Map internal markerId field; "
                            + "internal canonical IDs belong to L1 import/fixture layer (entry: %s)",
                        marker.toString())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("대표 시나리오 배정은 mock-112 HTTP 응답에서도 accountCode 필드로 노출된다")
    void precinctFirstAssignmentsExposeAccountCodeInHttpResponseShape() {
        SeedDataLoader loader = new SeedDataLoader();
        InMemoryIncidentStore store = new InMemoryIncidentStore();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        MockIncident incident = loader.loadPrecinctFirstScenario(store);

        JsonNode assignments = mapper.valueToTree(incident).get("assignments");
        assertThat(assignments).as("assignments array must exist in mock-112 response").isNotNull();
        assertThat(assignments.isArray()).isTrue();
        assertThat(assignments).hasSize(3);
        assertThat(assignments.get(0).path("accountCode").asText()).isEqualTo("acct-precinct-cmd");
        assertThat(assignments.get(1).path("accountCode").asText()).isEqualTo("acct-precinct-car");
        assertThat(assignments.get(2).path("accountCode").asText()).isEqualTo("acct-precinct-team");
        assertThat(assignments)
                .allSatisfy(assignment -> assertThat(assignment.hasNonNull("accountCode")).isTrue());
        assertThat(assignments)
                .allSatisfy(assignment -> assertThat(assignment.has("accountId")).isFalse());
    }
}
