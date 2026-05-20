package com.surimap.marker.photo.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class S3CompatibleObjectStorageAdapterTest {

  @Test
  void presignedUploadUrlUsesPublicEndpointAndBucketPath() {
    S3CompatibleObjectStorageAdapter adapter =
        new S3CompatibleObjectStorageAdapter(
            "http://object-storage:9000",
            "https://k14c106.p.ssafy.io",
            "suri-map-photo",
            "minioadmin",
            "minioadmin",
            "us-east-1");

    var result =
        adapter.generatePresignedUrl(
            "markers/incident-a/photo-a.jpg",
            "image/jpeg",
            1024L,
            null,
            Duration.ofMinutes(15));

    assertThat(result.storageUri()).isEqualTo("s3://suri-map-photo");
    assertThat(result.uploadUrl())
        .startsWith("https://k14c106.p.ssafy.io/suri-map-photo/markers/incident-a/photo-a.jpg?");
  }

  @Test
  void presignedViewUrlUsesGetUrlWithPublicEndpointAndBucketPath() {
    S3CompatibleObjectStorageAdapter adapter =
        new S3CompatibleObjectStorageAdapter(
            "http://object-storage:9000",
            "https://k14c106.p.ssafy.io",
            "suri-map-photo",
            "minioadmin",
            "minioadmin",
            "us-east-1");

    String viewUrl =
        adapter
            .generatePresignedViewUrl("markers/incident-a/photo-a.jpg", Duration.ofMinutes(15))
            .orElseThrow();

    assertThat(viewUrl)
        .startsWith("https://k14c106.p.ssafy.io/suri-map-photo/markers/incident-a/photo-a.jpg?");
  }
}
