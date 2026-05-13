#!/usr/bin/env python3
"""L1-D01B 리허설 증거 문서 검증 스크립트."""

from __future__ import annotations

import re
import sys
from pathlib import Path


TARGET = Path(__file__).with_name("l1-d01b-rehearsal-evidence.md")
RUNBOOK = "docs/tasks/l1-rehearsal-checklist-recovery-runbook.md"


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
        "task metadata": ["Task", "L1-D01B"],
        "Jira metadata": ["Jira", "S14P31C106-295"],
        "branch metadata": ["feature/S14P31C106-295-l1-d01b-rehearsal-evidence"],
        "runbook reference": [RUNBOOK],
        "required sources": ["PRD §2.3", "PRD §5.1", "docs/spec/harness-scenarios.md"],
        "executed evidence commands": ["실행한 증거 명령"],
        "SC-04 coverage": ["SC-04", "전체 수색 구역", "구역 할당"],
        "SC-05 coverage": ["SC-05", "수색 경로", "PolicePhone GPS"],
        "SC-06 coverage": ["SC-06", "현장 마커"],
        "fixture incident uuid": ["aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001"],
        "fixture OP uuid": ["88888888-8888-8888-8888-888888880001"],
        "SC-04 fixtures": [
            "osa-precinct-001-v1",
            "area-precinct-a1",
            "saa-precinct-a1-001",
            "evt-s2-assignment-001",
        ],
        "SC-05 fixtures": [
            "gps-path-normal-001",
            "seg-precinct-vehicle-001",
            "seg-precinct-foot-001",
            "police_phone_freshness",
        ],
        "SC-06 fixtures": [
            "55555555-5555-5555-5555-555555550001",
            "55555555-5555-5555-5555-555555550101",
            "evt-s5-marker-updated-photo-001",
        ],
        "board evidence": [
            "bs-inc-precinct-first-001",
            "overall_search_area",
            "area",
            "path",
            "marker",
        ],
        "failure log section": ["실패 로그와 follow-up"],
        "SC-04 failure log": ["SC-04 failure log"],
        "SC-05 failure log": ["SC-05 failure log"],
        "SC-06 failure log": ["SC-06 failure log"],
        "follow-up task id": ["follow-up fix task ID"],
        "linked defect task list": ["결함 task 목록"],
        "completion verdict": ["완료 판정", "Verdict:"],
    }
    require_fragments(failures, text, required_fragments)

    if not regex(text, r"\|\s*명령\s*\|") and not regex(
        text, r"`[^`]+`.{0,160}(PASS|FAIL|BLOCKED)"
    ):
        failures.append("executed evidence commands must include command/result rows")

    if not regex(text, r"Verdict:\s*(PASS|FAIL|BLOCKED)"):
        failures.append("completion verdict must record Verdict: PASS, FAIL, or BLOCKED")

    if not regex(
        text,
        r"(follow-up fix task ID).{0,180}(None|N/A|없음|S14P31C106-\d+)",
    ):
        failures.append("follow-up fix task ID must be None/N/A/없음 or a Jira task key")

    return report(failures)


def report(failures: list[str]) -> int:
    if not failures:
        print(f"PASS: {TARGET} satisfies L1-D01B rehearsal evidence requirements")
        return 0

    print(f"FAIL: {TARGET} is missing or incomplete for L1-D01B")
    for failure in failures:
        print(f"- {failure}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
