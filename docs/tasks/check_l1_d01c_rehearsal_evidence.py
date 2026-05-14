#!/usr/bin/env python3
"""L1-D01C 리허설 증거 문서 검증 스크립트."""

from __future__ import annotations

import re
import sys
from pathlib import Path


TARGET = Path(__file__).with_name("l1-d01c-rehearsal-evidence.md")
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
        "task metadata": ["Task", "L1-D01C"],
        "Jira metadata": ["Jira", "S14P31C106-296"],
        "branch metadata": ["feature/S14P31C106-296-l1-d01c-rehearsal-evidence"],
        "runbook reference": [RUNBOOK],
        "required sources": ["PRD §2.3", "PRD §5.1", "docs/spec/harness-scenarios.md"],
        "executed evidence commands": ["실행한 증거 명령"],
        "SC-07 coverage": ["SC-07", "통신 단절", "로컬 기록"],
        "SC-08 coverage": ["SC-08", "지원 요청", "실종자 발견"],
        "SC-09 coverage": ["SC-09", "통신 복구", "동기화"],
        "fixture incident uuid": ["aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001"],
        "offline scripts": ["net-script-domain-write-001", "net-script-outbox-flush-001"],
        "outbox fixtures": ["outbox-path-001", "outbox-marker-001", "outbox-photo-001"],
        "support request fixtures": [
            "mk-precinct-support-001",
            "55555555-5555-5555-5555-555555550801",
            "66666666-6666-6666-6666-666666660801",
            "evt-s5-support-request-001",
        ],
        "person found fixtures": [
            "mk-precinct-person-found-001",
            "55555555-5555-5555-5555-555555550802",
            "66666666-6666-6666-6666-666666660802",
            "evt-s5-person-found-001",
        ],
        "board evidence": [
            "bs-inc-precinct-first-001",
            "marker",
            "toast",
            "path",
            "police_phone_freshness",
        ],
        "android evidence": [
            "SearchPathLocalRecorderRoomTest",
            "MarkerLocalRecorderRoomTest",
            "RoomLocalSyncServicesTest",
            "OfflinePackageUiStateTest",
        ],
        "failure log section": ["실패 로그와 follow-up"],
        "SC-07 failure log": ["SC-07 failure log"],
        "SC-08 failure log": ["SC-08 failure log"],
        "SC-09 failure log": ["SC-09 failure log"],
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
        print(f"PASS: {TARGET} satisfies L1-D01C rehearsal evidence requirements")
        return 0

    print(f"FAIL: {TARGET} is missing or incomplete for L1-D01C")
    for failure in failures:
        print(f"- {failure}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
