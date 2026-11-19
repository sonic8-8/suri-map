# 오프라인 수색 준비 상태 확인을 어느 계층에서 하는지 조사

- 조사일: 2026-11-18
- 질문: 배정된 인원이 오프라인 수색을 할 준비가 됐는지 확인하는 일을 기관 MDM에 맡길 수 있는가? 아니면 업무 애플리케이션에서 맡는 것이 맞는가?
- 상태: 조사 결과다. 수리맵 설계 결정은 하지 않았다.
- 배경: 오프라인 패키지의 기존 요구와 현재 구현 차이는 [오프라인 패키지 기능 정의 문서](../features/offline-package.md)에 있다. 업무폰 인증을 인프라 계층에 둔 결정은 [ADR-0037](../adr/0037-police-phone-authentication-at-infrastructure.md)에 있다.
- 이어지는 조사: MDM의 통신 상태 값과 갱신 주기, keyed app state의 전송 지연과 Pub/Sub 알림, Intune 앱 보호 정책의 마지막 동기화 시각은 [MDM 통신 상태 조사](mdm-device-liveness-research.md)에 있다. 관리 구성(managed configuration)의 기본 동작과 공유 단말 모델(Android 다중 사용자, Entra 공유 단말 모드, Knox Manage 공유 단말, Apple Shared iPad)은 [단말 신뢰 계층 조사](device-trust-layer-research.md)에 있다. 이 문서에서는 두 문서의 내용을 다시 쓰지 않는다.

이 문서에서 **사실:** 은 출처에 적힌 내용이다. **추론:** 은 조사자의 해석이다. 출처의 접근일은 모두 2026-11-18이다. 영어 출처의 필드 이름, 설정 이름, 제품 화면 이름은 원문 표기를 그대로 옮겼다.

## 결론 요약

1. **MDM에서 앱 내부 상태를 알 수 있는가.** 조사한 MDM과 EMM에는 앱이 받은 파일이나 앱에서 정한 준비 상태를 스스로 알아내는 기능이 없었다. 앱 내부 상태를 MDM으로 보내는 표준 경로로는 AndroidX Enterprise의 keyed app state 하나만 확인했다. 이 경로에서도 상태는 앱 개발자가 만들어 보낸다. key는 100자, message와 data는 각각 1000자까지다. 즉시 전송은 1분에 한 번이다. 하루에 쓸 수 있는 고유 key는 500개다. 외부 서버에서는 Android Management API의 `devices.get`이나 Pub/Sub `STATUS_REPORT` 알림으로 값을 읽는다. Intune과 Knox Manage에서 keyed app state를 관리자 화면에 보여 준다는 공식 문서는 찾지 못했다.
2. **MDM 콘텐츠 배포.** Knox Manage와 Workspace ONE UEM에는 파일 배포 기능이 있다. Knox Manage 콘텐츠는 MBTILES, MMPK, TPK 같은 지도 파일 형식도 받는다. 다만 배포 단위는 조직, 그룹, 사용자다. 파일이 놓이는 곳은 공용 미디어 폴더나 MDM 쪽 콘텐츠 앱이다. 단말별 다운로드 상태를 API로 읽는 방법은 문서에서 확인하지 못했다. Knox Manage OpenAPI에는 콘텐츠를 올리거나 배정하는 API가 없었다. Intune과 Android Management API에서는 일반 파일 배포 기능을 찾지 못했다. **추론:** 사건 데이터는 사건마다 새로 만들어진다. 배정이 바뀌면 갱신해야 한다. 사건 종료 뒤에는 지워야 한다. 이런 데이터를 이 기능으로 다루기는 어렵다.
3. **현장 업무 제품.** 조사한 제품(Esri ArcGIS Field Maps, Dynamics 365 Field Service, Salesforce Field Service, ServiceNow Mobile, SAP Field Service Management, Mapbox)에서는 모두 업무 앱이 업무 서버에서 오프라인 데이터를 내려받는다. 준비 상태와 동기화 상태도 앱과 업무 서버 쪽에서 만들어진다. 관리자가 보는 곳은 업무 서버의 기록(Esri 복제본 정보, Dynamics 동기화 오류)이나 앱 원격 측정(Dynamics의 Application Insights 대시보드)이다. Esri의 MDM 기술 문서에서 MDM의 역할은 앱 배포, 앱 설정 값, 앱별 VPN, 접근 제어, SSO다. 오프라인 지도 배포나 준비 상태 보고는 그 목록에 없다.
4. **기업 기술 블로그.** 해외에서는 Uber 기사 앱의 Optimistic Mode 글과 Grab의 사용자 중심 설계 글을 찾았다. 두 글은 망이 끊긴 현장에서 앱이 어떻게 동작하는지를 다룬다. 단말 상태를 MDM으로 보고한다는 내용은 두 글에 없다. 한국 기업 기술 블로그에서는 현장 작업자 앱의 오프라인 데이터 준비, 기기 공유, 단말 상태 보고를 다룬 글을 찾지 못했다.
5. **공유 단말의 사용자 교체.** 이전 사용자 데이터를 지우는 수단은 MDM 쪽에도 앱 쪽에도 있다. MDM 쪽 수단은 앱 데이터 전체를 지우는 명령(Android Management API `CLEAR_APP_DATA`, Knox Manage `sendDeviceControlForDeleteAppData`)과 Intune의 공유 모드 로그아웃 연동 설정이다. 이 수단으로는 앱 안의 사용자별 데이터를 나눠 지울 수 없다. Microsoft 공식 문서에는 앱 개발자가 이전 사용자의 캐시 데이터를 지워야 한다고 적혀 있다. Intune 선택적 초기화는 앱에 Intune App SDK가 들어 있어야 쓸 수 있다. 초기화는 사용자가 앱을 열어야 실행된다.

## 1. MDM과 EMM에서 앱 내부 상태를 알 수 있는지

### 앱 내부 상태가 MDM으로 가는 표준 경로: keyed app state

전송 지연, 1분 제한, Pub/Sub 설정은 [MDM 통신 상태 조사](mdm-device-liveness-research.md#3-mdm-값으로-알-수-있는-것과-없는-것)에 있다. 여기서는 크기 제한, 용도, 전달 경로만 적는다.

- **사실:** 개발자 안내에는 keyed app state가 "Apps targeting enterprise organizations can send feedback to EMMs in the form of keyed app states."라고 적혀 있다. 예시는 이메일 앱의 계정 설정 확인과 동기화 오류 보고다. ([Send app feedback to EMMs](https://developer.android.com/work/app-feedback/overview))
- **사실:** keyed app state는 key, message, data, severity, timestamp로 이뤄진다. key는 최대 100자다. message는 최대 1000자다. data도 최대 1000자다. timestamp는 상태를 설정할 때 자동으로 붙는다. ([Send app feedback to EMMs](https://developer.android.com/work/app-feedback/overview), [KeyedAppState 레퍼런스](https://developer.android.com/reference/androidx/enterprise/feedback/KeyedAppState))
- **사실:** data는 "Optional machine-readable value intended for EMMs to allow IT admins to set up alerts or filters based on the value."다. 예시는 `battery_percentage < 10` 알림이다. ([Send app feedback to EMMs](https://developer.android.com/work/app-feedback/overview))
- **사실:** 한 번의 `setStates` 또는 `setStatesImmediate` 호출은 모두 합쳐 300KB로 제한된다. 안내 문서에는 이 값이 하루에 저장할 수 있는 양의 약 1/3이라고 적혀 있다. 앱 하나는 하루에 고유 key를 500개까지 쓸 수 있다. key마다 보내는 상태 수에는 제한이 없다. ([Send app feedback to EMMs](https://developer.android.com/work/app-feedback/overview))
- **사실:** 안내 문서에는 "Never include personally identifiable information (PII) in a state---keyed apps states aren't suitable for sensitive data."라고 적혀 있다. ([Send app feedback to EMMs](https://developer.android.com/work/app-feedback/overview))
- **사실:** `setStates()`를 호출하면 상태가 Play 스토어 앱(`com.android.vending`)에 전달된다. 이 앱이 설치되어 있을 때만 그렇다. 단말이나 업무 프로필의 관리자 앱에도 전달된다. ([Send app feedback to EMMs](https://developer.android.com/work/app-feedback/overview))
- **사실:** Android Management API에서는 keyed app state가 단말 보고의 `applicationReports` 아래 패키지 이름별로 묶인다. 각 상태에는 `createTime`과 `lastUpdateTime`이 있다. 외부 서버에서는 Pub/Sub 알림이나 `devices.get()`으로 최신 보고를 읽는다. ([Retrieve feedback from apps](https://developers.google.com/android/management/app-feedback))
- **사실:** 안내 문서에서 가장 흔한 용도로 든 것은 관리 구성 적용 결과의 보고다. 관리 구성 문서에도 관리 구성 변경을 적용한 뒤 keyed app state로 결과를 알리는 것이 좋다고 적혀 있다. ([Retrieve feedback from apps](https://developers.google.com/android/management/app-feedback), [관리 구성](https://developer.android.com/work/managed-configurations))
- **추론:** keyed app state의 값은 MDM에서 관찰한 값이 아니다. 앱 개발자가 정의한 key와 data가 앱에서 만들어져 MDM 경로로 전달될 뿐이다. 그래서 "오프라인 패키지가 준비됐다"는 판정 자체는 이 경로를 써도 앱에서 해야 한다.
- **추론:** 사건 ID나 배정 정보처럼 사건과 사람을 알 수 있는 값은 PII 금지 안내와 충돌할 수 있다. 사건마다 key를 만들면 하루 500개 제한도 고려해야 한다.

### 관리 구성의 방향

- **사실:** 관리 구성은 IT 관리자가 앱의 설정을 원격으로 정하는 기능이다. 예시는 브라우저의 URL 허용, 셀룰러 동기화 허용 여부, 이메일 설정이다. 값의 형식은 bool, string, integer, choice, multi-select, hidden, bundle_array다. ([관리 구성](https://developer.android.com/work/managed-configurations))
- **사실:** 관리 구성이 바뀌어도 앱에 자동으로 알려지지 않는다. 앱이 시작하거나 다시 열릴 때 값을 읽어야 한다. 실행 중 변경은 동적으로 등록한 `ACTION_APPLICATION_RESTRICTIONS_CHANGED` 수신기로만 알 수 있다. ([관리 구성](https://developer.android.com/work/managed-configurations))
- **추론:** 관리 구성은 MDM에서 앱으로 가는 방향이다. 앱 상태를 MDM으로 올리는 기능은 관리 구성 자체에 없다. 앱 상태는 keyed app state로 올린다.

### 제품별 확인 결과

| 제품 | 앱에서 정한 상태를 보여 주는 기능 | 근거 |
|---|---|---|
| Android Management API | keyed app state를 Device 자원의 `applicationReports`로 읽는다. | [Retrieve feedback from apps](https://developers.google.com/android/management/app-feedback) |
| Microsoft Intune | 공식 문서에서 찾지 못했다. 앱 단위 값은 앱 보호 정책의 마지막 동기화 시각뿐이다. | [MDM 통신 상태 조사](mdm-device-liveness-research.md) |
| Samsung Knox Manage | 공식 문서와 OpenAPI 명세에서 찾지 못했다. | 아래 사실 |
| Workspace ONE UEM | 2020년 VMware 블로그에 OEMConfig 앱의 feedback channel이 콘솔에 표시된다고 적혀 있다. 일반 업무 앱의 keyed app state 표시는 제품 문서로 확인하지 못했다. | 아래 사실 |

- **사실:** Knox Manage OpenAPI 25.07 명세의 경로 89개에는 단말, 그룹, 조직, 사용자, 단말 명령 API가 있다. 앱이 보낸 상태나 keyed app state를 조회하는 경로는 없었다. ([Knox Manage API 명세](https://docs.samsungknox.com/dev/knox-manage/assets/km-api.yml))
- **사실:** VMware 블로그 "Android Series Ep 14: Feedback Channel"(2020-05-14)에는 "Feedback channel is a way for OEMConfig applications to return feedback to the Workspace ONE UEM console."라고 적혀 있다. ([VMware EUC 블로그](https://blogs.vmware.com/euc/?p=25657))
- 원문 미확인: Citrix Endpoint Management의 관리 구성 피드백 문서는 검색 결과로만 확인했다. 본문은 읽지 못했다.

## 2. MDM이 앱 콘텐츠를 직접 배포하는 기능

### Samsung Knox Manage 콘텐츠

- **사실:** 새 콘솔에서 올릴 수 있는 파일의 최대 크기는 4GB다. 지원 형식에는 문서, 이미지, 동영상과 함께 JSON, DB3, XML, KML, KMZ, VTPK, GEODATABASE, MBTILES, MMPK, TPK, TPKX가 있다. 기본 다운로드 경로를 바꿀 때는 Documents, Download, Pictures, Movies, Music 같은 미디어 폴더 중 하나를 골라야 한다. ([Knox Manage 콘텐츠 추가](https://docs.samsungknox.com/admin/knox-manage/new-console/manage-content/add-supported-content/))
- **사실:** 콘텐츠는 그룹, 조직, 사용자에 배정한다. 배정된 사용자, 그룹, 조직에 연결된 단말에 콘텐츠가 자동으로 내려받힌다. 최신 파일을 보낼 때는 관리자가 Push content를 다시 실행한다. ([Knox Manage 콘텐츠 배정](https://docs.samsungknox.com/admin/knox-manage/new-console/manage-content/assign-push-or-unassign-content/))
- **사실:** 배정을 해제해도 단말의 콘텐츠는 기본으로 지워지지 않는다. "Delete content from device when unassigned" 설정을 켜야 지워진다. ([Knox Manage 콘텐츠 배정](https://docs.samsungknox.com/admin/knox-manage/new-console/manage-content/assign-push-or-unassign-content/))
- **사실:** 기존 콘솔 문서에는 Android 10에서 콘텐츠 파일이나 이름을 바꿔도 단말의 기존 사본이 갱신되지 않는다고 적혀 있다. 이 경우 관리자가 파일을 다시 배포해야 한다. ([Knox Manage 콘텐츠 개요](https://docs.samsungknox.com/admin/knox-manage/original-console/configure/content/content-overview/))
- **사실:** 콘텐츠 배정과 배포 문서에는 단말별 다운로드 성공, 실패 상태를 보여 주는 화면이 나오지 않았다. ([Knox Manage 콘텐츠 배정](https://docs.samsungknox.com/admin/knox-manage/new-console/manage-content/assign-push-or-unassign-content/), [Knox Manage 콘텐츠 배포](https://docs.samsungknox.com/admin/knox-manage/original-console/configure/content/manage-content/distribute-content/))
- **사실:** Knox Manage OpenAPI 25.07 명세에는 콘텐츠를 올리거나 배정하는 API가 없다. ([Knox Manage API 명세](https://docs.samsungknox.com/dev/knox-manage/assets/km-api.yml))

### Omnissa Workspace ONE UEM 콘텐츠 관리

- **사실:** UEM Managed 저장소의 파일은 Workspace ONE Content 앱과 동기화되어 단말에 전달된다. 업로드 최대 크기의 기본값은 200MB다. 상한은 2GB다. 파일마다 버전을 올릴 수 있다. ([Managed Content Repository](https://docs.omnissa.com/WS1UEMMobileContentManagement/ManagedContentRepository))
- **사실:** Content 앱의 배포 설정에는 Download type(Automatic 또는 On Demand), Required content, Effective date, Expiration date가 있다. 파일 단위 DLP 설정에는 "Allow Open into 3rd Party Apps"가 있다. ([Mobile Content Management FAQ](https://docs.omnissa.com/WS1UEMMobileContentManagement/MCMFAQ))
- **사실:** 콘솔의 Content Management Dashboard에는 단말과 파일의 콘텐츠 준수 상태를 요약하는 User/Content Status가 있다. UEM Managed 파일 하나를 고르면 VIEW DEVICES 메뉴가 나온다. ([콘솔의 콘텐츠 관리](https://docs.omnissa.com/WS1UEMMobileContentManagement/ContentMgmntUsingConsole))
- 2차 자료: Product Provisioning의 Files/Actions로 Android 단말의 지정 경로에 파일을 내려받는 기능이 VMware 문서에 있다. 검색 결과의 결함 기록과 커뮤니티 글에는 Android 10 이상에서 Hub 앱 폴더 밖으로 파일을 옮기지 못한다고 적혀 있다. 이 두 자료는 본문을 직접 확인하지 않았다.
- **추론:** Workspace ONE에서 관리자가 다운로드 상태를 볼 수 있는 대상은 Content 앱으로 받은 파일이다. 업무 앱이 그 파일을 열어 쓸 수 있는지는 DLP 설정과 파일 위치에 달려 있다.

### Microsoft Intune과 Android Management API

- 원문 미확인: Intune 문서에서 Android Enterprise 단말에 앱이 아닌 일반 파일을 배포하는 기능은 찾지 못했다. 검색 결과에는 APK 배포 문서와 답이 없는 2019년 커뮤니티 질문만 나왔다.
- **사실:** Android Management API의 단말 명령은 LOCK, RESET_PASSWORD, REBOOT, RELINQUISH_OWNERSHIP, CLEAR_APP_DATA, START_LOST_MODE, STOP_LOST_MODE, ADD_ESIM, REMOVE_ESIM, REQUEST_DEVICE_INFO, WIPE다. 파일을 내려보내는 명령은 없다. ([issueCommand](https://developers.google.com/android/management/reference/rest/v1/enterprises.devices/issueCommand))

### 지도 데이터를 MDM으로 미리 넣는 일의 제약

- **사실:** Mapbox 문서에는 Mapbox 서버에서 받은 오프라인 지도를 개발자나 최종 사용자가 재배포할 수 없다고 적혀 있다. 원문은 "the data may not be preloaded, bundled or otherwise redistributed"다. ([Mapbox 오프라인 개념](https://docs.mapbox.com/android/maps/guides/offline/concepts/))
- **추론:** 수리맵은 자체 타일 서버를 쓴다([지도 SDK 결정](../adr/0004-maplibre-and-self-hosted-tiles.md)). 그래서 이 약관이 바로 적용되지는 않는다. 다만 상용 지도 제공자를 쓰는 제품에서는 MDM으로 지도 파일을 미리 넣는 방법 자체가 막힐 수 있다.

### 사건별 데이터에 비춘 한계

이 소절은 모두 조사자의 해석이다.

- **추론:** 조사한 MDM 콘텐츠 기능의 배포 단위는 조직, 그룹, 사용자다. 수리맵의 배정 단위는 사건과 수색 구역이다. 사건마다 MDM 그룹을 만들고 배정이 바뀔 때마다 그룹 구성원을 바꿔야 한다. Knox Manage OpenAPI에는 그룹 API는 있지만 콘텐츠 API가 없다. 그래서 수리맵 서버에서 사건 데이터를 자동으로 올리고 배정하는 흐름을 만들기 어렵다.
- **추론:** 여러 사람이 업무폰 한 대를 돌려쓴다. 쓸 때마다 각자 자기 계정으로 로그인한다. MDM 사용자 배정은 MDM에 등록된 사용자 기준이다. 수리맵 계정과 MDM 사용자가 같다는 보장이 없으면 콘텐츠가 엉뚱한 사람의 단말에 남을 수 있다.
- **추론:** 사건 종료 뒤 파기는 배정 해제 시 삭제 설정에 달려 있다. Knox Manage는 이 설정이 기본으로 꺼져 있다. 단말별 삭제 완료를 API로 확인하는 방법도 문서에서 찾지 못했다.
- **추론:** Knox Manage의 파일 위치는 공용 미디어 폴더다. 실종자 정보처럼 개인정보가 든 사건 데이터를 이 위치에 두면 다른 앱에서 읽힐 수 있는지 따로 확인해야 한다.
- **추론:** 지도 타일 묶음처럼 사건과 무관하게 지역 단위로 고정된 데이터는 MDM 콘텐츠로 미리 넣는 방법을 검토할 수 있다. 사건, 배정, 실종자 정보는 이 방법에 맞지 않는다.

## 3. 현장 업무 제품에서 오프라인 준비 상태를 관리하는 방법

| 제품 | 오프라인 데이터를 받는 경로 | 상태가 만들어지는 곳 | 관리자가 보는 곳 |
|---|---|---|---|
| Esri ArcGIS Field Maps | ArcGIS 서버에서 모바일 앱으로 지도 영역을 내려받는다. | 다운로드할 때 서버에 복제본이 등록된다. | 피처 서비스의 replica 정보(`replicaOwner`, `creationDate`, `lastSyncDate`) |
| Dynamics 365 Field Service | 오프라인 프로필에 따라 Dataverse에서 모바일 앱으로 내려받는다. | 앱의 Offline Status와 Device Status 화면, 앱 원격 측정 | Sync Errors 화면, Application Insights 대시보드 |
| Salesforce Field Service | priming으로 Salesforce에서 모바일 앱으로 내려받는다. | 앱 | 관리자 화면은 확인하지 못했다. |
| ServiceNow Mobile | ServiceNow 인스턴스에서 모바일 앱으로 캐시를 내려받는다. | 앱의 캐시 크기와 마지막 캐시 시각 | 관리자 화면은 확인하지 못했다. |
| SAP Field Service Management | 모바일 앱에서 동기화가 시작된다. | 앱 | 관리자 화면은 확인하지 못했다. |
| Mapbox Maps SDK | SDK API로 Mapbox 서버에서 tile region을 내려받는다. | 단말의 tile store | 서버 쪽 기록은 문서에 없다. |

어느 제품의 공식 문서에도 오프라인 준비 상태가 MDM에서 만들어진다는 내용은 없었다.

### Esri ArcGIS Field Maps

- **사실:** 오프라인 지도 영역은 Field Maps Designer에서 정한다. 모바일 작업자는 Field Maps 앱에서 그 영역을 보고 내려받는다. 웹 지도 하나에는 오프라인 지도 영역을 16개까지 만들 수 있다. ([Field Maps 다운로드 준비](https://doc.esri.com/en/arcgis-field-maps/latest/prepare-maps/prepare-maps-for-download.html))
- **사실:** 내려받은 지도는 요청할 때만 갱신된다. 다른 작업자의 수정은 동기화하지 않으면 보이지 않는다. ([Field Maps 다운로드 준비](https://doc.esri.com/en/arcgis-field-maps/latest/prepare-maps/prepare-maps-for-download.html))
- **사실:** 지도 소유자는 지도 영역 패키지의 갱신 주기를 정할 수 있다. 타일 레이어는 예약 갱신 대상이 아니어서 소유자가 직접 갱신해야 한다. 지도 영역의 패키지 예약은 만든 지 1년 뒤에 만료된다. ([ArcGIS Online 지도 오프라인 사용](https://doc.esri.com/en/arcgis-online/latest/manage-data/take-maps-offline.html))
- **사실:** 피처 데이터를 내려받으면 오프라인 데이터가 geodatabase 파일로 저장된다. 그 파일은 피처 서비스에 등록된다. 문서에서는 더는 필요 없으면 등록을 해제해 피처 서비스에 남은 추적 정보를 정리하라고 권한다. ([ArcGIS 오프라인 데이터](https://developers.arcgis.com/documentation/offline-mapping-apps/partially-offline-apps/offline-data/))
- **사실:** 피처 서비스의 replica 정보에는 `replicaName`, `replicaID`, `replicaOwner`, `creationDate`, `lastSyncDate`가 있다. ([Replica Info REST](https://developers.arcgis.com/rest/services-reference/enterprise/replica-info/))
- **사실:** Esri의 MDM 기술 문서(2025년 7월)에 적힌 MDM 활용 분야는 앱 설정(App configuration), 앱별 VPN(App tunnel), 보안 정책과 접근 제어, SSO다. Field Maps의 MDM 설정 key에는 `portalURL`, `isAutoSyncEnabled`, 위치 공유 업로드 주기 같은 값이 있다. 같은 문서에는 "ArcGIS mobile apps do not support any MDM vendor application programming interfaces (APIs) for customization."라고 적혀 있다. ([ArcGIS Field Apps and MDM Support](https://content.esri.com/support/downloads/other_/arcgis%20mobile%20apps%20and%20mdm%20support%20technical%20paper%20-%20july%202025.pdf))
- **추론:** Esri에서 관리자가 "누가 어느 지도 영역을 언제 받았고 언제 마지막으로 동기화했는가"를 서버 쪽에서 알 수 있는 근거는 replica 기록이다. 이 기록은 앱의 다운로드로 업무 서버에 생긴다. MDM은 Field Maps 앱 설치와 설정 값 전달에 쓰인다.

### Microsoft Dynamics 365 Field Service

- **사실:** 오프라인 프로필을 설정하고 처음 데이터를 내려받은 뒤에는 모바일 앱이 offline-first로 동작한다. 동기화 간격은 테이블마다 5분에서 하루 사이로 정한다. 동기화는 앱이 활성 상태일 때만 시작된다. 단말이 인터넷에 연결되어 있어야 한다. ([Dynamics 365 오프라인 동기화](https://learn.microsoft.com/en-us/dynamics365/field-service/mobile/offline-data-sync))
- **사실:** 앱의 Offline Status 화면에는 동기화된 테이블의 마지막 갱신 시각이 나온다. 오프라인 동기화 아이콘을 누르면 Device Status 화면이 열린다. 관리자는 Settings > Sync Errors에서 과거 동기화 오류를 본다. ([Dynamics 365 오프라인 동기화](https://learn.microsoft.com/en-us/dynamics365/field-service/mobile/offline-data-sync))
- **사실:** 동기화 뒤 오프라인 프로필 조건에 맞지 않는 데이터는 단말에서 지워진다. 앱을 초기화하면 단말의 오프라인 데이터베이스가 지워진다. ([Dynamics 365 오프라인 동기화](https://learn.microsoft.com/en-us/dynamics365/field-service/mobile/offline-data-sync))
- **사실:** Microsoft의 예시 대시보드를 쓰려면 앱 원격 측정을 Azure Application Insights로 내보내야 한다. 대시보드에는 동기화 유형(`FIRST_SYNC`, `DELTA_SYNC`), 일별 동기화 성공률, 사용자별 동기화 상세가 나온다. 문서에는 버전 정보가 사용자가 동기화를 실행할 때만 수집된다고 적혀 있다. ([Field Service 모바일 원격 측정 예시](https://learn.microsoft.com/en-us/dynamics365/guidance/resources/field-service-mobile-sample-telemetry))
- **추론:** Microsoft는 Intune을 함께 파는 회사다. 그런데도 Field Service의 오프라인 동기화 상태는 Intune이 아니라 앱 화면과 앱 원격 측정으로 보여 준다.

### Salesforce Field Service

- **사실:** Trailhead에는 Field Service 모바일 앱이 "uses offline priming to make sure mobile workers always have access to their data."라고 적혀 있다. priming은 예약과 작업 데이터를 백그라운드에서 단말로 자동으로 내려받는 방식이다. 기본 범위는 오늘 앞뒤 45일, 모두 90일이다. 관리자는 Field Service Mobile Settings에서 이 범위를 줄일 수 있다. ([Trailhead Field Service Mobile](https://trailhead.salesforce.com/content/learn/modules/field-service-mobile/set-up-a-mobile-worker))
- **사실:** Briefcase는 오프라인에서 볼 레코드를 사용자와 그룹별로 정하는 정의다. 메타데이터 API에는 priming용 유형이 있다. ([BriefcaseDefinition 메타데이터](https://developer.salesforce.com/docs/atlas.en-us.api_meta.meta/api_meta/meta_briefcasedefinition.htm))
- 원문 미확인: Salesforce Help의 offline priming 문서는 페이지가 열리지 않아 읽지 못했다. 관리자가 사용자별 priming 상태를 보는 화면이 있는지 확인하지 못했다.

### ServiceNow Mobile

- **사실:** 오프라인 모드는 사용자가 접근할 수 있는 앱의 레코드로 단말에 캐시를 만든다. 캐시는 기본으로 단말에 내려받은 지 48시간 뒤에 만료된다. 48시간 안에 동기화되지 않은 변경은 outbox로 옮겨진다. outbox의 변경은 사용자가 다음에 로그아웃할 때 동기화될 수 있다. ([ServiceNow 오프라인 모드](https://github.com/ServiceNow/ServiceNowDocs/blob/australia/markdown/mobile/mobile-offline-mode.md))
- **사실:** iOS에서는 Expires 필드로 캐시 만료 시각이 표시된다. Android에서는 캐시 크기와 마지막 캐시 시각만 표시된다. ([ServiceNow 오프라인 모드](https://github.com/ServiceNow/ServiceNowDocs/blob/australia/markdown/mobile/mobile-offline-mode.md))
- **사실:** 오프라인 데이터에 들어갈 항목은 관리자가 하나씩 켜야 한다. ([ServiceNow 오프라인 모드](https://github.com/ServiceNow/ServiceNowDocs/blob/australia/markdown/mobile/mobile-offline-mode.md))

### SAP Field Service Management

- **사실:** SAP 교육 과정에는 "The mobile applications are designed to work offline, going online only to synchronize data when needed."라고 적혀 있다. 동기화는 모바일 앱이나 사용자가 시작한다. 동기화 내용은 권한 설정으로 정해진다. ([SAP Learning, Utilizing SAP Mobile Field Service](https://learning.sap.com/courses/managing-sap-field-service-management/utilizing-sap-mobile-field-service))
- 원문 미확인: help.sap.com의 FSM 동기화 설정 문서는 찾지 못했다.

### Mapbox와 Google Maps Platform

- **사실:** Mapbox Android SDK에서는 tile region을 `TileStore.loadTileRegion`으로 내려받는다. 진행은 `TileRegionLoadProgressCallback`으로, 완료와 오류는 `TileRegionCallback`으로 받는다. 내려받은 영역 목록은 `getAllTileRegions()`로 읽는다. 오프라인 지도는 새 지도 데이터로 자동 갱신되지 않는다. ([Mapbox 오프라인 데이터 관리](https://docs.mapbox.com/android/maps/guides/offline/manage-offline-data/), [Mapbox 오프라인](https://docs.mapbox.com/android/maps/guides/offline/))
- **사실:** 고유 tile pack 수의 누계는 750개를 넘을 수 없다. tile region에는 이름, 설명, 다운로드 날짜 같은 사용자 정의 metadata를 붙일 수 있다. ([Mapbox 오프라인 개념](https://docs.mapbox.com/android/maps/guides/offline/concepts/))
- **추론:** Mapbox의 오프라인 상태는 단말 안에서만 관리된다. 서버나 관리자에게 이 상태를 알리려면 앱에서 따로 보내야 한다.
- 원문 미확인: Google Maps Platform Android SDK의 오프라인 영역 다운로드 API나 그 상태 값은 찾지 못했다. 검색 결과에는 Google 문서가 나오지 않았다.

## 4. 기업 기술 블로그

### 찾은 글

| 회사 | 제목과 URL | 핵심 사실 |
|---|---|---|
| Uber | How Uber's New Driver App Overcomes Network Lag (2018-11-28), https://www.uber.com/blog/driver-app-optimistic-mode/ | 도시의 통신 음영 지역 때문에 기사가 운행을 시작하거나 끝내지 못하는 문제를 다룬다. Optimistic Mode에서는 연결이 없어도 기사가 운행을 끝낼 수 있다. 낙관적 요청과 마지막 낙관적 상태는 디스크에 저장된다. 앱을 다시 실행하면 저장된 요청이 동기화 대기열에 다시 들어간다. 데이터 미리 받기나 단말 상태 보고는 다루지 않는다. |
| Grab | Driving Southeast Asia Forward Through People-Focused Design (2019-11-05), https://engineering.grab.com/driving-sea-forward-through-people-focused-design | "A loss of connectivity shouldn't mean the end of your product's experience."라고 적혀 있다. 약한 신호에서는 시각 단서, 안내 문구, 캐시된 콘텐츠로 화면을 쓸 수 있게 하라고 권한다. 단말을 여러 사람이 함께 쓰는 경우가 있다고 적혀 있다. 운전자는 단말을 대시보드에 거치한다고 적혀 있다. |

- **추론:** 두 글 모두 오프라인 동작을 앱 설계 문제로 다룬다. MDM이나 단말 관리 도구는 언급되지 않는다. Grab 글의 기기 공유는 소비자 가족 공유이므로 업무폰 교대 사용과는 다르다.

### 본문을 확인하지 못한 글

- DoorDash "Our "Tech Stack" in the Android Dasher App"(https://careersatdoordash.com/blog/our-tech-stack-in-the-android-dasher-app/)은 검색 요약에 Dasher 앱이 Room으로 로컬 데이터를 캐시한다고 나왔다. 본문은 403 응답과 스크립트 보호로 읽지 못했다. 그래서 근거로 쓰지 않았다.

### 찾지 못한 글과 검색한 내용

- 한국 기업: "라이더 앱 오프라인 네트워크 끊김"(배민, 쿠팡이츠, 카카오모빌리티), techblog.woowahan.com의 "오프라인, 단말, 기기 공유", "PDA 앱 물류 단말 공용 로그인", toss.tech의 "단말기 원격 업데이트 상태 모니터링 POS", "쿠팡 엔지니어링 물류센터 PDA 앱 오프라인", "쏘카 차량 단말 상태", "카카오모빌리티, 당근, 네이버 D2 현장 작업자 앱 오프라인 데이터 미리 받기"를 검색했다. 현장 작업자 앱의 오프라인 데이터 준비, 기기 공유, 단말 상태 보고를 다룬 기술 블로그 글은 나오지 않았다.
- **사실:** 우아한형제들 "라이더스앱(iOS) 개발기"(2018-05-19, https://techblog.woowahan.com/2573/)는 라이더 앱을 다루지만 오프라인 처리, 망 끊김, 데이터 미리 받기, 기기 공유를 다루지 않는다.
- **사실:** 토스 "포스를 확장하는 가장 빠른 방법, 포스 플러그인"(https://toss.tech/article/toss-pos-plugin)은 검색 요약상 POS 플러그인 구조를 다룬다. 단말 상태 관리는 다루지 않는다.
- 해외 기업: Lyft(eng.lyft.com), DoorDash(careersatdoordash.com), Grab(engineering.grab.com)에서 "driver app offline", "Dasher app offline", "offline driver app sync"를 검색했다. 위 표의 글 말고 오프라인 데이터 준비나 단말 상태 보고를 다룬 글은 찾지 못했다. Airbnb는 현장 작업자 앱이 아니어서 검색하지 않았다.
- 라인, 배민 라이더, 쿠팡 배송 기사 앱은 기술 블로그에서 관련 글을 찾지 못했다. 라인은 별도 검색어로 찾지 않았다.

## 5. 공유 단말에서 로그인 사용자가 바뀔 때의 오프라인 데이터

Android 다중 사용자, Entra 공유 단말 모드의 MSAL 전역 로그아웃, Knox Manage 공유 단말의 지속형과 임시형 세션은 [단말 신뢰 계층 조사의 공유 단말 모델](device-trust-layer-research.md#공유-단말-모델)에 있다. 그 문서에 따르면 MSAL 전역 로그아웃은 계정과 캐시된 토큰만 지운다. 앱 데이터는 앱 개발자가 지워야 한다. 여기서는 그 문서에 없는 사실만 적는다.

### Microsoft 공유 단말 모드와 Intune

- **사실:** Microsoft 365 현장 근무자 문서에는 공유 단말 모드에서 single sign-out을 쓰면 다음 사용자에게 이전 데이터가 보이지 않는다고 적혀 있다. 조건은 "given that the apps ensure cleaning up of any cached user data and app protection policies are applied"다. ([현장 근무자 공유 단말 관리](https://learn.microsoft.com/en-us/microsoft-365/frontline/flw-shared-devices))
- **사실:** Intune의 Android Enterprise 단말 제한 설정에는 "Clear local data in apps not optimized for Shared device mode"가 있다. 설명은 "The app's local data is cleared whenever a user signs out of an app that's optimized for shared device mode."다. 공유 모드로 등록한 Android 9 이상의 전용 단말에서만 쓸 수 있다. 공유 단말 모드에 맞춘 Microsoft 앱의 예는 Teams와 Managed Home Screen이다. ([Intune Android Enterprise 단말 제한 설정](https://learn.microsoft.com/en-us/intune/device-configuration/templates/ref-device-restrictions-android-enterprise))
- **사실:** Intune 선택적 초기화는 Intune App SDK가 들어 있는 앱에만 적용된다. Android에서는 앱 보호 정책을 배포해야 쓸 수 있다. 사용자가 앱을 열어야 초기화가 일어난다. 요청 뒤 최대 30분이 걸릴 수 있다. 관리 센터에서 요청 상태(pending, failed, successful)를 볼 수 있다. ([Intune 회사 데이터 선택적 초기화](https://learn.microsoft.com/en-us/intune/app-management/protection/wipe-corporate-data))
- **추론:** Intune 설정으로 지우는 범위는 앱의 로컬 데이터 전체다. 사건별 데이터와 공용 지도 캐시를 나눠 지우는 기능은 아니다. 로그아웃 연동도 Entra 공유 단말 모드와 그 모드에 맞춘 앱이 있어야 동작한다.

### MDM의 앱 데이터 삭제 명령

- **사실:** Android Management API의 `CLEAR_APP_DATA` 명령 설명은 "Clears the application data of specified apps. This is supported on Android 9 and above."다. ([issueCommand](https://developers.google.com/android/management/reference/rest/v1/enterprises.devices/issueCommand))
- **사실:** Knox Manage OpenAPI의 `sendDeviceControlForDeleteAppData` 설명은 "This API sends a Device Control to delete application internal data. It runs on Android."다. ([Knox Manage API 명세](https://docs.samsungknox.com/dev/knox-manage/assets/km-api.yml))
- **추론:** 두 명령 모두 단말 단위로 앱 데이터 전체를 지운다. 단말이 꺼져 있거나 관리 서버에 닿지 못하면 명령 전달이 늦어진다. 갱신 주기는 [MDM 통신 상태 조사](mdm-device-liveness-research.md#1-mdm과-emm에서-제공하는-통신-상태-값)에 있다. 앱 데이터를 전부 지우면 아직 서버에 보내지 못한 수색 기록도 함께 지워질 수 있다.

### 책임의 위치

| 수단 | 지우는 범위 | 실행 조건 | 책임 위치 |
|---|---|---|---|
| Android 임시 사용자, Knox Manage 임시형 세션 | 사용자 세션 전체 | 단말을 그 방식으로 등록 | MDM과 OS |
| Intune 공유 모드 로그아웃 연동 | 목록에 넣은 앱의 로컬 데이터 전체 | Entra 공유 모드 등록, Android 9 이상, 공유 모드 지원 앱에서 로그아웃 | MDM |
| `CLEAR_APP_DATA`, Knox Manage 앱 데이터 삭제 | 지정한 앱의 데이터 전체 | 관리자 명령, 단말 연결 | MDM |
| Intune 선택적 초기화 | 앱의 회사 데이터 | Intune App SDK, 앱 실행 | MDM과 앱이 함께 |
| MSAL 전역 로그아웃 뒤 앱의 정리 | 앱에서 정한 범위 | 앱 구현 | 앱 |

- **추론:** 사용자 세션이나 앱 데이터를 통째로 지우는 일은 MDM 명령과 설정으로 할 수 있다. 앱 안에서 이전 사용자의 데이터만 골라 지우는 일은 조사한 공식 문서에서 모두 앱 개발자 몫으로 적혀 있다.
- **추론:** 수리맵에서는 계정이 바뀌어도 같은 사건을 이어서 수색할 수 있다. 공용 지도 타일 캐시는 사건과 무관하다. 미전송 기록은 지우면 안 된다. 이런 구분은 MDM의 통째 삭제로 표현할 수 없다.

## 6. 브라우저 보완 조사 (2026-11-18)

1차 조사에서 열리지 않은 Salesforce, SAP, DoorDash 페이지를 브라우저로 다시 열었다. 한국 기업 기술 블로그 9곳도 다시 검색했다.

### Salesforce Field Service

- **사실:** 오프라인용 레코드, 메타데이터, 페이지 레이아웃, LWC 코드는 Field Service 모바일 앱이 미리 내려받는다. ([Recommendations and Considerations](https://help.salesforce.com/s/articleView?id=service.mfs_offline_recs_considerations.htm&type=5))
- **사실:** 서버에서는 프로필별 offline Briefcase가 만들어진다. 관리자는 Briefcase Builder에서 priming할 데이터를 본다. ([How Performance Priming Briefcases Work](https://help.salesforce.com/s/articleView?id=service.mfs_perf_priming_briefcases_work.htm&type=5), [Offline Behaviors](https://help.salesforce.com/s/articleView?id=service.mfs_offline_behaviors.htm&type=5))
- **사실:** priming 중 네트워크 오류가 나면 앱의 offline priming 화면에서 다시 동기화한다. ([Offline Priming FAQ](https://help.salesforce.com/s/articleView?id=service.mfs_offline_questions.htm&type=5))
- **확인하지 못함:** 단말별 준비 완료 현황을 관리자가 한곳에서 보는 화면은 공식 문서에서 찾지 못했다. Briefcase Builder는 준비할 대상을 정하는 화면이다.

### SAP Field Service Management

- **사실:** 관리자 앱의 User Statistics에 `Last Sync`, `Last Sync Client Id`, `Last Sync Client Version`이 표시된다. `Last Sync`는 그 사용자 계정으로 모바일 앱을 마지막으로 동기화한 시각이다. ([User Statistics](https://help.sap.com/docs/SAP_FIELD_SERVICE_MANAGEMENT/fsm_admin/user-statistics.html))
- **추론:** SAP FSM에서는 동기화 상태를 단말이 아니라 사용자 계정 기준으로 보여 준다. 마지막으로 동기화한 앱 설치의 client ID도 계정 기록에 함께 남는다.
- **사실:** 모바일 오프라인 저장 대상은 Admin Policy Groups의 Data Sync Rules로 정한다. 이 내용은 검색 결과 요약에서만 확인했다. ([Data Sync Rules](https://help.sap.com/docs/SAP_FIELD_SERVICE_MANAGEMENT/fsm_mobile/sync-performance-management-data-sync-rules.html))

### DoorDash

- **사실:** [App health through metric-aware rollouts](https://careersatdoordash.com/blog/app-health-through-metric-aware-rollouts)는 지연, 오류, 크래시 지표가 나빠지면 rollout을 멈추는 방법을 다룬다. Dasher 단말 상태 보고의 항목이나 주기는 다루지 않는다.
- Dasher 앱의 오프라인 준비나 기기 공유를 다룬 글은 찾지 못했다.

### 한국 기업 기술 블로그

- 9곳(우아한형제들, 토스, 카카오, 카카오모빌리티, 쿠팡, 당근, 쏘카, 네이버, 라인)에서 현장 작업자 앱의 오프라인 데이터 준비, 공유 단말, 단말 상태 보고, MDM을 다룬 글은 찾지 못했다.
- 가까운 글은 다음과 같다. 모두 직접 읽었지만 같은 사례로 보지 않았다.
  - 우아한형제들 [집 나간 네트워크는 돌아왔는데 React.lazy는 왜 안 돌아올까](https://techblog.woowahan.com/27330/): 배민주문접수 웹뷰에서 주요 화면 코드를 미리 받아 오프라인에서도 그리도록 고쳤다. 업무 데이터가 아니라 화면 코드 준비다.
  - 라인 [Recode 프로젝트](https://techblog.lycorp.co.jp/ko/recode_project): 데마에칸 배송원 앱과 가맹점 태블릿 앱의 강제 업데이트, 현장 단말의 스토어 계정 로그아웃 문제를 다룬다.
  - 라인 [SAGE 개발기 1편](https://techblog.lycorp.co.jp/ko/ai-agent-platform-sage-dev-log-part-1): VPN 장애 원인을 찾을 때 단말 보안 솔루션 상태, MDM 등록 상태, 계정 상태를 구분한다.
  - 쏘카 [차량용 단말을 위한 IoT 파이프라인 구축기 #2](https://tech.socarcorp.kr/mobility/2022/02/09/socar-iot-pipeline-2.html): 차량 단말이 `/log`, `/boot` 엔드포인트로 상태를 보고한다. 작업자 휴대 단말이 아니라 차량 장착 단말이다.
- 사이트마다 "오프라인", "공유 단말", "단말 상태", "MDM"을 라이더, 기사, 작업자, 배송과 묶어 검색했다.

## 판단에 쓸 근거

이 절은 판단에 쓸 근거를 나란히 정리한 것이다. 설계 결정은 하지 않는다.

| 관점 | MDM 책임 쪽 근거 | 업무 애플리케이션 책임 쪽 근거 |
|---|---|---|
| 상태를 아는 곳 | keyed app state로 앱에서 정한 상태를 MDM 콘솔과 Android Management API에서 볼 수 있다(1절). | MDM 값으로는 앱 내부 파일이나 준비 상태를 알 수 없다. keyed app state를 써도 판정과 전송은 앱에서 이뤄진다(1절). |
| 상태 전달 경로 | keyed app state의 높은 우선순위 보고는 1분 안에 Pub/Sub로 전달된다([MDM 통신 상태 조사](mdm-device-liveness-research.md)). | keyed app state는 Google Play 스토어 앱과 Google 서버를 거친다. PII를 넣지 말라는 안내가 있다. 하루 고유 key 500개 제한이 있다(1절). Intune과 Knox Manage에서 이 값을 보여 준다는 문서는 찾지 못했다(1절). |
| 데이터 배포 | Knox Manage와 Workspace ONE에는 파일 배포 기능이 있다. Knox Manage는 MBTILES, MMPK 같은 지도 형식을 받는다(2절). | 배포 단위가 조직, 그룹, 사용자다. Knox Manage OpenAPI에는 콘텐츠 API가 없다. 단말별 다운로드 상태를 API로 읽는 방법을 찾지 못했다. 배정 해제 시 삭제는 기본으로 꺼져 있다(2절). |
| 사건 수명 주기 | 지역 단위로 고정된 지도 타일은 MDM 콘텐츠로 미리 넣을 수 있다(2절 추론). | 사건 데이터는 사건마다 만들어진다. 배정이 바뀌면 갱신해야 한다. 종료 뒤에는 지워야 한다. 수리맵 서버에는 사건, 배정, 종료 정보가 이미 있다(2절 추론, [오프라인 패키지 기능 정의 문서](../features/offline-package.md)). |
| 현장 업무 제품 사례 | Esri는 앱 배포, 설정 값, 앱별 VPN, 접근 제어, SSO를 MDM으로 처리하라고 안내한다(3절). | Esri, Dynamics 365, Salesforce, ServiceNow, SAP FSM, Mapbox 모두 앱이 업무 서버에서 오프라인 데이터를 내려받는다. 준비 상태와 동기화 상태도 앱과 업무 서버에서 만들어진다(3절). |
| 기업 기술 블로그 | 찾지 못했다(4절). | Uber와 Grab 글은 오프라인 동작을 앱 설계 문제로 다룬다(4절). |
| 공유 단말의 사용자 교체 | MDM 명령과 Intune 공유 모드 설정으로 앱 데이터 전체를 지울 수 있다(5절). | Microsoft 문서에는 앱이 사용자 캐시를 지워야 한다고 적혀 있다. MDM으로 지울 때는 사건별 데이터, 공용 지도 캐시, 미전송 기록을 구분할 수 없다(5절). |
| 계정 기준 | MDM의 사용자 필드는 단말에 연결된 관리 사용자다([MDM 통신 상태 조사](mdm-device-liveness-research.md)). | 여러 사람이 업무폰 한 대를 돌려쓴다. 쓸 때마다 각자 자기 계정으로 로그인한다. 지휘자가 알고 싶은 것은 배정된 계정의 준비 상태다. MDM 값은 단말 단위다([MDM 통신 상태 조사](mdm-device-liveness-research.md)). |
| ADR-0037과의 관계 | ADR-0037에서 업무폰 인증은 인프라 계층에 두었다. | **추론:** 인증은 요청의 출처를 증명하는 일이다. 오프라인 준비 상태는 업무 데이터가 단말에 갖춰졌는지를 확인하는 일이다. 두 일의 성격이 다르다. |

사용자와 정할 질문:

- 실제 기관 MDM이 어느 제품인지, 그 제품에서 keyed app state를 콘솔이나 API로 보여 주는지
- 상황판의 준비 상태가 단말 기준이어야 하는지, 배정된 계정 기준이어야 하는지
- 지도 타일처럼 사건과 무관한 데이터를 MDM 콘텐츠로 미리 넣는 방법을 검토할지
- 계정이 바뀔 때 지울 데이터와 남길 데이터(공용 지도 캐시, 같은 사건 데이터, 미전송 기록)를 어떻게 나눌지

## 확인하지 못한 것

- Intune, Knox Manage, Workspace ONE UEM 콘솔에 일반 업무 앱의 keyed app state가 표시되는지는 제품 문서로 확인하지 못했다.
- Citrix Endpoint Management의 관리 구성 피드백 문서는 본문을 읽지 못했다.
- Knox Manage 콘텐츠의 단말별 다운로드 상태 화면과 그 API는 문서에서 찾지 못했다. 공용 미디어 폴더에 둔 파일을 다른 앱이 읽을 수 있는지도 확인하지 못했다.
- Workspace ONE UEM Files/Actions의 Android 10 이상 제한은 결함 기록과 커뮤니티 글의 검색 요약으로만 봤다. VIEW DEVICES 화면에 나오는 다운로드 상태 값도 확인하지 못했다.
- Intune에서 Android Enterprise 단말에 일반 파일을 배포하는 공식 기능이 있는지 확인하지 못했다.
- Salesforce Help의 offline priming 문서와 help.sap.com의 FSM 동기화 문서는 읽지 못했다. 두 제품에서 관리자가 사용자별 준비 상태를 보는 화면이 있는지 모른다.
- ServiceNow 관리자 화면에서 사용자별 캐시 다운로드 상태를 보는 기능이 있는지 확인하지 못했다.
- Esri Field Maps에서 미리 정의한 지도 영역을 내려받을 때 replica가 단말마다 하나씩 생기는지는 Maps SDK 일반 문서로만 확인했다. Field Maps 전용 문서로 확인하지 못했다.
- Google Maps Platform의 오프라인 영역 다운로드 기능은 찾지 못했다.
- DoorDash Dasher 앱 글은 본문을 읽지 못했다. 한국 기업 기술 블로그에서는 관련 글을 찾지 못했다. 라인은 별도 검색어로 찾지 않았다.
- 실제 기관 MDM 환경에서 keyed app state나 콘텐츠 배포가 얼마나 늦게 반영되는지는 측정하지 않았다.

## 출처

접근일은 모두 2026-11-18이다. 표시가 없는 항목은 1차 자료다.

### Google과 Android

- Android Developers, Send app feedback to EMMs: https://developer.android.com/work/app-feedback/overview
- Android Developers, KeyedAppState 레퍼런스: https://developer.android.com/reference/androidx/enterprise/feedback/KeyedAppState
- Android Developers, Set up managed configurations: https://developer.android.com/work/managed-configurations
- Android Management API, Retrieve feedback from apps: https://developers.google.com/android/management/app-feedback
- Android Management API, `enterprises.devices.issueCommand`: https://developers.google.com/android/management/reference/rest/v1/enterprises.devices/issueCommand

### Microsoft

- Intune, Android Enterprise device restriction settings: https://learn.microsoft.com/en-us/intune/device-configuration/templates/ref-device-restrictions-android-enterprise
- Intune, How to wipe only corporate data from apps: https://learn.microsoft.com/en-us/intune/app-management/protection/wipe-corporate-data
- Microsoft 365, Manage shared devices for frontline workers: https://learn.microsoft.com/en-us/microsoft-365/frontline/flw-shared-devices
- Dynamics 365 Field Service, Configure offline data synchronization: https://learn.microsoft.com/en-us/dynamics365/field-service/mobile/offline-data-sync
- Dynamics 365, Sample telemetry dashboard for use of mobile app offline profile: https://learn.microsoft.com/en-us/dynamics365/guidance/resources/field-service-mobile-sample-telemetry

### Samsung

- Knox Manage, Add supported content: https://docs.samsungknox.com/admin/knox-manage/new-console/manage-content/add-supported-content/
- Knox Manage, Assign, push, or unassign content: https://docs.samsungknox.com/admin/knox-manage/new-console/manage-content/assign-push-or-unassign-content/
- Knox Manage, Content overview(기존 콘솔): https://docs.samsungknox.com/admin/knox-manage/original-console/configure/content/content-overview/
- Knox Manage, Distribute content(기존 콘솔): https://docs.samsungknox.com/admin/knox-manage/original-console/configure/content/manage-content/distribute-content/
- Knox Manage API reference 25.07 명세 파일: https://docs.samsungknox.com/dev/knox-manage/assets/km-api.yml

### Omnissa와 VMware

- Workspace ONE UEM, Managed Content Repository: https://docs.omnissa.com/WS1UEMMobileContentManagement/ManagedContentRepository
- Workspace ONE UEM, Mobile Content Management FAQ: https://docs.omnissa.com/WS1UEMMobileContentManagement/MCMFAQ
- Workspace ONE UEM, Content Management Using the Console: https://docs.omnissa.com/WS1UEMMobileContentManagement/ContentMgmntUsingConsole
- VMware EUC 블로그, Android Series Ep 14: Feedback Channel(2020-05-14): https://blogs.vmware.com/euc/?p=25657

### 현장 업무 제품과 지도 SDK

- Esri, Prepare maps for download: https://doc.esri.com/en/arcgis-field-maps/latest/prepare-maps/prepare-maps-for-download.html
- Esri, Take web maps offline(ArcGIS Online): https://doc.esri.com/en/arcgis-online/latest/manage-data/take-maps-offline.html
- Esri, Offline data(ArcGIS 개발자 문서): https://developers.arcgis.com/documentation/offline-mapping-apps/partially-offline-apps/offline-data/
- Esri, Replica Info(ArcGIS REST API): https://developers.arcgis.com/rest/services-reference/enterprise/replica-info/
- Esri, ArcGIS Field Apps and Mobile Device Management (MDM) Support(2025년 7월): https://content.esri.com/support/downloads/other_/arcgis%20mobile%20apps%20and%20mdm%20support%20technical%20paper%20-%20july%202025.pdf
- Salesforce Trailhead, Set Up Mobile Workers: Licenses, Sharing, and Priming: https://trailhead.salesforce.com/content/learn/modules/field-service-mobile/set-up-a-mobile-worker
- Salesforce, BriefcaseDefinition 메타데이터: https://developer.salesforce.com/docs/atlas.en-us.api_meta.meta/api_meta/meta_briefcasedefinition.htm
- ServiceNow, Offline mode(ServiceNowDocs GitHub, australia 브랜치): https://github.com/ServiceNow/ServiceNowDocs/blob/australia/markdown/mobile/mobile-offline-mode.md
- SAP Learning, Utilizing SAP Mobile Field Service: https://learning.sap.com/courses/managing-sap-field-service-management/utilizing-sap-mobile-field-service
- Mapbox, Offline Maps(Android): https://docs.mapbox.com/android/maps/guides/offline/
- Mapbox, Offline concepts(Android): https://docs.mapbox.com/android/maps/guides/offline/concepts/
- Mapbox, Manage offline data(Android): https://docs.mapbox.com/android/maps/guides/offline/manage-offline-data/

### 기업 기술 블로그

- Uber, How Uber's New Driver App Overcomes Network Lag(2018-11-28): https://www.uber.com/blog/driver-app-optimistic-mode/
- Grab, Driving Southeast Asia Forward Through People-Focused Design(2019-11-05): https://engineering.grab.com/driving-sea-forward-through-people-focused-design
- 우아한형제들, 라이더스앱(iOS) 개발기(2018-05-19): https://techblog.woowahan.com/2573/
