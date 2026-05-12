# L3-D01 Demo Evidence - Geometry, OP, Handover

## Scope

This document records the L3-D01 Phase 5 evidence for validating prepared demo
fixtures against PRD §5.1 and the SC-04, SC-10, SC-11 harness criteria. It is
an evidence gate only; defect fixes are not mixed into this task.

L3 owns the S2/S8 backend fixture and domain evidence. SC-11 board rendering and
slot integration remain L6-owned; this document references existing L6 evidence
where board/UI proof is needed and does not modify board shell, route, layout,
or slot ownership.

## Run Metadata

| Field | Value |
|---|---|
| Task | `L3-D01` |
| Jira | `S14P31C106-202` |
| Branch | `feature/S14P31C106-202-demo-geometry-op-handover-data-validation` |
| Evidence timestamp | `2026-05-11T11:13:22+09:00` |
| Required sources | PRD §5.1, `docs/spec/harness-scenarios.md §2 SC-04`, `docs/spec/harness-scenarios.md §2 SC-10`, `docs/spec/harness-scenarios.md §2 SC-11` |
| Fixture source docs | `docs/spec/harness-scenarios.md §6`, `docs/spec/specs/S2.json`, `docs/spec/specs/S8.json`, `docs/spec/fixtures/common-fixtures.json` |

## Executed Evidence Commands

| Command | Purpose | Result |
|---|---|---|
| `python3 docs/tasks/check_l3_d01_demo_evidence.py` | RED guard for this evidence sink before the document existed | FAIL as expected: missing `docs/tasks/l3-d01-demo-evidence.md` |
| `cd backend && ./gradlew test --tests 'com.surimap.demo.DemoScenarioFixturesTest'` | Existing integrated demo fixture validation for SC-04, SC-10, SC-11 constants and guard snippets | PASS, `BUILD SUCCESSFUL in 36s` |
| `cd backend && ./gradlew test --tests 'com.surimap.harness.sc04.Sc04SearchAreaHarnessTest' --tests 'com.surimap.harness.sc10.Sc10OpHandoverHarnessTest' --tests 'com.surimap.demo.DemoScenarioFixturesTest' --tests 'com.surimap.summary.SearchHistorySummaryPublishRequestRedTest'` | SC-04 geometry/assignment, SC-10 OP/handover, demo fixture, and SC-11 summary publish evidence | PASS, `BUILD SUCCESSFUL in 2s` |
| `python3 docs/spec/fixtures/preflight_common_fixtures.py` | Shared fixture preflight for confirmed common fixture sections and board slots | PASS, `common fixture preflight passed` |
| `cd backend && ./gradlew test` | Required backend platform verification from `backend/AGENTS.md` | PASS, `BUILD SUCCESSFUL in 2m 48s` |

## SC-04 Fixture Validation

Coverage: SC-04 validates `overall_search_area`, `search_area`, and
`search_area_assignment` fixture readiness.

| Target | Evidence |
|---|---|
| Incident | PASS. `incidentId=inc-precinct-first-001` is the shared mock 112 assigned incident. |
| Overall search area | PASS. `overallAreaId=osa-precinct-001`, backend alias `osa-precinct-001-v1`, status `ACTIVE`, version `2`, event `evt-s2-overall-area-001`, board row `board-overall-search-area-inc-precinct-first-001`. |
| Search area | PASS. `areaId=area-precinct-a1`, status `ACTIVE`, created version `1`, event `evt-s2-area-created-001`, board row `board-area-precinct-a1`. |
| Assignment | PASS. `assignmentId=saa-precinct-a1-001`, assignee `acct-precinct-cmd`, status `ACTIVE`, version `1`, event `evt-s2-assignment-001`. |
| Geometry | PASS. EPSG:4326 fixture uses overall polygon coordinates including `126.948000,37.565000` and search area coordinates including `126.961000,37.575000`; bbox and 6-decimal precision are covered by `DemoScenarioFixturesTest` and `GeometryFixtureExactnessTest`-backed constants. |
| Board boundary | PASS for L3 source evidence. L6-owned board convergence is referenced through `docs/tasks/l6-demo-smoke-evidence.md`, not re-owned here. |

## SC-10 Fixture Validation

| Target | Evidence |
|---|---|
| Command flow | PASS. Common fixture sequence is `radio_report_received -> commander_decision_recorded -> area_completed -> op_transitioned -> handover_saved -> board_api_refetched`. |
| Radio report | PASS. `rr-precinct-001`, `incidentId=inc-precinct-first-001`, `opId=op-precinct-001-op1`, `areaId=area-precinct-a1`, reporter `acct-precinct-cmd`. |
| OP transition | PASS. `opId=op-precinct-001-op1` ends at version `2`; `opId=op-precinct-001-op2` becomes `ACTIVE`, reason `RE_SEARCH`, sequence `2`, version `1`. |
| OP board source IDs | PASS by reference. Expected board latest event is `evt-s8-op-transition-001` for `op_toggle` and `op_history`; L6 owns slot rendering. |
| Handover saved | PASS for SC-10 seed flow. `memoId=memo-precinct-handover-001`, event `evt-s8-handover-memo-001`, backend fixture status `ACTIVE`, version `1`. |
| Manual judgement boundary | PASS. The evidence validates the `RE_SEARCH` demo path and does not claim automatic area completion, next-area recommendation, missing-area certainty, or risk judgement. |

## SC-11 Fixture Validation

| Target | Evidence |
|---|---|
| OP comparison | REFERENCED. L3 validates OP1/OP2 fixture data; L6 evidence owns OP comparison rendering and board slot proof. |
| Handover memo | BLOCKED. `docs/spec/harness-scenarios.md §6` and SC-10 command flow use `memo-precinct-handover-001`, while `docs/spec/specs/S8.json` SC-11 `sc11_handover_ai_convergence` uses `memo-precinct-op2-001`. This prevents a single PASS claim for SC-11 handover memo convergence. |
| Search history summary | PASS for L3 summary publish fixture. `summary-precinct-op2-001`, board source `ai-summary-op-precinct-001-op2`, event `evt-s8-ai-summary-001`, status `FAILED`, display status `UNAVAILABLE`. |
| FR-23 guard | PASS. Existing summary guard evidence blocks next-area recommendation, missing-area certainty, high-risk wording, and automatic judgement wording. |
| Board slots | REFERENCED. `op_toggle`, `op_history`, `handover_memo`, `handover_status`, and `search_history_summary` are L6 board slots. L3 does not modify those slots. |

## Manual Data Repair Checklist

live manual data repair is not required for the validated L3 fixture set. Before
demo rehearsal, verify the following without patching live data:

| Check | Expected |
|---|---|
| Incident | `incidentId=inc-precinct-first-001` exists and remains the mock 112 assigned incident. |
| Geometry | `overallAreaId=osa-precinct-001` and `areaId=area-precinct-a1` match EPSG:4326 canonical fixture coordinates. |
| Assignment | `assignmentId=saa-precinct-a1-001` remains `ACTIVE` for `acct-precinct-cmd`. |
| OP transition | `opId=op-precinct-001-op1` remains available after `opId=op-precinct-001-op2` becomes current. |
| Handover memo | Do not repair manually. Resolve the `memo-precinct-handover-001` vs `memo-precinct-op2-001` fixture contract through the linked defect task. |
| Summary | `ai-summary-op-precinct-001-op2` stays display-only and does not introduce recommendation, missing-area certainty, risk judgement, or automatic judgement text. |

## Linked Defect Task List

| Defect | Jira | Evidence target | Status |
|---|---|---|---|
| S8 SC-11 handover memo fixture ID differs from harness SC-10/§6 fixture ID | `S14P31C106-204` | SC-11 handover memo convergence | Created, not fixed in L3-D01 |

## Residual Risk

- L3-D01 is blocked from a PASS verdict until `S14P31C106-204` resolves the SC-11 handover memo fixture identity.
- The current evidence proves SC-04 and SC-10 L3 backend fixture readiness, plus SC-11 summary fixture readiness.
- Board rendering, OP comparison UI, and slot convergence are intentionally referenced from L6 evidence and are not re-owned here.
- No new public API, event, entity, board slot, or fixture ID was introduced.

## Completion Verdict

Verdict: BLOCKED.

The L3 fixture validation result and demo rehearsal evidence are recorded, and
the discovered defect is tracked separately as `S14P31C106-204`. L3-D01 should
not be checked complete until that defect is resolved or the owner-approved
fixture contract clarifies why both handover memo IDs are expected.
