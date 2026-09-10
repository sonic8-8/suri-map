package com.surimap.app.controller.photo;

import com.surimap.app.controller.photo.request.MarkerCreatePhotoUploadUrlRequest;
import com.surimap.app.controller.photo.request.PhotoAttachRequest;
import com.surimap.app.controller.photo.request.PhotoUploadUrlRequest;
import com.surimap.app.controller.photo.response.PhotoAttachResponse;
import com.surimap.app.controller.photo.response.PhotoUploadUrlResponse;
import com.surimap.app.service.photo.PhotoRequestContext;
import com.surimap.app.service.photo.PhotoService;
import com.surimap.app.service.photo.response.PhotoUploadUrlServiceResponse;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequirePolicePhone;
import com.surimap.common.auth.RequirePolicePhoneRegistered;
import com.surimap.global.error.BusinessException;
import com.surimap.global.error.ErrorCode;
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
@RequestMapping("/api/markers")
public class PhotoController {

  private final PhotoService photoService;
  private final PhotoRequestContextResolver contextResolver;

  public PhotoController(PhotoService photoService, PhotoRequestContextResolver contextResolver) {
    this.photoService = photoService;
    this.contextResolver = contextResolver;
  }

  @PostMapping("/{markerId}/photos/upload-url")
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
    validatePhotoRequest(validation);
    PhotoUploadUrlServiceResponse response =
        photoService.createUploadUrl(request.toServiceRequest(markerId, context));
    return ResponseEntity.status(HttpStatus.CREATED).body(PhotoUploadUrlResponse.from(response));
  }

  @PostMapping("/photos/upload-url")
  @RequireChannel(Channel.APP)
  @RequirePolicePhone
  @RequirePolicePhoneRegistered
  public ResponseEntity<PhotoUploadUrlResponse> createUploadUrlBeforeMarkerCreation(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestHeader(value = "X-Client-Channel", required = false) String channel,
      @RequestHeader(value = "X-PolicePhone-Id", required = false) String policePhoneId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody MarkerCreatePhotoUploadUrlRequest request,
      BindingResult validation) {
    PhotoRequestContext context =
        contextResolver.resolve(authorization, channel, policePhoneId, idempotencyKey);
    validatePhotoRequest(validation);
    PhotoUploadUrlServiceResponse response =
        photoService.createUploadUrlBeforeMarkerCreation(request.toServiceRequest(context));
    return ResponseEntity.status(HttpStatus.CREATED).body(PhotoUploadUrlResponse.from(response));
  }

  @PostMapping("/{markerId}/photos/{photoId}/attach")
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
      @Valid @RequestBody PhotoAttachRequest request,
      BindingResult validation) {
    PhotoRequestContext context =
        contextResolver.resolve(authorization, channel, policePhoneId, idempotencyKey);
    validatePhotoRequest(validation);
    return ResponseEntity.ok(
        PhotoAttachResponse.from(
            photoService.attach(request.toServiceRequest(markerId, photoId, context))));
  }

  private void validatePhotoRequest(BindingResult validation) {
    if (validation.hasFieldErrors("markerId")
        || validation.hasFieldErrors("incidentId")
        || validation.hasFieldErrors("opId")) {
      throw new BusinessException(ErrorCode.WRITE_CONFLICT);
    }
    if (validation.hasFieldErrors("contentType")) {
      throw new BusinessException(ErrorCode.INVALID_PHOTO_CONTENT_TYPE);
    }
    if (validation.hasFieldErrors("sizeBytes")) {
      throw new BusinessException(ErrorCode.PHOTO_LIMIT_EXCEEDED);
    }
  }
}
