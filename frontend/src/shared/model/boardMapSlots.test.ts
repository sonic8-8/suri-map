import { describe, expect, test } from 'vitest';
import { createBoardMovementPaths, type BoardResponseLike } from './boardMapSlots';

describe('createBoardMovementPaths', () => {
  test('path slot segment geometry를 segment별 이동 경로로 변환한다', () => {
    const board = createBoardWithPathSegments();

    const paths = createBoardMovementPaths(board);

    expect(paths).toHaveLength(2);
    expect(paths[0]).toMatchObject({
      id: SEGMENT_VEHICLE_ID,
      policePhoneId: POLICE_PHONE_ID,
      accountId: ACCOUNT_ID,
      opId: OP_ID,
      movementType: 'VEHICLE',
      startedAt: '2026-05-16T09:00:00+09:00',
      endedAt: '2026-05-16T09:00:10+09:00',
      coordinates: [
        [126.913, 35.162],
        [126.914, 35.163],
      ],
    });
    expect(paths[1]).toMatchObject({
      id: SEGMENT_FOOT_ID,
      policePhoneId: POLICE_PHONE_ID,
      accountId: ACCOUNT_ID,
      opId: OP_ID,
      movementType: 'FOOT',
      startedAt: '2026-05-16T09:00:15+09:00',
      endedAt: null,
      coordinates: [
        [126.914, 35.163],
        [126.915, 35.164],
      ],
    });
  });

  test('segment 식별자가 없으면 parent path 값으로 담당자와 OP를 상속한다', () => {
    const board = createBoardWithPathSegments();

    const paths = createBoardMovementPaths(board);

    expect(paths.map((path) => path.policePhoneId)).toEqual([POLICE_PHONE_ID, POLICE_PHONE_ID]);
    expect(paths.map((path) => path.accountId)).toEqual([ACCOUNT_ID, ACCOUNT_ID]);
    expect(paths.map((path) => path.opId)).toEqual([OP_ID, OP_ID]);
  });
});

function createBoardWithPathSegments(): BoardResponseLike {
  return {
    incidentId: INCIDENT_ID,
    serverTs: '2026-05-16T09:00:30+09:00',
    activeOpId: OP_ID,
    slots: {
      path: [
        {
          id: PATH_ID,
          opId: OP_ID,
          policePhoneId: POLICE_PHONE_ID,
          accountId: ACCOUNT_ID,
          geometry: {
            type: 'LineString',
            coordinates: [
              [126.913, 35.162],
              [126.914, 35.163],
              [126.915, 35.164],
            ],
          },
          segments: [
            {
              id: SEGMENT_VEHICLE_ID,
              movementType: 'VEHICLE',
              movementTypeSource: 'AUTO',
              geometry: {
                type: 'LineString',
                coordinates: [
                  [126.913, 35.162],
                  [126.914, 35.163],
                ],
              },
              startedAt: '2026-05-16T09:00:00+09:00',
              endedAt: '2026-05-16T09:00:10+09:00',
            },
            {
              id: SEGMENT_FOOT_ID,
              movementType: 'FOOT',
              movementTypeSource: 'AUTO',
              geometry: {
                type: 'LineString',
                coordinates: [
                  [126.914, 35.163],
                  [126.915, 35.164],
                ],
              },
              startedAt: '2026-05-16T09:00:15+09:00',
            },
          ],
        },
      ],
    },
  };
}

const INCIDENT_ID = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001';
const OP_ID = '88888888-8888-8888-8888-888888880001';
const POLICE_PHONE_ID = '50000000-0000-0000-0000-000000000001';
const ACCOUNT_ID = '10000000-0000-0000-0000-000000000001';
const PATH_ID = 'ffffffff-ffff-ffff-ffff-ffffffffffff';
const SEGMENT_VEHICLE_ID = '33333333-3333-3333-3333-333333330001';
const SEGMENT_FOOT_ID = '33333333-3333-3333-3333-333333330002';
