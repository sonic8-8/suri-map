package com.surimap.api.controller.path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.surimap.api.service.path.SearchPathBoardService;
import com.surimap.api.service.path.response.SearchPathPageServiceResponse;
import com.surimap.common.auth.Channel;
import com.surimap.common.auth.guard.IncidentAccessPort;
import com.surimap.common.auth.guard.PolicePhoneValidationPort;
import com.surimap.config.ClockConfig;
import com.surimap.config.GuardConfig;
import com.surimap.global.error.GlobalExceptionHandler;
import com.surimap.global.error.SearchPathQueryExceptionHandler;
import com.surimap.retention.purge.LocationAccessRecorder;
import com.surimap.retention.purge.RecordLocationAccessAspect;
import com.surimap.support.auth.WithMockAccount;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = SearchPathBoardController.class,
    properties = "surimap.board.search-path.enabled=true")
@AutoConfigureMockMvc(addFilters = false)
@Import({
  GlobalExceptionHandler.class,
  SearchPathQueryExceptionHandler.class,
  GuardConfig.class,
  AopAutoConfiguration.class,
  RecordLocationAccessAspect.class,
  ClockConfig.class
})
@WithMockAccount(accountId = "62000000-0000-0000-0000-000000002621", channel = Channel.WEB)
class SearchPathBoardControllerTest {
  private static final String URL =
      "/api/incidents/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001/board/search-paths/";
  @Autowired private MockMvc mvc;
  @MockitoBean private SearchPathBoardService service;
  @MockitoBean private IncidentAccessPort incidentAccessPort;
  @MockitoBean private PolicePhoneValidationPort policePhoneValidationPort;
  @MockitoBean private LocationAccessRecorder locationAccessRecorder;

  @Test
  @DisplayName("빈 구간 조회도 정상 JSON으로 응답하고 위치 조회 감사를 남긴다")
  void empty_segments_query_returns_json_and_records_access() throws Exception {
    // given: 접근 가능한 웹 계정의 조회 결과가 비어 있다.
    when(service.getSearchPathSegments(any()))
        .thenReturn(SearchPathPageServiceResponse.builder().paths(List.of()).build());
    // when & then: 204 대신 명시적 빈 페이지를 반환한다.
    mvc.perform(
            post(URL + "segments/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"opIds\":[],\"paths\":[],\"nextSearchPathId\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths").isEmpty())
        .andExpect(jsonPath("$.nextSearchPathId").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.hasMore").value(false));
    verify(locationAccessRecorder)
        .record(
            any(),
            eq(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001")),
            any(),
            eq("WEB"),
            eq("BOARD_VIEW"),
            any());
  }

  @Test
  @DisplayName("구간 버전과 기준 버전은 정밀도를 잃지 않는 문자열로 반환한다")
  void versions_are_serialized_as_decimal_strings() throws Exception {
    // given: JavaScript 안전 정수 범위보다 큰 버전과 구간을 반환한다.
    SearchPathPageServiceResponse.Segment segment =
        SearchPathPageServiceResponse.Segment.builder()
            .id(UUID.randomUUID())
            .version(9007199254740993L)
            .startPointOrder(0)
            .endPointOrder(1)
            .coordinates(List.of(List.of(126.9, 35.1), List.of(126.91, 35.11)))
            .build();
    when(service.getSearchPathSegments(any()))
        .thenReturn(
            SearchPathPageServiceResponse.builder()
                .paths(
                    List.of(
                        SearchPathPageServiceResponse.Path.builder()
                            .id(UUID.randomUUID())
                            .version(9007199254740993L)
                            .baselineVersion(9007199254740993L)
                            .completed(true)
                            .beforeStartPointOrder(0)
                            .segments(List.of(segment))
                            .build()))
                .build());
    // when & then: 순번은 숫자이고 전체 경로 도형과 변경분 진행은 추가하지 않는다.
    mvc.perform(
            post(URL + "segments/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"opIds\":[],\"paths\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths[0].version").value("9007199254740993"))
        .andExpect(jsonPath("$.paths[0].baselineVersion").value("9007199254740993"))
        .andExpect(jsonPath("$.paths[0].segments[0].version").isString())
        .andExpect(jsonPath("$.paths[0].segments[0].startPointOrder").isNumber())
        .andExpect(jsonPath("$.paths[0].segments[0].geometry.type").value("LineString"))
        .andExpect(jsonPath("$.paths[0].geometry").doesNotExist())
        .andExpect(jsonPath("$.paths[0].changesProgress").doesNotExist());
  }

  @Test
  @DisplayName("변경분에서 발견한 새 경로는 기준 버전과 진행을 null로 반환한다")
  void discovered_path_has_null_baseline_and_changes_progress() throws Exception {
    // given: 변경분 조회가 새 경로의 기본 정보만 발견했다.
    when(service.getSearchPathChanges(any()))
        .thenReturn(
            SearchPathPageServiceResponse.builder()
                .paths(
                    List.of(
                        SearchPathPageServiceResponse.Path.builder()
                            .id(UUID.randomUUID())
                            .version(3)
                            .segments(List.of())
                            .build()))
                .build());
    // when & then: 빈 구간을 전체 이력 완료로 해석하지 않도록 초기 상태를 표현한다.
    mvc.perform(
            post(URL + "changes/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"opIds\":[],\"paths\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths[0].baselineVersion").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.paths[0].changesProgress").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.paths[0].segmentsProgress").doesNotExist());
  }

  @Test
  @DisplayName("좌표가 하나인 구간도 두 조회 API에서 같은 점을 잇는 LineString으로 반환한다")
  void single_point_segment_returns_valid_line_string_without_changing_point_orders()
      throws Exception {
    // given: 이동 유형이 바뀌는 지점에 원본 GPS가 하나인 구간이 있다.
    SearchPathPageServiceResponse.Segment segment =
        SearchPathPageServiceResponse.Segment.builder()
            .id(UUID.randomUUID())
            .version(1)
            .startPointOrder(3)
            .endPointOrder(3)
            .coordinates(List.of(List.of(126.9, 35.1)))
            .build();
    SearchPathPageServiceResponse response =
        SearchPathPageServiceResponse.builder()
            .paths(
                List.of(
                    SearchPathPageServiceResponse.Path.builder()
                        .id(UUID.randomUUID())
                        .version(2)
                        .baselineVersion(1L)
                        .appliedVersion(2L)
                        .targetVersion(2L)
                        .completed(true)
                        .segments(List.of(segment))
                        .build()))
            .build();
    when(service.getSearchPathSegments(any())).thenReturn(response);
    when(service.getSearchPathChanges(any())).thenReturn(response);

    // when & then: 표시용 좌표만 반복하고 원본 GPS 순번과 서비스 자료는 유지한다.
    for (String kind : List.of("segments", "changes")) {
      mvc.perform(
              post(URL + kind + "/query")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"opIds\":[],\"paths\":[]}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.paths[0].segments[0].startPointOrder").value(3))
          .andExpect(jsonPath("$.paths[0].segments[0].endPointOrder").value(3))
          .andExpect(jsonPath("$.paths[0].segments[0].geometry.type").value("LineString"))
          .andExpect(jsonPath("$.paths[0].segments[0].geometry.coordinates").isArray())
          .andExpect(jsonPath("$.paths[0].segments[0].geometry.coordinates.length()").value(2))
          .andExpect(jsonPath("$.paths[0].segments[0].geometry.coordinates[0][0]").value(126.9))
          .andExpect(jsonPath("$.paths[0].segments[0].geometry.coordinates[0][1]").value(35.1))
          .andExpect(jsonPath("$.paths[0].segments[0].geometry.coordinates[1][0]").value(126.9))
          .andExpect(jsonPath("$.paths[0].segments[0].geometry.coordinates[1][1]").value(35.1));
    }
    org.assertj.core.api.Assertions.assertThat(segment.getCoordinates())
        .containsExactly(List.of(126.9, 35.1));
  }

  @Test
  @DisplayName("차수 목록을 생략하면 현재 차수로 추정하지 않고 거부한다")
  void missing_scope_is_rejected() throws Exception {
    // given: 필수 조회 범위를 생략했다.
    // when & then: 서비스 호출 전에 기존 오류 본문 형태로 거부한다.
    mvc.perform(
            post(URL + "segments/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paths\":[]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_search_path_query"));
    verifyNoInteractions(service);
  }

  @Test
  @DisplayName("음수 순번과 잘못된 버전 형식은 전체 요청 오류로 반환한다")
  void invalid_progress_format_is_rejected() throws Exception {
    // given: 버전 문자열과 순번이 모두 유효하지 않다.
    // when & then: 부분 조회나 입력 자동 보정을 하지 않는다.
    mvc.perform(
            post(URL + "changes/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
        {"opIds":[],"paths":[{"id":"ffffffff-ffff-ffff-ffff-ffffffffffff","baselineVersion":"1e3",
        "changesProgress":{"appliedVersion":null,"targetVersion":"3","beforeStartPointOrder":-1,"completed":false}}]}
        """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("invalid_search_path_query"));
    verifyNoInteractions(service);
  }

  @Test
  @WithMockAccount(channel = Channel.APP)
  @DisplayName("앱 채널은 웹 상황판 전용 경로 조회를 사용할 수 없다")
  void app_channel_is_rejected() throws Exception {
    // given: 앱 계정으로 웹 전용 API를 호출한다.
    // when & then: 서비스와 감사 기록을 실행하지 않는다.
    mvc.perform(
            post(URL + "segments/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"opIds\":[],\"paths\":[]}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("channel_not_allowed"));
    verifyNoInteractions(service, locationAccessRecorder);
  }

  @Test
  @DisplayName("버전 숫자를 문자열로 자동 변환하거나 소수 순번을 반올림하지 않는다")
  void invalid_json_scalar_types_are_not_coerced() throws Exception {
    // given: 형식이 잘못된 버전과 순번을 각각 요청한다.
    List<String> bodies =
        List.of(
            """
        {"opIds":[],"paths":[{"id":"ffffffff-ffff-ffff-ffff-ffffffffffff","baselineVersion":3,"changesProgress":null}]}
        """,
            """
        {"opIds":[],"paths":[{"id":"ffffffff-ffff-ffff-ffff-ffffffffffff","baselineVersion":"3",
        "changesProgress":{"appliedVersion":null,"targetVersion":"4","beforeStartPointOrder":1.5,"completed":false}}]}
        """);
    // when & then: 서버가 값을 추측해서 바꾸지 않는다.
    for (String body : bodies) {
      mvc.perform(post(URL + "changes/query").contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.error").value("invalid_search_path_query"));
    }
    verifyNoInteractions(service);
  }
}
