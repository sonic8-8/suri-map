import { describe, expect, test } from 'vitest';
import { createBoardMovementPaths, type BoardResponseLike } from './boardMapSlots';
import { readSlotRows, type BoardSlotRowContainer } from './boardSlotRows';

describe('readSlotRows', () => {
  test('ignores empty object payloads when reading slot rows', () => {
    const board = { slots: { overall_search_area: {} } } satisfies BoardSlotRowContainer;

    expect(readSlotRows(board, 'overall_search_area')).toEqual([]);
  });

  test('keeps populated object payloads as a single row', () => {
    const board = {
      slots: {
        overall_search_area: {
          id: 'area-overall-001',
          geometry: {
            type: 'Polygon',
            coordinates: [
              [
                [126.904, 35.158],
                [126.923, 35.158],
                [126.923, 35.173],
                [126.904, 35.158],
              ],
            ],
          },
        },
      },
    } satisfies BoardSlotRowContainer;

    expect(readSlotRows(board, 'overall_search_area')).toHaveLength(1);
  });
});

describe('createBoardMovementPaths', () => {
  test('path slot segment geometry를 segment별 이동 경로로 변환한다', () => {
    const board = createBoardWithPathSegments();

    const paths = createBoardMovementPaths(board);

    expect(paths).toHaveLength(2);
    expect(paths[0]).toMatchObject({
      id: SEGMENT_VEHICLE_ID,
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

  test('segment 식별자가 없으면 parent path의 계정과 OP를 상속한다', () => {
    const board = createBoardWithPathSegments();

    const paths = createBoardMovementPaths(board);

    expect(paths.map((path) => path.accountId)).toEqual([ACCOUNT_ID, ACCOUNT_ID]);
    expect(paths.map((path) => path.opId)).toEqual([OP_ID, OP_ID]);
  });

  test('accountId가 없는 경로는 업무폰 최신성 행으로 보완하지 않고 제외한다', () => {
    const board = createBoardWithPathSegments();
    board.slots.police_phone_freshness = [
      {
        policePhoneId: POLICE_PHONE_ID,
        accountId: ACCOUNT_ID,
      },
    ];
    const pathRow = (board.slots.path as Record<string, unknown>[])[0];
    delete pathRow.accountId;

    const paths = createBoardMovementPaths(board);

    expect(paths).toEqual([]);
  });

  test('경로 계정에서 가장 최근 heartbeat를 보낸 업무폰의 최신성을 찾는다', () => {
    const board = createBoardWithPathSegments();
    board.slots.police_phone_freshness = [
      {
        policePhoneId: POLICE_PHONE_ID,
        accountId: ACCOUNT_ID,
        freshnessStatus: 'STALE',
        lastHeartbeatAt: '2026-05-16T09:00:00+09:00',
      },
      {
        policePhoneId: OTHER_POLICE_PHONE_ID,
        accountId: ACCOUNT_ID,
        freshnessStatus: 'ONLINE',
        lastHeartbeatAt: '2026-05-16T09:00:20+09:00',
      },
    ];

    const paths = createBoardMovementPaths(board);

    expect(paths.map((path) => path.freshnessStatus)).toEqual(['ONLINE', 'ONLINE']);
  });

  test('connects adjacent rendered segments across batch boundaries', () => {
    const board = createBoardWithPathSegments();
    const pathRow = (board.slots.path as Record<string, unknown>[])[0];
    const segments = pathRow.segments as Record<string, unknown>[];
    segments[1] = {
      ...segments[1],
      geometry: {
        type: 'LineString',
        coordinates: [
          [126.9145, 35.1635],
          [126.915, 35.164],
        ],
      },
    };

    const paths = createBoardMovementPaths(board);

    expect(paths[1]?.coordinates).toEqual([
      [126.914, 35.163],
      [126.9145, 35.1635],
      [126.915, 35.164],
    ]);
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
const OTHER_POLICE_PHONE_ID = '50000000-0000-0000-0000-000000000002';
const ACCOUNT_ID = '10000000-0000-0000-0000-000000000001';
const PATH_ID = 'ffffffff-ffff-ffff-ffff-ffffffffffff';
const SEGMENT_VEHICLE_ID = '33333333-3333-3333-3333-333333330001';
const SEGMENT_FOOT_ID = '33333333-3333-3333-3333-333333330002';
