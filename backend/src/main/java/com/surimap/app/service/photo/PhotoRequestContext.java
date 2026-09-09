package com.surimap.app.service.photo;

import com.surimap.marker.photo.security.SuriMapAuthentication;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhotoRequestContext {

  private SuriMapAuthentication authentication;
  private String idempotencyKey;
}
