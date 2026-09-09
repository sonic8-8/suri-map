package com.surimap.marker.photo.port;

import com.surimap.app.service.photo.PhotoRequestContext;
import com.surimap.domain.marker.Marker;
import java.util.UUID;

public interface PhotoWriteGuardPort {

  /**
   * S1-1/S1-2/S8 real guard 구현이 들어오기 전까지 이 port가 photo write의 fail-closed contract를 고정한다. 구현체는
   * incident/PolicePhone guard와 함께 marker opId가 S8 current OP와 다르면 {@code op_mismatch}, current OP가
   * 없으면 {@code op_required}로 차단해야 한다.
   */
  Marker requireUploadUrlAccess(UUID markerId, PhotoRequestContext context);

  Marker requireAttachAccess(UUID markerId, UUID photoId, PhotoRequestContext context);
}
