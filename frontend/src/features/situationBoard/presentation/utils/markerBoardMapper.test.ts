import { describe, expect, test } from 'vitest';

import type { SituationBoardResponseDto } from '../../data/getSituationBoard';
import { toBoardRecentMarkers } from './markerBoardMapper';

describe('markerBoardMapper', () => {
  test('마커 메모가 요약에 중복 노출되지 않도록 한다', () => {
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
            memo: '헬기 촬영 확인 요청',
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
    expect(markers[0]?.title).toBe('드론 지원 요청');
    expect(markers[0]?.opLabel).toBe('OP 2차');
    expect(markers[0]?.reporterLabel).toBeUndefined();
    expect(markers[0]?.memo).toBe('헬기 촬영 확인 요청');
  });

  test('메모 마커의 제목은 내용에서 의미 있게 유도한다', () => {
    const markers = toBoardRecentMarkers({
      incidentId: 'incident-001',
      boardResponseVersion: 1,
      serverTs: '2026-05-01T00:00:00Z',
      activeOpId: 'op-001',
      selectedOpIds: ['op-001'],
      geometryHash: null,
      sourceHashes: {},
      slotSources: {},
      sourceVersions: {},
      slots: {
        marker: [
          {
            id: 'marker-note-001',
            markerType: 'NOTE',
            title: '메모',
            memo: '북쪽 출입문 조명 22시 이후 계속 켜짐',
            occurredAt: '2026-05-01T09:48:00+09:00',
            geometry: {
              type: 'Point',
              coordinates: [126.9416, 37.5274],
            },
          },
        ],
      },
    } satisfies SituationBoardResponseDto);

    expect(markers).toHaveLength(1);
    expect(markers[0]?.title).toBe('북쪽 출입문 조명 22시 이후 계속 켜짐');
    expect(markers[0]?.summary).toBe('메모');
  });

  test('maps attached marker photo summary to count and thumbnail preview url', () => {
    const markers = toBoardRecentMarkers({
      incidentId: 'incident-001',
      boardResponseVersion: 1,
      serverTs: '2026-05-01T00:00:00Z',
      activeOpId: 'op-001',
      selectedOpIds: ['op-001'],
      geometryHash: null,
      sourceHashes: {},
      slotSources: {},
      sourceVersions: {},
      slots: {
        marker: [
          {
            id: 'marker-clue-photo-001',
            markerType: 'CLUE',
            memo: '사진 첨부 메모',
            occurredAt: '2026-05-01T09:48:00+09:00',
            geometry: {
              type: 'Point',
              coordinates: [126.9416, 37.5274],
            },
            photoSummary: [
              {
                photoId: 'photo-001',
                status: 'ATTACHED',
                thumbnailUrl: '/mock-upload/markers/photo-001-thumb.jpg',
              },
            ],
          },
        ],
      },
    } satisfies SituationBoardResponseDto);

    expect(markers[0]?.photoCount).toBe(1);
    expect(markers[0]?.photoThumbnailUrl).toBe('/mock-upload/markers/photo-001-thumb.jpg');
  });
});
