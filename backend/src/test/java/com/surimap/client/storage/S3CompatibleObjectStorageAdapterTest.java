package com.surimap.client.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class S3CompatibleObjectStorageAdapterTest {

  @ParameterizedTest
  @ValueSource(strings = {"markers/incident-a/photo-a.jpg", "mock-112/missing-person/001.jpg"})
  @DisplayName("마커·실종자 사진의 업로드 주소를 발급하면, 외부 주소와 버킷·파일 키를 반영한다")
  void generatePresignedUrl_photoObjectKey_usesPublicEndpointAndBucket(String objectKey) {
    // given: 내부 저장소 주소와 브라우저에서 접근할 외부 주소가 다르다.
    S3CompatibleObjectStorageAdapter adapter =
        new S3CompatibleObjectStorageAdapter(
            "http://object-storage:9000",
            "https://k14c106.p.ssafy.io",
            "suri-map-photo",
            "minioadmin",
            "minioadmin",
            "us-east-1");

    // when: 마커 또는 실종자 사진의 서명된 업로드 주소를 발급한다.
    var result =
        adapter.generatePresignedUrl(objectKey, "image/jpeg", 1024L, null, Duration.ofMinutes(15));

    // then: 외부 주소 아래의 같은 버킷·파일 키로 업로드할 수 있다.
    assertThat(result.storageUri()).isEqualTo("s3://suri-map-photo");
    assertThat(result.uploadUrl())
        .startsWith("https://k14c106.p.ssafy.io/suri-map-photo/" + objectKey + "?");
  }

  @ParameterizedTest
  @ValueSource(strings = {"markers/incident-a/photo-a.jpg", "mock-112/missing-person/001.jpg"})
  @DisplayName("마커·실종자 사진의 조회 주소를 발급하면, 외부 주소와 버킷·파일 키를 반영한다")
  void generatePresignedViewUrl_photoObjectKey_usesPublicEndpointAndBucket(String objectKey) {
    // given: 사진 조회에 사용할 외부 주소와 버킷이 설정되어 있다.
    S3CompatibleObjectStorageAdapter adapter =
        new S3CompatibleObjectStorageAdapter(
            "http://object-storage:9000",
            "https://k14c106.p.ssafy.io",
            "suri-map-photo",
            "minioadmin",
            "minioadmin",
            "us-east-1");

    // when: 마커 또는 실종자 사진의 서명된 조회 주소를 발급한다.
    String viewUrl =
        adapter.generatePresignedViewUrl(objectKey, Duration.ofMinutes(15)).orElseThrow();

    // then: 내부 저장소 주소가 아니라 외부에서 접근할 주소를 반환한다.
    assertThat(viewUrl).startsWith("https://k14c106.p.ssafy.io/suri-map-photo/" + objectKey + "?");
  }
}
