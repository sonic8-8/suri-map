import { describe, expect, test } from 'vitest';

import { createAreaDraft, createOverallDraft } from './draftUtils';

describe('draftUtils', () => {
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
