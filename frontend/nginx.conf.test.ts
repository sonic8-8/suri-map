import { readFileSync } from 'node:fs';
import { describe, expect, test } from 'vitest';

describe('frontend nginx runtime routing', () => {
  const nginxConfig = readFileSync('nginx.conf', 'utf8');

  test('proxies mock-112 public route before SPA fallback', () => {
    const mockRouteIndex = nginxConfig.indexOf('location /mock-112/');
    const fallbackIndex = nginxConfig.indexOf('try_files $uri $uri/ /index.html;');

    expect(mockRouteIndex).toBeGreaterThan(-1);
    expect(fallbackIndex).toBeGreaterThan(-1);
    expect(mockRouteIndex).toBeLessThan(fallbackIndex);
    expect(nginxConfig).toContain('proxy_pass http://mock-112:18112;');
  });

  test('proxies Keycloak public route before SPA fallback while blocking admin console', () => {
    const keycloakRouteIndex = nginxConfig.indexOf('location /keycloak/ {');
    const keycloakAdminRouteIndex = nginxConfig.indexOf('location /keycloak/admin/ {');
    const fallbackIndex = nginxConfig.indexOf('try_files $uri $uri/ /index.html;');

    expect(keycloakRouteIndex).toBeGreaterThan(-1);
    expect(keycloakAdminRouteIndex).toBeGreaterThan(-1);
    expect(fallbackIndex).toBeGreaterThan(-1);
    expect(nginxConfig).toContain('return 308 /keycloak/realms/suri-map/account/;');
    expect(keycloakAdminRouteIndex).toBeLessThan(keycloakRouteIndex);
    expect(keycloakRouteIndex).toBeLessThan(fallbackIndex);
    expect(nginxConfig).toContain('proxy_pass http://keycloak:8080;');
    expect(nginxConfig).not.toContain('X-Forwarded-Prefix');
  });
});
