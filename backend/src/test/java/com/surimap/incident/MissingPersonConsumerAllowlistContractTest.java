package com.surimap.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.surimap.board.BoardAssembler;
import com.surimap.board.BoardAssemblyRequest;
import com.surimap.board.BoardDTO;
import com.surimap.board.BoardSourceRow;
import com.surimap.incident.controller.response.IncidentDetailMissingPersonResponse;
import com.surimap.incident.domain.MissingPersonConsumerView;
import com.surimap.incident.domain.MissingPersonRecord;
import com.surimap.incident.repository.IncidentMapper;
import com.surimap.incident.service.MissingPersonQueryService;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("L1-T03 실종자 정보 소비 필드 허용 목록")
class MissingPersonConsumerAllowlistContractTest {

  private static final String DETAIL_MISSING_PERSON_RESPONSE =
      "com.surimap.incident.controller.response.IncidentDetailMissingPersonResponse";
  private static final String MISSING_PERSON_CONSUMER_VIEW =
      "com.surimap.incident.domain.MissingPersonConsumerView";

  // S1-1이 incident detail과 S7 package 입력에 넘길 수 있는 active missing_person 필드만 둔다.
  private static final List<String> S1_1_ALLOWED_MISSING_PERSON_FIELDS =
      List.of(
          "incidentId",
          "displayName",
          "photoObjectKey",
          "appearanceText",
          "lastSeenLocationText",
          "lastSeenAt");
  private static final List<String> FORBIDDEN_MISSING_PERSON_FIELDS =
      List.of(
          "importedAt",
          "sourceFixture",
          "missingPersonId",
          "name",
          "sex",
          "gender",
          "age",
          "lastSeenSummary",
          "residentRegistrationNumber",
          "rrn",
          "socialSecurityNumber",
          "phone",
          "phoneNumber",
          "mobilePhone",
          "contactNumber",
          "address",
          "homeAddress",
          "roadAddress",
          "detailAddress");

  @Test
  @DisplayName("사건 상세 missingPerson DTO는 S1-1 허용 필드만 노출한다")
  void incident_detail_missing_person_dto_exposes_only_s1_1_consumer_allowlist() {
    assertAllowlist(DETAIL_MISSING_PERSON_RESPONSE);
  }

  @Test
  @DisplayName("S1-1 실종자 소비 projection은 importedAt을 내부 값으로만 둔다")
  void missing_person_consumer_view_keeps_imported_at_internal() {
    assertAllowlist(MISSING_PERSON_CONSUMER_VIEW);
  }

  @Test
  @DisplayName("진행 중 실종자 row 값이 소비 projection과 상세 응답으로 매핑된다")
  void consumer_view_and_detail_response_map_active_missing_person_values() {
    MissingPersonConsumerView view = MissingPersonConsumerView.from(activeMissingPersonRecord());
    IncidentDetailMissingPersonResponse response = IncidentDetailMissingPersonResponse.from(view);

    assertThat(response.incidentId())
        .isEqualTo(UUID.fromString("22222222-2222-2222-2222-222222220001"));
    assertThat(response.displayName()).isEqualTo("가상 실종자 001");
    assertThat(response.photoObjectKey()).isNull();
    assertThat(response.appearanceText()).isEqualTo("회색 점퍼와 검정 모자");
    assertThat(response.lastSeenLocationText()).isEqualTo("서울 종로구 사직로 161");
    assertThat(response.lastSeenAt()).isEqualTo(Instant.parse("2026-04-27T23:20:00Z"));
  }

  @Test
  @DisplayName("진행 중 실종자 row가 없으면 조회 결과도 비어 있다")
  void query_service_returns_empty_when_active_missing_person_row_is_gone() {
    UUID incidentId = UUID.fromString("22222222-2222-2222-2222-222222220001");
    IncidentMapper mapper = mock(IncidentMapper.class);
    when(mapper.findMissingPersonByIncidentId(incidentId)).thenReturn(Optional.empty());

    assertThat(new MissingPersonQueryService(mapper).findActiveConsumerView(incidentId)).isEmpty();
  }

  @Test
  @DisplayName("S3-2 종료 보드 fixture는 실종자 개인정보 필드를 제거한다")
  void s3_2_terminal_board_fixture_drops_missing_person_fields() {
    BoardDTO board =
        new BoardAssembler()
            .assemble(
                new BoardAssemblyRequest(
                    "inc-precinct-first-001",
                    "bs-inc-precinct-first-001",
                    1L,
                    OffsetDateTime.parse("2026-04-28T10:30:00+09:00"),
                    null,
                    List.of(),
                    "geometry-hash",
                    List.of(terminalIncidentRowWithMissingPersonFields())));

    @SuppressWarnings("unchecked")
    Map<String, Object> terminalSlot = (Map<String, Object>) board.slots().get("incident_terminal");
    assertThat(terminalSlot)
        .doesNotContainKeys(
            "displayName", "photoObjectKey", "appearanceText", "lastSeenLocationText", "lastSeenAt")
        .doesNotContainKeys(FORBIDDEN_MISSING_PERSON_FIELDS.toArray(String[]::new));
    assertThat(terminalSlot)
        .containsOnlyKeys(
            "id",
            "incidentId",
            "terminalStatus",
            "closedStatus",
            "closedAt",
            "writeDisabledReason",
            "localPurgeState",
            "status",
            "version",
            "sequence",
            "sourceSpec",
            "sourceHash",
            "latestEventId");
  }

  private static void assertAllowlist(String className) {
    assertThat(recordFields(className))
        .containsExactlyElementsOf(S1_1_ALLOWED_MISSING_PERSON_FIELDS)
        .doesNotContainAnyElementsOf(FORBIDDEN_MISSING_PERSON_FIELDS);
  }

  private static List<String> recordFields(String className) {
    Class<?> type = requireClass(className);
    assertThat(type.isRecord()).as("%s must be a record DTO", className).isTrue();
    return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
  }

  private static Class<?> requireClass(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException exception) {
      throw new AssertionError(
          className + " must exist for the L1-T03 consumer contract", exception);
    }
  }

  private static MissingPersonRecord activeMissingPersonRecord() {
    MissingPersonRecord record = new MissingPersonRecord();
    record.setIncidentId(UUID.fromString("22222222-2222-2222-2222-222222220001"));
    record.setDisplayName("가상 실종자 001");
    record.setPhotoObjectKey(null);
    record.setAppearanceText("회색 점퍼와 검정 모자");
    record.setLastSeenLocationText("서울 종로구 사직로 161");
    record.setLastSeenAt(Instant.parse("2026-04-27T23:20:00Z"));
    record.setImportedAt(Instant.parse("2026-04-28T00:00:00Z"));
    return record;
  }

  private static BoardSourceRow terminalIncidentRowWithMissingPersonFields() {
    return new BoardSourceRow(
        "incident_terminal",
        "S1-1",
        "inc-precinct-first-001",
        "board-terminal-inc-precinct-first-001",
        "CLOSED",
        2L,
        45L,
        "evt-incident-closed-001",
        "hash-terminal-current",
        Map.ofEntries(
            Map.entry("incidentId", "inc-precinct-first-001"),
            Map.entry("terminalStatus", "CLOSED"),
            Map.entry("closedStatus", "closed"),
            Map.entry("closedAt", "2026-04-28T10:29:58+09:00"),
            Map.entry("writeDisabledReason", "incident_closed"),
            Map.entry("localPurgeState", "queued"),
            Map.entry("displayName", "가상 실종자 001"),
            Map.entry("photoObjectKey", "fixture-photo-object-key"),
            Map.entry("appearanceText", "남색 점퍼, 회색 등산화"),
            Map.entry("lastSeenLocationText", "인왕산 북측 산책로 입구"),
            Map.entry("lastSeenAt", "2026-04-27T23:20:00Z"),
            Map.entry("importedAt", "2026-04-28T00:00:00Z"),
            Map.entry("residentRegistrationNumber", "000000-0000000"),
            Map.entry("phoneNumber", "010-0000-0000"),
            Map.entry("address", "서울시 종로구")));
  }
}
