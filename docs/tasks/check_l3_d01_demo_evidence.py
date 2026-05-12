#!/usr/bin/env python3
"""L3-D01 demo geometry, OP, and handover evidence document check."""

from __future__ import annotations

import re
import sys
from pathlib import Path


TARGET = Path(__file__).with_name("l3-d01-demo-evidence.md")


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
        "task metadata": ["Task", "L3-D01"],
        "Jira metadata": ["Jira", "S14P31C106-202"],
        "branch metadata": [
            "Branch",
            "feature/S14P31C106-202-demo-geometry-op-handover-data-validation",
        ],
        "required sources": [
            "PRD §5.1",
            "docs/spec/harness-scenarios.md §2 SC-04",
            "docs/spec/harness-scenarios.md §2 SC-10",
            "docs/spec/harness-scenarios.md §2 SC-11",
        ],
        "executed evidence commands": ["Executed Evidence Commands"],
        "SC-04 coverage": ["SC-04", "overall_search_area", "search_area", "search_area_assignment"],
        "SC-10 coverage": ["SC-10", "area_completed", "op_transitioned", "handover_saved"],
        "SC-11 coverage": ["SC-11", "search_history_summary", "OP comparison"],
        "incident fixture": ["incidentId=inc-precinct-first-001"],
        "overall area fixture": ["overallAreaId=osa-precinct-001"],
        "area fixture": ["areaId=area-precinct-a1"],
        "assignment fixture": ["assignmentId=saa-precinct-a1-001"],
        "OP fixture": ["opId=op-precinct-001-op1", "opId=op-precinct-001-op2"],
        "handover memo fixture": ["memoId=memo-precinct-handover-001"],
        "summary fixture": ["ai-summary-op-precinct-001-op2"],
        "geometry fixture": [
            "EPSG:4326",
            "126.948000",
            "37.565000",
            "126.961000",
            "37.575000",
        ],
        "event evidence": [
            "evt-s2-overall-area-001",
            "evt-s2-area-created-001",
            "evt-s2-assignment-001",
            "evt-s8-op-transition-001",
            "evt-s8-handover",
        ],
        "board evidence": [
            "board-overall-search-area-inc-precinct-first-001",
            "board-area-precinct-a1",
            "op_toggle",
            "op_history",
            "handover_memo",
            "handover_status",
            "search_history_summary",
        ],
        "manual data repair checklist": ["Manual Data Repair Checklist"],
        "no live repair dependency": ["live manual data repair", "not required"],
        "linked defect task list": ["Linked Defect Task List"],
        "completion verdict": ["Completion Verdict"],
    }
    require_fragments(failures, text, required_fragments)

    if not regex(text, r"\|\s*Command\s*\|") and not regex(
        text, r"`[^`]+`.{0,160}(PASS|FAIL|BLOCKED|BUILD SUCCESSFUL)"
    ):
        failures.append("executed evidence commands must include command/result rows")

    if not regex(text, r"Verdict\s*:\s*(PASS|FAIL|BLOCKED)"):
        failures.append("completion verdict must record Verdict: PASS, FAIL, or BLOCKED")

    if not regex(text, r"(Defect|결함).{0,180}(None|N/A|없음|S14P31C106-\d+)"):
        failures.append("linked defect task list must record None/N/A/없음 or Jira task keys")

    return report(failures)


def report(failures: list[str]) -> int:
    if not failures:
        print(f"PASS: {TARGET} satisfies L3-D01 demo evidence requirements")
        return 0

    print(f"FAIL: {TARGET} is missing or incomplete for L3-D01")
    for failure in failures:
        print(f"- {failure}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
