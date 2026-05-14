import { describe, expect, test } from 'vitest';

const forbiddenSummaryText = /누락 확정|위험도 높음|위험도 판단|다음 구역|추천|자동 판단|재수색 제안/;
const forbiddenCtaText = /누락 확정|위험도 판단|다음 구역|추천|자동 판단|재수색|source owner write|source row mutation/;
const forbiddenInternalLeakText =
  /OpenAiSearchHistorySummaryAdapter|provider|prompt|model|secret|api[_ -]?key|timeout|schema validation/i;
const OP2_ID = '88888888-8888-8888-8888-888888880002';
const PATH_MIXED_ID = 'ffffffff-ffff-ffff-ffff-ffffffffffff';
const MARKER_ID = '55555555-5555-5555-5555-555555550001';
const MEMO_OP2_ID = 'eeeeeeee-eeee-eeee-eeee-eeeeeeee0010';

describe('L6-T10C SC-11 OP/search_history_summary rendering guard harness RED', () => {
  test('requires a concrete SC-11 harness runner report covering OpenAI success, failure, forbidden, and low-quality fixtures', async () => {
    const { runSc11OpSummaryRenderingHarness } = await loadSc11HarnessRunner();

    const report = await runSc11OpSummaryRenderingHarness();

    expect(report.scenarioId).toBe('SC-11');
    expect(report.slot).toBe('search_history_summary');
    expect(report.fixtureKeys).toEqual(
      expect.arrayContaining([
        'mock_search_history_summary_adapter.success',
        'mock_search_history_summary_adapter.adapterFailure',
        'mock_search_history_summary_adapter.negativeOnlyInputs.forbiddenPhraseResponse',
        'mock_search_history_summary_adapter.negativeOnlyInputs.lowQualityResponse',
      ]),
    );
    expect(report.cases.map((caseReport) => caseReport.fixtureKey)).toEqual([
      'mock_search_history_summary_adapter.success',
      'mock_search_history_summary_adapter.adapterFailure',
      'mock_search_history_summary_adapter.negativeOnlyInputs.forbiddenPhraseResponse',
      'mock_search_history_summary_adapter.negativeOnlyInputs.lowQualityResponse',
    ]);

    const success = caseByKey(report, 'mock_search_history_summary_adapter.success');
    expect(success.summaryDisplayStatus).toBe('READY');
    expect(success.opComparisonVisible).toBe(true);
    expect(success.sourceEvidenceVisible).toEqual(
      expect.arrayContaining([
        OP2_ID,
        PATH_MIXED_ID,
        MARKER_ID,
        MEMO_OP2_ID,
      ]),
    );
    expect(success.renderedText).toContain('OP2 동안 수색한 경로');
    expect(success.renderedText).not.toMatch(forbiddenInternalLeakText);
    expect(success.renderedText).not.toMatch(forbiddenSummaryText);

    for (const fixtureKey of [
      'mock_search_history_summary_adapter.adapterFailure',
      'mock_search_history_summary_adapter.negativeOnlyInputs.forbiddenPhraseResponse',
      'mock_search_history_summary_adapter.negativeOnlyInputs.lowQualityResponse',
    ] as const) {
      const failureCase = caseByKey(report, fixtureKey);
      expect(failureCase.summaryDisplayStatus).toBe('UNAVAILABLE');
      expect(failureCase.opComparisonVisible).toBe(true);
      expect(failureCase.retryAffordance).toBe('summary_unavailable');
      expect(failureCase.renderedText).toContain('summary_unavailable');
      expect(failureCase.renderedText).not.toMatch(forbiddenInternalLeakText);
      expect(failureCase.renderedText).not.toMatch(forbiddenSummaryText);
      expect(failureCase.renderedButtons.join(' ')).not.toMatch(forbiddenCtaText);
    }

    const forbiddenCase = caseByKey(
      report,
      'mock_search_history_summary_adapter.negativeOnlyInputs.forbiddenPhraseResponse',
    );
    expect(forbiddenCase.blockedForbiddenSummaryText).toBe(true);
    expect(forbiddenCase.blockedRecommendationRiskMissingAreaCta).toBe(true);
    expect(forbiddenCase.rejectedAdapterText).toBe('다음 구역 추천: 북쪽으로 이동. 위험도 높음.');
    expect(forbiddenCase.renderedText).not.toContain('다음 구역 추천: 북쪽으로 이동. 위험도 높음.');

    expect(report.displayOnlyEvidence).toMatchObject({
      sourceOwnerCtaButtons: [],
      sourceRowMutationCount: 0,
      geometryWriteEvents: [],
      writeApiCalls: [],
      eventDispatchJobRows: [],
      sseEvents: [],
    });
    expect(report.displayOnlyEvidence.afterGeometryHash).toBe(report.displayOnlyEvidence.beforeGeometryHash);
    expect(report.displayOnlyEvidence.sourceRowsAfter).toEqual(report.displayOnlyEvidence.sourceRowsBefore);
  });
});

type Sc11HarnessFixtureKey =
  | 'mock_search_history_summary_adapter.success'
  | 'mock_search_history_summary_adapter.adapterFailure'
  | 'mock_search_history_summary_adapter.negativeOnlyInputs.forbiddenPhraseResponse'
  | 'mock_search_history_summary_adapter.negativeOnlyInputs.lowQualityResponse';

type Sc11HarnessCaseReport = {
  readonly fixtureKey: Sc11HarnessFixtureKey;
  readonly summaryDisplayStatus: 'READY' | 'UNAVAILABLE';
  readonly opComparisonVisible: boolean;
  readonly sourceEvidenceVisible: readonly string[];
  readonly retryAffordance: 'summary_unavailable' | null;
  readonly blockedForbiddenSummaryText: boolean;
  readonly blockedRecommendationRiskMissingAreaCta: boolean;
  readonly rejectedAdapterText: string | null;
  readonly renderedText: string;
  readonly renderedButtons: readonly string[];
};

type Sc11HarnessReport = {
  readonly scenarioId: 'SC-11';
  readonly slot: 'search_history_summary';
  readonly fixtureKeys: readonly Sc11HarnessFixtureKey[];
  readonly cases: readonly Sc11HarnessCaseReport[];
  readonly displayOnlyEvidence: {
    readonly sourceOwnerCtaButtons: readonly string[];
    readonly sourceRowMutationCount: number;
    readonly geometryWriteEvents: readonly string[];
    readonly beforeGeometryHash: string;
    readonly afterGeometryHash: string;
    readonly sourceRowsBefore: Record<string, unknown>;
    readonly sourceRowsAfter: Record<string, unknown>;
    readonly writeApiCalls: readonly string[];
    readonly eventDispatchJobRows: readonly string[];
    readonly sseEvents: readonly string[];
  };
};

type Sc11HarnessRunnerModule = {
  readonly runSc11OpSummaryRenderingHarness: () => Promise<Sc11HarnessReport>;
};

async function loadSc11HarnessRunner(): Promise<Sc11HarnessRunnerModule> {
  const modulePath = '../test/sc11OpSearchHistorySummaryHarnessRunner';
  const imported = (await import(/* @vite-ignore */ modulePath)) as Partial<Sc11HarnessRunnerModule>;

  if (typeof imported.runSc11OpSummaryRenderingHarness !== 'function') {
    throw new Error('Missing runSc11OpSummaryRenderingHarness test-support runner for L6-T10C SC-11 RED');
  }

  return imported as Sc11HarnessRunnerModule;
}

function caseByKey(report: Sc11HarnessReport, fixtureKey: Sc11HarnessFixtureKey): Sc11HarnessCaseReport {
  const matchingCase = report.cases.find((caseReport) => caseReport.fixtureKey === fixtureKey);

  if (!matchingCase) {
    throw new Error(`Missing SC-11 harness case report for ${fixtureKey}`);
  }

  return matchingCase;
}
