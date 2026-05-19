package com.surimap.opcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.api.controller.opcomparison.request.CreateOpComparisonRequest;
import com.surimap.api.controller.opcomparison.response.OpComparisonResponse;
import com.surimap.api.service.opcomparison.OpComparisonApiException;
import com.surimap.api.service.opcomparison.OpComparisonApiService;
import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.port.EventHub;
import com.surimap.handover.query.HandoverMemoQuery;
import com.surimap.incident.lifecycle.IncidentLifecycleGuard;
import com.surimap.incident.lifecycle.IncidentLifecycleSnapshot;
import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.query.MarkerQuery;
import com.surimap.marker.query.MarkerQueryFilters;
import com.surimap.marker.query.MarkerQueryResult;
import com.surimap.marker.query.MarkerView;
import com.surimap.operationalperiod.OperationalPeriod;
import com.surimap.operationalperiod.OperationalPeriodMapper;
import com.surimap.path.SearchPathRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class OpComparisonApiServiceTest {

  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final UUID OP1_ID = UUID.fromString("88888888-8888-8888-8888-888888880001");
  private static final UUID OP2_ID = UUID.fromString("88888888-8888-8888-8888-888888880002");
  private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111110001");
  private static final Instant NOW = Instant.parse("2026-05-19T00:00:00Z");

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final OpComparisonAnalysisMapper analysisMapper =
      org.mockito.Mockito.mock(OpComparisonAnalysisMapper.class);
  private final OperationalPeriodMapper operationalPeriodMapper =
      org.mockito.Mockito.mock(OperationalPeriodMapper.class);
  private final SearchPathRepository searchPathRepository =
      org.mockito.Mockito.mock(SearchPathRepository.class);
  private final MarkerQuery markerQuery = org.mockito.Mockito.mock(MarkerQuery.class);
  private final HandoverMemoQuery handoverMemoQuery = org.mockito.Mockito.mock(HandoverMemoQuery.class);
  private final OpComparisonRegionFactMapper regionFactMapper =
      org.mockito.Mockito.mock(OpComparisonRegionFactMapper.class);
  private final OpComparisonNarrativePort narrativePort =
      org.mockito.Mockito.mock(OpComparisonNarrativePort.class);
  private final EventHub eventHub = org.mockito.Mockito.mock(EventHub.class);
  private final IncidentLifecycleGuard incidentLifecycleGuard =
      org.mockito.Mockito.mock(IncidentLifecycleGuard.class);

  private OpComparisonApiService service;

  @BeforeEach
  void setUp() {
    when(incidentLifecycleGuard.requireOpen(INCIDENT_ID))
        .thenReturn(new IncidentLifecycleSnapshot(INCIDENT_ID, "OPEN", 1L));
    when(operationalPeriodMapper.findAllByIncidentOrderBySequence(INCIDENT_ID))
        .thenReturn(List.of(op(OP1_ID, 1), op(OP2_ID, 2)));
    when(searchPathRepository.findAll()).thenReturn(List.of());
    when(markerQuery.byIncident(eq(INCIDENT_ID), any(MarkerQueryFilters.class)))
        .thenReturn(new MarkerQueryResult(INCIDENT_ID, List.of()));
    when(handoverMemoQuery.byContext(eq(INCIDENT_ID), any(), eq(null), eq(null)))
        .thenReturn(List.of());
    when(regionFactMapper.findRegionFacts(any(), any(), any())).thenReturn(List.of());
    when(analysisMapper.findByRequestHash(anyString())).thenReturn(Optional.empty());
    when(analysisMapper.insert(any())).thenReturn(1);
    when(analysisMapper.markDeterministicReady(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(1);
    when(analysisMapper.updateNarrativeResult(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(1);

    OpComparisonSourceCollector sourceCollector =
        new OpComparisonSourceCollector(
            operationalPeriodMapper, searchPathRepository, markerQuery, handoverMemoQuery);
    service =
        new OpComparisonApiService(
            analysisMapper,
            sourceCollector,
            regionFactMapper,
            narrativePort,
            eventHub,
            incidentLifecycleGuard,
            objectMapper,
            Clock.fixed(NOW, ZoneOffset.UTC),
            emptyProvider());
  }

  @Test
  @DisplayName("comparison write stores deterministic result and publishes final event")
  void createsSkippedComparisonAndPublishesEvent() {
    OpComparisonResponse response = service.create(request(OP1_ID, OP2_ID), "idem-001", ACCOUNT_ID);

    assertThat(response.incidentId()).isEqualTo(INCIDENT_ID);
    assertThat(response.operationalPeriodIds()).containsExactly(OP1_ID, OP2_ID);
    assertThat(response.status()).isEqualTo("READY");
    assertThat(response.narrativeStatus()).isEqualTo("SKIPPED");
    assertThat(response.metrics()).hasSize(2);
    assertThat(response.diffFacts()).isEmpty();
    assertThat(response.regionFacts()).isEmpty();
    assertThat(response.version()).isEqualTo(2L);

    ArgumentCaptor<PublishRequest> event = ArgumentCaptor.forClass(PublishRequest.class);
    verify(eventHub).publish(event.capture());
    assertThat(event.getValue().type()).isEqualTo("OP_COMPARISON_ANALYSIS_CHANGED");
    assertThat(event.getValue().sourceEntityType()).isEqualTo("op_comparison_analysis");
    assertThat(event.getValue().sourceEntityId()).isEqualTo(response.comparisonId());
    assertThat(event.getValue().payload())
        .containsEntry("id", response.comparisonId().toString())
        .containsEntry("comparisonId", response.comparisonId().toString())
        .containsEntry("incidentId", INCIDENT_ID.toString())
        .containsEntry("status", "READY")
        .containsEntry("narrativeStatus", "SKIPPED")
        .containsEntry("version", 2L);
    @SuppressWarnings("unchecked")
    List<String> eventOpIds = (List<String>) event.getValue().payload().get("operationalPeriodIds");
    assertThat(eventOpIds)
        .containsExactly(OP1_ID.toString(), OP2_ID.toString());
  }

  @Test
  @DisplayName("same request hash returns existing comparison without duplicate event")
  void reusesExistingComparisonByRequestHash() throws Exception {
    OpComparisonAnalysisRecord existing =
        new OpComparisonAnalysisRecord(
            UUID.fromString("99000000-0000-0000-0000-000000000201"),
            INCIDENT_ID,
            objectMapper.writeValueAsString(List.of(OP1_ID.toString(), OP2_ID.toString())),
            "existing-request-hash",
            "b".repeat(64),
            OpComparisonAnalysisStatus.READY,
            "[]",
            "[]",
            "[]",
            OpComparisonNarrativeStatus.SKIPPED,
            null,
            null,
            ACCOUNT_ID,
            NOW,
            NOW,
            2L,
            NOW,
            NOW);
    when(analysisMapper.findByRequestHash(anyString())).thenReturn(Optional.of(existing));

    OpComparisonResponse response = service.create(request(OP2_ID, OP1_ID), "idem-existing", ACCOUNT_ID);

    assertThat(response.comparisonId()).isEqualTo(existing.id());
    verify(analysisMapper, never()).insert(any());
    verify(eventHub, never()).publish(any());
  }

  @Test
  @DisplayName("significant deterministic facts call narrative port and persist READY narrative")
  void createsReadyNarrativeWhenThresholdFactsExist() {
    when(markerQuery.byIncident(eq(INCIDENT_ID), eq(new MarkerQueryFilters(OP2_ID, null, null))))
        .thenReturn(new MarkerQueryResult(INCIDENT_ID, List.of(marker(1), marker(2), marker(3))));
    when(narrativePort.generate(any()))
        .thenReturn(OpComparisonNarrativeResult.ready("""
            {"observations":[{"observation":"OP2의 마커 수는 3입니다.","evidence":[{"source":"metric","factId":"","operationalPeriodId":"88888888-8888-8888-8888-888888880002","key":"markerCount","value":"3"}]}]}
            """.trim()));

    OpComparisonResponse response = service.create(request(OP1_ID, OP2_ID), "idem-narrative", ACCOUNT_ID);

    assertThat(response.status()).isEqualTo("READY");
    assertThat(response.narrativeStatus()).isEqualTo("READY");
    assertThat(response.diffFacts()).hasSize(1);
    assertThat(response.observations()).isNotNull();
    verify(narrativePort).generate(any());
    verify(analysisMapper).updateNarrativeResult(
        any(),
        eq(OpComparisonAnalysisStatus.READY),
        eq(OpComparisonNarrativeStatus.READY),
        anyString(),
        eq(null),
        any(),
        any());
  }

  @Test
  @DisplayName("same Idempotency-Key with different body returns idempotency_mismatch")
  void idempotencyMismatch() {
    service.create(request(OP1_ID, OP2_ID), "idem-mismatch", ACCOUNT_ID);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.create(request(OP1_ID, UUID.fromString("88888888-8888-8888-8888-888888880003")), "idem-mismatch", ACCOUNT_ID))
        .isInstanceOf(OpComparisonApiException.class)
        .hasMessage("idempotency_mismatch");
  }

  private static OperationalPeriod op(UUID id, int sequenceNumber) {
    return new OperationalPeriod(
        id,
        INCIDENT_ID,
        sequenceNumber,
        sequenceNumber == 1 ? "ENDED" : "ACTIVE",
        sequenceNumber == 1 ? "INITIAL" : "RE_SEARCH",
        null,
        ACCOUNT_ID,
        null,
        NOW.plusSeconds(sequenceNumber * 60L),
        sequenceNumber == 1 ? NOW.plusSeconds(120L) : null,
        1L,
        NOW,
        NOW);
  }

  private static MarkerView marker(int index) {
    return new MarkerView(
        UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccc57%02d".formatted(index)),
        INCIDENT_ID,
        OP2_ID,
        null,
        ACCOUNT_ID,
        null,
        MarkerType.NOTE,
        null,
        MarkerSource.APP,
        MarkerStatus.ACTIVE,
        index,
        null,
        "memo",
        NOW.plusSeconds(index),
        List.of());
  }

  private static CreateOpComparisonRequest request(UUID firstOpId, UUID secondOpId) {
    CreateOpComparisonRequest request = new CreateOpComparisonRequest();
    request.setIncidentId(INCIDENT_ID);
    request.setOperationalPeriodIds(List.of(firstOpId, secondOpId));
    return request;
  }

  private static ObjectProvider<com.surimap.sync.idempotency.IdempotentResponseCache> emptyProvider() {
    return new ObjectProvider<>() {
      @Override
      public com.surimap.sync.idempotency.IdempotentResponseCache getObject(Object... args) {
        return null;
      }

      @Override
      public com.surimap.sync.idempotency.IdempotentResponseCache getIfAvailable() {
        return null;
      }

      @Override
      public com.surimap.sync.idempotency.IdempotentResponseCache getIfUnique() {
        return null;
      }

      @Override
      public com.surimap.sync.idempotency.IdempotentResponseCache getObject() {
        return null;
      }
    };
  }
}
