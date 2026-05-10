package com.surimap.policephone;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("L2-T04 FcmTokenQuery.activeByPolicePhone RED")
class FcmTokenQueryRedTest {

  @Test
  @DisplayName("FcmTokenQuery.activeByPolicePhone 포트는 policePhoneId를 입력으로 받는다")
  void fcm_token_query_active_by_police_phone_port_exists() throws Exception {
    Class<?> query = Class.forName("com.surimap.policephone.query.FcmTokenQuery");

    Method activeByPolicePhone = query.getMethod("activeByPolicePhone", UUID.class);

    assertThat(activeByPolicePhone.getReturnType()).isEqualTo(java.util.List.class);
  }

  @Test
  @DisplayName("FcmToken row shape는 active token 참조 canonical fields를 노출한다")
  void fcm_token_row_shape_exposes_canonical_fields() throws Exception {
    Class<?> row = Class.forName("com.surimap.policephone.query.FcmTokenRow");

    assertThat(row.getRecordComponents())
        .extracting(component -> component.getName())
        .contains(
            "id",
            "policePhoneId",
            "appInstanceId",
            "tokenCiphertext",
            "tokenHash",
            "status",
            "version");
  }
}
