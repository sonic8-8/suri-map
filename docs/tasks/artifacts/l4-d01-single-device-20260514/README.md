# L4-D01 Single-Device Partial Rehearsal Artifacts

이 디렉터리는 `S14P31C106-300`의 단일 실기기 부분 리허설 증거다. 최종
`L4-D01` 통과 증거가 아니다.

## 실행 환경

| 항목 | 값 |
|---|---|
| 실행 시각 | 2026-05-14 00:28:04-00:30:36  |
| 기기 | Samsung SM-S901N |
| Android | 16, SDK 36 |
| 앱 | `com.surimap`, `com.surimap/.MainActivity` |
| 서버 | current develop backend, local `18080` exposed through a temporary `trycloudflare.com` tunnel |
| 제한 | 물리 Android PolicePhone 1대만 사용 가능 |

## 결과 요약

| 항목 | 결과 |
|---|---|
| 앱 실행 | PASS. 앱 PID `17392`가 10회 네트워크 토글 후에도 유지됨. |
| 네트워크 off | PASS. 10/10회 `curl` health check가 DNS/network unreachable로 실패함. |
| 네트워크 on | PASS. 10/10회 health check가 회복됨. 복구 시도는 1-2회, 최대 약 20초. |
| crash/ANR | PASS. `FATAL EXCEPTION`, `ANR`, `am_crash` 로그 없음. |
| 최종 화면 | PARTIAL. Search map 화면은 유지됐고 `미전송 1건 처리 불가` 경고가 표시됨. |
| local convergence | FAIL/PARTIAL. marker outbox는 `ACKED/SYNCED`이나 `local_marker.sync_status`가 `PENDING_SEND`로 남음. |
| package convergence | FAIL/PARTIAL. package installation outbox가 `FAILED_FINAL` / `invalid_payload`로 남음. |

## 파일

| 파일 | 내용 |
|---|---|
| `device-runtime.txt` | 연결 기기, Android 버전, 앱 PID |
| `network-cycles.txt` | 네트워크 off/on 10회 결과 |
| `db-summary-after-cycles.txt` | Room DB 요약 쿼리 결과 |
| `surimap-ui-tunnel-retry.xml` | 터널 안정화 후 사건 선택 화면 XML |
| `surimap-ui-after-cycles.xml` | 10회 토글 후 Search map 화면 XML |
| `surimap-after-cycles.png` | 10회 토글 후 화면 캡처 |
| `logcat-crash-check.txt` | crash/ANR 패턴 확인 요약 |

## 판정

이 실행은 단일 기기에서 앱이 실제 네트워크 차단/복구 10회를 견딘다는 부분 증거다.
그러나 2대 PolicePhone + board 1개 조건을 만족하지 못했고, local/package convergence
결함이 관찰됐으므로 `L4-D01`과 `S14P31C106-300`은 계속 BLOCKED로 둔다.
