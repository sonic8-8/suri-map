package com.surimap.maparea.event;

public interface SearchAreaEventPublisher {
    void publish(StateTransitionPublishRequest request);

    record StateTransitionPublishRequest(
        String type,
        java.util.UUID id,
        java.util.UUID incidentId,
        java.util.UUID opId,
        String status,
        long version,
        String previousState,
        String nextState
    ) {}
}
