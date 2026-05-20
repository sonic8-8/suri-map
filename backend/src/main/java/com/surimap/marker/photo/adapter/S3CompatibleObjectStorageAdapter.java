package com.surimap.marker.photo.adapter;

import com.surimap.marker.photo.port.ObjectStoragePort;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "surimap.object-storage.provider", havingValue = "s3-compatible")
public class S3CompatibleObjectStorageAdapter implements ObjectStoragePort {

  private static final int MAX_PRESIGNED_TTL_SECONDS = 604_800;

  private final MinioClient objectClient;
  private final MinioClient presignClient;
  private final String bucket;
  private final String storageUri;

  public S3CompatibleObjectStorageAdapter(
      @Value("${surimap.object-storage.endpoint}") String endpoint,
      @Value("${surimap.object-storage.public-endpoint:}") String publicEndpoint,
      @Value("${surimap.object-storage.bucket}") String bucket,
      @Value("${surimap.object-storage.access-key}") String accessKey,
      @Value("${surimap.object-storage.secret-key}") String secretKey,
      @Value("${surimap.object-storage.region:}") String region) {
    this.bucket = requireText(bucket, "bucket");
    this.storageUri = "s3://" + this.bucket;
    this.objectClient = buildClient(endpoint, accessKey, secretKey, region);
    this.presignClient =
        buildClient(
            publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint,
            accessKey,
            secretKey,
            region);
  }

  @PostConstruct
  public void ensureBucketExists() {
    try {
      boolean exists = objectClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
      if (!exists) {
        objectClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
      }
    } catch (Exception exception) {
      throw new IllegalStateException("object storage bucket is not ready: " + bucket, exception);
    }
  }

  @Override
  public PresignedUploadResult generatePresignedUrl(
      String objectKey, String contentType, long sizeBytes, String checksumSha256, Duration ttl) {
    int ttlSeconds = ttlSeconds(ttl);
    try {
      String uploadUrl =
          presignClient.getPresignedObjectUrl(
              GetPresignedObjectUrlArgs.builder()
                  .method(Method.PUT)
                  .bucket(bucket)
                  .object(requireText(objectKey, "objectKey"))
                  .expiry(ttlSeconds, TimeUnit.SECONDS)
                  .build());
      return new PresignedUploadResult(
          uploadUrl,
          objectKey,
          storageUri,
          Instant.now().plus(ttl),
          sizeBytes,
          contentType,
          checksumSha256);
    } catch (Exception exception) {
      throw new IllegalStateException("failed to generate object storage upload URL", exception);
    }
  }

  @Override
  public Optional<String> generatePresignedViewUrl(String objectKey, Duration ttl) {
    int ttlSeconds = ttlSeconds(ttl);
    try {
      return Optional.of(
          presignClient.getPresignedObjectUrl(
              GetPresignedObjectUrlArgs.builder()
                  .method(Method.GET)
                  .bucket(bucket)
                  .object(requireText(objectKey, "objectKey"))
                  .expiry(ttlSeconds, TimeUnit.SECONDS)
                  .build()));
    } catch (Exception exception) {
      throw new IllegalStateException("failed to generate object storage view URL", exception);
    }
  }

  @Override
  public Optional<ObjectMetadata> headObject(String objectKey) {
    try {
      StatObjectResponse response =
          objectClient.statObject(
              StatObjectArgs.builder()
                  .bucket(bucket)
                  .object(requireText(objectKey, "objectKey"))
                  .build());
      return Optional.of(
          new ObjectMetadata(
              objectKey,
              response.contentType(),
              response.size(),
              checksumSha256(response.userMetadata())));
    } catch (ErrorResponseException exception) {
      if (isMissingObject(exception)) {
        return Optional.empty();
      }
      throw new IllegalStateException(
          "failed to stat object storage object: " + objectKey, exception);
    } catch (Exception exception) {
      throw new IllegalStateException(
          "failed to stat object storage object: " + objectKey, exception);
    }
  }

  @Override
  public void deleteObject(String objectKey) {
    try {
      objectClient.removeObject(
          RemoveObjectArgs.builder()
              .bucket(bucket)
              .object(requireText(objectKey, "objectKey"))
              .build());
    } catch (ErrorResponseException exception) {
      if (!isMissingObject(exception)) {
        throw new IllegalStateException(
            "failed to delete object storage object: " + objectKey, exception);
      }
    } catch (Exception exception) {
      throw new IllegalStateException(
          "failed to delete object storage object: " + objectKey, exception);
    }
  }

  private static MinioClient buildClient(
      String endpoint, String accessKey, String secretKey, String region) {
    MinioClient.Builder builder =
        MinioClient.builder()
            .endpoint(requireText(endpoint, "endpoint"))
            .credentials(requireText(accessKey, "accessKey"), requireText(secretKey, "secretKey"));
    if (region != null && !region.isBlank()) {
      builder.region(region.trim());
    }
    return builder.build();
  }

  private static int ttlSeconds(Duration ttl) {
    if (ttl == null || ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("ttl must be positive");
    }
    long seconds = ttl.getSeconds();
    if (seconds <= 0 || seconds > MAX_PRESIGNED_TTL_SECONDS) {
      throw new IllegalArgumentException("ttl must be between 1 and 604800 seconds");
    }
    return Math.toIntExact(seconds);
  }

  private static String checksumSha256(Map<String, String> userMetadata) {
    if (userMetadata == null || userMetadata.isEmpty()) {
      return null;
    }
    return firstText(
        userMetadata.get("checksum-sha256"),
        userMetadata.get("x-amz-meta-checksum-sha256"),
        userMetadata.get("X-Amz-Meta-Checksum-Sha256"));
  }

  private static String firstText(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private static boolean isMissingObject(ErrorResponseException exception) {
    String code = exception.errorResponse() == null ? "" : exception.errorResponse().code();
    return "NoSuchKey".equals(code) || "NoSuchObject".equals(code) || "NoSuchBucket".equals(code);
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value.trim();
  }
}
