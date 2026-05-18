import { afterEach, describe, expect, test, vi } from 'vitest';
import {
  getApiBaseUrl,
  getKeycloakAuthBaseUrl,
  getKeycloakClientId,
  getKeycloakIssuerUrl,
  getTileBaseUrl,
  isLocalDevLoginEnabled,
} from './config';

describe('shared environment config', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  test('returns default API and tile base paths', () => {
    vi.stubEnv('VITE_API_BASE_URL', undefined);
    vi.stubEnv('VITE_KEYCLOAK_CLIENT_ID', undefined);
    vi.stubEnv('VITE_KEYCLOAK_ISSUER_URL', undefined);
    vi.stubEnv('VITE_TILE_BASE_URL', undefined);

    expect(getApiBaseUrl()).toBe('/api');
    expect(getKeycloakClientId()).toBe('suri-map-web');
    expect(getKeycloakIssuerUrl()).toBe('https://k14c106.p.ssafy.io/keycloak/realms/suri-map');
    expect(getKeycloakAuthBaseUrl()).toBe('https://k14c106.p.ssafy.io/keycloak/realms/suri-map');
    expect(getTileBaseUrl()).toBe('/tiles');
  });

  test('normalizes tile base URL trailing slashes', () => {
    vi.stubEnv('VITE_TILE_BASE_URL', '/tiles/');

    expect(getTileBaseUrl()).toBe('/tiles');
  });

  test('normalizes Keycloak base URLs to the suri-map realm', () => {
    vi.stubEnv('VITE_KEYCLOAK_BASE_URL', 'https://k14c106.p.ssafy.io/keycloak');

    expect(getKeycloakAuthBaseUrl()).toBe('https://k14c106.p.ssafy.io/keycloak/realms/suri-map');

    vi.stubEnv('VITE_KEYCLOAK_BASE_URL', 'https://k14c106.p.ssafy.io/keycloak/realms/suri-map/');

    expect(getKeycloakAuthBaseUrl()).toBe('https://k14c106.p.ssafy.io/keycloak/realms/suri-map');
  });

  test('keeps browser tile requests on the public /tiles contract', () => {
    vi.stubEnv('VITE_TILE_BASE_URL', 'http://localhost:8082');

    expect(getTileBaseUrl()).toBe('/tiles');
  });

  test('uses SSO login by default and only enables local dev login explicitly', () => {
    vi.stubEnv('VITE_ENABLE_LOCAL_DEV_LOGIN', undefined);

    expect(isLocalDevLoginEnabled()).toBe(false);

    vi.stubEnv('VITE_ENABLE_LOCAL_DEV_LOGIN', 'true');

    expect(isLocalDevLoginEnabled()).toBe(true);
  });
});
