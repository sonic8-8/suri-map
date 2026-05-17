import react from '@vitejs/plugin-react';
import path from 'node:path';
import { loadEnv } from 'vite';
import type { ProxyOptions } from 'vite';
import { defineConfig } from 'vitest/config';

export default defineConfig(({ mode }) => {
  const rootEnv = loadEnv(mode, path.resolve(process.cwd(), '..'), '');
  const frontendEnv = loadEnv(mode, process.cwd(), '');
  const env = { ...rootEnv, ...frontendEnv };
  const apiBaseUrl = env.VITE_API_BASE_URL ?? '/api';
  const apiProxyTarget = env.VITE_API_PROXY_TARGET ?? resolveDefaultApiProxyTarget(apiBaseUrl);
  const keycloakBaseUrl = env.VITE_KEYCLOAK_BASE_URL ?? 'http://localhost:18080/keycloak';
  const vWorldApiKey = env.V_WORLD_API_KEY ?? env.VITE_V_WORLD_API_KEY ?? '';
  const tileBaseUrl = env.VITE_TILE_BASE_URL ?? `${resolveApiProxyTarget(apiProxyTarget)}/tiles`;

  return {
    plugins: [react()],
    css: {
      postcss: {},
    },
    define: {
      __V_WORLD_API_KEY__: JSON.stringify(vWorldApiKey),
    },
    server: {
      port: 5174,
      strictPort: true,
      proxy: {
        '/api': {
          target: resolveApiProxyTarget(apiProxyTarget),
          changeOrigin: true,
          configure: stripBrowserBasicAuthChallenge,
        },
        '/tiles': {
          target: resolveTileProxyTarget(apiBaseUrl, tileBaseUrl),
          changeOrigin: true,
        },
        '/keycloak': {
          target: resolveKeycloakProxyTarget(keycloakBaseUrl),
          changeOrigin: false,
          configure: configureKeycloakProxy,
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

function resolveDefaultApiProxyTarget(apiBaseUrl: string) {
  if (/^https?:\/\//.test(apiBaseUrl)) {
    return apiBaseUrl;
  }

  return 'http://localhost:8080/api';
}

export function resolveTileProxyTarget(apiBaseUrl: string, tileBaseUrl: string) {
  if (/^https?:\/\//.test(tileBaseUrl)) {
    return tileBaseUrl.replace(/\/tiles\/?$/, '').replace(/\/+$/, '');
  }

  return resolveApiProxyTarget(apiBaseUrl);
}

export function resolveKeycloakProxyTarget(keycloakBaseUrl: string) {
  return keycloakBaseUrl.replace(/\/keycloak\/?$/, '').replace(/\/+$/, '');
}

const stripBrowserBasicAuthChallenge: NonNullable<ProxyOptions['configure']> = (proxy) => {
  proxy.on('proxyRes', (proxyRes) => {
    delete proxyRes.headers['www-authenticate'];
  });
};

const configureKeycloakProxy: NonNullable<ProxyOptions['configure']> = (proxy) => {
  proxy.on('proxyReq', (proxyReq, req) => {
    const browserHost = getRequestHost(req.headers.host);

    proxyReq.setHeader('x-forwarded-host', browserHost);
    proxyReq.setHeader('x-forwarded-port', getRequestPort(browserHost));
    proxyReq.setHeader('x-forwarded-proto', 'http');
  });

  proxy.on('proxyRes', (proxyRes) => {
    delete proxyRes.headers['www-authenticate'];
  });
};

function getRequestHost(hostHeader: string | string[] | undefined) {
  if (typeof hostHeader === 'string' && hostHeader.trim()) {
    return hostHeader.trim();
  }

  return 'localhost:5174';
}

function getRequestPort(host: string) {
  const separatorIndex = host.lastIndexOf(':');
  if (separatorIndex === -1) {
    return '80';
  }

  return host.slice(separatorIndex + 1);
}
