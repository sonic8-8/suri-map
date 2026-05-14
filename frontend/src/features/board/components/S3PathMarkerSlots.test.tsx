import { render, screen, within } from '@testing-library/react';
import { describe, expect, test } from 'vitest';
import { MarkerSlot, type MarkerSlotProps } from './MarkerSlot';
import { PathSlot, type PathSlotProps } from './PathSlot';

const forbiddenWriteButtonName = /저장|수정|보정|삭제|생성|시작|종료|지원 요청|사진|업로드/;

describe('L6-T03B S3-1/S5 path and marker board slots', () => {
  test('renders path source data without mutating the S3-1 owner rows', () => {
    const rows = deepFreeze([createPathRow()]);
    const beforeRender = JSON.stringify(rows);

    render(<PathSlot rows={rows} />);

    const slot = screen.getByTestId('slot-path');
    const pathRow = slot.querySelector(`[data-source-id="${PATH_MIXED_ID}"]`);
    expect(pathRow).not.toBeNull();
    expect.soft(pathRow).toHaveAttribute('data-geometry-hash', 'path-geometry-hash-precinct-mixed-current');
    expect(within(slot).getByText(PATH_MIXED_ID)).toBeInTheDocument();
    expect(within(slot).getByText('ACTIVE')).toBeInTheDocument();
    expect(within(slot).getByText('version=2')).toBeInTheDocument();
    expect(within(slot).getByText('sequence=502')).toBeInTheDocument();
    expect(within(slot).getByText('sourceSpec=S3-1')).toBeInTheDocument();
    expect(within(slot).getByText('evt-s3-path-appended-001')).toBeInTheDocument();
    expect(within(slot).getByText('hash-s3-path-mixed-current')).toBeInTheDocument();
    expect(within(slot).getByText('opId')).toBeInTheDocument();
    expect(within(slot).getByText(OP1_ID)).toBeInTheDocument();
    expect(within(slot).getByText('policePhoneId')).toBeInTheDocument();
    expect(within(slot).getByText(POLICE_PHONE_ID)).toBeInTheDocument();
    expect(within(slot).getByText('LineString')).toBeInTheDocument();
    expect(within(slot).getByText('126.956000,37.570000')).toBeInTheDocument();
    expect(within(slot).getByText('126.958250,37.571220')).toBeInTheDocument();
    expect(within(slot).getByText(SEGMENT_VEHICLE_ID)).toBeInTheDocument();
    expect(within(slot).getByText('VEHICLE')).toBeInTheDocument();
    expect(within(slot).getByText(SEGMENT_FOOT_ID)).toBeInTheDocument();
    expect(within(slot).getByText('FOOT')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: forbiddenWriteButtonName })).not.toBeInTheDocument();
    expect(JSON.stringify(rows)).toBe(beforeRender);
  });

  test('renders marker source data without mutating the S5 owner rows', () => {
    const rows = deepFreeze([createMarkerRow()]);
    const beforeRender = JSON.stringify(rows);

    render(<MarkerSlot rows={rows} />);

    const slot = screen.getByTestId('slot-marker');
    const markerRow = slot.querySelector(`[data-source-id="${MARKER_ID}"]`);
    expect(markerRow).not.toBeNull();
    expect.soft(markerRow).toHaveAttribute('data-geometry-hash', 'marker-geometry-hash-mk-precinct-clue-current');
    expect(within(slot).getByText(MARKER_ID)).toBeInTheDocument();
    expect(within(slot).getByText('CLUE')).toBeInTheDocument();
    expect(within(slot).getByText('ACTIVE')).toBeInTheDocument();
    expect(within(slot).getByText('version=1')).toBeInTheDocument();
    expect(within(slot).getByText('sequence=601')).toBeInTheDocument();
    expect(within(slot).getByText('sourceSpec=S5')).toBeInTheDocument();
    expect(within(slot).getByText('evt-s5-marker-created-001')).toBeInTheDocument();
    expect(within(slot).getByText('hash-s5-marker-clue-current')).toBeInTheDocument();
    expect(within(slot).getByText('opId')).toBeInTheDocument();
    expect(within(slot).getByText(OP1_ID)).toBeInTheDocument();
    expect(within(slot).getByText('Point')).toBeInTheDocument();
    expect(within(slot).getByText('126.956500,37.571200')).toBeInTheDocument();
    expect(within(slot).getByText('신고자 진술 위치')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: forbiddenWriteButtonName })).not.toBeInTheDocument();
    expect(JSON.stringify(rows)).toBe(beforeRender);
  });

  test('exposes observable loading and failure states without write callbacks', () => {
    const { rerender } = render(<PathSlot loadState={{ kind: 'loading' }} rows={[]} />);

    expect(screen.getByRole('status')).toHaveTextContent('경로 불러오는 중');

    rerender(<MarkerSlot loadState={{ kind: 'loading' }} rows={[]} />);

    expect(screen.getByRole('status')).toHaveTextContent('마커 불러오는 중');

    rerender(<PathSlot loadState={{ kind: 'failure', reason: 'board_fetch_failed' }} rows={[]} />);

    expect(screen.getByRole('alert')).toHaveTextContent('board_fetch_failed');

    rerender(<MarkerSlot loadState={{ kind: 'failure', reason: 'board_fetch_failed' }} rows={[]} />);

    expect(screen.getByRole('alert')).toHaveTextContent('board_fetch_failed');
  });
});

function createPathRow(): PathSlotProps['rows'][number] {
  return {
    slot: 'path',
    id: 'board-path-precinct-mixed-001',
    sourceId: PATH_MIXED_ID,
    incidentId: INCIDENT_ID,
    opId: OP1_ID,
    policePhoneId: POLICE_PHONE_ID,
    status: 'ACTIVE',
    version: 2,
    sequence: 502,
    sourceSpec: 'S3-1',
    sourceHash: 'hash-s3-path-mixed-current',
    latestEventId: 'evt-s3-path-appended-001',
    geometryHash: 'path-geometry-hash-precinct-mixed-current',
    geometry: {
      type: 'LineString',
      coordinates: [
        [126.956, 37.57],
        [126.95665, 37.57018],
        [126.9573, 37.57036],
        [126.95785, 37.57054],
        [126.958, 37.5707],
        [126.95808, 37.57088],
        [126.95816, 37.57105],
        [126.95825, 37.57122],
      ],
    } as const,
    segments: [
      {
        id: SEGMENT_VEHICLE_ID,
        version: 1,
        movementType: 'VEHICLE',
        movementTypeSource: 'AUTO',
        geometry: {
          type: 'LineString',
          coordinates: [
            [126.956, 37.57],
            [126.95665, 37.57018],
            [126.9573, 37.57036],
            [126.95785, 37.57054],
          ],
        } as const,
        startedAt: '2026-04-28T09:00:00+09:00',
        endedAt: '2026-04-28T09:00:15+09:00',
      },
      {
        id: SEGMENT_FOOT_ID,
        version: 1,
        movementType: 'FOOT',
        movementTypeSource: 'AUTO',
        geometry: {
          type: 'LineString',
          coordinates: [
            [126.958, 37.5707],
            [126.95808, 37.57088],
            [126.95816, 37.57105],
            [126.95825, 37.57122],
          ],
        } as const,
        startedAt: '2026-04-28T09:00:20+09:00',
        endedAt: '2026-04-28T09:00:35+09:00',
      },
    ],
  };
}

function createMarkerRow(): MarkerSlotProps['rows'][number] {
  return {
    slot: 'marker',
    id: 'board-marker-mk-precinct-clue-001',
    sourceId: MARKER_ID,
    incidentId: INCIDENT_ID,
    opId: OP1_ID,
    policePhoneId: null,
    type: 'CLUE',
    memo: '신고자 진술 위치',
    status: 'ACTIVE',
    version: 1,
    sequence: 601,
    sourceSpec: 'S5',
    sourceHash: 'hash-s5-marker-clue-current',
    latestEventId: 'evt-s5-marker-created-001',
    geometryHash: 'marker-geometry-hash-mk-precinct-clue-current',
    geometry: {
      type: 'Point',
      coordinates: [126.9565, 37.5712],
    } as const,
  };
}

const INCIDENT_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001';
const OP1_ID = '88888888-8888-8888-8888-888888880001';
const POLICE_PHONE_ID = '50000000-0000-0000-0000-000000000001';
const PATH_MIXED_ID = 'ffffffff-ffff-ffff-ffff-ffffffffffff';
const SEGMENT_VEHICLE_ID = '33333333-3333-3333-3333-333333330001';
const SEGMENT_FOOT_ID = '33333333-3333-3333-3333-333333330002';
const MARKER_ID = '55555555-5555-5555-5555-555555550001';

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
