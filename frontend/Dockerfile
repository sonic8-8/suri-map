FROM node:22-alpine AS builder

WORKDIR /app

ARG V_WORLD_API_KEY=""
ARG VITE_KEYCLOAK_ISSUER_URL=""
ARG VITE_KEYCLOAK_BASE_URL=""
ARG SURI_MAP_KEYCLOAK_REALM_URL="https://suri-map.sonic8-8.com/keycloak/realms/suri-map"

COPY package*.json ./
RUN npm ci

COPY . .
RUN V_WORLD_API_KEY="${V_WORLD_API_KEY}" \
    VITE_KEYCLOAK_ISSUER_URL="${VITE_KEYCLOAK_ISSUER_URL:-$SURI_MAP_KEYCLOAK_REALM_URL}" \
    VITE_KEYCLOAK_BASE_URL="${VITE_KEYCLOAK_BASE_URL:-$SURI_MAP_KEYCLOAK_REALM_URL}" \
    npm run build

FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80
