# L6 Demo Smoke Procedure - Board, Package, Tile, Terminal

## Scope

This procedure prepares the Phase 5 demo smoke for L6-T10D and the L6-D01 pre-run. It covers SC-03 package/tile readiness, SC-11 OP/search_history_summary rendering guard, SC-12 terminal/sanitized close, and board convergence for SC-02 through SC-12.

This procedure is a checklist only. Final pass/fail evidence, screenshots, logs, linked defects, and pass/fail judgment belong to L6-D01, not this procedure. Do not record final pass/fail evidence here.

The smoke is aligned with PRD §2.2 acceptance expectations for offline map display, board responsiveness, duplicate-free convergence, and stable multi-device operation. It also supports PRD §2.3 by preparing the board/package/tile and terminal checks for the 12-step demonstration checklist.

## Roles, Fixtures, Prereqs

| Item | Required setup |
|---|---|
| Command actor | Field commander role account with web board access to the fixture incident |
| App actor | Incident-assigned account on a registered, assigned PolicePhone with offline package UI available |
| Observer | Records L6-D01 evidence only; does not edit this procedure during the smoke |
| Incident fixture | `incidentId=inc-precinct-first-001` |
| Tile manifest | `tileManifestId=tile-manifest-inc-precinct-001` |
| Local tile URI | `local://tiles/inc-precinct-first-001/{z}/{x}/{y}.pbf` |
| Tile key range | `z=15..16`, `x=27925..27960`, `y=12680..12720` |
| Overall area hash | `overall-area-hash-precinct-current` |
| Network scripts | `net-script-manifest-001`, `net-script-tile-blob-001` |
| Board assembly | Use `mock board API refetch/assembly` for convergence probes |
| Board slots | `package_badge`, `op_toggle`, `handover_memo`, `search_history_summary`, `incident_terminal` visible or probeable |
| Terminal inputs | Close/tombstone fixtures loaded for SC-12 sanitized terminal state |

External tile hosts are forbidden. The smoke must not call `*.tile.openstreetmap.org`, `*.mapbox.com`, or `*.googleapis.com`. Use local `/tiles` capture for tile requests and local fixture/blob resolution only.

## board/package/tile demo smoke checklist

1. Confirm the board is open for `incidentId=inc-precinct-first-001` and can probe the current board response.
2. Confirm the app actor is assigned to the incident and current OP on a registered PolicePhone.
3. Confirm the SC-03 offline package manifest fixture is current: `tileManifestId=tile-manifest-inc-precinct-001`, `overall-area-hash-precinct-current`, and tile URI `local://tiles/inc-precinct-first-001/{z}/{x}/{y}.pbf`.
4. Run or stage `net-script-manifest-001` for manifest success, retryable failure, and recovery readiness without recording final evidence here.
5. Run or stage `net-script-tile-blob-001` for tile blob success, per-tile retryable failure, checksum/corrupt handling, and recovery readiness without using external tile hosts.
6. Target for L6-D01 capture: SC-03 package state drives the board `package_badge` from incomplete or warning state to ready state after package installation convergence.
7. Confirm local `/tiles` capture is enabled before rendering map tiles in the app or board.
8. Target for L6-D01 capture: tile requests stay inside the local fixture route and no request is made to `*.tile.openstreetmap.org`, `*.mapbox.com`, or `*.googleapis.com`.
9. Confirm `mock board API refetch/assembly` is ready for board convergence probes after each relevant scenario event.
10. Target for L6-D01 capture: board convergence for SC-02 through SC-12 keeps each slot at the latest accepted version and ignores stale lower-version or duplicate event input.
11. Prepare SC-11 data with OP1/OP2 comparison, handover memo, and search history summary fixtures.
12. Target for L6-D01 capture: SC-11 OP/search_history_summary rendering guard shows OP comparison and summary state while hiding recommendation, missing-area certainty, and risk-judgment CTA or text.
13. Prepare SC-12 terminal close fixtures and the sanitized tombstone response probe.
14. Target for L6-D01 capture: SC-12 renders sanitized terminal board state and does not expose active missing person data after close.

## local tile network capture plan

L6-D01 should capture local tile traffic only. Before the smoke, configure the browser, app harness, proxy, or network recorder to distinguish fixture-local tile traffic from external network traffic.

Capture targets for the later L6-D01 evidence set:

- manifest request for `incidentId=inc-precinct-first-001`
- manifest identity `tileManifestId=tile-manifest-inc-precinct-001`
- tile URI template `local://tiles/inc-precinct-first-001/{z}/{x}/{y}.pbf`
- tile ranges `z=15..16`, `x=27925..27960`, `y=12680..12720`
- area identity `overall-area-hash-precinct-current`
- manifest script `net-script-manifest-001`
- tile script `net-script-tile-blob-001`
- local `/tiles` capture showing fixture-local requests only

Any observed request to `*.tile.openstreetmap.org`, `*.mapbox.com`, or `*.googleapis.com` must be treated as an external tile host violation and routed to L6-D01 defect tracking. Do not add a pass/fail result to this procedure.

## SC-11 Rendering Guard Preparation

1. Confirm SC-10 materialized seed or equivalent precondition exists before executing SC-11.
2. Confirm the board can render OP1/OP2 comparison without changing source geometry, marker, memo, or path status/version.
3. Confirm `search_history_summary` can show loading, success, failure, or unavailable states from the mock provider.
4. Confirm forbidden output is prepared for guard verification: next-area recommendation, automatic missing-area certainty, and risk-judgment language.
5. Target for L6-D01 capture: the board hides forbidden recommendation/risk/missing-area CTA state while preserving OP comparison and summary source evidence.

## terminal state convergence capture target

L6-D01 should capture one board screenshot or log set per target:

| Target | Capture expectation |
|---|---|
| `incident_terminal` | Board terminal slot converges after SC-12 close and exposes sanitized closed state only |
| `package_badge` | Package badge reflects terminal/package purge or tombstone state after close |
| `tombstone response` | Closed incident read returns the sanitized tombstone response fields only |
| `missing_person absence` | Terminal board state and tombstone response do not expose active `missing_person` data |
| `no live SSE resubscribe after close` | Web does not establish a new live SSE tail or render new live events after terminal close |

These are capture targets only. Final pass/fail evidence and defects are deferred to L6-D01.
