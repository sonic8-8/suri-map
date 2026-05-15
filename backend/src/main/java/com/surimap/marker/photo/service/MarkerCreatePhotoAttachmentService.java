package com.surimap.marker.photo.service;

import com.surimap.marker.dto.MarkerCreatePhotoRequest;
import com.surimap.marker.dto.MarkerCreatePhotoResponse;
import com.surimap.marker.photo.domain.MarkerPhoto;
import com.surimap.marker.photo.dto.PhotoDelta;
import com.surimap.marker.photo.dto.PublishRequest;
import com.surimap.marker.photo.dto.PublishRequestPayload;
import com.surimap.marker.photo.exception.PhotoApiException;
import com.surimap.marker.photo.port.ObjectStoragePort;
import com.surimap.marker.photo.port.PhotoEventPublisher;
import com.surimap.marker.photo.repository.PhotoRepository;
import com.surimap.marker.repository.MarkerRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MarkerCreatePhotoAttachmentService {

  private final PhotoRepository photoRepository;
  private final ObjectStoragePort storagePort;
  private final MarkerRepository markerRepository;
  private final PhotoEventPublisher photoEventPublisher;
  private final Clock clock;

  @Autowired
  public MarkerCreatePhotoAttachmentService(
      PhotoRepository photoRepository,
      ObjectStoragePort storagePort,
      MarkerRepository markerRepository,
      PhotoEventPublisher photoEventPublisher) {
    this(photoRepository, storagePort, markerRepository, photoEventPublisher, Clock.systemUTC());
  }

  public MarkerCreatePhotoAttachmentService(
      PhotoRepository photoRepository,
      ObjectStoragePort storagePort,
      MarkerRepository markerRepository,
      PhotoEventPublisher photoEventPublisher,
      Clock clock) {
    this.photoRepository = Objects.requireNonNull(photoRepository);
    this.storagePort = Objects.requireNonNull(storagePort);
    this.markerRepository = Objects.requireNonNull(markerRepository);
    this.photoEventPublisher = Objects.requireNonNull(photoEventPublisher);
    this.clock = Objects.requireNonNull(clock);
  }

  public AttachmentResult attachForCreate(
      UUID incidentId,
      UUID opId,
      UUID markerId,
      UUID policePhoneId,
      long initialMarkerVersion,
      List<MarkerCreatePhotoRequest> photos) {
    if (photos == null || photos.isEmpty()) {
      return new AttachmentResult("ACTIVE", initialMarkerVersion, List.of());
    }
    validatePhotos(photos);

    long currentMarkerVersion = initialMarkerVersion;
    List<MarkerCreatePhotoResponse> responses = new ArrayList<>();
    for (MarkerCreatePhotoRequest request : photos) {
      MarkerPhoto photo =
          photoRepository.findById(request.photoId()).orElseThrow(() -> conflict("write_conflict"));
      requireAttachableMarker(markerId, photo);
      requireOpenUploadUrl(photo);
      ObjectStoragePort.ObjectMetadata objectMetadata = requireUploadedObject(photo);
      requireMatchingMetadata(photo, request, objectMetadata);

      photo.attach(clock.instant(), request.width(), request.height());
      photoRepository.save(photo);
      long nextMarkerVersion = currentMarkerVersion + 1L;
      bumpParentMarkerVersion(markerId, currentMarkerVersion, nextMarkerVersion);
      PublishRequest publishRequest =
          publishRequest(incidentId, opId, markerId, policePhoneId, nextMarkerVersion, photo);
      photoEventPublisher.publish(publishRequest);
      responses.add(
          new MarkerCreatePhotoResponse(
              photo.id(), photo.status().name(), photo.version(), markerId, nextMarkerVersion));
      currentMarkerVersion = nextMarkerVersion;
    }
    return new AttachmentResult("UPDATED", currentMarkerVersion, responses);
  }

  private void validatePhotos(List<MarkerCreatePhotoRequest> photos) {
    if (photos.size() > PhotoService.MAX_PHOTOS_PER_MARKER) {
      throw new PhotoApiException("photo_limit_exceeded", HttpStatus.PAYLOAD_TOO_LARGE);
    }
    var photoIds = new HashSet<UUID>();
    for (MarkerCreatePhotoRequest photo : photos) {
      if (photo == null
          || photo.photoId() == null
          || !photoIds.add(photo.photoId())
          || !PhotoService.ALLOWED_CONTENT_TYPES.contains(photo.contentType())) {
        throw conflict("write_conflict");
      }
      if (photo.sizeBytes() <= 0 || photo.sizeBytes() > PhotoService.MAX_SIZE_BYTES) {
        throw new PhotoApiException("photo_limit_exceeded", HttpStatus.PAYLOAD_TOO_LARGE);
      }
      if ((photo.width() != null && photo.width() <= 0)
          || (photo.height() != null && photo.height() <= 0)) {
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
      photo.fail();
      photoRepository.save(photo);
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
    if (photo.sizeBytes() != request.sizeBytes()
        || !photo.contentType().equals(request.contentType())
        || !photo.objectKey().equals(objectMetadata.objectKey())
        || !photo.contentType().equals(objectMetadata.contentType())
        || photo.sizeBytes() != objectMetadata.sizeBytes()
        || !checksumMatches(
            photo.checksumSha256(), request.checksumSha256(), objectMetadata.checksumSha256())) {
      photo.fail();
      photoRepository.save(photo);
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

  private void bumpParentMarkerVersion(UUID markerId, long expectedVersion, long nextVersion) {
    int updated =
        markerRepository.updateMarkerStatusVersion(markerId, expectedVersion, "UPDATED", nextVersion);
    if (updated != 1) {
      throw conflict("write_conflict");
    }
  }

  private PublishRequest publishRequest(
      UUID incidentId, UUID opId, UUID markerId, UUID policePhoneId, long markerVersion, MarkerPhoto photo) {
    return new PublishRequest(
        "MARKER_UPDATED",
        new PublishRequestPayload(
            markerId,
            incidentId,
            opId,
            policePhoneId,
            "UPDATED",
            markerVersion,
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
