package com.mock112.photo;

import static org.assertj.core.api.Assertions.assertThat;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "MOCK112_MINIO_IT", matches = "true")
class S3CompatibleMissingPersonPhotoStorageIT {

    @Test
    void putUploadsMissingPersonPhotoToMinioBucket() throws Exception {
        String endpoint = env("MOCK112_OBJECT_STORAGE_ENDPOINT", "http://localhost:9000");
        String publicEndpoint = env("MOCK112_OBJECT_STORAGE_PUBLIC_ENDPOINT", endpoint);
        String bucket = env("MOCK112_OBJECT_STORAGE_BUCKET", "suri-map-photo");
        String accessKey = env("MOCK112_OBJECT_STORAGE_ACCESS_KEY", "minioadmin");
        String secretKey = env("MOCK112_OBJECT_STORAGE_SECRET_KEY", "minioadmin");
        String region = env("MOCK112_OBJECT_STORAGE_REGION", "us-east-1");
        String objectKey = "mock-112/missing-person/it/" + UUID.randomUUID() + ".jpg";
        byte[] payload = "mock112-minio-photo".getBytes(StandardCharsets.UTF_8);

        S3CompatibleMissingPersonPhotoStorage storage = new S3CompatibleMissingPersonPhotoStorage(
                endpoint,
                publicEndpoint,
                bucket,
                accessKey,
                secretKey,
                region);
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .region(region)
                .build();

        storage.ensureBucketExists();
        try {
            MissingPersonPhotoUploadResult result = storage.put(
                    objectKey,
                    "image/jpeg",
                    payload.length,
                    new ByteArrayInputStream(payload));

            assertThat(result.objectKey()).isEqualTo(objectKey);
            assertThat(result.storageUri()).isEqualTo("s3://" + bucket);
            assertThat(result.photoUrl()).endsWith("/" + bucket + "/" + objectKey);
            assertThat(client.statObject(StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build())
                    .size())
                    .isEqualTo(payload.length);
            assertThat(client.getObject(GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build())
                    .readAllBytes())
                    .isEqualTo(payload);
            HttpResponse<byte[]> publicResponse = HttpClient.newHttpClient()
                    .send(
                            HttpRequest.newBuilder(URI.create(result.photoUrl())).GET().build(),
                            HttpResponse.BodyHandlers.ofByteArray());
            assertThat(publicResponse.statusCode()).isEqualTo(200);
            assertThat(publicResponse.body()).isEqualTo(payload);
        } finally {
            try {
                client.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .build());
            } catch (Exception ignored) {
                // Best-effort cleanup after the assertion path has already completed.
            }
        }
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
