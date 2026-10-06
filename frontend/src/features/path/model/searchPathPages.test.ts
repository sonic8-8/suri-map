import { describe, expect, test } from 'vitest';
import {
  createSearchPathPages,
  createSearchPathPageRequest,
  mergeSearchPathPage,
  parseSearchPathPage,
  type SearchPathPageRow,
  type SearchPathPage,
} from './searchPathPages';

export function pathRow(overrides: Partial<SearchPathPageRow> = {}): SearchPathPageRow {
  return {
    id: 'path-1',
    accountId: 'account-1',
    opId: 'op-1',
    status: 'RECORDING',
    version: '9007199254740993',
    baselineVersion: '9007199254740993',
    segments: [
      {
        id: 'segment-1',
        version: '2',
        startPointOrder: 6,
        endPointOrder: 11,
        movementType: 'FOOT',
        geometry: {
          type: 'LineString',
          coordinates: [
            [127, 35],
            [127.001, 35.001],
          ],
        },
        startedAt: '2026-10-07T00:00:00Z',
        endedAt: '2026-10-07T00:00:15Z',
      },
    ],
    segmentsProgress: { beforeStartPointOrder: 6, completed: false },
    ...overrides,
  };
}
export function page(paths: SearchPathPageRow[], hasMore = false): SearchPathPage {
  return { paths, nextSearchPathId: null, hasMore };
}

describe('경로 페이지 병합', () => {
  test('이력 응답이 늦게 도착하면, 최신 구간과 상태를 보존하고 이력 진행만 갱신한다', () => {
    // given: 최초 기준 이후 구간 보정과 경로 종료를 반영했다.
    const initial = mergeSearchPathPage(createSearchPathPages(), page([pathRow()], true), 'segments');
    const corrected = pathRow({
      status: 'ENDED',
      version: '9007199254740994',
      changesProgress: {
        appliedVersion: '9007199254740994',
        targetVersion: '9007199254740994',
        beforeStartPointOrder: 6,
        completed: true,
      },
    });
    corrected.segments = [{ ...corrected.segments[0], version: '3', movementType: 'VEHICLE' }];
    const updated = mergeSearchPathPage(initial, page([corrected]), 'changes');
    // when: 더 오래된 이력 자료를 받는다.
    const result = mergeSearchPathPage(
      updated,
      page([pathRow({ baselineVersion: undefined, segmentsProgress: { beforeStartPointOrder: 0, completed: true } })]),
      'segments',
    );
    // then: 큰 버전도 정확히 비교하고 변경분 진행을 이력 진행으로 덮지 않는다.
    expect(result.paths[0]).toMatchObject({
      status: 'ENDED',
      version: '9007199254740994',
      baselineVersion: '9007199254740993',
      segments: [{ version: '3', movementType: 'VEHICLE' }],
      changesProgress: corrected.changesProgress,
      segmentsProgress: { beforeStartPointOrder: 0, completed: true },
    });
  });

  test('변경분에서 새 경로를 발견하면, 좌표가 비어도 이력 조회를 다시 진행한다', () => {
    // given: 기존 범위의 이력은 끝났다.
    const state = { ...createSearchPathPages(), hasMoreSegments: false };
    // when: 기준 버전과 진행이 없는 새 경로를 발견한다.
    const result = mergeSearchPathPage(
      state,
      page([pathRow({ baselineVersion: null, segments: [], changesProgress: null })]),
      'changes',
    );
    // then: 빈 완료 경로로 오인하지 않고 요청에는 좌표를 싣지 않는다.
    expect(result.hasMoreSegments).toBe(true);
    expect(createSearchPathPageRequest(result, ['op-1'], 'segments').paths).toEqual([
      { id: 'path-1', segmentsProgress: null },
    ]);
    expect(JSON.stringify(createSearchPathPageRequest(result, ['op-1'], 'changes'))).not.toContain('coordinates');
  });

  test('응답 일부 경로만 받으면, 다른 경로와 다른 API의 순환 위치를 유지한다', () => {
    const state = mergeSearchPathPage(
      createSearchPathPages(),
      { ...page([pathRow()]), nextSearchPathId: 'path-2' },
      'segments',
    );
    const result = mergeSearchPathPage(
      state,
      page([pathRow({ id: 'path-2', baselineVersion: null, segments: [], changesProgress: null })]),
      'changes',
    );
    expect(result.paths.map((path) => path.id)).toEqual(['path-1', 'path-2']);
    expect(result.segmentsCursor).toBe('path-2');
  });

  test('잘못된 공개 응답이면, 이어받기 상태에 반영하기 전에 거부한다', () => {
    expect(() => parseSearchPathPage(page([pathRow({ version: '0' })]), 'segments', ['op-1'])).toThrow(
      'invalid_search_path_response',
    );
    expect(() => parseSearchPathPage(page([pathRow()]), 'segments', ['other-op'])).toThrow(
      'invalid_search_path_response',
    );
    expect(() => parseSearchPathPage(page([pathRow(), pathRow()]), 'segments', ['op-1'])).toThrow(
      'invalid_search_path_response',
    );
    expect(parseSearchPathPage(page([pathRow()]), 'segments', ['op-1'])).toEqual(page([pathRow()]));
  });
});
