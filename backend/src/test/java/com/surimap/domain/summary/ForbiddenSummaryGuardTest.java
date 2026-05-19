package com.surimap.domain.summary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ForbiddenSummaryGuardTest {

  @Test
  void containsForbiddenPhraseKeepsSpecPhrasesVerbatim() {
    assertThat(ForbiddenSummaryGuard.FORBIDDEN_PHRASES)
        .containsExactlyInAnyOrder("다음 구역 추천", "누락 확정", "위험도 높음", "자동 판단");
  }

  @Test
  void containsForbiddenPhraseAlsoBlocksSharedAiJudgmentTerms() {
    ForbiddenSummaryGuard guard = new ForbiddenSummaryGuard();

    assertThat(guard.containsForbiddenPhrase("이 경로는 위험 가능성 높음.")).isTrue();
    assertThat(guard.containsForbiddenPhrase("다음 차수 전략을 추천합니다.")).isTrue();
    assertThat(guard.containsForbiddenPhrase("해당 판단은 수색 효율이 더 나음.")).isTrue();
  }
}
