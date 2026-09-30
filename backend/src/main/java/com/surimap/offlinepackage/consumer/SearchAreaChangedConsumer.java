package com.surimap.offlinepackage.consumer;

import com.surimap.global.event.EventPublishRequest;

/** S7 consumer contract called by S4 EventFanout for SEARCH_AREA_CHANGED events. */
public interface SearchAreaChangedConsumer {

  void consume(EventPublishRequest event);
}
