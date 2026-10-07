import type { Feature, FeatureCollection } from 'geojson';
import type { Map as MapLibreMap } from 'maplibre-gl';

declare global {
  interface Window {
    __SURI_MAP_MEASUREMENT_ENABLED__?: boolean;
    __SURI_MAP_COORDINATE_CHECKS_PENDING__?: number;
    __SURI_MAP_COORDINATE_CHECK_SCOPE__?: 'test-writes';
    __SURI_MAP_COORDINATE_CHECK_TARGETS__?: {
      searchPathIds: Set<string>;
      markerIds: Set<string>;
    };
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
  sourceId: string;
  updateId: string;
  layerIds: string[];
  remainingIds: Set<string>;
  coordinateChecks: Map<string, { feature: Feature; coordinateSignature: string }>;
};

const pendingMapUpdates = new WeakMap<MapLibreMap, Map<string, PendingMapUpdate>>();
const knownCoordinateSignatures = new WeakMap<MapLibreMap, Map<string, string>>();
const revisionProperty = '__boardMeasurementUpdateId';

// 지도에 전달한 이전 데이터와 새 데이터를 구분한다. 좌표와 API 데이터는 변경하지 않는다.
export function measureBoardMapUpdate(
  map: MapLibreMap,
  sourceId: string,
  layerIds: string[],
  data: FeatureCollection,
  changes?: { removedIds: string[] },
): FeatureCollection {
  if (!isBoardMeasurementEnabled()) return data;
  window.__SURI_MAP_COORDINATE_CHECK_SCOPE__ = 'test-writes';
  const existingUpdates = pendingMapUpdates.get(map);
  const updates = existingUpdates ?? new Map<string, PendingMapUpdate>();
  const coordinateSignatures = knownCoordinateSignatures.get(map) ?? new Map<string, string>();
  knownCoordinateSignatures.set(map, coordinateSignatures);
  if (!existingUpdates) {
    pendingMapUpdates.set(map, updates);
    const observeRenderedFeatures = () => {
      if (!isBoardMeasurementEnabled()) {
        updates.clear();
        return;
      }
      const layersBySource = new Map<string, string[]>();
      for (const update of updates.values()) layersBySource.set(update.sourceId, update.layerIds);
      for (const [source, layerIds] of layersBySource) {
        const layers = layerIds.filter((id) => map.getLayer(id));
        if (layers.length === 0) continue;
        const observedIdsByUpdate = new Map<string, Set<string>>();
        for (const feature of map.queryRenderedFeatures({ layers })) {
          const update = updates.get(feature.properties?.[revisionProperty]);
          if (feature.source !== source || !update || update.sourceId !== source) continue;
          const id = feature.properties?.entityId ?? feature.properties?.id;
          if (typeof id === 'string' && update.remainingIds.delete(id)) {
            const observedIds = observedIdsByUpdate.get(update.updateId) ?? new Set<string>();
            observedIds.add(id);
            observedIdsByUpdate.set(update.updateId, observedIds);
            const submitted = update.coordinateChecks.get(id);
            if (submitted) {
              // ponytail: 새 좌표도 render에서 동기 검사한다. 대량 신규 입력에서 지연이 재현되면 분할한다.
              recordRenderedCoordinates(map, source, layers, update.updateId, id, submitted.feature);
              coordinateSignatures.set(`${source}:${id}`, submitted.coordinateSignature);
            }
          }
        }
        for (const [updateId, observedIds] of observedIdsByUpdate) {
          recordBoardMeasurement('map_features_rendered', {
            sourceId: source,
            updateId,
            entityIds: [...observedIds],
          });
          if (updates.get(updateId)?.remainingIds.size === 0) updates.delete(updateId);
        }
      }
    };
    const finishUnobservedUpdates = () => {
      for (const update of updates.values()) {
        recordBoardMeasurement('map_update_unobserved_at_idle', {
          sourceId: update.sourceId,
          updateId: update.updateId,
          entityIds: [...update.remainingIds],
        });
      }
      updates.clear();
    };
    map.on('render', observeRenderedFeatures);
    map.on('idle', finishUnobservedUpdates);
    map.once('remove', () => {
      for (const update of updates.values()) {
        recordBoardMeasurement('map_update_cancelled', {
          sourceId: update.sourceId,
          updateId: update.updateId,
          entityIds: [...update.remainingIds],
        });
      }
      updates.clear();
      map.off('render', observeRenderedFeatures);
      map.off('idle', finishUnobservedUpdates);
      pendingMapUpdates.delete(map);
      knownCoordinateSignatures.delete(map);
    });
  }
  const updateId = crypto.randomUUID();
  const entityIds = data.features.flatMap((feature) => {
    const id = feature.properties?.entityId ?? feature.properties?.id;
    return typeof id === 'string' ? [id] : [];
  });
  const replacedIds = new Set([...entityIds, ...(changes?.removedIds ?? [])]);
  // 부분 갱신에서 건드리지 않은 도형은 이전 revision으로 계속 표시를 기다린다.
  for (const [previousId, previous] of updates) {
    if (previous.sourceId !== sourceId) continue;
    const replaced = [...previous.remainingIds].filter((id) => !changes || replacedIds.has(id));
    if (replaced.length > 0) {
      recordBoardMeasurement('map_update_replaced', { sourceId, updateId: previousId, entityIds: replaced });
      for (const id of replaced) {
        previous.remainingIds.delete(id);
        previous.coordinateChecks.delete(id);
      }
    }
    if (previous.remainingIds.size === 0) updates.delete(previousId);
  }
  if (changes) {
    for (const id of changes.removedIds) coordinateSignatures.delete(`${sourceId}:${id}`);
  } else {
    const currentIds = new Set(entityIds);
    const prefix = `${sourceId}:`;
    for (const key of coordinateSignatures.keys()) {
      if (key.startsWith(prefix) && !currentIds.has(key.slice(prefix.length))) coordinateSignatures.delete(key);
    }
  }
  const coordinateChecks: PendingMapUpdate['coordinateChecks'] = new Map();
  const targets = window.__SURI_MAP_COORDINATE_CHECK_TARGETS__;
  for (const feature of data.features) {
    const id = feature.properties?.entityId ?? feature.properties?.id;
    const coordinates = getMeasuredCoordinates(sourceId, feature);
    if (typeof id !== 'string' || !coordinates) continue;
    const coordinateSignature = JSON.stringify(coordinates);
    const key = `${sourceId}:${id}`;
    // 전송을 허용하기 전의 자료는 기준값이다. 표시 확인은 아래 전체 entityIds로 유지한다.
    if (!targets) {
      coordinateSignatures.set(key, coordinateSignature);
      continue;
    }
    const searchPathId = feature.properties?.searchPathId;
    const isTarget =
      sourceId === 'operational-movement-path'
        ? typeof searchPathId === 'string' && targets.searchPathIds.has(searchPathId)
        : targets.markerIds.has(id);
    if (isTarget && coordinateSignatures.get(key) !== coordinateSignature) {
      coordinateChecks.set(id, { feature, coordinateSignature });
    }
  }
  if (entityIds.length > 0)
    updates.set(updateId, { sourceId, updateId, layerIds, remainingIds: new Set(entityIds), coordinateChecks });
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
  const coordinates = getMeasuredCoordinates(sourceId, feature);
  if (!coordinates) return;

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

function getMeasuredCoordinates(sourceId: string, feature: Feature): number[][] | null {
  const geometry = feature.geometry;
  let coordinates: number[][];
  if (sourceId === 'operational-movement-path' && geometry.type === 'LineString' && geometry.coordinates.length >= 6) {
    coordinates = geometry.coordinates.slice(-6);
  } else if (sourceId === 'operational-marker' && geometry.type === 'Point') {
    coordinates = [geometry.coordinates];
  } else return null;
  if (coordinates.some((point) => point.length !== 2 || point.some((value) => !Number.isFinite(value)))) return null;
  return coordinates;
}
