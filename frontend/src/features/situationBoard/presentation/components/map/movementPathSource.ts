import type { GeoJSONSource, Map as MapLibreMap } from 'maplibre-gl';
import type { BoardMapFeature, BoardMapFeatureCollection } from '../../../../../shared/model/boardMapFeatures';
import { isBoardMeasurementEnabled, measureBoardMapUpdate } from '../../../../board/model/boardMeasurement';

type PathSnapshot = {
  data: BoardMapFeatureCollection;
  byId: Map<string, BoardMapFeature>;
  measured: boolean;
};
type PathSourceState = {
  latest: BoardMapFeatureCollection;
  applied?: PathSnapshot;
  sending?: PathSnapshot;
  failures: number;
};

const sourceStates = new WeakMap<GeoJSONSource, PathSourceState>();

export function syncMovementPathSource(
  map: MapLibreMap,
  sourceId: string,
  data: BoardMapFeatureCollection,
  layerIds: string[],
) {
  const source = map.getSource<GeoJSONSource>(sourceId);
  if (source?.type !== 'geojson') return;
  let state = sourceStates.get(source);
  if (!state) {
    const created: PathSourceState = { latest: data, failures: 0 };
    state = created;
    sourceStates.set(source, created);
    source.on('data', (event) => {
      if (event.sourceDataType !== 'content' || !source.loaded()) return;
      if (created.sending) {
        created.applied = created.sending;
        created.sending = undefined;
        created.failures = 0;
      }
      flushMovementPaths(map, sourceId, source, layerIds, created);
    });
    source.on('error', (event) => {
      created.sending = undefined;
      created.applied = undefined;
      created.failures += 1;
      console.error('Failed to update movement path source', event.error);
      // 증분 기준을 신뢰할 수 없으므로 최신 전체 자료로 한 번만 복구한다.
      if (created.failures === 1) flushMovementPaths(map, sourceId, source, layerIds, created);
    });
  }
  if (state.latest !== data) state.failures = 0;
  state.latest = data;
  flushMovementPaths(map, sourceId, source, layerIds, state);
}

function flushMovementPaths(
  map: MapLibreMap,
  sourceId: string,
  source: GeoJSONSource,
  layerIds: string[],
  state: PathSourceState,
) {
  if (state.sending || !source.loaded() || state.failures > 1) return;
  const data = state.latest;
  const measured = isBoardMeasurementEnabled();
  if (state.applied?.data === data && state.applied.measured === measured) return;
  const byId = new Map<string, BoardMapFeature>();
  for (const feature of data.features) {
    const id = feature.properties.entityId;
    if (!id || byId.has(id)) throw new Error('Movement path features require unique entity IDs');
    byId.set(id, feature);
  }
  const snapshot = { data, byId, measured };
  const previous = state.applied;
  const identify = (features: BoardMapFeature[]) => ({
    type: 'FeatureCollection' as const,
    features: features.map((feature) => ({ ...feature, id: feature.properties.entityId })),
  });
  if (!previous || previous.measured !== measured) {
    state.sending = snapshot;
    source.setData(measureBoardMapUpdate(map, sourceId, layerIds, identify(data.features)));
    return;
  }
  if (byId.size === 0 && previous.byId.size > 0) {
    state.sending = snapshot;
    measureBoardMapUpdate(map, sourceId, layerIds, data);
    source.updateData({ removeAll: true });
    return;
  }
  const remove = [...previous.byId.keys()].filter((id) => !byId.has(id));
  const changed = data.features.filter((feature) => {
    const old = previous.byId.get(feature.properties.entityId);
    return old !== feature && (!old || JSON.stringify(old) !== JSON.stringify(feature));
  });
  if (remove.length === 0 && changed.length === 0) {
    state.applied = snapshot;
    return;
  }
  state.sending = snapshot;
  // 재사용할 도형이 없거나 삭제 목록보다 남길 자료가 적으면 전체 전달이 더 작다.
  // 대량 개별 삭제·추가가 라이브러리의 인자 수 제한에 걸리는 경로도 피한다.
  if (changed.length === data.features.length || remove.length > data.features.length) {
    source.setData(measureBoardMapUpdate(map, sourceId, layerIds, identify(data.features)));
    return;
  }
  const marked = measureBoardMapUpdate(map, sourceId, layerIds, identify(changed), { removedIds: remove });
  // add는 같은 ID의 도형도 통째로 대체하므로 없어진 속성까지 이전 값이 남지 않는다.
  source.updateData({ remove, add: marked.features });
}
