package com.surimap.marker.photo.adapter;

import com.surimap.marker.photo.port.ObjectStoragePort.ObjectMetadata;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

@RestController
@ConditionalOnProperty(
    name = "surimap.object-storage.provider",
    havingValue = "mock",
    matchIfMissing = true)
public class MockObjectStorageUploadController {

  private static final String UPLOAD_PREFIX = "/mock-upload/";

  private final MockObjectStorageAdapter storage;

  public MockObjectStorageUploadController(MockObjectStorageAdapter storage) {
    this.storage = storage;
  }

  @PutMapping("/mock-upload/**")
  public ResponseEntity<Void> upload(
      HttpServletRequest request,
      @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
      @RequestBody(required = false) byte[] body) {
    String objectKey = objectKeyFrom(request);
    if (objectKey == null || objectKey.isBlank()) {
      return ResponseEntity.badRequest().build();
    }
    byte[] uploadBytes = body == null ? new byte[0] : body;
    storage.simulateUpload(
        objectKey,
        contentType == null || contentType.isBlank()
            ? MediaType.APPLICATION_OCTET_STREAM_VALUE
            : contentType,
        uploadBytes);
    return ResponseEntity.ok()
        .header("X-Mock-Object-Key", objectKey)
        .header("X-Mock-Upload-Size", String.valueOf(uploadBytes.length))
        .build();
  }

  @GetMapping("/mock-upload/**")
  public ResponseEntity<byte[]> view(HttpServletRequest request) {
    String objectKey = objectKeyFrom(request);
    if (objectKey == null || objectKey.isBlank()) {
      return ResponseEntity.badRequest().build();
    }
    return storage
        .readObject(objectKey)
        .map(
            body ->
                ResponseEntity.ok()
                    .contentType(contentTypeOf(objectKey))
                    .header("X-Mock-Object-Key", objectKey)
                    .body(body))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  private MediaType contentTypeOf(String objectKey) {
    return storage
        .headObject(objectKey)
        .map(MockObjectStorageUploadController::mediaTypeOf)
        .orElse(MediaType.APPLICATION_OCTET_STREAM);
  }

  private static MediaType mediaTypeOf(ObjectMetadata metadata) {
    try {
      return MediaType.parseMediaType(metadata.contentType());
    } catch (Exception ignored) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
  }

  private String objectKeyFrom(HttpServletRequest request) {
    String uri = request.getRequestURI();
    int prefixIndex = uri.indexOf(UPLOAD_PREFIX);
    if (prefixIndex < 0) {
      return null;
    }
    String encoded = uri.substring(prefixIndex + UPLOAD_PREFIX.length());
    return UriUtils.decode(encoded, StandardCharsets.UTF_8);
  }
}
