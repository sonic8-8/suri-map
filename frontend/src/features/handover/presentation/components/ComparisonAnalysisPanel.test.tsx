import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, test, vi } from 'vitest';

import type { OpComparisonResponse } from '../../../operationalPeriod/api/opComparisonApi';
import { ComparisonAnalysisPanel } from './ComparisonAnalysisPanel';

const INCIDENT_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001';
const OP1_ID = '88888888-8888-8888-8888-888888880001';
const OP2_ID = '88888888-8888-8888-8888-888888880002';

describe('ComparisonAnalysisPanel', () => {
  test('requires at least two selected OPs before creating analysis', () => {
    render(
      <ComparisonAnalysisPanel
        incidentId={INCIDENT_ID}
        selectedOperationalPeriodIds={[OP1_ID]}
        operationalPeriods={periodOptions()}
        analysis={null}
        isCreating={false}
        errorMessage=""
        onCreateAnalysis={vi.fn()}
      />,
    );

    expect(screen.getByRole('button', { name: 'OP 비교 분석 생성' })).toBeDisabled();
    expect(screen.getByText('2개 이상 OP를 선택하면 비교 분석을 생성할 수 있습니다.')).toBeInTheDocument();
  });

  test('renders deterministic metrics and skipped narrative status', () => {
    render(
      <ComparisonAnalysisPanel
        incidentId={INCIDENT_ID}
        selectedOperationalPeriodIds={[OP1_ID, OP2_ID]}
        operationalPeriods={periodOptions()}
        analysis={comparisonResponse({ narrativeStatus: 'SKIPPED' })}
        isCreating={false}
        errorMessage=""
        onCreateAnalysis={vi.fn()}
      />,
    );

    expect(screen.getAllByText('OP 1차').length).toBeGreaterThan(0);
    expect(screen.getByText('1,240m')).toBeInTheDocument();
    expect(screen.getByText('문장 생성 생략')).toBeInTheDocument();
    expect(screen.getByText('임계값을 넘은 차이가 없어 결정 근거만 저장됐습니다.')).toBeInTheDocument();
  });

  test('renders observations and region facts without recommendation language', () => {
    render(
      <ComparisonAnalysisPanel
        incidentId={INCIDENT_ID}
        selectedOperationalPeriodIds={[OP1_ID, OP2_ID]}
        operationalPeriods={periodOptions()}
        analysis={comparisonResponse({ narrativeStatus: 'READY', withObservation: true })}
        isCreating={false}
        errorMessage=""
        onCreateAnalysis={vi.fn()}
      />,
    );

    expect(screen.getByText('OP2의 마커 수는 3건입니다.')).toBeInTheDocument();
    expect(screen.getByText('공통 통과 영역')).toBeInTheDocument();
    expect(screen.getByText(/면적\s+82\.5㎡/)).toBeInTheDocument();
    expect(screen.queryByText(/추천|위험|미수색/)).not.toBeInTheDocument();
  });

  test('keeps deterministic facts visible when narrative failed', () => {
    render(
      <ComparisonAnalysisPanel
        incidentId={INCIDENT_ID}
        selectedOperationalPeriodIds={[OP1_ID, OP2_ID]}
        operationalPeriods={periodOptions()}
        analysis={comparisonResponse({ narrativeStatus: 'FAILED', failureReason: 'narrative_generation_failed' })}
        isCreating={false}
        errorMessage=""
        onCreateAnalysis={vi.fn()}
      />,
    );

    expect(screen.getAllByText('문장 생성 실패').length).toBeGreaterThan(0);
    expect(screen.getByText('narrative_generation_failed')).toBeInTheDocument();
    expect(screen.getByText('마커 수')).toBeInTheDocument();
  });

  test('calls create handler from action button', async () => {
    const onCreateAnalysis = vi.fn();
    render(
      <ComparisonAnalysisPanel
        incidentId={INCIDENT_ID}
        selectedOperationalPeriodIds={[OP1_ID, OP2_ID]}
        operationalPeriods={periodOptions()}
        analysis={null}
        isCreating={false}
        errorMessage=""
        onCreateAnalysis={onCreateAnalysis}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'OP 비교 분석 생성' }));

    expect(onCreateAnalysis).toHaveBeenCalledTimes(1);
  });
});

function periodOptions() {
  return [
    { id: OP1_ID, label: 'OP 1차', statusLabel: '종료' },
    { id: OP2_ID, label: 'OP 2차', statusLabel: '진행 중' },
  ];
}

function comparisonResponse({
  narrativeStatus,
  failureReason,
  withObservation = false,
}: {
  narrativeStatus: OpComparisonResponse['narrativeStatus'];
  failureReason?: string;
  withObservation?: boolean;
}): OpComparisonResponse {
  return {
    comparisonId: '99000000-0000-0000-0000-000000000101',
    incidentId: INCIDENT_ID,
    operationalPeriodIds: [OP1_ID, OP2_ID],
    sourceHash: 'a'.repeat(64),
    status: 'READY',
    narrativeStatus,
    metrics: [
      {
        operationalPeriodId: OP1_ID,
        sequenceNumber: 1,
        startedAt: '2026-05-19T00:00:00Z',
        endedAt: '2026-05-19T01:00:00Z',
        pathDistanceMeters: 1240,
        walkingDistanceMeters: 840,
        drivingDistanceMeters: 400,
        walkingRatioPercent: 68,
        averageSpeedKmh: 2.4,
        stoppedSegmentCount: 1,
        stoppedDurationSeconds: 180,
        markerCount: 1,
        handoverMemoCount: 1,
      },
      {
        operationalPeriodId: OP2_ID,
        sequenceNumber: 2,
        startedAt: '2026-05-19T02:00:00Z',
        endedAt: null,
        pathDistanceMeters: 1700,
        walkingDistanceMeters: 900,
        drivingDistanceMeters: 800,
        walkingRatioPercent: 53,
        averageSpeedKmh: 3.1,
        stoppedSegmentCount: 0,
        stoppedDurationSeconds: 0,
        markerCount: 3,
        handoverMemoCount: 1,
      },
    ],
    diffFacts: [
      {
        factId: 'marker-count-op1-op2',
        type: 'METRIC_DIFF',
        metricKey: 'markerCount',
        leftOperationalPeriodId: OP1_ID,
        rightOperationalPeriodId: OP2_ID,
        leftValue: 1,
        rightValue: 3,
        delta: 2,
        threshold: '>=2',
      },
    ],
    regionFacts: [
      {
        factId: 'common-region-001',
        type: 'COMMON_REGION',
        operationalPeriodIds: [OP1_ID, OP2_ID],
        geometryGeojson: '{"type":"Polygon","coordinates":[]}',
        areaSquareMeters: 82.5,
        occupancies: [
          {
            operationalPeriodId: OP1_ID,
            firstObservedAt: '2026-05-19T00:10:00Z',
            lastObservedAt: '2026-05-19T00:18:00Z',
            durationSeconds: 480,
          },
        ],
      },
    ],
    observations: withObservation
      ? {
          observations: [
            {
              observation: 'OP2의 마커 수는 3건입니다.',
              evidence: [
                {
                  source: 'metric',
                  factId: '',
                  operationalPeriodId: OP2_ID,
                  key: 'markerCount',
                  value: '3',
                },
              ],
            },
          ],
        }
      : null,
    failureReason,
    requestedAt: '2026-05-19T00:00:00Z',
    generatedAt: '2026-05-19T00:00:03Z',
    version: 2,
  };
}
