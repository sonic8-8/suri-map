import { useCallback, useEffect, useRef } from 'react';
import type maplibregl from 'maplibre-gl';
import type { LngLatBoundsLike } from 'maplibre-gl';
import { MapControls } from './MapControls';
import { MapLegend } from './MapLegend';
import { SearchMapCanvas } from './SearchMapCanvas';
import styles from './DashboardMapShell.module.css';

const INCIDENT_FIT_PADDING = 44;
const INCIDENT_FIT_MAX_ZOOM = 15;

type DashboardMapShellProps = {
  isMapExpanded: boolean;
  onToggleMapExpanded: () => void;
  selectedSearchAreaId: string | null;
  onSelectSearchArea: (searchAreaId: string) => void;
};

export function DashboardMapShell({
  isMapExpanded,
  onSelectSearchArea,
  onToggleMapExpanded,
  selectedSearchAreaId,
}: DashboardMapShellProps) {
  const mapRef = useRef<maplibregl.Map | null>(null);
  const initialBoundsRef = useRef<LngLatBoundsLike | null>(null);

  const handleMapReady = useCallback((map: maplibregl.Map | null) => {
    mapRef.current = map;
  }, []);

  const handleInitialBoundsReady = useCallback((bounds: LngLatBoundsLike | null) => {
    initialBoundsRef.current = bounds;
  }, []);

  const handleZoomIn = useCallback(() => {
    mapRef.current?.zoomIn();
  }, []);

  const handleZoomOut = useCallback(() => {
    mapRef.current?.zoomOut();
  }, []);

  const handleFitIncidentSearchArea = useCallback(() => {
    const map = mapRef.current;
    const bounds = initialBoundsRef.current;
    if (!map || !bounds) {
      return;
    }

    map.fitBounds(bounds, {
      padding: INCIDENT_FIT_PADDING,
      duration: 420,
      maxZoom: INCIDENT_FIT_MAX_ZOOM,
    });
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) {
      return;
    }

    map.resize();
    const resizeTimer = window.setTimeout(() => {
      map.resize();
    }, 220);

    return () => {
      window.clearTimeout(resizeTimer);
    };
  }, [isMapExpanded]);

  return (
    <div className={styles.layout}>
      <div className={styles.canvasShell}>
        <MapControls
          isMapExpanded={isMapExpanded}
          onFitIncidentSearchArea={handleFitIncidentSearchArea}
          onToggleMapExpanded={onToggleMapExpanded}
          onZoomIn={handleZoomIn}
          onZoomOut={handleZoomOut}
        />
        <SearchMapCanvas
          onInitialBoundsReady={handleInitialBoundsReady}
          onMapReady={handleMapReady}
          selectedSearchAreaId={selectedSearchAreaId}
          onSelectSearchArea={onSelectSearchArea}
        />
        <MapLegend />
      </div>
    </div>
  );
}
