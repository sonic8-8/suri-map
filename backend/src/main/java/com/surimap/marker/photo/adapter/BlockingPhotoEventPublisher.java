package com.surimap.marker.photo.adapter;

import com.surimap.marker.photo.dto.PublishRequest;
import com.surimap.marker.photo.exception.PhotoApiException;
import com.surimap.marker.photo.port.PhotoEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * L2/S4 EventHub 구현 전까지 event dispatch staging을 fail-closed로 막는 adapter.
 *
 * <p>S5 unit tests bind a capture double and verify MARKER_UPDATED payload shape.
 */
@Component
public class BlockingPhotoEventPublisher implements PhotoEventPublisher {

  @Override
  public void publish(PublishRequest request) {
    throw new PhotoApiException("write_conflict", HttpStatus.CONFLICT);
  }
}
