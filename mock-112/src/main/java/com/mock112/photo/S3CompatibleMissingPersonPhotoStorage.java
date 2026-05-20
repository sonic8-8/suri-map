package com.mock112.photo;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

@Component
@ConditionalOnProperty(name = "mock112.object-storage.provider", havingValue = "s3-compatible")
public class S3CompatibleMissingPersonPhotoStorage implements MissingPersonPhotoStorage {

    private final MinioClient client;
    private final String publicEndpoint;
    private final String bucket;
    private final String storageUri;

    public S3CompatibleMissingPersonPhotoStorage(
            @Value("${mock112.object-storage.endpoint}") String endpoint,
            @Value("${mock112.object-storage.public-endpoint:}") String publicEndpoint,
            @Value("${mock112.object-storage.bucket}") String bucket,
            @Value("${mock112.object-storage.access-key}") String accessKey,
            @Value("${mock112.object-storage.secret-key}") String secretKey,
            @Value("${mock112.object-storage.region:}") String region) {
        this.bucket = requireText(bucket, "bucket");
        this.publicEndpoint = stripTrailingSlash(
                publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint);
        this.storageUri = "s3://" + this.bucket;
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(requireText(endpoint, "endpoint"))
                .credentials(requireText(accessKey, "accessKey"), requireText(secretKey, "secretKey"));
        if (region != null && !region.isBlank()) {
            builder.region(region.trim());
        }
        this.client = builder.build();
    }

    @PostConstruct
    public void ensureBucketExists() {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            client.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(bucket)
                    .config(publicReadPolicy())
                    .build());
        } catch (Exception exception) {
            throw new IllegalStateException("missing person photo object storage is not ready: " + bucket, exception);
        }
    }

    @Override
    public MissingPersonPhotoUploadResult put(
            String objectKey,
            String contentType,
            long sizeBytes,
            InputStream inputStream) throws IOException {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(requireText(objectKey, "objectKey"))
                    .stream(inputStream, sizeBytes, -1)
                    .contentType(requireText(contentType, "contentType"))
                    .build());
            return new MissingPersonPhotoUploadResult(
                    objectKey,
                    publicUrl(objectKey),
                    storageUri,
                    contentType,
                    sizeBytes);
        } catch (IOException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("failed to upload missing person photo to object storage", exception);
        }
    }

    private String publicUrl(String objectKey) {
        return publicEndpoint + "/" + encodePathSegment(bucket) + "/" + encodeObjectKey(objectKey);
    }

    private String publicReadPolicy() {
        return """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Principal": "*",
                      "Action": ["s3:GetObject"],
                      "Resource": ["arn:aws:s3:::%s/*"]
                    }
                  ]
                }
                """.formatted(bucket);
    }

    private static String encodeObjectKey(String objectKey) {
        String[] segments = objectKey.split("/");
        StringBuilder builder = new StringBuilder();
        for (String segment : segments) {
            if (!builder.isEmpty()) {
                builder.append('/');
            }
            builder.append(encodePathSegment(segment));
        }
        return builder.toString();
    }

    private static String encodePathSegment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String value) {
        String text = requireText(value, "publicEndpoint");
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
