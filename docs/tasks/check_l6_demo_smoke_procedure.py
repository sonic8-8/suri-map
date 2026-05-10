#!/usr/bin/env python3
"""RED check for the L6 demo smoke procedure document.

This guard intentionally verifies only the procedure checklist. Final demo
evidence and pass/fail judgment belong to L6-D01.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path


TARGET = Path(__file__).with_name("l6-demo-smoke-procedure.md")


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
        failures.append(f"missing required procedure document: {TARGET}")
        return report(failures)

    raw = TARGET.read_text(encoding="utf-8")
    text = compact(raw)

    if not (
        regex(
            text,
            r"(final\s+pass/fail\s+evidence|pass/fail\s+evidence|최종\s*pass/fail|최종.{0,20}증거).{0,200}L6-D01",
        )
        or regex(
            text,
            r"L6-D01.{0,200}(final\s+pass/fail\s+evidence|pass/fail\s+evidence|최종\s*pass/fail|최종.{0,20}증거)",
        )
    ):
        failures.append("must state that final pass/fail evidence belongs to L6-D01")

    if not regex(
        text,
        r"(do\s+not\s+record|does\s+not\s+record|must\s+not\s+record|not\s+this\s+procedure|기록하지\s+않)",
    ):
        failures.append("must state this procedure does not record final pass/fail evidence")

    required_fragments = {
        "board/package/tile demo smoke checklist": [
            "board/package/tile demo smoke checklist"
        ],
        "local tile network capture plan": ["local tile network capture plan"],
        "terminal state convergence capture target": [
            "terminal state convergence capture target"
        ],
        "PRD §2.2 reference": ["PRD §2.2"],
        "PRD §2.3 reference": ["PRD §2.3"],
        "SC-03 reference": ["SC-03"],
        "SC-11 reference": ["SC-11"],
        "SC-12 reference": ["SC-12"],
        "incident fixture key": ["incidentId=inc-precinct-first-001"],
        "tile manifest fixture key": [
            "tileManifestId=tile-manifest-inc-precinct-001"
        ],
        "local tile blob URI": [
            "local://tiles/inc-precinct-first-001/{z}/{x}/{y}.pbf"
        ],
        "tile zoom range": ["z=15..16"],
        "tile x range": ["x=27925..27960"],
        "tile y range": ["y=12680..12720"],
        "overall area hash": ["overall-area-hash-precinct-current"],
        "manifest network script": ["net-script-manifest-001"],
        "tile blob network script": ["net-script-tile-blob-001"],
        "board refetch fixture": ["mock board API refetch/assembly"],
        "local /tiles capture": ["/tiles", "capture"],
        "incident terminal target": ["incident_terminal"],
        "package badge target": ["package_badge"],
        "tombstone response target": ["tombstone response"],
        "missing person absence target": ["missing_person absence"],
        "post-close SSE target": ["no live SSE resubscribe after close"],
    }

    for label, fragments in required_fragments.items():
        if not contains_all(text, fragments):
            failures.append(f"missing {label}: {', '.join(fragments)}")

    if not regex(
        text,
        r"(board\s+convergence.{0,120}SC-02.{0,120}SC-12|SC-02.{0,120}SC-12.{0,120}board\s+convergence)",
    ):
        failures.append("missing board convergence reference for SC-02 through SC-12")

    for host in (
        "*.tile.openstreetmap.org",
        "*.mapbox.com",
        "*.googleapis.com",
    ):
        if host not in text:
            failures.append(f"missing forbidden external tile host: {host}")

    if not regex(
        text,
        r"(forbid|forbidden|must\s+not|reject|fail|no\s+external|외부.{0,20}(금지|실패|차단))",
    ):
        failures.append("must forbid external tile hosts")

    return report(failures)


def report(failures: list[str]) -> int:
    if not failures:
        print(f"PASS: {TARGET} satisfies the L6 demo smoke procedure check")
        return 0

    print(f"FAIL: {TARGET} is missing or incomplete for L6-T10D")
    for failure in failures:
        print(f"- {failure}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
