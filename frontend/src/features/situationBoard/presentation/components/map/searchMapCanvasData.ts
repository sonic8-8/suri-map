import maplibregl, { type LngLatBoundsLike } from 'maplibre-gl';

import type {
  BoardMapFeature,
  BoardMapFeatureCollection,
} from '../../../../../shared/model/boardMapFeatures';
import type { SearchAreaTreeNode } from '../../constants/mockSituationBoard';

export type Position = [number, number];

type OperationalFeature = BoardMapFeature;
type OperationalFeatureCollection = BoardMapFeatureCollection;

const EMPTY_OPERATIONAL_FEATURE_COLLECTION: OperationalFeatureCollection = {
  type: 'FeatureCollection',
  features: [],
};

export { EMPTY_OPERATIONAL_FEATURE_COLLECTION };

export function createOperationalFeatureCollectionSignature(collection: OperationalFeatureCollection): string {
  return collection.features
    .map((feature) =>
      [
        feature.properties.entityId,
        feature.properties.areaLevel ?? '',
        feature.properties.status ?? '',
        feature.properties.version ?? '',
        feature.properties.bbox ?? '',
        JSON.stringify(feature.geometry.coordinates),
      ].join('|'),
    )
    .join(';');
}

export function createOperationalFeatureCollectionBoundsSignature(collection: OperationalFeatureCollection): string {
  return collection.features
    .map((feature) =>
      [
        feature.properties.entityId,
        feature.properties.areaLevel ?? '',
        feature.properties.bbox ?? '',
        JSON.stringify(feature.geometry.coordinates),
      ].join('|'),
    )
    .join(';');
}

export function toBounds(bbox: [number, number, number, number]): LngLatBoundsLike {
  return [
    [bbox[0], bbox[1]],
    [bbox[2], bbox[3]],
  ];
}

export function applySearchAreaStatuses(
  searchAreas: OperationalFeatureCollection,
  searchAreaTree: SearchAreaTreeNode,
): OperationalFeatureCollection {
  const statusesByAreaId = collectSearchAreaStatuses(searchAreaTree);

  return {
    type: 'FeatureCollection',
    features: searchAreas.features.map((feature) => ({
      ...feature,
      properties: {
        ...feature.properties,
        status: statusesByAreaId.get(feature.properties.entityId) ?? feature.properties.status,
      },
    })),
  };
}

export function getAssignedSearchAreaBounds(searchAreas: OperationalFeatureCollection): LngLatBoundsLike | null {
  const overallSearchAreas: OperationalFeatureCollection = {
    type: 'FeatureCollection',
    features: searchAreas.features.filter((feature) => feature.geometry.type === 'Polygon' && feature.properties.areaLevel === 'OVERALL'),
  };
  const overallSearchAreaBounds = getFeatureCollectionBounds(overallSearchAreas);
  if (overallSearchAreaBounds) {
    return overallSearchAreaBounds;
  }

  const availableSearchAreas: OperationalFeatureCollection = {
    type: 'FeatureCollection',
    features: searchAreas.features.filter((feature) => feature.geometry.type === 'Polygon'),
  };
  return getFeatureCollectionBounds(availableSearchAreas);
}

export function getSearchAreaBoundsById(
  searchAreas: OperationalFeatureCollection,
  searchAreaId: string,
): LngLatBoundsLike | null {
  const searchArea = searchAreas.features.find(
    (feature) => feature.properties.entityId === searchAreaId && feature.geometry.type === 'Polygon',
  );
  if (!searchArea) {
    return null;
  }

  return getFeatureCollectionBounds({
    type: 'FeatureCollection',
    features: [searchArea],
  });
}

export function resolveInitialMapView(
  fallbackBounds: LngLatBoundsLike | null,
  assignedSearchAreas: OperationalFeatureCollection,
): { state: 'overall-ready'; bounds: LngLatBoundsLike; overallSearchArea: OperationalFeatureCollection } | { state: 'fallback'; bounds: LngLatBoundsLike | null } {
  const assignedSearchAreaBounds = getAssignedSearchAreaBounds(assignedSearchAreas);
  if (assignedSearchAreaBounds) {
    return {
      state: 'overall-ready',
      bounds: assignedSearchAreaBounds,
      overallSearchArea: assignedSearchAreas,
    };
  }

  return {
    state: 'fallback',
    bounds: fallbackBounds,
  };
}

function collectSearchAreaStatuses(
  searchAreaTree: SearchAreaTreeNode,
  statusesByAreaId = new Map<string, SearchAreaTreeNode['status']>(),
) {
  statusesByAreaId.set(searchAreaTree.id, searchAreaTree.status);
  searchAreaTree.children?.forEach((childArea) => collectSearchAreaStatuses(childArea, statusesByAreaId));
  return statusesByAreaId;
}

function getFeatureCollectionBounds(collection: OperationalFeatureCollection): LngLatBoundsLike | null {
  const bounds = new maplibregl.LngLatBounds();
  collection.features.forEach((feature) => {
    const bbox = parseFeatureBbox(feature);
    if (bbox) {
      bounds.extend([bbox[0], bbox[1]]);
      bounds.extend([bbox[2], bbox[3]]);
      return;
    }
    extendBounds(bounds, feature.geometry.coordinates);
  });
  return bounds.isEmpty() ? null : bounds;
}

function parseFeatureBbox(feature: OperationalFeature): [number, number, number, number] | null {
  const rawBbox = feature.properties.bbox;
  if (!rawBbox) return null;

  try {
    const bbox = JSON.parse(rawBbox) as unknown;
    if (
      Array.isArray(bbox) &&
      bbox.length >= 4 &&
      typeof bbox[0] === 'number' &&
      typeof bbox[1] === 'number' &&
      typeof bbox[2] === 'number' &&
      typeof bbox[3] === 'number'
    ) {
      return [bbox[0], bbox[1], bbox[2], bbox[3]];
    }
  } catch {
    return null;
  }

  return null;
}

function extendBounds(bounds: maplibregl.LngLatBounds, coordinates: unknown): void {
  if (!Array.isArray(coordinates)) {
    return;
  }
  if (typeof coordinates[0] === 'number' && typeof coordinates[1] === 'number') {
    bounds.extend(coordinates as Position);
    return;
  }
  coordinates.forEach((item) => extendBounds(bounds, item));
}
