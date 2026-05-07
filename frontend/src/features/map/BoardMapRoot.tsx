import maplibregl, { type StyleSpecification } from 'maplibre-gl';
import { useEffect, useRef } from 'react';
import { useBoardDisplayStore } from '../board/model/boardDisplayStore';

const bootstrapMapStyle: StyleSpecification = {
  version: 8,
  sources: {},
  layers: [
    {
      id: 'board-bootstrap-background',
      type: 'background',
      paint: {
        'background-color': '#e5ebe7',
      },
    },
  ],
};

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
      style: bootstrapMapStyle,
      center: [126.9565, 37.5712],
      zoom: 13,
      attributionControl: false,
    });

    map.addControl(new maplibregl.NavigationControl({ visualizePitch: true }), 'top-left');
    map.addControl(
      new maplibregl.AttributionControl({
        compact: true,
        customAttribution: 'OpenStreetMap contributors',
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
