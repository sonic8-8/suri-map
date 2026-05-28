import { describe, expect, test } from 'vitest';

import { createAreaDraft, createOverallDraft } from './draftUtils';

describe('draftUtils', () => {
  test('uses server colorToken for saved drafts', () => {
    const area = {
      id: 'area-001',
      incidentId: 'incident-001',
      areaLevel: 'TEAM',
      parentAreaId: 'unit-001',
      status: 'ACTIVE',
      colorToken: 'AREA_ROSE_01',
      version: 1,
      geometry: {
        type: 'Polygon',
        coordinates: [
          [
            [126.91, 37.51],
            [126.93, 37.51],
            [126.93, 37.53],
            [126.91, 37.53],
            [126.91, 37.51],
          ],
        ],
      },
    } as Parameters<typeof createAreaDraft>[0];

    expect(createAreaDraft(area, 1)?.colorToken).toBe('AREA_ROSE_01');
  });

  test('returns null when polygon geometry is malformed', () => {
    const malformedArea = {
      id: 'area-001',
      areaLevel: 'UNIT',
      parentAreaId: 'overall-001',
      status: 'ACTIVE',
      version: 1,
      geometry: {
        type: 'Polygon',
        coordinates: [],
      },
    } as unknown as Parameters<typeof createAreaDraft>[0];

    expect(createAreaDraft(malformedArea, 1)).toBeNull();
    expect(createOverallDraft(malformedArea as unknown as Parameters<typeof createOverallDraft>[0])).toBeNull();
  });
});
