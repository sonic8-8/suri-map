package com.surimap.marker.photo.adapter;

import com.surimap.marker.photo.domain.PhotoMarkerContext;
import com.surimap.marker.photo.exception.PhotoApiException;
import com.surimap.marker.photo.port.PhotoWriteGuardPort;
import com.surimap.marker.photo.service.PhotoRequestContext;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * S1-1/S1-2/S8 marker context guard 구현 전까지 photo writes를 fail-closed로 막는 adapter.
 *
 * <p>S5는 이 port의 호출 계약을 고정하고, 실제 incident/PolicePhone/current OP 판정은 owner Lane 구현이 대체한다.
 */
@Component
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
