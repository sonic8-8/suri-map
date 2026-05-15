import { render, screen, within } from '@testing-library/react';
import { describe, expect, test } from 'vitest';

import type { RecentMarker } from '../../constants/mockSituationBoard';
import { RecentMarkerList } from './RecentMarkerList';

describe('RecentMarkerList', () => {
  test('shows marker cards without technical type summaries or duplicate memo text', () => {
    render(
      <RecentMarkerList
        recentMarkers={[
          createMarker({
            id: 'marker-drone-support',
            markerType: 'SUPPORT_REQUEST',
            markerTypeLabel: '지원 요청',
            title: 'request aerial check over roof line',
            summary: '드론 지원 요청',
            memo: 'request aerial check over roof line',
          }),
          createMarker({
            id: 'marker-field-condition',
            markerType: 'FIELD_CONDITION',
            markerTypeLabel: '지형',
            title: 'patrol car blocked by construction barrier',
            summary: '지형',
            memo: null,
          }),
        ]}
      />,
    );

    const droneMarker = screen.getByText('request aerial check over roof line').closest('li');
    expect(droneMarker).not.toBeNull();
    expect(within(droneMarker as HTMLElement).queryByText('드론 지원 요청')).not.toBeInTheDocument();
    expect(within(droneMarker as HTMLElement).getByText('지원 요청')).toBeInTheDocument();
    expect(within(droneMarker as HTMLElement).queryAllByText('request aerial check over roof line')).toHaveLength(1);

    const fieldMarker = screen.getByText('patrol car blocked by construction barrier').closest('li');
    expect(fieldMarker).not.toBeNull();
    expect(within(fieldMarker as HTMLElement).getByText('지형')).toBeInTheDocument();
  });
});

function createMarker(overrides: Partial<RecentMarker>): RecentMarker {
  return {
    id: 'marker',
    markerType: 'NOTE',
    markerTypeLabel: '메모',
    title: 'marker title',
    summary: 'marker summary',
    occurredAt: '2026-05-15T03:20:00Z',
    timeLabel: '12:20',
    opLabel: 'OP 2차',
    reporterLabel: undefined,
    sourceLabel: '상황판',
    coordinateLabel: '35.12345N / 126.12345E',
    memo: null,
    ...overrides,
  };
}
