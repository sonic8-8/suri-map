#!/usr/bin/env python3
"""L4-D01 downscoped physical stability evidence gate check."""

from __future__ import annotations

import re
import sys
from pathlib import Path


TARGET = Path(__file__).with_name("l4-d01-stability-evidence.md")


def compact(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def contains_all(text: str, fragments: list[str]) -> bool:
    lowered = text.lower()
    return all(fragment.lower() in lowered for fragment in fragments)


def regex(text: str, pattern: str) -> bool:
    return re.search(pattern, text, re.IGNORECASE | re.DOTALL) is not None


def main() -> int:
    failures: list[str] = []

    if not TARGET.exists():
        failures.append(f"missing required evidence document: {TARGET}")
        return report(failures)

    text = compact(TARGET.read_text(encoding="utf-8"))
    required = {
        "task metadata": ["Task", "L4-D01"],
        "jira references": ["S14P31C106-196", "S14P31C106-300"],
        "required sources": [
            "docs/prd.md §2.2",
            "docs/prd.md §2.3",
            "docs/spec/harness-scenarios.md §2 SC-05",
            "docs/spec/harness-scenarios.md §2 SC-07",
            "docs/spec/harness-scenarios.md §2 SC-09",
        ],
        "downscope decision": [
            "downscoped",
            "2026-05-14",
            "user approval",
            "Android PolicePhone 1대",
            "board 1개",
        ],
        "physical completion criteria": [
            "Android PolicePhone 1대",
            "board 1개",
            "1시간",
            "network on/off 10회",
            "duplicate server row 0건",
            "convergence evidence",
        ],
        "checked evidence sinks": [
            "attachment 0건",
            "comment 0건",
            "worklog 0건",
            "docs/tasks 하위",
            "final evidence 문서 없음",
        ],
        "device availability": ["Windows ADB", "one physical Android device"],
        "merged follow-up defects": [
            "S14P31C106-307",
            "S14P31C106-308",
            "S14P31C106-310",
            "S14P31C106-312",
            "origin/develop",
            "fresh one-device",
        ],
        "downscoped attempt": [
            "2026-05-14 Downscoped Execution Attempt",
            "docs/tasks/artifacts/l4-d01-one-device-downscope-20260514/",
            "adb reverse tcp:8080 tcp:18080",
            "cmd jobscheduler run -f",
        ],
        "partial convergence evidence": [
            "Package local state",
            "ACKED/SYNCED",
            "Offline marker local state",
            "PENDING_SEND",
            "Marker recovery",
            "Board marker convergence",
        ],
        "not final blockers": [
            "Protocol duplicate event check",
            "FAIL/PARTIAL",
            "1-hour stability",
            "NOT EXECUTED",
            "Network on/off 10 cycles",
            "Only cycle 1 was executed",
        ],
        "partial verdict": ["Verdict", "PARTIAL / NOT FINAL"],
        "next action": [
            "docs/tasks/l4-network-switch-stability-protocol.md",
            "one physical Android PolicePhone",
            "validated network constraints",
        ],
        "runtime preflight": ["check_l4_d01_runtime_preflight.py", "one ready physical Android device"],
        "downscope risk": ["PolicePhone A/B divergence", "outside this final claim"],
    }

    for label, fragments in required.items():
        if not contains_all(text, fragments):
            failures.append(f"missing {label}: {', '.join(fragments)}")

    if not regex(text, r"Verdict\s*:\s*PARTIAL\s*/\s*NOT FINAL"):
        failures.append("current evidence must record Verdict: PARTIAL / NOT FINAL")

    if regex(text, r"Verdict\s*:\s*PASS"):
        failures.append("must not record PASS without physical 1-hour/on-off evidence")

    return report(failures)


def report(failures: list[str]) -> int:
    if not failures:
        print(f"PASS: {TARGET} records the L4-D01 downscope gate")
        return 0

    print(f"FAIL: {TARGET} is missing L4-D01 downscope gate details")
    for failure in failures:
        print(f"- {failure}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
