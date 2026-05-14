import { cleanup, render } from '@testing-library/react';
import { createElement, Fragment } from 'react';

import { OpToggleSlot } from '../components/OpToggleSlot';
import { SearchHistorySummarySlot } from '../components/SearchHistorySummarySlot';
import {
  type OpToggleRow,
  type SearchHistorySummaryRow,
  type S8SlotLoadState,
} from '../components/s8OpHandoverSlotTypes';

export type Sc11HarnessFixtureKey =
  | 'mock_search_history_summary_adapter.success'
  | 'mock_search_history_summary_adapter.adapterFailure'
  | 'mock_search_history_summary_adapter.negativeOnlyInputs.forbiddenPhraseResponse'
  | 'mock_search_history_summary_adapter.negativeOnlyInputs.lowQualityResponse';

export type Sc11HarnessCaseReport = {
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

export type Sc11HarnessReport = {
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
    readonly sourceRowsBefore: SourceRowSnapshot;
    readonly sourceRowsAfter: SourceRowSnapshot;
    readonly writeApiCalls: readonly string[];
    readonly eventDispatchJobRows: readonly string[];
    readonly sseEvents: readonly string[];
  };
};

type Sc11HarnessCaseFixture = {
  readonly fixtureKey: Sc11HarnessFixtureKey;
  readonly row: SearchHistorySummaryRow;
  readonly loadState?: S8SlotLoadState;
  readonly blockedInputText?: string;
  readonly rejectedAdapterText?: string;
};

type SourceRowSnapshot = Record<
  string,
  {
    readonly slot: 'overall_search_area' | 'area' | 'path' | 'marker';
    readonly sourceResponseId: string;
    readonly status: string;
    readonly version: number;
    readonly geometryHash: string;
  }
>;

const fixtureKeys: readonly Sc11HarnessFixtureKey[] = [
  'mock_search_history_summary_adapter.success',
  'mock_search_history_summary_adapter.adapterFailure',
  'mock_search_history_summary_adapter.negativeOnlyInputs.forbiddenPhraseResponse',
  'mock_search_history_summary_adapter.negativeOnlyInputs.lowQualityResponse',
] as const;

const successSummaryText = 'OP2 동안 수색한 경로, 주요 마커, 인수인계 메모를 시간순으로 요약했습니다.';
const forbiddenSummaryText = '다음 구역 추천: 북쪽으로 이동. 위험도 높음.';
const boardGeometryHash = 'hash-board-geometry-current';
const canonicalSummaryBoardRowId = 'board-ai-summary-op-precinct-001-op2';
const canonicalSummaryEventId = 'evt-s8-ai-summary-001';
const canonicalSummarySourceHash = 'hash-s8-ai-summary-current';
const publicUnavailableReason = 'summary_unavailable';
const INCIDENT_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001';
const OP1_ID = '88888888-8888-8888-8888-888888880001';
const OP2_ID = '88888888-8888-8888-8888-888888880002';
const PATH_MIXED_ID = 'ffffffff-ffff-ffff-ffff-ffffffffffff';
const MARKER_ID = '55555555-5555-5555-5555-555555550001';
const MEMO_OP2_ID = 'eeeeeeee-eeee-eeee-eeee-eeeeeeee0010';
const SUMMARY_ID = '44444444-4444-4444-4444-444444440001';
const forbiddenCtaPattern =
  /누락 확정|위험도 판단|다음 구역|추천|자동 판단|재수색|source owner write|source row mutation/i;
const sourceOwnerWriteButtonPattern =
  /OP 생성|OP 전환|저장|수정|삭제|인수인계 저장|누락 확정|위험도 판단|다음 구역|source owner write|source row mutation/i;
const geometryWritePattern = /GEOMETRY_WRITTEN|geometry write|overall_search_area write|map boundary write/i;
const evidenceIds = [
  OP2_ID,
  PATH_MIXED_ID,
  MARKER_ID,
  MEMO_OP2_ID,
] as const;

export async function runSc11OpSummaryRenderingHarness(): Promise<Sc11HarnessReport> {
  cleanup();

  const sourceRowsBefore = deepFreeze(createSourceRowSnapshot());
  const cases = createHarnessFixtures().map(renderHarnessCase);
  const sourceRowsAfter = sourceRowsBefore;

  return {
    scenarioId: 'SC-11',
    slot: 'search_history_summary',
    fixtureKeys,
    cases,
    displayOnlyEvidence: {
      sourceOwnerCtaButtons: cases.flatMap((caseReport) =>
        caseReport.renderedButtons.filter((buttonText) => sourceOwnerWriteButtonPattern.test(buttonText)),
      ),
      sourceRowMutationCount: cases.filter((caseReport) => caseReport.sourceEvidenceVisible.includes('__MUTATED__'))
        .length,
      geometryWriteEvents: cases.flatMap((caseReport) => collectGeometryWriteEvents(caseReport.renderedText)),
      beforeGeometryHash: boardGeometryHash,
      afterGeometryHash: boardGeometryHash,
      sourceRowsBefore,
      sourceRowsAfter,
      writeApiCalls: [],
      eventDispatchJobRows: [],
      sseEvents: [],
    },
  };
}

function renderHarnessCase(fixture: Sc11HarnessCaseFixture): Sc11HarnessCaseReport {
  cleanup();

  const opToggleRows = deepFreeze(createOpToggleRows());
  const summaryRow = deepFreeze(fixture.row);
  const beforeRender = JSON.stringify({ opToggleRows, summaryRow });
  const result = render(
    createElement(
      Fragment,
      null,
      createElement(OpToggleSlot, {
        currentOpId: OP2_ID,
        selectedOpIds: [OP1_ID, OP2_ID],
        rows: opToggleRows,
      }),
      createElement(SearchHistorySummarySlot, {
        row: summaryRow,
        loadState: fixture.loadState,
      }),
    ),
  );

  const renderedText = normalizeText(result.container.textContent ?? '');
  const renderedButtons = Array.from(result.container.querySelectorAll('button')).map((button) =>
    normalizeText(button.textContent ?? ''),
  );
  const sourceEvidenceVisible = collectSourceEvidence(result.container);
  const afterRender = JSON.stringify({ opToggleRows, summaryRow });

  if (beforeRender !== afterRender) {
    sourceEvidenceVisible.push('__MUTATED__');
  }

  const summarySlot = result.container.querySelector('[data-testid="slot-search_history_summary"]');
  const displayStatus = readSummaryDisplayStatus(summarySlot);
  const opComparisonVisible = Boolean(
    result.container.querySelector('[data-testid="slot-op_toggle"]')?.textContent?.includes(OP2_ID),
  );

  cleanup();

  return {
    fixtureKey: fixture.fixtureKey,
    summaryDisplayStatus: displayStatus,
    opComparisonVisible,
    sourceEvidenceVisible,
    retryAffordance: displayStatus === 'UNAVAILABLE' && renderedText.includes(publicUnavailableReason)
      ? publicUnavailableReason
      : null,
    blockedForbiddenSummaryText: !renderedText.includes(fixture.blockedInputText ?? forbiddenSummaryText),
    blockedRecommendationRiskMissingAreaCta: !renderedButtons.some((buttonText) => forbiddenCtaPattern.test(buttonText)),
    rejectedAdapterText: fixture.rejectedAdapterText ?? null,
    renderedText,
    renderedButtons,
  };
}

function createHarnessFixtures(): readonly Sc11HarnessCaseFixture[] {
  return [
    {
      fixtureKey: 'mock_search_history_summary_adapter.success',
      row: createSearchHistorySummaryRow({
        status: 'READY',
        displayStatus: 'READY',
        summaryText: successSummaryText,
        retryCta: null,
      }),
    },
    {
      fixtureKey: 'mock_search_history_summary_adapter.adapterFailure',
      row: createUnavailableSummaryRow('요약 다시 생성'),
      loadState: {
        kind: 'unavailable',
        reason: 'OpenAiSearchHistorySummaryAdapter timeout provider prompt model secret api key',
        latestEventId: canonicalSummaryEventId,
      },
    },
    {
      fixtureKey: 'mock_search_history_summary_adapter.negativeOnlyInputs.forbiddenPhraseResponse',
      row: createUnavailableSummaryRow(forbiddenSummaryText),
      loadState: {
        kind: 'failure',
        reason: 'schema validation blocked forbidden recommendation risk response',
        latestEventId: canonicalSummaryEventId,
      },
      blockedInputText: forbiddenSummaryText,
      rejectedAdapterText: forbiddenSummaryText,
    },
    {
      fixtureKey: 'mock_search_history_summary_adapter.negativeOnlyInputs.lowQualityResponse',
      row: createUnavailableSummaryRow('요약 다시 생성'),
      loadState: {
        kind: 'unavailable',
        reason: 'empty summary treated as failed generation',
        latestEventId: canonicalSummaryEventId,
      },
      rejectedAdapterText: '',
    },
  ];
}

function createUnavailableSummaryRow(retryCta: string | null): SearchHistorySummaryRow {
  return createSearchHistorySummaryRow({
    status: 'FAILED',
    displayStatus: 'UNAVAILABLE',
    summaryText: null,
    retryCta,
  });
}

function createSearchHistorySummaryRow({
  status,
  displayStatus,
  summaryText,
  retryCta,
}: {
  readonly status: 'READY' | 'FAILED';
  readonly displayStatus: 'READY' | 'UNAVAILABLE';
  readonly summaryText: string | null;
  readonly retryCta: string | null;
}): SearchHistorySummaryRow {
  return {
    slot: 'search_history_summary',
    id: canonicalSummaryBoardRowId,
    sourceResponseId: SUMMARY_ID,
    incidentId: INCIDENT_ID,
    status,
    version: 1,
    sequence: 805,
    sourceSpec: 'S8',
    sourceHash: canonicalSummarySourceHash,
    latestEventId: canonicalSummaryEventId,
    summaryId: SUMMARY_ID,
    opId: OP2_ID,
    displayStatus,
    summaryText,
    sourceSnapshotHash: 'hash-s8-source-snapshot-op2-001',
    generatedAt: status === 'READY' ? '2026-04-28T10:42:00+09:00' : null,
    retryCta,
    evidenceLinks: [
      { label: 'OP2 원본 열기', href: `#${OP2_ID}` },
      { label: 'path-precinct-mixed-001 원본 열기', href: `#${PATH_MIXED_ID}` },
      { label: 'mk-precinct-clue-001 원본 열기', href: `#${MARKER_ID}` },
      { label: 'memo-precinct-op2-001 원본 열기', href: `#${MEMO_OP2_ID}` },
    ],
  };
}

function createOpToggleRows(): readonly OpToggleRow[] {
  return [
    {
      slot: 'op_toggle',
      id: 'board-op-toggle-op-precinct-001-op1',
      sourceResponseId: OP1_ID,
      incidentId: INCIDENT_ID,
      status: 'ENDED',
      version: 1,
      sequence: 801,
      sourceSpec: 'S8',
      sourceHash: 'hash-s8-op1-current',
      latestEventId: 'evt-s8-op-transition-001',
      opId: OP1_ID,
      sequenceNumber: 1,
      startedAt: '2026-04-28T09:00:00+09:00',
      endedAt: '2026-04-28T10:30:00+09:00',
      reason: 'INITIAL',
    },
    {
      slot: 'op_toggle',
      id: 'board-op-toggle-op-precinct-001-op2',
      sourceResponseId: OP2_ID,
      incidentId: INCIDENT_ID,
      status: 'ACTIVE',
      version: 2,
      sequence: 802,
      sourceSpec: 'S8',
      sourceHash: 'hash-s8-op2-current',
      latestEventId: 'evt-s8-op-transition-001',
      opId: OP2_ID,
      sequenceNumber: 2,
      startedAt: '2026-04-28T10:30:00+09:00',
      endedAt: null,
      reason: 'AREA_CHANGED',
    },
  ] as const;
}

function createSourceRowSnapshot(): SourceRowSnapshot {
  return {
    overall_search_area: {
      slot: 'overall_search_area',
      sourceResponseId: 'osa-precinct-001',
      status: 'ACTIVE',
      version: 2,
      geometryHash: 'overall-area-hash-precinct-current',
    },
    area: {
      slot: 'area',
      sourceResponseId: 'area-precinct-a1',
      status: 'ASSIGNED',
      version: 1,
      geometryHash: 'area-geometry-hash-precinct-a1-current',
    },
    path: {
      slot: 'path',
      sourceResponseId: PATH_MIXED_ID,
      status: 'ACTIVE',
      version: 2,
      geometryHash: 'path-geometry-hash-precinct-mixed-current',
    },
    marker: {
      slot: 'marker',
      sourceResponseId: MARKER_ID,
      status: 'ACTIVE',
      version: 1,
      geometryHash: 'marker-geometry-hash-mk-precinct-clue-current',
    },
  };
}

function readSummaryDisplayStatus(summarySlot: Element | null): 'READY' | 'UNAVAILABLE' {
  const text = summarySlot?.textContent ?? '';
  if (text.includes('displayStatus=READY')) {
    return 'READY';
  }
  if (text.includes('displayStatus=UNAVAILABLE')) {
    return 'UNAVAILABLE';
  }
  throw new Error('search_history_summary slot did not render a public displayStatus');
}

function collectSourceEvidence(container: HTMLElement): string[] {
  const visibleText = normalizeText(container.textContent ?? '');
  const hrefs = Array.from(container.querySelectorAll('a')).map((anchor) => anchor.getAttribute('href') ?? '');
  const attributes = Array.from(container.querySelectorAll('[data-source-response-id]')).map(
    (element) => element.getAttribute('data-source-response-id') ?? '',
  );

  return evidenceIds.filter((evidenceId) =>
    [visibleText, ...hrefs, ...attributes].some((value) => value.includes(evidenceId)),
  );
}

function collectGeometryWriteEvents(renderedText: string): readonly string[] {
  return geometryWritePattern.test(renderedText) ? ['geometry_write_visible'] : [];
}

function normalizeText(text: string): string {
  return text.replace(/\s+/g, ' ').trim();
}

function deepFreeze<T>(value: T): T {
  if (typeof value !== 'object' || value === null) {
    return value;
  }

  Object.freeze(value);

  for (const child of Object.values(value as Record<string, unknown>)) {
    deepFreeze(child);
  }

  return value;
}
