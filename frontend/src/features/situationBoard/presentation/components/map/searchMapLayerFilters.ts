import type { BoardMapFeatureCollection } from '../../../../../shared/model/boardMapFeatures';
import type { PolicePhoneLegendFilterId, SearchAreaLegendFilterId } from '../../constants/mockSituationBoard';

export function filterSearchAreasByLegendFilters(
  searchAreas: BoardMapFeatureCollection,
  selectedFilterIds: SearchAreaLegendFilterId[],
): BoardMapFeatureCollection {
  const selectedFilterIdSet = new Set(selectedFilterIds);

  return {
    type: 'FeatureCollection',
    features: searchAreas.features.filter((feature) => {
      if (feature.properties.areaLevel === 'OVERALL') {
        return selectedFilterIdSet.has('overall_area');
      }

      if (feature.properties.areaLevel === 'UNIT') {
        return selectedFilterIdSet.has('unit_area');
      }

      if (feature.properties.areaLevel === 'TEAM' && feature.properties.status === 'COMPLETED') {
        return selectedFilterIdSet.has('completed_team_area');
      }

      if (feature.properties.areaLevel === 'TEAM') {
        return selectedFilterIdSet.has('team_area');
      }

      return true;
    }),
  };
}

export function filterMovementPathsByPolicePhoneLegendFilters(
  movementPaths: BoardMapFeatureCollection,
  selectedFilterIds: PolicePhoneLegendFilterId[],
): BoardMapFeatureCollection {
  const selectedFilterIdSet = new Set(selectedFilterIds);

  return {
    type: 'FeatureCollection',
    features: movementPaths.features.filter((feature) => {
      if (feature.properties.isActiveOp === 'true' && !selectedFilterIdSet.has('active_phone')) {
        return false;
      }

      switch (feature.properties.freshnessStatus) {
        case 'ONLINE':
          return selectedFilterIdSet.has('phone_online');
        case 'STALE':
          return selectedFilterIdSet.has('phone_stale');
        case 'LOST':
          return selectedFilterIdSet.has('phone_lost');
        default:
          return true;
      }
    }),
  };
}
