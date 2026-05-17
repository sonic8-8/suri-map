import { render, screen, within } from '@testing-library/react';
import { describe, expect, test } from 'vitest';

import { createIncidentScopedFallbackBoard } from '../../constants/mockSituationBoard';
import type { RecentMarker } from '../../constants/mockSituationBoard';
import { RecentMarkerList } from './RecentMarkerList';

describe('RecentMarkerList', () => {
  test('왼쪽 아이콘과 배지를 보여주면서 제목은 유지한다', () => {
    const board = createIncidentScopedFallbackBoard('incident-001');

    render(
      <RecentMarkerList
        incidentId={board.incidentId}
        markerTypes={board.markerTypes}
        supportMarkerTypes={board.supportMarkerTypes}
        recentMarkers={[
          createMarker({
            id: 'marker-drone-support',
            markerType: 'SUPPORT_REQUEST',
            supportRequestType: 'DRONE',
            markerTypeLabel: '지원 요청',
            title: '드론 촬영 요청',
            summary: '드론 지원 요청',
            memo: '헬기 촬영 확인 요청',
          }),
          createMarker({
            id: 'marker-field-condition',
            markerType: 'FIELD_CONDITION',
            markerTypeLabel: '지형',
            title: '공사 펜스로 차량 통행 불가',
            summary: '지형',
            memo: null,
          }),
        ]}
      />,
    );

    const droneMarker = screen.getByText('드론 촬영 요청').closest('li');
    expect(droneMarker).not.toBeNull();
    expect(droneMarker?.querySelector('svg')).not.toBeNull();
    expect(within(droneMarker as HTMLElement).getByText('드론 지원 요청')).toBeInTheDocument();
    expect(within(droneMarker as HTMLElement).getByText('지원 요청')).toBeInTheDocument();
    expect(within(droneMarker as HTMLElement).getByText('OP 2차')).toBeInTheDocument();
    expect(within(droneMarker as HTMLElement).getByText('드론 촬영 요청')).toBeInTheDocument();

    const fieldMarker = screen.getByText('공사 펜스로 차량 통행 불가').closest('li');
    expect(fieldMarker).not.toBeNull();
    expect(fieldMarker?.querySelector('svg')).not.toBeNull();
    expect(within(fieldMarker as HTMLElement).getByText('공사 펜스로 차량 통행 불가')).toBeInTheDocument();
    expect(within(fieldMarker as HTMLElement).getByText('지형')).toBeInTheDocument();
  });
});

function createMarker(overrides: Partial<RecentMarker>): RecentMarker {
  return {
    id: 'marker',
    markerType: 'NOTE',
    markerTypeLabel: '메모',
    title: '마커 제목',
    summary: '마커 요약',
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
