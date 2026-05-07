package com.surimap.marker.photo.adapter;

import com.surimap.marker.photo.exception.PhotoApiException;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import com.surimap.marker.photo.security.SuriMapAuthenticationResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * L2/S1-2 SecurityContext 구현 전까지 public photo write를 fail-closed로 막는 adapter.
 *
 * <p>테스트는 이 port를 double로 대체해 S5 계약만 검증한다.
 */
@Component
public class BlockingSuriMapAuthenticationResolver implements SuriMapAuthenticationResolver {

  @Override
  public SuriMapAuthentication resolve(String authorization, String channel) {
    throw new PhotoApiException("incident_access_denied", HttpStatus.FORBIDDEN);
  }
}
