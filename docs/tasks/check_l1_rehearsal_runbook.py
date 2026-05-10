#!/usr/bin/env python3
"""L1-I04 리허설 준비 문서 검증 스크립트."""

from __future__ import annotations

import re
import sys
from pathlib import Path


TARGET = Path(__file__).with_name("l1-rehearsal-checklist-recovery-runbook.md")


def compact(text: str) -> str:
    # 줄바꿈과 여러 공백을 하나로 줄여서 문서 형식 차이 때문에 검사가 흔들리지 않게 한다.
    return re.sub(r"\s+", " ", text).strip()


def contains_all(text: str, fragments: list[str]) -> bool:
    # 한 요구사항을 여러 단어 조합으로 확인할 때 쓴다. 대소문자는 의미가 없으므로 낮춰 비교한다.
    lowered = text.lower()
    return all(fragment.lower() in lowered for fragment in fragments)


def regex(text: str, pattern: str) -> bool:
    # 단순 포함으로 보기 어려운 표현은 정규식으로 확인한다. 줄바꿈을 지운 compact text에 맞춘다.
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
        failures.append(f"missing required L1-I04 document: {TARGET}")
        return report(failures)

    raw = TARGET.read_text(encoding="utf-8")
    text = compact(raw)

    # L1-I04 완료 기준을 문서 안에서 확인할 수 있게 필수 메타·섹션·fixture ID를 고정한다.
    required_fragments = {
        "task metadata": ["L1-I04", "S14P31C106-200", "Phase 4"],
        "required sources": ["PRD §2.3", "PRD §5.1", "docs/spec/harness-scenarios.md"],
        "no final evidence policy": ["최종 pass/fail", "L1-D01"],
        "rehearsal checklist section": ["12단계 리허설 체크리스트"],
        "seed sheet section": ["데모 시드 데이터 표"],
        "retry runbook section": ["재시도·복구 절차서"],
        "import retry": ["배정 사건 가져오기", "import", "retry"],
        "handover retry": ["실종팀 인계", "support", "externalAssignmentKey"],
        "close retry": ["사건 종료", "terminal", "재오픈 없음"],
        "soft delete policy": ["24시간", "soft delete"],
        "fixture incident": ["inc-precinct-first-001"],
        "fixture mock source": ["mock-112-incident-001"],
        "fixture OP1": ["op-precinct-001-op1"],
        "fixture OP2": ["op-precinct-001-op2"],
        "tile manifest": ["tile-manifest-inc-precinct-001"],
        "tile hash": ["overall-area-hash-precinct-current"],
        "board response": ["bs-inc-precinct-first-001"],
        "terminal tombstone": ["tombstone-inc-precinct-first-001"],
        "network scripts": ["net-script-manifest-001", "net-script-outbox-flush-001"],
        "accounts": ["acct-precinct-cmd", "acct-cmd-alpha", "acct-support-cmd"],
        "police phones": ["dev-precinct-phone-01", "dev-alpha-phone-01", "dev-support-phone-01"],
        "no official system replacement": ["112 공식 시스템", "대체하지 않는다"],
    }
    require_fragments(failures, text, required_fragments)

    # PRD §5.1의 12단계가 빠지면 리허설 체크리스트로 쓸 수 없으므로 SC-01~SC-12를 모두 요구한다.
    for sc in range(1, 13):
        if f"SC-{sc:02d}" not in text:
            failures.append(f"missing SC-{sc:02d} rehearsal row")

    # 이 문서는 준비 절차서다. 실제 통과/실패 증거는 Phase 5 evidence 문서로 넘겨야 한다.
    if not regex(
        text,
        r"(demo\s+pass/fail|pass/fail\s+evidence|최종\s*pass/fail|최종.{0,20}증거).{0,160}(기록하지\s+않|L1-D01)",
    ):
        failures.append("must defer final pass/fail evidence to L1-D01")

    # task 산출물 3종이 하나의 문서 안에서 연결되어 있는지 확인한다.
    if not regex(text, r"(체크리스트).{0,120}(시드).{0,120}(재시도|복구)"):
        failures.append("must tie checklist, seed sheet, and retry/recovery runbook together")

    # 리허설 문서가 자동 추천·위험 판단 같은 금지 기능을 암시하지 않도록 guardrail을 확인한다.
    if not regex(text, r"(자동\s*판단|다음\s+투입\s+구역\s+자동|위험도\s+판단).{0,120}(금지|하지\s+않|없음)"):
        failures.append("must preserve the no-automatic-judgment guard")

    return report(failures)


def report(failures: list[str]) -> int:
    # 실패가 하나라도 있으면 사람이 바로 보완할 수 있게 누락 항목을 줄 단위로 출력한다.
    if not failures:
        print(f"PASS: {TARGET} satisfies L1-I04 rehearsal runbook requirements")
        return 0

    print(f"FAIL: {TARGET} is missing or incomplete for L1-I04")
    for failure in failures:
        print(f"- {failure}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
