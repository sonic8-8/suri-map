#!/usr/bin/env python3
"""L1-D01A 리허설 증거 문서 검증 스크립트."""

from __future__ import annotations

import re
import sys
from pathlib import Path


TARGET = Path(__file__).with_name("l1-d01a-rehearsal-evidence.md")
RUNBOOK = "docs/tasks/l1-rehearsal-checklist-recovery-runbook.md"


def compact(text: str) -> str:
    # 줄바꿈과 여러 공백을 하나로 줄여서 표/문단 형식 차이로 검사가 흔들리지 않게 한다.
    return re.sub(r"\s+", " ", text).strip()


def contains_all(text: str, fragments: list[str]) -> bool:
    # 한 요구사항을 여러 핵심 단어 조합으로 확인한다. 대소문자는 의미가 없으므로 낮춰 비교한다.
    lowered = text.lower()
    return all(fragment.lower() in lowered for fragment in fragments)


def regex(text: str, pattern: str) -> bool:
    # 순서나 간격이 중요한 요구사항은 정규식으로 확인한다.
    return re.search(pattern, text, re.IGNORECASE | re.DOTALL) is not None


def require_fragments(
    failures: list[str], text: str, requirements: dict[str, list[str]]
) -> None:
    # 각 label은 사람이 읽기 쉬운 실패 메시지이고, fragments는 문서에 반드시 있어야 하는 핵심 단어다.
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

    # L1-D01A 완료 기준을 문서 안에서 직접 추적할 수 있게 필수 메타, 기준 문서, 시나리오를 고정한다.
    required_fragments = {
        "task metadata": ["Task", "L1-D01A"],
        "Jira metadata": ["Jira", "S14P31C106-201"],
        "branch metadata": ["feature/S14P31C106-201-rehearsal-evidence"],
        "runbook reference": [RUNBOOK],
        "required sources": ["PRD §2.3", "PRD §5.1", "docs/spec/harness-scenarios.md"],
        "executed evidence commands": ["실행한 증거 명령"],
        "SC-01 coverage": ["SC-01", "배정 사건 가져오기"],
        "SC-02 coverage": ["SC-02", "실종팀 인계", "지원 부대 배정"],
        "SC-03 coverage": ["SC-03", "오프라인 패키지"],
        "fixture incident": ["incidentId=inc-precinct-first-001"],
        "fixture mock source": ["sourceIncidentId=00000000-0000-0000-0000-000000000001"],
        "fixture OP1": ["opId=op-precinct-001-op1"],
        "fixture marker/path/memo": [
            "mk-precinct-clue-001",
            "path-precinct-car-001",
            "memo-precinct-handover-001",
        ],
        "assignment fixtures": [
            "00000000-0000-0000-0000-000000000001:cmd-alpha",
            "00000000-0000-0000-0000-000000000001:support-car",
            "ia-precinct-support-car-001=ACTIVE",
        ],
        "FCM fixture": ["fcm:dev-support-car-01"],
        "tile manifest fixture": ["tileManifestId=tile-manifest-inc-precinct-001"],
        "tile hash fixture": ["overall-area-hash-precinct-current"],
        "tile URI fixture": ["local://tiles/inc-precinct-first-001/{z}/{x}/{y}.pbf"],
        "network scripts": ["net-script-manifest-001", "net-script-tile-blob-001"],
        "board evidence": ["bs-inc-precinct-first-001", "package_badge"],
        "failure log section": ["실패 로그와 follow-up"],
        "import failure log": ["import", "failure log"],
        "handover failure log": ["handover", "failure log"],
        "package failure log": ["package", "failure log"],
        "follow-up task id": ["follow-up fix task ID"],
        "linked defect task list": ["결함 task 목록"],
        "completion verdict": ["완료 판정", "Verdict:"],
    }
    require_fragments(failures, text, required_fragments)

    # 증거 명령 표가 없으면 pass/fail 판단과 실제 실행 근거를 연결하기 어렵다.
    if not regex(text, r"\|\s*명령\s*\|") and not regex(
        text, r"`[^`]+`.{0,160}(PASS|FAIL|BLOCKED)"
    ):
        failures.append("executed evidence commands must include command/result rows")

    # Phase 5 evidence 문서는 최종 판정을 PASS/FAIL/BLOCKED 중 하나로 남겨야 한다.
    if not regex(text, r"Verdict:\s*(PASS|FAIL|BLOCKED)"):
        failures.append("completion verdict must record Verdict: PASS, FAIL, or BLOCKED")

    # L1-D01A는 결함 수정 task가 아니므로 결함 없음 또는 별도 task ID가 명시되어야 한다.
    if not regex(
        text,
        r"(follow-up fix task ID).{0,180}(None|N/A|없음|S14P31C106-\d+)",
    ):
        failures.append("follow-up fix task ID must be None/N/A/없음 or a Jira task key")

    return report(failures)


def report(failures: list[str]) -> int:
    # 실패가 하나라도 있으면 사람이 바로 보완할 수 있게 누락 항목을 줄 단위로 출력한다.
    if not failures:
        print(f"PASS: {TARGET} satisfies L1-D01A rehearsal evidence requirements")
        return 0

    print(f"FAIL: {TARGET} is missing or incomplete for L1-D01A")
    for failure in failures:
        print(f"- {failure}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
