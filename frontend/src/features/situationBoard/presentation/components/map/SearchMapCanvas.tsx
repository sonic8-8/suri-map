import { Map } from 'lucide-react';

export function SearchMapCanvas() {
  return (
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
  );
}
