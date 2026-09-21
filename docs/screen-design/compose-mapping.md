# Android 시안과 Compose 코드의 대응

HTML 시안의 색·공통 UI·화면을 Android로 옮길 때 참고했던 기록이다. 현재 값을 복제한 대응표 대신 실제 선언 위치를 연결한다.

| 확인할 대상 | 코드 |
|---|---|
| 색·글자·간격·모양 | [theme](../../android/app/src/main/java/com/surimap/ui/theme)와 [토큰 배경](dense-tokens.md) |
| 버튼·칩·배너·카드·행·상단 바·다이얼로그·시트 | [PoliComponents.kt](../../android/app/src/main/java/com/surimap/ui/components/PoliComponents.kt) |
| 관리 업무폰 접속 | [AuthBootstrapScreen](../../android/app/src/main/java/com/surimap/feature/bootstrap/ui/AuthBootstrapScreen.kt) |
| 배정 사건 선택 | [IncidentListScreen](../../android/app/src/main/java/com/surimap/feature/incidents/ui/IncidentListScreen.kt) |
| 이전 근무 확인·메모 작성 | [DutyHandoverScreen](../../android/app/src/main/java/com/surimap/feature/handover/ui/DutyHandoverScreen.kt), [HandoverMemoScreen](../../android/app/src/main/java/com/surimap/feature/handover/ui/HandoverMemoScreen.kt) |
| 종료·처리 불가 알림 표시 | [AppOverlayHost](../../android/app/src/main/java/com/surimap/ui/AppOverlayHost.kt) |
| 사건 맥락·화면 연결 | [탐색 상태](../../android/app/src/main/java/com/surimap/ui/navigation/PolicePhoneRoute.kt), [SuriMapApp](../../android/app/src/main/java/com/surimap/ui/SuriMapApp.kt) |

## 표시와 저장·전송을 구분

- 화면은 전달받은 상태를 표시하고 사용자 행동을 상위 흐름에 전달하려는 설계였다. 실제 Composable의 존재만으로 API·저장·재전송 연결을 보장하지 않는다.
- 정상 오프라인·자동 복구와 처리 불가 안내를 구분한다. 별도 진단 화면은 사용자가 확인할 실패 이유를 보여주려던 목적이다.
- 이전 근무 화면은 [인수인계 요구](../handover.md)에 맞춰 원본·서버 생성 요약을 확인한다. 옛 시안의 요약 생성 버튼을 되살리지 않는다.
- 원문은 사건 맥락을 Activity/탐색 상태로 두고 DataStore에는 저장하지 않는다고 적었다. 현재는 [SuriMapSessionSnapshotStore](../../android/app/src/main/java/com/surimap/ui/session/SuriMapSessionSnapshotStore.kt)가 SharedPreferences로 복원용 값을 보존한다. 화면 상태와 프로세스 재시작 후 복구를 같은 것으로 설명하지 않는다.
- HTML px와 Android dp는 그대로 등치하지 않는다. 터치·겹침 검증 범위는 [실기기 기록](artifacts/device-qa/AUI-T14/report.md)에 있다.

색 이름 1:1 대응·컴포넌트 변형·과거 화면 이름은 [정리 전 원문](https://github.com/sonic8-8/suri-map/blob/3cd777752e2178ebb3470e6f749b1521ce404e7a/docs/screen-design/compose-mapping.md)에서 확인한다.
