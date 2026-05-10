# L6-D01 Demo Smoke Evidence - Board, Package, Tile, Device

## Scope

Evidence sink for the L6-D01 board/package/tile/device demo smoke. This evidence follows `docs/tasks/l6-demo-smoke-procedure.md` and records the L6 portion of the demo readiness check for PRD §2.2, PRD §2.3, and the 12-step rehearsal context.

Coverage targets:

- SC-03 package and local tile readiness for the assigned incident.
- SC-11 search history summary rendering guard.
- SC-12 terminal incident convergence.
- Board convergence for SC-02 through SC-12, including package badge, board slot assembly, terminal state, and privacy/tombstone behavior.

This evidence is harness/test-report evidence. It is not a physical 1-hour Android stability run; L4-D01 owns the physical 2-device network stability evidence. It also does not claim browser/proxy network capture or client EventSource reconnect observation; tile and SSE close claims below are limited to harness/style fixture and sanitized payload evidence.

## Run Metadata

| Field | Value |
|---|---|
| Task | `L6-D01` |
| Jira | `S14P31C106-191` |
| Branch | `feature/S14P31C106-191-board-package-tile-device-demo-evidence` |
| Base SHA | `43b7ae2` |
| Evidence timestamp | `2026-05-10T14:15:39+09:00` |
| Procedure reference | `docs/tasks/l6-demo-smoke-procedure.md` |
| Backend test XML directory | `backend/build/test-results/test/` |

## Executed Evidence Commands

| # | Command | Purpose | Result | Artifact / log pointer |
|---|---|---|---|---|
| 1 | `cd backend && ./gradlew test --tests com.surimap.offlinepackage.Sc03PackageTileHarnessRedTest --tests com.surimap.board.BoardApiSseConvergenceHarnessRedTest --tests com.surimap.board.IncidentTerminalPackageBadgePrivacyRedTest` | SC-03 package/tile harness/style fixture evidence, board API/SSE convergence, and SC-12 terminal package badge/privacy evidence | PASS, `BUILD SUCCESSFUL in 17s` | `backend/build/test-results/test/` |
| 2 | `cd frontend && npm run test -- src/features/board/components/Sc11OpSearchHistorySummaryHarnessRedTest.test.tsx` | SC-11 board rendering guard evidence for search history summary | PASS. Vitest reported 1 test file passed, 1 test passed, duration 2.27s | Vitest console output |
| 3 | `python3 docs/spec/fixtures/preflight_common_fixtures.py` | Fixture preflight for shared demo IDs | PASS, `common fixture preflight passed` | Console output |
| 4 | `python3 docs/tasks/check_l6_demo_smoke_procedure.py` | Procedure document guard before recording evidence | PASS | Console output |

Selected backend XML evidence:

| Test suite | Result |
|---|---|
| `TEST-com.surimap.offlinepackage.Sc03PackageTileHarnessRedTest.xml` | 4 tests, 0 failures, 0 errors |
| `TEST-com.surimap.board.BoardApiSseConvergenceHarnessRedTest.xml` | 5 tests, 0 failures, 0 errors |
| `TEST-com.surimap.board.IncidentTerminalPackageBadgePrivacyRedTest.xml` | 3 tests, 0 failures, 0 errors |

Frontend evidence:

| Test file | Result |
|---|---|
| `src/features/board/components/Sc11OpSearchHistorySummaryHarnessRedTest.test.tsx` | 1 test, PASS, duration 2.27s |

## Local Tile Network Evidence

The SC-03 package/tile harness records local tile readiness for the assigned incident and verifies the demo style/fixture path does not depend on external tile providers. The phrase local /tiles capture here means harness/style fixture evidence for the local `/tiles` route template; no browser, app, or proxy network capture artifact was recorded in this run.

| Evidence item | Result |
|---|---|
| Incident fixture | PASS. `incidentId=inc-precinct-first-001`. |
| Tile manifest fixture | PASS. `tileManifestId=tile-manifest-inc-precinct-001`. |
| Local tile URI template | PASS. `local://tiles/inc-precinct-first-001/{z}/{x}/{y}.pbf`. |
| Tile bounds | PASS. `z=15..16`, `x=27925..27960`, `y=12680..12720`. |
| Overall search area identity | PASS. `overall-area-hash-precinct-current`. |
| Network script fixtures | PASS. `net-script-manifest-001`, `net-script-tile-blob-001`. |
| Local capture | PASS. local /tiles capture target is represented by harness/style fixture evidence for manifest and blob access. No browser/proxy packet capture is claimed. |
| External tile boundary | PASS. external tile requests 0 in the harness/style fixture path. Forbidden provider host patterns are `*.tile.openstreetmap.org`, `*.mapbox.com`, and `*.googleapis.com`. |

## Package / Board Evidence

| Target | Result |
|---|---|
| `package_badge` | PASS. Board package badge is present for the fixture incident and reflects local package state. |
| Local tile/package state | PASS. local tile/package state displayed on the board package surface for the current incident package. |
| Mock board API | PASS. mock board API refetch/assembly path is exercised for assembled board state. |
| Board slot convergence | PASS. board slot convergence is covered for SC-02 through SC-12 by `BoardApiSseConvergenceHarnessRedTest`. |
| SC-03 package/tile | PASS. SC-03 package tile readiness converges through manifest, local `/tiles`, and package badge evidence. |

## SC-11 Rendering Guard Evidence

| Guard | Result |
|---|---|
| `search_history_summary` | PASS. SC-11 renders the search history summary slot. |
| OP comparison | PASS. OP comparison is rendered for the fixture summary. |
| Recommendation guard | PASS. no recommendation is rendered or implied by the summary. |
| Missing-area certainty guard | PASS. no automatic missing-area certainty is rendered. |
| Risk judgment guard | PASS. no risk-judgment CTA/text is rendered. |

## Terminal State Convergence Evidence

| Target | Result |
|---|---|
| `incident_terminal` | PASS. Terminal incident state is surfaced through the board terminal slot. |
| Terminal package badge | PASS. `package_badge` reaches terminal/tombstone state after incident close. |
| Tombstone response | PASS. tombstone response is returned for terminal package state. |
| Missing person privacy | PASS. missing_person absence is asserted for sanitized terminal state. |
| SSE close behavior | PASS. no live SSE resubscribe after close hint is present in sanitized board/tombstone payloads. No client EventSource reconnect probe was recorded. |
| Sanitized terminal state | PASS. sanitized terminal state is asserted for closed incident package/board output. |

## Explicit Board / Tile Boundary Statement

This L6-D01 evidence was collected from harnesses and test reports:

- Board convergence: SC-02 through SC-12 board convergence is covered by board API/SSE convergence harness evidence.
- Package/tile: SC-03 fixture package and local tile network evidence use harness/style fixture evidence for local `/tiles` capture only; no browser/proxy capture artifact is claimed.
- Terminal: SC-12 terminal package badge, tombstone response, missing_person absence, and sanitized terminal state are covered by terminal package badge/privacy harness evidence.
- External tile providers: external tile requests 0 for `*.tile.openstreetmap.org`, `*.mapbox.com`, and `*.googleapis.com` in the harness/style fixture path.

## Linked Defect Task List

| Defect | Link | Evidence target | Status |
|---|---|---|---|
| None | N/A | All L6-D01 evidence targets | No defect task created |

## Residual Risk

- This is harness/test-report evidence for demo smoke readiness, not physical 1-hour Android stability evidence.
- L4-D01 owns physical 2-device network stability, including repeated on/off network behavior and long-running Android runtime observation.
- No browser/proxy network capture artifact was recorded; local tile evidence is limited to harness/style fixture proof that local `/tiles` is the configured route and forbidden external tile hosts are absent from that path.
- No client EventSource reconnect probe was recorded; close behavior evidence is limited to sanitized payloads and absence of live resubscribe hints.

## Completion Verdict

Verdict: PASS.

Completion criteria:

- SC-03 package/tile command evidence is present and passing.
- SC-11 rendering guard evidence is present and passing.
- SC-12 terminal package badge/privacy evidence is present and passing.
- Fixture preflight and L6 procedure checks are passing.
- Local tile evidence confirms local `/tiles` use and external tile requests 0 in the harness/style fixture path.
- Board convergence evidence covers SC-02 through SC-12.
