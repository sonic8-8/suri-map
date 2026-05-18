import { describe, expect, test } from 'vitest';

import type { SituationBoardResponseDto } from '../../data/getSituationBoard';
import { readSlotRows } from './boardApiMappers';
import { toSearchAreaRows } from './searchAreaBoardMapper';

describe('boardApiMappers', () => {
  test('ignores empty object payloads when reading slot rows', () => {
    const board = { slots: { overall_search_area: {} } } as unknown as SituationBoardResponseDto;

    expect(readSlotRows(board, 'overall_search_area')).toEqual([]);
    expect(toSearchAreaRows(board)).toEqual([]);
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
    } as unknown as SituationBoardResponseDto;

    expect(readSlotRows(board, 'overall_search_area')).toHaveLength(1);
    expect(toSearchAreaRows(board)).toHaveLength(1);
  });
});
