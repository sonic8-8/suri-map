import { useCallback, useRef } from 'react';
import type maplibregl from 'maplibre-gl';
import { MapControls } from './MapControls';
import { MapLegend } from './MapLegend';
import { SearchMapCanvas } from './SearchMapCanvas';
import styles from './DashboardMapShell.module.css';

export function DashboardMapShell() {
  const mapRef = useRef<maplibregl.Map | null>(null);

  const handleMapReady = useCallback((map: maplibregl.Map | null) => {
    mapRef.current = map;
  }, []);

  const handleZoomIn = useCallback(() => {
    mapRef.current?.zoomIn();
  }, []);

  const handleZoomOut = useCallback(() => {
    mapRef.current?.zoomOut();
  }, []);

  return (
    <div className={styles.layout}>
      <div className={styles.canvasShell}>
        <MapControls onZoomIn={handleZoomIn} onZoomOut={handleZoomOut} />
        <SearchMapCanvas onMapReady={handleMapReady} />
        <MapLegend />
      </div>
    </div>
  );
}
