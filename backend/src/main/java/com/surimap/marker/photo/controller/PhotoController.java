package com.surimap.marker.photo.controller;

import com.surimap.marker.photo.dto.PhotoAttachRequest;
import com.surimap.marker.photo.dto.PhotoAttachResponse;
import com.surimap.marker.photo.dto.PhotoUploadUrlRequest;
import com.surimap.marker.photo.dto.PhotoUploadUrlResponse;
import com.surimap.marker.photo.service.PhotoRequestContext;
import com.surimap.marker.photo.service.PhotoService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/markers/{markerId}/photos")
public class PhotoController {

  private final PhotoService photoService;
  private final PhotoRequestContextResolver contextResolver;

  public PhotoController(PhotoService photoService, PhotoRequestContextResolver contextResolver) {
    this.photoService = photoService;
    this.contextResolver = contextResolver;
  }

  @PostMapping("/upload-url")
  public ResponseEntity<PhotoUploadUrlResponse> createUploadUrl(
      @PathVariable UUID markerId,
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestHeader(value = "X-Client-Channel", required = false) String channel,
      @RequestHeader(value = "X-PolicePhone-Id", required = false) String policePhoneId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody PhotoUploadUrlRequest request) {
    PhotoRequestContext context =
        contextResolver.resolve(authorization, channel, policePhoneId, idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(photoService.createUploadUrl(markerId, request, context));
  }

  @PostMapping("/{photoId}/attach")
  public PhotoAttachResponse attach(
      @PathVariable UUID markerId,
      @PathVariable UUID photoId,
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestHeader(value = "X-Client-Channel", required = false) String channel,
      @RequestHeader(value = "X-PolicePhone-Id", required = false) String policePhoneId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody PhotoAttachRequest request) {
    PhotoRequestContext context =
        contextResolver.resolve(authorization, channel, policePhoneId, idempotencyKey);
    return photoService.attach(markerId, photoId, request, context).response();
  }
}
