package com.mock112.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.mock112.domain.MockAssignment;
import com.mock112.domain.MockIncident;
import com.mock112.domain.MockMissingPerson;
import com.mock112.domain.MockSeedMarker;
import com.mock112.seed.SeedDataLoader;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mock112-store-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "suri-map.webhook.enabled=false"
})
@DisplayName("mock-112 JDBC incident store")
class JdbcIncidentStoreTest {

    @Autowired
    private MockIncidentStore store;

    @AfterEach
    void tearDown() {
        store.reset();
    }

    @Test
    @DisplayName("사건, 실종자, 배정, seed marker를 DB에 저장하고 다시 읽는다")
    void persistsIncidentAggregate() {
        MockIncident incident = newIncident("00000000-0000-0000-0000-000000009901");

        store.save(incident);

        MockIncident found = store.findById(incident.getSourceIncidentId()).orElseThrow();
        assertThat(found.getTitle()).isEqualTo("광주 무등산 실종 신고");
        assertThat(found.getStatus()).isEqualTo("READY");
        assertThat(found.getMissingPerson().getDisplayName()).isEqualTo("홍길동");
        assertThat(found.getAssignments())
                .extracting(MockAssignment::getExternalAssignmentKey)
                .containsExactly("assign-9901-01");
        assertThat(found.getSeedMarkers())
                .extracting(MockSeedMarker::getMemo)
                .containsExactly("신고자 진술 위치");
        assertThat(store.findByStatus("ready")).hasSize(1);
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("배정 추가는 externalAssignmentKey 기준으로 멱등 처리된다")
    void addAssignmentIsIdempotentByExternalAssignmentKey() {
        MockIncident incident = newIncident("00000000-0000-0000-0000-000000009902");
        store.save(incident);
        MockAssignment assignment = new MockAssignment(
                "assign-9902-02",
                "acct-missing-team",
                "MISSING_TEAM",
                OffsetDateTime.parse("2026-05-16T09:10:00+09:00"));

        assertThat(store.addAssignment(incident.getSourceIncidentId(), assignment)).isTrue();
        assertThat(store.addAssignment(incident.getSourceIncidentId(), assignment)).isFalse();

        MockIncident found = store.findById(incident.getSourceIncidentId()).orElseThrow();
        assertThat(found.getAssignments())
                .extracting(MockAssignment::getExternalAssignmentKey)
                .containsExactly("assign-9902-01", "assign-9902-02");
    }

    @Test
    @DisplayName("인계·지원 seed 배정은 대상 sourceIncidentId별 externalAssignmentKey로 저장된다")
    void seedFollowUpAssignmentsAreScopedToTargetSourceIncidentId() {
        SeedDataLoader loader = new SeedDataLoader();
        MockIncident canonical = loader.loadPrecinctFirstScenario(store);
        loader.loadHandoverAssignments(store, canonical.getSourceIncidentId());
        loader.loadSupportAssignments(store, canonical.getSourceIncidentId());

        String sourceIncidentId = "00000000-0000-0000-0000-000000009904";
        MockIncident dynamicIncident = newIncident(sourceIncidentId);
        dynamicIncident.setAssignments(List.of());
        dynamicIncident.setSeedMarkers(List.of());
        store.save(dynamicIncident);

        List<MockAssignment> handover = loader.loadHandoverAssignments(store, sourceIncidentId);
        List<MockAssignment> support = loader.loadSupportAssignments(store, sourceIncidentId);

        assertThat(handover).hasSize(2);
        assertThat(support).hasSize(3);
        assertThat(store.findById(sourceIncidentId).orElseThrow().getAssignments())
                .extracting(MockAssignment::getExternalAssignmentKey)
                .containsExactlyInAnyOrder(
                        sourceIncidentId + ":cmd-alpha",
                        sourceIncidentId + ":team-alpha",
                        sourceIncidentId + ":support-cmd",
                        sourceIncidentId + ":support-car",
                        sourceIncidentId + ":support-team");
    }

    @Test
    @DisplayName("import 완료 표시와 reset은 DB 상태에 반영된다")
    void markImportedAndResetMutateDbState() {
        MockIncident incident = newIncident("00000000-0000-0000-0000-000000009903");
        store.save(incident);

        store.markImported(incident.getSourceIncidentId());

        assertThat(store.findByStatus("READY")).isEmpty();
        assertThat(store.findByStatus("IMPORTED")).hasSize(1);

        store.reset();

        assertThat(store.size()).isZero();
    }

    private static MockIncident newIncident(String sourceIncidentId) {
        OffsetDateTime openedAt = OffsetDateTime.of(2026, 5, 16, 9, 0, 0, 0, ZoneOffset.ofHours(9));
        MockIncident incident = new MockIncident();
        incident.setSourceIncidentId(sourceIncidentId);
        incident.setTitle("광주 무등산 실종 신고");
        incident.setOpenedAt(openedAt);
        incident.setMissingPerson(new MockMissingPerson(
                "홍길동",
                "mock-112/missing-person/test.jpg",
                "검은색 등산복",
                "무등산 장불재 인근",
                openedAt.minusMinutes(30)));
        incident.setAssignments(java.util.List.of(new MockAssignment(
                "assign-" + sourceIncidentId.substring(sourceIncidentId.length() - 4) + "-01",
                "acct-precinct-cmd",
                "COMMAND",
                openedAt.plusMinutes(5))));
        incident.setSeedMarkers(java.util.List.of(new MockSeedMarker(
                "CLUE",
                "MOCK_SEED",
                "신고자 진술 위치",
                126.9134,
                35.1631)));
        return incident;
    }
}
