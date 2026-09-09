package com.surimap.marker.photo.service;

import com.surimap.app.service.photo.PhotoService;
import com.surimap.client.storage.ObjectStoragePort;
import com.surimap.domain.marker.Marker;
import com.surimap.domain.marker.MarkerMapper;
import com.surimap.marker.dto.MarkerCreatePhotoRequest;
import com.surimap.marker.dto.MarkerCreatePhotoResponse;
import com.surimap.marker.photo.domain.MarkerPhoto;
import com.surimap.marker.photo.dto.PhotoDelta;
import com.surimap.marker.photo.dto.PublishRequest;
import com.surimap.marker.photo.dto.PublishRequestPayload;
import com.surimap.marker.photo.exception.PhotoApiException;
import com.surimap.marker.photo.port.PhotoEventPublisher;
import com.surimap.marker.photo.repository.PhotoMapper;
import com.surimap.marker.photo.repository.PhotoRepository;
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

  private final PhotoRepository photoRepository;
  private final ObjectStoragePort storagePort;
  private final MarkerMapper markerMapper;
  private final PhotoEventPublisher photoEventPublisher;
  private final Clock clock = Clock.systemUTC();
  private final PhotoService photoService;
  private final PhotoMapper photoMapper;

  public MarkerCreatePhotoAttachmentService(
      PhotoRepository photoRepository,
      ObjectStoragePort storagePort,
      MarkerMapper markerMapper,
      PhotoEventPublisher photoEventPublisher,
      PhotoService photoService,
      PhotoMapper photoMapper) {
    this.photoRepository = Objects.requireNonNull(photoRepository);
    this.storagePort = Objects.requireNonNull(storagePort);
    this.markerMapper = Objects.requireNonNull(markerMapper);
    this.photoEventPublisher = Objects.requireNonNull(photoEventPublisher);
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
          photoRepository
              .findById(request.getPhotoId())
              .orElseThrow(() -> conflict("write_conflict"));
      requireAttachableMarker(marker.getId(), photo);
      requireOpenUploadUrl(photo);
      ObjectStoragePort.ObjectMetadata objectMetadata = requireUploadedObject(photo);
      requireMatchingMetadata(photo, request, objectMetadata);

      long expectedPhotoVersion = photo.version();
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
      PublishRequest publishRequest = publishRequest(marker, photo);
      photoEventPublisher.publish(publishRequest);
      responses.add(
          MarkerCreatePhotoResponse.builder()
              .photoId(photo.id())
              .status(photo.status().name())
              .version(photo.version())
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
    if (!photo.markerId().equals(markerId) || !photo.isOpenForAttach()) {
      throw conflict("write_conflict");
    }
  }

  private void requireOpenUploadUrl(MarkerPhoto photo) {
    if (!photo.uploadUrlExpiresAt().isAfter(clock.instant())) {
      photoService.failPendingPhoto(photo.id(), photo.version());
      throw conflict("write_conflict");
    }
  }

  private ObjectStoragePort.ObjectMetadata requireUploadedObject(MarkerPhoto photo) {
    return storagePort.headObject(photo.objectKey()).orElseThrow(() -> conflict("write_conflict"));
  }

  private void requireMatchingMetadata(
      MarkerPhoto photo,
      MarkerCreatePhotoRequest request,
      ObjectStoragePort.ObjectMetadata objectMetadata) {
    if (photo.sizeBytes() != request.getSizeBytes()
        || !photo.contentType().equals(request.getContentType())
        || !photo.objectKey().equals(objectMetadata.objectKey())
        || !photo.contentType().equals(objectMetadata.contentType())
        || photo.sizeBytes() != objectMetadata.sizeBytes()
        || !checksumMatches(
            photo.checksumSha256(), request.getChecksumSha256(), objectMetadata.checksumSha256())) {
      photoService.failPendingPhoto(photo.id(), photo.version());
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

  private PublishRequest publishRequest(Marker marker, MarkerPhoto photo) {
    return new PublishRequest(
        "MARKER_UPDATED",
        new PublishRequestPayload(
            marker.getId(),
            marker.getIncidentId(),
            marker.getOperationalPeriodId(),
            marker.getPolicePhoneId(),
            marker.getStatus(),
            marker.getVersion(),
            new PhotoDelta(photo.id(), photo.status().name(), photo.version())));
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
