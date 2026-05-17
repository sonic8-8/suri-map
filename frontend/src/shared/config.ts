export function getApiBaseUrl() {
  return import.meta.env.VITE_API_BASE_URL ?? '/api';
}

export function getKeycloakIssuerUrl() {
  return normalizeBaseUrl(
    import.meta.env.VITE_KEYCLOAK_ISSUER_URL ?? 'https://k14c106.p.ssafy.io/keycloak/realms/suri-map',
    'https://k14c106.p.ssafy.io/keycloak/realms/suri-map',
  );
}

export function getKeycloakAuthBaseUrl() {
  return normalizeKeycloakRealmUrl(
    import.meta.env.VITE_KEYCLOAK_BASE_URL ?? getKeycloakIssuerUrl(),
    getKeycloakIssuerUrl(),
  );
}

export function getKeycloakClientId() {
  return import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'suri-map-web';
}

export function isLocalDevLoginEnabled() {
  return import.meta.env.DEV && import.meta.env.VITE_ENABLE_LOCAL_DEV_LOGIN === 'true';
}

export function getLocalDevAccessToken() {
  return import.meta.env.VITE_LOCAL_DEV_ACCESS_TOKEN ?? 'dev-local-access-token';
}

export function isLocalDevAccessToken(accessToken: string | null | undefined) {
  return Boolean(accessToken) && accessToken === getLocalDevAccessToken();
}

export function getTileBaseUrl() {
  return normalizePublicTileBaseUrl(import.meta.env.VITE_TILE_BASE_URL ?? '/tiles');
}

function normalizeBaseUrl(baseUrl: string, fallback: string) {
  const normalized = baseUrl.trim().replace(/\/+$/, '');
  return normalized || fallback;
}

function normalizeKeycloakRealmUrl(baseUrl: string, fallback: string) {
  const normalized = normalizeBaseUrl(baseUrl, fallback);
  if (normalized.endsWith('/realms/suri-map')) {
    return normalized;
  }

  if (normalized.endsWith('/keycloak')) {
    return `${normalized}/realms/suri-map`;
  }

  return normalized;
}

function normalizePublicTileBaseUrl(baseUrl: string) {
  const normalized = baseUrl.trim().replace(/\/+$/, '');
  if (!normalized) {
    return '/tiles';
  }

  if (/^https?:\/\//.test(normalized)) {
    const pathname = new URL(normalized).pathname.replace(/\/+$/, '');
    return pathname === '/tiles' ? pathname : '/tiles';
  }

  return normalized === '/tiles' ? normalized : '/tiles';
}
