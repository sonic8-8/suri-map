package com.surimap.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.app.service.path.AppSearchPathCommandService;
import com.surimap.app.service.searcharea.AppSearchAreaBoundaryAlertService;
import com.surimap.common.auth.guard.PolicePhoneValidationPort;
import com.surimap.marker.adapter.MarkerRuntimeGuardMapper;
import com.surimap.marker.adapter.RuntimeMarkerWriteGuardAdapter;
import com.surimap.marker.photo.adapter.RuntimePhotoWriteGuardAdapter;
import com.surimap.api.service.path.SearchPathService;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PolicePhoneBoundaryArchitectureTest {

  @Test
  @DisplayName("SearchPathService는 업무폰 등록 여부를 직접 검증하지 않는다")
  void searchPathServiceDoesNotDependOnPolicePhoneValidationPort() {
    assertThat(Arrays.stream(SearchPathService.class.getDeclaredFields()).map(Field::getType))
        .doesNotContain(PolicePhoneValidationPort.class);
    assertThat(
            Arrays.stream(AppSearchPathCommandService.class.getDeclaredFields())
                .map(field -> field.getType().getSimpleName()))
        .doesNotContain("PolicePhoneGuard");
    assertThat(
            Arrays.stream(AppSearchAreaBoundaryAlertService.class.getDeclaredFields())
                .map(Field::getType))
        .doesNotContain(PolicePhoneValidationPort.class);
  }

  @Test
  @DisplayName("마커 비즈니스 guard는 업무폰 등록 조회 계약을 갖지 않는다")
  void markerBusinessGuardDoesNotOwnRegisteredPhoneLookup() {
    assertThat(
            Arrays.stream(MarkerRuntimeGuardMapper.class.getDeclaredMethods()).map(Method::getName))
        .doesNotContain("countRegisteredPolicePhone");
    assertThat(
            Arrays.stream(RuntimeMarkerWriteGuardAdapter.class.getDeclaredMethods())
                .map(Method::getName))
        .doesNotContain("requireRegisteredPolicePhone");
    assertThat(
            Arrays.stream(RuntimePhotoWriteGuardAdapter.class.getDeclaredMethods())
                .map(Method::getName))
        .doesNotContain("requireRegisteredPolicePhone");
  }

  @Test
  @DisplayName("수색 경로 조회 mapper는 활성 근무교대로 조회 범위를 제한하지 않는다")
  void searchPathReadQueriesDoNotRequireActiveDutyShift() throws Exception {
    String mapper =
        Files.readString(Path.of("src/main/resources/mapper/path/SearchPathMapper.xml"));

    assertThat(selectBlock(mapper, "findPathById")).doesNotContain("ds.status = 'ACTIVE'");
    assertThat(selectBlock(mapper, "findAllPaths")).doesNotContain("ds.status = 'ACTIVE'");
    assertThat(selectBlock(mapper, "findPaths")).doesNotContain("ds.status = 'ACTIVE'");
  }

  private String selectBlock(String mapper, String id) {
    String startToken = "<select id=\"" + id + "\"";
    int start = mapper.indexOf(startToken);
    assertThat(start).as("select id=%s exists", id).isNotNegative();
    int end = mapper.indexOf("</select>", start);
    assertThat(end).as("select id=%s closes", id).isGreaterThan(start);
    return mapper.substring(start, end);
  }
}
