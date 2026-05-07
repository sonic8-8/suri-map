import { render, screen, within } from '@testing-library/react';
import { describe, expect, test, vi } from 'vitest';
import { createElement, type ComponentType, type ReactNode } from 'react';
import {
  s3_2BoardSlotRegistryContract,
  type BoardSlotName,
  type BoardSlotRegistryEntry,
} from './boardSlotRegistryContract';

const section9_2Registry = [
  {
    slot: 'overall_search_area',
    featureOwner: 'S2',
    mountedBy: 'S3-2',
    sourceContract: 'SearchAreaQuery.overallOf',
    purpose: '지도 기준 범위 표시',
  },
  {
    slot: 'area',
    featureOwner: 'S2',
    mountedBy: 'S3-2',
    sourceContract: 'AreaQuery.byIncident, AreaQuery.byOp',
    purpose: '구역 폴리곤·상태 표시',
  },
  {
    slot: 'path',
    featureOwner: 'S3-1',
    mountedBy: 'S3-2',
    sourceContract: 'PathQuery.byIncident, PathQuery.byOp',
    purpose: 'PolicePhone 경로·구간 표시',
  },
  {
    slot: 'police_phone_freshness',
    featureOwner: 'S1-2',
    mountedBy: 'S3-2',
    sourceContract: 'PolicePhoneFreshnessQuery.byIncident',
    purpose: '위치 점 최신성 표시',
  },
  {
    slot: 'marker',
    featureOwner: 'S5',
    mountedBy: 'S3-2',
    sourceContract: 'MarkerQuery.byIncident',
    purpose: '마커 레이어',
  },
  {
    slot: 'toast',
    featureOwner: 'S5',
    mountedBy: 'S3-2',
    sourceContract: 'SUPPORT_REQUEST_CREATED, PERSON_FOUND',
    purpose: '지원 요청·발견 알림',
  },
  {
    slot: 'package_badge',
    featureOwner: 'S7',
    mountedBy: 'S3-2',
    sourceContract: 'OfflinePackageInstallationQuery.byIncident',
    purpose: '오프라인 패키지 상태',
  },
  {
    slot: 'op_toggle',
    featureOwner: 'S8',
    mountedBy: 'S3-2',
    sourceContract: 'OperationalPeriodQuery.list',
    purpose: 'OP 레이어 토글',
  },
  {
    slot: 'op_history',
    featureOwner: 'S8',
    mountedBy: 'S3-2',
    sourceContract: 'OperationalPeriodQuery.list, OP_TRANSITIONED, OP_ASSIGNMENT_CHANGED',
    purpose: 'OP 전환·배정 이력 표시',
  },
  {
    slot: 'handover_memo',
    featureOwner: 'S8',
    mountedBy: 'S3-2',
    sourceContract: 'HandoverMemoQuery.byContext',
    purpose: '인수인계 메모 표시',
  },
  {
    slot: 'handover_status',
    featureOwner: 'S8',
    mountedBy: 'S3-2',
    sourceContract: 'HandoverStatusSnapshot.byIncident, HANDOVER_MEMO_CREATED, OP_TRANSITIONED',
    purpose: '인수인계 준비·완료 상태 표시',
  },
  {
    slot: 'search_history_summary',
    featureOwner: 'S8',
    mountedBy: 'S3-2',
    sourceContract: 'SearchHistorySummaryQuery.byOp',
    purpose: 'AI 수색 이력 요약',
  },
  {
    slot: 'incident_terminal',
    featureOwner: 'S1-1 terminal close, S1-3 sanitized tombstone/delete summary',
    mountedBy: 'S3-2',
    sourceContract: 'INCIDENT_CLOSED, IncidentTerminalSnapshot.closed, IncidentTombstoneSnapshot.byIncident',
    purpose: '사건 종료·sanitized tombstone read-only summary·사용자 노출 가능한 localPurgeState 표시',
  },
] as const satisfies readonly BoardSlotRegistryEntry[];

const section9_2Slots = section9_2Registry.map((entry) => entry.slot);

const forbiddenInventedSlots = ['missing_area', 'recommended_area', 'auto_blindspot'];

type BoardSlotCursor = {
  readonly id: string;
  readonly status: string;
  readonly version: number;
  readonly sequence: number;
  readonly sourceSpec: string;
  readonly sourceHash: string;
  readonly latestEventId: string;
};

type BoardSlotRow = BoardSlotCursor & {
  readonly slot: BoardSlotName;
  readonly slotSources: readonly string[];
  readonly sourceVersions: Readonly<Record<string, number>>;
  readonly sourceHashes: Readonly<Record<string, string>>;
};

type BoardSlotHostProps = {
  readonly registry: readonly BoardSlotRegistryEntry[];
  readonly rows: readonly BoardSlotRow[];
  readonly renderers: Partial<Record<BoardSlotName, ComponentType<{ readonly row: BoardSlotRow }>>>;
};

describe('S3-2 board slot registry contract', () => {
  test('contains exactly the spec/boundaries.md section 9.2 slots in order', () => {
    expect(s3_2BoardSlotRegistryContract).toEqual(section9_2Registry);
    expect(s3_2BoardSlotRegistryContract.map((entry) => entry.slot)).toEqual(section9_2Slots);
    expect(s3_2BoardSlotRegistryContract).toHaveLength(13);

    for (const entry of s3_2BoardSlotRegistryContract) {
      expect(entry.mountedBy).toBe('S3-2');
      expect(entry.featureOwner).not.toHaveLength(0);
      expect(entry.sourceContract).not.toHaveLength(0);
      expect(entry.purpose).not.toHaveLength(0);
    }
  });

  test('forbids invented slots outside section 9.2', () => {
    const registrySlots = new Set<string>(s3_2BoardSlotRegistryContract.map((entry) => entry.slot));

    for (const inventedSlot of forbiddenInventedSlots) {
      expect(registrySlots.has(inventedSlot)).toBe(false);
    }
  });

  test('situation_board_mounts_registered_slots_only', async () => {
    const BoardSlotHost = await loadFutureBoardSlotHost();
    const rows = createRowsFor(section9_2Slots);
    const renderers = createRenderers();

    render(createElement(BoardSlotHost, { registry: s3_2BoardSlotRegistryContract, rows, renderers }));

    const mountedSlots = screen.getAllByTestId('board-slot').map((node) => node.getAttribute('data-slot'));
    expect(mountedSlots).toEqual(section9_2Slots);

    for (const row of rows) {
      const slot = screen.getByTestId(`slot-${row.slot}`);
      expect(within(slot).getByText(row.id)).toBeInTheDocument();
      expect(within(slot).getByText(row.status)).toBeInTheDocument();
      expect(within(slot).getByText(String(row.version))).toBeInTheDocument();
      expect(within(slot).getByText(String(row.sequence))).toBeInTheDocument();
      expect(within(slot).getByText(row.sourceSpec)).toBeInTheDocument();
      expect(within(slot).getByText(row.sourceHash)).toBeInTheDocument();
      expect(within(slot).getByText(row.latestEventId)).toBeInTheDocument();
      expect(within(slot).getByText(row.slotSources.join(','))).toBeInTheDocument();
      expect(within(slot).getByText(JSON.stringify(row.sourceVersions))).toBeInTheDocument();
      expect(within(slot).getByText(JSON.stringify(row.sourceHashes))).toBeInTheDocument();
    }

    const rogueRows = [
      ...rows,
      createBoardSlotRow('rogue_unknown_slot', section9_2Slots.length + 1),
    ] as readonly BoardSlotRow[];
    expect(() =>
      render(createElement(BoardSlotHost, { registry: s3_2BoardSlotRegistryContract, rows: rogueRows, renderers })),
    ).toThrow(/rogue_unknown_slot/);

    const rogueRegistry = [
      ...s3_2BoardSlotRegistryContract,
      {
        slot: 'rogue_unknown_slot',
        featureOwner: 'rogue',
        mountedBy: 'S3-2',
        sourceContract: 'rogue',
        purpose: 'rogue',
      },
    ] as const satisfies readonly BoardSlotRegistryEntry[];
    expect(() => render(createElement(BoardSlotHost, { registry: rogueRegistry, rows, renderers }))).toThrow(
      /spec\/boundaries\.md §9\.2/,
    );
  });

  test('slot_scoped_renderer_failure_is_contained', async () => {
    const BoardSlotHost = await loadFutureBoardSlotHost();
    const rows = createRowsFor(section9_2Slots);
    const renderers = {
      ...createRenderers(),
      marker: function ThrowingMarkerRenderer(): ReactNode {
        throw new Error('marker renderer failed');
      },
    };

    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);

    try {
      render(createElement(BoardSlotHost, { registry: s3_2BoardSlotRegistryContract, rows, renderers }));

      expect(screen.getByTestId('slot-failure-marker')).toHaveTextContent('marker renderer failed');
      expect(screen.getByTestId('slot-overall_search_area')).toBeInTheDocument();
      expect(screen.getByTestId('slot-path')).toBeInTheDocument();
      expect(screen.getByTestId('slot-incident_terminal')).toBeInTheDocument();
    } finally {
      consoleError.mockRestore();
    }
  });
});

function createRowsFor(slots: readonly string[]): readonly BoardSlotRow[] {
  return slots.map((slot, index) => createBoardSlotRow(slot, index + 1));
}

function createBoardSlotRow(slot: string, index: number): BoardSlotRow {
  return {
    slot: slot as BoardSlotName,
    id: `board-row-${slot}`,
    status: 'ACTIVE',
    version: 100 + index,
    sequence: 900 + index,
    sourceSpec: `owner-spec-${slot}`,
    sourceHash: `hash-${slot}`,
    latestEventId: `evt-${slot}`,
    slotSources: [`slot-source-${slot}`],
    sourceVersions: { [`slot-source-${slot}`]: 100 + index },
    sourceHashes: { [`slot-source-${slot}`]: `hash-${slot}` },
  };
}

function createRenderers(): Record<BoardSlotName, ComponentType<{ readonly row: BoardSlotRow }>> {
  return Object.fromEntries(
    section9_2Slots.map((slot) => [
      slot,
      function BoardSlotRenderer({ row }: { readonly row: BoardSlotRow }) {
        return (
          <section data-testid={`slot-${slot}`}>
            <span>{row.id}</span>
            <span>{row.status}</span>
            <span>{row.version}</span>
            <span>{row.sequence}</span>
            <span>{row.sourceSpec}</span>
            <span>{row.sourceHash}</span>
            <span>{row.latestEventId}</span>
            <span>{row.slotSources.join(',')}</span>
            <span>{JSON.stringify(row.sourceVersions)}</span>
            <span>{JSON.stringify(row.sourceHashes)}</span>
          </section>
        );
      },
    ]),
  ) as Record<BoardSlotName, ComponentType<{ readonly row: BoardSlotRow }>>;
}

async function loadFutureBoardSlotHost(): Promise<ComponentType<BoardSlotHostProps>> {
  const futureModulePath = '../components/BoardSlotHost';
  const loadedModule = (await import(futureModulePath)) as unknown;

  if (!isBoardSlotHostModule(loadedModule)) {
    throw new Error('Future BoardSlotHost module must export BoardSlotHost');
  }

  return loadedModule.BoardSlotHost;
}

function isBoardSlotHostModule(value: unknown): value is { readonly BoardSlotHost: ComponentType<BoardSlotHostProps> } {
  return typeof value === 'object' && value !== null && 'BoardSlotHost' in value;
}
