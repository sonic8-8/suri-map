#!/usr/bin/env python3
"""Ops-side GPS/marker writers. Stop their container when browser permission expires."""

import argparse
import importlib.util
import json
import os
from pathlib import Path
import re
import secrets
import select
import shlex
import signal
import subprocess
import sys
import time
import threading
import uuid
from http.server import BaseHTTPRequestHandler, HTTPServer

IMAGE = "grafana/k6@sha256:e7eeddf1ce2361df6920d925297f487c0ba549c44be242c6a9c22f28d9b08efa"
ROOT = Path("/srv/ops/k6")
LEASE_SECONDS = 5  # One permission per second; tolerate four missed ticks, not a product SLA.


def read_permission(fd, timeout):
    if not select.select([fd], [], [], max(0, timeout))[0]:
        raise RuntimeError("browser_permission_expired")
    data = os.read(fd, 4096)
    if not data:
        raise RuntimeError("browser_disconnected")
    return data


def fresh_permission(fd, pending=b"", lease_seconds=LEASE_SECONDS):
    # Buffered heartbeats from preparation cannot authorize a new start.
    while select.select([fd], [], [], 0)[0]:
        pending += read_permission(fd, 0)
        if len(pending) > 4096:
            raise RuntimeError("invalid_browser_permission")
    if any(line != b"CONTINUE" for line in pending.split(b"\n")[:-1]):
        raise RuntimeError("browser_requested_stop")
    pending = pending.split(b"\n")[-1]
    discard_partial = bool(pending)
    deadline = time.monotonic() + lease_seconds
    while True:
        pending += read_permission(fd, deadline - time.monotonic())
        if len(pending) > 4096:
            raise RuntimeError("invalid_browser_permission")
        lines = pending.split(b"\n")
        pending = lines.pop()
        if any(line != b"CONTINUE" for line in lines):
            raise RuntimeError("browser_requested_stop")
        if discard_partial and lines:
            lines = lines[1:]
            discard_partial = False
        if lines:
            return time.monotonic() + lease_seconds, pending


def supervise(process, fd, lease_seconds=LEASE_SECONDS, pending=b"", stopped=None, permission_deadline=None):
    deadline = permission_deadline if permission_deadline is not None else time.monotonic() + lease_seconds
    while process.poll() is None:
        if time.monotonic() >= deadline:
            raise RuntimeError("browser_permission_expired")
        if stopped is not None and stopped.is_set():
            raise RuntimeError("marker_safety_check_failed")
        while b"\n" in pending:
            line, pending = pending.split(b"\n", 1)
            if line != b"CONTINUE":
                raise RuntimeError("browser_requested_stop")
            deadline = time.monotonic() + lease_seconds
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise RuntimeError("browser_permission_expired")
        if not select.select([fd], [], [], min(0.1, remaining))[0]:
            continue
        pending += read_permission(fd, 0)
        if len(pending) > 4096:
            raise RuntimeError("invalid_browser_permission")
    if process.returncode != 0:
        raise RuntimeError("write_scenario_failed")


def docker(*args, timeout=15):
    return subprocess.run(
        ["docker", *args], check=True, capture_output=True, text=True, timeout=timeout,
    ).stdout.strip()


def check_marker_safety(app_host, expected_backend=None):
    command = "python3 /srv/apps/suri-map/infra/k6/prepare-situation-board-account.py check-marker-safety"
    if expected_backend:
        command += " --expected-backend " + shlex.quote(expected_backend)
    checked = subprocess.run(
        ["ssh", "-T", "-o", "BatchMode=yes", "-o", "ConnectTimeout=5", app_host, command],
        check=True, capture_output=True, text=True, timeout=10,
    )
    report = json.loads(checked.stdout)
    identity = report.get("backendIdentity")
    if (report.get("fcmProvider") != "mock" or report.get("activeFcmTokens") != 0
            or report.get("incidentOpen") is not True or report.get("periodActive") is not True
            or not isinstance(identity, str) or not re.fullmatch(r"[a-f0-9]{64}/[0-9TZ:.+-]+", identity)
            or (expected_backend is not None and identity != expected_backend)):
        raise RuntimeError("marker_safety_check_failed")
    return identity


def marker_safety_gate(marker_ids, key, check, stopped, log):
    remaining = set(marker_ids)

    class Handler(BaseHTTPRequestHandler):
        def setup(self):
            super().setup()
            self.connection.settimeout(2)

        def do_GET(self):
            marker_id = self.path.removeprefix("/marker-safety/")
            approved = False
            authorized = secrets.compare_digest(self.headers.get("X-Board-Control", ""), key)
            if authorized and self.path.startswith("/marker-safety/") and marker_id in remaining and not stopped.is_set():
                remaining.remove(marker_id)  # Never authorize a second send for the same request.
                try:
                    check()
                    approved = not stopped.is_set()
                except Exception:
                    stopped.set()
                log.write(json.dumps({"markerId": marker_id, "approved": approved, "wallTimeMs": time.time_ns() // 1_000_000}) + "\n")
                log.flush()
            if not approved:
                stopped.set()
            self.send_response(200 if approved else 409)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            try:
                self.wfile.write(json.dumps({"approved": approved, "markerId": marker_id if approved else None}).encode())
            except OSError:
                stopped.set()

        def log_message(self, *args):
            pass  # Do not log the control key or HTTP headers.

    server = HTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, kwargs={"poll_interval": 0.1}, daemon=True)
    thread.start()
    return server, thread


def run(run_id, seconds, app_host=None):
    # Paths, image and load conditions are fixed. Never evaluate a remote shell command from input.
    output = ROOT / "results" / f"board-{run_id}"
    output.mkdir(mode=0o700)
    result = {"result": "FAILED", "runId": run_id, "containerStopped": True}
    container_name = f"board-load-{run_id}"
    container_id = None
    process = None
    gate = None
    safety_log = None
    stopped = threading.Event()
    try:
        permission = b""
        deadline = time.monotonic() + LEASE_SECONDS
        while b"\n" not in permission:
            permission += read_permission(sys.stdin.fileno(), deadline - time.monotonic())
            if len(permission) > 4096:
                raise RuntimeError("invalid_browser_permission")
        first, pending = permission.split(b"\n", 1)
        if first != b"START":
            raise RuntimeError("start_permission_missing")
        docker("image", "inspect", IMAGE)  # No image pull during the measured run.
        if docker("ps", "--all", "--filter", f"name=^/{container_name}$", "--format", "{{.ID}}"):
            raise RuntimeError("writer_container_already_exists")
        extra = []
        script = "search-path-batch-load.js"
        if app_host:
            spec = importlib.util.spec_from_file_location("marker_fixtures", Path(__file__).with_name("prepare-situation-board-marker-fixtures.py"))
            fixtures = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(fixtures)
            fixture = fixtures.marker_fixture(str(uuid.uuid4()), (seconds + 9) // 10)
            with (output / "marker-fixture.json").open("x") as file:
                json.dump(fixture, file)
            backend = check_marker_safety(app_host)
            result["backendIdentity"] = backend
            safety_key = secrets.token_hex(32)
            safety_log = (output / "marker-safety.jsonl").open("x")
            gate, gate_thread = marker_safety_gate(
                [marker["id"] for marker in fixture["markers"]], safety_key,
                lambda: check_marker_safety(app_host, backend), stopped, safety_log,
            )
            script = "situation-board-write-load.js"
            extra = [
                "-v", f"{ROOT}/{script}:/scripts/{script}:ro",
                "-e", "BOARD_MARKER_FIXTURE=/results/marker-fixture.json",
                "-e", f"BOARD_MARKER_SAFETY_URL=http://127.0.0.1:{gate.server_port}/marker-safety",
                "-e", f"BOARD_MARKER_SAFETY_KEY={safety_key}",
            ]
        result["containerStopped"] = False
        container_id = docker(
            "create", "--network", "host", "--user", "0:0",
            "--name", container_name,
            "--label", f"surimap.board-run={run_id}",
            "-v", f"{ROOT}/search-path-batch-load.js:/scripts/search-path-batch-load.js:ro",
            "-v", f"{ROOT}/secrets/path-append-requester-fixtures.json:/secrets/path-append-requester-fixtures.json:ro",
            "-v", f"{output}:/results",
            "-e", f"RUN_ID={run_id}", "-e", "BOARD_MEASUREMENT=true",
            "-e", "SCENARIO=prepopulated-long-path", "-e", "POLICE_PHONE_COUNT=468",
            "-e", "BASE_URL=https://suri-map.sonic8-8.com",
            "-e", "PREPOPULATED_LONG_PATH_REQUEST_RATE=31",
            "-e", f"PREPOPULATED_LONG_PATH_TEST_DURATION={seconds}s",
            "-e", "K6_PROMETHEUS_RW_SERVER_URL=http://127.0.0.1:9090/api/v1/write",
            "-e", "K6_PROMETHEUS_RW_TREND_STATS=p(50),p(95),p(99),min,max",
            *extra, IMAGE, "run", "--quiet", "--no-color", "--log-format=raw",
            "--console-output=/results/write-requests.jsonl", "-o", "experimental-prometheus-rw",
            f"/scripts/{script}",
        )
        if not re.fullmatch(r"[a-f0-9]{64}", container_id):
            container_id = None
            raise RuntimeError("invalid_container_identity")
        result["containerId"] = container_id
        result["containerStopped"] = False
        with (output / "k6.log").open("x") as log:
            permission_deadline, pending = fresh_permission(sys.stdin.fileno(), pending)
            docker("start", container_id, timeout=LEASE_SECONDS)
            process = subprocess.Popen(
                ["docker", "wait", container_id],
                stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=log, text=True,
            )
            try:
                supervise(process, sys.stdin.fileno(), pending=pending, stopped=stopped, permission_deadline=permission_deadline)
                if process.communicate(timeout=5)[0].strip() != "0":
                    raise RuntimeError("write_scenario_failed")
                if stopped.is_set():
                    raise RuntimeError("marker_safety_check_failed")
                result["result"] = "COLLECTED"
            finally:
                stopped.set()
                # Stop the exact container, not just the local docker/SSH client.
                docker("stop", "--time", "2", container_id)
                result["containerStopped"] = docker("inspect", "--format", "{{.State.Running}}", container_id) == "false"
                if not result["containerStopped"]:
                    raise RuntimeError("container_stop_unconfirmed")
                process.wait(timeout=10)
                log.write(docker("logs", container_id))
    except Exception as error:
        result["result"] = "FAILED"
        known_failures = {
            "browser_permission_expired", "browser_disconnected", "invalid_browser_permission",
            "browser_requested_stop", "write_scenario_failed", "start_permission_missing",
            "writer_container_already_exists", "invalid_container_identity", "container_stop_unconfirmed",
            "controller_interrupted", "marker_safety_check_failed",
        }
        result["failure"] = str(error) if str(error) in known_failures else "writer_or_control_failed"
        result["errorType"] = type(error).__name__
    finally:
        stopped.set()
        # A timed-out create command may still have created a container. Reconcile by its
        # reserved name and ownership label rather than assuming that no workload exists.
        if container_id is None and not result["containerStopped"]:
            try:
                existing = docker("ps", "--all", "--filter", f"name=^/{container_name}$", "--format", "{{.ID}}")
                if not existing:
                    result["containerStopped"] = True
                else:
                    metadata = json.loads(docker("inspect", container_name))[0]
                    if metadata["Config"]["Labels"].get("surimap.board-run") == run_id:
                        container_id = metadata["Id"]
                        result["containerId"] = container_id
                    else:
                        result["failure"] = "container_stop_unconfirmed"
            except Exception:
                result["failure"] = "container_stop_unconfirmed"
        if container_id and not result["containerStopped"]:
            try:
                docker("stop", "--time", "2", container_id)
                result["containerStopped"] = docker("inspect", "--format", "{{.State.Running}}", container_id) == "false"
            except Exception:
                result["failure"] = "container_stop_unconfirmed"
        if process and process.poll() is None:
            process.terminate()  # The container stop above is separate and must be confirmed.
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
        if container_id and result["containerStopped"]:
            try:
                docker("rm", container_id)
            except Exception:
                result["cleanupRequired"] = True
        if gate:
            gate.shutdown()
            gate.server_close()
            gate_thread.join(timeout=2)
        if safety_log:
            safety_log.close()
        for filename in ("write-requests.jsonl", "k6.log"):
            file = output / filename
            if file.exists():
                file.chmod(0o600)
        with (output / "writer-result.json").open("x") as file:
            json.dump(result, file)
        print(json.dumps(result), flush=True)
    return 0 if result["result"] == "COLLECTED" else 1


def self_check():
    for initial, fresh, allowed in ((b"", b"CONTINUE\nSTOP\n", False), (b"CONT", b"INUE\nCONTINUE\n", True)):
        read_fd, write_fd = os.pipe()
        os.write(write_fd, initial)
        sender = threading.Thread(target=lambda: (time.sleep(0.02), os.write(write_fd, fresh)))
        sender.start()
        try:
            try:
                fresh_permission(read_fd, lease_seconds=0.5)
                assert allowed
            except RuntimeError:
                assert not allowed
        finally:
            sender.join()
            os.close(read_fd)
            os.close(write_fd)
    read_fd, write_fd = os.pipe()
    try:
        os.write(write_fd, b"CONTINUE\n")
        try:
            fresh_permission(read_fd, lease_seconds=0.05)
            raise AssertionError("old heartbeat allowed startup")
        except RuntimeError as error:
            assert str(error) == "browser_permission_expired"
    finally:
        os.close(read_fd)
        os.close(write_fd)
    # Real child processes and OS pipes, but no Docker, network or service writes.
    for mode in ("stop", "disconnect", "expire", "failure", "complete"):
        read_fd, write_fd = os.pipe()
        code = "import time; time.sleep(5)"
        if mode == "failure":
            code = "raise SystemExit(7)"
        elif mode == "complete":
            code = "pass"
        child = subprocess.Popen([sys.executable, "-c", code])
        try:
            if mode == "stop":
                os.write(write_fd, b"CONTINUE\nSTOP\n")
            if mode == "disconnect":
                os.close(write_fd)
                write_fd = None
            try:
                supervise(child, read_fd, lease_seconds=0.5)
                assert mode == "complete"
            except RuntimeError:
                assert mode != "complete"
        finally:
            if child.poll() is None:
                child.terminate()
            child.wait(timeout=2)
            os.close(read_fd)
            if write_fd is not None:
                os.close(write_fd)
        assert child.poll() is not None
    import io
    from urllib.error import HTTPError
    from urllib.request import ProxyHandler, Request, build_opener

    local_http = build_opener(ProxyHandler({}))

    for mode in ("normal", "duplicate", "wrong-key", "unknown", "failed", "stopped"):
        stopped = threading.Event()
        calls = []

        def check():
            calls.append(1)
            if mode == "failed":
                raise RuntimeError("unsafe")

        log = io.StringIO()
        gate, thread = marker_safety_gate(["marker-1"], "test-key", check, stopped, log)
        try:
            if mode == "stopped":
                stopped.set()
            marker_id = "unknown" if mode == "unknown" else "marker-1"
            key = "wrong" if mode == "wrong-key" else "test-key"
            request = Request(f"http://127.0.0.1:{gate.server_port}/marker-safety/{marker_id}", headers={"X-Board-Control": key})
            for attempt in range(2 if mode == "duplicate" else 1):
                try:
                    response = local_http.open(request, timeout=2)
                except HTTPError as error:
                    response = error
                with response:
                    approved = json.load(response)["approved"]
                assert approved == (mode in ("normal", "duplicate") and attempt == 0)
            assert len(calls) == (1 if mode in ("normal", "duplicate", "failed") else 0)
            assert stopped.is_set() == (mode != "normal")
            assert "test-key" not in log.getvalue()
        finally:
            gate.shutdown()
            gate.server_close()
            thread.join(timeout=2)
    print("PASS: writer lifecycle and marker gate approval, duplicate, wrong key, unknown ID, failure, stop")


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-id")
    parser.add_argument("--seconds", type=int, default=300)
    parser.add_argument("--app-host", help="Ops SSH alias for the App safety check; enables marker writes")
    parser.add_argument("--self-check", action="store_true")
    args = parser.parse_args()
    if args.self_check:
        self_check()
        return 0
    if not args.run_id or not re.fullmatch(r"[A-Za-z0-9_-]{1,80}", args.run_id):
        parser.error("A fresh, safe --run-id is required")
    if not 1 <= args.seconds <= 300:
        parser.error("--seconds must be between 1 and 300")
    if args.app_host and not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]*", args.app_host):
        parser.error("--app-host must be a safe SSH alias")

    def interrupted(signum, frame):
        raise InterruptedError("controller_interrupted")

    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    return run(args.run_id, args.seconds, args.app_host)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception:
        print("Writer preparation failed; inspect the private result directory.", file=sys.stderr)
        sys.exit(1)
