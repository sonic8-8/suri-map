package com.surimap.marker.photo.adapter;

import com.surimap.marker.photo.port.ObjectStoragePort;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * ObjectStoragePort의 in-memory mock 구현. 개발·테스트 환경에서 external S3/MinIO 호출 없이 동작한다.
 *
 * <p>ADR-0035: 개발·하네스 object storage adapter는 MinIO(S3-compatible)를 기본으로 하고, harness는 mock object
 * storage/upload URL fixture를 사용한다.
 */
public class MockObjectStorage implements ObjectStoragePort {

  public static final String MOCK_OBJECT_STORAGE_URI = "mock://object-storage/suri-map-harness";
  public static final String MOCK_UPLOAD_BASE_URL = "http://127.0.0.1:18080/mock-upload";
  public static final long MAX_SIZE_BYTES = 10_485_760L;

  private static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp");
  private static final Set<String> FAILURE_FIXTURES =
      Set.of(
          "upload-url-denied",
          "upload-timeout",
          "upload-500",
          "checksum-mismatch",
          "attach-missing-blob",
          "attach-duplicate");
  private static final Set<String> MISSING_OBJECT_FAILURES =
      Set.of("upload-timeout", "upload-500", "attach-missing-blob");
  private static final Pattern CHECKSUM_SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");

  private final ConcurrentHashMap<String, StoredObject> store = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> failureFixtures = new ConcurrentHashMap<>();

  private String nextUploadUrlFailure;

  @Override
  public PresignedUploadResult generatePresignedUrl(
      String objectKey, String contentType, long sizeBytes, String checksumSha256, Duration ttl) {
    if (nextUploadUrlFailure != null) {
      String fixtureKey = nextUploadUrlFailure;
      nextUploadUrlFailure = null;
      throw new FixtureFailureException(fixtureKey);
    }
    if (objectKey == null || objectKey.isBlank()) {
      throw new IllegalArgumentException("objectKey must not be blank");
    }
    if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
      throw new IllegalArgumentException("unsupported contentType: " + contentType);
    }
    if (sizeBytes <= 0 || sizeBytes > MAX_SIZE_BYTES) {
      throw new IllegalArgumentException("sizeBytes must be between 1 and " + MAX_SIZE_BYTES);
    }
    if (checksumSha256 != null && !CHECKSUM_SHA256_PATTERN.matcher(checksumSha256).matches()) {
      throw new IllegalArgumentException("checksumSha256 must be 64 lowercase hex characters");
    }
    if (ttl == null || ttl.isNegative() || ttl.isZero()) {
      throw new IllegalArgumentException("ttl must be positive");
    }

    Instant expiresAt = Instant.now().plus(ttl);
    String uploadUrl = MOCK_UPLOAD_BASE_URL + "/" + photoIdFrom(objectKey);

    // upload URL 발급 시에는 아직 실제 object가 저장되지 않음.
    // 클라이언트가 uploadUrl로 PUT 한 뒤 attach에서 headObject로 확인하는 흐름.
    // mock에서는 simulateUpload()로 업로드를 시뮬레이션한다.
    store.put(
        objectKey,
        StoredObject.pending(objectKey, contentType, sizeBytes, checksumSha256, expiresAt));

    return new PresignedUploadResult(
        uploadUrl,
        objectKey,
        MOCK_OBJECT_STORAGE_URI,
        expiresAt,
        sizeBytes,
        contentType,
        checksumSha256);
  }

  @Override
  public Optional<ObjectMetadata> headObject(String objectKey) {
    StoredObject obj = store.get(objectKey);
    if (obj == null) {
      return Optional.empty();
    }
    if (!obj.uploaded()) {
      return Optional.empty();
    }
    String failureFixture = failureFixtures.get(objectKey);
    if (failureFixture != null && MISSING_OBJECT_FAILURES.contains(failureFixture)) {
      return Optional.empty();
    }
    return Optional.of(
        new ObjectMetadata(
            obj.objectKey(),
            obj.uploadedContentType(),
            obj.uploadedSizeBytes(),
            obj.uploadedChecksumSha256()));
  }

  @Override
  public Optional<String> generatePresignedViewUrl(String objectKey, Duration ttl) {
    if (objectKey == null || objectKey.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(MOCK_UPLOAD_BASE_URL + "/" + objectKey);
  }

  @Override
  public void deleteObject(String objectKey) {
    store.remove(objectKey);
  }

  // --- Mock 전용 메서드 (테스트에서만 사용) ---

  /**
   * 클라이언트가 upload URL로 파일을 업로드한 것을 시뮬레이션한다.
   *
   * @param objectKey 업로드 대상 key
   * @param contentType 실제 업로드된 contentType
   * @param sizeBytes 실제 업로드된 파일 크기
   */
  public void simulateUpload(String objectKey, String contentType, long sizeBytes) {
    simulateUpload(objectKey, contentType, sizeBytes, expectedChecksumFor(objectKey));
  }

  /**
   * 클라이언트가 upload URL로 checksum metadata를 포함해 업로드한 것을 시뮬레이션한다.
   *
   * @param objectKey 업로드 대상 key
   * @param contentType 실제 업로드된 contentType
   * @param sizeBytes 실제 업로드된 파일 크기
   * @param checksumSha256 실제 업로드된 checksum
   */
  public void simulateUpload(
      String objectKey, String contentType, long sizeBytes, String checksumSha256) {
    StoredObject existing = store.get(objectKey);
    if (existing == null) {
      throw new IllegalStateException("No upload URL entry for key: " + objectKey);
    }
    if (checksumSha256 != null && !CHECKSUM_SHA256_PATTERN.matcher(checksumSha256).matches()) {
      throw new IllegalArgumentException("checksumSha256 must be 64 lowercase hex characters");
    }
    store.put(objectKey, existing.uploaded(contentType, sizeBytes, checksumSha256));
  }

  /**
   * upload URL의 TTL이 만료된 것을 시뮬레이션한다.
   *
   * @param objectKey 만료시킬 key
   */
  public void simulateExpiry(String objectKey) {
    StoredObject existing = store.get(objectKey);
    if (existing == null) {
      throw new IllegalStateException("No entry for key: " + objectKey);
    }
    Instant expired = Instant.now().minusSeconds(60);
    store.put(objectKey, existing.withExpiresAt(expired));
  }

  /** 특정 key의 upload URL 만료 시각을 반환한다. */
  public Optional<Instant> getExpiresAt(String objectKey) {
    StoredObject obj = store.get(objectKey);
    return obj == null ? Optional.empty() : Optional.of(obj.expiresAt());
  }

  /** 특정 key가 TTL 만료되었는지 확인한다. */
  public boolean isExpired(String objectKey) {
    StoredObject obj = store.get(objectKey);
    if (obj == null) {
      return false;
    }
    return Instant.now().isAfter(obj.expiresAt());
  }

  /** 다음 upload URL 발급 호출에 실패 fixture를 주입한다. */
  public void failNextUploadUrl(String fixtureKey) {
    validateFailureFixture(fixtureKey);
    if (!"upload-url-denied".equals(fixtureKey)) {
      throw new IllegalArgumentException("upload URL failure must use upload-url-denied");
    }
    nextUploadUrlFailure = fixtureKey;
  }

  /** upload 또는 attach 단계 실패 fixture를 object key에 기록한다. */
  public void simulateFailure(String objectKey, String fixtureKey) {
    validateFailureFixture(fixtureKey);
    failureFixtures.put(objectKey, fixtureKey);
  }

  /** object key에 주입된 실패 fixture를 조회한다. */
  public Optional<String> failureFixtureFor(String objectKey) {
    return Optional.ofNullable(failureFixtures.get(objectKey));
  }

  /** store를 초기화한다 (테스트 간 격리). */
  public void clear() {
    store.clear();
    failureFixtures.clear();
    nextUploadUrlFailure = null;
  }

  /** 현재 저장된 object 수를 반환한다. */
  public int size() {
    return store.size();
  }

  public String storageUri() {
    return MOCK_OBJECT_STORAGE_URI;
  }

  private String expectedChecksumFor(String objectKey) {
    StoredObject existing = store.get(objectKey);
    return existing == null ? null : existing.expectedChecksumSha256();
  }

  private void validateFailureFixture(String fixtureKey) {
    if (!FAILURE_FIXTURES.contains(fixtureKey)) {
      throw new IllegalArgumentException("unknown failure fixture: " + fixtureKey);
    }
  }

  private String photoIdFrom(String objectKey) {
    int slashIndex = objectKey.lastIndexOf('/');
    String fileName = slashIndex >= 0 ? objectKey.substring(slashIndex + 1) : objectKey;
    int dotIndex = fileName.lastIndexOf('.');
    if (dotIndex <= 0) {
      throw new IllegalArgumentException("objectKey must end with a photo filename extension");
    }
    return fileName.substring(0, dotIndex);
  }

  private record StoredObject(
      String objectKey,
      String expectedContentType,
      long expectedSizeBytes,
      String expectedChecksumSha256,
      String uploadedContentType,
      long uploadedSizeBytes,
      String uploadedChecksumSha256,
      Instant expiresAt,
      boolean uploaded) {
    private static StoredObject pending(
        String objectKey,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        Instant expiresAt) {
      return new StoredObject(
          objectKey, contentType, sizeBytes, checksumSha256, null, 0L, null, expiresAt, false);
    }

    private StoredObject uploaded(String contentType, long sizeBytes, String checksumSha256) {
      return new StoredObject(
          objectKey,
          expectedContentType,
          expectedSizeBytes,
          expectedChecksumSha256,
          contentType,
          sizeBytes,
          checksumSha256,
          expiresAt,
          true);
    }

    private StoredObject withExpiresAt(Instant expiresAt) {
      return new StoredObject(
          objectKey,
          expectedContentType,
          expectedSizeBytes,
          expectedChecksumSha256,
          uploadedContentType,
          uploadedSizeBytes,
          uploadedChecksumSha256,
          expiresAt,
          uploaded);
    }
  }

  public static class FixtureFailureException extends RuntimeException {

    private final String fixtureKey;

    public FixtureFailureException(String fixtureKey) {
      super(fixtureKey);
      this.fixtureKey = fixtureKey;
    }

    public String fixtureKey() {
      return fixtureKey;
    }
  }
}
