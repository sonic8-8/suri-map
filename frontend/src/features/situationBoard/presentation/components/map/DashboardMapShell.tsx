import { MapControls } from './MapControls';
import { MapLayerPanel } from './MapLayerPanel';
import { MapLegend } from './MapLegend';
import { SearchMapCanvas } from './SearchMapCanvas';

export function DashboardMapShell() {
  return (
    <div className="map-layout">
      <div className="map-canvas-shell">
        <MapControls />
        <SearchMapCanvas />
        <MapLegend />
      </div>
      <MapLayerPanel />
    </div>
  );
}
