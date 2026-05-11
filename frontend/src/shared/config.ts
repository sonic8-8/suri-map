export function getApiBaseUrl() {
  return import.meta.env.VITE_API_BASE_URL ?? '/api';
}

export function getTileBaseUrl() {
  return normalizeBaseUrl(import.meta.env.VITE_TILE_BASE_URL ?? '/tiles');
}

function normalizeBaseUrl(baseUrl: string) {
  const normalized = baseUrl.trim().replace(/\/+$/, '');
  return normalized || '/tiles';
}
