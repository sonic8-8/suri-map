import { describe, expect, test } from 'vitest';

import type { SituationBoardResponseDto } from '../../data/getSituationBoard';
import { toBoardRecentMarkers } from './markerBoardMapper';

describe('markerBoardMapper', () => {
  test('keeps marker memo out of summary so the marker list does not repeat the same text', () => {
    const markers = toBoardRecentMarkers({
      incidentId: 'incident-001',
      boardResponseVersion: 1,
      serverTs: '2026-05-01T00:00:00Z',
      activeOpId: 'op-002',
      selectedOpIds: ['op-002'],
      geometryHash: null,
      sourceHashes: {},
      slotSources: {},
      sourceVersions: {},
      slots: {
        op_toggle: [{ opId: 'op-002', sequenceNumber: 2 }],
        marker: [
          {
            id: 'marker-support-001',
            markerType: 'SUPPORT_REQUEST',
            supportRequestType: 'DRONE',
            source: 'APP',
            memo: 'request aerial check over roof line',
            occurredAt: '2026-05-01T09:48:00+09:00',
            policePhoneId: '00000000-0000-0000-0000-000000000207',
            geometry: {
              type: 'Point',
              coordinates: [126.9416, 37.5274],
            },
          },
        ],
      },
    } satisfies SituationBoardResponseDto);

    expect(markers).toHaveLength(1);
    expect(markers[0]?.summary).toBe('드론 지원 요청');
    expect(markers[0]?.opLabel).toBe('OP 2차');
    expect(markers[0]?.reporterLabel).toBeUndefined();
    expect(markers[0]?.memo).toBe('request aerial check over roof line');
  });
});
