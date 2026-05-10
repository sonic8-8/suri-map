#!/usr/bin/env python3
"""RED check for the L4-T10C network switch stability protocol document.

This guard intentionally verifies only the Phase 5 pre-run procedure. Final
1-hour stability and network on/off pass/fail evidence belong to L4-D01.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path


TARGET = Path(__file__).with_name("l4-network-switch-stability-protocol.md")


def compact(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def contains_all(text: str, fragments: list[str]) -> bool:
    lowered = text.lower()
    return all(fragment.lower() in lowered for fragment in fragments)


def regex(text: str, pattern: str) -> bool:
    return re.search(pattern, text, re.IGNORECASE | re.DOTALL) is not None


def query_blocks(raw: str) -> list[str]:
    return re.findall(r"```(?:sql|query)?\s*(.*?)```", raw, re.IGNORECASE | re.DOTALL)


def query_blocks_for_table(raw: str, table: str) -> list[str]:
    return [block for block in query_blocks(raw) if regex(block, rf"\b{re.escape(table)}\b")]


def require_fragments(
    failures: list[str], text: str, requirements: dict[str, list[str]]
) -> None:
    for label, fragments in requirements.items():
        if not contains_all(text, fragments):
            failures.append(f"missing {label}: {', '.join(fragments)}")


def require_regex(failures: list[str], text: str, label: str, pattern: str) -> None:
    if not regex(text, pattern):
        failures.append(f"missing {label}")


def require_query_table(failures: list[str], raw: str, table: str) -> None:
    blocks = query_blocks_for_table(raw, table)
    if not any(regex(block, r"(select|from|count|duplicate)") for block in blocks):
        failures.append(f"missing duplicate-row SQL/query block for {table}")


def require_query_block_regex(
    failures: list[str], raw: str, table: str, label: str, pattern: str
) -> None:
    blocks = query_blocks_for_table(raw, table)
    if not any(regex(block, pattern) for block in blocks):
        failures.append(f"missing {label} in {table} query")


def main() -> int:
    failures: list[str] = []

    if not TARGET.exists():
        failures.append(f"missing required protocol document: {TARGET}")
        return report(failures)

    raw = TARGET.read_text(encoding="utf-8")
    text = compact(raw)

    required_fragments = {
        "task metadata": ["Task", "L4-T10C"],
        "Jira metadata": ["Jira", "S14P31C106-194"],
        "PRD 2.2 reference": ["PRD §2.2"],
        "PRD 2.3 reference": ["PRD §2.3"],
        "SC-05 reference": ["SC-05"],
        "SC-07 reference": ["SC-07"],
        "SC-09 reference": ["SC-09"],
        "incident fixture": ["incidentId=inc-precinct-first-001"],
        "GPS path fixture": ["gps-path-normal-001"],
        "mixed path fixture": ["path-precinct-mixed-001"],
        "marker fixture": ["mk-precinct-clue-001"],
        "domain-write network script": ["net-script-domain-write-001"],
        "heartbeat network script": ["net-script-heartbeat-001"],
        "outbox-flush network script": ["net-script-outbox-flush-001"],
        "tile manifest fixture": ["tileManifestId=tile-manifest-inc-precinct-001"],
        "package badge fixture value": ["package_badge"],
        "path fixture value": ["path"],
        "marker fixture value": ["marker"],
        "police phone freshness fixture value": ["police_phone_freshness"],
        "SC-07 PENDING_LOCAL expectation": ["PENDING_LOCAL"],
        "SC-07 PENDING_SEND expectation": ["PENDING_SEND"],
        "SC-07 OFFLINE expectation": ["OFFLINE"],
        "SC-07 offline Korean copy": ["오프라인 / 기록 중"],
        "SC-07 unsent queue Korean copy": ["미전송 큐"],
        "SC-09 sending to acked expectation": ["SENDING", "ACKED"],
        "SC-09 synced expectation": ["SYNCED"],
        "SC-09 unsent zero expectation": ["미전송 0"],
        "SC-09 pending cleared expectation": ["pending 해제"],
        "SC-09 duplicate zero expectation": ["duplicate 생성 0건"],
        "SC-09 stale refetch expectation": ["STALE_REFETCH"],
        "observer roles": ["observer", "roles"],
        "required artifacts/logs": ["artifacts", "logs"],
        "blocker/follow-up defect recording": ["blocker", "follow-up", "defect"],
    }
    require_fragments(failures, text, required_fragments)

    require_regex(
        failures,
        text,
        "Phase 5 pre-run/procedure scope",
        r"Phase\s*5.{0,120}(pre[- ]?run|before|procedure|protocol|scope)"
        r"|(?:pre[- ]?run|before).{0,80}Phase\s*5",
    )

    if not (
        regex(
            text,
            r"L4-D01.{0,240}(final.{0,40}pass/fail|pass/fail.{0,40}evidence).{0,160}(1[- ]?hour|on/off)",
        )
        or regex(
            text,
            r"(1[- ]?hour|on/off).{0,160}(final.{0,40}pass/fail|pass/fail.{0,40}evidence).{0,240}L4-D01",
        )
    ):
        failures.append(
            "must state final 1-hour/on-off pass/fail evidence belongs to L4-D01"
        )

    require_regex(
        failures,
        text,
        "not-this-procedure final evidence boundary",
        r"(not\s+this\s+procedure|does\s+not\s+record|must\s+not\s+record|not\s+record|기록하지\s+않)",
    )

    require_regex(
        failures,
        text,
        "1-hour checklist for 2 PolicePhones and 1 board",
        r"1[- ]?hour.{0,240}(2|two)\s*PolicePhones?.{0,160}(1|one)\s*board"
        r"|1[- ]?hour.{0,240}(1|one)\s*board.{0,160}(2|two)\s*PolicePhones?",
    )
    require_regex(
        failures,
        text,
        "1-hour capture targets without final pass/fail",
        r"1[- ]?hour.{0,240}(checklist|capture\s+targets?).{0,240}(no\s+final\s+pass/fail|without\s+final\s+pass/fail|not\s+final\s+pass/fail|capture\s+only)",
    )
    require_regex(
        failures,
        text,
        "1-hour continuous path collection",
        r"(continuous|연속).{0,80}(gps|path|경로).{0,80}(collection|recording|수집|기록)",
    )
    require_regex(
        failures,
        text,
        "1-hour 10-second batch cadence evidence",
        r"(10[- ]?second|10\s*s|10초).{0,80}(batch|cadence|interval|주기)",
    )
    require_regex(
        failures,
        text,
        "1-hour runtime stability signals",
        r"(runtime|liveness|stability).{0,120}(crash|anr|memory|battery|process|cpu|foreground|heartbeat)",
    )
    require_regex(
        failures,
        text,
        "1-hour board path/freshness convergence capture",
        r"board.{0,160}path.{0,160}(police_phone_freshness|freshness).{0,160}(convergence|converge|capture)",
    )

    require_regex(
        failures,
        text,
        "network on/off 10-cycle protocol",
        r"(network\s+on/off|on/off).{0,160}(10[- ]?cycle|10\s+cycles)"
        r"|(10[- ]?cycle|10\s+cycles).{0,160}(network\s+on/off|on/off)",
    )
    require_regex(
        failures,
        text,
        "manual or script-ready network steps",
        r"(manual\s+protocol|manual\s+steps|script[- ]?ready|scriptable)",
    )
    require_regex(
        failures,
        text,
        "cycle numbering in network switch protocol",
        r"(cycle\s*#|cycle\s+number|cycles?\s+1\s*(?:-|to|\.\.)\s*10|1\s*/\s*10)",
    )
    require_regex(
        failures,
        text,
        "offline duration checkpoint",
        r"offline\s+duration|duration.{0,40}offline|오프라인.{0,40}지속",
    )
    require_regex(
        failures,
        text,
        "online recovery checkpoints",
        r"online\s+recovery\s+checkpoints?|recovery\s+checkpoint|온라인.{0,40}복구",
    )
    require_regex(
        failures,
        text,
        "queue drain observation",
        r"queue\s+drain\s+observation|observe.{0,40}queue\s+drain|미전송.{0,40}(큐|0)",
    )
    require_regex(
        failures,
        text,
        "30-minute offline coverage",
        r"30[- ]?minute|30\s*min|30분",
    )
    require_regex(
        failures,
        text,
        "app restart queue persistence checkpoint",
        r"(app|application).{0,80}restart.{0,120}(queue|outbox|PENDING_SEND|미전송)"
        r"|(?:queue|outbox|PENDING_SEND|미전송).{0,120}(app|application).{0,80}restart",
    )
    require_regex(
        failures,
        text,
        "offline negative server/event/SSE/board row check",
        r"(offline\s+negative\s+check|negative\s+check).{0,160}(server|event_dispatch_job|sse|board).{0,160}(not\s+created|no\s+row|0\s+row|0건)",
    )
    require_regex(
        failures,
        text,
        "partial recovery and duplicate retransmission modes",
        r"(partial[- ]?recovery|부분\s*복구).{0,200}(duplicate[- ]?retransmission|duplicate\s+replay|중복\s*재전송)",
    )

    require_regex(
        failures,
        text,
        "duplicate row verification query section",
        r"duplicate.{0,80}(row|verification).{0,80}(sql|query)",
    )
    require_regex(
        failures,
        text,
        "duplicate query execution boundary",
        r"(L4-T10C.{0,160}(prepare|prepared|readiness)|prepared.{0,160}L4-T10C).{0,240}(L4-D01|execution[- ]?time|Phase\s*5)"
        r"|(?:execution[- ]?time|when\s+L4-D01\s+executes|L4-D01\s+execution).{0,160}duplicate",
    )
    for table in (
        "search_path",
        "search_path_segment",
        "marker",
        "photo",
        "marker_notification",
        "event_dispatch_job",
        "idempotency_record",
        "offline_package_installation",
    ):
        require_query_table(failures, raw, table)
    require_query_block_regex(
        failures,
        raw,
        "event_dispatch_job",
        "source entity duplicate grouping",
        r"group\s+by\s+source_entity_type\s*,\s*source_entity_id\s*,\s*event_type",
    )
    for block in query_blocks_for_table(raw, "event_dispatch_job"):
        if regex(block, r"group\s+by[^;]*\bevent_id\b"):
            failures.append(
                "event_dispatch_job duplicate query must not group by event_id"
            )
            break

    require_regex(
        failures,
        text,
        "board slot convergence observations",
        r"board\s+slot\s+convergence.{0,160}(observation|observe|capture|checkpoint)",
    )

    require_regex(
        failures,
        text,
        "pre-run procedure before Phase 5",
        r"(pre[- ]?run|before).{0,80}Phase\s*5|Phase\s*5.{0,80}(pre[- ]?run|before)",
    )

    return report(failures)


def report(failures: list[str]) -> int:
    if not failures:
        print(f"PASS: {TARGET} satisfies the L4-T10C network switch protocol check")
        return 0

    print(f"FAIL: {TARGET} is missing or incomplete for L4-T10C")
    for failure in failures:
        print(f"- {failure}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
