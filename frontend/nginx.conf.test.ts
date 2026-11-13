import { readFileSync } from 'node:fs';
import { describe, expect, test } from 'vitest';

describe('frontend nginx runtime routing', () => {
  const nginxConfig = readFileSync('nginx.conf', 'utf8');

  test('접근 로그에 요청마다 upstream 응답 헤더 수신, upstream 응답 수신, 전체 요청 시간이 남는다', () => {
    const start = nginxConfig.indexOf('log_format timing');
    const format = nginxConfig.slice(start, nginxConfig.indexOf(';', start));
    for (const field of ['$http_x_forwarded_for', 'msec=$msec', 'rt=$request_time', 'uht=$upstream_header_time', 'urt=$upstream_response_time']) {
      expect(format).toContain(field);
    }
    expect(nginxConfig).toContain('access_log /var/log/nginx/access.log timing;');
  });

  test('uses relative redirects behind the EC2 TLS reverse proxy', () => {
    expect(nginxConfig).toContain('absolute_redirect off;');
  });

  test('API와 타일 요청은 외부 HTTPS 정보를 보존하고 표준 Forwarded 헤더는 제거한다', () => {
    expect(nginxConfig).toContain('map $http_x_forwarded_proto $original_request_proto');
    expect(nginxConfig).toContain('map $http_x_forwarded_port $original_request_port');
    expect(nginxConfig).toContain('"" $scheme;');
    expect(nginxConfig).toContain('"" $server_port;');
    for (const route of ['/api/', '/tiles/']) {
      const start = nginxConfig.indexOf(`location ${route} {`);
      const block = nginxConfig.slice(start, nginxConfig.indexOf('\n    }', start));
      expect(block).toContain('proxy_set_header X-Forwarded-Proto $original_request_proto;');
      expect(block).toContain('proxy_set_header X-Forwarded-Port $original_request_port;');
      expect(block).toContain('proxy_set_header X-Forwarded-Host $host;');
      expect(block).toContain('proxy_set_header Forwarded "";');
    }
  });

  test('proxies mock-112 public route before SPA fallback', () => {
    const mockRouteIndex = nginxConfig.indexOf('location /mock-112/');
    const fallbackIndex = nginxConfig.indexOf('try_files $uri $uri/ /index.html;');

    expect(mockRouteIndex).toBeGreaterThan(-1);
    expect(fallbackIndex).toBeGreaterThan(-1);
    expect(mockRouteIndex).toBeLessThan(fallbackIndex);
    expect(nginxConfig).toContain('proxy_pass http://mock112;');
    expect(nginxConfig).toContain('set $mock112_forwarded_proto $http_x_forwarded_proto;');
    expect(nginxConfig).toContain('proxy_set_header X-Forwarded-Host $host;');
    expect(nginxConfig).toContain('proxy_set_header X-Forwarded-Proto $mock112_forwarded_proto;');
    expect(nginxConfig).toContain('proxy_set_header X-Forwarded-Port $mock112_forwarded_port;');
  });

  test('proxies S3-compatible object-storage bucket route before SPA fallback', () => {
    const uploadRouteIndex = nginxConfig.indexOf('location /suri-map-photo/');
    const fallbackIndex = nginxConfig.indexOf('try_files $uri $uri/ /index.html;');

    expect(uploadRouteIndex).toBeGreaterThan(-1);
    expect(fallbackIndex).toBeGreaterThan(-1);
    expect(uploadRouteIndex).toBeLessThan(fallbackIndex);
    expect(nginxConfig).toContain('return 308 /suri-map-photo/;');
    expect(nginxConfig).toContain('proxy_request_buffering off;');
    expect(nginxConfig).toContain('proxy_pass http://object_storage;');
  });

  test('proxies Keycloak public route before SPA fallback including admin console', () => {
    const keycloakRouteIndex = nginxConfig.indexOf('location /keycloak/ {');
    const fallbackIndex = nginxConfig.indexOf('try_files $uri $uri/ /index.html;');

    expect(keycloakRouteIndex).toBeGreaterThan(-1);
    expect(fallbackIndex).toBeGreaterThan(-1);
    expect(nginxConfig).toContain('return 308 /keycloak/;');
    expect(nginxConfig).not.toContain('location /keycloak/admin/');
    expect(keycloakRouteIndex).toBeLessThan(fallbackIndex);
    expect(nginxConfig).toContain('proxy_pass http://keycloak;');
    expect(nginxConfig).not.toContain('X-Forwarded-Prefix');
  });
});
