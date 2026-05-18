# Suri-Map Frontend

React web dashboard for Suri-Map.

## Stack

- React
- TypeScript
- Vite
- MapLibre GL JS
- TanStack Query
- Zustand

## Commands

```bash
npm i
npm run dev
npm run build
```

`VITE_API_BASE_URL` defaults to `/api`.
`VITE_KEYCLOAK_ISSUER_URL` defaults to `https://k14c106.p.ssafy.io/keycloak/realms/suri-map`.
`VITE_KEYCLOAK_CLIENT_ID` defaults to `suri-map-web`.
`VITE_KEYCLOAK_BASE_URL` defaults to `https://k14c106.p.ssafy.io/keycloak/realms/suri-map` for browser auth, token exchange, and logout.
`VITE_KEYCLOAK_PROXY_TARGET` defaults to `https://k14c106.p.ssafy.io/keycloak` for the optional Vite `/keycloak` dev proxy; normal SSO login does not use this proxy.
The local Vite dev server defaults to `http://127.0.0.1:5173`.
`/map-style/osm-local.json` is the public local style document; it still points at `/tiles` for vector tiles and glyphs.
`VITE_TILE_BASE_URL` defaults to `https://k14c106.p.ssafy.io/tiles` for the Vite dev proxy; browser code still uses the public `/tiles` path.
`VITE_ENABLE_LOCAL_DEV_LOGIN=true` opt-in enables the local development login bypass; SSO is the default.
