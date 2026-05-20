package com.surimap.marker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.dto.MarkerListResponse;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.query.MarkerPhotoSummary;
import com.surimap.marker.query.MarkerQuery;
import com.surimap.marker.query.MarkerQueryFilters;
import com.surimap.marker.query.MarkerQueryResult;
import com.surimap.marker.query.MarkerView;
import com.surimap.marker.service.MarkerReadService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

@DisplayName("S5 marker public read service")
class MarkerReadServiceTest {

  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa5701");
  private static final UUID OP_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbb5701");
  private static final UUID MARKER_ID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccc5701");
  private static final UUID ACCOUNT_ID = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddd5701");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeee5701");
  private static final UUID PHOTO_ID = UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffff5701");
  private static final Instant OCCURRED_AT = Instant.parse("2026-05-14T00:00:01Z");
  private static final Instant ATTACHED_AT = Instant.parse("2026-05-14T00:00:02Z");
  private static final GeometryFactory GEOMETRY_FACTORY =
      new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 4326);

  @Test
  @DisplayName("incident/op/type/status filter를 MarkerQuery.byIncident로 전달하고 canonical response로 변환한다")
  void listMarkersMapsQueryResultToPublicResponse() {
    CapturingMarkerQuery query = new CapturingMarkerQuery();
    MarkerReadService service = new MarkerReadService(query);

    MarkerListResponse response =
        service.list(INCIDENT_ID, OP_ID, "clue", MarkerStatus.ACTIVE.name());

    assertThat(query.incidentId).isEqualTo(INCIDENT_ID);
    assertThat(query.filters.opId()).isEqualTo(OP_ID);
    assertThat(query.filters.type()).isEqualTo(MarkerType.CLUE);
    assertThat(query.filters.status()).isEqualTo(MarkerStatus.ACTIVE);
    assertThat(response.incidentId()).isEqualTo(INCIDENT_ID);
    assertThat(response.markers()).hasSize(1);
    assertThat(response.markers().get(0).id()).isEqualTo(MARKER_ID);
    assertThat(response.markers().get(0).type()).isEqualTo("CLUE");
    assertThat(response.markers().get(0).status()).isEqualTo("ACTIVE");
    assertThat(response.markers().get(0).location().coordinates())
        .extracting(Object::toString)
        .containsExactly("126.913400", "35.163100");
    assertThat(response.markers().get(0).photoSummary())
        .extracting(MarkerListResponse.MarkerPhotoSummaryResponse::photoId)
        .containsExactly(PHOTO_ID);
    assertThat(response.markers().get(0).photoSummary())
        .extracting(MarkerListResponse.MarkerPhotoSummaryResponse::photoUrl)
        .containsExactly("https://photo.example/marker-photo.jpg");
  }

  @Test
  @DisplayName("지원하지 않는 marker filter enum은 invalid_marker_filter로 거부한다")
  void invalidFilterRejected() {
    MarkerReadService service = new MarkerReadService(new CapturingMarkerQuery());

    assertThatThrownBy(() -> service.list(INCIDENT_ID, OP_ID, "bad-type", null))
        .isInstanceOf(MarkerApiException.class)
        .extracting("error")
        .isEqualTo("invalid_marker_filter");
  }

  private static final class CapturingMarkerQuery implements MarkerQuery {
    private UUID incidentId;
    private MarkerQueryFilters filters;

    @Override
    public MarkerQueryResult byIncident(UUID incidentId, MarkerQueryFilters filters) {
      this.incidentId = incidentId;
      this.filters = filters;
      return new MarkerQueryResult(
          incidentId,
          List.of(
              new MarkerView(
                  MARKER_ID,
                  incidentId,
                  OP_ID,
                  null,
                  ACCOUNT_ID,
                  POLICE_PHONE_ID,
                  MarkerType.CLUE,
                  null,
                  MarkerSource.APP,
                  MarkerStatus.ACTIVE,
                  7L,
                  GEOMETRY_FACTORY.createPoint(new Coordinate(126.9134, 35.1631)),
                  "등산로 입구 제보",
                  OCCURRED_AT,
                  List.of(
                      new MarkerPhotoSummary(
                          PHOTO_ID,
                          "ATTACHED",
                          3L,
                          "image/jpeg",
                          1024L,
                          ATTACHED_AT,
                          "https://photo.example/marker-photo.jpg",
                          "https://photo.example/marker-photo.jpg")))));
    }
  }
}
