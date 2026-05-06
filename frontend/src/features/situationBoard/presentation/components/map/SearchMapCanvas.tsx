import { useEffect, useRef } from 'react';
import maplibregl, { type StyleSpecification } from 'maplibre-gl';
import { getVWorldApiKey } from '../../../../../shared/config';
import styles from './SearchMapCanvas.module.css';

const GWANGJU_CENTER: [number, number] = [126.8526, 35.1595];
const V_WORLD_TILE_SIZE = 256;
const V_WORLD_MAX_ZOOM = 19;

function createVWorldBaseStyle(apiKey: string): StyleSpecification {
  return {
    version: 8,
    sources: {
      'vworld-base-raster': {
        type: 'raster',
        tiles: [`https://api.vworld.kr/req/wmts/1.0.0/${apiKey}/Base/{z}/{y}/{x}.png`],
        tileSize: V_WORLD_TILE_SIZE,
        maxzoom: V_WORLD_MAX_ZOOM,
        attribution: 'VWorld',
      },
    },
    layers: [
      {
        id: 'vworld-base-raster',
        type: 'raster',
        source: 'vworld-base-raster',
      },
    ],
  };
}

type SearchMapCanvasProps = {
  onMapReady?: (map: maplibregl.Map | null) => void;
};

export function SearchMapCanvas({ onMapReady }: SearchMapCanvasProps) {
  const mapContainerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!mapContainerRef.current) {
      return;
    }

    const vWorldApiKey = getVWorldApiKey();

    const map = new maplibregl.Map({
      container: mapContainerRef.current,
      style: createVWorldBaseStyle(vWorldApiKey),
      center: GWANGJU_CENTER,
      zoom: 13,
      maxZoom: V_WORLD_MAX_ZOOM,
      attributionControl: false,
    });

    map.addControl(new maplibregl.AttributionControl({ compact: true }), 'bottom-right');
    onMapReady?.(map);

    return () => {
      onMapReady?.(null);
      map.remove();
    };
  }, [onMapReady]);

  return (
    <div className={styles.surface} aria-label="수색 지도">
      <div ref={mapContainerRef} className={styles.canvas} />
    </div>
  );
}
