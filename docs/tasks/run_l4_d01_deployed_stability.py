#!/usr/bin/env python3
"""Collect L4-D01 deployed-device stability evidence.

This runner is intentionally evidence-oriented. It does not decide final PASS on
its own; it captures device, local Room DB, deployed route, and optional EC2
database evidence so the L4-D01 gate can be reviewed without relying on memory
or screenshots only.
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import shlex
import sqlite3
import subprocess
import sys
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable


DEFAULT_ADB = "/mnt/c/Users/SSAFY/AppData/Local/Android/Sdk/platform-tools/adb.exe"
DEFAULT_BACKEND_URL = "https://k14c106.p.ssafy.io"
DEFAULT_PACKAGE = "com.surimap"
DEFAULT_ACTIVITY = "com.surimap/.MainActivity"
DEFAULT_INCIDENT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001"
DEFAULT_POLICE_PHONE_ID = "00000000-0000-0000-0000-000000000101"


@dataclass(frozen=True)
class CommandResult:
    args: list[str]
    returncode: int
    stdout: str
    stderr: str


def now_slug() -> str:
    return datetime.now().astimezone().strftime("%Y%m%d-%H%M%S")


def iso_now() -> str:
    return datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")


def run(args: list[str], timeout: int = 30, check: bool = False) -> CommandResult:
    completed = subprocess.run(
        args,
        text=True,
        capture_output=True,
        timeout=timeout,
    )
    result = CommandResult(
        args=args,
        returncode=completed.returncode,
        stdout=completed.stdout.replace("\r\n", "\n"),
        stderr=completed.stderr.replace("\r\n", "\n"),
    )
    if check and result.returncode != 0:
        joined = " ".join(shlex.quote(part) for part in args)
        raise RuntimeError(
            f"command failed ({result.returncode}): {joined}\n"
            f"stdout:\n{result.stdout}\n"
            f"stderr:\n{result.stderr}"
        )
    return result


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def write_json(path: Path, value: object) -> None:
    write_text(path, json.dumps(value, ensure_ascii=False, indent=2) + "\n")


def adb(args: Iterable[str], *, adb_path: str, serial: str, timeout: int = 30, check: bool = False) -> CommandResult:
    return run([adb_path, "-s", serial, *args], timeout=timeout, check=check)


def adb_shell(command: str, *, adb_path: str, serial: str, timeout: int = 30, check: bool = False) -> CommandResult:
    return adb(["shell", command], adb_path=adb_path, serial=serial, timeout=timeout, check=check)


def append_command_log(path: Path, label: str, result: CommandResult) -> None:
    command = " ".join(shlex.quote(part) for part in result.args)
    with path.open("a", encoding="utf-8") as fp:
        fp.write(f"\n## {label}\n")
        fp.write(f"time: {iso_now()}\n")
        fp.write(f"command: {command}\n")
        fp.write(f"exit: {result.returncode}\n")
        if result.stdout:
            fp.write("\nstdout:\n")
            fp.write(result.stdout)
            if not result.stdout.endswith("\n"):
                fp.write("\n")
        if result.stderr:
            fp.write("\nstderr:\n")
            fp.write(result.stderr)
            if not result.stderr.endswith("\n"):
                fp.write("\n")


def selected_device(adb_path: str, serial: str | None) -> str:
    if serial:
        state = run([adb_path, "-s", serial, "get-state"], timeout=15, check=True).stdout.strip()
        if state != "device":
            raise RuntimeError(f"adb device {serial} is not ready: {state}")
        return serial

    output = run([adb_path, "devices"], timeout=15, check=True).stdout.splitlines()
    devices = [line.split()[0] for line in output[1:] if line.strip().endswith("\tdevice")]
    physical = [device for device in devices if not device.startswith("emulator-")]
    if len(physical) != 1:
        raise RuntimeError(f"expected exactly one physical device, found {physical}")
    return physical[0]


def capture_device_snapshot(
    artifact_dir: Path,
    label: str,
    *,
    adb_path: str,
    serial: str,
    package_name: str,
) -> None:
    snap = artifact_dir / "device" / label
    snap.mkdir(parents=True, exist_ok=True)
    cmd_log = snap / "commands.md"

    commands = {
        "getprop-product-model": ["shell", "getprop ro.product.model"],
        "getprop-android-release": ["shell", "getprop ro.build.version.release"],
        "getprop-sdk": ["shell", "getprop ro.build.version.sdk"],
        "pidof-app": ["shell", f"pidof {shlex.quote(package_name)} || true"],
        "dumpsys-package": ["shell", f"dumpsys package {shlex.quote(package_name)} | sed -n '1,180p'"],
        "dumpsys-connectivity": ["shell", "dumpsys connectivity | sed -n '1,160p'"],
        "jobscheduler": ["shell", f"dumpsys jobscheduler {shlex.quote(package_name)} | sed -n '1,220p'"],
    }
    for name, command in commands.items():
        result = adb(command, adb_path=adb_path, serial=serial, timeout=30)
        append_command_log(cmd_log, name, result)
        write_text(snap / f"{name}.txt", result.stdout + result.stderr)

    adb_shell(
        "uiautomator dump /sdcard/surimap-window.xml >/dev/null 2>&1 || true",
        adb_path=adb_path,
        serial=serial,
        timeout=30,
    )
    xml = adb_shell("cat /sdcard/surimap-window.xml 2>/dev/null || true", adb_path=adb_path, serial=serial, timeout=30)
    write_text(snap / "window.xml", xml.stdout)

    completed = subprocess.run(
        [adb_path, "-s", serial, "exec-out", "screencap", "-p"],
        capture_output=True,
        timeout=30,
    )
    if completed.returncode == 0:
        (snap / "screen.png").write_bytes(completed.stdout)
    else:
        write_text(
            snap / "screen.png.err",
            completed.stderr.decode("utf-8", errors="replace"),
        )


def copy_room_db(
    artifact_dir: Path,
    label: str,
    *,
    adb_path: str,
    serial: str,
    package_name: str,
) -> Path | None:
    db_dir = artifact_dir / "local-db" / label
    db_dir.mkdir(parents=True, exist_ok=True)
    copied = False
    for suffix in ("", "-wal", "-shm"):
        remote = f"databases/suri-map.db{suffix}"
        completed = subprocess.run(
            [adb_path, "-s", serial, "exec-out", "run-as", package_name, "cat", remote],
            capture_output=True,
            timeout=30,
        )
        if completed.returncode == 0 and completed.stdout:
            (db_dir / f"suri-map.db{suffix}").write_bytes(completed.stdout)
            copied = True
        else:
            write_text(
                db_dir / f"suri-map.db{suffix}.missing.txt",
                completed.stderr.decode("utf-8", errors="replace"),
            )
    return db_dir / "suri-map.db" if copied else None


def table_exists(con: sqlite3.Connection, table: str) -> bool:
    row = con.execute(
        "select 1 from sqlite_master where type = 'table' and name = ?",
        (table,),
    ).fetchone()
    return row is not None


def columns(con: sqlite3.Connection, table: str) -> set[str]:
    return {row[1] for row in con.execute(f"pragma table_info({table})")}


def query_rows(con: sqlite3.Connection, sql: str) -> list[dict[str, object]]:
    con.row_factory = sqlite3.Row
    return [dict(row) for row in con.execute(sql).fetchall()]


def summarize_room_db(db_path: Path, output_path: Path) -> None:
    con = sqlite3.connect(db_path)
    summary: dict[str, object] = {"database": str(db_path), "tables": {}}
    for table in (
        "android_outbox_row",
        "local_marker",
        "local_write_draft",
        "offline_package_installation_status",
        "offline_package_item_status",
        "android_sync_status",
    ):
        if not table_exists(con, table):
            continue
        table_cols = columns(con, table)
        table_summary: dict[str, object] = {
            "count": con.execute(f"select count(*) from {table}").fetchone()[0],
            "columns": sorted(table_cols),
        }
        if table == "android_outbox_row":
            group_cols = [col for col in ("entity_type", "idempotency_status", "local_mirror_status") if col in table_cols]
            if group_cols:
                table_summary["status_counts"] = query_rows(
                    con,
                    "select "
                    + ", ".join(group_cols)
                    + ", count(*) as count from android_outbox_row group by "
                    + ", ".join(group_cols)
                    + " order by count desc, "
                    + ", ".join(group_cols),
                )
            table_summary["recent_rows"] = query_rows(
                con,
                "select * from android_outbox_row order by sequence desc, outbox_id desc limit 20",
            )
        elif table == "local_marker" and "sync_status" in table_cols:
            table_summary["status_counts"] = query_rows(
                con,
                "select sync_status, count(*) as count from local_marker group by sync_status order by count desc",
            )
            table_summary["recent_rows"] = query_rows(
                con,
                "select * from local_marker order by created_at_millis desc, local_marker_id desc limit 20",
            )
        elif "status" in table_cols:
            table_summary["status_counts"] = query_rows(
                con,
                f"select status, count(*) as count from {table} group by status order by count desc",
            )
        summary["tables"][table] = table_summary
    con.close()
    write_json(output_path, summary)


def parse_env_file(path: Path) -> dict[str, str]:
    env: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        env[key] = value
    return env


def ssh_psql(ec2_env: Path, sql: str, timeout: int = 60) -> CommandResult:
    env = parse_env_file(ec2_env)
    key = env.get("EC2_SSH_KEY")
    user = env.get("EC2_USER")
    host = env.get("EC2_HOST")
    if not key or not user or not host:
        raise RuntimeError(f"{ec2_env} must contain EC2_SSH_KEY, EC2_USER, and EC2_HOST")

    sql_b64 = base64.b64encode(sql.encode("utf-8")).decode("ascii")
    remote = (
        "set -euo pipefail; "
        f"SQL_B64={shlex.quote(sql_b64)}; "
        "docker exec -e SQL_B64=\"$SQL_B64\" suri-map-postgis sh -lc "
        + shlex.quote(
            "printf '%s' \"$SQL_B64\" | base64 -d > /tmp/l4d01-query.sql && "
            "psql -v ON_ERROR_STOP=1 -U \"$POSTGRES_USER\" -d \"$POSTGRES_DB\" "
            "-At -F '|' -f /tmp/l4d01-query.sql"
        )
    )
    return run(
        [
            "ssh",
            "-i",
            key,
            "-o",
            "StrictHostKeyChecking=no",
            f"{user}@{host}",
            remote,
        ],
        timeout=timeout,
    )


def duplicate_sql(incident_id: str, since_iso: str) -> str:
    incident = incident_id.replace("'", "''")
    since = since_iso.replace("'", "''")
    return f"""
with scoped_paths as (
  select sp.id,
         ds.police_phone_id,
         sp.started_at,
         sp.ended_at
  from search_path sp
  join duty_shift ds on ds.id = sp.duty_shift_id
  join operational_period op on op.id = ds.operational_period_id
  where op.incident_id = '{incident}'::uuid
    and sp.created_at >= '{since}'::timestamptz
)
select 'duplicate check search_path' as check_name,
       police_phone_id::text,
       started_at::text,
       ended_at::text,
       count(*)::text as duplicate_count
from scoped_paths
group by police_phone_id, started_at, ended_at
having count(*) > 1;

with scoped_paths as (
  select sp.id
  from search_path sp
  join duty_shift ds on ds.id = sp.duty_shift_id
  join operational_period op on op.id = ds.operational_period_id
  where op.incident_id = '{incident}'::uuid
    and sp.created_at >= '{since}'::timestamptz
)
select 'duplicate check search_path_segment' as check_name,
       search_path_id::text,
       movement_type::text,
       started_at::text,
       ended_at::text,
       count(*)::text as duplicate_count
from search_path_segment
where search_path_id in (select id from scoped_paths)
group by search_path_id, movement_type, started_at, ended_at
having count(*) > 1;

select 'duplicate check marker' as check_name,
       incident_id::text,
       operational_period_id::text,
       police_phone_id::text,
       marker_type::text,
       occurred_at::text,
       coalesce(memo, '') as memo,
       count(*)::text as duplicate_count
from marker
where incident_id = '{incident}'::uuid
  and created_at >= '{since}'::timestamptz
group by incident_id, operational_period_id, police_phone_id, marker_type, occurred_at, memo
having count(*) > 1;

select 'duplicate check event_dispatch_job' as check_name,
       source_entity_type::text,
       source_entity_id::text,
       event_type::text,
       count(*)::text as duplicate_count
from event_dispatch_job
where incident_id = '{incident}'::uuid
  and created_at >= '{since}'::timestamptz
group by source_entity_type, source_entity_id, event_type
having count(*) > 1;

select 'duplicate check idempotency_record' as check_name,
       idempotency_key::text,
       request_path::text,
       request_method::text,
       count(*)::text as duplicate_count
from idempotency_record
where incident_id = '{incident}'::uuid
  and created_at >= '{since}'::timestamptz
group by idempotency_key, request_path, request_method
having count(*) > 1;
"""


def server_counts_sql(incident_id: str, police_phone_id: str, since_iso: str) -> str:
    incident = incident_id.replace("'", "''")
    phone = police_phone_id.replace("'", "''")
    since = since_iso.replace("'", "''")
    return f"""
select 'incident_total' as name, count(*)::text as count
from incident
where id = '{incident}'::uuid;

select 'marker_total' as name, count(*)::text as count
from marker
where incident_id = '{incident}'::uuid;

select 'marker_since_run_start' as name, count(*)::text as count
from marker
where incident_id = '{incident}'::uuid
  and created_at >= '{since}'::timestamptz;

select 'search_path_total_for_phone' as name, count(*)::text as count
from search_path sp
join duty_shift ds on ds.id = sp.duty_shift_id
join operational_period op on op.id = ds.operational_period_id
where op.incident_id = '{incident}'::uuid
  and ds.police_phone_id = '{phone}'::uuid;

select 'search_path_since_run_start_for_phone' as name, count(*)::text as count
from search_path sp
join duty_shift ds on ds.id = sp.duty_shift_id
join operational_period op on op.id = ds.operational_period_id
where op.incident_id = '{incident}'::uuid
  and ds.police_phone_id = '{phone}'::uuid
  and sp.created_at >= '{since}'::timestamptz;

select 'event_dispatch_job_total' as name, count(*)::text as count
from event_dispatch_job
where incident_id = '{incident}'::uuid;

select 'event_dispatch_job_since_run_start' as name, count(*)::text as count
from event_dispatch_job
where incident_id = '{incident}'::uuid
  and created_at >= '{since}'::timestamptz;
"""


def capture_server_snapshot(
    artifact_dir: Path,
    label: str,
    *,
    ec2_env: Path | None,
    incident_id: str,
    police_phone_id: str,
    since_iso: str,
) -> None:
    if not ec2_env:
        write_text(artifact_dir / "server" / label / "skipped.txt", "EC2 env was not provided.\n")
        return
    server_dir = artifact_dir / "server" / label
    server_dir.mkdir(parents=True, exist_ok=True)
    for name, sql in {
        "counts": server_counts_sql(incident_id, police_phone_id, since_iso),
        "duplicates": duplicate_sql(incident_id, since_iso),
    }.items():
        result = ssh_psql(ec2_env, sql, timeout=60)
        write_text(server_dir / f"{name}.sql", sql)
        write_text(server_dir / f"{name}.out", result.stdout)
        write_text(server_dir / f"{name}.err", result.stderr)
        write_json(
            server_dir / f"{name}.meta.json",
            {"returncode": result.returncode, "capturedAt": iso_now()},
        )


def capture_local_snapshot(
    artifact_dir: Path,
    label: str,
    *,
    adb_path: str,
    serial: str,
    package_name: str,
) -> None:
    capture_device_snapshot(
        artifact_dir,
        label,
        adb_path=adb_path,
        serial=serial,
        package_name=package_name,
    )
    db_path = copy_room_db(
        artifact_dir,
        label,
        adb_path=adb_path,
        serial=serial,
        package_name=package_name,
    )
    if db_path:
        summarize_room_db(db_path, artifact_dir / "local-db" / label / "summary.json")


def device_network_probe(adb_path: str, serial: str) -> dict[str, object]:
    ping = adb_shell(
        "ping -c 1 -W 3 k14c106.p.ssafy.io >/dev/null 2>&1; echo $?",
        adb_path=adb_path,
        serial=serial,
        timeout=10,
    )
    connectivity = adb_shell(
        "dumpsys connectivity | grep -E 'VALIDATED|NetworkAgentInfo|mNetworkInfo|NetworkCapabilities' | sed -n '1,80p'",
        adb_path=adb_path,
        serial=serial,
        timeout=20,
    )
    return {
        "pingExit": ping.stdout.strip(),
        "pingCommandExit": ping.returncode,
        "connectivityExit": connectivity.returncode,
        "connectivity": connectivity.stdout,
    }


def set_device_network(enabled: bool, *, adb_path: str, serial: str) -> None:
    value = "enable" if enabled else "disable"
    adb_shell(f"svc wifi {value} || true", adb_path=adb_path, serial=serial, timeout=20)
    adb_shell(f"svc data {value} || true", adb_path=adb_path, serial=serial, timeout=20)


def launch_app(*, adb_path: str, serial: str, activity: str) -> None:
    adb_shell(f"am start -n {shlex.quote(activity)} >/dev/null", adb_path=adb_path, serial=serial, timeout=20)


def force_stop_app(*, adb_path: str, serial: str, package_name: str) -> None:
    adb_shell(f"am force-stop {shlex.quote(package_name)}", adb_path=adb_path, serial=serial, timeout=20)


def run_cycles(args: argparse.Namespace, artifact_dir: Path) -> list[dict[str, object]]:
    results: list[dict[str, object]] = []
    toggled_network = False
    try:
        for cycle in range(1, args.cycles + 1):
            cycle_label = f"cycle-{cycle:02d}"
            offline_seconds = args.first_offline_seconds if cycle == 1 else args.offline_seconds
            result: dict[str, object] = {
                "cycle": cycle,
                "startedAt": iso_now(),
                "offlineSeconds": offline_seconds,
            }

            capture_local_snapshot(
                artifact_dir,
                f"{cycle_label}-online-before",
                adb_path=args.adb,
                serial=args.device,
                package_name=args.package_name,
            )

            if not args.skip_network_toggle:
                set_device_network(False, adb_path=args.adb, serial=args.device)
                toggled_network = True
            time.sleep(args.offline_settle_seconds)
            result["offlineProbeStart"] = device_network_probe(args.adb, args.device)
            time.sleep(offline_seconds)

            capture_local_snapshot(
                artifact_dir,
                f"{cycle_label}-offline",
                adb_path=args.adb,
                serial=args.device,
                package_name=args.package_name,
            )

            if cycle == 1 and args.restart_app_during_first_offline:
                force_stop_app(adb_path=args.adb, serial=args.device, package_name=args.package_name)
                time.sleep(2)
                launch_app(adb_path=args.adb, serial=args.device, activity=args.activity)
                time.sleep(args.after_launch_seconds)
                capture_local_snapshot(
                    artifact_dir,
                    f"{cycle_label}-offline-after-restart",
                    adb_path=args.adb,
                    serial=args.device,
                    package_name=args.package_name,
                )

            if not args.skip_network_toggle:
                set_device_network(True, adb_path=args.adb, serial=args.device)
            time.sleep(args.online_wait_seconds)
            result["onlineProbe"] = device_network_probe(args.adb, args.device)

            capture_local_snapshot(
                artifact_dir,
                f"{cycle_label}-recovered",
                adb_path=args.adb,
                serial=args.device,
                package_name=args.package_name,
            )
            capture_server_snapshot(
                artifact_dir,
                f"{cycle_label}-recovered",
                ec2_env=args.ec2_env,
                incident_id=args.incident_id,
                police_phone_id=args.police_phone_id,
                since_iso=args.run_started_at,
            )
            result["finishedAt"] = iso_now()
            results.append(result)
            write_json(artifact_dir / "cycle-results.json", results)
    finally:
        if toggled_network:
            set_device_network(True, adb_path=args.adb, serial=args.device)
    return results


def run_stability_samples(args: argparse.Namespace, artifact_dir: Path) -> list[dict[str, object]]:
    samples: list[dict[str, object]] = []
    if args.total_stability_seconds <= 0:
        return samples

    deadline = time.monotonic() + args.total_stability_seconds
    index = 0
    while True:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            break
        label = f"stability-{index:02d}"
        capture_local_snapshot(
            artifact_dir,
            label,
            adb_path=args.adb,
            serial=args.device,
            package_name=args.package_name,
        )
        samples.append(
            {
                "sample": index,
                "capturedAt": iso_now(),
                "networkProbe": device_network_probe(args.adb, args.device),
            }
        )
        write_json(artifact_dir / "stability-samples.json", samples)
        index += 1
        time.sleep(min(args.stability_sample_seconds, max(0, remaining)))
    capture_local_snapshot(
        artifact_dir,
        f"stability-{index:02d}-final",
        adb_path=args.adb,
        serial=args.device,
        package_name=args.package_name,
    )
    return samples


def validate_backend_routes(backend_url: str) -> dict[str, object]:
    checks = {}
    for name, path in {
        "health": "/api/health",
        "oidc": "/keycloak/realms/suri-map/.well-known/openid-configuration",
        "tileStyle": "/tiles/styles/osm-local.json",
        "vectorTile": "/tiles/osm-local/13/6984/3172.pbf",
        "glyph": "/tiles/fonts/Pretendard%20GOV/0-255.pbf",
    }.items():
        result = run(["curl", "-sS", "-o", "/dev/null", "-w", "%{http_code}", backend_url.rstrip("/") + path], timeout=20)
        checks[name] = {"status": result.stdout.strip(), "returncode": result.returncode, "stderr": result.stderr}
    return checks


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default=os.environ.get("ADB", DEFAULT_ADB))
    parser.add_argument("--device", default=os.environ.get("DEVICE_SERIAL"))
    parser.add_argument("--backend-url", default=os.environ.get("BACKEND_URL", DEFAULT_BACKEND_URL))
    parser.add_argument("--package-name", default=DEFAULT_PACKAGE)
    parser.add_argument("--activity", default=DEFAULT_ACTIVITY)
    parser.add_argument("--incident-id", default=os.environ.get("INCIDENT_ID", DEFAULT_INCIDENT_ID))
    parser.add_argument("--police-phone-id", default=os.environ.get("POLICE_PHONE_ID", DEFAULT_POLICE_PHONE_ID))
    parser.add_argument("--artifact-dir", type=Path, default=None)
    parser.add_argument("--ec2-env", type=Path, default=None)
    parser.add_argument("--preflight-only", action="store_true")
    parser.add_argument("--skip-network-toggle", action="store_true")
    parser.add_argument("--cycles", type=int, default=10)
    parser.add_argument("--first-offline-seconds", type=int, default=30 * 60)
    parser.add_argument("--offline-seconds", type=int, default=60)
    parser.add_argument("--offline-settle-seconds", type=int, default=5)
    parser.add_argument("--online-wait-seconds", type=int, default=120)
    parser.add_argument("--after-launch-seconds", type=int, default=10)
    parser.add_argument("--restart-app-during-first-offline", action="store_true", default=True)
    parser.add_argument("--no-restart-app-during-first-offline", action="store_false", dest="restart_app_during_first_offline")
    parser.add_argument("--total-stability-seconds", type=int, default=60 * 60)
    parser.add_argument("--stability-sample-seconds", type=int, default=10 * 60)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    args.run_started_at = iso_now()
    args.device = selected_device(args.adb, args.device)
    artifact_dir = args.artifact_dir or Path("docs/tasks/artifacts") / f"l4-d01-deployed-{now_slug()}"
    artifact_dir.mkdir(parents=True, exist_ok=True)

    route_checks = validate_backend_routes(args.backend_url)
    write_json(
        artifact_dir / "run-metadata.json",
        {
            "task": "L4-D01",
            "jira": "S14P31C106-300",
            "startedAt": args.run_started_at,
            "backendUrl": args.backend_url,
            "device": args.device,
            "packageName": args.package_name,
            "incidentId": args.incident_id,
            "policePhoneId": args.police_phone_id,
            "cycles": args.cycles,
            "firstOfflineSeconds": args.first_offline_seconds,
            "offlineSeconds": args.offline_seconds,
            "totalStabilitySeconds": args.total_stability_seconds,
            "skipNetworkToggle": args.skip_network_toggle,
            "routeChecks": route_checks,
        },
    )

    capture_local_snapshot(
        artifact_dir,
        "preflight",
        adb_path=args.adb,
        serial=args.device,
        package_name=args.package_name,
    )
    capture_server_snapshot(
        artifact_dir,
        "preflight",
        ec2_env=args.ec2_env,
        incident_id=args.incident_id,
        police_phone_id=args.police_phone_id,
        since_iso=args.run_started_at,
    )

    if args.preflight_only:
        write_text(
            artifact_dir / "README.md",
            f"# L4-D01 Deployed Stability Evidence\n\nPreflight only at {iso_now()}.\n",
        )
        print(f"artifact_dir={artifact_dir}")
        return 0

    cycle_results = run_cycles(args, artifact_dir)
    stability_samples = run_stability_samples(args, artifact_dir)
    capture_server_snapshot(
        artifact_dir,
        "final",
        ec2_env=args.ec2_env,
        incident_id=args.incident_id,
        police_phone_id=args.police_phone_id,
        since_iso=args.run_started_at,
    )

    write_text(
        artifact_dir / "README.md",
        "\n".join(
            [
                "# L4-D01 Deployed Stability Evidence",
                "",
                f"- Captured at: {iso_now()}",
                f"- Backend: `{args.backend_url}`",
                f"- Device: `{args.device}`",
                f"- Incident: `{args.incident_id}`",
                f"- PolicePhone: `{args.police_phone_id}`",
                f"- Network cycles captured: `{len(cycle_results)}`",
                f"- Stability samples captured: `{len(stability_samples)}`",
                "",
                "This runner captures evidence only. Review local DB summaries, server duplicate",
                "query outputs, screenshots/XML, and logs before marking L4-D01 complete.",
                "",
            ]
        ),
    )
    print(f"artifact_dir={artifact_dir}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.TimeoutExpired as exc:
        print(f"TIMEOUT: {exc}", file=sys.stderr)
        raise SystemExit(124)
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
