#!/usr/bin/env sh
set -eu

host_root="${HOST_ROOT:-}"
site_path="${HOST_NGINX_SITE:-/etc/nginx/sites-available/suri-map}"
snippet_path="${HOST_NGINX_SSE_SNIPPET:-/home/ubuntu/infra/nginx/suri-map-sse.locations.conf}"
skip_test_reload="${HOST_NGINX_SKIP_TEST_RELOAD:-false}"
client_max_body_size="${HOST_NGINX_CLIENT_MAX_BODY_SIZE:-11m}"

site="${host_root}${site_path}"
snippet="${snippet_path}"
case "${snippet}" in
  /*) snippet="${host_root}${snippet_path}" ;;
esac

if [ ! -f "${site}" ]; then
  echo "host_nginx_site=missing path=${site}" >&2
  exit 1
fi

if [ ! -f "${snippet}" ]; then
  echo "host_nginx_sse_snippet=missing path=${snippet}" >&2
  exit 1
fi

tmp="$(mktemp)"
trap 'rm -f "${tmp}"' EXIT

awk -v snippet="${snippet}" -v client_max_body_size="${client_max_body_size}" '
  function brace_delta(value, copy, opened, closed) {
    copy = value
    opened = gsub(/\{/, "{", copy)
    copy = value
    closed = gsub(/\}/, "}", copy)
    return opened - closed
  }
  BEGIN {
    inserted = 0
    body_size_inserted = 0
    before_first_location = 1
    in_managed_block = 0
    in_legacy_sse_block = 0
    legacy_depth = 0
  }
  before_first_location && /^[[:space:]]*client_max_body_size[[:space:]]+/ {
    if (!body_size_inserted) {
      match($0, /^[[:space:]]*/)
      print substr($0, RSTART, RLENGTH) "client_max_body_size " client_max_body_size ";"
      body_size_inserted = 1
    }
    next
  }
  before_first_location && /^[[:space:]]*location[[:space:]]+/ {
    if (!body_size_inserted) {
      print "    client_max_body_size " client_max_body_size ";"
      print ""
      body_size_inserted = 1
    }
    before_first_location = 0
  }
  /^[[:space:]]*# BEGIN Suri-Map SSE proxy/ {
    in_managed_block = 1
    if (!inserted) {
      while ((getline line < snippet) > 0) {
        print line
      }
      close(snippet)
      inserted = 1
    }
    next
  }
  /^[[:space:]]*# END Suri-Map SSE proxy/ {
    in_managed_block = 0
    next
  }
  in_managed_block {
    next
  }
  in_legacy_sse_block {
    legacy_depth += brace_delta($0)
    if (legacy_depth <= 0) {
      in_legacy_sse_block = 0
    }
    next
  }
  /^[[:space:]]*location[[:space:]]*=[[:space:]]*\/api\/incidents\/events[[:space:]]*\{/ ||
  /^[[:space:]]*location[[:space:]]*~[[:space:]]*\^\/api\/incidents\/\[\^\/\]\+\/events\$/ {
    in_legacy_sse_block = 1
    legacy_depth = brace_delta($0)
    if (legacy_depth <= 0) {
      in_legacy_sse_block = 0
    }
    next
  }
  !inserted && /^[[:space:]]*location[[:space:]]+\/api\/[[:space:]]*\{/ {
    while ((getline line < snippet) > 0) {
      print line
    }
    close(snippet)
    print ""
    inserted = 1
  }
  {
    print
  }
  END {
    if (!inserted) {
      exit 42
    }
  }
' "${site}" > "${tmp}" || {
  status="$?"
  if [ "${status}" = "42" ]; then
    echo "host_nginx_api_location=missing path=${site}" >&2
  fi
  exit "${status}"
}

if cmp -s "${site}" "${tmp}"; then
  echo "host_nginx_sse_proxy=unchanged"
else
  backup="${site}.bak.$(date +%Y%m%d%H%M%S)"
  cp "${site}" "${backup}"
  cat "${tmp}" > "${site}"
  echo "host_nginx_sse_proxy=updated backup=${backup}"
fi

if [ "${skip_test_reload}" = "true" ]; then
  echo "host_nginx_test_reload=skipped"
  exit 0
fi

if [ -n "${host_root}" ]; then
  chroot "${host_root}" nginx -t
  chroot "${host_root}" nginx -s reload
else
  nginx -t
  nginx -s reload
fi

echo "host_nginx_reload=done"
