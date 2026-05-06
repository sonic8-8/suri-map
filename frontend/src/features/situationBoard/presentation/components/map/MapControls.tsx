import { LocateFixed, Maximize2, Minus, Plus } from 'lucide-react';

type MapControlsProps = {
  onZoomIn: () => void;
  onZoomOut: () => void;
};

export function MapControls({ onZoomIn, onZoomOut }: MapControlsProps) {
  return (
    <div className="map-toolbar" aria-label="지도 도구">
      <button type="button" className="icon-button" title="현재 위치">
        <LocateFixed size={18} aria-hidden="true" />
      </button>
      <button type="button" className="icon-button" title="확대" onClick={onZoomIn}>
        <Plus size={18} aria-hidden="true" />
      </button>
      <button type="button" className="icon-button" title="축소" onClick={onZoomOut}>
        <Minus size={18} aria-hidden="true" />
      </button>
      <button type="button" className="icon-button" title="전체 화면">
        <Maximize2 size={18} aria-hidden="true" />
      </button>
    </div>
  );
}
