# L5 Demo Smoke Procedure - Marker, Photo, Notification

## Scope

This procedure prepares the Phase 5 demo smoke for L5-T09C. It covers SC-06 marker/photo creation and SC-08 support request/person-found notification convergence only.

Do not record final pass/fail evidence in this document. Final evidence, screenshots, logs, defect links, and pass/fail judgment belong to L5-D01.

## Roles, Fixtures, Prereqs

| Item | Required setup |
|---|---|
| App actor | Incident-assigned team account or patrol car account on a registered, assigned PolicePhone |
| Command actor | Field commander role account with web board access |
| Observer | Records L5-D01 evidence only; does not edit this procedure during the smoke |
| Incident state | Mock/seed incident is `OPEN`, assigned to the app actor, with current OP active |
| Location fixture | Normal marker Point `[126.956500,37.571200]` inside the harness map envelope |
| Marker types | `CLUE`, `SUPPORT_REQUEST`, `PERSON_FOUND` available from the app marker bottom sheet |
| Photo fixture | `0` or `1` photo path from the harness; success photo size should be within `10_485_760 bytes` |
| Mock object storage | Use only `mock://object-storage/suri-map-harness` and upload URL `http://127.0.0.1:18080/mock-upload/{photoId}` |
| Mock FCM | Use only mock dispatcher capture for foreground, background, duplicate, recipient, payload, `eventId`, and `version` |
| Board | Web situation board open on the same incident with marker and toast slots visible |

External FCM and real S3 are explicitly out of scope. No request may go to real Firebase, `s3.amazonaws.com`, or a real bucket endpoint.

## Smoke Checklist

1. Confirm the app actor is online, logged in, assigned to the incident/current OP, and using the registered PolicePhone.
2. Confirm the board is open for the same incident and currently shows the active OP layer.
3. In the app marker bottom sheet, create a `CLUE` marker at the normal marker Point with a short memo and no photo.
4. Observe that the app shows saving, disables duplicate save action while saving, then shows saved feedback.
5. Target for L5-D01 capture: within 3 seconds, the board `marker` slot renders the new marker with the same marker id, status, version, OP, account, PolicePhone, and canonical Point.
6. Create or reopen a marker flow with `1` valid photo fixture attached.
7. Confirm the photo flow uses mock upload-url issuance, mock object upload, and attach in order.
8. Target for L5-D01 capture: attach success produces matching `photoId/status/version` across photo row, `event_dispatch_job`, SSE payload, and board marker/photo response; the board marker remains in the `marker` slot.
9. In the app, create a `SUPPORT_REQUEST` marker using one allowed request type such as `DRONE`, `POLICE_DOG`, or `OTHER`.
10. Target for L5-D01 capture: `SUPPORT_REQUEST_CREATED` is captured by mock FCM for missing-team command and field-commander recipients, and the web toast references the same `eventId`, marker id, status, and version as the marker response.
11. Put one receiving app instance in background if available, then create a `PERSON_FOUND` marker from the assigned app actor.
12. Target for L5-D01 capture: `PERSON_FOUND` is captured by mock FCM for all incident-assigned active devices, Android creates the local background notification from the data message, and the board `toast` slot displays a distinct person-found toast.
13. Replay the same notification event once through the mock channel if the harness exposes duplicate delivery.
14. Target for L5-D01 capture: duplicate delivery does not create duplicate web toasts, app banners, or OS notifications for the same `eventId`.

## Mock FCM Evidence Capture Plan

L5-D01 should capture mock dispatcher output only. For each support request and person-found notification, record:

- event type: `SUPPORT_REQUEST_CREATED` or `PERSON_FOUND`
- `eventId`, marker id, marker status, marker version
- recipient account/PolicePhone list and whether it is foreground or background
- payload fields for marker type, source account/team, PolicePhone name or type, OP, and location summary
- duplicate delivery result for the same `eventId`

Do not capture or require a real Firebase delivery receipt.

## Mock S3/Object Storage Evidence Capture Plan

L5-D01 should capture mock object storage output only. For the photo attach path, record:

- issued upload URL host and path, which must match `http://127.0.0.1:18080/mock-upload/{photoId}`
- object storage URI under `mock://object-storage/suri-map-harness`
- upload result, checksum or fixture validation result, and attach request result
- resulting `photoId/status/version` in attach response, event payload, SSE payload, and board response

Do not capture or require real S3 bucket state.

## Board Convergence Capture Targets

L5-D01 should capture one board screenshot or log set per target:

| Target | Capture expectation |
|---|---|
| Basic marker | `marker` slot shows the new `CLUE` marker within 3 seconds of app save |
| Photo marker | `marker` slot shows photo metadata after `MARKER_UPDATED.photoDelta` convergence |
| Support request | `marker` slot shows the support marker and `toast` slot shows one support request toast |
| Person found | `marker` slot shows the found marker and `toast` slot shows one emphasized person-found toast |
| Duplicate event | repeated same `eventId` leaves one toast/banner/notification, not multiple |

These are capture targets only. Final pass/fail evidence and defects are deferred to L5-D01.
