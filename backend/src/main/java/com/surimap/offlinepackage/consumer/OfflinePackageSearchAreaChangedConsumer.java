package com.surimap.offlinepackage.consumer;

import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.consumer.DomainEventConsumer;
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
  public void consume(PublishRequest event) {
    service.consumeSearchAreaChanged(event);
  }

  @Override
  public boolean supports(PublishRequest event) {
    return event != null && "SEARCH_AREA_CHANGED".equals(event.type());
  }
}
