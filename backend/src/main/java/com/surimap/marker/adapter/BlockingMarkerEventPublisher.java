package com.surimap.marker.adapter;

import com.surimap.marker.dto.MarkerPublishRequest;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.port.MarkerEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * L2/S4 EventHub 구현 전까지 marker create event staging을 fail-closed로 막는 adapter.
 *
 * <p>S5 tests bind a capture double and verify MARKER_CREATED payload shape.
 */
@Component
public class BlockingMarkerEventPublisher implements MarkerEventPublisher {

  @Override
  public void publish(MarkerPublishRequest request) {
    throw new MarkerApiException("write_conflict", HttpStatus.CONFLICT);
  }
}
