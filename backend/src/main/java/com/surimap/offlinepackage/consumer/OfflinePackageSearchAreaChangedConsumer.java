package com.surimap.offlinepackage.consumer;

import com.surimap.global.event.DomainEventConsumer;
import com.surimap.global.event.EventPublishRequest;
import com.surimap.offlinepackage.service.OfflinePackageService;
import org.springframework.stereotype.Component;

@Component
public class OfflinePackageSearchAreaChangedConsumer
    implements SearchAreaChangedConsumer, DomainEventConsumer {

  private final OfflinePackageService service;

  public OfflinePackageSearchAreaChangedConsumer(OfflinePackageService service) {
    this.service = service;
  }

  @Override
  public void consume(EventPublishRequest event) {
    service.consumeSearchAreaChanged(event);
  }

  @Override
  public boolean supports(EventPublishRequest event) {
    return event != null && "SEARCH_AREA_CHANGED".equals(event.getType());
  }
}
