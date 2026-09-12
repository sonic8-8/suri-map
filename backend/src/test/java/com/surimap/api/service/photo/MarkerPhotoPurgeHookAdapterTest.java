package com.surimap.api.service.photo;

import static com.surimap.marker.domain.fixture.MarkerGeometryFixtures.INCIDENT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.retention.purge.PurgeHookName;
import com.surimap.retention.purge.PurgeHookRequest;
import com.surimap.retention.purge.PurgeHookResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MarkerPhotoPurgeHookAdapterTest {

  @Test
  @DisplayName("같은 사진 파기 요청을 반복하면, 요청 필드를 그대로 전달하고 훅의 결과를 반환한다")
  void purge_repeatedRequest_preservesFieldsAndDelegatedResult() {
    // given: 실제 삭제가 아닌 파기 요청의 전달·결과 변환을 확인한다.
    PurgeHookRequest request =
        new PurgeHookRequest(
            INCIDENT_ID,
            UUID.fromString("33333333-3333-3333-3333-333333331148"),
            Instant.parse("2026-05-08T01:05:00Z"),
            Instant.parse("2026-05-09T01:05:00Z"));
    List<PurgeHookRequest> receivedRequests = new ArrayList<>();
    PurgeHookResult hookResult = PurgeHookResult.succeeded(2, 0);
    MarkerPhotoPurgeHookAdapter adapter =
        new MarkerPhotoPurgeHookAdapter(
            (incidentId, purgeRunId, closedAt, purgeDeadlineTs) -> {
              receivedRequests.add(
                  new PurgeHookRequest(incidentId, purgeRunId, closedAt, purgeDeadlineTs));
              return hookResult;
            });

    // when: 같은 요청을 두 번 전달한다.
    PurgeHookResult first = adapter.purge(request);
    PurgeHookResult repeated = adapter.purge(request);

    // then: 훅 이름·요청 필드·반환 결과를 바꾸지 않는다.
    assertThat(adapter.name()).isEqualTo(PurgeHookName.MARKER_PHOTO);
    assertThat(receivedRequests).containsExactly(request, request);
    assertThat(first).isEqualTo(hookResult);
    assertThat(repeated).isEqualTo(hookResult);
  }
}
