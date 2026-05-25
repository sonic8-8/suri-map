import maplibregl from 'maplibre-gl';
import { useEffect, useRef } from 'react';
import { getLocalTileStyleUrl, transformLocalTileRequest } from '../../shared/map/localTileMap';
import { useBoardDisplayStore } from '../board/model/boardDisplayStore';

const gwangjuDemoCenter: [number, number] = [126.8481, 35.1603];
const gwangjuDemoZoom = 16;

export function BoardMapRoot() {
  const mapContainerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const incidentId = useBoardDisplayStore((state) => state.incidentId);

  useEffect(() => {
    if (!mapContainerRef.current || mapRef.current) {
      return;
    }

    const map = new maplibregl.Map({
      container: mapContainerRef.current,
      style: getLocalTileStyleUrl(),
      center: gwangjuDemoCenter,
      zoom: gwangjuDemoZoom,
      attributionControl: false,
      transformRequest: transformLocalTileRequest,
    });

    map.addControl(new maplibregl.NavigationControl({ visualizePitch: true }), 'top-left');
    map.addControl(
      new maplibregl.AttributionControl({
        compact: true,
        customAttribution: 'OpenStreetMap contributors | OpenMapTiles',
      }),
      'bottom-right',
    );

    mapRef.current = map;

    return () => {
      map.remove();
      mapRef.current = null;
    };
  }, []);

  return <div ref={mapContainerRef} className="map-root" aria-label={`MapLibre board map ${incidentId}`} />;
}
