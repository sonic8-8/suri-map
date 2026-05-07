import { render, screen, within } from '@testing-library/react';
import { describe, expect, test } from 'vitest';
import { BoardSlotState } from './BoardSlotState';
import { PolicePhoneFreshnessSlot, type PolicePhoneFreshnessSlotProps } from './PolicePhoneFreshnessSlot';

const forbiddenWriteButtonName =
  /저장|수정|보정|삭제|생성|시작|종료|지원 요청|사진|업로드|재조회|동기화|명령|전송|확정/;

describe('L6-T03C S3-2 police_phone_freshness board slot', () => {
  test('renders S3-2 freshness row variants without mutating source-owner data', () => {
    const rows = deepFreeze([createFreshnessRow()]);
    const beforeRender = JSON.stringify(rows);
    expectNoSourceId(rows);

    const { rerender } = render(<PolicePhoneFreshnessSlot nowIso="2026-04-28T10:30:00+09:00" rows={rows} />);

    const slot = screen.getByTestId('slot-police-phone-freshness');
    expectCanonicalRowIdentity(slot, 'normal');
    expect(within(slot).getByText('board-PolicePhone-freshness-dev-precinct-car-01')).toBeInTheDocument();
    expect(within(slot).getByText('dev-precinct-car-01')).toBeInTheDocument();
    expect(within(slot).getByText('ONLINE')).toBeInTheDocument();
    expect(within(slot).getByText('version=4')).toBeInTheDocument();
    expect(within(slot).getByText('sequence=504')).toBeInTheDocument();
    expect(within(slot).getByText('sourceSpec=S1-2')).toBeInTheDocument();
    expect(within(slot).getByText('evt-s1-2-heartbeat-001')).toBeInTheDocument();
    expect(within(slot).getByText('hash-s1-2-dev-precinct-car-current')).toBeInTheDocument();
    expect(within(slot).getByText('policePhoneId')).toBeInTheDocument();
    expect(within(slot).getByText('lastHeartbeatAt')).toBeInTheDocument();
    expect(within(slot).getByText('2026-04-28T10:29:35+09:00')).toBeInTheDocument();
    expect(within(slot).getByText('lastSyncAt')).toBeInTheDocument();
    expect(within(slot).getByText('2026-04-28T10:29:40+09:00')).toBeInTheDocument();
    expect(within(slot).getByText('elapsedSeconds=25')).toBeInTheDocument();
    expectNoWriteControls(slot);
    expect(JSON.stringify(rows)).toBe(beforeRender);

    const staleRows = deepFreeze([
      createFreshnessRow({
        freshness: 'stale',
        lastHeartbeatAt: '2026-04-28T10:28:45+09:00',
        lastSyncAt: '2026-04-28T10:28:50+09:00',
        elapsedSeconds: 75,
        elapsedLabel: '1분 15초 전',
      }),
    ]);
    const beforeStaleRender = JSON.stringify(staleRows);
    expectNoSourceId(staleRows);

    rerender(<PolicePhoneFreshnessSlot nowIso="2026-04-28T10:30:00+09:00" rows={staleRows} />);

    expectCanonicalRowIdentity(slot, 'stale');
    expect(within(slot).getByText('stale')).toBeInTheDocument();
    expect(within(slot).getByText('2026-04-28T10:28:50+09:00')).toBeInTheDocument();
    expect(within(slot).getByText('elapsedSeconds=75')).toBeInTheDocument();
    expect(within(slot).getByText('1분 15초 전')).toBeInTheDocument();
    expectNoWriteControls(slot);
    expect(JSON.stringify(staleRows)).toBe(beforeStaleRender);

    const lostRows = deepFreeze([
      createFreshnessRow({
        freshness: 'lost',
        lastHeartbeatAt: '2026-04-28T10:24:10+09:00',
        lastSyncAt: '2026-04-28T10:24:15+09:00',
        elapsedSeconds: 350,
        elapsedLabel: '5분 50초 전',
      }),
    ]);
    const beforeLostRender = JSON.stringify(lostRows);
    expectNoSourceId(lostRows);

    rerender(<PolicePhoneFreshnessSlot nowIso="2026-04-28T10:30:00+09:00" rows={lostRows} />);

    expectCanonicalRowIdentity(slot, 'lost');
    expect(within(slot).getByText('lost')).toBeInTheDocument();
    expect(within(slot).getByText('2026-04-28T10:24:15+09:00')).toBeInTheDocument();
    expect(within(slot).getByText('elapsedSeconds=350')).toBeInTheDocument();
    expect(within(slot).getByText('5분 50초 전')).toBeInTheDocument();
    expectNoWriteControls(slot);
    expect(JSON.stringify(lostRows)).toBe(beforeLostRender);
  });

  test('exposes shared loading, stale, and failure states with accessible roles', () => {
    const { rerender } = render(
      <BoardSlotState slot="police_phone_freshness" state={{ kind: 'loading', label: '단말 최신성 불러오는 중' }} />,
    );

    expect(screen.getByRole('status')).toHaveTextContent('단말 최신성 불러오는 중');

    rerender(
      <BoardSlotState
        slot="police_phone_freshness"
        state={{
          kind: 'stale',
          rowId: 'board-PolicePhone-freshness-dev-precinct-car-01',
          reason: 'STALE_REFETCH',
          latestEventId: 'evt-s1-2-heartbeat-001',
        }}
      />,
    );

    expect(screen.getByRole('status')).toHaveTextContent('STALE_REFETCH');
    expect(screen.getByRole('status')).toHaveTextContent('evt-s1-2-heartbeat-001');

    rerender(
      <BoardSlotState
        slot="package_badge"
        state={{
          kind: 'stale',
          rowId: 'board-package-inc-precinct-first-001',
          reason: 'STALE_REFETCH',
          latestEventId: 'evt-s7-package-stale-001',
        }}
      />,
    );

    expect(screen.getByRole('status')).toHaveTextContent('board-package-inc-precinct-first-001');
    expect(screen.getByRole('status')).toHaveTextContent('evt-s7-package-stale-001');

    rerender(
      <BoardSlotState
        slot="police_phone_freshness"
        state={{ kind: 'failure', reason: 'board_fetch_failed', latestEventId: 'evt-s1-2-heartbeat-001' }}
      />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('board_fetch_failed');
    expect(screen.queryByRole('button', { name: forbiddenWriteButtonName })).not.toBeInTheDocument();
  });
});

type FreshnessOverride = Partial<PolicePhoneFreshnessSlotProps['rows'][number]>;
type FreshnessRow = PolicePhoneFreshnessSlotProps['rows'][number];

function createFreshnessRow(override: FreshnessOverride = {}): PolicePhoneFreshnessSlotProps['rows'][number] {
  return {
    slot: 'police_phone_freshness',
    id: 'board-PolicePhone-freshness-dev-precinct-car-01',
    sourceResponseId: 'dev-precinct-car-01',
    policePhoneId: 'dev-precinct-car-01',
    status: 'ONLINE',
    freshness: 'normal',
    version: 4,
    sequence: 504,
    sourceSpec: 'S1-2',
    sourceHash: 'hash-s1-2-dev-precinct-car-current',
    latestEventId: 'evt-s1-2-heartbeat-001',
    lastHeartbeatAt: '2026-04-28T10:29:35+09:00',
    lastSyncAt: '2026-04-28T10:29:40+09:00',
    elapsedSeconds: 25,
    elapsedLabel: '25초 전',
    ...override,
  };
}

function expectCanonicalRowIdentity(slot: HTMLElement, freshness: FreshnessRow['freshness']) {
  const canonicalRow = slot.querySelector('[data-source-response-id="dev-precinct-car-01"]');
  expect(canonicalRow).not.toBeNull();
  expect.soft(canonicalRow).toHaveAttribute('data-board-row-id', 'board-PolicePhone-freshness-dev-precinct-car-01');
  expect.soft(canonicalRow).toHaveAttribute('data-source-response-id', 'dev-precinct-car-01');
  expect.soft(canonicalRow).toHaveAttribute('data-source-spec', 'S1-2');
  expect.soft(canonicalRow).toHaveAttribute('data-freshness', freshness);
}

function expectNoSourceId(rows: readonly FreshnessRow[]) {
  expect(rows.every((row) => !('sourceId' in row))).toBe(true);
}

function expectNoWriteControls(slot: HTMLElement) {
  expect(screen.queryByRole('button', { name: forbiddenWriteButtonName })).not.toBeInTheDocument();
  expect(slot.querySelectorAll('button')).toHaveLength(0);
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
