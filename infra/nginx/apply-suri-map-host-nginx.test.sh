#!/usr/bin/env sh
set -eu

tmpdir="$(mktemp -d)"
trap 'rm -rf "${tmpdir}"' EXIT

site="${tmpdir}/suri-map"
cat > "${site}" <<'CONF'
server {
    listen 443 ssl http2;
    server_name k14c106.p.ssafy.io;

    location / {
        proxy_pass http://127.0.0.1:3000;
    }

    location = /api/incidents/events {
        proxy_pass http://127.0.0.1:8081;
        proxy_buffering off;
    }

    location ~ ^/api/incidents/[^/]+/events$ {
        proxy_pass http://127.0.0.1:8081;
        proxy_buffering off;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8081/api/;
    }
}
CONF

HOST_NGINX_SITE="${site}" \
HOST_NGINX_SSE_SNIPPET="infra/nginx/suri-map-sse.locations.conf" \
HOST_NGINX_SKIP_TEST_RELOAD=true \
sh infra/nginx/apply-suri-map-host-nginx.sh >/dev/null

grep -q "BEGIN Suri-Map SSE proxy" "${site}"
grep -q "location = /api/incidents/events" "${site}"
grep -q "proxy_buffering off;" "${site}"

HOST_NGINX_SITE="${site}" \
HOST_NGINX_SSE_SNIPPET="infra/nginx/suri-map-sse.locations.conf" \
HOST_NGINX_SKIP_TEST_RELOAD=true \
sh infra/nginx/apply-suri-map-host-nginx.sh >/dev/null

count="$(grep -c "BEGIN Suri-Map SSE proxy" "${site}")"
test "${count}" = "1"
exact_count="$(grep -c "location = /api/incidents/events" "${site}")"
test "${exact_count}" = "1"
regex_count="$(grep -Fc 'location ~ ^/api/incidents/[^/]+/events$' "${site}")"
test "${regex_count}" = "1"

echo "apply_suri_map_host_nginx_test=passed"
