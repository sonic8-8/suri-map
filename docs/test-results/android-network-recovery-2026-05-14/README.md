# Android 네트워크 복구 시험 — 2026-05-14

당시 판정은 **PARTIAL / NOT FINAL**이다. 업무폰 1대의 일부 저장·복구는 확인했지만 자동 복구·상황판 표시·1시간 안정성을 모두 통과한 기록은 아니다. 2026-09-21 문서 정리에서는 저장된 원본을 대조했으며 단말·서버 시험을 다시 실행하지 않았다.

## 어떤 순서로 확인했는가

| 당시 시험 | 확인된 결과 | 해석의 한계 |
|---|---|---|
| 임시 터널을 통한 10회 네트워크 전환 | 앱 PID 유지, health 응답 복구. 패키지 전송은 `FAILED_FINAL / invalid_payload`, ACK된 마커의 로컬 상태는 `PENDING_SEND`로 남음 | 전환 로그는 00:28:04~00:30:36의 약 2분 32초다. 30분 단절·1시간 안정성 시험이 아니며 health 응답 복구도 데이터 복구 완료와 다름 |
| 수정 후 격리 DB·ADB reverse로 단절 1회 재현 | 오프라인 마커 저장 후 수동 WorkManager 실행으로 Outbox·로컬 마커 동기화, 상황판 API에 마커 1개 추가 | ADB 연결 제거·복원은 실제 이동통신·Wi-Fi 단절과 다르다. 자동 실행이 되지 않아 작업을 강제 실행했고 최종 1시간·10회 시험은 미실행 |
| 05-20 배포 서버 사전 점검 | 옛 기록에는 단말·경로 접근과 실행 시작 이후 중복 조회 0건으로 남음 | 당시 `.agents/scratch/l4d01-preflight-runner` 원본은 현재 작업 트리에 없다. 기록과 원본을 재대조하지 못했으며 최종 시험의 대체 증거가 아님 |

단말은 Samsung SM-S901N, Android 16 / SDK 36이었다. 처음의 2대 조건은 05-14 사용자 승인으로 업무폰 1대·상황판 1개로 줄였다. 이 변경은 여러 단말 간 동기화·위치 최신성 비교를 검증했다는 뜻이 아니다. 당시 시간·횟수는 앞으로 수행할 시험의 확정 기준으로 재사용하지 않는다.

## 원본에서 다시 확인한 내용

- **통신 복구와 데이터 반영은 달랐다.** `cycle-01-offline-after-marker-save.db`와 `cycle-01-recovered.db`는 바이트가 같고 마커가 `PENDING_SEND`다. `cycle-01-recovered-after-job2.db`에서야 마커·패키지 Outbox가 모두 `ACKED / SYNCED`, 로컬 마커가 `SYNCED`, 초안이 0건이 된다.
- `minute-00-suri-map.db`와 `after-force-flush-suri-map.db`도 같으며 패키지 전송이 아직 대기 중이다. 별도 초기화 뒤의 `fresh-after-package-flush-suri-map.db`는 ACK 상태다. 파일 이름에 `flush`가 있다고 성공으로 판정하지 않는다.
- 상황판 JSON은 마커 0→1개를 보여주지만 두 응답 모두 경로는 비어 있고 업무폰 최신성은 `LOST`다. 캡처는 **MapLibre 연결 대기** 화면이므로 지도·마커 표시 성공의 증거가 아니다. 앱 캡처도 경로 기록 대기·빈 지도 화면을 포함한다.
- 중복 조회 결과는 경로·마커·멱등 기록 0그룹, 이벤트 1그룹이다. 해당 그룹의 heartbeat 이벤트 ID 2개는 서로 다르다. 같은 업무폰·이벤트 유형으로 묶였다는 것만으로 같은 요청의 중복 저장을 입증하지 못한다.
- crash/ANR 파일은 전체 logcat이 아니라 당시 검색 결과 요약이다. 원본 4.5MB 로그는 커밋하지 않았다고 명시돼 있어 재검색한 결과로 보고하지 않는다.

## 원본의 보존과 복원

원본 27개는 현재 빌드·테스트의 입력이 아니므로 작업 트리에서 제거하고 Git 이력으로 보존했다. 제거 전 모든 파일이 커밋 `403b383d`의 원본과 바이트까지 같음을 확인했다. 당시 상태를 다시 조사할 때 아래 링크에서 필요한 파일을 복원한다.

| Git 원본 | 개수 | 확인할 내용 |
|---|---:|---|
| [network-cycles/](https://github.com/sonic8-8/suri-map/tree/403b383dc2693fd15d1616ee7206e6cb0e420df5/docs/evidence/android-network-recovery-2026-05-14/network-cycles) | 8 | 최초 부분 판정, 단말·전환 시각, DB 요약과 미전송 화면 |
| [marker-recovery/](https://github.com/sonic8-8/suri-map/tree/403b383dc2693fd15d1616ee7206e6cb0e420df5/docs/evidence/android-network-recovery-2026-05-14/marker-recovery) | 19 | DB 6개, 앱 화면·XML 8개, 상황판 JSON·화면 3개, 서버 중복 조회 2개 |

바이트가 같은 DB 사본도 서로 다른 관찰 시점에서 상태가 바뀌지 않았다는 기록이므로 Git 원본에는 함께 남아 있다. 파일 이름만으로 전송 성공을 판정하지 않고 위의 상태 차이를 함께 읽는다.

이번 원본 대조에서 JSON 2개·XML 6개를 파싱하고 화면 6장을 직접 확인했다. DB 6개는 읽기 전용으로 열어 `quick_check`, 테이블·행 수, 저장 상태와 요청·초안 JSON을 확인했다. 이 확인은 과거 자료의 해석이며 현재 앱의 결함 재현·해결 확인은 아니다.

## 옛 기록·도구의 복원

[L4-D01 원문](https://github.com/sonic8-8/suri-map/blob/c88365473bc459637f1e3c664b30e241f7489715/docs/tasks/l4-d01-stability-evidence.md)에 당시 명령·환경·Jira·수정 이력이 있다. Jira의 완료 표시와 최종 실행 증거 부재를 구분하며 미완료 검증은 후속 항목으로 유지한다. 삭제 전 미커밋 보완도 이 구분을 명시한 문장이었다.

| 제거한 도구 | 확인한 역할·제거 이유 |
|---|---|
| [check_l4_d01_runtime_preflight.py 원문](https://github.com/sonic8-8/suri-map/blob/c88365473bc459637f1e3c664b30e241f7489715/docs/tasks/check_l4_d01_runtime_preflight.py) | `adb devices -l` 결과에서 물리 단말 수만 검사한다. 현재 빌드·시험의 호출자가 없고 옛 시험 전용 문구·판정이 남아 있어 제거 |
| [run_l4_d01_deployed_stability.py 원문](https://github.com/sonic8-8/suri-map/blob/c88365473bc459637f1e3c664b30e241f7489715/docs/tasks/run_l4_d01_deployed_stability.py) | 옛 환경의 증거 수집기이며 최종 PASS 판정기가 아니다. 현재 호출자가 없어 제거하고 실행 방법은 원문으로 보존 |

수집기를 복원해 사용하려면 고정된 옛 서버 주소·Windows ADB 경로·EC2 전제·SQL 범위를 먼저 검토한다. `--backend-url`만 바꿔도 단말 ping 대상은 바뀌지 않는다. `--preflight-only`도 단말·서버에 접근하고, 네트워크 복구는 원래 상태와 무관하게 Wi-Fi·데이터를 모두 켠다. 중복 판정과 수집할 개인정보 범위도 새 시험에 맞춰 합의해야 한다. 이번 정리에서 대체 실행기나 새 검사는 만들지 않았다.
