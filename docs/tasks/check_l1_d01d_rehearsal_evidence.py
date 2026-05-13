#!/usr/bin/env python3
"""L1-D01D 리허설 증거 문서 검증 스크립트."""

from __future__ import annotations

import re
import sys
from pathlib import Path


TARGET = Path(__file__).with_name("l1-d01d-rehearsal-evidence.md")
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
        "task metadata": ["Task", "L1-D01D"],
        "Jira metadata": ["Jira", "S14P31C106-297"],
        "branch metadata": ["feature/S14P31C106-297-l1-d01d-rehearsal-evidence"],
        "runbook reference": [RUNBOOK],
        "required sources": ["PRD §2.3", "PRD §5.1", "docs/spec/harness-scenarios.md"],
        "executed evidence commands": ["실행한 증거 명령"],
        "prerequisite evidence": ["check_l3_d01_demo_evidence.py", "check_l6_demo_smoke_evidence.py"],
        "SC-10 coverage": ["SC-10", "구역 완료", "새 OP"],
        "SC-11 coverage": ["SC-11", "인수인계", "수색 이력 요약"],
        "SC-12 coverage": ["SC-12", "사건 종료", "데이터 파기"],
        "fixture incident uuid": ["aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001"],
        "op transition fixtures": [
            "op-precinct-001-op2",
            "88888888-8888-8888-8888-888888880002",
            "evt-s8-op-transitioned-001",
        ],
        "handover fixtures": [
            "memo-precinct-op2-001",
            "eeeeeeee-eeee-eeee-eeee-eeeeeeee0010",
            "evt-s8-handover-memo-001",
        ],
        "summary fixtures": [
            "summary-precinct-op2-001",
            "44444444-4444-4444-4444-444444440001",
            "evt-s8-ai-summary-001",
        ],
        "terminal evidence": [
            "INCIDENT_CLOSED",
            "INCIDENT_PURGED",
            "incident_terminal",
            "localPurgeState",
        ],
        "board evidence": [
            "area",
            "op_toggle",
            "op_history",
            "handover_memo",
            "handover_status",
            "search_history_summary",
        ],
        "frontend evidence": [
            "S8OpHandoverSlots.test.tsx",
            "SearchHistorySummarySlot.test.tsx",
            "Sc11OpSearchHistorySummaryHarnessRedTest.test.tsx",
        ],
        "android evidence": [
            "HandoverRepositoriesTest",
            "HandoverMemoLocalRecorderRoomTest",
            "DutyHandoverStateLoaderTest",
            "IncidentLocalCleanupPolicyTest",
        ],
        "failure log section": ["실패 로그와 follow-up"],
        "SC-10 failure log": ["SC-10 failure log"],
        "SC-11 failure log": ["SC-11 failure log"],
        "SC-12 failure log": ["SC-12 failure log"],
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
        print(f"PASS: {TARGET} satisfies L1-D01D rehearsal evidence requirements")
        return 0

    print(f"FAIL: {TARGET} is missing or incomplete for L1-D01D")
    for failure in failures:
        print(f"- {failure}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
