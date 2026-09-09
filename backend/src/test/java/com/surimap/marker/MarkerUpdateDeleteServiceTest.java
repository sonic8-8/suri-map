package com.surimap.marker;

import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.INCIDENT_ID;
import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.OP1_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.surimap.marker.domain.MarkerSource;
import com.surimap.marker.domain.MarkerStatus;
import com.surimap.marker.domain.MarkerType;
import com.surimap.marker.domain.service.MarkerLocationValidatorImpl;
import com.surimap.marker.dto.MarkerGeoJsonPoint;
import com.surimap.marker.dto.MarkerPublishRequest;
import com.surimap.marker.exception.MarkerApiException;
import com.surimap.marker.photo.security.SuriMapAuthentication;
import com.surimap.marker.repository.MarkerCreateRecord;
import com.surimap.marker.repository.MarkerRecord;
import com.surimap.marker.repository.MarkerSeedRecord;
import com.surimap.marker.seed.support.InMemoryMarkerRepository;
import com.surimap.marker.service.MarkerMutationContext;
import com.surimap.marker.service.MarkerRequestContext;
import com.surimap.marker.service.MarkerUpdateDeleteService;
import com.surimap.marker.service.request.MarkerDeleteServiceRequest;
import com.surimap.marker.service.request.MarkerUpdateServiceRequest;
import com.surimap.marker.service.response.MarkerMutationServiceResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MarkerUpdateDeleteServiceTest {

  private static final UUID MARKER_ID = UUID.fromString("55555555-5555-5555-5555-555555550072");
  private static final UUID MOCK_SEED_MARKER_ID =
      UUID.fromString("55555555-5555-5555-5555-555555550172");
  private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111110072");
  private static final UUID POLICE_PHONE_ID =
      UUID.fromString("22222222-2222-2222-2222-222222220072");
  private static final Instant CLIENT_TS = Instant.parse("2026-04-28T00:05:00Z");
  private static final Instant SERVER_TS = Instant.parse("2026-04-28T00:06:03Z");

  private final InMemoryMarkerRepository markerRepository = new InMemoryMarkerRepository();
  private final CapturingMarkerEventPublisher eventPublisher = new CapturingMarkerEventPublisher();
  private final AllowingMarkerWriteGuard guard = new AllowingMarkerWriteGuard();
  private final MarkerUpdateDeleteService service =
      new MarkerUpdateDeleteService(
          markerRepository,
          new MarkerLocationValidatorImpl(),
          guard,
          eventPublisher,
          Clock.fixed(SERVER_TS, ZoneOffset.UTC));

  private MarkerRequestContext appContext;

  @BeforeEach
  void setUp() {
    appContext =
        new MarkerRequestContext(
            new SuriMapAuthentication(ACCOUNT_ID, "APP", POLICE_PHONE_ID),
            "idem-marker-update-delete-001");
    markerRepository.insertCreate(
        new MarkerCreateRecord(
            INCIDENT_ID,
            MARKER_ID,
            OP1_ID,
            null,
            MarkerType.CLUE,
            null,
            new MarkerGeoJsonPoint(
                    "Point", List.of(new BigDecimal("126.913400"), new BigDecimal("35.163100")))
                .toPoint(),
            "initial clue",
            CLIENT_TS,
            ACCOUNT_ID,
            POLICE_PHONE_ID,
            MarkerSource.APP,
            MarkerStatus.ACTIVE,
            1L));
  }

  @Test
  @DisplayName("업무폰 정보가 없는 사전 등록 마커를 웹에서 수정하면, 수정 이벤트에도 업무폰 정보 없이 기록한다")
  void updateMarker_webSeedWithoutPolicePhone_publishesEventWithoutPolicePhone() {
    // given: 업무폰 정보가 없는 사전 등록 마커와 웹 요청을 준비한다.
    insertMockSeedMarkerWithNullPolicePhone();
    guard.mutationPolicePhoneId = null;
    MarkerRequestContext webContext =
        new MarkerRequestContext(
            new SuriMapAuthentication(ACCOUNT_ID, "WEB", null), "idem-web-update-null-phone-001");

    // when: 메모와 마커 유형을 수정한다.
    MarkerMutationServiceResponse response =
        service.update(
            MarkerUpdateServiceRequest.builder()
                .markerId(MOCK_SEED_MARKER_ID)
                .version(1L)
                .memo("web corrected seed marker")
                .type("NOTE")
                .context(webContext)
                .build());

    // then: 수정 결과와 이벤트에 업무폰 정보가 추가되지 않는다.
    assertThat(response.getId()).isEqualTo(MOCK_SEED_MARKER_ID);
    assertThat(response.getStatus()).isEqualTo("UPDATED");
    assertThat(response.getVersion()).isEqualTo(2L);

    MarkerRecord row = markerRepository.findById(MOCK_SEED_MARKER_ID).orElseThrow();
    assertThat(row.getPolicePhoneId()).isNull();
    assertThat(row.getStatus()).isEqualTo(MarkerStatus.UPDATED.name());
    assertThat(row.getMemo()).isEqualTo("web corrected seed marker");

    MarkerPublishRequest published = eventPublisher.published().get(0);
    assertThat(published.type()).isEqualTo("MARKER_UPDATED");
    assertThat(published.payload().id()).isEqualTo(MOCK_SEED_MARKER_ID);
    assertThat(published.payload().policePhoneId()).isNull();
    assertThat(published.payload().status()).isEqualTo("UPDATED");
    assertThat(published.payload().version()).isEqualTo(2L);
  }

  @Test
  @DisplayName("업무폰 정보가 없는 사전 등록 마커를 웹에서 삭제하면, 삭제 이벤트에도 업무폰 정보 없이 기록한다")
  void deleteMarker_webSeedWithoutPolicePhone_publishesEventWithoutPolicePhone() {
    // given: 업무폰 정보가 없는 사전 등록 마커와 웹 요청을 준비한다.
    insertMockSeedMarkerWithNullPolicePhone();
    guard.mutationPolicePhoneId = null;
    MarkerRequestContext webContext =
        new MarkerRequestContext(
            new SuriMapAuthentication(ACCOUNT_ID, "WEB", null), "idem-web-delete-null-phone-001");

    // when: 사전 등록 마커를 삭제한다.
    MarkerMutationServiceResponse response =
        service.delete(
            MarkerDeleteServiceRequest.builder()
                .markerId(MOCK_SEED_MARKER_ID)
                .version(1L)
                .reason("seed cleanup")
                .context(webContext)
                .build());

    // then: 삭제 결과와 이벤트에 업무폰 정보가 추가되지 않는다.
    assertThat(response.getId()).isEqualTo(MOCK_SEED_MARKER_ID);
    assertThat(response.getStatus()).isEqualTo("DELETED");
    assertThat(response.getVersion()).isEqualTo(2L);

    MarkerRecord row = markerRepository.findById(MOCK_SEED_MARKER_ID).orElseThrow();
    assertThat(row.getPolicePhoneId()).isNull();
    assertThat(row.getStatus()).isEqualTo(MarkerStatus.DELETED.name());

    MarkerPublishRequest published = eventPublisher.published().get(0);
    assertThat(published.type()).isEqualTo("MARKER_DELETED");
    assertThat(published.payload().id()).isEqualTo(MOCK_SEED_MARKER_ID);
    assertThat(published.payload().policePhoneId()).isNull();
    assertThat(published.payload().status()).isEqualTo("DELETED");
    assertThat(published.payload().version()).isEqualTo(2L);
  }

  @Test
  @DisplayName("수정할 메모가 2,000자를 넘으면, 마커와 이벤트를 변경하지 않고 거부한다")
  void updateMarker_memoExceedsTwoThousandCharacters_rejectsWithoutChangingMarkerOrEvents() {
    // given: 허용 길이를 넘는 메모를 준비한다.
    String tooLongMemo = "m".repeat(2001);

    // when & then: 수정 요청을 거부한다.
    assertThatThrownBy(
            () ->
                service.update(
                    MarkerUpdateServiceRequest.builder()
                        .markerId(MARKER_ID)
                        .version(1L)
                        .memo(tooLongMemo)
                        .context(appContext)
                        .build()))
        .isInstanceOf(MarkerApiException.class);

    // then: 기존 마커를 유지하고 이벤트를 발행하지 않는다.
    MarkerRecord row = markerRepository.records().get(0);
    assertThat(row.getStatus()).isEqualTo(MarkerStatus.ACTIVE.name());
    assertThat(row.getVersion()).isEqualTo(1L);
    assertThat(row.getMemo()).isEqualTo("initial clue");
    assertThat(eventPublisher.published()).isEmpty();
  }

  @Test
  @DisplayName("마커를 수정하면, 변경된 내용과 증가한 버전을 저장하고 수정 이벤트를 발행한다")
  void updateMarker_currentVersion_savesChangesAndPublishesUpdatedEvent() {
    // given: 현재 버전과 변경할 좌표·메모·유형을 준비한다.
    MarkerUpdateServiceRequest request =
        MarkerUpdateServiceRequest.builder()
            .markerId(MARKER_ID)
            .version(1L)
            .location(
                new MarkerGeoJsonPoint(
                    "Point", List.of(new BigDecimal("126.9137007"), new BigDecimal("35.1634007"))))
            .memo("updated clue memo")
            .type("NOTE")
            .context(appContext)
            .build();

    // when: 마커를 수정한다.
    MarkerMutationServiceResponse response = service.update(request);

    // then: 수정 결과와 이벤트에 같은 내용과 버전을 기록한다.
    assertThat(response.getId()).isEqualTo(MARKER_ID);
    assertThat(response.getStatus()).isEqualTo("UPDATED");
    assertThat(response.getVersion()).isEqualTo(2L);

    MarkerRecord row = markerRepository.records().get(0);
    assertThat(row.getStatus()).isEqualTo(MarkerStatus.UPDATED.name());
    assertThat(row.getVersion()).isEqualTo(2L);
    assertThat(row.getMarkerType()).isEqualTo(MarkerType.NOTE.name());
    assertThat(row.getMemo()).isEqualTo("updated clue memo");
    assertThat(row.getLocation().getSRID()).isEqualTo(4326);
    assertThat(row.getLocation().getX()).isEqualTo(126.913701);
    assertThat(row.getLocation().getY()).isEqualTo(35.163401);

    MarkerPublishRequest published = eventPublisher.published().get(0);
    assertThat(published.type()).isEqualTo("MARKER_UPDATED");
    assertThat(published.payload().id()).isEqualTo(MARKER_ID);
    assertThat(published.payload().incidentId()).isEqualTo(INCIDENT_ID);
    assertThat(published.payload().opId()).isEqualTo(OP1_ID);
    assertThat(published.payload().policePhoneId()).isEqualTo(POLICE_PHONE_ID);
    assertThat(published.payload().status()).isEqualTo("UPDATED");
    assertThat(published.payload().version()).isEqualTo(2L);
    assertThat(published.payload().type()).isEqualTo("NOTE");
    assertThat(published.payload().location().coordinates())
        .containsExactly(new BigDecimal("126.913701"), new BigDecimal("35.163401"));
    assertThat(published.payload().serverTs()).isEqualTo(SERVER_TS);
  }

  @Test
  @DisplayName("마커를 삭제하면, 삭제 상태와 증가한 버전을 저장하고 삭제 이벤트를 발행한다")
  void deleteMarker_currentVersion_marksDeletedAndPublishesDeletedEvent() {
    // given: 버전이 1인 앱 마커가 저장되어 있다.
    // when: 현재 버전으로 마커 삭제를 요청한다.
    MarkerMutationServiceResponse response =
        service.delete(
            MarkerDeleteServiceRequest.builder()
                .markerId(MARKER_ID)
                .version(1L)
                .reason("wrong marker")
                .context(appContext)
                .build());

    // then: 행을 지우지 않고 삭제 상태와 새 버전을 기록한다.
    assertThat(response.getId()).isEqualTo(MARKER_ID);
    assertThat(response.getStatus()).isEqualTo("DELETED");
    assertThat(response.getVersion()).isEqualTo(2L);

    MarkerRecord row = markerRepository.records().get(0);
    assertThat(row.getStatus()).isEqualTo(MarkerStatus.DELETED.name());
    assertThat(row.getVersion()).isEqualTo(2L);

    MarkerPublishRequest published = eventPublisher.published().get(0);
    assertThat(published.type()).isEqualTo("MARKER_DELETED");
    assertThat(published.payload().id()).isEqualTo(MARKER_ID);
    assertThat(published.payload().incidentId()).isEqualTo(INCIDENT_ID);
    assertThat(published.payload().opId()).isEqualTo(OP1_ID);
    assertThat(published.payload().policePhoneId()).isEqualTo(POLICE_PHONE_ID);
    assertThat(published.payload().status()).isEqualTo("DELETED");
    assertThat(published.payload().version()).isEqualTo(2L);
  }

  @Nested
  class Guards {

    @Test
    @DisplayName("요청 버전이 저장된 버전과 다르면, 마커와 이벤트를 변경하지 않고 write_conflict 오류로 거부한다")
    void updateMarker_versionMismatch_rejectsWithoutChangingMarkerOrEvents() {
      // given: 저장된 마커 버전은 1인데 요청 버전은 99이다.
      // when & then: 버전이 다른 수정 요청을 거부한다.
      assertThatThrownBy(
              () ->
                  service.update(
                      MarkerUpdateServiceRequest.builder()
                          .markerId(MARKER_ID)
                          .version(99L)
                          .memo("stale memo")
                          .context(appContext)
                          .build()))
          .isInstanceOf(MarkerApiException.class)
          .extracting("error")
          .isEqualTo("write_conflict");

      // then: 기존 마커와 이벤트를 유지한다.
      MarkerRecord row = markerRepository.records().get(0);
      assertThat(row.getStatus()).isEqualTo(MarkerStatus.ACTIVE.name());
      assertThat(row.getVersion()).isEqualTo(1L);
      assertThat(row.getMemo()).isEqualTo("initial clue");
      assertThat(eventPublisher.published()).isEmpty();
    }

    @Test
    @DisplayName("권한 검사에서 삭제를 거부하면, 마커와 이벤트를 변경하지 않고 role_denied 오류를 전달한다")
    void deleteMarker_accessDenied_rejectsWithoutChangingMarkerOrEvents() {
      // given: 권한 검사가 삭제를 거부하도록 준비한다.
      guard.error = new MarkerApiException("role_denied", HttpStatus.FORBIDDEN);

      // when & then: 삭제 요청에 권한 오류를 전달한다.
      assertThatThrownBy(
              () ->
                  service.delete(
                      MarkerDeleteServiceRequest.builder()
                          .markerId(MARKER_ID)
                          .version(1L)
                          .reason("not authorized")
                          .context(appContext)
                          .build()))
          .isInstanceOf(MarkerApiException.class)
          .extracting("error")
          .isEqualTo("role_denied");

      // then: 기존 마커를 유지하고 이벤트를 발행하지 않는다.
      MarkerRecord row = markerRepository.records().get(0);
      assertThat(row.getStatus()).isEqualTo(MarkerStatus.ACTIVE.name());
      assertThat(row.getVersion()).isEqualTo(1L);
      assertThat(eventPublisher.published()).isEmpty();
    }
  }

  private void insertMockSeedMarkerWithNullPolicePhone() {
    markerRepository.insertSeed(
        new MarkerSeedRecord(
            INCIDENT_ID,
            MOCK_SEED_MARKER_ID,
            OP1_ID,
            null,
            MarkerType.FIELD_CONDITION,
            null,
            new MarkerGeoJsonPoint(
                    "Point", List.of(new BigDecimal("126.913600"), new BigDecimal("35.163300")))
                .toPoint(),
            "initial seeded marker",
            CLIENT_TS,
            ACCOUNT_ID,
            null,
            MarkerSource.MOCK_SEED,
            MarkerStatus.ACTIVE,
            1L));
  }

  private static final class CapturingMarkerEventPublisher
      implements com.surimap.marker.port.MarkerEventPublisher {

    private final List<MarkerPublishRequest> published = new ArrayList<>();

    @Override
    public void publish(MarkerPublishRequest request) {
      published.add(request);
    }

    List<MarkerPublishRequest> published() {
      return published;
    }
  }

  private static final class AllowingMarkerWriteGuard
      implements com.surimap.marker.port.MarkerWriteGuardPort {

    private MarkerApiException error;
    private UUID mutationPolicePhoneId = POLICE_PHONE_ID;

    @Override
    public UUID requireCreateAccess(UUID incidentId, UUID opId, MarkerRequestContext context) {
      return null;
    }

    @Override
    public MarkerMutationContext requireUpdateAccess(UUID markerId, MarkerRequestContext context) {
      if (error != null) {
        throw error;
      }
      return new MarkerMutationContext(INCIDENT_ID, markerId, OP1_ID, mutationPolicePhoneId);
    }

    @Override
    public MarkerMutationContext requireDeleteAccess(UUID markerId, MarkerRequestContext context) {
      if (error != null) {
        throw error;
      }
      return new MarkerMutationContext(INCIDENT_ID, markerId, OP1_ID, mutationPolicePhoneId);
    }
  }
}
