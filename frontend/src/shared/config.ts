// TODO: VWorld 키가 웹 번들에 노출되는 구조이므로 도메인 제한 또는 서버 프록시 적용 여부를 확인한다.
// VWorld 키는 클라이언트 번들에서 직접 읽을 수 있도록 Vite define으로 주입한다.
export function getVWorldApiKey() {
  return __V_WORLD_API_KEY__;
}

export function getApiBaseUrl() {
  return import.meta.env.VITE_API_BASE_URL ?? '/api';
}

export function getKeycloakIssuerUrl() {
  return normalizeBaseUrl(import.meta.env.VITE_KEYCLOAK_ISSUER_URL ?? '/keycloak/realms/suri-map');
}

export function getKeycloakClientId() {
  return import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'suri-map-web';
}

export function isLocalDevLoginEnabled() {
  return import.meta.env.DEV && import.meta.env.VITE_ENABLE_LOCAL_DEV_LOGIN !== 'false';
}

export function getLocalDevAccessToken() {
  return import.meta.env.VITE_LOCAL_DEV_ACCESS_TOKEN ?? 'dev-local-access-token';
}

export function isLocalDevAccessToken(accessToken: string | null | undefined) {
  return Boolean(accessToken) && accessToken === getLocalDevAccessToken();
}

export function getTileBaseUrl() {
  return normalizeBaseUrl(import.meta.env.VITE_TILE_BASE_URL ?? '/tiles');
}

function normalizeBaseUrl(baseUrl: string) {
  const normalized = baseUrl.trim().replace(/\/+$/, '');
  return normalized || '/tiles';
}
