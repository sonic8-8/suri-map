package com.surimap.marker.photo.controller;

import com.surimap.marker.photo.dto.MarkerCreatePhotoUploadUrlRequest;
import com.surimap.marker.photo.dto.PhotoUploadUrlResponse;
import com.surimap.marker.photo.service.MarkerPhotoDraftService;
import com.surimap.marker.photo.service.PhotoRequestContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/markers/photos")
public class MarkerPhotoDraftController {

  private final MarkerPhotoDraftService markerPhotoDraftService;
  private final PhotoRequestContextResolver contextResolver;

  public MarkerPhotoDraftController(
      MarkerPhotoDraftService markerPhotoDraftService, PhotoRequestContextResolver contextResolver) {
    this.markerPhotoDraftService = markerPhotoDraftService;
    this.contextResolver = contextResolver;
  }

  @PostMapping("/upload-url")
  public ResponseEntity<PhotoUploadUrlResponse> createMarkerPhotoUploadUrl(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestHeader(value = "X-Client-Channel", required = false) String channel,
      @RequestHeader(value = "X-PolicePhone-Id", required = false) String policePhoneId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody MarkerCreatePhotoUploadUrlRequest request) {
    PhotoRequestContext context =
        contextResolver.resolve(authorization, channel, policePhoneId, idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(markerPhotoDraftService.createUploadUrl(request, context));
  }
}
