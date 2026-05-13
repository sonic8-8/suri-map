# L4-D01 Stability Evidence Gate

## Scope

This document records the current L4-D01 evidence state. It is not a final pass
claim. L4-D01 requires physical execution with Android PolicePhone 2대 and board
1개 for 1시간, including network on/off 10회, duplicate server row 0건, and
convergence evidence.

## Run Metadata

| Field | Value |
|---|---|
| Task | `L4-D01` |
| Existing Jira | `S14P31C106-196` |
| Evidence gap Jira | `S14P31C106-300` |
| Branch | `feature/S14P31C106-300-l4-d01-single-device-partial-template` |
| Checked at | `2026-05-13T16:53:20+09:00` |
| Required sources | `docs/prd.md §2.2`, `docs/prd.md §2.3`, `docs/spec/harness-scenarios.md §2 SC-05`, `docs/spec/harness-scenarios.md §2 SC-07`, `docs/spec/harness-scenarios.md §2 SC-09` |
| Execution protocol | `docs/tasks/l4-network-switch-stability-protocol.md` |

## Evidence Sink Check

| Check | Result |
|---|---|
| Jira `S14P31C106-196` status | DONE, but no physical evidence was attached to the work item. |
| Jira attachments/comments/worklog | BLOCKED. attachment 0건, comment 0건, worklog 0건. |
| Remote branch | BLOCKED. `feature/S14P31C106-196-stability-network-switch-validation` is an ancestor of current `origin/develop`; no L4-D01 evidence commit is ahead of develop. |
| Repository evidence | BLOCKED. docs/tasks 하위에 L4-D01 final evidence 문서 없음. |
| Task checklist | BLOCKED. `docs/tasks/L4-tasks.md` keeps `L4-D01` unchecked. |
| Device availability | BLOCKED. `adb devices -l` returned 연결된 Android device 0대. User-confirmed available physical Android device count is 1대, which is insufficient for the final 2-PolicePhone gate. |

## Required Final Evidence

The final L4-D01 pass claim must include all of the following:

| Required evidence | Expected |
|---|---|
| 1-hour stability run result | Android PolicePhone 2대 and board 1개 stay alive for 1시간 without crash, ANR, process death, or board session loss. |
| Network on/off 10-cycle result | Each cycle records offline entry, offline duration, recovery, and post-recovery state for both PolicePhones. |
| Local state convergence | path, marker, and package local state converge from pending/offline states to synced or documented final states. |
| Duplicate row verification | duplicate server row 0건 for path, marker, package/outbox replay checks after recovery. |
| Board convergence evidence | path, marker, package_badge, and police_phone_freshness board slots converge after recovery. |

## Single-Device Partial Rehearsal

The current available physical device count is 1대. A one-device run is allowed
only as partial rehearsal evidence. It must not close `L4-D01`, transition
`S14P31C106-300` to done, or check `docs/tasks/L4-tasks.md`.

Partial rehearsal may capture:

| Partial evidence | Scope |
|---|---|
| Device availability | `adb devices -l` shows exactly one Android device in `device` state. |
| App runtime | Debug build installs and launches on the single PolicePhone without crash or ANR during the rehearsal window. |
| Network on/off cycles | 10 cycles are executed on the single PolicePhone and each cycle records offline entry, recovery, and measured duration. |
| Local state | path, marker, package, and outbox states are captured on the single device before offline, during offline, and after recovery. |
| Board observation | If a board session is available, board convergence is recorded as a single-device observation only. |
| Duplicate check | Duplicate query output may be recorded after recovery, but it does not replace the two-device final duplicate evidence. |

Partial rehearsal cannot cover:

| Missing final evidence | Reason |
|---|---|
| PolicePhone A/B divergence | Only one physical PolicePhone is available. |
| Two-device freshness comparison | `police_phone_freshness` needs two device heartbeat rows for final comparison. |
| Full board convergence gate | Final board evidence must compare two PolicePhones and one board session over the 1-hour run. |
| Final pass/fail | `L4-D01` requires the full 2-PolicePhone protocol. |

## Next Action

Connect the single available physical Android device and run the partial
rehearsal first. Record the result as `partial evidence, final blocked` in this
document and Jira `S14P31C106-300`.

Run `docs/tasks/l4-network-switch-stability-protocol.md` on 실제 장치 with two
Android PolicePhone devices and one board session when the second physical
device becomes available. Do not check L4-D01 complete until the full execution
artifacts are captured in the repository or attached to Jira.

## Completion Verdict

Verdict: BLOCKED.

`S14P31C106-196` is marked complete in Jira, but the repository and Jira work
item do not contain the required physical execution evidence. `S14P31C106-300`
tracks the missing evidence.
