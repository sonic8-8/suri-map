#!/usr/bin/env python3
"""RED check for the L6-D01 demo smoke evidence document.

This guard intentionally expects the future evidence sink to exist and to
record observable proof for the board/package/tile/terminal demo smoke. It
should fail before GREEN creates `l6-demo-smoke-evidence.md`.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path


TARGET = Path(__file__).with_name("l6-demo-smoke-evidence.md")
PROCEDURE = "docs/tasks/l6-demo-smoke-procedure.md"


def compact(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def contains_all(text: str, fragments: list[str]) -> bool:
    lowered = text.lower()
    return all(fragment.lower() in lowered for fragment in fragments)


def regex(text: str, pattern: str) -> bool:
    return re.search(pattern, text, re.IGNORECASE | re.DOTALL) is not None


def require_fragments(
    failures: list[str], text: str, requirements: dict[str, list[str]]
) -> None:
    for label, fragments in requirements.items():
        if not contains_all(text, fragments):
            failures.append(f"missing {label}: {', '.join(fragments)}")


def main() -> int:
    failures: list[str] = []

    if not TARGET.exists():
        failures.append(f"missing required evidence document: {TARGET}")
        return report(failures)

    raw = TARGET.read_text(encoding="utf-8")
    text = compact(raw)

    required_fragments = {
        "task metadata": ["Task", "L6-D01"],
        "Jira metadata": ["Jira", "S14P31C106-191"],
        "branch metadata": [
            "Branch",
            "feature/S14P31C106-191-board-package-tile-device-demo-evidence",
        ],
        "procedure reference": [PROCEDURE],
        "executed evidence commands section": ["Executed Evidence Commands"],
        "completion verdict section": ["Completion Verdict"],
        "PRD 2.2 context": ["PRD §2.2"],
        "PRD 2.3 context": ["PRD §2.3"],
        "12-step rehearsal context": ["12-step", "rehearsal"],
        "SC-03 coverage": ["SC-03"],
        "SC-11 coverage": ["SC-11"],
        "SC-12 coverage": ["SC-12"],
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
        "local tile capture key": ["local /tiles capture"],
        "external tile request count": ["external tile requests 0"],
        "forbidden OpenStreetMap tile host": ["*.tile.openstreetmap.org"],
        "forbidden Mapbox tile host": ["*.mapbox.com"],
        "forbidden Google APIs tile host": ["*.googleapis.com"],
        "package badge evidence": ["package_badge"],
        "local tile/package state evidence": ["local tile/package state displayed"],
        "board refetch fixture evidence": ["mock board API refetch/assembly"],
        "board slot convergence evidence": ["board slot convergence"],
        "SC-11 summary slot evidence": ["search_history_summary"],
        "SC-11 OP comparison evidence": ["OP comparison"],
        "SC-11 no recommendation guard": ["no recommendation"],
        "SC-11 missing-area certainty guard": [
            "no automatic missing-area certainty"
        ],
        "SC-11 risk judgment guard": ["no risk-judgment CTA/text"],
        "terminal slot evidence": ["incident_terminal"],
        "terminal package badge state": ["package_badge", "terminal/tombstone state"],
        "tombstone response evidence": ["tombstone response"],
        "missing person absence evidence": ["missing_person absence"],
        "post-close SSE guard": ["no live SSE resubscribe after close"],
        "sanitized terminal state evidence": ["sanitized terminal state"],
        "linked defect task list section": ["Linked Defect Task List"],
        "harness evidence disclaimer": ["harness/test-report evidence"],
        "no physical stability claim": ["not physical", "L4-D01"],
        "no browser proxy capture claim": ["No browser/proxy", "capture"],
        "no client EventSource probe claim": ["No client EventSource", "probe"],
    }
    require_fragments(failures, text, required_fragments)

    if not regex(
        text,
        r"(board\s+convergence.{0,120}SC-02.{0,120}SC-12|SC-02.{0,120}SC-12.{0,120}board\s+convergence)",
    ):
        failures.append("missing board convergence reference for SC-02 through SC-12")

    if not regex(text, r"\|\s*command\s*\|") and not regex(
        text, r"`[^`]+`.{0,120}(PASS|FAIL|Result|Artifact|log)"
    ):
        failures.append("executed evidence commands must include command/result rows")

    if not regex(text, r"Verdict\s*:\s*(PASS|FAIL|BLOCKED)"):
        failures.append("completion verdict must record Verdict: PASS, FAIL, or BLOCKED")

    return report(failures)


def report(failures: list[str]) -> int:
    if not failures:
        print(f"PASS: {TARGET} satisfies the L6-D01 demo smoke evidence check")
        return 0

    print(f"FAIL: {TARGET} is missing or incomplete for L6-D01")
    for failure in failures:
        print(f"- {failure}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
