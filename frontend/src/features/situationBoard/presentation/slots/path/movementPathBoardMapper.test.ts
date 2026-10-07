import { describe, expect, test } from 'vitest';
import { areaColorTokens } from '../../../../../shared/constants/areaColorTokens';
import type { CompletedAreaDraft } from '../../../../../shared/model/areaDraft';
import { resolveRouteColorByGeometry } from '../../../../../shared/model/routeAreaColorMatcher';
import type { MovementPath, SearchAreaTreeNode } from '../../constants/mockSituationBoard';
import type { SituationBoardResponseDto } from '../../../data/getSituationBoard';
import { assignRouteColorsToMovementPaths, createMovementPathMapper, toMovementPaths } from './movementPathBoardMapper';

describe('createMovementPathMapper', () => {
  const segment = (id: string, start: number) => ({
    id, startPointOrder: start, endPointOrder: start + 1, movementType: 'FOOT',
    geometry: { type: 'LineString', coordinates: [[127 + start / 1000, 37], [127 + (start + 1) / 1000, 37]] },
    startedAt: '2026-10-07T00:00:00Z', endedAt: '2026-10-07T00:01:00Z',
  });
  const first = { id: 'path-a', accountId: 'account-a', opId: OP_ID, version: '1', segments: [segment('a-2', 2)] };
  const second = { id: 'path-b', accountId: 'account-b', opId: OP_ID, version: '1', segments: [segment('b-2', 2)] };
  const board = (rows: unknown[]): SituationBoardResponseDto => ({
    incidentId: 'incident-a', boardResponseVersion: 1, serverTs: '2026-10-07T00:00:00Z', activeOpId: OP_ID,
    selectedOpIds: [OP_ID], slots: { path: rows }, sourceVersions: {}, geometryHash: null, sourceHashes: {}, slotSources: {},
  });

  test('한 경로에 과거 구간을 추가하면, 다른 경로는 재사용하고 이웃 연결선은 다시 계산한다', () => {
    // given: 두 경로를 한 번 지도 자료로 변환한다.
    const mapPaths = createMovementPathMapper();
    const tree = createAreaNode();
    const previous = mapPaths(board([first, second]), OP_ID, tree, []);

    // when: 첫 경로의 앞 구간만 새로 도착한다.
    const changed = { ...first, segments: [segment('a-0', 0), ...first.segments] };
    const nextBoard = board([changed, second]);
    const next = mapPaths(nextBoard, OP_ID, tree, []);

    // then: 전체 변환과 같은 결과를 유지하면서, 바뀌지 않은 경로 객체를 재사용한다.
    expect(next).toEqual(assignRouteColorsToMovementPaths(toMovementPaths(nextBoard), tree, []));
    expect(next[2]).toBe(previous[1]);
    expect(next[2].label).toBe('Path 2');
    expect(next[1].coordinates).toEqual([[127.001, 37], [127.002, 37], [127.003, 37]]);
  });

  test('구간을 수정하거나 삭제하면, 좌표·버전과 이웃 연결선에 이전 값이 남지 않는다', () => {
    // given: 연결된 두 구간을 변환한다.
    const mapPaths = createMovementPathMapper();
    const tree = createAreaNode();
    const withHistory = { ...first, segments: [segment('a-0', 0), ...first.segments] };
    mapPaths(board([withHistory, second]), OP_ID, tree, []);

    // when: 첫 구간의 끝 좌표와 이동 유형, 경로 버전을 수정한다.
    const corrected = { ...withHistory, version: '2', segments: [
      { ...segment('a-0', 0), movementType: 'VEHICLE', geometry: { type: 'LineString', coordinates: [[127, 37], [127.0015, 37]] } },
      ...first.segments,
    ] };
    const correctedBoard = board([corrected, second]);
    const actual = mapPaths(correctedBoard, OP_ID, tree, []);

    // then: 수정·삭제 모두 전체 변환과 일치하며 삭제한 앞 구간의 끝 좌표를 남기지 않는다.
    expect(actual).toEqual(assignRouteColorsToMovementPaths(toMovementPaths(correctedBoard), tree, []));
    const deleted = mapPaths(board([first]), OP_ID, tree, []);
    expect(deleted).toHaveLength(1);
    expect(deleted[0].coordinates).toEqual(first.segments[0].geometry.coordinates);
    expect(mapPaths(board([]), OP_ID, tree, [])).toEqual([]);
  });

  test('색상·업무폰 상태·차수·사건이 바뀌면, 같은 경로 객체라도 새 맥락으로 계산한다', () => {
    // given: 경로 원본은 유지하고 표시 맥락만 바꾼다.
    const mapPaths = createMovementPathMapper();
    const tree = createAreaNode({ assignedAccounts: [{ accountId: 'account-a', displayName: 'A' }] });
    const initial = mapPaths(board([first]), OP_ID, tree, []);

    // when: 배정 색상과 업무폰 최신성을 바꾼다.
    const coloredTree = { ...tree, colorToken: 'AREA_ROSE_01' as const };
    const nextBoard = { ...board([first]), slots: { path: [first], police_phone_freshness: [
      { accountId: 'account-a', status: 'LOST', lastHeartbeatAt: '2026-10-07T00:00:00Z' },
    ] } };
    const changed = mapPaths(nextBoard, OP_ID, coloredTree, []);

    // then: 색상·상태는 갱신하고 차수 제외·사건 전환 시 이전 결과를 재사용하지 않는다.
    expect(changed).toEqual(assignRouteColorsToMovementPaths(toMovementPaths(nextBoard), coloredTree, []));
    expect(changed[0]).not.toBe(initial[0]);
    expect(changed[0].routeColor).toBe(areaColorTokens.AREA_ROSE_01.lineColor);
    expect(changed[0].freshnessStatus).toBe('LOST');
    expect(mapPaths(nextBoard, NEXT_OP_ID, coloredTree, [])).toEqual([]);
    const otherIncident = mapPaths({ ...nextBoard, incidentId: 'incident-b' }, OP_ID, coloredTree, []);
    expect(otherIncident[0]).not.toBe(changed[0]);
  });

  test('경로 순서가 바뀌면, 기본 라벨과 출력 순서를 전체 변환과 같게 유지한다', () => {
    // given: 첫 응답 순서로 자료를 만든다.
    const mapPaths = createMovementPathMapper();
    const tree = createAreaNode();
    mapPaths(board([first, second]), OP_ID, tree, []);

    // when: 같은 경로들이 다른 순서로 전달된다.
    const reordered = board([second, first]);
    const actual = mapPaths(reordered, OP_ID, tree, []);

    // then: 부분 배열의 인덱스를 기본 이름으로 잘못 사용하지 않는다.
    expect(actual).toEqual(assignRouteColorsToMovementPaths(toMovementPaths(reordered), tree, []));
    expect(actual.map(path => path.label)).toEqual(['Path 1', 'Path 2']);
  });
});

describe('assignRouteColorsToMovementPaths', () => {
  test('keeps route colors scoped by OP when the same account is assigned to different areas', () => {
    const paths = assignRouteColorsToMovementPaths(
      [
        createMovementPath({ id: 'path-op-a', opId: OP_ID, accountId: ACCOUNT_ID }),
        createMovementPath({ id: 'path-op-b', opId: NEXT_OP_ID, accountId: ACCOUNT_ID }),
      ],
      {
        id: 'overall',
        kind: 'overall',
        colorToken: 'AREA_BLUE_01',
        name: 'overall',
        meta: 'OVERALL',
        status: 'ACTIVE',
        geometryState: 'saved',
        children: [
          createAreaNode({ id: AREA_ID, opId: OP_ID, colorToken: 'AREA_GREEN_01' }),
          createAreaNode({ id: NEXT_AREA_ID, opId: NEXT_OP_ID, colorToken: 'AREA_ROSE_01' }),
        ],
      },
      [
        createDraft({ areaId: AREA_ID, colorToken: 'AREA_GREEN_01' }),
        createDraft({ areaId: NEXT_AREA_ID, colorToken: 'AREA_ROSE_01' }),
      ],
    );

    expect(paths[0].routeColor).toBe(areaColorTokens.AREA_GREEN_01.lineColor);
    expect(paths[1].routeColor).toBe(areaColorTokens.AREA_ROSE_01.lineColor);
  });

  test('uses the actual route geometry area color over stale assignee mapping', () => {
    expect(
      resolveRouteColorByGeometry(
        [
          [126.9162, 35.1625],
          [126.917, 35.1625],
        ],
        [
          {
            id: AREA_ID,
            opId: OP_ID,
            kind: 'team',
            coordinates: [
              [126.913, 35.162],
              [126.914, 35.162],
              [126.914, 35.163],
              [126.913, 35.163],
              [126.913, 35.162],
            ],
            lineColor: areaColorTokens.AREA_GREEN_01.lineColor,
          },
          {
            id: NEXT_AREA_ID,
            opId: OP_ID,
            kind: 'team',
            coordinates: [
              [126.916, 35.162],
              [126.918, 35.162],
              [126.918, 35.163],
              [126.916, 35.163],
              [126.916, 35.162],
            ],
            lineColor: areaColorTokens.AREA_ROSE_01.lineColor,
          },
        ],
        OP_ID,
      ),
    ).toBe(areaColorTokens.AREA_ROSE_01.lineColor);

    const paths = assignRouteColorsToMovementPaths(
      [
        createMovementPath({
          coordinates: [
            [126.9162, 35.1625],
            [126.917, 35.1625],
          ],
        }),
      ],
      {
        id: 'overall',
        kind: 'overall',
        colorToken: 'AREA_BLUE_01',
        name: 'overall',
        meta: 'OVERALL',
        status: 'ACTIVE',
        geometryState: 'saved',
        children: [
          createAreaNode({ id: AREA_ID, opId: OP_ID, colorToken: 'AREA_GREEN_01' }),
          createAreaNode({ id: NEXT_AREA_ID, opId: OP_ID, colorToken: 'AREA_ROSE_01', assignedAccounts: [] }),
        ],
      },
      [
        createDraft({
          areaId: AREA_ID,
          colorToken: 'AREA_GREEN_01',
          coordinates: [
            [126.913, 35.162],
            [126.914, 35.162],
            [126.914, 35.163],
            [126.913, 35.163],
            [126.913, 35.162],
          ],
        }),
        createDraft({
          areaId: NEXT_AREA_ID,
          colorToken: 'AREA_ROSE_01',
          coordinates: [
            [126.916, 35.162],
            [126.918, 35.162],
            [126.918, 35.163],
            [126.916, 35.163],
            [126.916, 35.162],
          ],
        }),
      ],
    );

    expect(paths[0].routeColor).toBe(areaColorTokens.AREA_ROSE_01.lineColor);
  });

  test('keeps paths renderable for incident accounts that are not assigned to any area', () => {
    const paths = assignRouteColorsToMovementPaths(
      [createMovementPath({ id: 'path-unassigned', accountId: UNASSIGNED_ACCOUNT_ID })],
      {
        id: 'overall',
        kind: 'overall',
        colorToken: 'AREA_BLUE_01',
        name: 'overall',
        meta: 'OVERALL',
        status: 'ACTIVE',
        geometryState: 'saved',
        assignedAccounts: [],
        children: [createAreaNode({ assignedAccounts: [] })],
      },
      [],
    );

    expect(paths).toHaveLength(1);
    expect(paths[0].routeColor).toMatch(/^#[0-9a-f]{6}$/i);
  });
});

function createAreaNode(overrides: Partial<SearchAreaTreeNode> = {}): SearchAreaTreeNode {
  return {
    id: AREA_ID,
    opId: OP_ID,
    kind: 'team',
    colorToken: 'AREA_GREEN_01',
    name: 'team area',
    meta: 'TEAM',
    status: 'ACTIVE',
    geometryState: 'saved',
    assignedAccounts: [{ accountId: ACCOUNT_ID, displayName: '대원 A', policePhoneId: POLICE_PHONE_ID }],
    children: [],
    ...overrides,
  };
}

function createDraft(overrides: Partial<CompletedAreaDraft>): CompletedAreaDraft {
  return {
    areaId: AREA_ID,
    kind: 'team',
    colorToken: 'AREA_GREEN_01',
    label: 'team area',
    coordinates: [
      [126.913, 35.162],
      [126.914, 35.162],
      [126.914, 35.163],
      [126.913, 35.163],
      [126.913, 35.162],
    ],
    ...overrides,
  };
}

function createMovementPath(overrides: Partial<MovementPath>): MovementPath {
  return {
    id: 'path-a',
    accountId: ACCOUNT_ID,
    freshnessStatus: 'UNKNOWN',
    routeColor: null,
    opId: OP_ID,
    label: 'Path A',
    movementType: 'FOOT',
    coordinates: [
      [126.913, 35.162],
      [126.914, 35.163],
    ],
    startedAt: '2026-05-16T09:00:00+09:00',
    endedAt: null,
    ...overrides,
  };
}

const OP_ID = '88888888-8888-8888-8888-888888880001';
const NEXT_OP_ID = '88888888-8888-8888-8888-888888880002';
const AREA_ID = 'cccccccc-cccc-cccc-cccc-cccccccc0001';
const NEXT_AREA_ID = 'cccccccc-cccc-cccc-cccc-cccccccc0002';
const POLICE_PHONE_ID = '50000000-0000-0000-0000-000000000001';
const ACCOUNT_ID = '10000000-0000-0000-0000-000000000001';
const UNASSIGNED_ACCOUNT_ID = '10000000-0000-0000-0000-000000000002';
