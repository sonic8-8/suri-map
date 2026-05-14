import react from '@vitejs/plugin-react';
import path from 'node:path';
import { loadEnv } from 'vite';
import { defineConfig } from 'vitest/config';

export default defineConfig(({ mode }) => {
  const rootEnv = loadEnv(mode, path.resolve(process.cwd(), '..'), '');
  const frontendEnv = loadEnv(mode, process.cwd(), '');
  const env = { ...rootEnv, ...frontendEnv };
  const apiBaseUrl = env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';
  const vWorldApiKey = env.V_WORLD_API_KEY ?? env.VITE_V_WORLD_API_KEY ?? '';
  const tileBaseUrl = env.VITE_TILE_BASE_URL ?? `${resolveApiProxyTarget(apiBaseUrl)}/tiles`;

  return {
    plugins: [react()],
    css: {
      postcss: {},
    },
    define: {
      __V_WORLD_API_KEY__: JSON.stringify(vWorldApiKey),
    },
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: resolveApiProxyTarget(apiBaseUrl),
          changeOrigin: true,
          configure: stripBrowserBasicAuthChallenge,
        },
        '/tiles': {
          target: resolveTileProxyTarget(apiBaseUrl, tileBaseUrl),
          changeOrigin: true,
        },
      },
    },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: './src/test/setup.ts',
    },
  };
});

export function resolveApiProxyTarget(apiBaseUrl: string) {
  return apiBaseUrl.replace(/\/api\/?$/, '').replace(/\/+$/, '');
}

export function resolveTileProxyTarget(apiBaseUrl: string, tileBaseUrl: string) {
  if (/^https?:\/\//.test(tileBaseUrl)) {
    return tileBaseUrl.replace(/\/tiles\/?$/, '').replace(/\/+$/, '');
  }

  return resolveApiProxyTarget(apiBaseUrl);
}

function stripBrowserBasicAuthChallenge(proxy: { on: (event: 'proxyRes', handler: (proxyRes: { headers: Record<string, unknown> }) => void) => void }) {
  proxy.on('proxyRes', (proxyRes) => {
    delete proxyRes.headers['www-authenticate'];
  });
}
