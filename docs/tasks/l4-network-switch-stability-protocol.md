# L4 Network Switch Stability Protocol

## Metadata

- Task: L4-T10C
- Jira: S14P31C106-194
- Scope: Phase 5 pre-run/procedure scope only. This document defines the pre-run checks, observer roles, capture points, and network switch protocol to execute before Phase 5.
- Boundary: the 1-hour stability and network on/off final pass/fail evidence belongs to L4-D01, not this procedure. This procedure must not record final pass/fail evidence.

## References And Harness Coverage

- PRD §2.2: offline field capture must preserve local user work during network loss.
- PRD §2.3: recovery must synchronize without duplicate domain rows and with visible queue convergence.
- Harness scenarios: SC-05, SC-07, SC-09.
- Incident fixture: incidentId=inc-precinct-first-001.
- GPS and path fixtures: gps-path-normal-001 and path-precinct-mixed-001.
- Marker fixture: mk-precinct-clue-001.
- Network scripts: net-script-domain-write-001, net-script-heartbeat-001, net-script-outbox-flush-001.
- Tile manifest fixture: tileManifestId=tile-manifest-inc-precinct-001.
- Board slots under observation: package_badge, path, marker, police_phone_freshness.

## Observer Roles

- Lead observer: owns cycle numbering, timestamp alignment, and confirms this remains a capture-only procedure.
- PolicePhone A observer: captures device UI, local queue state, and GPS/path/marker activity.
- PolicePhone B observer: repeats the same observation set on the second PolicePhone to detect device divergence.
- Board observer: records board slot convergence for package_badge, path, marker, and police_phone_freshness.
- Log observer: collects artifacts and logs from app, backend, harness, network script runner, and database queries.
- Defect recorder: records any blocker, follow-up, or defect without converting this procedure into final pass/fail evidence.

## Expected Offline And Recovery States

SC-07 offline expectations:

- Domain writes made while offline enter PENDING_LOCAL and then PENDING_SEND as applicable.
- Device network status shows OFFLINE.
- Korean offline copy is visible as "오프라인 / 기록 중".
- Korean unsent queue copy is visible as "미전송 큐".

SC-09 recovery expectations:

- Recovery state progresses SENDING -> ACKED.
- Final synchronized UI state for the observed items is SYNCED.
- Queue text converges to "미전송 0".
- Pending visual state shows "pending 해제".
- Duplicate observation target is "duplicate 생성 0건".
- Stale board references use STALE_REFETCH when the board receives old or missing slot data.

## Pre-Run Steps Before Phase 5

1. Confirm the Phase 5 environment is using incidentId=inc-precinct-first-001 and tileManifestId=tile-manifest-inc-precinct-001.
2. Confirm both PolicePhones are logged in, assigned to the same incident, and able to display gps-path-normal-001.
3. Confirm path-precinct-mixed-001 can be loaded and that mk-precinct-clue-001 is available for marker write observation.
4. Confirm the board renders the package_badge, path, marker, and police_phone_freshness slots before any network switching starts.
5. Confirm net-script-domain-write-001, net-script-heartbeat-001, and net-script-outbox-flush-001 are script-ready and can be invoked manually if the harness is not driving them.
6. Start log capture for Android/device logs, frontend console if available, backend application logs, network script output, and database query output.
7. Record blockers, follow-up items, and defects in the run notes with timestamps and artifact links only. Do not record final pass/fail evidence here.

## 1-Hour Stability Checklist, Capture Only

This 1-hour checklist covers 2 PolicePhones and 1 board. It is capture only, with no final pass/fail decision, because L4-D01 owns final 1-hour pass/fail evidence.

- At minute 0, capture both PolicePhones showing the incident screen and the board showing package_badge, path, marker, and police_phone_freshness.
- Keep continuous GPS/path collection recording active on both PolicePhones for the whole hour, using gps-path-normal-001 as the harness path input.
- Record the SC-05 10-second batch cadence evidence from the app collector or harness log, including batch timestamp, point count, and outbox enqueue state.
- Every 10 minutes, capture PolicePhone A queue state, PolicePhone B queue state, and board slot freshness.
- Every 10 minutes, capture board path and police_phone_freshness convergence together so path drawing and device heartbeat freshness can be compared on the same timestamp.
- Every 10 minutes, record whether heartbeat script output from net-script-heartbeat-001 is current or delayed.
- Every 10 minutes, record runtime stability signals for both apps and the board session: foreground/background state, process alive, crash/ANR count, memory trend if available, and heartbeat age.
- At each capture point, record artifacts/logs paths and timestamps.
- If an anomaly appears, record a blocker, follow-up, or defect with reproduction notes and continue capture if safe.
- Do not mark final pass/fail in this document or this procedure.

## Network On/Off 10-Cycle Protocol

Use this as a manual protocol or script-ready set of steps for 10-cycle network on/off coverage. Cycle numbering must be recorded as cycles 1 to 10.

For each cycle #1 through #10:

1. Cycle number: record `cycle=N/10` where N is the current cycle from 1 to 10.
2. Online baseline: capture both PolicePhones and the board before switching the network off.
3. Switch network off or invoke the script-ready off step.
4. Offline duration checkpoint: hold the offline state for the configured interval and record the measured offline duration. Cycle #1 must cover the SC-07 30-minute offline mode; cycles #2 through #10 may use the shorter harness interval if L4-D01 records the exact duration.
5. While offline, create or observe domain activity using net-script-domain-write-001 and verify PENDING_LOCAL, PENDING_SEND, OFFLINE, "오프라인 / 기록 중", and "미전송 큐" where applicable.
6. App restart queue persistence checkpoint: after the offline write reaches PENDING_SEND, restart one PolicePhone app and verify the local outbox queue and "미전송 큐" are still visible after application restart.
7. Offline negative check: while still offline, confirm server domain rows, event_dispatch_job rows, SSE dispatch, and board rows are not created for the offline write. Record the 0 row/0건 observation as capture-only protocol evidence.
8. Switch network on or invoke the script-ready on step.
9. Online recovery checkpoint: observe SENDING -> ACKED, SYNCED, "미전송 0", and "pending 해제".
10. Queue drain observation: record the time from online restoration until the unsent queue drains to "미전송 0".
11. Invoke or observe net-script-outbox-flush-001 and net-script-heartbeat-001 output for recovery timing.
12. For at least one recovery, run the partial-recovery mode where domain write returns 200 but SSE/board delivery is delayed and confirm STALE_REFETCH appears. For at least one later recovery, run duplicate-retransmission mode with the same idempotency key and confirm "duplicate 생성 0건".
13. Capture board slot convergence observation for package_badge, path, marker, and police_phone_freshness, including any STALE_REFETCH event.

## Board Slot Convergence Observation

Board slot convergence observation is required before Phase 5 execution and during each network on/off recovery checkpoint.

- package_badge: capture whether the offline package indicator reflects the current incident package state.
- path: capture whether gps-path-normal-001 and path-precinct-mixed-001 converge after recovery.
- marker: capture whether mk-precinct-clue-001 appears once and updates without duplicate markers.
- police_phone_freshness: capture freshness timestamps for both PolicePhones after heartbeat recovery.
- If a slot remains stale, record whether STALE_REFETCH appears and attach artifacts/logs.

## Artifacts And Logs Capture

- Device screen recordings or screenshots for both PolicePhones.
- Board screenshots for all observed board slots.
- Android/device logs covering offline entry, local write, recovery, ACKED, and SYNCED transitions.
- Backend logs for domain writes, outbox flush, heartbeat, and duplicate protection paths.
- Network script logs for net-script-domain-write-001, net-script-heartbeat-001, and net-script-outbox-flush-001.
- Database query outputs from the duplicate row verification query section.
- Blocker, follow-up, and defect records with timestamps, observer name, incidentId, cycle number if applicable, and artifact references.

## Duplicate Row Verification SQL Query Section

L4-T10C prepares these duplicate row verification SQL/query blocks and records binding readiness only. The blocks are L4-D01 execution-time steps for the Phase 5 run: when L4-D01 executes the protocol, run them after each recovery checkpoint that writes data and once after cycle 10. Each query is capture-only evidence for this procedure; final duplicate pass/fail evidence remains in L4-D01.

Execution binding:

- Bind `incidentId=inc-precinct-first-001` to the fixture incident UUID used by the integrated Phase 5 database. The current common fixture UUID is `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001`.
- Bind `path-precinct-mixed-001` and `mk-precinct-clue-001` through the fixture loader if those aliases are materialized separately from the physical UUID primary keys.
- The expected result for every duplicate query is zero returned rows. Non-zero output is recorded as an observation here and becomes L4-D01 final pass/fail evidence only when L4-D01 executes.

```sql
with fixture as (
  select 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001'::uuid as incident_uuid
),
scoped_paths as (
  select sp.id,
         ds.police_phone_id,
         sp.started_at,
         sp.ended_at
  from search_path sp
  join duty_shift ds on ds.id = sp.duty_shift_id
  join operational_period op on op.id = ds.operational_period_id
  join fixture f on f.incident_uuid = op.incident_id
)
select 'duplicate check search_path' as check_name,
       police_phone_id,
       started_at,
       ended_at,
       count(*) as row_count,
       count(*) - count(distinct id) as duplicate_count
from scoped_paths
group by police_phone_id, started_at, ended_at
having count(*) > 1;
```

```sql
with fixture as (
  select 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001'::uuid as incident_uuid
),
scoped_paths as (
  select sp.id
  from search_path sp
  join duty_shift ds on ds.id = sp.duty_shift_id
  join operational_period op on op.id = ds.operational_period_id
  join fixture f on f.incident_uuid = op.incident_id
)
select 'duplicate check search_path_segment' as check_name,
       search_path_id,
       movement_type,
       started_at,
       ended_at,
       count(*) as duplicate_count
from search_path_segment
where search_path_id in (select id from scoped_paths)
group by search_path_id, movement_type, started_at, ended_at
having count(*) > 1;
```

```sql
with fixture as (
  select 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001'::uuid as incident_uuid
)
select 'duplicate check marker' as check_name,
       incident_id,
       operational_period_id,
       police_phone_id,
       marker_type,
       occurred_at,
       memo,
       count(*) as duplicate_count
from marker
where incident_id = (select incident_uuid from fixture)
group by incident_id, operational_period_id, police_phone_id, marker_type, occurred_at, memo
having count(*) > 1;
```

```sql
with fixture as (
  select 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001'::uuid as incident_uuid
),
scoped_markers as (
  select id
  from marker
  where incident_id = (select incident_uuid from fixture)
)
select 'duplicate check photo' as check_name,
       marker_id,
       object_key,
       checksum_sha256,
       count(*) as duplicate_count
from photo
where marker_id in (select id from scoped_markers)
group by marker_id, object_key, checksum_sha256
having count(*) > 1;
```

```sql
with fixture as (
  select 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001'::uuid as incident_uuid
),
scoped_markers as (
  select id
  from marker
  where incident_id = (select incident_uuid from fixture)
)
select 'duplicate check marker_notification' as check_name,
       marker_id,
       count(*) as duplicate_count
from marker_notification
where marker_id in (select id from scoped_markers)
group by marker_id
having count(*) > 1;
```

```sql
with fixture as (
  select 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001'::uuid as incident_uuid
)
select 'duplicate check event_dispatch_job' as check_name,
       source_entity_type,
       source_entity_id,
       event_type,
       count(*) as duplicate_count,
       array_agg(event_id order by occurred_at) as observed_event_ids
from event_dispatch_job
where incident_id = (select incident_uuid from fixture)
group by source_entity_type, source_entity_id, event_type
having count(*) > 1;
```

```sql
with fixture as (
  select 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001'::uuid as incident_uuid
)
select 'duplicate check idempotency_record' as check_name,
       idempotency_key,
       request_path,
       request_method,
       count(*) as duplicate_count
from idempotency_record
where incident_id = (select incident_uuid from fixture)
group by idempotency_key, request_path, request_method
having count(*) > 1;
```

```sql
with fixture as (
  select 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001' as incident_alias,
         'tile-manifest-inc-precinct-001' as manifest_id
)
select 'duplicate check offline_package_installation' as check_name,
       opi.offline_package_manifest_id,
       opi.police_phone_id,
       count(*) as duplicate_count
from offline_package_installation opi
join offline_package_manifest opm on opm.id = opi.offline_package_manifest_id
join fixture f on f.manifest_id = opi.offline_package_manifest_id
where opm.incident_id = f.incident_alias
group by opi.offline_package_manifest_id, opi.police_phone_id
having count(*) > 1;
```

## Blocker And Follow-Up Defect Recording

- Record a blocker when the procedure cannot continue, such as both PolicePhones unable to load the incident or network scripts unavailable.
- Record a follow-up when observation is incomplete but the procedure can continue, such as missing board screenshot for one cycle.
- Record a defect when observed behavior differs from the expected offline/recovery states, including missing PENDING_LOCAL, missing PENDING_SEND, missing OFFLINE, missing SENDING -> ACKED, missing SYNCED, missing queue drain, or duplicate 생성 0건 not observed.
- Every blocker, follow-up, or defect must include observer role, timestamp, cycle number if applicable, artifact/log link, and a short reproduction note.
- Do not close this document with final pass/fail. L4-D01 owns the 1-hour and on/off final pass/fail evidence.
