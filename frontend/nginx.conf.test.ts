import { readFileSync } from 'node:fs';
import { describe, expect, test } from 'vitest';

describe('frontend nginx runtime routing', () => {
  const nginxConfig = readFileSync('nginx.conf', 'utf8');

  test('uses relative redirects behind the EC2 TLS reverse proxy', () => {
    expect(nginxConfig).toContain('absolute_redirect off;');
  });

  test('proxies mock-112 public route before SPA fallback', () => {
    const mockRouteIndex = nginxConfig.indexOf('location /mock-112/');
    const fallbackIndex = nginxConfig.indexOf('try_files $uri $uri/ /index.html;');

    expect(mockRouteIndex).toBeGreaterThan(-1);
    expect(fallbackIndex).toBeGreaterThan(-1);
    expect(mockRouteIndex).toBeLessThan(fallbackIndex);
    expect(nginxConfig).toContain('proxy_pass http://mock-112:18112;');
    expect(nginxConfig).toContain('set $mock112_forwarded_proto $http_x_forwarded_proto;');
    expect(nginxConfig).toContain('proxy_set_header X-Forwarded-Host $host;');
    expect(nginxConfig).toContain('proxy_set_header X-Forwarded-Proto $mock112_forwarded_proto;');
    expect(nginxConfig).toContain('proxy_set_header X-Forwarded-Port $mock112_forwarded_port;');
  });

  test('proxies Keycloak public route before SPA fallback including admin console', () => {
    const keycloakRouteIndex = nginxConfig.indexOf('location /keycloak/ {');
    const fallbackIndex = nginxConfig.indexOf('try_files $uri $uri/ /index.html;');

    expect(keycloakRouteIndex).toBeGreaterThan(-1);
    expect(fallbackIndex).toBeGreaterThan(-1);
    expect(nginxConfig).toContain('return 308 /keycloak/;');
    expect(nginxConfig).not.toContain('location /keycloak/admin/');
    expect(keycloakRouteIndex).toBeLessThan(fallbackIndex);
    expect(nginxConfig).toContain('proxy_pass http://keycloak:8080;');
    expect(nginxConfig).not.toContain('X-Forwarded-Prefix');
  });
});
