package com.surimap.maparea.event;

import com.surimap.maparea.event.SearchAreaEventPublisher.StateTransitionPublishRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PublishRequestCollector implements SearchAreaEventPublisher {
    private final List<StateTransitionPublishRequest> requests = new ArrayList<>();

    @Override
    public void publish(StateTransitionPublishRequest request) {
        requests.add(request);
    }

    public List<StateTransitionPublishRequest> collected() {
        return Collections.unmodifiableList(requests);
    }
}
