import { describe, expect, test } from 'vitest';

import type { SituationBoardResponseDto } from '../../data/getSituationBoard';
import type { MovementPath, RecentMarker } from '../constants/mockSituationBoard';
import type { BoardSearchAreaRow } from './searchAreaBoardMapper';
import {
  filterSituationBoardMarkersForMap,
  filterSituationBoardMovementPathsForMap,
  filterSituationBoardSearchAreaRowsForMap,
} from './opScopedMapRendering';

describe('opScopedMapRendering', () => {
  test('상황판 searchAreas는 현재 활성 OP만 남긴다', () => {
    expect(
      filterSituationBoardSearchAreaRowsForMap(
        [
          createSearchAreaRow({ id: 'area-op-1', opId: OP_1_ID }),
          createSearchAreaRow({ id: 'area-op-7', opId: OP_7_ID }),
          createSearchAreaRow({ id: 'area-no-op', opId: null }),
        ],
        OP_7_ID,
      ).map((row) => row.id),
    ).toEqual(['area-op-7']);
  });

  test('상황판 routes는 현재 활성 OP만 남긴다', () => {
    expect(
      filterSituationBoardMovementPathsForMap(
        [
          createMovementPath({ id: 'path-op-1', opId: OP_1_ID }),
          createMovementPath({ id: 'path-op-7', opId: OP_7_ID }),
          createMovementPath({ id: 'path-op-8', opId: OP_8_ID }),
        ],
        OP_7_ID,
      ).map((path) => path.id),
    ).toEqual(['path-op-7']);
  });

  test('상황판 markers는 OP 1부터 현재 활성 OP까지만 누적한다', () => {
    const board = createBoardWithOperationalPeriods(OP_7_ID);

    expect(
      filterSituationBoardMarkersForMap(
        [
          createMarker({ id: 'marker-op-1', opId: OP_1_ID }),
          createMarker({ id: 'marker-op-7', opId: OP_7_ID }),
          createMarker({ id: 'marker-op-8', opId: OP_8_ID }),
          createMarker({ id: 'marker-no-op', opId: null }),
        ],
        board,
        OP_7_ID,
      ).map((marker) => marker.id),
    ).toEqual(['marker-op-1', 'marker-op-7']);
  });
});

function createSearchAreaRow(overrides: Partial<BoardSearchAreaRow>): BoardSearchAreaRow {
  return {
    id: 'area-op-7',
    opId: OP_7_ID,
    parentAreaId: null,
    areaLevel: 'TEAM',
    status: 'ACTIVE',
    name: '팀 구역',
    version: 1,
    coordinates: [
      [126.9, 35.1],
      [126.91, 35.1],
      [126.91, 35.11],
      [126.9, 35.1],
    ],
    assignedAccounts: [],
    ...overrides,
  };
}

function createMovementPath(overrides: Partial<MovementPath>): MovementPath {
  return {
    id: 'path-op-7',
    policePhoneId: 'phone-1',
    accountId: 'account-1',
    freshnessStatus: 'ONLINE',
    routeColor: null,
    opId: OP_7_ID,
    label: '경로',
    movementType: 'FOOT',
    coordinates: [
      [126.9, 35.1],
      [126.91, 35.11],
    ],
    startedAt: '2026-05-19T09:00:00+09:00',
    endedAt: null,
    ...overrides,
  };
}

function createMarker(overrides: Partial<RecentMarker>): RecentMarker {
  return {
    id: 'marker-op-7',
    markerType: 'NOTE',
    supportRequestType: null,
    markerTypeLabel: '메모',
    title: '운영 메모',
    summary: '메모',
    occurredAt: '2026-05-19T09:10:00+09:00',
    timeLabel: '09:10',
    opId: OP_7_ID,
    coordinates: [126.9, 35.1],
    ...overrides,
  };
}

function createBoardWithOperationalPeriods(activeOpId: string): SituationBoardResponseDto {
  return {
    incidentId: 'incident-1',
    boardResponseVersion: 1,
    serverTs: '2026-05-19T09:30:00+09:00',
    activeOpId,
    selectedOpIds: [OP_1_ID, OP_7_ID, OP_8_ID],
    geometryHash: 'hash',
    slots: {
      op_history: [
        createOpRow(OP_1_ID, 1),
        createOpRow(OP_7_ID, 7),
        createOpRow(OP_8_ID, 8),
      ],
    },
    slotSources: {},
    sourceVersions: {},
    sourceHashes: {},
  };
}

function createOpRow(opId: string, sequenceNumber: number) {
  return {
    id: opId,
    opId,
    sequenceNumber,
  };
}

const OP_1_ID = 'op-1';
const OP_7_ID = 'op-7';
const OP_8_ID = 'op-8';
