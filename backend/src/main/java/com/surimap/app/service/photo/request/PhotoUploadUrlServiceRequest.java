package com.surimap.app.service.photo.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.surimap.app.service.photo.PhotoRequestContext;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonPropertyOrder({"contentType", "sizeBytes", "checksumSha256"})
public class PhotoUploadUrlServiceRequest {

  private String contentType;
  private long sizeBytes;
  private String checksumSha256;

  // URL의 마커 ID와 인증·요청 키는 HTTP 본문이 아니므로 요청 해시에서 제외한다.
  @JsonIgnore private UUID markerId;
  @JsonIgnore private PhotoRequestContext context;

  @Builder(toBuilder = true)
  private PhotoUploadUrlServiceRequest(
      String contentType,
      long sizeBytes,
      String checksumSha256,
      UUID markerId,
      PhotoRequestContext context) {
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.checksumSha256 = checksumSha256;
    this.markerId = markerId;
    this.context = context;
  }
}
