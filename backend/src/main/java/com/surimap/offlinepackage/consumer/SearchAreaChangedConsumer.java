package com.surimap.offlinepackage.consumer;

import com.surimap.eventhub.dto.PublishRequest;

/** S7 consumer contract called by S4 EventFanout for SEARCH_AREA_CHANGED events. */
public interface SearchAreaChangedConsumer {

  void consume(PublishRequest event);
}
