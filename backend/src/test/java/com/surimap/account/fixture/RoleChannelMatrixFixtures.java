package com.surimap.account.fixture;

import java.util.List;

/** S1-2 role/channel matrix fixture from docs/spec/boundaries.md §4.6. */
public final class RoleChannelMatrixFixtures {

  private static final List<RoleChannelRule> RULES =
      List.of(
          rule("사건 가져오기", "WEB", "실종팀 지휘, 지구대/파출소 지휘"),
          rule("사건 종료", "WEB", "실종팀 지휘"),
          rule("지원 부대 배정", "WEB", "실종팀 지휘"),
          rule("지도 기준 범위 조정", "WEB", "현장 지휘관"),
          rule("구역 분할·할당", "WEB", "현장 지휘관"),
          rule("구역 완료", "WEB", "현장 지휘관"),
          rule("OP 생성", "WEB", "현장 지휘관"),
          rule("수색 세션", "APP", "사건 배정 계정"),
          rule("경로 batch", "APP", "사건 배정 계정 + PolicePhone"),
          rule("현장 마커 생성", "APP", "사건 배정 계정"),
          rule("마커 수정·삭제", "APP", "WEB", "사건 배정 계정, 세부 정책은 S5"),
          rule("인수인계 메모", "APP", "WEB", "사건 배정 계정"),
          rule("AI 요약 생성", "WEB", "현장 지휘관"),
          rule("상황판 조회", "WEB", "사건 배정 계정"));

  private RoleChannelMatrixFixtures() {}

  public static List<RoleChannelRule> rules() {
    return RULES;
  }

  public static RoleChannelRule rule(String feature) {
    return RULES.stream()
        .filter(rule -> rule.feature().equals(feature))
        .findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Unknown role/channel feature: " + feature));
  }

  private static RoleChannelRule rule(String feature, String channel, String requiredRoleText) {
    return new RoleChannelRule(feature, List.of(channel), requiredRoleText);
  }

  private static RoleChannelRule rule(
      String feature, String firstChannel, String secondChannel, String requiredRoleText) {
    return new RoleChannelRule(feature, List.of(firstChannel, secondChannel), requiredRoleText);
  }

  public record RoleChannelRule(
      String feature, List<String> allowedChannels, String requiredRoleText) {}
}
