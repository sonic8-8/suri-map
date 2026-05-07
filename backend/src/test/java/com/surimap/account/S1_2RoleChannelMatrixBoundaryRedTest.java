package com.surimap.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.account.fixture.RoleChannelMatrixFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("L2-T01 S1-2 role/channel matrix boundary RED")
class S1_2RoleChannelMatrixBoundaryRedTest {

  @Test
  @DisplayName("role/channel matrix fixture mirrors every boundaries.md section 4.6 row")
  void role_channel_matrix_fixture_mirrors_every_boundaries_section_4_6_row() {
    assertThat(RoleChannelMatrixFixtures.rules()).hasSize(14);
    assertThat(RoleChannelMatrixFixtures.rule("지원 부대 배정").allowedChannels()).containsExactly("WEB");
    assertThat(RoleChannelMatrixFixtures.rule("지원 부대 배정").requiredRoleText()).isEqualTo("실종팀 지휘");
  }
}
