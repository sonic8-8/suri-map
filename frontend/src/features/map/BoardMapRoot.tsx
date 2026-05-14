import maplibregl from 'maplibre-gl';
import { useEffect, useRef } from 'react';
import { getTileBaseUrl } from '../../shared/config';
import { useBoardDisplayStore } from '../board/model/boardDisplayStore';

const tileBaseUrl = getTileBaseUrl();
const tileBaseUrlObject = new URL(tileBaseUrl, window.location.origin);
const tileBaseOrigin = tileBaseUrlObject.origin;
const tileBasePath = tileBaseUrlObject.pathname.replace(/\/+$/, '') || '/tiles';
const boardMapStyleUrl = `${tileBaseUrl}/styles/osm-local.json`;
const boardMapStylePath = `${tileBasePath}/styles/osm-local.json`;
const gwangjuDemoCenter: [number, number] = [126.8481, 35.1603];
const gwangjuDemoZoom = 16;
const localVectorTilePathPattern = new RegExp(
  `^${escapeRegExp(tileBasePath)}/(?:osm-local|gwangju-building-labels)/\\d+/\\d+/\\d+\\.pbf$`,
);
const localGlyphPathPattern = new RegExp(`^${escapeRegExp(tileBasePath)}/fonts/[^/]+/\\d+-\\d+\\.pbf$`);
const browserAccessTokenStorageKeys = ['accessToken', 'access_token', 'suriMapAccessToken'];

function readBoardMapAccessToken() {
  for (const storage of [window.localStorage, window.sessionStorage]) {
    for (const storageKey of browserAccessTokenStorageKeys) {
      const token = storage.getItem(storageKey);

      if (token) {
        return token;
      }
    }
  }

  return '';
}

function buildWebTileRequestHeaders() {
  const accessToken = readBoardMapAccessToken();

  return {
    Authorization: accessToken ? (accessToken.startsWith('Bearer ') ? accessToken : `Bearer ${accessToken}`) : '',
    'X-Client-Channel': 'WEB',
  };
}

function transformLocalTileRequest(url: string, resourceType?: string) {
  const requestUrl = new URL(url, window.location.origin);
  const isAllowedTileOrigin = requestUrl.origin === tileBaseOrigin;

  if (!isAllowedTileOrigin) {
    throw new Error(`external tile host rejected: ${url}`);
  }

  if (resourceType === 'Style') {
    if (requestUrl.pathname === boardMapStylePath) {
      return { url, headers: buildWebTileRequestHeaders() };
    }

    throw new Error(`non-local tile style rejected: ${url}`);
  }

  if (resourceType === 'Tile') {
    if (!localVectorTilePathPattern.test(requestUrl.pathname)) {
      throw new Error(`non-local tile rejected: ${url}`);
    }

    return { url, headers: buildWebTileRequestHeaders() };
  }

  if (resourceType === 'Glyphs') {
    if (!localGlyphPathPattern.test(requestUrl.pathname)) {
      throw new Error(`non-local glyph rejected: ${url}`);
    }

    return { url, headers: buildWebTileRequestHeaders() };
  }

  throw new Error(`non-local tile resource rejected: ${url}`);
}

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
      style: boardMapStyleUrl,
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

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
