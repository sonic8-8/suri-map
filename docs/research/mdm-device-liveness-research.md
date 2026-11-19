# MDM으로 업무폰 통신 상태를 확인할 수 있는지 조사

- 조사일: 2026-11-18
- 질문: 상황판의 업무폰 통신 상태(ONLINE, STALE, LOST)를 앱 heartbeat 대신 MDM 같은 인프라 계층의 값으로 만들 수 있는가?
- 상태: 조사 결과다. 수리맵 설계 결정은 하지 않았다.
- 배경: [ADR-0037](../adr/0037-police-phone-authentication-at-infrastructure.md)에서 업무폰 인증은 인프라 계층에서 하기로 정했다. 현재 통신 상태의 기존 요구와 구현 차이는 [인증과 접근 권한 기능 정의 문서](../features/authentication.md)에 있다.
- 이어지는 조사: 단말 신뢰를 어느 계층에서 확인하는지는 [단말 신뢰 계층 조사](device-trust-layer-research.md)에 있다. 업무폰 등록 확인 요구는 [업무폰 등록 확인 요구 조사](device-check-requirement-research.md)에 있다. 두 문서에서 다룬 Android Management API의 Device 자원 개요, 공유 단말 모델, 국가 사이버보안 기본지침, N2SF 해설서, 2012년과 2022년 경찰 모바일 업무 기사는 다시 쓰지 않는다.

이 문서에서 **사실:** 은 출처에 적힌 내용이다. **추론:** 은 조사자의 해석이다. 출처의 접근일은 모두 2026-11-18이다. 영어 출처의 필드 이름과 설정 이름은 원문 표기를 그대로 옮겼다.

## 결론 요약

1. **값과 주기.** 조사한 MDM과 EMM에서는 모두 "단말이 마지막으로 관리 서버와 통신한 시각"을 제공한다. 기본 주기는 Intune이 약 8시간이다. Workspace ONE UEM의 비활성 판정 기본값은 480분이다. Knox Manage의 연결 끊김 판정 기간은 3일에서 365일 사이다. Teams 단말의 Offline 신호는 120분마다 평가된다. 분 단위로 줄일 수 있다고 문서에 적힌 값은 Workspace ONE의 Heartbeat Interval과 Knox Manage의 위치 수집 주기뿐이다. Workspace ONE 값은 분 단위로 설정하지만 최솟값은 문서에서 확인하지 못했다. Knox Manage 위치 수집 주기의 최솟값은 30분이다. Android Management API에는 상태 보고 주기를 정하는 정책 필드가 없다. 갱신 방식은 단말 쪽 주기 보고와 FCM 같은 push로 단말을 깨우는 방식이 섞여 있다.
2. **외부 서버에서 읽기.** 네 제품 모두에서 REST API로 마지막 통신 시각을 읽을 수 있다. 확인한 필드는 Android Management API의 `lastStatusReportTime`, Intune Graph의 `lastSyncDateTime`, Knox Manage OpenAPI의 `lastConnectionDate`다. Workspace ONE UEM의 API 필드 이름은 1차 출처로 확인하지 못했다. 이벤트로 받을 수 있는 것은 Android Management API의 Pub/Sub `STATUS_REPORT` 알림뿐이다. Intune, Knox Manage, Workspace ONE의 공개 이벤트 목록에는 마지막 통신 시각이나 연결 끊김 이벤트가 없었다.
3. **알 수 있는 것과 없는 것.** **추론:** MDM 값으로 알 수 있는 것은 "단말의 관리 에이전트가 관리 서버에 닿았다"는 사실이다. 수리맵 앱이 실행 중인지, 어느 계정으로 앱에 로그인했는지, 앱이 수리맵 서버와 통신하는지는 MDM 값만으로 알 수 없다. 앱 단위에 가까운 값은 두 가지만 확인했다. 하나는 Intune 앱 보호 정책의 사용자별, 앱별 마지막 동기화 시각이다. 다른 하나는 앱이 직접 보내는 Android Management API의 keyed app state다. 두 값 모두 앱에 SDK나 라이브러리를 넣어야 한다.
4. **현장 업무 제품.** 1차 출처를 확인한 Esri Field Maps, Microsoft Teams 단말, Zebra Device Tracker는 통신 상태를 MDM check-in이 아니라 앱이나 전용 단말 에이전트의 보고로 만든다. Zebra 문서에서 EMM은 앱 설치와 설정에 쓰인다. Motorola와 Hexagon의 CAD, AVL unit status는 1차 출처를 찾지 못했다.
5. **경찰청 폴리폰.** 경찰청 폴리폰이나 국내 공공 모바일 업무환경에서 MDM으로 단말 통신 상태를 확인한다는 공개 1차 자료는 찾지 못했다.

## 1. MDM과 EMM에서 제공하는 통신 상태 값

### 제품별 요약

| 제품 | 통신 상태 값 | 문서에 적힌 주기와 지연 | 설정으로 바꿀 수 있는 값 | 갱신 방식 |
|---|---|---|---|---|
| Android Management API | `lastStatusReportTime`, `lastPolicySyncTime` | 상태 보고 주기는 적혀 있지 않다. 앱 관련 이벤트는 최대 60분 안에 보고된다. | 보고 항목을 켜고 끄는 필드만 있다. 주기 필드는 없다. | 단말 보고, FCM으로 정책 push |
| Microsoft Intune | `lastSyncDateTime` | 정기 동기화 약 8시간. 6.5시간에 한 번만 허용된다. | 확인하지 못했다. 관리자가 단말별 동기화를 요청할 수 있다. | 변경 알림 push, 단말 정기 동기화 |
| Samsung Knox Manage | Last seen, `lastConnectionDate`, 상태 Enrolled와 Disconnected | 연결 끊김 판정 기간 3일에서 365일 | keepalive 확인 주기(시간 단위), 위치 수집 주기 30분에서 24시간 | 에이전트 정기 보고와 이벤트 보고 |
| Omnissa Workspace ONE UEM | Last Seen | 비활성 판정 기본값 480분 | Heartbeat Interval(분), Device Inactivity Timeout(분) | Hub check-in, FCM 또는 AWCM push |

### Android Management API

- **사실:** Device 자원의 `lastStatusReportTime` 설명은 "The last time the device sent a status report."다. `lastPolicySyncTime` 설명은 "The last time the device fetched its policy."다. ([enterprises.devices](https://developers.google.com/android/management/reference/rest/v1/enterprises.devices))
- **사실:** 정책의 `statusReportingSettings`에는 `applicationReportsEnabled`, `deviceSettingsEnabled`, `networkInfoEnabled`, `powerManagementEventsEnabled` 같은 보고 항목 스위치가 있다. 조사자가 읽은 필드 목록에는 보고 주기를 정하는 필드가 없었다. ([enterprises.policies](https://developers.google.com/android/management/reference/rest/v1/enterprises.policies))
- **사실:** Device 자원 문서와 알림 문서에는 상태 보고를 얼마나 자주 보내는지 적혀 있지 않다. ([enterprises.devices](https://developers.google.com/android/management/reference/rest/v1/enterprises.devices), [Pub/Sub 알림](https://developers.google.com/android/management/notifications))
- **사실:** 알림 문서의 지연 표에는 앱 관련 이벤트만 있다. 높은 우선순위 keyed app state는 "Immediate, at most one report per minute"다. 보통 우선순위 keyed app state는 "Within one minute"다. 그 밖의 단말 앱 이벤트는 "Within 60 minutes"다. 이 값은 통제된 조건에서 정한 best effort 목표라고 적혀 있다. ([Pub/Sub 알림](https://developers.google.com/android/management/notifications))
- **사실:** Android Enterprise 망 요구 문서에는 `fcm.googleapis.com`의 용도로 "EMM Console <-> DPC communication, like pushing configs"가 적혀 있다. 같은 표에는 Android Management API 호출용 `androidmanagement.googleapis.com`이 있다. ([Android Enterprise 망 요구](https://support.google.com/work/android/answer/10513641?hl=en))
- **추론:** 정책 변경은 FCM push로 단말에 전달된다. 상태 보고는 단말 쪽에서 올라온다. 보고 주기는 Google이 정하는 것으로 보인다. EMM 관리자가 바꾸는 설정은 찾지 못했다. 폐쇄망에서는 Google 서버에 닿지 못하면 값이 갱신되지 않는다.

### Microsoft Intune

- **사실:** Intune 동기화는 변경 기반, 클라이언트 시작, 단일 단말의 세 종류다. 변경 기반 동기화에서는 정책이나 앱 배정이 바뀌면 온라인 단말에 동기화 알림이 간다. 알림 시간은 "immediately up to a few hours"다. 전원이 꺼진 단말은 알림을 받지 못한다. 그 단말은 다음 동기화 때 정책을 받는다. ([Intune 정책 갱신 주기](https://learn.microsoft.com/en-us/intune/device-configuration/troubleshoot-device-profiles))
- **사실:** 정기 동기화(maintenance sync)의 예상 주기는 모든 플랫폼에서 약 8시간이다. 클라이언트 일정과 관계없이 정기 동기화는 6.5시간에 한 번만 허용된다. ([Intune 정책 갱신 주기](https://learn.microsoft.com/en-us/intune/device-configuration/troubleshoot-device-profiles))
- **사실:** 새로 등록한 Android 단말은 처음 15분 동안 3분마다 동기화한다. 그다음 2시간 동안은 15분마다 동기화한다. 그 뒤에는 약 8시간마다 동기화한다. ([Intune 정책 갱신 주기](https://learn.microsoft.com/en-us/intune/device-configuration/troubleshoot-device-profiles))
- **사실:** 관리자는 관리 센터에서 단말 하나의 동기화를 시작할 수 있다. 같은 문서에는 Intune 보고서에 단말의 최신 상태가 반영되기까지 시간이 더 걸릴 수 있다고 적혀 있다. ([Intune 정책 갱신 주기](https://learn.microsoft.com/en-us/intune/device-configuration/troubleshoot-device-profiles))
- **사실:** GMS가 없는 환경에서는 push 알림을 쓰지 못할 수 있다. 장치 관리자나 AOSP 관리로 등록한 Android 단말은 8시간마다 Intune에 보고한다. 최신 Company Portal이나 Intune 앱에서는 약 15분마다 새 작업을 확인한다. 이 주기는 제조사, 사용 패턴, 배터리 최적화의 영향을 받는다. ([GMS 없는 환경의 Intune](https://learn.microsoft.com/en-us/intune/app-management/manage-without-gms))
- **사실:** 준수 상태 유효 기간(Compliance status validity period)은 기본 30일이다. 1일에서 120일 사이로 설정할 수 있다. 이 기간 안에 준수 상태를 보고하지 않은 단말은 비준수로 처리된다. ([Intune 준수 정책](https://learn.microsoft.com/en-us/intune/device-security/compliance/overview))
- **추론:** Intune의 통신 시각은 시간 단위로 갱신된다. 관리자가 동기화를 요청하면 더 자주 갱신할 수 있다. 다만 그 주기를 분 단위로 고정하는 설정은 문서에서 찾지 못했다.

### Samsung Knox Manage와 Knox Suite

- **사실:** 새 콘솔의 단말 목록에서 Status는 Knox Manage 서버와의 연결 상태다. Enrolled 상태에서는 에이전트 보고가 "regularly and when triggered by an event" 들어온다. Disconnected는 에이전트가 설정한 keepalive 만료 기간보다 오래 보고하지 않은 상태다. ([Knox Manage 단말 보기](https://docs.samsungknox.com/admin/knox-manage/new-console/manage-devices/view-devices/))
- **사실:** Last seen은 "The last time the Knox Manage server could communicate with the device"다. 이 값은 아래 사건이 있을 때 갱신된다. ([Knox Manage 연결 상태](https://docs.samsungknox.com/admin/knox-manage/original-console/configure/devices/view-device-information/device-connection-statuses/))
  - 마지막 통신 뒤 6시간이 지나서 단말을 재부팅했을 때
  - 단말에 배정된 프로필을 갱신했을 때
  - 프로필 갱신 일정에 따라
  - 프로필에 정한 위치 수집 주기마다
  - Update User Information 명령이나 Push Notification 명령을 보냈을 때
- **사실:** keepalive 설정의 Interval은 "The frequency (in hours) that the server checks the device's connection status"다. Expiration은 3일에서 365일 사이로 정한다. 정한 기간 동안 통신이 없으면 직접 연결 재시도가 일어난다. 그래도 연결되지 않으면 상태가 Disconnected로 바뀐다. 만료 때 단말을 잠그는 옵션도 있다. ([Knox Manage keepalive 설정](https://docs.samsungknox.com/admin/knox-manage/original-console/configure/advanced-settings/configure-the-keepalive-settings/), [Knox Manage 단말 보기](https://docs.samsungknox.com/admin/knox-manage/new-console/manage-devices/view-devices/))
- **사실:** 위치 정책의 Set collection time 값은 30분(기본), 1시간, 2시간, 4시간, 12시간, 24시간이다. 지원 버전은 Android 8 이상으로 적혀 있다. ([Knox Manage Android 정책](https://docs.samsungknox.com/admin/knox-manage/new-console/references/android-policies/))
- **추론:** Knox Manage에서 Last seen을 가장 자주 갱신하는 문서상 방법은 위치 수집 주기다. 그 최솟값이 30분이다. 연결 끊김 판정은 일 단위다.
- **사실:** Knox Asset Intelligence의 webhook 이벤트에는 배터리 상태(3시간마다), 앱별 데이터 사용량(2시간마다), 화면 사용 시간(3시간마다), Wi-Fi 연결과 비정상 끊김이 있다. 조사자가 읽은 이벤트 목록에는 단말 온라인, 오프라인, 마지막 통신 이벤트가 없었다. ([Knox Webhook Notification for KAI](https://docs.samsungknox.com/dev/knox-webhook-notification/tutorial/knox-webhook-notification-for-knox-asset-intelligence/))
- **사실:** Knox Asset Intelligence의 실외 GPS 수집 주기는 1분보다 크고 24시간보다 작은 값으로 정한다. 단말 전체 모바일 데이터와 Wi-Fi 사용량은 하루에 한 번 올라간다. ([KAI 데이터 조건](https://docs.samsungknox.com/admin/knox-asset-intelligence/dashboard/settings/data-conditions/))
- 원문 미확인: Knox Suite 플랜별 포함 제품은 원문으로 확인하지 못했다. 이 문서에서는 Knox Suite의 통신 상태 값으로 Knox Manage와 Knox Asset Intelligence 값을 본다.

### Omnissa Workspace ONE UEM

- **사실:** Android Intelligent Hub 설정의 Heartbeat Interval (min)은 Hub가 Workspace ONE UEM 서버에 check-in하는 빈도다. Data Transmit Interval (min)은 수집한 데이터를 서버로 보내는 빈도다. 이 값은 Data Sample Interval보다 커야 한다. 같은 문서에는 간격이 짧을수록 배터리 소모가 커진다고 적혀 있다. ([Workspace ONE Android Hub 설정](https://docs.omnissa.com/SystemSettings-V2310/AndroidSettingsforWorkspaceONEIntelligentHub/))
- **사실:** 같은 문서에는 원격 명령에 Google Cloud Messaging을 쓰라고 적혀 있다. AWCM을 쓰면 이 옵션을 끌 수 있다. AWCM 연결 상태는 콘솔의 Device Details > Summary에서 볼 수 있다. ([Workspace ONE Android Hub 설정](https://docs.omnissa.com/SystemSettings-V2310/AndroidSettingsforWorkspaceONEIntelligentHub/))
- **사실:** 단말 목록의 Last Seen 열에는 단말이 check-in한 뒤 지난 분이 표시된다. 표시는 비활성 시간에 따라 빨강이나 초록이다. 기준값은 Device Inactivity Timeout (min)이다. 기본값은 480분이다. 이 기준을 넘은 단말에는 서버의 check-in 요청이 간다. ([Workspace ONE 단말 목록](https://docs.omnissa.com/WorkspaceONE-UEM-Managing-Devices-VSaaS/DeviceListView/))
- **사실:** Workspace ONE Intelligence의 데이터 정의에서 Last Seen은 "Lists the last time a device checked in and parameters changed from the last reported parameter values"다. ([Workspace ONE Intelligence UEM 정의](https://docs.omnissa.com/Intelligence/IntelWorkspaceONEUEMDefinitions/))
- 원문 미확인: Heartbeat Interval의 기본값과 최솟값은 문서에서 찾지 못했다.
- **추론:** Intelligence 정의대로라면 Last Seen은 check-in이 있어도 보고 값이 바뀌지 않으면 갱신되지 않을 수 있다. 콘솔의 Last Seen과 같은 값인지는 확인하지 못했다.

### Common Criteria MDM 보호 프로파일

제품 문서 말고 MDM 제품의 평가 기준에서 통신 상태를 어떻게 다루는지 확인했다.

- **사실:** NIAP의 MDM Agent PP-Module 4.0(2019-03-01)의 FAU_ALT_EXT.2.1에는 MDM Agent가 "[selection: receiving, generating] periodic reachability events" 때 MDM 서버에 경보를 보내라고 적혀 있다. 주기를 설정할 수 있으면 "configure periodicity of reachability events"를 관리 기능으로 선택해야 한다. ([PP-Module for MDM Agents 4.0 텍스트](https://sec-certs.org/pp/36b1d4890baaa7f4/profile.txt))
- **사실:** MDM Agent EP 3.0(2016-11-21)의 적용 설명에는 reachability event가 두 방식이라고 적혀 있다. 하나는 에이전트가 서버 poll에 응답하는 방식이다. 다른 하나는 에이전트가 서버에 정기적으로 도달 가능을 알리는 방식이다. 같은 설명에는 연결이 나쁘면 경보가 늦게 도착하거나 도착하지 않을 수 있다고 적혀 있다. 신뢰 채널이 없으면 경보를 쌓아 두었다가 보내야 한다. ([EP for MDM Agents 3.0 텍스트](https://sec-certs.org/pp/652138e3ca5b246a/profile.txt))
- **사실:** MDM PP 2.0의 FAU_NET_EXT.1.1은 "The MDM Server shall provide authorized administrators with the capability to read the network connectivity status of an enrolled agent."다. 공개 초안(5.0-Draft)에도 같은 요구가 있다. ([PP for MDM 2.0 텍스트](https://sec-certs.org/pp/542d6622e5fd5aa5/profile.txt), [PP for MDM 초안](https://commoncriteria.github.io/mdm/xml-builder-test/mdm.html))
- **추론:** 평가 기준에는 MDM에서 단말 망 연결 상태를 관리자에게 보여 줘야 한다고 적혀 있다. 주기 값은 정해져 있지 않다. 그래서 인증 제품이라도 분 단위 갱신을 보장한다고 볼 수 없다.

### 국내 공공기관 MDM

- 원문 미확인: 국내 공공기관에 납품되는 MDM 제품(삼성SDS EMM, 라온시큐어, 지란지교시큐리티 등)의 관리자 문서에서 마지막 통신 시각, 연결 상태, 갱신 주기를 적은 공개 자료를 찾지 못했다. 라온시큐어 제품 소개 PDF는 다른 주소로 이동되어 읽지 않았다. 지란지교시큐리티 자료 링크는 404였다.
- 2차 자료: 아주경제 2022년 7월 11일 기사에 따르면 MDM 공급 기업은 조달 사업에 참여할 때 '스마트폰 보안관리제품' 보안요구사항을 따라야 한다. ([아주경제](https://www.ajunews.com/view/20220711171117149)) 이 보안요구사항 원문은 읽지 못했다.

## 2. 외부 서버에서 읽는 방법

| 제품 | 조회 API와 필드 | 이벤트 | 호출 제한 |
|---|---|---|---|
| Android Management API | `enterprises.devices.get`, `list`의 `lastStatusReportTime`, `lastPolicySyncTime` | Pub/Sub `STATUS_REPORT` 알림(본문은 Device 자원) | 프로젝트당 100초에 1000회, 같은 단말과 동작 조합에 분당 60회 |
| Microsoft Intune | Graph `deviceManagement/managedDevices`의 `lastSyncDateTime`(`lt`, `gt` 필터 지원) | Graph 변경 알림 지원 목록에 없음 | Intune devices 범주 20초에 테넌트 4000회, 앱당 2000회 |
| Samsung Knox Manage | OpenAPI `selectDeviceList`, `selectDeviceInfo`의 `lastConnectionDate`, `deviceStatus`, `userId` | Knox Webhook Notification 지원 서비스에 없음 | 내려받은 명세에서 찾지 못함 |
| Workspace ONE UEM | 필드 이름 미확인 | Event Notifications에 마지막 통신 이벤트 없음 | 확인하지 않음 |

- **사실:** Android Management API의 알림은 `enterprises.patch`로 `pubsubTopic`과 `enabledNotificationTypes`를 정해 받는다. 알림 종류는 `ENROLLMENT`, `STATUS_REPORT`, `COMMAND`, `USAGE_LOGS`, `ENTERPRISE_UPGRADE`다. `ENROLLMENT`과 `STATUS_REPORT` 알림은 Device 자원 형식이다. ([Pub/Sub 알림](https://developers.google.com/android/management/notifications))
- **사실:** Android Management API의 기본 할당량은 프로젝트당 100초에 1000회다. API 동작, EMM ID, 기업 ID, 정책 ID 또는 단말 ID의 조합마다 분당 60회 제한도 있다. ([AMAPI 허용 사용](https://developers.google.com/android/management/permissible-usage))
- **사실:** Graph `managedDevice`의 `lastSyncDateTime` 설명은 "The date and time that the device last completed a successful sync with Intune."이다. `$filter`의 `lt`와 `gt`를 지원한다. 같은 자원에 `syncDevice` 동작이 있다. ([Graph managedDevice](https://learn.microsoft.com/en-us/graph/api/resources/intune-devices-manageddevice?view=graph-rest-1.0))
- **사실:** Graph 변경 알림의 지원 자원 표에는 Intune `managedDevice`가 없다. ([Graph 변경 알림](https://learn.microsoft.com/en-us/graph/change-notifications-overview))
- **사실:** Graph 제한 문서의 Intune devices 범주는 20초에 테넌트 전체 4000회, 앱당 2000회다. 쓰기 요청은 20초에 400회, 앱당 200회다. ([Graph 제한](https://learn.microsoft.com/en-us/graph/throttling-limits))
- **사실:** Knox Manage OpenAPI 25.07 명세의 `DeviceDetail`과 `SimpleDevice` 스키마에는 `lastConnectionDate`와 `stdFormatLastConnectionDate`가 있다. `stdFormatLastConnectionDate` 설명은 "The last connection datetime in ISO8601 format"이다. `deviceStatus` 값은 I(Deactivated), P(Provisioned), A(Activated), B(Blocked) 등이다. ([Knox Manage API 명세](https://docs.samsungknox.com/dev/knox-manage/assets/km-api.yml), [Knox Manage API 레퍼런스](https://docs.samsungknox.com/dev/knox-manage/api/))
- **사실:** 같은 명세의 `sendDeviceControlForKeepAlive` 설명은 "This API sends a Device Control to check KM Service availability. It runs on iOS and Windows."다. ([Knox Manage API 명세](https://docs.samsungknox.com/dev/knox-manage/assets/km-api.yml))
- **사실:** Knox Webhook Notification을 지원하는 서비스는 Knox Asset Intelligence, Knox Configure, Knox Guard, Knox E-FOTA다. ([Knox Webhook Notification](https://docs.samsungknox.com/dev/knox-webhook-notification/))
- **사실:** Workspace ONE UEM의 Event Notifications 이벤트는 Check-in/Check-out(다중 사용자 단말), Device Attribute Change, Device Compliance Status Change, Device Compromised Status Change, Device Delete, Device Enrollment, Device Unenrolled Enterprise Wipe, Device Wipe다. 대상 URL에는 기본 인증으로 JSON이나 XML이 전달된다. ([Workspace ONE Event Notifications](https://docs.omnissa.com/SystemSettings-V2406/EventNotificationsSystemSettings))
- **추론:** Knox Manage 콘솔의 Last seen과 API의 `lastConnectionDate`가 같은 값인지는 문서로 확인하지 못했다.
- **추론:** Android Management API를 빼면 외부 서버에서 통신 상태 변화를 확인하려면 주기적으로 조회해야 한다. 업무폰 수가 늘면 위 호출 제한 안에서 조회 주기를 정해야 한다.

## 3. MDM 값으로 알 수 있는 것과 없는 것

| 알고 싶은 것 | MDM 값으로 알 수 있는가 | 근거 |
|---|---|---|
| 단말 전원과 망 연결 | 관리 서버에 마지막으로 닿은 시각으로 간접 확인한다. 갱신은 시간 단위다. | 1절 |
| 특정 앱 설치 여부 | 알 수 있다. | Android Management API `applicationReports`, Knox Manage `selectDeviceAppList` |
| 특정 앱 실행 여부 | 단말 MDM 값으로는 알 수 없다. Knox Asset Intelligence의 앱 사용 시간은 3시간 묶음이다. | 아래 사실 |
| 앱에 로그인한 계정 | MDM의 사용자 필드는 단말에 연결된 관리 사용자다. 앱 로그인 계정이 아니다. | 아래 사실 |
| 앱과 수리맵 서버의 통신 | 알 수 없다. | 추론 |

- **사실:** Android Management API의 `userName`은 "The resource name of the user that owns this device"다. `applicationReports`는 "Reports for apps installed on the device."다. ([enterprises.devices](https://developers.google.com/android/management/reference/rest/v1/enterprises.devices))
- **사실:** Android Management API의 keyed app state는 앱에 넣은 Enterprise Jetpack 라이브러리로 전송된다. keyed app state는 `applicationReports`의 `keyedAppStates`에 들어간다. 즉시 보고는 1분에 한 번만 보낼 수 있다. 그 사이에 생긴 보고는 대기했다가 함께 보내진다. 받으려면 `STATUS_REPORT` 알림과 `applicationReportsEnabled`를 켜야 한다. ([앱 피드백](https://developers.google.com/android/management/app-feedback))
- **사실:** Graph `managedDevice`의 `userPrincipalName`은 "Device user principal name"이다. ([Graph managedDevice](https://learn.microsoft.com/en-us/graph/api/resources/intune-devices-manageddevice?view=graph-rest-1.0))
- **사실:** Intune 앱 보호 정책의 `managedAppRegistration`에는 `userId`, `appIdentifier`, `lastSyncDateTime`이 있다. `lastSyncDateTime` 설명은 "Date and time of last the app synced with management service."다. ([Graph managedAppRegistration](https://learn.microsoft.com/en-us/graph/api/resources/intune-mam-managedappregistration?view=graph-rest-1.0))
- **사실:** 앱 보호 정책에 등록된 사용자의 재시도 주기는 "Typically 30 mins"다. 재시도에는 앱이 실행되어 사용 중이어야 할 수 있다. 망 문제로 등록에 실패하면 재시도 간격이 늘어나 60분까지 간다. ([앱 보호 정책 전달 시점](https://learn.microsoft.com/en-us/intune/app-management/protection/policy-delivery-timing))
- **추론:** Intune 앱 보호 정책 값은 사용자와 앱 단위라서 MDM 값 중 수리맵 요구에 가장 가깝다. 하지만 앱에 Intune App SDK를 넣고 Entra 계정으로 로그인해야 한다. 이 값으로 확인할 수 있는 것도 Intune MAM 서비스와의 통신이지 수리맵 서버와의 통신이 아니다.
- **추론:** keyed app state는 앱이 보내는 보고를 MDM 경로로 전달하는 방식이다. 그래서 인프라 계층의 값이라기보다 앱 heartbeat를 Google 서버로 돌려 보내는 구조다.

## 4. 현장 업무 제품의 통신 상태 표시

### Esri Field Maps 위치 공유

- **사실:** 위치 정보는 Field Maps 모바일 앱에서 수집된다. 수집한 위치는 모바일 단말의 SQLite DB에 저장된 뒤 업로드된다. ([Field Maps 위치 공유 설정](https://doc.esri.com/en/arcgis-field-maps/latest/prepare-maps/enable-location-sharing.html))
- **사실:** 마지막 알려진 위치는 기본 60초마다 갱신된다. 위치 업로드는 충전 중이면 60초마다, 아니면 10분마다 일어난다. 망이 없으면 위치는 단말에 남아 있다가 연결되면 올라간다. ([Field Maps 위치 공유 사용](https://doc.esri.com/en/arcgis-field-maps/latest/use-maps/track.html?pivots=mobile-android))
- **사실:** last known locations 레이어는 "a single record for each user representing their most recently reported location"이다. 경로 선은 약 10분마다 갱신된다. ([ArcGIS Online 경로 레이어](https://doc.esri.com/en/arcgis-online/latest/reference/use-tracks.html))
- **추론:** Field Maps의 지휘 화면 상태는 앱 업로드로 만들어진다. 기록 단위는 단말이 아니라 사용자다.

### Microsoft Teams 단말

- **사실:** Teams 단말 건강 신호는 단말 에이전트가 보고한 속성으로 활성화된다. 일부 속성은 5분마다, 일부는 2시간마다 평가된다. ([Teams 건강 신호](https://learn.microsoft.com/en-us/microsoftteams/rooms/signals))
- **사실:** Android 기반 Teams Rooms, 패널, Teams 전화의 Offline 신호는 단말의 Admin agent가 보고를 멈추면 활성화된다. 평가 주기는 120분이다. 관찰 기간은 Rooms와 패널이 60분, Teams 전화가 1분이다. 로그인 여부는 Sign in 신호로 따로 평가된다. ([Teams 건강 신호](https://learn.microsoft.com/en-us/microsoftteams/rooms/signals))
- **사실:** Windows 기반 Teams Rooms에는 Teams Rooms 앱이 실행되지 않을 때 활성화되는 Meeting app (heartbeat) 신호가 있다. 평가 주기는 10분이다. 관찰 기간은 60분이다. 텔레메트리가 오지 않는데 Teams 로그인은 최근에 성공한 경우를 따로 보는 Monitoring 신호도 있다. ([Teams 건강 신호](https://learn.microsoft.com/en-us/microsoftteams/rooms/signals))
- **추론:** Teams 단말에서는 단말 연결, 로그인, 앱 실행을 서로 다른 신호로 나눈다. 세 신호 모두 MDM이 아니라 전용 에이전트와 앱의 보고에서 온다.

### Zebra Device Tracker

- **사실:** Device Tracker는 Zebra가 운영하는 클라우드 서비스다. 단말에는 모바일 앱이 설치된다. EMM과 StageNow는 이 앱의 설치와 설정에 쓰인다. ([Device Tracker 소개](https://techdocs.zebra.com/devicetracker/5-7/guide/about/))
- **사실:** 대시보드의 Last Reported는 단말이 서버에 마지막으로 보고한 시각이다. 서버와 약 12분 동안 통신이 없으면 Connection State가 Disconnected로 바뀐다. ([Device Tracker 대시보드](https://techdocs.zebra.com/devicetracker/5-7/guide/dashboard/))
- **추론:** Zebra에서도 EMM은 배포 경로다. 통신 상태는 업무 앱과 그 서버 사이의 보고로 만들어진다.

### 찾지 못한 제품

- 원문 미확인: Motorola와 Hexagon의 CAD, AVL unit status가 무엇으로 만들어지는지 적은 공개 1차 문서는 찾지 못했다.
- **사실:** 국가기록원 자료에 따르면 2004년 인천지방경찰청에 GPS 기반 112순찰차 신속배치시스템(IDS)이 설치되었다. 이 시스템으로 112순찰차의 위치가 실시간으로 확인되었다. ([국가기록원 112](https://theme.archives.go.kr/next/koreaOfRecord/112.do)) 이 자료에는 위치를 보내는 장치가 무엇인지 적혀 있지 않다.

## 5. 경찰청 폴리폰과 국내 공공 모바일 업무환경

- 원문 미확인: 경찰청 폴리폰이나 국내 공공 모바일 업무환경에서 MDM으로 단말 통신 상태를 확인한다는 보도자료, 국회 자료, 제안요청서, 규격서를 찾지 못했다. 2025년 폴리폰 구매 공고 규격서를 읽지 못한 사정은 [업무폰 등록 확인 요구 조사](device-check-requirement-research.md#경찰청-폴리폰-자료)에 있다.
- 2차 자료: 더팩트 2026년 10월 12일 기사는 국회 행정안전위원회 의원실을 통해 받은 경찰청 자료를 인용한다. 이 기사에 따르면 전국에 보급된 폴리폰은 2만2733대다. 같은 기사에는 폴리앱 시스템에 목적 외 조회와 이상징후를 자동으로 탐지하는 기능이 없다고 경찰청이 밝혔다고 적혀 있다. 인터뷰한 경찰관은 교대 근무 중 기기를 공유하면 로그인된 상태에서 다른 사람이 조회해도 인지하지 못할 수 있다고 말했다. ([더팩트, 네이트 뉴스](https://m.news.nate.com/view/20261012n00097))
- 원문 미확인: 방송통신위원회의 2012년 「안전한 모바일오피스 도입과 운영을 위한 정보보호수칙」 PDF를 내려받았지만 본문을 추출하지 못했다. ([방송통신위원회 자료](https://www.kmcc.go.kr/download.do?fileSeq=37005))
- **추론:** 공개 자료로 확인한 범위에서 경찰 업무폰 관리 논의는 조회 남용과 로그인 공유에 있다. MDM 통신 상태를 지휘에 쓰는지는 공개 자료로 확인되지 않는다.

## 수리맵에 비춰 본 점

이 절은 모두 조사자의 해석이다. 설계 결정은 하지 않는다.

- **추론:** 현재 상황판 기준은 60초와 5분이다. 조사한 MDM 값은 대부분 시간 단위이거나, 설정해도 30분 이상이다. 그래서 MDM 값을 그대로 쓰면 STALE과 LOST를 지금 기준으로 나눌 수 없다.
- **추론:** MDM 값은 단말 단위다. 수리맵에서 지휘자가 알고 싶은 것은 "이 계정의 현장 인원과 연락이 닿는가"다. 여러 사람이 업무폰 한 대를 돌려쓴다. 쓸 때마다 각자 자기 계정으로 로그인한다. 그래서 단말의 통신 시각과 그 계정으로 앱을 쓰는 상태가 다를 수 있다.
- **추론:** 조사한 현장 제품은 앱이나 전용 에이전트의 보고로 상태를 만든다. MDM으로는 앱을 배포하고 설정한다. 이 구조는 ADR-0037에서 인증을 인프라 계층에 둔 결정과 충돌하지 않는다. 인증은 요청의 출처를 증명하는 일이다. 통신 상태는 앱이 서버에 닿는지 관찰하는 일이다.
- **추론:** MDM 값은 앱 heartbeat를 대신하기보다 보조 신호로 쓸 수 있다. 예를 들어 앱 heartbeat는 끊겼지만 MDM 마지막 통신 시각은 최근이면, 단말은 켜져 있으나 앱 쪽에 문제가 있다고 볼 수 있다. 이 방식이 필요한지는 사용자와 정할 문제다.
- 사용자와 정할 질문
  - 실제 기관 MDM이 어느 제품인지, 외부 서버에 API 조회를 허용하는지
  - 통신 상태가 단말 기준이어야 하는지, 로그인한 계정 기준이어야 하는지
  - 60초와 5분 기준이 현장 요구에서 나온 값인지

## 확인하지 못한 것

- Android Management API의 상태 보고 주기와 보고를 일으키는 사건은 공식 문서에 없었다.
- Workspace ONE UEM의 Heartbeat Interval 기본값과 최솟값, REST API의 마지막 통신 시각 필드 이름은 확인하지 못했다. Omnissa 알림 서비스 가이드 PDF는 본문을 추출하지 못했다.
- Knox Manage keepalive Interval에서 고를 수 있는 값, 콘솔 Last seen과 API `lastConnectionDate`의 관계, OpenAPI 호출 제한은 확인하지 못했다.
- Knox Suite 플랜별 포함 제품은 원문으로 확인하지 못했다.
- Intune에서 분 단위 정기 동기화를 설정하는 방법이 있는지는 문서에서 찾지 못했다.
- DoD의 MDM PP Server Annex에서 reachability 주기 값을 정했는지는 PDF 본문을 추출하지 못해 확인하지 못했다.
- 국내 공공기관 MDM 제품(삼성SDS EMM, 라온시큐어, 지란지교시큐리티)의 관리자 문서와 국가정보원 '스마트폰 보안관리제품' 보안요구사항 원문은 읽지 못했다.
- Motorola, Hexagon의 CAD와 AVL 문서, TAK 같은 군경 상황 인식 도구의 위치 보고 규격은 찾지 못했다.
- 실제 기관 MDM 환경에서 값이 얼마나 늦게 갱신되는지는 측정하지 않았다.

## 출처

접근일은 모두 2026-11-18이다. 표시가 없는 항목은 1차 자료다.

### Google

- Android Management API, REST `enterprises.devices`: https://developers.google.com/android/management/reference/rest/v1/enterprises.devices
- Android Management API, REST `enterprises.policies`: https://developers.google.com/android/management/reference/rest/v1/enterprises.policies
- Android Management API, Pub/Sub notifications: https://developers.google.com/android/management/notifications
- Android Management API, Retrieve feedback from apps: https://developers.google.com/android/management/app-feedback
- Android Management API, Permissible usage: https://developers.google.com/android/management/permissible-usage
- Android Enterprise Network Requirements: https://support.google.com/work/android/answer/10513641?hl=en

### Microsoft

- Intune, Policy refresh intervals: https://learn.microsoft.com/en-us/intune/device-configuration/troubleshoot-device-profiles
- Intune, Environments without Google Mobile Services: https://learn.microsoft.com/en-us/intune/app-management/manage-without-gms
- Intune, Device compliance policies: https://learn.microsoft.com/en-us/intune/device-security/compliance/overview
- Intune, App protection policy delivery timing: https://learn.microsoft.com/en-us/intune/app-management/protection/policy-delivery-timing
- Microsoft Graph, managedDevice: https://learn.microsoft.com/en-us/graph/api/resources/intune-devices-manageddevice?view=graph-rest-1.0
- Microsoft Graph, managedAppRegistration: https://learn.microsoft.com/en-us/graph/api/resources/intune-mam-managedappregistration?view=graph-rest-1.0
- Microsoft Graph, Change notifications overview: https://learn.microsoft.com/en-us/graph/change-notifications-overview
- Microsoft Graph, Throttling limits: https://learn.microsoft.com/en-us/graph/throttling-limits
- Microsoft Teams, Health signals: https://learn.microsoft.com/en-us/microsoftteams/rooms/signals

### Samsung

- Knox Manage, View devices: https://docs.samsungknox.com/admin/knox-manage/new-console/manage-devices/view-devices/
- Knox Manage, Device connection statuses: https://docs.samsungknox.com/admin/knox-manage/original-console/configure/devices/view-device-information/device-connection-statuses/
- Knox Manage, Configure the keepalive settings: https://docs.samsungknox.com/admin/knox-manage/original-console/configure/advanced-settings/configure-the-keepalive-settings/
- Knox Manage, Android policies: https://docs.samsungknox.com/admin/knox-manage/new-console/references/android-policies/
- Knox Manage API reference 25.07: https://docs.samsungknox.com/dev/knox-manage/api/ , 명세 파일: https://docs.samsungknox.com/dev/knox-manage/assets/km-api.yml
- Knox Webhook Notification: https://docs.samsungknox.com/dev/knox-webhook-notification/
- Knox Webhook Notification for Knox Asset Intelligence: https://docs.samsungknox.com/dev/knox-webhook-notification/tutorial/knox-webhook-notification-for-knox-asset-intelligence/
- Knox Asset Intelligence, Data conditions: https://docs.samsungknox.com/admin/knox-asset-intelligence/dashboard/settings/data-conditions/

### Omnissa

- Workspace ONE UEM, Android Settings for Workspace ONE Intelligent Hub: https://docs.omnissa.com/SystemSettings-V2310/AndroidSettingsforWorkspaceONEIntelligentHub/
- Workspace ONE UEM, Device List View: https://docs.omnissa.com/WorkspaceONE-UEM-Managing-Devices-VSaaS/DeviceListView/
- Workspace ONE UEM, Event Notifications System Settings: https://docs.omnissa.com/SystemSettings-V2406/EventNotificationsSystemSettings
- Workspace ONE Intelligence, Workspace ONE UEM data definitions: https://docs.omnissa.com/Intelligence/IntelWorkspaceONEUEMDefinitions/

### 표준

- NIAP, PP-Module for MDM Agents 4.0 텍스트(sec-certs 사본): https://sec-certs.org/pp/36b1d4890baaa7f4/profile.txt
- NIAP, Extended Package for MDM Agents 3.0 텍스트(sec-certs 사본): https://sec-certs.org/pp/652138e3ca5b246a/profile.txt
- NIAP, Protection Profile for Mobile Device Management 2.0 텍스트(sec-certs 사본): https://sec-certs.org/pp/542d6622e5fd5aa5/profile.txt
- Protection Profile for Mobile Device Management 5.0-Draft: https://commoncriteria.github.io/mdm/xml-builder-test/mdm.html

### 현장 업무 제품

- Esri, Enable location sharing: https://doc.esri.com/en/arcgis-field-maps/latest/prepare-maps/enable-location-sharing.html
- Esri, Share location (Android): https://doc.esri.com/en/arcgis-field-maps/latest/use-maps/track.html?pivots=mobile-android
- Esri, Track layers: https://doc.esri.com/en/arcgis-online/latest/reference/use-tracks.html
- Zebra, Device Tracker 5.7 About: https://techdocs.zebra.com/devicetracker/5-7/guide/about/
- Zebra, Device Tracker 5.7 Dashboard: https://techdocs.zebra.com/devicetracker/5-7/guide/dashboard/

### 한국 자료

- 국가기록원, 기록으로 보는 112: https://theme.archives.go.kr/next/koreaOfRecord/112.do
- 방송통신위원회, 안전한 모바일오피스 도입과 운영을 위한 정보보호수칙(2012, 본문 미추출): https://www.kmcc.go.kr/download.do?fileSeq=37005
- 2차 자료, 더팩트 "'연 2700만건 조회' 경찰 폴리폰, 점검은 부서장 '30건 무작위'"(2026-10-12), 네이트 뉴스: https://m.news.nate.com/view/20261012n00097
- 2차 자료, 아주경제 "애플 아이폰, 10년 만에 모바일 전자정부 문턱 넘는다"(2022-07-11): https://www.ajunews.com/view/20220711171117149
