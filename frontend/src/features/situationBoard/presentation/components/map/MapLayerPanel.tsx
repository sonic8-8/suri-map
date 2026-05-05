import { Layers } from 'lucide-react';
import { visibleLayers } from '../../constants/mockSituationBoard';

export function MapLayerPanel() {
  return (
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
  );
}
