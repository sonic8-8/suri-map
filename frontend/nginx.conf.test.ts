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
});
