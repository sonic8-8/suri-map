import { describe, expect, test } from 'vitest';

import type { SituationBoardResponseDto } from '../../data/getSituationBoard';
import { toOperationalPeriods, toMarkerOpLabel } from './operationalPeriodBoardMapper';

describe('operationalPeriodBoardMapper', () => {
  test('toOperationalPeriods는 sequenceNumber 기준으로 OP 라벨을 만든다', () => {
    const board = boardResponse({
      selectedOpIds: ['op-current', 'op-old'],
      activeOpId: 'op-current',
      slots: {
        op_toggle: [
          { opId: 'op-current', sequenceNumber: 7 },
          { opId: 'op-old', sequenceNumber: 1 },
        ],
      },
    });

    const periods = toOperationalPeriods(board, fallbackPeriods());

    expect(periods.map((period) => period.label)).toEqual(['OP 1차', 'OP 7차']);
    expect(periods.find((period) => period.id === 'op-current')?.state).toBe('current');
    expect(periods.find((period) => period.id === 'op-current')?.meta).toBe('진행 중');
  });

  test('toMarkerOpLabel은 marker의 sequenceNumber를 우선 사용한다', () => {
    const board = boardResponse({
      selectedOpIds: ['op-current'],
      activeOpId: 'op-current',
      slots: {
        op_history: [{ opId: 'op-current', sequenceNumber: 7 }],
      },
    });

    expect(toMarkerOpLabel('op-current', board)).toBe('OP 7차');
  });
});

function boardResponse({
  selectedOpIds,
  activeOpId,
  slots,
}: {
  selectedOpIds: string[];
  activeOpId: string | null;
  slots: Record<string, unknown>;
}): SituationBoardResponseDto {
  return {
    incidentId: 'incident-001',
    boardResponseVersion: 1,
    serverTs: '2026-05-01T00:00:00Z',
    activeOpId,
    selectedOpIds,
    slots,
    sourceVersions: {},
    geometryHash: null,
    sourceHashes: {},
    slotSources: {},
  };
}

function fallbackPeriods() {
  return [
    {
      id: 'fallback-1',
      label: 'OP 1차',
      reason: '수색',
      meta: '종료됨',
      state: 'ended' as const,
      startDate: '05.10',
      startTime: '09:00',
      endDate: '05.10',
      endTime: '09:30',
    },
  ];
}
