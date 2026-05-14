#!/usr/bin/env python3
"""Runtime preflight for the L4-D01 physical device gate.

The L4-D01 runtime gate was downscoped on 2026-05-14 to the available one
physical Android PolicePhone plus one board session. Emulators are still ignored
so the physical-device count cannot be satisfied by an AVD.
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
from dataclasses import dataclass


DEFAULT_MIN_PHYSICAL_DEVICES = 1


@dataclass(frozen=True)
class AdbDevice:
    serial: str
    state: str
    detail: str

    @property
    def is_ready_physical(self) -> bool:
        if self.state != "device":
            return False
        if self.serial.startswith("emulator-"):
            return False
        lowered = self.detail.lower()
        emulator_markers = (
            "model:sdk_gphone",
            "model:sdk_phone",
            "model:emulator",
            "device:generic",
            "product:sdk_gphone",
        )
        return not any(marker in lowered for marker in emulator_markers)


def parse_devices(output: str) -> list[AdbDevice]:
    devices: list[AdbDevice] = []
    for raw_line in output.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("List of devices"):
            continue
        parts = line.split(maxsplit=2)
        if len(parts) < 2:
            continue
        serial = parts[0]
        state = parts[1]
        detail = parts[2] if len(parts) == 3 else ""
        devices.append(AdbDevice(serial=serial, state=state, detail=detail))
    return devices


def run_adb_devices(adb: str) -> str:
    try:
        completed = subprocess.run(
            [adb, "devices", "-l"],
            check=True,
            capture_output=True,
            text=True,
        )
    except FileNotFoundError:
        print(f"FAIL: adb executable not found: {adb}", file=sys.stderr)
        return ""
    except subprocess.CalledProcessError as exc:
        print(f"FAIL: adb devices -l failed with exit code {exc.returncode}", file=sys.stderr)
        if exc.stderr:
            print(exc.stderr.strip(), file=sys.stderr)
        return ""
    return completed.stdout


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--adb",
        default=os.environ.get("ADB", "adb"),
        help="adb executable path. Defaults to ADB env var or 'adb'.",
    )
    parser.add_argument(
        "--min-physical-devices",
        type=int,
        default=DEFAULT_MIN_PHYSICAL_DEVICES,
        help="minimum physical Android device count required for L4-D01 run",
    )
    args = parser.parse_args()

    output = run_adb_devices(args.adb)
    if not output:
        return 1

    devices = parse_devices(output)
    physical_devices = [device for device in devices if device.is_ready_physical]
    emulator_or_blocked = [device for device in devices if not device.is_ready_physical]

    print(f"ADB: {args.adb}")
    print(f"ready physical devices: {len(physical_devices)}")
    for device in physical_devices:
        detail = f" {device.detail}" if device.detail else ""
        print(f"- physical {device.serial} {device.state}{detail}")

    if emulator_or_blocked:
        print("ignored devices:")
        for device in emulator_or_blocked:
            detail = f" {device.detail}" if device.detail else ""
            print(f"- ignored {device.serial} {device.state}{detail}")

    if len(physical_devices) < args.min_physical_devices:
        print(
            "BLOCKED: L4-D01 evidence requires "
            f"{args.min_physical_devices} physical Android devices; "
            f"found {len(physical_devices)}."
        )
        print("Emulators are ignored and do not count as physical PolicePhone evidence.")
        return 2

    print("PASS: L4-D01 physical Android device preflight satisfied.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
