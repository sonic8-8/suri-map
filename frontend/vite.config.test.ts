import { describe, expect, test } from 'vitest';
import { resolveApiProxyTarget, resolveKeycloakProxyTarget, resolveTileProxyTarget } from './vite.config';

describe('vite dev proxy targets', () => {
  test('routes /api and default /tiles proxy to the same backend origin', () => {
    const apiBaseUrl = 'http://localhost:8080/api';

    expect(resolveApiProxyTarget(apiBaseUrl)).toBe('http://localhost:8080');
    expect(resolveTileProxyTarget(apiBaseUrl, 'http://localhost:8080/tiles')).toBe('http://localhost:8080');
  });

  test('keeps relative tile base URL on the API backend origin', () => {
    expect(resolveTileProxyTarget('http://localhost:8080/api', '/tiles')).toBe('http://localhost:8080');
  });

  test('allows a dedicated backend tile origin for development', () => {
    expect(resolveTileProxyTarget('http://localhost:8080/api', 'http://localhost:8081/tiles')).toBe(
      'http://localhost:8081',
    );
  });

  test('routes /keycloak proxy to the Keycloak origin', () => {
    expect(resolveKeycloakProxyTarget('http://localhost:18080/keycloak')).toBe('http://localhost:18080');
  });
});
