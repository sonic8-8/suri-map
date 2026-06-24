package com.surimap.marker.photo;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.maparea.testdouble.SearchAreaQueryMock;
import com.surimap.marker.domain.fixture.MarkerGeometryFixtures;
import com.surimap.marker.domain.service.MarkerOpBindingValidator;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.photo.adapter.MockObjectStorageAdapter;
import com.surimap.marker.photo.dto.MarkerCreatePhotoUploadUrlRequest;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import com.surimap.marker.photo.service.MarkerPhotoDraftService;
import com.surimap.marker.photo.service.PhotoRequestContext;
import com.surimap.marker.photo.support.InMemoryPhotoRepository;
import com.surimap.marker.port.MarkerWriteGuardPort;
import com.surimap.marker.service.MarkerMutationContext;
import com.surimap.marker.service.MarkerRequestContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@DisplayName("마커 생성 사진 draft upload-url 서비스")
class MarkerPhotoDraftServiceTest {

  private static final UUID MARKER_ID =
      UUID.fromString("55555555-5555-5555-5555-555555550340");
  private static final UUID ACCOUNT_ID =
      UUID.fromString("11111111-1111-1111-1111-111111110340");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("22222222-2222-2222-2222-222222220340");
  private static final Instant NOW = Instant.parse("2026-05-15T00:30:00Z");

  @Test
  @DisplayName("마커 생성 전 사진 upload-url은 client markerId로 pending photo row를 만든다")
  void createMarkerPhotoUploadUrlUsesClientMarkerId() {
    var storage = new MockObjectStorageAdapter();
    var repository = new InMemoryPhotoRepository();
    var service =
        new MarkerPhotoDraftService(
            storage,
            repository,
            new AllowingMarkerWriteGuard(),
            new MarkerOpBindingValidator(incidentId -> Optional.of(MarkerGeometryFixtures.OP1_ID)),
            Clock.fixed(NOW, ZoneOffset.UTC),
            UUID::randomUUID,
            null);

    var response =
        service.createUploadUrl(
            new MarkerCreatePhotoUploadUrlRequest(
                MARKER_ID,
                MarkerGeometryFixtures.INCIDENT_ID,
                MarkerGeometryFixtures.OP1_ID,
                "image/jpeg",
                1_048_576L,
                "sha256:fixture"),
            new PhotoRequestContext(
                new SuriMapAuthentication(ACCOUNT_ID, "APP", POLICE_PHONE_ID),
                "idem-marker-create-photo-upload-001"));

    String objectKey =
        "markers/"
            + MarkerGeometryFixtures.INCIDENT_ID
            + "/"
            + MARKER_ID
            + "/"
            + response.photoId()
            + ".jpg";
    assertThat(response.uploadUrl()).isEqualTo("http://127.0.0.1:18080/mock-upload/" + objectKey);
    assertThat(response.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    assertThat(repository.findById(response.photoId()))
        .get()
        .extracting("markerId", "objectKey", "contentType", "sizeBytes")
        .containsExactly(MARKER_ID, objectKey, "image/jpeg", 1_048_576L);
  }

  private static final class AllowingMarkerWriteGuard implements MarkerWriteGuardPort {

    @Override
    public UUID requireCreateAccess(UUID incidentId, UUID opId, MarkerRequestContext context) {
      if (!"APP".equals(context.authentication().channel())) {
        throw new MarkerApiException("channel_not_allowed", HttpStatus.FORBIDDEN);
      }
      return null;
    }

    @Override
    public MarkerMutationContext requireUpdateAccess(UUID markerId, MarkerRequestContext context) {
      throw new UnsupportedOperationException("not used");
    }

    @Override
    public MarkerMutationContext requireDeleteAccess(UUID markerId, MarkerRequestContext context) {
      throw new UnsupportedOperationException("not used");
    }
  }
}
