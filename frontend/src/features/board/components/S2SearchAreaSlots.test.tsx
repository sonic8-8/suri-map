import { render, screen, within } from '@testing-library/react';
import { describe, expect, test, vi } from 'vitest';
import { OverallSearchAreaSlot, type OverallSearchAreaSlotProps } from './OverallSearchAreaSlot';
import { SearchAreaSlot, type SearchAreaSlotProps } from './SearchAreaSlot';

const forbiddenWriteButtonName = /저장|수정|분할|완료|배정/;

describe('L6-T03A S2 search area board slots', () => {
  test('renders overall_search_area source data without mutating the S2 owner row', () => {
    const onDraftSave = vi.fn();
    const row = deepFreeze(createOverallSearchAreaRow());
    const beforeRender = JSON.stringify(row);

    render(<OverallSearchAreaSlot row={row} onDraftSave={onDraftSave} />);

    const slot = screen.getByTestId('slot-overall_search_area');
    expect(slot).toHaveAttribute('data-geometry-hash', 'overall-area-hash-precinct-current');
    expect(within(slot).getByText('osa-precinct-001')).toBeInTheDocument();
    expect(within(slot).getByText('ACTIVE')).toBeInTheDocument();
    expect(within(slot).getByText('version=2')).toBeInTheDocument();
    expect(within(slot).getByText('sequence=401')).toBeInTheDocument();
    expect(within(slot).getByText('sourceSpec=S2')).toBeInTheDocument();
    expect(within(slot).getByText('evt-s2-overall-area-001')).toBeInTheDocument();
    expect(within(slot).getByText('hash-s2-overall-area-current')).toBeInTheDocument();
    expect(within(slot).getByText('126.948000,37.565000')).toBeInTheDocument();
    expect(within(slot).getByText('126.968000,37.579000')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: forbiddenWriteButtonName })).not.toBeInTheDocument();
    expect(onDraftSave).not.toHaveBeenCalled();
    expect(JSON.stringify(row)).toBe(beforeRender);
  });

  test('renders area source data without mutating the S2 owner rows', () => {
    const onStateChange = vi.fn();
    const onSplit = vi.fn();
    const rows = deepFreeze([createAreaRow()]);
    const beforeRender = JSON.stringify(rows);

    render(<SearchAreaSlot rows={rows} onStateChange={onStateChange} onSplit={onSplit} />);

    const slot = screen.getByTestId('slot-area');
    const areaRow = slot.querySelector('[data-source-id="area-precinct-a1"]');
    expect(areaRow).not.toBeNull();
    expect.soft(areaRow).toHaveAttribute('data-geometry-hash', 'area-geometry-hash-precinct-a1-current');
    expect(within(slot).getByText('area-precinct-a1')).toBeInTheDocument();
    expect(within(slot).getByText('ASSIGNED')).toBeInTheDocument();
    expect(within(slot).getByText('version=1')).toBeInTheDocument();
    expect(within(slot).getByText('sequence=402')).toBeInTheDocument();
    expect(within(slot).getByText('sourceSpec=S2')).toBeInTheDocument();
    expect(within(slot).getByText('evt-s2-area-created-001')).toBeInTheDocument();
    expect(within(slot).getByText('hash-s2-area-a1-current')).toBeInTheDocument();
    expect.soft(within(slot).queryByText('area-geometry-hash-precinct-a1-current')).toBeInTheDocument();
    expect.soft(within(slot).queryByText('opId')).toBeInTheDocument();
    expect.soft(within(slot).queryByText('op-precinct-001-op1')).toBeInTheDocument();
    expect(within(slot).getByText('126.952000,37.568000')).toBeInTheDocument();
    expect(within(slot).getByText('126.961000,37.575000')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: forbiddenWriteButtonName })).not.toBeInTheDocument();
    expect(onStateChange).not.toHaveBeenCalled();
    expect(onSplit).not.toHaveBeenCalled();
    expect(JSON.stringify(rows)).toBe(beforeRender);
  });

  test('exposes observable loading and failure states without write callbacks', () => {
    const onDraftSave = vi.fn();
    const onStateChange = vi.fn();
    const onSplit = vi.fn();

    const { rerender } = render(
      <OverallSearchAreaSlot loadState={{ kind: 'loading' }} row={null} onDraftSave={onDraftSave} />,
    );

    expect(screen.getByRole('status')).toHaveTextContent('전체 수색 구역 불러오는 중');
    expect(onDraftSave).not.toHaveBeenCalled();

    rerender(
      <SearchAreaSlot loadState={{ kind: 'loading' }} rows={[]} onStateChange={onStateChange} onSplit={onSplit} />,
    );

    expect(screen.getByRole('status')).toHaveTextContent('수색 구역 불러오는 중');
    expect(onStateChange).not.toHaveBeenCalled();
    expect(onSplit).not.toHaveBeenCalled();

    rerender(
      <OverallSearchAreaSlot
        loadState={{ kind: 'failure', reason: 'board_fetch_failed' }}
        row={null}
        onDraftSave={onDraftSave}
      />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('board_fetch_failed');
    expect(onDraftSave).not.toHaveBeenCalled();

    rerender(
      <SearchAreaSlot
        loadState={{ kind: 'failure', reason: 'board_fetch_failed' }}
        rows={[]}
        onStateChange={onStateChange}
        onSplit={onSplit}
      />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('board_fetch_failed');
    expect(onStateChange).not.toHaveBeenCalled();
    expect(onSplit).not.toHaveBeenCalled();
  });
});

function createOverallSearchAreaRow(): OverallSearchAreaSlotProps['row'] {
  return {
    slot: 'overall_search_area',
    id: 'board-overall-search-area-inc-precinct-first-001',
    sourceId: 'osa-precinct-001',
    incidentId: 'inc-precinct-first-001',
    status: 'ACTIVE',
    version: 2,
    sequence: 401,
    sourceSpec: 'S2',
    sourceHash: 'hash-s2-overall-area-current',
    latestEventId: 'evt-s2-overall-area-001',
    geometryHash: 'overall-area-hash-precinct-current',
    geometry: {
      type: 'Polygon',
      coordinates: [
        [
          [126.948, 37.565],
          [126.968, 37.565],
          [126.968, 37.579],
          [126.948, 37.579],
          [126.948, 37.565],
        ],
      ],
    } as const,
  };
}

function createAreaRow(): SearchAreaSlotProps['rows'][number] {
  return {
    slot: 'area',
    id: 'board-area-precinct-a1',
    sourceId: 'area-precinct-a1',
    incidentId: 'inc-precinct-first-001',
    opId: 'op-precinct-001-op1',
    status: 'ASSIGNED',
    version: 1,
    sequence: 402,
    sourceSpec: 'S2',
    sourceHash: 'hash-s2-area-a1-current',
    latestEventId: 'evt-s2-area-created-001',
    geometryHash: 'area-geometry-hash-precinct-a1-current',
    geometry: {
      type: 'Polygon',
      coordinates: [
        [
          [126.952, 37.568],
          [126.961, 37.568],
          [126.961, 37.575],
          [126.952, 37.575],
          [126.952, 37.568],
        ],
      ],
    } as const,
  };
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
