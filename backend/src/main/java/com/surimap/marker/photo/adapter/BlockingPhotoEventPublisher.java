package com.surimap.marker.photo.adapter;

import com.surimap.marker.photo.dto.PublishRequest;
import com.surimap.marker.photo.exception.PhotoApiException;
import com.surimap.marker.photo.port.PhotoEventPublisher;
import org.springframework.http.HttpStatus;

/**
 * Test/double adapter that keeps photo event publishing fail-closed when explicitly wired outside
 * Spring.
 */
public class BlockingPhotoEventPublisher implements PhotoEventPublisher {

  @Override
  public void publish(PublishRequest request) {
    throw new PhotoApiException("write_conflict", HttpStatus.CONFLICT);
  }
}
