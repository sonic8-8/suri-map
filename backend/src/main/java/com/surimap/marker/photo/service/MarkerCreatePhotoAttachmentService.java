package com.surimap.marker.photo.service;

import com.surimap.app.service.photo.PhotoService;
import com.surimap.client.storage.ObjectStoragePort;
import com.surimap.domain.marker.Marker;
import com.surimap.domain.marker.MarkerMapper;
import com.surimap.marker.dto.MarkerCreatePhotoRequest;
import com.surimap.marker.dto.MarkerCreatePhotoResponse;
import com.surimap.marker.photo.domain.MarkerPhoto;
import com.surimap.marker.photo.exception.PhotoApiException;
import com.surimap.marker.photo.repository.PhotoMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MarkerCreatePhotoAttachmentService {

  private final ObjectStoragePort storagePort;
  private final MarkerMapper markerMapper;
  private final Clock clock = Clock.systemUTC();
  private final PhotoService photoService;
  private final PhotoMapper photoMapper;

  public MarkerCreatePhotoAttachmentService(
      ObjectStoragePort storagePort,
      MarkerMapper markerMapper,
      PhotoService photoService,
      PhotoMapper photoMapper) {
    this.storagePort = Objects.requireNonNull(storagePort);
    this.markerMapper = Objects.requireNonNull(markerMapper);
    this.photoService = Objects.requireNonNull(photoService);
    this.photoMapper = Objects.requireNonNull(photoMapper);
  }

  public AttachmentResult attachForCreate(Marker marker, List<MarkerCreatePhotoRequest> photos) {
    if (photos == null || photos.isEmpty()) {
      return new AttachmentResult(marker.getStatus(), marker.getVersion(), List.of());
    }
    validatePhotos(photos);

    List<MarkerCreatePhotoResponse> responses = new ArrayList<>();
    for (MarkerCreatePhotoRequest request : photos) {
      MarkerPhoto photo =
          photoMapper.findById(request.getPhotoId()).orElseThrow(() -> conflict("write_conflict"));
      requireAttachableMarker(marker.getId(), photo);
      requireOpenUploadUrl(photo);
      ObjectStoragePort.ObjectMetadata objectMetadata = requireUploadedObject(photo);
      requireMatchingMetadata(photo, request, objectMetadata);

      long expectedPhotoVersion = photo.getVersion();
      photo.attach(clock.instant(), request.getWidth(), request.getHeight());
      if (photoMapper.attachPendingPhoto(photo, expectedPhotoVersion) != 1) {
        throw conflict("write_conflict");
      }
      long expectedMarkerVersion = marker.getVersion();
      marker.markUpdated(expectedMarkerVersion);
      int updated =
          markerMapper.updateMarkerStatusVersion(
              marker.getId(), expectedMarkerVersion, marker.getStatus(), marker.getVersion());
      if (updated != 1) {
        throw conflict("write_conflict");
      }
      photoService.publishMarkerPhotoUpdate(marker, marker.getPolicePhoneId(), photo);
      responses.add(
          MarkerCreatePhotoResponse.builder()
              .photoId(photo.getId())
              .status(photo.getStatus().name())
              .version(photo.getVersion())
              .markerId(marker.getId())
              .markerVersion(marker.getVersion())
              .build());
    }
    return new AttachmentResult(marker.getStatus(), marker.getVersion(), responses);
  }

  private void validatePhotos(List<MarkerCreatePhotoRequest> photos) {
    if (photos.size() > PhotoService.MAX_PHOTOS_PER_MARKER) {
      throw new PhotoApiException("photo_limit_exceeded", HttpStatus.PAYLOAD_TOO_LARGE);
    }
    var photoIds = new HashSet<UUID>();
    for (MarkerCreatePhotoRequest photo : photos) {
      if (photo == null
          || photo.getPhotoId() == null
          || !photoIds.add(photo.getPhotoId())
          || !PhotoService.ALLOWED_CONTENT_TYPES.contains(photo.getContentType())) {
        throw conflict("write_conflict");
      }
      if (photo.getSizeBytes() <= 0 || photo.getSizeBytes() > PhotoService.MAX_SIZE_BYTES) {
        throw new PhotoApiException("photo_limit_exceeded", HttpStatus.PAYLOAD_TOO_LARGE);
      }
      if ((photo.getWidth() != null && photo.getWidth() <= 0)
          || (photo.getHeight() != null && photo.getHeight() <= 0)) {
        throw conflict("write_conflict");
      }
    }
  }

  private void requireAttachableMarker(UUID markerId, MarkerPhoto photo) {
    if (!photo.getMarkerId().equals(markerId) || !photo.isOpenForAttach()) {
      throw conflict("write_conflict");
    }
  }

  private void requireOpenUploadUrl(MarkerPhoto photo) {
    if (!photo.getUploadUrlExpiresAt().isAfter(clock.instant())) {
      photoService.failPendingPhoto(photo.getId(), photo.getVersion());
      throw conflict("write_conflict");
    }
  }

  private ObjectStoragePort.ObjectMetadata requireUploadedObject(MarkerPhoto photo) {
    return storagePort
        .headObject(photo.getObjectKey())
        .orElseThrow(() -> conflict("write_conflict"));
  }

  private void requireMatchingMetadata(
      MarkerPhoto photo,
      MarkerCreatePhotoRequest request,
      ObjectStoragePort.ObjectMetadata objectMetadata) {
    if (photo.getSizeBytes() != request.getSizeBytes()
        || !photo.getContentType().equals(request.getContentType())
        || !photo.getObjectKey().equals(objectMetadata.objectKey())
        || !photo.getContentType().equals(objectMetadata.contentType())
        || photo.getSizeBytes() != objectMetadata.sizeBytes()
        || !checksumMatches(
            photo.getChecksumSha256(),
            request.getChecksumSha256(),
            objectMetadata.checksumSha256())) {
      photoService.failPendingPhoto(photo.getId(), photo.getVersion());
      throw conflict("write_conflict");
    }
  }

  private boolean checksumMatches(String expected, String requested, String uploaded) {
    if (expected != null
        && (!Objects.equals(expected, requested) || !Objects.equals(expected, uploaded))) {
      return false;
    }
    if (requested != null || uploaded != null) {
      return Objects.equals(requested, uploaded);
    }
    return true;
  }

  private PhotoApiException conflict(String error) {
    return new PhotoApiException(error, HttpStatus.CONFLICT);
  }

  public record AttachmentResult(
      String markerStatus, long markerVersion, List<MarkerCreatePhotoResponse> photos) {
    public AttachmentResult {
      photos = photos == null ? List.of() : List.copyOf(photos);
    }
  }
}
