import { useEffect, useRef } from 'react';
import maplibregl from 'maplibre-gl';
import { useBoardDisplayStore } from '../model/boardDisplayStore';

const BOARD_MAP_STYLE_URL = '/tiles/styles/osm-local.json';
const BOARD_MAP_CENTER: [number, number] = [126.9565, 37.5712];
const BOARD_MAP_ZOOM = 13;

export function DashboardMapShell() {
  const mapContainerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const incidentId = useBoardDisplayStore((state) => state.incidentId);

  useEffect(() => {
    if (!mapContainerRef.current) {
      return;
    }

    const map = new maplibregl.Map({
      container: mapContainerRef.current,
      style: BOARD_MAP_STYLE_URL,
      center: BOARD_MAP_CENTER,
      zoom: BOARD_MAP_ZOOM,
    });

    mapRef.current = map;

    return () => {
      mapRef.current = null;
      map.remove();
    };
  }, []);

  return <div ref={mapContainerRef} className="map-root" aria-label={`MapLibre board map ${incidentId}`} />;
}
