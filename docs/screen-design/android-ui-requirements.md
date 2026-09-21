# Android UI 요구와 과거 검증

AUI-T01~T14의 긴 작업 계획에서 필요한 사용 의도와 검증 한계를 남겼다. 아래 내용은 현재 구현 완료 목록이 아니다. 변경할 화면의 코드·API와 사용자 합의를 먼저 확인하며, 플랫폼 규칙은 [Android AGENTS.md](../../android/AGENTS.md)를 따른다.

## 화면을 다듬을 때 확인할 요구

| 흐름 | 남길 의도·확인 위치 |
|---|---|
| 공통 탐색·상태 | Android는 현장 입력, Web은 지휘 상황판이다. 사건·차수·업무폰 맥락과 입력 초안을 탐색 중 유지한다. 로딩·빈 목록·오류·오프라인을 구분하고 저장 버튼의 중복 실행을 막는다. |
| 업무폰 확인·사건 선택 | 관리 설정 누락·미관리 업무폰·통신 불가·서버 거부를 구분한다. 정상 인증 후 배정 0건은 빈 목록으로 안내한다는 기존 요구와 실제 인증 거부 조건을 [인증 기록](../features/authentication.md)과 대조한다. |
| 패키지·지도·경로 | 패키지가 덜 준비됐다는 경고와 기록 차단을 구분한다. 로컬 준비·서버 ACK·상황판 반영은 별개다. 실패 항목 재시도와 경로 시작·일시정지·재개·종료는 [패키지](../features/offline-package.md)·[경로](../features/search-path.md)에서 확인한다. |
| 마커·사진 | 최소 입력으로 생성하고, 수정·삭제 권한과 확인 절차를 구분한다. 사진 업로드 후 첨부가 실패해도 초안·재시도 대상을 잃지 않아야 한다. [마커·사진 기록](../features/marker-photo.md)을 확인한다. |
| 미전송 기록·알림 | 평상시 미전송 수와 사용자 개입이 필요한 차단 상태를 구분한다. 로컬 저장·서버 반영·알림 확인을 같은 성공으로 표시하지 않는다. 알림에서 해당 위치로 이동할 수 있어야 한다. [오프라인 처리](../features/offline-sync.md)·[전달과 재전송](../features/event-delivery.md)을 확인한다. |
| 인수인계 | 이전 근무의 요약 조회와 새 메모 작성을 구분한다. 앱은 현장 인수인계를 지원하며 차수 비교는 웹에서 다룬다는 기존 요구를 [인수인계 기록](../features/handover.md)과 대조한다. |
| 사건 종료 | 앱 전체에서 종료를 알리고 새 쓰기를 막는다. 남은 초안·미전송 원본의 보존과 개인정보 파기가 충돌하므로 즉시 일괄 삭제를 전제하지 않는다. [종료 후 남은 기록](../features/offline-sync.md#사건-종료-후-남은-기록)을 먼저 확인한다. |

사건 맥락을 화면 상태로 관리하고 작은 사용자 설정만 DataStore에 두자는 설계, 멱등키를 UI가 아니라 Outbox에 보관하자는 의도는 현재 상태 관리·저장 코드와 대조한다. 당시 의존성 도입 표를 설치 목록으로 재사용하지 않으며 실제 의존성은 [버전 카탈로그](../../android/gradle/libs.versions.toml)에서 확인한다.

## 화면과 상태의 코드 위치

| 확인할 대상 | 코드 |
|---|---|
| 색·글자·간격·모양 | [theme](../../android/app/src/main/java/com/surimap/ui/theme). 선택 이유는 [화면 표현](screen-guidelines.md)에 보존 |
| 버튼·칩·배너·카드·행·상단 바·다이얼로그·시트 | [PoliComponents.kt](../../android/app/src/main/java/com/surimap/ui/components/PoliComponents.kt) |
| 접속 확인·사건 선택 | [AuthBootstrapScreen](../../android/app/src/main/java/com/surimap/feature/bootstrap/ui/AuthBootstrapScreen.kt), [IncidentListScreen](../../android/app/src/main/java/com/surimap/feature/incidents/ui/IncidentListScreen.kt) |
| 이전 근무 확인·메모 작성 | [DutyHandoverScreen](../../android/app/src/main/java/com/surimap/feature/handover/ui/DutyHandoverScreen.kt), [HandoverMemoScreen](../../android/app/src/main/java/com/surimap/feature/handover/ui/HandoverMemoScreen.kt) |
| 종료·처리 불가 알림 | [AppOverlayHost](../../android/app/src/main/java/com/surimap/ui/AppOverlayHost.kt) |
| 사건 맥락·화면 연결 | [PolicePhoneRoute](../../android/app/src/main/java/com/surimap/ui/navigation/PolicePhoneRoute.kt), [SuriMapApp](../../android/app/src/main/java/com/surimap/ui/SuriMapApp.kt) |

화면은 전달받은 상태를 표시하고 사용자 행동을 상위 흐름에 전달하려던 설계다. Composable이 있다는 사실만으로 저장·API·재전송 연결을 보장하지 않는다. 현재 [SuriMapSessionSnapshotStore](../../android/app/src/main/java/com/surimap/ui/session/SuriMapSessionSnapshotStore.kt)는 SharedPreferences에 복원용 값을 보존한다. 화면 상태와 프로세스 재시작 후 복구를 같은 것으로 설명하지 않는다.

## 그대로 다시 구현하지 않을 과거 계획

- 초기 계획의 AI 요약 생성·재시도 버튼은 후속 계획의 읽기 전용 요구와 충돌했다. 요약 API가 남아 있다는 이유만으로 버튼을 되살리지 않는다.
- 미전송 탭을 항상 노출하는 초기 계획과 차단 상태에서만 진입시키는 후속 계획이 다르다. 필요한 진입 방식은 실제 화면을 보고 정한다.
- 사건 카드의 실종자 요약·마지막 활동·현재 차수 정보가 당시 API에 부족하다는 메모가 있었다. 현재도 부족하다고 단정하거나 빈 값을 완료로 처리하지 않고 실제 응답과 대조한다.
- 전체 화면 알림·미루기·배터리 최적화 예외·키오스크, 음성·주사용 손·개인 인증 등은 보류된 제안이다. [Knox/MDM 계획](../proposals/knox-mdm.md)도 도입 결정 전까지 현재 구현 범위가 아니다.

## 무엇을 검증했는가

- AUI-T13은 공용 입력·컴포넌트·탐색 상태 검사를 기록했다. 당시 별도 ViewModel 대신 Activity/Nav 범위 상태를 검사했으며, 실제 서버·단말의 전체 흐름을 검증한 것은 아니다. 입력·로더·현재 검사 방법은 [공용 테스트 입력](../../test-fixtures/README.md)을 따른다.
- AUI-T14는 2026-05-11 Galaxy S22의 디버그 화면 표시 기록이다. [관찰 결과·검증 한계와 Git 원본](README.md#실기기-qa-자료의-쓰임)을 확인한다. 현재 API·네트워크 전환·Knox 검증의 통과 기록은 아니다.
- 이후 기능을 연결해 시험할 때는 [검증 흐름](../guides/feature-verification.md)에서 범위를 고른다. 옛 체크 표시나 대역 시험을 현재 완료 증거로 대신하지 않는다.

## 원문

[AUI-T01~T14의 계획·도입 보류 이유·명령·결과](https://github.com/sonic8-8/suri-map/blob/c88365473bc459637f1e3c664b30e241f7489715/docs/tasks/android-ui-tasks.md)와 [과거 Compose 대응표](https://github.com/sonic8-8/suri-map/blob/3cd777752e2178ebb3470e6f749b1521ce404e7a/docs/screen-design/compose-mapping.md)에서 상세 이력을 복원한다. 정리 전 미커밋 보완이었던 역사적 기록 주의와 현재 공용 입력·기능 문서 연결은 위에 반영했다.
