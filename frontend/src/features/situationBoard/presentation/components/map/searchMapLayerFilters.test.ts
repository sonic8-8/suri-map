import { describe, expect, test } from 'vitest';

import type { BoardMapFeatureCollection } from '../../../../../shared/model/boardMapFeatures';
import { createIncidentScopedFallbackBoard } from '../../../../../shared/model/situationBoardViewModel';
import { filterMovementPathsByPolicePhoneLegendFilters } from './searchMapLayerFilters';

describe('filterMovementPathsByPolicePhoneLegendFilters', () => {
  test('통신 상태 필터는 계정 기준 필터 ID로 경로를 고른다', () => {
    const paths = movementPaths([
      { entityId: 'online', isActiveOp: 'false', freshnessStatus: 'ONLINE' },
      { entityId: 'stale', isActiveOp: 'false', freshnessStatus: 'STALE' },
      { entityId: 'lost', isActiveOp: 'false', freshnessStatus: 'LOST' },
      { entityId: 'active', isActiveOp: 'true', freshnessStatus: 'ONLINE' },
    ]);

    const filtered = filterMovementPathsByPolicePhoneLegendFilters(paths, ['account_online', 'account_lost']);

    expect(filtered.features.map((feature) => feature.properties.entityId)).toEqual(['online', 'lost']);
    expect(
      filterMovementPathsByPolicePhoneLegendFilters(paths, ['active_account', 'account_online']).features.map(
        (feature) => feature.properties.entityId,
      ),
    ).toEqual(['online', 'active']);
  });

  test('범례에는 운용중 인원이 나오고 운용중 폴리폰은 나오지 않는다', () => {
    const labels = createIncidentScopedFallbackBoard('incident-a').legendItems.map((item) => item.label);

    expect(labels).toContain('운용중 인원');
    expect(labels).not.toContain('운용중 폴리폰');
  });
});

function movementPaths(properties: Record<string, string>[]): BoardMapFeatureCollection {
  return {
    type: 'FeatureCollection',
    features: properties.map((featureProperties) => ({
      type: 'Feature',
      properties: featureProperties,
      geometry: { type: 'LineString', coordinates: [[127, 37], [127.001, 37.001]] },
    })),
  };
}
