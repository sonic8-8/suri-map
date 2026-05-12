# AUI-T14 Android Device QA

## Device

- Date: 2026-05-11
- Device: Samsung Galaxy S22 `SM-S901N`
- ADB serial: `R3CT50BD92Y`
- APK: `android/app/build/outputs/apk/debug/app-debug.apk`
- Install command: `cd android && ./gradlew --no-daemon :app:installDebug`
- Result: `BUILD SUCCESSFUL`, installed on `SM-S901N - 16`

## Scope

The real launch flow reaches P1-B only because this local device does not have production Knox/MDM managed configuration or a reachable internal backend. For P2/P3/P5/P6/P8/P9, the same Compose components were opened on the physical device through a `src/debug` QA-only Activity. This Activity is not included in release source sets.

## Screenshots

| Screen | Evidence |
|---|---|
| P1-B real app bootstrap, unmanaged/local backend failure | `p1-auth-bootstrap-awake.png` |
| P1-B QA managed-server-rejected state | `p1-auth-bootstrap.png` |
| P2 assigned incident list | `p2-incident-list.png` |
| P3 offline package loading | `p3-offline-package.png` |
| P5 search map, offline pending + handover + alert | `p5-search-map.png` |
| P5 search map, synced chip | `p5-synced.png` |
| P5 blocked outbox notice | `p5-blocked-outbox.png` |
| P6-A handover summary | `p6-handover-summary.png` |
| P6-B handover memo | `p6-handover-memo.png` |
| P8 incident alert banner | `p8-incident-alert.png` |
| P9 marker detail | `p9-marker-detail.png` |
| Blocked outbox diagnostic | `blocked-outbox.png` |

Each screenshot also has a matching `*.xml` UIAutomator dump.

## Checks

- `adb devices -l`: `R3CT50BD92Y device product:r0qksx model:SM_S901N device:r0q`
- Touch target scan from UIAutomator bounds: 0 clickable nodes below 48 px in captured screens.
- Long-clickable scan: 0 long-click-only confirm controls. The only long-clickable nodes are standard text fields in P6-B and P9.
- Visual review: no incoherent text overlap or blocking control overlap observed on the captured 1080 x 2340 device screenshots.
- External domain fallback: P5 uses the local Compose mock map shell for this phase. Logcat was scanned for `openstreetmap`, `mapbox`, `googleapis`, `s3.amazonaws`, and `google.com`; `p5-external-domain-scan.txt` records no matches.
- Sync/offline evidence: `p5-synced.png` shows `동기화`, `p5-search-map.png` shows normal pending `미전송 1 · 2분`, and `p5-blocked-outbox.png` plus `blocked-outbox.png` show blocked outbox handling.

## Notes

- Live network on/off behavior still needs a managed test profile and backend fixture to validate product state transitions end to end. This pass verifies the actual Android components and device rendering states without introducing release-only bypasses.
