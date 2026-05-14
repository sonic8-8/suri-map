# L4-D01 Stability Evidence Gate

## Scope

This document records the current L4-D01 evidence state. It is not a final pass
claim until the execution result section is filled. L4-D01 was downscoped on
2026-05-14 by user approval to physical execution with Android PolicePhone 1대
and board 1개 for 1시간, including network on/off 10회, duplicate server row
0건, and convergence evidence.

## Run Metadata

| Field | Value |
|---|---|
| Task | `L4-D01` |
| Existing Jira | `S14P31C106-196` |
| Evidence gap Jira | `S14P31C106-300` |
| Branch | `docs/S14P31C106-300-l4-d01-one-device-downscope` |
| Checked at | `2026-05-14T09:53:21+09:00` |
| Completion scope | Downscoped to 1 physical Android PolicePhone + 1 board session by user approval on 2026-05-14. |
| Required sources | `docs/prd.md §2.2`, `docs/prd.md §2.3`, `docs/spec/harness-scenarios.md §2 SC-05`, `docs/spec/harness-scenarios.md §2 SC-07`, `docs/spec/harness-scenarios.md §2 SC-09` |
| Execution protocol | `docs/tasks/l4-network-switch-stability-protocol.md` |
| Runtime preflight | `python3 docs/tasks/check_l4_d01_runtime_preflight.py --adb <adb-path>` |

## Evidence Sink Check

| Check | Result |
|---|---|
| Jira `S14P31C106-196` status | DONE, but no physical evidence was attached to the work item. |
| Jira attachments/comments/worklog | BLOCKED. attachment 0건, comment 0건, worklog 0건. |
| Remote branch | BLOCKED. `feature/S14P31C106-196-stability-network-switch-validation` is an ancestor of current `origin/develop`; no L4-D01 evidence commit is ahead of develop. |
| Repository evidence | BLOCKED. docs/tasks 하위에 L4-D01 final evidence 문서 없음. |
| Task checklist | BLOCKED. `docs/tasks/L4-tasks.md` keeps `L4-D01` unchecked. |
| Device availability | PASS for downscoped preflight. Windows ADB saw one physical Android device, `SM-S901N` / `R3CT50BD92Y`, in `device` state. |

## Required Final Evidence

The final L4-D01 pass claim must include all of the following:

| Required evidence | Expected |
|---|---|
| 1-hour stability run result | Android PolicePhone 1대 and board 1개 stay alive for 1시간 without crash, ANR, process death, or board session loss. |
| Network on/off 10-cycle result | Each cycle records offline entry, offline duration, recovery, and post-recovery state for the PolicePhone. |
| Local state convergence | path, marker, and package local state converge from pending/offline states to synced or documented final states. |
| Duplicate row verification | duplicate server row 0건 for path, marker, package/outbox replay checks after recovery. |
| Board convergence evidence | path, marker, package_badge, and police_phone_freshness board slots converge for the single PolicePhone after recovery. |

## Downscope Risk

The 2026-05-14 downscope allows one physical PolicePhone as L4-D01 completion
evidence. The following two-device behaviors remain outside this final claim:

| Excluded evidence | Reason |
|---|---|
| PolicePhone A/B divergence | Only one physical PolicePhone is available. |
| Two-device freshness comparison | `police_phone_freshness` comparison across two heartbeat rows is not covered. |
| Multi-device board convergence gate | Board evidence covers one PolicePhone and one board session only. |

## 2026-05-14 Single-Device Partial Result

Artifacts: `docs/tasks/artifacts/l4-d01-single-device-20260514/`.

This run used one physical Samsung SM-S901N on Android 16 / SDK 36. The app was
installed as `com.surimap` and launched as `com.surimap/.MainActivity`.
The local backend was current develop and was exposed to the phone through a
temporary `trycloudflare.com` tunnel because WSL and Windows LAN addresses were
not reachable from the device network.

| Check | Result |
|---|---|
| Device availability | PARTIAL PASS. Windows ADB saw exactly one physical device in `device` state. WSL ADB still saw no devices because `usbipd` was unavailable. |
| App bootstrap | PASS. After tunnel stabilization, the app reached the incident selection screen for police phone `00000000-0000-0000-0000-000000000101`. |
| Network on/off cycles | PARTIAL PASS. 10/10 off phases became network unreachable, and 10/10 on phases recovered health check access. Recovery took 1-2 attempts, maximum about 20s. |
| Runtime stability | PARTIAL PASS. App PID `17392` stayed alive throughout the 10 cycles. `FATAL EXCEPTION`, `ANR`, and `am_crash` patterns were not observed. |
| Search map after recovery | PARTIAL. Search map remained open after the cycles, but it showed `미전송 1건 처리 불가`. |
| Package local state | FAIL/PARTIAL. Local package installation showed `READY` with 7/7 items, but the package installation outbox row stayed `FAILED_FINAL` / `invalid_payload`. |
| Marker local state | FAIL/PARTIAL. Marker outbox row was `ACKED` / `SYNCED`, but the mirrored `local_marker.sync_status` remained `PENDING_SEND`, and a marker draft still existed. |
| Duplicate server row | NOT FINAL. This run did not execute the required two-device duplicate-row gate. |
| Board convergence | NOT FINAL. Board comparison against two PolicePhones was not possible with one physical device. |

Historical verdict: app runtime survived one-device network switching, but local
convergence was not clean. This result is bug-finding evidence only because it
predates the follow-up fixes and the current downscoped execution.

Follow-up defects:

| Jira | Scope |
|---|---|
| `S14P31C106-307` | Android package installation outbox `invalid_payload` failure. |
| `S14P31C106-308` | Marker ACK leaves local marker/draft in pending state. |
| `S14P31C106-310` | Android outbox replay retry/auth policy alignment found during follow-up validation. |
| `S14P31C106-312` | Offline package tile checksum convergence issue found during post-fix single-device validation. |

Follow-up status as of 2026-05-14: `S14P31C106-307`, `S14P31C106-308`,
`S14P31C106-310`, `S14P31C106-312`, `S14P31C106-313`, `S14P31C106-294`,
and `S14P31C106-314` are completed and merged. These fixes improve the
single-device baseline and tileserver runtime baseline. A fresh one-device
execution is still required after the downscope.

## 2026-05-14 Runtime Preflight Recheck

Command:

```bash
python3 docs/tasks/check_l4_d01_runtime_preflight.py --adb '/mnt/c/Users/SSAFY/AppData/Local/Android/Sdk/platform-tools/adb.exe'
```

Historical result at `2026-05-14T04:47:48+09:00`: BLOCKED under the previous
two-device criterion.

Windows ADB reported one ready physical Android device:
`SM_S901N` / `R3CT50BD92Y`. The runtime preflight was polled 6 times over
about 60 seconds, and every attempt reported 1 physical device found, 2
required. The current downscoped preflight requires 1 physical device.

## 2026-05-14 Downscoped Execution Attempt

Artifacts: `docs/tasks/artifacts/l4-d01-one-device-downscope-20260514/`.

Runtime setup used one physical Samsung SM-S901N (`R3CT50BD92Y`) as the APP
PolicePhone and one Playwright browser board session. The backend used an
isolated PostgreSQL database `surimap_l4d01_20260514`, and the app talked to
the WSL backend through `adb reverse tcp:8080 tcp:18080`. Because the installed
debug build uses `http://127.0.0.1:8080`, network switching in this attempt was
implemented by removing and restoring the ADB reverse binding. This is API
transport loss simulation, not a full cellular/Wi-Fi network-loss proof.

Server fixture setup:

- Incident import succeeded for incident
  `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001` / OP
  `88888888-8888-8888-8888-888888880001`.
- Active overall search area
  `48954f0e-51ea-4c58-aa57-2b442300b503` was created through
  `POST /api/search-areas` with `areaLevel=OVERALL`.
- DutyShift `8d4380df-7383-46ac-a0ee-be203fae62e5` was ACTIVE for
  PolicePhone `00000000-0000-0000-0000-000000000101`.

Observed result:

| Check | Result |
|---|---|
| App runtime baseline | PASS. Fresh app data reached the incident search map with active DutyShift, `전체 수색 구역`, `수색 기록 중`, `경로 기록 가능`, and `마커 생성 가능`. |
| Board runtime baseline | PASS/PARTIAL. Board route opened with MapLibre tile auth after a local CORS proxy for `/tiles`; screenshot `board/current-board-screenshot.png` and board API returned `overall_search_area`, `package_badge`, and `police_phone_freshness`. The current web shell does not render board slots directly on screen. |
| Package local state | PASS with manual flush. Fresh local package installation outbox moved from `PENDING/PENDING_SEND` to `ACKED/SYNCED` after forced WorkManager job `cmd jobscheduler run -f -n androidx.work.systemjobscheduler com.surimap 1`. |
| Offline marker local state | PASS. With `adb reverse` removed, a SUPPORT_REQUEST/DRONE marker was saved locally as `MARKER / PENDING_SEND`; local marker `b7974c2d-e5f1-42f0-ab4f-d131aed5ea7b` was visible in `local_marker` as `PENDING_SEND`. |
| Marker recovery | PASS. After restoring `adb reverse`, forced WorkManager job `2` replayed the marker outbox to `ACKED/SYNCED`, and `local_marker.sync_status` became `SYNCED`. |
| Board marker convergence | PASS. `/api/incidents/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001/board` returned one `marker` slot row for server marker `fcadbbda-0841-4101-abb3-1b1da1ca6de7`, type `SUPPORT_REQUEST`, status `ACTIVE`, PolicePhone `00000000-0000-0000-0000-000000000101`. |
| Duplicate domain row checks | PARTIAL. `search_path`, `marker`, and `idempotency_record` duplicate groups were 0 after marker recovery. |
| Protocol duplicate event check | FAIL/PARTIAL. The protocol `event_dispatch_job` duplicate query returned one duplicate group for repeated `POLICE_PHONE_HEARTBEAT_UPDATED` events on the same PolicePhone. This came from heartbeat rechecks during setup and prevents a strict final L4-D01 PASS claim. |
| 1-hour stability | NOT EXECUTED. This attempt stopped after the first offline marker/recovery cycle because the duplicate-event query and transport-simulation limitation already prevented final closure. |
| Network on/off 10 cycles | NOT EXECUTED. Only cycle 1 was executed. |

Additional runtime finding: the app can reach the backend through ADB reverse,
but WorkManager's `NetworkType.CONNECTED` constraint waits for Android's
validated internet capability. In this local transport setup the outbox replay
jobs did not auto-run, so jobs were forced with `cmd jobscheduler run -f`.
This is acceptable as diagnostic evidence for local replay behavior, but not as
unassisted field-network recovery proof.

## Next Action

Run `docs/tasks/l4-network-switch-stability-protocol.md` again on one physical
Android PolicePhone and one board session using a transport that satisfies both
the app API base URL and Android validated network constraints, or explicitly
record a team-approved protocol exception for ADB reverse based transport
simulation. Do not check L4-D01 complete until the 1-hour run, 10 network
cycles, and duplicate/convergence evidence are captured in the repository or
attached to Jira.

Before starting the final run, execute
`python3 docs/tasks/check_l4_d01_runtime_preflight.py --adb <adb-path>` and
confirm it reports at least one ready physical Android device. If it reports
`BLOCKED`, the run cannot start.

## Completion Verdict

Verdict: PARTIAL / NOT FINAL.

`S14P31C106-196` is marked complete in Jira, but the repository and Jira work
item do not contain final downscoped physical execution evidence yet. The
2026-05-14 attempt proves one-device package and marker local replay can
converge, but it does not satisfy the 1-hour, 10-cycle, and strict duplicate
event query gates. `S14P31C106-300` tracks the missing evidence.
