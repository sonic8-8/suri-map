import type { Feature, FeatureCollection } from 'geojson';
import type { Map as MapLibreMap } from 'maplibre-gl';

declare global {
  interface Window {
    __SURI_MAP_OBSERVER__?: {
      connect(map: MapLibreMap): void;
      disconnect(map: MapLibreMap): void;
    };
    __SURI_MAP_MEASUREMENT_ENABLED__?: boolean;
    __SURI_MAP_PATH_TRACE__?: { incidentId: string; expiresAt: number };
    __SURI_MAP_COORDINATE_CHECKS_PENDING__?: number;
    __SURI_MAP_COORDINATE_CHECK_SCOPE__?: 'test-writes';
    __SURI_MAP_COORDINATE_CHECK_TARGETS__?: {
      searchPathIds: Set<string>;
      markerIds: Set<string>;
    };
  }
}

// 시험 도구를 지도 생성 전에 등록한다. 무거운 도형 계측과는 별도로 연결한다.
export function connectBoardMapObserver(map: MapLibreMap) {
  const observer = typeof window === 'undefined' ? undefined : window.__SURI_MAP_OBSERVER__;
  if (!observer) return () => {};

  try {
    observer.connect(map);
  } catch (error) {
    console.warn('Failed to connect board map observer', error);
  }

  // 등록이 교체·삭제돼도 이 지도에 연결했던 관찰 도구에 해제를 알린다.
  return () => {
    try {
      observer.disconnect(map);
    } catch (error) {
      console.warn('Failed to disconnect board map observer', error);
    }
  };
}

export function isBoardMeasurementEnabled() {
  return typeof window !== 'undefined' && window.__SURI_MAP_MEASUREMENT_ENABLED__ === true;
}

// 시험 브라우저가 수집한다. 제품에서는 이력을 쌓거나 외부로 전송하지 않는다.
export function recordBoardMeasurement(stage: string, details: Record<string, unknown>) {
  if (!isBoardMeasurementEnabled()) {
    const trace = typeof window === 'undefined' ? undefined : window.__SURI_MAP_PATH_TRACE__;
    if (!trace || trace.incidentId !== details.incidentId) return;
    const remaining = trace.expiresAt - Date.now();
    if (!(remaining > 0 && remaining <= 60_000)) return;
    if (
      ![
        'sse_received',
        'path_refresh_requested',
        'path_page_started',
        'path_page_response_received',
        'path_page_completed',
        'path_page_failed',
      ].includes(stage)
    )
      return;
    // 짧은 추적에는 본문·좌표·경로 배열을 싣지 않는다.
    details = Object.fromEntries(
      [
        'incidentId',
        'requestId',
        'queryScope',
        'kind',
        'eventId',
        'eventType',
        'sequence',
        'sourceEntityId',
        'sourceEntityType',
        'sourceVersion',
        'duplicate',
        'dirty',
        'hasMoreSegments',
        'hasMoreChanges',
        'consecutiveChanges',
        'failedKind',
        'hasMore',
      ].flatMap((key) => {
        const value = details[key];
        return value === null || ['string', 'number', 'boolean'].includes(typeof value) ? [[key, value]] : [];
      }),
    );
  }
  window.dispatchEvent(
    new CustomEvent('suri-map:board-measurement', {
      detail: { ...details, stage, timeOriginMs: performance.timeOrigin, elapsedMs: performance.now() },
    }),
  );
}

type BoardOpenTrace = {
  incidentId: string;
  pathHistoryReceived: boolean;
  isPathHistoryStillReceived: () => boolean;
  pathMap?: MapLibreMap;
  pathSourceCurrent: boolean;
  waitingIdle: boolean;
  holders: number;
};

// 상황판을 한 번 열 때 끝 시점을 한 번만 기록한다. 계측이 꺼져 있으면 만들지 않는다.
let boardOpenTrace: BoardOpenTrace | null = null;

// click 이벤트의 timeStamp는 performance.now와 같은 기준이다.
export function recordBoardOpenClicked(incidentId: string, timeStamp: number) {
  boardOpenTrace = null;
  if (!isBoardMeasurementEnabled()) return;
  boardOpenTrace = {
    incidentId,
    pathHistoryReceived: false,
    isPathHistoryStillReceived: () => true,
    pathSourceCurrent: false,
    waitingIdle: false,
    holders: 0,
  };
  recordBoardMeasurement('board_open_clicked', { incidentId, atMs: timeStamp });
}

// 열린 상황판이 추적을 붙잡는다. 다른 사건이면 바로, 상황판을 떠나면 해제 뒤에 추적을 지운다.
export function holdBoardOpenTrace(incidentId: string) {
  const trace = boardOpenTrace;
  if (!trace) return () => {};
  if (trace.incidentId !== incidentId) {
    boardOpenTrace = null;
    return () => {};
  }
  trace.holders += 1;
  return () => {
    trace.holders -= 1;
    // StrictMode는 해제 직후 같은 작업 안에서 다시 붙잡으므로, 그 뒤에도 남은 holder가 없을 때만 지운다.
    queueMicrotask(() => {
      if (trace.holders === 0 && boardOpenTrace === trace) boardOpenTrace = null;
    });
  };
}

// 상황판 화면이 렌더한 경로 조회 상태를 알린다. 지도 갱신 효과보다 늦게 호출해야 같은 render의 자료와 맞는다.
// isStillReceived는 render 뒤 React에 아직 전달되지 않은 새 조회가 있는지 idle 때 다시 확인한다.
export function markBoardPathHistoryReceived(received: boolean, isStillReceived: () => boolean = () => true) {
  if (!boardOpenTrace) return;
  boardOpenTrace.pathHistoryReceived = received;
  boardOpenTrace.isPathHistoryStillReceived = isStillReceived;
  waitForBoardPathsIdle(boardOpenTrace);
}

// 경로 source가 마지막으로 받은 자료를 반영했는지 알린다.
export function markBoardPathSourceCurrent(map: MapLibreMap, current: boolean) {
  if (!boardOpenTrace) return;
  boardOpenTrace.pathMap = map;
  boardOpenTrace.pathSourceCurrent = current;
  waitForBoardPathsIdle(boardOpenTrace);
}

function waitForBoardPathsIdle(trace: BoardOpenTrace) {
  const map = trace.pathMap;
  if (!map || !trace.pathHistoryReceived || !trace.pathSourceCurrent || trace.waitingIdle) return;
  trace.waitingIdle = true;
  map.once('idle', () => {
    trace.waitingIdle = false;
    if (boardOpenTrace !== trace || !isBoardMeasurementEnabled()) return;
    // 기다리는 동안 새 조회나 새 자료가 생겼으면, 다시 두 조건을 채운 뒤의 idle을 기다린다.
    if (!trace.pathHistoryReceived || !trace.isPathHistoryStillReceived() || !trace.pathSourceCurrent) return;
    boardOpenTrace = null;
    recordBoardMeasurement('all_paths_map_idle', { incidentId: trace.incidentId, atMs: performance.now() });
  });
  // source가 이미 같은 자료를 반영해 새 render가 없을 때도 idle을 한 번 받는다.
  map.triggerRepaint();
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
