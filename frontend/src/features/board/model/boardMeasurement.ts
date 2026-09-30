import type { Feature, FeatureCollection } from 'geojson';
import type { Map as MapLibreMap } from 'maplibre-gl';

declare global {
  interface Window {
    __SURI_MAP_MEASUREMENT_ENABLED__?: boolean;
    __SURI_MAP_COORDINATE_CHECKS_PENDING__?: number;
  }
}

export function isBoardMeasurementEnabled() {
  return typeof window !== 'undefined' && window.__SURI_MAP_MEASUREMENT_ENABLED__ === true;
}

// 시험 브라우저가 수집한다. 제품에서는 이력을 쌓거나 외부로 전송하지 않는다.
export function recordBoardMeasurement(stage: string, details: Record<string, unknown>) {
  if (!isBoardMeasurementEnabled()) return;
  window.dispatchEvent(
    new CustomEvent('suri-map:board-measurement', {
      detail: { ...details, stage, timeOriginMs: performance.timeOrigin, elapsedMs: performance.now() },
    }),
  );
}

type PendingMapUpdate = {
  updateId: string;
  layerIds: string[];
  remainingIds: Set<string>;
  features: Map<string, Feature>;
};

const pendingMapUpdates = new WeakMap<MapLibreMap, Map<string, PendingMapUpdate>>();
const revisionProperty = '__boardMeasurementUpdateId';

// 지도에 전달한 이전 데이터와 새 데이터를 구분한다. 좌표와 API 데이터는 변경하지 않는다.
export function measureBoardMapUpdate(
  map: MapLibreMap,
  sourceId: string,
  layerIds: string[],
  data: FeatureCollection,
): FeatureCollection {
  if (!isBoardMeasurementEnabled()) return data;
  const existingUpdates = pendingMapUpdates.get(map);
  const updates = existingUpdates ?? new Map<string, PendingMapUpdate>();
  if (!existingUpdates) {
    pendingMapUpdates.set(map, updates);
    const observeRenderedFeatures = () => {
      if (!isBoardMeasurementEnabled()) {
        updates.clear();
        return;
      }
      for (const [source, update] of updates) {
        const layers = update.layerIds.filter((id) => map.getLayer(id));
        if (layers.length === 0) continue;
        const observedIds = new Set<string>();
        for (const feature of map.queryRenderedFeatures({ layers })) {
          if (feature.source !== source || feature.properties?.[revisionProperty] !== update.updateId) continue;
          const id = feature.properties?.entityId ?? feature.properties?.id;
          if (typeof id === 'string' && update.remainingIds.delete(id)) {
            observedIds.add(id);
            const submitted = update.features.get(id);
            if (submitted) recordRenderedCoordinates(map, source, layers, update.updateId, id, submitted);
          }
        }
        if (observedIds.size > 0) {
          recordBoardMeasurement('map_features_rendered', {
            sourceId: source,
            updateId: update.updateId,
            entityIds: [...observedIds],
          });
        }
        if (update.remainingIds.size === 0) updates.delete(source);
      }
    };
    const finishUnobservedUpdates = () => {
      for (const [source, update] of updates) {
        recordBoardMeasurement('map_update_unobserved_at_idle', {
          sourceId: source,
          updateId: update.updateId,
          entityIds: [...update.remainingIds],
        });
      }
      updates.clear();
    };
    map.on('render', observeRenderedFeatures);
    map.on('idle', finishUnobservedUpdates);
    map.once('remove', () => {
      for (const [source, update] of updates) {
        recordBoardMeasurement('map_update_cancelled', {
          sourceId: source,
          updateId: update.updateId,
          entityIds: [...update.remainingIds],
        });
      }
      updates.clear();
      map.off('render', observeRenderedFeatures);
      map.off('idle', finishUnobservedUpdates);
      pendingMapUpdates.delete(map);
    });
  }
  const previous = updates.get(sourceId);
  if (previous) {
    recordBoardMeasurement('map_update_replaced', {
      sourceId,
      updateId: previous.updateId,
      entityIds: [...previous.remainingIds],
    });
  }
  const updateId = crypto.randomUUID();
  const entityIds = data.features.flatMap((feature) => {
    const id = feature.properties?.entityId ?? feature.properties?.id;
    return typeof id === 'string' ? [id] : [];
  });
  const features = new Map<string, Feature>();
  for (const feature of data.features) {
    const id = feature.properties?.entityId ?? feature.properties?.id;
    if (typeof id === 'string') features.set(id, feature);
  }
  updates.set(sourceId, { updateId, layerIds, remainingIds: new Set(entityIds), features });
  const entities = data.features.flatMap((feature) => {
    const properties = feature.properties;
    const id = properties?.entityId ?? properties?.id;
    if (typeof id !== 'string') return [];
    let sourceEntityType: string | null = null;
    let sourceEntityId: unknown = null;
    let rawVersion: unknown = null;
    if (sourceId === 'operational-movement-path') {
      sourceEntityType = 'search_path';
      sourceEntityId = properties?.searchPathId;
      rawVersion = properties?.searchPathVersion;
    } else if (sourceId === 'operational-marker') {
      sourceEntityType = 'marker';
      sourceEntityId = id;
      rawVersion = properties?.version;
    }
    const version = rawVersion == null || rawVersion === '' ? null : Number(rawVersion);
    return [
      {
        id,
        sourceEntityType,
        sourceEntityId: typeof sourceEntityId === 'string' ? sourceEntityId : null,
        sourceVersion: version !== null && Number.isSafeInteger(version) && version >= 0 ? version : null,
      },
    ];
  });
  recordBoardMeasurement('map_data_submitted', { sourceId, updateId, entityIds, entities });
  return {
    ...data,
    features: data.features.map((feature) => ({
      ...feature,
      properties: { ...feature.properties, [revisionProperty]: updateId },
    })),
  };
}

function recordRenderedCoordinates(
  map: MapLibreMap,
  sourceId: string,
  layers: string[],
  updateId: string,
  entityId: string,
  feature: Feature,
) {
  const geometry = feature.geometry;
  let coordinates: number[][];
  if (sourceId === 'operational-movement-path' && geometry.type === 'LineString' && geometry.coordinates.length >= 6) {
    coordinates = geometry.coordinates.slice(-6);
  } else if (sourceId === 'operational-marker' && geometry.type === 'Point') {
    coordinates = [geometry.coordinates];
  } else return;
  if (coordinates.some((point) => point.length !== 2 || point.some((value) => !Number.isFinite(value)))) return;

  const canvas = map.getCanvas();
  const pixels = coordinates.map(([longitude, latitude]) => map.project([longitude, latitude]));
  const inViewport = pixels.every(
    ({ x, y }) =>
      Number.isFinite(x) && Number.isFinite(y) && x >= 0 && y >= 0 && x < canvas.clientWidth && y < canvas.clientHeight,
  );
  const renderedAtCoordinates =
    inViewport &&
    pixels.every((pixel) =>
      map
        .queryRenderedFeatures(pixel, { layers })
        .some(
          (rendered) =>
            rendered.source === sourceId &&
            rendered.properties?.[revisionProperty] === updateId &&
            (rendered.properties?.entityId ?? rendered.properties?.id) === entityId,
        ),
    );
  const observedAtMs = performance.now();
  // 원문 좌표는 내보내지 않는다. 해시 계산 완료 시각과 render 관측 시각은 구분한다.
  window.__SURI_MAP_COORDINATE_CHECKS_PENDING__ = (window.__SURI_MAP_COORDINATE_CHECKS_PENDING__ ?? 0) + 1;
  void crypto.subtle
    .digest('SHA-256', new TextEncoder().encode(JSON.stringify(coordinates)))
    .then((digest) => {
      recordBoardMeasurement('map_coordinates_checked', {
        sourceId,
        updateId,
        entityId,
        pointCount: coordinates.length,
        inViewport,
        renderedAtCoordinates,
        observedAtMs,
        coordinateHash: Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join(''),
      });
    })
    .catch(() => recordBoardMeasurement('map_coordinate_check_failed', { sourceId, updateId, entityId }))
    .finally(() => {
      window.__SURI_MAP_COORDINATE_CHECKS_PENDING__ = (window.__SURI_MAP_COORDINATE_CHECKS_PENDING__ ?? 1) - 1;
    });
}
