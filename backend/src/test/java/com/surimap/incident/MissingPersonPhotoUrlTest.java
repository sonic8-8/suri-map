package com.surimap.incident;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.incident.controller.response.MissingPersonPhotoUrl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("실종자 사진 표시 URL")
class MissingPersonPhotoUrlTest {

  @Test
  @DisplayName("mock object storage는 /mock-upload/{objectKey} 경로를 반환한다")
  void mockProviderUsesMockUploadPath() {
    MissingPersonPhotoUrl photoUrl =
        new MissingPersonPhotoUrl("mock", "http://localhost:9000", "", "suri-map-photo");

    assertThat(photoUrl.fromObjectKey("mock-112/missing-person/001.jpg"))
        .isEqualTo("/mock-upload/mock-112/missing-person/001.jpg");
  }

  @Test
  @DisplayName("s3-compatible object storage는 public endpoint, bucket, object key를 조합한다")
  void s3CompatibleProviderUsesPublicEndpointBucketAndObjectKey() {
    MissingPersonPhotoUrl photoUrl =
        new MissingPersonPhotoUrl(
            "s3-compatible",
            "http://object-storage:9000",
            "https://k14c106.p.ssafy.io",
            "suri-map-photo");

    assertThat(photoUrl.fromObjectKey("mock-112/missing-person/001.jpg"))
        .isEqualTo("https://k14c106.p.ssafy.io/suri-map-photo/mock-112/missing-person/001.jpg");
  }

  @Test
  @DisplayName("object key path segment는 URL 인코딩한다")
  void objectKeySegmentsAreEncoded() {
    MissingPersonPhotoUrl photoUrl =
        new MissingPersonPhotoUrl("mock", "http://localhost:9000", "", "suri-map-photo");

    assertThat(photoUrl.fromObjectKey("mock-112/missing person/홍길동.jpg"))
        .isEqualTo("/mock-upload/mock-112/missing%20person/%ED%99%8D%EA%B8%B8%EB%8F%99.jpg");
  }
}
