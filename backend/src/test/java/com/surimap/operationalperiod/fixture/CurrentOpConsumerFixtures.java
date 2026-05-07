package com.surimap.operationalperiod.fixture;

import java.util.List;
import java.util.UUID;

/** S8 current OP consumer contract fixture. */
public final class CurrentOpConsumerFixtures {

  /** current OP를 소비하는 Lane fixture 이름. */
  public static final List<String> CURRENT_OP_CONSUMERS =
      List.of(
          "S7 offline manifest builder",
          "S3-1 search session/path write",
          "S5 marker/support/person write");

  /** S8 current OP mock이 직접 만들면 안 되는 다른 Lane 소유 row 목록. */
  public static final List<String> FORBIDDEN_S8_OWNER_WRITES =
      List.of(
          "offline_package_manifest",
          "offline_package_status",
          "search_session",
          "search_path",
          "path_segment",
          "marker",
          "photo",
          "notification_delivery");

  private CurrentOpConsumerFixtures() {}

  /** S7/S3-1/S5가 같은 current OP fixture를 소비하는지 비교하기 위한 probe. */
  public static List<CurrentOpConsumerProbe> currentOpConsumerProbes() {
    return CURRENT_OP_CONSUMERS.stream()
        .map(
            consumer ->
                new CurrentOpConsumerProbe(
                    consumer,
                    OperationalPeriodFixtures.CURRENT_OP_ID,
                    OperationalPeriodFixtures.CURRENT_OP_SEQUENCE_NO,
                    OperationalPeriodFixtures.CURRENT_OP_VERSION))
        .toList();
  }

  /** current OP 소비자별 fixture 비교 모델. */
  public record CurrentOpConsumerProbe(String consumer, UUID opId, int sequenceNo, long version) {}
}
