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
`VITE_KEYCLOAK_ISSUER_URL` defaults to `/keycloak/realms/suri-map`.
`VITE_KEYCLOAK_CLIENT_ID` defaults to `suri-map-web`.
`VITE_KEYCLOAK_BASE_URL` defaults to `http://localhost:18080/keycloak` for the Vite `/keycloak` dev proxy.
`VITE_TILE_BASE_URL` defaults to `/tiles`; the Vite dev server proxies `/tiles` to the backend tiles endpoint.
