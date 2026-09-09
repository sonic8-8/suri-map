package com.surimap.app.controller.photo;

import com.surimap.app.controller.photo.request.PhotoUploadUrlRequest;
import com.surimap.app.controller.photo.response.PhotoUploadUrlResponse;
import com.surimap.app.service.photo.PhotoRequestContext;
import com.surimap.app.service.photo.PhotoService;
import com.surimap.app.service.photo.response.PhotoUploadUrlServiceResponse;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequirePolicePhone;
import com.surimap.common.auth.RequirePolicePhoneRegistered;
import com.surimap.marker.photo.controller.PhotoRequestContextResolver;
import com.surimap.marker.photo.dto.PhotoAttachRequest;
import com.surimap.marker.photo.dto.PhotoAttachResponse;
import com.surimap.marker.photo.exception.PhotoApiException;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/markers/{markerId}/photos")
public class PhotoController {

  private final PhotoService photoService;
  private final PhotoRequestContextResolver contextResolver;

  public PhotoController(PhotoService photoService, PhotoRequestContextResolver contextResolver) {
    this.photoService = photoService;
    this.contextResolver = contextResolver;
  }

  @PostMapping("/upload-url")
  @RequireChannel(Channel.APP)
  @RequirePolicePhone
  @RequirePolicePhoneRegistered
  public ResponseEntity<PhotoUploadUrlResponse> createUploadUrl(
      @PathVariable UUID markerId,
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestHeader(value = "X-Client-Channel", required = false) String channel,
      @RequestHeader(value = "X-PolicePhone-Id", required = false) String policePhoneId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody PhotoUploadUrlRequest request,
      BindingResult validation) {
    PhotoRequestContext context =
        contextResolver.resolve(authorization, channel, policePhoneId, idempotencyKey);
    if (validation.hasFieldErrors("contentType")) {
      throw new PhotoApiException("write_conflict", HttpStatus.CONFLICT);
    }
    // ponytail: 형식 누락 오류는 기존 Service 순서를 유지한다. 누락 처리 개선 후 필수 검증으로 교체한다.
    if (request.getContentType() != null && validation.hasFieldErrors("sizeBytes")) {
      throw new PhotoApiException("photo_limit_exceeded", HttpStatus.PAYLOAD_TOO_LARGE);
    }
    PhotoUploadUrlServiceResponse response =
        photoService.createUploadUrl(request.toServiceRequest(markerId, context));
    return ResponseEntity.status(HttpStatus.CREATED).body(PhotoUploadUrlResponse.from(response));
  }

  @PostMapping("/{photoId}/attach")
  @RequireChannel(Channel.APP)
  @RequirePolicePhone
  @RequirePolicePhoneRegistered
  public ResponseEntity<PhotoAttachResponse> attach(
      @PathVariable UUID markerId,
      @PathVariable UUID photoId,
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestHeader(value = "X-Client-Channel", required = false) String channel,
      @RequestHeader(value = "X-PolicePhone-Id", required = false) String policePhoneId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody PhotoAttachRequest request) {
    PhotoRequestContext context =
        contextResolver.resolve(authorization, channel, policePhoneId, idempotencyKey);
    return ResponseEntity.ok(photoService.attach(markerId, photoId, request, context));
  }
}
