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
          target: apiBaseUrl.replace(/\/api$/, ''),
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
