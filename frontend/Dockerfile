FROM node:22-alpine AS builder

WORKDIR /app

ARG V_WORLD_API_KEY=""

COPY package*.json ./
RUN npm ci

COPY . .
RUN V_WORLD_API_KEY="${V_WORLD_API_KEY}" npm run build

FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80
