package com.surimap.marker.photo.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.eventhub.dto.PublishRequest;
import com.surimap.eventhub.port.EventHub;
import com.surimap.marker.photo.dto.PhotoDelta;
import com.surimap.marker.photo.dto.PublishRequestPayload;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventHubPhotoEventPublisherTest {

  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final UUID MARKER_ID = UUID.fromString("4ca60e44-9cfe-410b-8794-2983baac0eac");
  private static final UUID PHOTO_ID = UUID.fromString("55555555-5555-5555-5555-555555550001");
  private static final UUID OP_ID = UUID.fromString("88888888-8888-8888-8888-888888880001");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000101");

  private final CapturingEventHub eventHub = new CapturingEventHub();
  private final EventHubPhotoEventPublisher publisher = new EventHubPhotoEventPublisher(eventHub);

  @Test
  @DisplayName("photo attach MARKER_UPDATED 이벤트는 EventHub payload에 photoDelta를 포함한다")
  void photoAttachMarkerUpdatedEventContainsPhotoDelta() {
    publisher.publish(
        new com.surimap.marker.photo.dto.PublishRequest(
            "MARKER_UPDATED",
            new PublishRequestPayload(
                MARKER_ID,
                INCIDENT_ID,
                OP_ID,
                POLICE_PHONE_ID,
                "UPDATED",
                2L,
                new PhotoDelta(PHOTO_ID, "ATTACHED", 2L))));

    PublishRequest captured = eventHub.captured;
    assertThat(captured.type()).isEqualTo("MARKER_UPDATED");
    assertThat(captured.incidentId()).isEqualTo(INCIDENT_ID);
    assertThat(captured.sourceEntityType()).isEqualTo("marker");
    assertThat(captured.sourceEntityId()).isEqualTo(MARKER_ID);
    assertThat(captured.payload())
        .containsEntry("id", MARKER_ID.toString())
        .containsEntry("incidentId", INCIDENT_ID.toString())
        .containsEntry("opId", OP_ID.toString())
        .containsEntry("policePhoneId", POLICE_PHONE_ID.toString())
        .containsEntry("status", "UPDATED")
        .containsEntry("version", 2L);
    assertThat(captured.payload().get("photoDelta"))
        .isEqualTo(
            Map.of(
                "photoId", PHOTO_ID.toString(),
                "status", "ATTACHED",
                "version", 2L));
  }

  private static final class CapturingEventHub implements EventHub {

    private PublishRequest captured;

    @Override
    public void publish(PublishRequest request) {
      captured = request;
    }
  }
}
