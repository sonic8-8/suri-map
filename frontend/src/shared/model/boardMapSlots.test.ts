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

  test('hydrates path account id from police phone freshness slot', () => {
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

    expect(paths.map((path) => path.accountId)).toEqual([ACCOUNT_ID, ACCOUNT_ID]);
  });

  test('hydrates path freshness status from police phone freshness slot', () => {
    const board = createBoardWithPathSegments();
    board.slots.police_phone_freshness = [
      {
        policePhoneId: POLICE_PHONE_ID,
        accountId: ACCOUNT_ID,
        freshnessStatus: 'STALE',
      },
    ];

    const paths = createBoardMovementPaths(board);

    expect(paths.map((path) => path.freshnessStatus)).toEqual(['STALE', 'STALE']);
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
