import react from '@vitejs/plugin-react';
import { loadEnv } from 'vite';
import { defineConfig } from 'vitest/config';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const apiBaseUrl = env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';
  const tileBaseUrl = env.VITE_TILE_BASE_URL ?? `${resolveApiProxyTarget(apiBaseUrl)}/tiles`;

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: resolveApiProxyTarget(apiBaseUrl),
          changeOrigin: true,
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
      setupFiles: './src/features/board/test/setup.ts',
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
