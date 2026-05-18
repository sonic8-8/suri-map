import { getStoredAccessToken } from '../api/client';
import { getTileBaseUrl } from '../config';

export const LOCAL_TILE_MAX_ZOOM = 19;

const LOCAL_TILE_STYLE_PATH = '/map-style/osm-local.json';
const LEGACY_LOCAL_TILE_STYLE_PATH = '/tiles/styles/osm-local.json';
const LOCAL_TILE_STYLE_ID = 'osm-local';
const LOCAL_TILE_LABEL_STYLE_ID = 'gwangju-building-labels';

export function getLocalTileStyleUrl() {
  return LOCAL_TILE_STYLE_PATH;
}

export function transformLocalTileRequest(url: string, resourceType?: string) {
  const tileBaseUrl = getTileBaseUrl();
  const tileBaseUrlObject = new URL(tileBaseUrl, window.location.origin);
  const tileBaseOrigin = tileBaseUrlObject.origin;
  const tileBasePath = tileBaseUrlObject.pathname.replace(/\/+$/, '') || '/tiles';
  const requestUrl = new URL(url, window.location.origin);
  const isAllowedTileOrigin = requestUrl.origin === tileBaseOrigin;

  if (!isAllowedTileOrigin) {
    throw new Error(`external tile host rejected: ${url}`);
  }

  if (resourceType === 'Style') {
    if (requestUrl.pathname === LOCAL_TILE_STYLE_PATH) {
      return { url, headers: buildWebTileRequestHeaders() };
    }

    if (requestUrl.pathname === LEGACY_LOCAL_TILE_STYLE_PATH) {
      return { url: LOCAL_TILE_STYLE_PATH, headers: buildWebTileRequestHeaders() };
    }

    throw new Error(`non-local tile style rejected: ${url}`);
  }

  if (resourceType === 'Tile') {
    const localVectorTilePathPattern = new RegExp(
      `^${escapeRegExp(tileBasePath)}/(?:${LOCAL_TILE_STYLE_ID}|${LOCAL_TILE_LABEL_STYLE_ID})/\\d+/\\d+/\\d+\\.pbf$`,
    );

    if (!localVectorTilePathPattern.test(requestUrl.pathname)) {
      throw new Error(`non-local tile rejected: ${url}`);
    }

    return { url, headers: buildWebTileRequestHeaders() };
  }

  if (resourceType === 'Glyphs') {
    const localGlyphPathPattern = new RegExp(`^${escapeRegExp(tileBasePath)}/fonts/[^/]+/\\d+-\\d+\\.pbf$`);

    if (!localGlyphPathPattern.test(requestUrl.pathname)) {
      throw new Error(`non-local glyph rejected: ${url}`);
    }

    return { url, headers: buildWebTileRequestHeaders() };
  }

  throw new Error(`non-local tile resource rejected: ${url}`);
}

function readBoardMapAccessToken() {
  return getStoredAccessToken() ?? '';
}

function buildWebTileRequestHeaders() {
  const accessToken = readBoardMapAccessToken();

  return {
    Authorization: accessToken ? (accessToken.startsWith('Bearer ') ? accessToken : `Bearer ${accessToken}`) : '',
    'X-Client-Channel': 'WEB',
  };
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
