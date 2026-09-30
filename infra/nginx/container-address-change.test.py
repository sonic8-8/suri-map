#!/usr/bin/env python3
"""Run: python3 infra/nginx/container-address-change.test.py [frontend/nginx.conf]

Requires local Docker, nginx:1.27.5-alpine and python:3.12-alpine.
Uses disposable containers only; no application data or production connections.
"""

import http.client
import json
from pathlib import Path
import subprocess
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from uuid import uuid4


def serve_upstream(version):
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def do_GET(self):
            if version == "old-address":
                self.send_error(404, "old container address")
                return
            if self.path == "/api/incidents/test/events":
                if self.headers.get("Last-Event-ID") != "41":
                    self.send_error(400, "missing Last-Event-ID")
                    return
                self.send_response(200)
                self.send_header("Content-Type", "text/event-stream")
                self.end_headers()
                self.wfile.write(f"id: 42\ndata: {version}\n\n".encode())
                self.wfile.flush()
                # 응답을 닫기 전에 이벤트를 받는지 확인한다.
                time.sleep(10)
                self.close_connection = True
                return
            payload = json.dumps({"version": version, "path": self.path,
                                  "headers": dict(self.headers)}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

        def log_message(self, *args):
            pass

    for port in (8080, 9000):
        server = ThreadingHTTPServer(("0.0.0.0", port), Handler)
        threading.Thread(target=server.serve_forever, daemon=True).start()
    ThreadingHTTPServer(("0.0.0.0", 18112), Handler).serve_forever()


def docker(*args):
    return subprocess.check_output(["docker", *args], text=True, timeout=30).strip()


def request(port, path, stream=False):
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=3)
    try:
        connection.request("GET", path, headers={
            "Host": "board.example.test", "X-Forwarded-Proto": "https",
            "X-Forwarded-Port": "443", "Last-Event-ID": "41",
            "Authorization": "Bearer test-only", "X-Client-Channel": "WEB",
            "Forwarded": "proto=http;host=untrusted.test",
        })
        response = connection.getresponse()
        if stream and response.status == 200:
            assert response.getheader("Content-Type") == "text/event-stream"
            body = b"".join(response.readline() for _ in range(3)).decode()
        else:
            body = response.read().decode()
        return response.status, body
    finally:
        connection.close()


def verify_routes(port, version):
    for path in ("/api/health?check=address", "/tiles/test?token=a%2Fb",
                 "/keycloak/realms/test", "/mock-112/health",
                 "/suri-map-photo/test?signature=a%2Fb"):
        status, body = request(port, path)
        assert status == 200, f"{path}: expected 200, got {status}: {body[:100]}"
        value = json.loads(body)
        assert value["version"] == version, value
        assert value["path"] == path, value
        photo = path.startswith("/suri-map-photo/")
        assert value["headers"]["X-Forwarded-Proto"] == ("http" if photo else "https"), value
        assert value["headers"]["X-Forwarded-Port"] == ("80" if photo else "443"), value
        assert value["headers"]["Authorization"] == "Bearer test-only", value
        if path.startswith(("/api/", "/tiles/")):
            assert "Forwarded" not in value["headers"], value
    status, body = request(port, "/api/incidents/test/events", stream=True)
    assert status == 200 and body == f"id: 42\ndata: {version}\n\n", (status, body)


def run_test(config):
    network = f"suri-map-nginx-test-{uuid4().hex[:12]}"
    containers = []
    docker("network", "create", "--subnet", "10.254.231.0/24", network)

    def start(image, *args, version=None):
        command = ["python", "/test.py", "--serve", version] if version else []
        container = docker("create", "--network", network, *args, image, *command)
        containers.append(container)
        docker("start", container)
        return container

    def published_port(container, port):
        return int(docker("port", container, f"{port}/tcp").rsplit(":", 1)[1])

    aliases = [argument for name in ("backend", "object-storage", "mock-112", "keycloak")
               for argument in ("--network-alias", name)]
    mount = ("-v", f"{Path(__file__).resolve()}:/test.py:ro")
    try:
        # Given: 운영과 같은 Nginx 설정으로 정상 HTTP·SSE 응답을 확인한다.
        backend = start("python:3.12-alpine", *mount, *aliases, version="before")
        old_ip = docker("inspect", "-f", "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}", backend)
        proxy = start("nginx:1.27.5-alpine", "-p", "127.0.0.1::80", "-v",
                      f"{config}:/etc/nginx/conf.d/default.conf:ro")
        docker("exec", proxy, "nginx", "-t")
        port = published_port(proxy, 80)
        deadline = time.monotonic() + 15
        while True:
            try:
                verify_routes(port, "before")
                break
            except (AssertionError, OSError):
                if time.monotonic() > deadline:
                    raise
                time.sleep(0.25)
        print("PASS: 주소 변경 전 HTTP·SSE 정상", flush=True)

        # When: 이전 IP는 404 서버가 사용하고 Backend는 다른 IP에서 정상 기동한다.
        docker("network", "disconnect", network, backend)
        start("python:3.12-alpine", *mount, "--ip", old_ip, version="old-address")
        replacement = start("python:3.12-alpine", *mount, *aliases,
                            "-p", "127.0.0.1::8080", version="after")
        new_ip = docker("inspect", "-f", "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}", replacement)
        assert old_ip != new_ip, (old_ip, new_ip)
        print(f"Backend IP: {old_ip} -> {new_ip}", flush=True)
        direct_port = published_port(replacement, 8080)
        # 15초는 검사 종료 한도이며 서비스 복구 SLA가 아니다.
        deadline = time.monotonic() + 15
        while True:
            try:
                status, body = request(direct_port, "/api/health")
                assert status == 200 and json.loads(body)["version"] == "after"
                break
            except (AssertionError, OSError):
                if time.monotonic() > deadline:
                    raise
                time.sleep(0.25)
        resolved = docker("exec", replacement, "python", "-c",
                          "import socket; print(socket.gethostbyname('backend'))")
        assert resolved == new_ip, (resolved, new_ip)
        print("PASS: 새 Backend 직접 요청 200·Docker DNS 새 주소 확인", flush=True)

        # Then: Nginx reload/restart 없이 새 주소로 HTTP·SSE 요청이 전달된다.
        deadline = time.monotonic() + 15
        started = time.monotonic()
        while True:
            try:
                verify_routes(port, "after")
                break
            except (AssertionError, OSError):
                if time.monotonic() > deadline:
                    for path in ("/api/health", "/tiles/test", "/keycloak/realms/test",
                                 "/mock-112/health", "/suri-map-photo/test",
                                 "/api/incidents/test/events"):
                        print(f"FAIL: {path} -> {request(port, path, stream=path.endswith('/events'))[0]}", flush=True)
                    raise
                time.sleep(0.5)
        print(f"PASS: 수동 reload 없이 HTTP·SSE 복구 ({time.monotonic() - started:.2f}s 관측)")
    finally:
        cleanup_commands = [("rm", "-f", container) for container in reversed(containers)]
        cleanup_commands.append(("network", "rm", network))
        cleanup_failed = False
        for command in cleanup_commands:
            try:
                docker(*command)
            except (subprocess.SubprocessError, OSError) as error:
                cleanup_failed = True
                print(f"Cleanup failed: {command}: {error}", file=sys.stderr)
        if cleanup_failed and sys.exc_info()[0] is None:
            raise RuntimeError("Docker cleanup incomplete; see failed resource names above")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--serve":
        serve_upstream(sys.argv[2])
    else:
        default = Path(__file__).resolve().parents[2] / "frontend/nginx.conf"
        run_test(Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else default)
