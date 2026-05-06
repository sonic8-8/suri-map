import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

export default defineConfig(({ mode }) => {
  // 프론트 실행 위치와 루트 실행 위치가 달라질 수 있어 두 위치의 env를 함께 읽는다.
  const rootEnv = loadEnv(mode, path.resolve(process.cwd(), '..'), '');
  const frontendEnv = loadEnv(mode, process.cwd(), '');
  const env = { ...rootEnv, ...frontendEnv };
  const apiBaseUrl = env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';
  // TODO: 클라이언트 define 주입은 키가 웹에 노출되므로 VWorld 운영 키의 허용 도메인 제한을 확인한다.
  // VWorld는 공용 V_WORLD_API_KEY와 Vite 관례의 VITE_V_WORLD_API_KEY를 모두 허용한다.
  const vWorldApiKey = env.V_WORLD_API_KEY ?? env.VITE_V_WORLD_API_KEY ?? '';

  return {
    plugins: [react()],
    define: {
      // MapLibre raster style 생성 시 런타임 환경 변수 접근 없이 키를 참조한다.
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
  };
});

