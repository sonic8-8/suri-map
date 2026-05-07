import { Layers, LocateFixed, Map, Maximize2, Minus, Plus } from 'lucide-react';
import { boardLayerLabels, type BoardLayerKey, useBoardDisplayStore } from '../board/model/boardDisplayStore';
import { BoardMapRoot } from './BoardMapRoot';

const boardLayerKeys = Object.keys(boardLayerLabels) as BoardLayerKey[];

export function DashboardMapShell() {
  const visibleLayers = useBoardDisplayStore((state) => state.visibleLayers);
  const toggleLayer = useBoardDisplayStore((state) => state.toggleLayer);

  return (
    <div className="map-layout">
      <div className="map-toolbar" aria-label="지도 도구">
        <button type="button" className="icon-button" title="현재 위치">
          <LocateFixed size={18} aria-hidden="true" />
        </button>
        <button type="button" className="icon-button" title="확대">
          <Plus size={18} aria-hidden="true" />
        </button>
        <button type="button" className="icon-button" title="축소">
          <Minus size={18} aria-hidden="true" />
        </button>
        <button type="button" className="icon-button" title="전체 화면">
          <Maximize2 size={18} aria-hidden="true" />
        </button>
      </div>

      <div className="map-surface" aria-label="수색 지도">
        <BoardMapRoot />
        <div className="map-grid" />
        <div className="map-empty-state">
          <Map size={28} aria-hidden="true" />
          <span>MapLibre 연결 대기</span>
        </div>
      </div>

      <aside className="layer-panel" aria-label="지도 레이어">
        <div className="panel-title">
          <Layers size={18} aria-hidden="true" />
          <strong>레이어</strong>
        </div>
        {boardLayerKeys.map((layer) => (
          <label key={layer} className="layer-row">
            <input type="checkbox" checked={visibleLayers[layer]} onChange={() => toggleLayer(layer)} />
            <span>{boardLayerLabels[layer]}</span>
          </label>
        ))}
      </aside>
    </div>
  );
}
