import { Layers, LocateFixed, Map, Maximize2, Minus, Plus } from 'lucide-react';

const visibleLayers = ['팀 경로', '구역', '마커', 'OP 이력'];

export function DashboardMapShell() {
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
        <div className="map-grid" />
        <div className="route-line route-line-a" />
        <div className="route-line route-line-b" />
        <span className="marker marker-a" />
        <span className="marker marker-b" />
        <span className="marker marker-c" />
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
        {visibleLayers.map((layer) => (
          <label key={layer} className="layer-row">
            <input type="checkbox" defaultChecked />
            <span>{layer}</span>
          </label>
        ))}
      </aside>
    </div>
  );
}
