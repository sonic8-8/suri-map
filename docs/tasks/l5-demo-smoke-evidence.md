# L5-D01 Demo Smoke Evidence - Marker, Photo, Notification

## Scope

Evidence sink for the L5-D01 marker/photo/notification demo smoke. This evidence follows `docs/tasks/l5-demo-smoke-procedure.md` and covers:

- SC-06 field marker creation and photo attach through mock object storage.
- SC-08 support request and person-found notification convergence through mock FCM and board `marker`/`toast` slots.

External Firebase Cloud Messaging and real S3/object storage are explicitly out of scope. Evidence below is mock-only.

## Run Metadata

| Field | Value |
|---|---|
| Task | `L5-D01` |
| Jira | `S14P31C106-172` |
| Branch | `feature/S14P31C106-172-marker-photo-notification-demo-evidence` |
| Base SHA | `04b5fb2` |
| Evidence timestamp | `2026-05-08T17:22:22+09:00` |
| Test report | `backend/build/reports/tests/test/index.html` |
| Test XML directory | `backend/build/test-results/test/` |

## Executed Evidence Commands

| # | Command | Purpose | Result | Artifact / log pointer |
|---|---|---|---|---|
| 1 | `cd backend && env GRADLE_USER_HOME=/tmp/codex-gradle bash -lc "sed 's/\r$//' gradlew \| bash -s -- test --no-daemon --tests com.surimap.harness.sc06.Sc06MarkerPhotoHarnessRedTest --tests com.surimap.harness.sc08.Sc08NotificationHarnessRedTest --tests com.surimap.marker.photo.adapter.MockObjectStorageTest --tests com.surimap.marker.notification.adapter.MockFcmDispatcherTest --tests com.surimap.marker.notification.SupportRequestNotificationDispatchTest --tests com.surimap.marker.notification.PersonFoundNotificationDispatchTest"` | SC-06/SC-08 smoke evidence plus mock S3/FCM support tests | PASS, `BUILD SUCCESSFUL in 14s` | `backend/build/reports/tests/test/index.html` |

Selected XML evidence:

| Test suite | Result |
|---|---|
| `TEST-com.surimap.harness.sc06.Sc06MarkerPhotoHarnessRedTest.xml` | 2 tests, 0 failures, 0 errors |
| `TEST-com.surimap.harness.sc08.Sc08NotificationHarnessRedTest.xml` | 4 tests, 0 failures, 0 errors |
| `TEST-com.surimap.marker.photo.adapter.MockObjectStorageTest.xml` | 26 tests, 0 failures, 0 errors |
| `TEST-com.surimap.marker.notification.SupportRequestNotificationDispatchTest.xml` | 3 tests, 0 failures, 0 errors |
| `TEST-com.surimap.marker.notification.PersonFoundNotificationDispatchTest.xml` | 4 tests, 0 failures, 0 errors |
| `TEST-com.surimap.marker.notification.adapter.MockFcmDispatcherTest$*.xml` | 19 tests across nested suites, 0 failures, 0 errors |

## Mock S3 / Object Storage Evidence

Required boundary: use only `mock://object-storage/suri-map-harness` and upload URL host/path `http://127.0.0.1:18080/mock-upload/{photoId}`. No evidence depends on `s3.amazonaws.com` or a real bucket endpoint.

| Evidence item | Result |
|---|---|
| Upload URL issuance | PASS. SC-06 harness and `MockObjectStorageTest.uploadUrlUsesHarnessEndpointAndPhotoId` require `http://127.0.0.1:18080/mock-upload/{photoId}`. |
| Object storage URI | PASS. `mock://object-storage/suri-map-harness`. |
| Upload result | PASS. Valid `1` photo fixture within `10_485_760 bytes` is accepted by mock storage. |
| Fixture/checksum validation | PASS. Mock storage records checksum metadata and failure keys including `checksum-mismatch`. |
| Attach request result | PASS. SC-06 one-photo path attaches after upload-url issuance and mock upload. |
| Photo row | PASS. `photo-precinct-clue-001`, `ATTACHED`, version `2`. |
| `event_dispatch_job` | PASS. `MARKER_UPDATED`, `evt-s5-marker-updated-photo-001`, status `UPDATED`, version `2`, `photoDeltaStatus=ATTACHED`. |
| SSE payload | PASS. `evt-s5-marker-updated-photo-001`, marker `mk-precinct-clue-001`, status `UPDATED`, version `2`. |
| Board marker/photo response | PASS. Board `marker` slot converged with marker `mk-precinct-clue-001`, photo `photo-precinct-clue-001`, board response `bs-inc-precinct-first-001`, version `2`. |
| External S3 calls | PASS. `externalS3Called=false`; no real S3 or bucket endpoint observed. |

## Mock FCM Evidence

Required boundary: capture mock dispatcher output only. No real Firebase delivery receipt is required or recorded.

| Evidence item | `SUPPORT_REQUEST_CREATED` result | `PERSON_FOUND` result |
|---|---|---|
| `eventId` | PASS. `evt-s5-support-request-001`. | PASS. `evt-s5-person-found-001`. |
| Marker id/status/version | PASS. Dynamic marker id maps to fixture `mk-precinct-support-001`, status `ACTIVE`, version `1`. | PASS. Dynamic marker id maps to fixture `mk-precinct-person-found-001`, status `ACTIVE`, version `1`. |
| Recipient accounts | PASS. `acct-cmd-alpha`, `acct-support-cmd`, `acct-support-car`, `acct-support-team`. | PASS. `acct-precinct-cmd`, `acct-precinct-car`, `acct-precinct-team`, `acct-cmd-alpha`, `acct-team-alpha`, `acct-support-cmd`, `acct-support-car`, `acct-support-team`. |
| Recipient PolicePhone / FCM aliases | PASS. PolicePhone IDs `dev-alpha-phone-01`, `dev-support-car-01`, `dev-support-phone-01`; FCM aliases `fcm:dev-alpha-phone-01`, `fcm:dev-support-car-01`, `fcm:dev-support-phone-01`. | PASS. Recipient PolicePhone IDs `dev-precinct-car-01`, `dev-precinct-phone-01`, `dev-alpha-phone-01`, `dev-support-car-01`, `dev-support-phone-01`; active mock FCM aliases `fcm:dev-alpha-phone-01`, `fcm:dev-support-car-01`, `fcm:dev-support-phone-01`. |
| Foreground capture | PASS. Mock dispatcher captured foreground data message. | PASS. Mock dispatcher captured foreground data message. |
| Background capture / Android local notification | PASS. Background data message captured by mock boundary; Android local notification remains owner-specific runtime evidence. | PASS. Background data message captured by mock boundary; Android local notification remains owner-specific runtime evidence. |
| Payload marker type | PASS. `SUPPORT_REQUEST`. | PASS. `PERSON_FOUND`. |
| Payload source account/team | Not claimed. Current backend notification payload contract does not include source account/team display fields; evidence is limited to recipient account IDs and source `policePhoneId`. | Not claimed. Current backend notification payload contract does not include source account/team display fields; evidence is limited to recipient account IDs and source `policePhoneId`. |
| Payload PolicePhone name/type | Not claimed. Current backend notification payload has `policePhoneId` and no `deviceId`; it does not include PolicePhone display name/type fields. | Not claimed. Current backend notification payload has `policePhoneId` and no `deviceId`; it does not include PolicePhone display name/type fields. |
| Payload OP and location summary | PASS. Payload includes `opId`, marker context, and `locationLabel`. | PASS. Payload includes `opId`, marker context, and `locationLabel`. |
| Duplicate delivery for same `eventId` | PASS. One logical toast/banner/notification only. | PASS. One logical toast/banner/notification only. |
| External FCM calls | PASS. `externalFcmCalled=false`, production FCM adapter not loaded. | PASS. `externalFcmCalled=false`, production FCM adapter not loaded. |

## Board Marker / Toast Convergence

The evidence run used log/test-report artifacts rather than screenshots. The SC-06 and SC-08 harness suites assert board response convergence directly.

| Target | Result |
|---|---|
| Basic marker | PASS. Board `marker` slot contains marker `mk-precinct-clue-001`, source spec `S5`, board response `bs-inc-precinct-first-001`, stale refetch rejected. |
| Photo marker | PASS. `MARKER_UPDATED.photoDelta` converges to board `marker` slot with photo `photo-precinct-clue-001`, status `ATTACHED`, version `2`. |
| Support request | PASS. Board `marker` slot and `toast` slot converge for `SUPPORT_REQUEST_CREATED`; toast ledger applies the event and suppresses duplicates. |
| Person found | PASS. Board `marker` slot and `toast` slot converge for `PERSON_FOUND`; emphasized person-found toast path is distinct from support request. |
| Duplicate event | PASS. Replayed same `eventId` leaves one toast/banner/notification, not multiple; duplicate ledger status is `DUPLICATE`. |

## Explicit External-Service Statement

This L5-D01 evidence was collected with mock infrastructure only:

- S3/object storage: `mock://object-storage/suri-map-harness` and `http://127.0.0.1:18080/mock-upload/{photoId}` only.
- FCM: mock dispatcher capture only.
- Real external services: no Firebase delivery, no `s3.amazonaws.com`, and no real bucket endpoint.

Observed external calls: `0`.

## Linked Defect Task List

| Defect | Link | Evidence target | Status |
|---|---|---|---|
| None | N/A | All L5-D01 evidence targets | No defect task created |

## Residual Risk

- Android-local notification rendering of source account/team display text and PolicePhone display name/type is not proven by this backend mock evidence. The current backend notification payload contract provides IDs, recipient lists, `markerType`, and `locationLabel`; Android/UI copy evidence remains owner-specific runtime evidence.

## Completion Verdict

Verdict: PASS.

Completion criteria:

- SC-06 command evidence is present and passing.
- SC-08 command evidence is present and passing.
- Mock S3/object storage evidence includes upload-url issuance, mock upload, attach, and matching `photoId/status/version` across row, outbox, SSE, and board response.
- Mock FCM evidence includes support request, person-found, foreground/background, recipient, current contract payload fields, `eventId`, version, and duplicate delivery behavior.
- Board convergence evidence covers `marker` and `toast` slots.
- External-service boundary evidence confirms zero real FCM/S3 calls.
