package com.surimap.maparea.statemachine;

import com.surimap.maparea.event.SearchAreaEventPublisher;
import com.surimap.maparea.event.SearchAreaEventPublisher.StateTransitionPublishRequest;
import com.surimap.maparea.geometry.geojson.GeoJsonPolygon;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SearchAreaStateTransitionService {

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
        "ACTIVE", Set.of("COMPLETED", "CANCELLED"),
        "COMPLETED", Set.of("ACTIVE", "CANCELLED")
    );

    private final SearchAreaEventPublisher publisher;
    private final Map<UUID, AtomicLong> historyCounters = new ConcurrentHashMap<>();

    public SearchAreaStateTransitionService(SearchAreaEventPublisher publisher) {
        this.publisher = publisher;
    }

    public StateTransitionResult transition(StateTransitionCommand command) {
        String current = command.currentStatus();
        String next = command.nextStatus();

        Set<String> allowed = ALLOWED_TRANSITIONS.get(current);
        if (allowed == null || !allowed.contains(next)) {
            throw new AreaStateConflictException(
                "Invalid state transition: " + current + " -> " + next);
        }

        long newVersion = command.currentVersion() + 1;

        SearchAreaHistory history = new SearchAreaHistory(
            UUID.randomUUID(),
            command.searchAreaId(),
            command.opId(),
            "STATUS_CHANGED",
            current,
            next,
            command.memo(),
            command.accountId(),
            Instant.now()
        );

        long count = historyCounters
            .computeIfAbsent(command.searchAreaId(), id -> new AtomicLong(1L))
            .incrementAndGet();

        publisher.publish(new StateTransitionPublishRequest(
            "SEARCH_AREA_CHANGED",
            command.searchAreaId(),
            command.incidentId(),
            command.opId(),
            next,
            newVersion,
            current,
            next
        ));

        return new StateTransitionResult(history, count);
    }

    public record StateTransitionCommand(
        UUID searchAreaId,
        UUID incidentId,
        UUID opId,
        UUID accountId,
        String currentStatus,
        String nextStatus,
        String memo,
        Instant clientTs,
        long currentVersion,
        GeoJsonPolygon geometry
    ) {}

    public record StateTransitionResult(
        SearchAreaHistory history,
        long historyCount
    ) {}
}
