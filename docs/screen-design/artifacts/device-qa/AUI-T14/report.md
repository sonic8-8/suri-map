# AUI-T14 Android Device QA

2026-05-11의 실기기 표시 확인 기록이다. 아래 당시 결과와 마지막의 2026-09-21 원본 대조 내용을 구분한다. 새 제품 동작 시험을 실행한 보고서가 아니다.

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

The QA-state screenshots have matching `*.xml` UIAutomator dumps. The real-launch screenshot `p1-auth-bootstrap-awake.png` has no matching XML in this directory (inventory checked 2026-09-21).

## Checks

- `adb devices -l`: `R3CT50BD92Y device product:r0qksx model:SM_S901N device:r0q`
- Touch target scan from UIAutomator bounds: 0 clickable nodes below 48 px in captured screens.
- Long-clickable scan: 0 long-click-only confirm controls. The only long-clickable nodes are standard text fields in P6-B and P9.
- Visual review: no incoherent text overlap or blocking control overlap observed on the captured 1080 x 2340 device screenshots.
- External domain fallback: P5 uses the local Compose mock map shell for this phase. Logcat was scanned for `openstreetmap`, `mapbox`, `googleapis`, `s3.amazonaws`, and `google.com`; `p5-external-domain-scan.txt` records no matches.
- Sync/offline evidence: `p5-synced.png` shows `동기화`, `p5-search-map.png` shows normal pending `미전송 1 · 2분`, and `p5-blocked-outbox.png` plus `blocked-outbox.png` show blocked outbox handling.

## Notes

- Live network on/off behavior still needs a managed test profile and backend fixture to validate product state transitions end to end. This pass verifies the actual Android components and device rendering states without introducing release-only bypasses.

## 원본 대조와 해석 범위 (2026-09-21)

- 화면 12개를 직접 보고 XML 11개의 표시 문구·클릭 영역을 대조했다. 48px 미만인 clickable node는 없었지만 단말 밀도 환산이나 48dp 접근성 충족을 검증한 것은 아니다.
- `p5-search-map.png`는 강조 알림·이전 근무 배너로 지도 영역이 거의 가려진다. 다른 P5/P8 상태도 가용 지도 면적이 달라진다. 당시 “겹침 없음”을 지도 가시성·현장 조작 목표의 통과로 확대하지 않는다.
- `p5-blocked-outbox.png`의 안내는 처리 불가 2건, `blocked-outbox.png`의 진단은 4건이다. 서로 다른 QA 상태이며 같은 큐의 연속 복구·수렴 증거로 사용하지 않는다.
- P6-A의 요약 준비됨·P5의 동기화 표시는 주입한 화면 상태다. 서버 요약 생성·실제 전송 성공을 입증하지 않는다. GPS 5초/전송 10초·입력 500자도 당시 표시값이다.
- `p5-external-domain-scan.txt`는 지정한 도메인 문자열을 찾지 못했다는 결과만 담고 있다. 입력 로그 `p5-synced-logcat.txt`는 이 디렉터리에 없어 검색을 재실행하지 못했다. 외부 통신이 전혀 없었다는 보장은 아니다.
- PNG·XML·검색 결과는 수정하지 않았다. [정리 전 보고서](https://github.com/sonic8-8/suri-map/blob/3cd777752e2178ebb3470e6f749b1521ce404e7a/docs/screen-design/artifacts/device-qa/AUI-T14/report.md)의 당시 명령·판정도 복원할 수 있다.
