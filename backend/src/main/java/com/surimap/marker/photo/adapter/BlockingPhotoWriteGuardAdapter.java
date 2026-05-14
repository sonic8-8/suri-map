package com.surimap.marker.photo.adapter;

import com.surimap.marker.photo.domain.PhotoMarkerContext;
import com.surimap.marker.photo.exception.PhotoApiException;
import com.surimap.marker.photo.port.PhotoWriteGuardPort;
import com.surimap.marker.photo.service.PhotoRequestContext;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Test/double adapter that keeps photo writes fail-closed when explicitly wired outside Spring.
 */
public class BlockingPhotoWriteGuardAdapter implements PhotoWriteGuardPort {

  @Override
  public PhotoMarkerContext requireUploadUrlAccess(UUID markerId, PhotoRequestContext context) {
    throw new PhotoApiException("incident_access_denied", HttpStatus.FORBIDDEN);
  }

  @Override
  public PhotoMarkerContext requireAttachAccess(
      UUID markerId, UUID photoId, PhotoRequestContext context) {
    throw new PhotoApiException("incident_access_denied", HttpStatus.FORBIDDEN);
  }
}
