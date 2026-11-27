# 공용 업무폰의 미전송 기록 처리 사례 조사

- 조사 기준일: 2026-11-27
- 목적: 한 업무폰을 여러 경찰관이 개인 계정으로 번갈아 쓸 때, 로그아웃한 사람의 미전송 기록을 누구 이름으로 언제 보내고 보내지 못한 기록을 어떻게 다룰지 정하기 전에 비슷한 영역 제품의 공식 동작을 확인한다.
- 외부 서비스의 동작과 수치는 조사 시점의 기록이다. 공식 문서와 소스 코드에서 확인하지 못한 동작은 추측하지 않고 "공식 문서에서 찾지 못함"으로 적는다.
- 수리맵에 대한 판단은 [수리맵 의사결정에 적용](#수리맵-의사결정에-적용)에만 적고, 사실과 추론을 표시로 나눈다.

## 결론

로그아웃한 사람 이름으로 백그라운드 전송을 이어 가는 구조를 공식 문서로 설명한 사례는 **Apple Shared iPad 하나뿐이다**. Apple Shared iPad에서는 사용자가 로그아웃한 뒤에도 백그라운드 업로드를 이어 갈 수 있다. 이것도 앱이 아니라 운영체제 수준의 구조다. 동기화가 끝날 때까지 이전 사용자의 저장 공간이 유지된다. 로그아웃한 사람의 토큰을 앱에 따로 보관해 갱신하는 방식까지 문서로 확인한 것은 아니다. 새 사용자용 저장 공간이 부족하고, 기존 계정 중 동기화를 끝내 제거할 수 있는 계정도 없으면 새 사용자의 로그인이 실패할 수 있다. [Apple 플랫폼 보안](https://support.apple.com/guide/security/secd99f373ef/web), [Shared iPad 앱 최적화](https://developer.apple.com/education/shared-ipad)

앱 수준의 동기화 엔진과 현장 수집 앱에서는 대체로 다음 두 방식 중 하나를 쓴다.

1. **사용자별 저장소를 나누고 그 사용자가 다시 로그인할 때 보낸다.** Firestore SDK에서는 사용자 uid마다 쓰기 대기열을 따로 둔다. 사용자가 바뀌면 Firestore SDK에서는 새 사용자의 대기열로 바꿔 끼운다. CommCare에서는 사용자마다 암호화된 별도 DB를 쓴다. Realm에서는 로그아웃한 사용자의 정보를 기기에 남긴다. 다만 그 사용자의 동기화는 멈춘다. ([Firestore 소스](https://github.com/firebase/firebase-android-sdk/blob/71b1cea86d75cb23fd0b0e6805bfb41f10d07cee/firebase-firestore/src/main/java/com/google/firebase/firestore/local/LocalStore.java), [CommCare 소스](https://github.com/dimagi/commcare-android/blob/dce9068092b3c1f376d730d3b679233daf1e7448/app/src/org/commcare/models/database/user/DatabaseUserOpenHelper.java), [Realm 다중 사용자](https://www.mongodb.com/docs/atlas/device-sdks/sdk/kotlin/users/multi-user-applications.md))
2. **로그아웃 전에 다 보내라고 안내하고, 그렇지 않으면 잃을 수 있다고 경고한다.** Square 도움말에서는 대기 중인 오프라인 결제가 있으면 로그아웃하지 말라고 안내한다. 같은 도움말에서 영구 손실도 경고한다. Power Apps 문서에서는 동기화가 끝나기 전에 로그아웃하면 데이터가 보존되지 않는다고 적는다. Realm, Amplify DataStore, Salesforce Field Service 문서에도 같은 경고가 있다. ([Square](https://squareup.com/help/us/en/article/7777-process-card-payments-with-offline-mode), [Power Apps](https://learn.microsoft.com/en-us/power-apps/mobile/mobile-offline-works-overview))

보내지 못한 기록에 대해서는 범주에 따라 태도가 갈린다.

- **현장 수집 제품에서는 자동 폐기 대신 격리한다.** CommCare에서는 서버 처리 오류가 난 양식을 격리 상태로 옮긴다. 사용자에게는 격리 사유가 보인다. 사용자는 격리된 양식을 전송 대기열로 되돌리거나 직접 삭제할 수 있다. CommCare 앱에서는 격리 건수를 서버에 보고한다. QuickCapture에서는 실패 기록을 기기에 남긴다. 사용자에게는 다시 시도, 이메일로 DB 보내기, 직접 지우기의 선택지만 있다. ServiceNow에서는 문제가 있는 변경을 해결될 때까지 outbox에 남긴다. ODK Collect에서는 전송에 실패한 양식도 자동 전송 대상에 계속 넣는다.
- **시간 기한이 지나면 회수할 수 없는 사례는 결제와 인증 보안 정책이다.** Square의 오프라인 결제는 72시간이 지나면 만료되고 다시 처리할 수 없다. Salesforce Field Service 공지에서는 30일 동안 쓰지 않은 토큰이 만료된다고 안내했다. 같은 공지에서 다시 로그인하면 미동기 데이터가 지워진다고 안내했다.

조사한 현장 수집 제품의 공식 문서와 소스 코드에서 **재시도 횟수나 경과 시간만으로 기록을 자동 폐기하거나 재시도를 영구히 막는 규칙은 찾지 못했다**. 수리맵의 `FAILED_FINAL`(자동, 수동 재시도를 모두 막는 상태)과 같은 상태를 둔 사례로는 CommCare의 `local-processing-error` 격리가 가장 가깝다. CommCare에서는 기기에서 양식 자체를 처리할 수 없을 때만 이 상태를 쓴다. 서버 오류로 격리된 양식은 사용자가 되돌릴 수 있다.

## 조사 범위와 방법

공식 제품 문서, 공식 API 참조, 제품사가 공개한 소스 코드, IETF와 OpenID 명세만 사용했다. 커뮤니티 포럼의 사용자 경험담은 근거로 쓰지 않았다. 예외로 Salesforce는 Salesforce 직원이 공식 커뮤니티 그룹에 올린 공지를 썼다. 이 공지에 연결된 릴리스 노트는 JavaScript로만 열리는 페이지여서 본문을 확인하지 못했다. 소스 코드는 아래 커밋 기준으로 읽었다.

| 저장소 | 커밋 |
| --- | --- |
| `dimagi/commcare-android` | `dce9068092b3c1f376d730d3b679233daf1e7448` |
| `firebase/firebase-android-sdk` | `71b1cea86d75cb23fd0b0e6805bfb41f10d07cee` |
| `getodk/collect` | `37c804df62a238c30207c8024806ff3fbdbd9786` |
| `keycloak/keycloak` | `c965c68bc2d20e78ee579f9fed84ddb0f2ab7770` |

조사 질문은 세 가지다.

1. 미전송 기록이 남은 채 로그아웃하거나 다른 사용자가 로그인하면 어떻게 되는가? 원래 작성자는 어떻게 보존되는가?
2. 보낼 수 없는 기록에 재시도 상한, 시간 기한, 최종 실패 상태, 자동 폐기가 있는가? 사용자나 관리자는 어떻게 복구하는가?
3. 로그아웃 뒤 백그라운드 전송을 위한 토큰 보관 지침이 있는가?

다음 제품은 공식 공개 문서를 찾지 못해 표에서 뺐다.

- **경찰, 공공 안전**: Axon, Mark43, Motorola Solutions, Tyler의 모바일 보고서 오프라인 처리와 로그아웃 동작은 공개 문서에서 찾지 못했다. 고객 전용 문서일 가능성이 있다.
- **의료**: Epic Rover와 Haiku의 공유 기기 동작은 공개 문서에서 찾지 못했다.
- **KoboCollect**: ODK Collect를 바탕으로 만든 앱이지만, 사용자 전환과 미전송 양식에 대한 공식 문서는 찾지 못했다.
- **Couchbase Lite**: 한 기기 여러 사용자의 로그아웃 처리는 공식 문서에서 찾지 못했다. 재시도 정책은 [기존 조사](./offline-sync-recovery-benchmarks.md)에 있다.

## 현장 데이터 수집

| 제품 | 로그아웃, 사용자 전환과 미전송 기록 | 원래 작성자 보존 | 보내지 못한 기록 |
| --- | --- | --- | --- |
| **CommCare** (Dimagi) | 양식 기록은 사용자별 DB에 저장된다. DB 이름은 사용자 키 기록 ID로 정해진다. 사용자 키 기록에는 sandbox ID, 비밀번호 해시와 암호화된 키가 들어 있다. ([DatabaseUserOpenHelper](https://github.com/dimagi/commcare-android/blob/dce9068092b3c1f376d730d3b679233daf1e7448/app/src/org/commcare/models/database/user/DatabaseUserOpenHelper.java), [UserKeyRecord](https://github.com/dimagi/commcare-android/blob/dce9068092b3c1f376d730d3b679233daf1e7448/app/src/org/commcare/android/database/app/models/UserKeyRecord.java)) 로그아웃할 때 경고하거나 막는지는 공식 문서에서 찾지 못함. 문제 해결 문서에서는 로그아웃하기 전에 Unsent Forms를 먼저 보내라고 안내한다. 같은 문서에서 기기의 데이터를 지우면 미전송 양식을 잃을 수 있다고 경고한다. ([Troubleshooting](https://dimagi.atlassian.net/wiki/spaces/commcarepublic/pages/2143944848/CommCare+Android+Troubleshooting)) | 사용자별 DB에 있는 양식을 그 사용자 세션에서 보낸다(저장 구조에서 한 추론). 다른 사용자가 로그인했을 때 이전 사용자의 양식을 보내는지는 공식 문서에서 찾지 못함. | CommCare에서는 한 번 전송할 때 최대 2회 시도한다. 서버 처리 실패나 기록 오류가 나면 양식을 격리 상태(`limbo`)로 옮긴다. 이때 격리 사유도 저장한다. ([FormSubmissionHelper](https://github.com/dimagi/commcare-android/blob/dce9068092b3c1f376d730d3b679233daf1e7448/app/src/org/commcare/sync/FormSubmissionHelper.java), [FormRecord](https://github.com/dimagi/commcare-android/blob/dce9068092b3c1f376d730d3b679233daf1e7448/app/src/org/commcare/android/database/user/models/FormRecord.java)) 사용자는 격리 사유를 볼 수 있다. 기기 처리 오류가 아니면 사용자는 양식을 전송 대기열로 되돌릴 수 있다. 삭제는 사용자가 직접 고를 때만 일어난다. ([FormRecordListActivity](https://github.com/dimagi/commcare-android/blob/dce9068092b3c1f376d730d3b679233daf1e7448/app/src/org/commcare/activities/FormRecordListActivity.java)) CommCare 앱에서는 heartbeat 요청에 미전송 수와 격리 수를 실어 서버에 보고한다. ([HeartbeatRequester](https://github.com/dimagi/commcare-android/blob/dce9068092b3c1f376d730d3b679233daf1e7448/app/src/org/commcare/heartbeat/HeartbeatRequester.java)) 미전송 5건 초과나 마지막 동기화 후 5일 경과(기본값)는 홈 화면 동기화 버튼의 표시 계산에 쓰인다. 기록을 지우는 기준은 아니다. ([SyncDetailCalculations](https://github.com/dimagi/commcare-android/blob/dce9068092b3c1f376d730d3b679233daf1e7448/app/src/org/commcare/utils/SyncDetailCalculations.java)) |
| **ODK Collect / Central** | Collect에서는 Project마다 양식과 설정을 따로 둔다. 공식 문서에서는 한 기기를 여러 조사원이 서버의 서로 다른 계정으로 쓰는 경우를 Project 사용 예로 든다. 사용자를 바꿀 때 미전송 양식을 어떻게 처리하는지는 공식 문서에서 찾지 못함. ([Projects](https://docs.getodk.org/collect-projects/)) | Central에서는 업로드한 App User를 제출자로 기록한다. App User의 접근을 철회해도 이미 올린 제출의 제출자 표시는 남는다. App User를 기기나 역할 단위로 만들 수도 있다. 그때는 deviceID나 조사원 이름을 양식 메타데이터로 남기라고 Central 문서에서 안내한다. ([Central users](https://docs.getodk.org/central-users/)) Collect에서는 설정의 사용자 이름과 기기 ID를 양식 메타데이터에 넣는다. ([Collect settings](https://docs.getodk.org/collect-settings/)) | Collect에서는 완료 상태와 전송 실패 상태의 양식을 함께 자동 전송 대상에 넣는다. 소스에서 재시도 횟수나 시간 상한은 찾지 못했다. ([InstanceAutoSendFetcher](https://github.com/getodk/collect/blob/37c804df62a238c30207c8024806ff3fbdbd9786/collect_app/src/main/java/org/odk/collect/android/instancemanagement/send/autosend/InstanceAutoSendFetcher.kt)) 양식 삭제는 사용자가 직접 하거나, 전송 후 삭제 설정을 켰을 때 전송에 성공한 양식에만 일어난다. ([Collect forms](https://docs.getodk.org/collect-forms/), [Collect settings](https://docs.getodk.org/collect-settings/)) |
| **ArcGIS Survey123** | 로그아웃과 사용자 전환 시 Outbox 처리는 공식 문서에서 찾지 못함. 오프라인이면 Survey123에서는 응답을 Outbox에 저장한다. 연결된 뒤 사용자가 Outbox의 응답을 보낸다. ([Submit survey results](https://doc.esri.com/en/arcgis-survey123/latest/capture/field-app/submitsurveyresults.html)) | 공식 문서에서 찾지 못함 | 사용자는 Outbox의 응답을 보내기 전에 고칠 수 있다. 저장 공간 설정에서 사용자가 Drafts, Outbox, Sent, Inbox를 모두 지울 수 있다. 자동 폐기 규칙은 공식 문서에서 찾지 못함. ([Quick reference](https://doc.esri.com/en/arcgis-survey123/latest/capture/field-app/quickreferencegetanswers.html)) |
| **ArcGIS Field Maps** | 로그아웃할 때 미동기 편집을 어떻게 처리하는지는 공식 문서에서 찾지 못함. Field Maps에서는 지도 목록에 영역별 로컬 편집 수를 표시한다. ([Sync](https://doc.esri.com/en/arcgis-field-maps/latest/use-maps/sync.html?pivots=mobile-android)) | 공식 문서에서 찾지 못함 | Field Maps 문서에서는 동기화 문제가 생기면 로그를 켜서 관리자에게 공유하라고 안내한다. 자동 폐기 규칙은 공식 문서에서 찾지 못함. ([Sync](https://doc.esri.com/en/arcgis-field-maps/latest/use-maps/sync.html?pivots=mobile-android)) |
| **ArcGIS QuickCapture** | 로그아웃 시 처리는 공식 문서에서 찾지 못함. 미전송 기록은 자동이나 수동으로 올라갈 때까지 기기에 남는다. | 이메일로 보내는 실패 기록 DB의 파일 이름에는 Project 이름, 사용자 이름과 시각이 들어간다. | 전송에 실패하면 QuickCapture에서는 지도 버튼에 경고를 표시한다. 사용자가 Try again을 고르면 GlobalID로 중복을 확인한 뒤 다시 보낸다. 사용자가 Send via email을 고르면 실패 기록 DB를 관리자에게 보낸다. 이때 기기의 기록은 지우지 않는다. 관리자는 SQLite의 상태 값을 `-1`(오류)에서 `1`(전송 대기)로 바꿔 다시 보낼 수 있게 한다. 기록은 사용자가 Discard를 직접 고를 때만 지워진다. 지운 기록은 되돌릴 수 없다. QuickCapture 문서에서는 Discard 전에 먼저 이메일로 보내라고 권한다. ([Troubleshoot capture](https://doc.esri.com/en/arcgis-quickcapture/latest/capture/troubleshoot-capture.html)) |
| **Fulcrum** | 로그아웃과 사용자 전환 시 처리는 공식 문서에서 찾지 못함. 기록 캐시를 지워도 서버에 동기화하지 않은 변경은 기기에 남는다. ([Clearing records cache](https://help.fulcrumapp.com/en/articles/77167-clearing-records-cache-stored-on-the-mobile-device)) | 공식 문서에서 찾지 못함 | Fulcrum iOS 앱에서는 사용자가 지원팀 진단용으로 DB와 진단 로그를 내보낼 수 있다. Fulcrum 문서에서는 이 기능을 미동기 기록 복구 절차로 설명하지 않는다. ([Export backup database](https://help.fulcrumapp.com/en/articles/16397199-exporting-a-backup-database-and-diagnostic-log-on-ios)) |

## 공유 기기 운영체제와 MDM

| 제품 | 로그아웃과 앱 데이터 | 원래 사용자 데이터 업로드 | 토큰 |
| --- | --- | --- | --- |
| **Apple Shared iPad** | Apple Shared iPad에서는 사용자마다 별도 저장 공간(volume)을 둔다. 사용자가 로그아웃해도 백그라운드 동기화로 iCloud에 변경이 올라간다. 그동안 그 사용자의 volume은 유지된다. 새 사용자용 저장 공간이 부족하고, 기존 계정 중 동기화를 끝내 제거할 수 있는 계정도 없으면 새 사용자의 로그인이 실패할 수 있다. 관리자가 계정을 강제로 지우면 데이터를 잃을 수 있다. ([Apple 플랫폼 보안](https://support.apple.com/guide/security/secd99f373ef/web)) | 앱에서 적절한 백그라운드 설정의 NSURLSession으로 올리면 사용자가 로그아웃한 뒤에도 업로드가 이어진다. CloudKit은 앱이 끝나거나 사용자가 로그아웃해도 커밋한 데이터가 도착하도록 설계됐다. Apple 문서에서는 데이터가 생기는 대로 동기화하라고 안내한다. 같은 문서에서 큰 묶음을 오래 들고 있으면 드물게 데이터를 잃을 수 있다고 경고한다. ([Shared iPad 앱 최적화](https://developer.apple.com/education/shared-ipad)) | Apple 문서에서는 자격 증명을 Keychain에 저장하라고 안내한다. ([Shared iPad 앱 최적화](https://developer.apple.com/education/shared-ipad)) |
| **Microsoft Entra 공유 기기 모드** (Intune 등 MDM과 함께 사용) | 전역 로그아웃을 하면 앱과 기기에서 계정과 캐시된 토큰이 지워진다. 앱 데이터는 지워지지 않는다. 그래서 앱에서 앱 데이터를 직접 지워야 한다. 앱에서는 쓰일 때마다 사용자 상태를 확인한다. 사용자가 바뀌었으면 앱에서는 이전 사용자의 데이터와 화면의 캐시 데이터를 지운다. Microsoft 문서에서는 계정 변경 broadcast를 받으면 진행 중인 작업을 멈추고 정리하라고 권한다. ([Android 공유 기기 모드](https://learn.microsoft.com/en-us/entra/identity-platform/msal-android-shared-devices), [개요](https://learn.microsoft.com/en-us/entra/identity-platform/msal-shared-devices)) | 미전송 데이터를 로그아웃 뒤 어떻게 보낼지는 공식 문서에서 찾지 못함 | 로그아웃하면 토큰이 지워지므로 이전 사용자 토큰으로 백그라운드 전송을 이어 가는 경로는 없다(문서의 로그아웃 동작에서 한 추론). |
| **Zebra Identity Guardian** | Clear Application Data 기능을 쓰면 다음 사용자가 이전 사용자 정보를 보지 못하도록 업무 앱의 저장 데이터가 지워진다. ([1.7 About](https://techdocs.zebra.com/identityguardian/1-7/about)) Workspace ONE 설정 예시에서는 로그아웃할 때 필요한 데이터가 지워지지 않도록 제외 목록을 두라고 안내한다. ([Setup](https://techdocs.zebra.com/identityguardian/latest/setup/)) | 미전송 데이터 처리는 공식 문서에서 찾지 못함 | 공식 문서에서 찾지 못함 |

## 현장 서비스

| 제품 | 로그아웃, 재인증과 미동기 변경 | 보내지 못한 변경 |
| --- | --- | --- |
| **Dynamics 365 Field Service / Power Apps** | Power Apps에서는 오프라인 쓰기를 로컬 대기열에 둔다. 연결되면 대기열의 쓰기를 보낸다. 데이터 보존 기간은 정해져 있지 않다. 다만 사용자가 동기화가 끝나기 전에 로그아웃하면 데이터가 보존되지 않는다. ([How mobile offline works](https://learn.microsoft.com/en-us/power-apps/mobile/mobile-offline-works-overview)) 앱을 초기화하면 기기의 오프라인 DB가 지워진다. ([Field Service offline sync](https://learn.microsoft.com/en-us/dynamics365/field-service/mobile/offline-data-sync)) 동기화 아이콘에는 올릴 변경이 남았는지와 오류가 표시된다. ([Offline sync icon](https://learn.microsoft.com/en-us/power-apps/mobile/offline-sync-icon)) | 충돌 검사를 켜면 서버 쪽 변경이 우선한다. 현장 사용자에게는 충돌 오류가 보인다. 관리자는 Sync Errors에서 지난 오류를 본다. ([Field Service offline sync](https://learn.microsoft.com/en-us/dynamics365/field-service/mobile/offline-data-sync)) 오류가 난 변경을 재시도하거나 버리는 기준은 공식 문서에서 찾지 못함. |
| **Salesforce Field Service** | Salesforce 직원의 공식 커뮤니티 공지에 따르면, 앱에서 다시 인증하면 기기에 남은 미동기 데이터가 지워지고 복구할 수 없다. 30일 동안 쓰지 않은 토큰은 만료된다. 그래서 30일 넘게 연결하지 않은 기기에서는 다시 로그인해야 한다. 이때 미동기 데이터를 잃는다. Salesforce에서는 2027-01-18부터 refresh token rotation을 강제한다. ([공지](https://trailhead.salesforce.com/trailblazer-community/feed/0D5KX00000jAULK0A4)) | 공식 문서에서 찾지 못함 |
| **ServiceNow Mobile** | 오프라인 동작은 Outbox에 쌓인다. 캐시는 기본 48시간 뒤 만료된다. 48시간 안에 동기화하지 않은 변경은 Outbox로 옮겨져 다음 로그아웃 때 동기화할 수 있다. ([Offline mode](https://www.servicenow.com/docs/r/mobile/mobile-offline-mode.html)) | 문제가 있는 변경은 해결될 때까지 Outbox에 남는다. 사용자는 Outbox에서 Resolve로 고치거나 Delete로 지운다. ([Offline mode for mobile](https://www.servicenow.com/docs/r/mobile/offline-end-user.html)) |

## POS의 직원 전환과 오프라인 결제

| 제품 | 로그아웃과 대기 중인 오프라인 결제 | 기한과 이후 처리 |
| --- | --- | --- |
| **Square** | Square 도움말에서는 대기 중인 오프라인 결제가 있으면 로그아웃, 모드 전환, 위치 전환과 앱 삭제를 하지 말라고 안내한다. 이를 어기면 결제가 영구히 사라지고 대금을 받지 못한다. 직원 전환은 공식 문서에서 찾지 못함. | 오프라인 결제는 연결해 올리지 않으면 72시간 뒤 만료된다. Square 도움말에서는 24시간 안에 올리라고 권한다. 만료된 결제는 되찾거나 다시 처리할 수 없다. ([Offline payments](https://squareup.com/help/us/en/article/7777-process-card-payments-with-offline-mode)) |
| **Shopify POS** | 오프라인 중에 로그아웃되면 다시 로그인하고 카드 리더를 연결해야 오프라인 결제를 처리할 수 있다. 직원 전환은 공식 문서에서 찾지 못함. | Shopify 도움말에서는 가능하면 24시간 안에 연결하라고 권한다. 연결 뒤 거절된 결제는 주문에 받아 둔 연락처로 고객에게 다시 받으라고 안내한다. 만료 규칙은 공식 문서에서 찾지 못함. ([Offline payments](https://help.shopify.com/en/manual/sell-in-person/shopify-pos/selling-offline/offline-payments)) |
| **Toast** | 로그아웃, 직원 전환, 기기 초기화는 공식 문서에서 찾지 못함. 표준 오프라인 모드의 주문은 각 기기에 저장된다. ([Offline mode](https://doc.toasttab.com/doc/platformguide/adminOfflineModeOverview.html)) | Toast 문서에서는 오프라인 결제를 받은 태블릿을 3일 안에 연결하라고 강하게 권한다. 거절, 만료, 분쟁 결제의 책임은 가맹점에 있다. ([Offline card payments](https://doc.toasttab.com/doc/platformguide/adminOfflineCCPayments.html)) |

## 다중 사용자 동기화 엔진

| 제품 | 사용자 전환과 대기 중인 쓰기 | 서버 거절과 폐기 |
| --- | --- | --- |
| **Firestore** (Android SDK) | 쓰기 대기열(mutation queue)은 사용자 uid마다 따로 저장된다. 사용자가 바뀌면 Firestore SDK에서는 새 사용자의 대기열로 바꿔 끼운다. ([SQLiteMutationQueue](https://github.com/firebase/firebase-android-sdk/blob/71b1cea86d75cb23fd0b0e6805bfb41f10d07cee/firebase-firestore/src/main/java/com/google/firebase/firestore/local/SQLiteMutationQueue.java), [LocalStore.handleUserChange](https://github.com/firebase/firebase-android-sdk/blob/71b1cea86d75cb23fd0b0e6805bfb41f10d07cee/firebase-firestore/src/main/java/com/google/firebase/firestore/local/LocalStore.java)) 이전 사용자의 쓰기는 그 사용자가 다시 로그인할 때까지 기기에 남는다(소스 구조에서 한 추론). 이전 사용자의 쓰기는 다른 사용자의 인증으로 보내지 않는다(소스 구조에서 한 추론). | 서버에서 거절된 쓰기 묶음은 대기열에서 제거된다. 그다음 로컬 문서가 다시 계산된다(`rejectBatch`). 거절된 쓰기를 따로 보관하는 경로는 소스에서 찾지 못했다. ([LocalStore](https://github.com/firebase/firebase-android-sdk/blob/71b1cea86d75cb23fd0b0e6805bfb41f10d07cee/firebase-firestore/src/main/java/com/google/firebase/firestore/local/LocalStore.java)) |
| **Atlas Device Sync / Realm** (지원 중단 공지됨) | 여러 사용자가 동시에 로그인할 수 있다. 다만 요청은 활성 사용자 하나의 이름으로 나간다. 로그아웃한 사용자의 정보는 기기에 남는다. 그 사용자를 제거(remove)하면 기기에서 그 정보가 모두 지워진다. ([다중 사용자](https://www.mongodb.com/docs/atlas/device-sdks/sdk/kotlin/users/multi-user-applications.md)) 로그아웃하면 자격 증명이 지워진다. 동기화도 즉시 멈춘다. 로그아웃 전에 끝나지 않은 쓰기는 오류가 나서 그 데이터를 잃을 수 있다. 그래서 Realm 문서에서는 로컬 변경이 다 올라간 뒤에 로그아웃하라고 안내한다. ([사용자 인증](https://www.mongodb.com/docs/atlas/device-sdks/sdk/kotlin/users/authenticate-users.md)) | refresh token이 만료되면 사용자가 다시 로그인할 때까지 동기화할 수 없다. ([사용자 인증](https://www.mongodb.com/docs/atlas/device-sdks/sdk/kotlin/users/authenticate-users.md)) |
| **AWS Amplify DataStore** (Gen 1, 유지 보수 모드) | Amplify 문서에서는 기기를 여러 사용자가 함께 쓸 수 있으면 로그인이나 로그아웃할 때 `DataStore.clear()`를 호출해 사용자별 데이터를 지우라고 권한다. ([Additional methods](https://docs.amplify.aws/gen1/android/build-a-backend/more-features/datastore/additional-methods/)) `clear()`를 호출하면 outbound queue에서 동기화를 기다리던 항목을 잃는다. ([API 참조](https://pub.dev/documentation/amplify_core/0.1.4/amplify_core/DataStoreCategory/clear.html)) | 공식 문서에서 찾지 못함 |

## 토큰 보관과 철회 지침

| 출처 | 내용 |
| --- | --- |
| **OpenID Connect Core 11절** | `offline_access` scope로는 사용자가 없을 때(로그인하지 않았을 때)도 access token을 받을 수 있는 refresh token을 요청한다. OIDC Core에서는 OP가 offline access를 허용하는 refresh token을 내주기 전에 항상 동의를 받아야 한다고 정한다. ([OIDC Core](https://openid.net/specs/openid-connect-core-1_0.html#OfflineAccess)) |
| **Keycloak 오프라인 접근** | Keycloak 문서에 따르면 클라이언트에서는 offline token을 저장해 둘 수 있다. 저장한 offline token은 사용자가 로그아웃한 뒤에도 쓸 수 있다. offline token은 SSO Session Idle과 Max의 영향을 받지 않는다. 그래서 로그아웃 뒤에도 유효하다. 기본적으로 30일(Offline Session Idle) 안에 한 번은 refresh해야 한다. Offline Session Max Limited를 켜면 refresh해도 60일 뒤 만료된다. Revoke Refresh Token을 켜면 offline token은 한 번만 쓸 수 있다. 이때 클라이언트에서는 응답의 새 토큰을 저장해야 한다. 사용자는 계정 콘솔에서, 관리자는 Consents 탭에서 offline token을 철회할 수 있다. 토큰을 받으려면 사용자에게 `offline_access` realm 역할이 있어야 한다. ([offline.adoc](https://github.com/keycloak/keycloak/blob/c965c68bc2d20e78ee579f9fed84ddb0f2ab7770/docs/documentation/server_admin/topics/sessions/offline.adoc)) |
| **Keycloak 철회 endpoint** | Keycloak 문서에서는 `/realms/{realm}/protocol/openid-connect/revoke`로 RFC 7009에 따라 refresh token과 access token을 철회한다고 설명한다. 같은 문서에는 refresh token을 철회하면 그 client에 대한 사용자 동의도 함께 철회된다고 적혀 있다. ([available-endpoints.adoc](https://github.com/keycloak/keycloak/blob/c965c68bc2d20e78ee579f9fed84ddb0f2ab7770/docs/guides/securing-apps/partials/oidc/available-endpoints.adoc)) Keycloak 26.6.1 소스의 철회 처리에서는 토큰의 세션 ID로 찾은 offline user session과 online user session에서 그 client의 client session만 지운다. 남은 client session이 없으면 그 user session도 지운다. 사용자 동의를 지우는 코드는 이 경로에 없다. 사용자 동의 철회는 별도 관리 API(`DELETE users/{user-id}/consents/{client}`)의 동작이다. ([TokenRevocationEndpoint.java](https://github.com/keycloak/keycloak/blob/26.6.1/services/src/main/java/org/keycloak/protocol/oidc/endpoints/TokenRevocationEndpoint.java#L248-L269), [UserSessionManager.java](https://github.com/keycloak/keycloak/blob/26.6.1/services/src/main/java/org/keycloak/services/managers/UserSessionManager.java#L111-L173), [동의 철회 API](https://github.com/keycloak/keycloak/blob/26.6.1/services/src/main/java/org/keycloak/services/resources/admin/UserResource.java#L636-L664)) |
| **RFC 9700 4.14.2** | RFC 9700에서는 public client의 refresh token에 sender constraint 또는 rotation을 요구한다. 비밀번호 변경이나 로그아웃 같은 보안 사건이 생기면 인가 서버에서 refresh token을 자동 철회해도 된다(MAY). 클라이언트가 한동안 쓰지 않은 refresh token은 만료돼야 한다(SHOULD). ([RFC 9700](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14.2)) |
| **Entra 공유 기기 모드** | 전역 로그아웃을 하면 계정과 캐시된 토큰이 앱과 기기에서 지워진다. ([Android 공유 기기 모드](https://learn.microsoft.com/en-us/entra/identity-platform/msal-android-shared-devices)) |
| **Realm** | 사용자가 로그아웃하면 Realm SDK에서는 토큰을 지운다. ([사용자 인증](https://www.mongodb.com/docs/atlas/device-sdks/sdk/kotlin/users/authenticate-users.md)) |
| **Apple Shared iPad** | Apple 문서에서는 자격 증명을 Keychain에 두라고 안내한다. 로그아웃 뒤 업로드는 백그라운드 NSURLSession이나 CloudKit으로 이어 갈 수 있다. ([Shared iPad 앱 최적화](https://developer.apple.com/education/shared-ipad)) |

로그아웃한 사용자의 refresh token이나 offline token을 앱에 따로 보관해 그 사용자 이름으로 백그라운드 전송을 하라는 지침은 조사한 제품 문서에서 찾지 못했다. Entra 공유 기기 모드와 Realm에서는 반대로 로그아웃할 때 토큰을 지운다. Keycloak 문서에서는 사용자가 없을 때 하는 작업(예: 정기 백업)의 수단으로 offline token을 설명한다.

## 범주를 가로지르는 패턴

아래는 위 사실을 묶은 분류다. 한 제품이 여러 패턴에 들어갈 수 있다.

| 패턴 | 확인한 제품 | 대가 |
| --- | --- | --- |
| **P1. 로그아웃 전에 보내도록 안내하고 손실을 경고한다** | Square, Power Apps, Realm, Amplify, Salesforce, CommCare 문제 해결 문서 | 사용자가 안내를 지키지 않으면 데이터를 잃는다. 로그아웃 자체를 막는 사례는 공식 문서에서 찾지 못했다. |
| **P2. 사용자별 저장소를 나누고 그 사용자가 다시 로그인할 때 보낸다** | Firestore, CommCare, Realm(로그아웃 사용자 정보 보존) | 원래 사용자가 이 기기에 다시 로그인하지 않으면 기록이 기기에 계속 남는다. 그 사이 기록을 볼 수 있는 경로가 없다. |
| **P3. 로그아웃 뒤에도 원래 사용자 이름으로 백그라운드 업로드를 이어 간다** | Apple Shared iPad(운영체제 수준) | 새 사용자용 저장 공간이 부족하고, 동기화를 끝내 제거할 수 있는 기존 계정도 없으면 다음 사용자의 로그인이 실패할 수 있다. 강제로 지우면 데이터를 잃는다. |
| **P4. 기기나 역할 자격 증명으로 보내고 사람은 메타데이터로 남긴다** | ODK Central App User(기기나 역할 단위 선택 시), ODK Collect의 사용자 이름 메타데이터 | 서버에서는 인증으로 작성자를 확인하지 못한다. 그래서 기기에 적힌 값을 믿어야 한다. ODK 문서에도 사람마다 App User를 두는 방식이 이상적이라고 적혀 있다. |
| **P5. 실패 기록은 자동 폐기하지 않고 격리해 사람이 해결한다** | CommCare 격리, QuickCapture, ServiceNow Outbox, ODK Collect 전송 실패 상태 | 해결하지 않은 기록이 기기에 쌓인다. 내보내기, 사유 표시, 서버 보고 같은 관리 경로가 필요하다. |
| **P6. 기한이 지나면 회수할 수 없다** | Square 72시간, Salesforce 30일 미사용 토큰 만료 뒤 재인증 | 결제 승인 기한이나 보안 정책처럼 기한이 바깥에서 정해진 경우다. 제품사 문서에서도 손실을 명시적으로 경고한다. |
| **P7. 공유 기기 모드에서는 로그아웃할 때 앱 데이터 삭제를 앱에 맡긴다** | Entra 공유 기기 모드, Zebra Clear Application Data, Amplify `clear()` 권고 | 이 방식으로 개인정보 노출은 막을 수 있다. 다만 미전송 데이터 보존과 부딪힌다. Apple 외에는 두 요구를 함께 푸는 방법을 공식 문서에서 찾지 못했다. |

## 수리맵 의사결정에 적용

토큰 교체와 로그아웃 화면 안내는 ADR-0039에서 이 절의 추론과 다르게 정했다.

이 절의 **사실**은 이 조사를 요청할 때 전달받은 수리맵 현재 동작과 [PRD](../prd.md#오프라인-사용동기화)의 요구다. **추론**은 위 패턴을 수리맵에 대입한 작업자의 판단이다. 추론은 결정이 아니다. 업무폰은 MDM으로 관리한다는 전제가 있다.

**사실**

- PRD에서는 통신 단절과 재전송으로 기록이 유실되거나 중복 생성되지 않아야 한다고 요구한다. 미전송 기록은 서버 반영을 확인하기 전에 지우지 않는다.
- 서버에는 요청 JWT의 계정이 작성자로 저장된다. 지금 앱에서는 A가 로그아웃하고 B가 로그인하면 A의 미전송 기록을 B의 토큰으로 보낸다. 그래서 작성자가 B로 잘못 저장된다.
- 지금 앱에서는 서버 거절, 재시도 가능한 실패 8회, 첫 시도 후 24시간 경과, 사건 종료 후 생성 중 하나에 해당하면 기록을 `FAILED_FINAL`로 바꾼다. 이 상태에서는 자동 재시도와 수동 재시도가 모두 없다.

### (a) `FAILED_FINAL` 기록만 남았을 때 A의 토큰을 철회할지

| 패턴 | 추론 |
| --- | --- |
| P2 | A의 토큰을 철회해도 A의 기록은 지우지 않는다. A의 기록은 A 이름으로 묶어 둔다. A가 이 업무폰에 다시 로그인하면 A의 토큰으로 보낼 수 있다. 토큰 철회와 기록 정리를 별개의 결정으로 다뤄야 한다. |
| P3 | 백그라운드 전송용 토큰은 앞으로 보낼 기록이 있을 때만 보관할 이유가 있다. `FAILED_FINAL`은 현재 자동 재시도 대상이 아니다. 그래서 지금 규칙대로라면 토큰을 남겨도 쓰이지 않는다. |
| P5 | `FAILED_FINAL`을 사람이 해결할 수 있는 격리 상태로 바꾸면, 해결한 뒤 누구의 토큰으로 다시 보낼지 정해야 한다. 이때 A의 토큰을 이미 철회했다면 A가 다시 로그인해야 한다. 아니면 서버에 작성자를 바꾸지 않는 별도 경로가 필요하다. 이 결정에 따라 토큰 철회 시점이 정해진다. |
| 토큰 지침 | Keycloak에서는 offline token을 30일 동안 쓰지 않으면 만료시킨다(기본값). 앱에서는 철회 endpoint로 offline token을 철회한다. 철회하면 그 토큰의 세션에서 이 앱의 세션만 끝난다. RFC 9700에서는 public client의 refresh token에 sender constraint 또는 rotation을 요구한다. rotation을 쓰는 경우에는 갱신 응답의 새 refresh token을 저장해야 한다. |

### (b) 24시간과 8회 기준의 `FAILED_FINAL` 규칙이 유실 금지 요구에 맞는지

| 패턴 | 추론 |
| --- | --- |
| P5 | 조사한 현장 수집 제품의 공식 문서와 소스 코드에서는 횟수나 경과 시간만으로 재시도를 영구히 막는 규칙을 찾지 못했다. ODK Collect에서는 실패한 양식도 계속 자동 전송 대상에 넣는다. CommCare에서는 서버에서 처리하지 못한 양식만 격리한다. 격리한 양식도 사용자가 되돌릴 수 있다. 수리맵의 `FAILED_FINAL` 기록은 지워지지 않는다. 하지만 서버에 닿을 경로가 없으므로 결과적으로는 서버에 없는 기록이 된다. 그래서 PRD의 유실 금지 요구와 부딪힐 수 있다. |
| P5 | 실패 원인이 다르면 처리도 다르다. 통신 장애처럼 재시도 가능한 실패는 상한을 두는 대신 간격을 늘려 계속 시도하는 쪽이 비교 사례와 맞다. 서버 거절은 다시 보내도 같은 결과이므로 격리하고 사유를 보여 줘야 한다. 그다음 사람이 해결하거나 내보내는 경로를 두는 쪽이 비교 사례와 맞다(CommCare 되돌리기, QuickCapture 다시 시도와 이메일 내보내기, ServiceNow Resolve). |
| P6 | 시간 기한으로 회수 불가를 둔 사례는 결제 승인이나 보안 정책처럼 기한이 바깥에서 정해진 경우였다. 수리맵의 24시간이 그런 외부 기한에서 나왔는지는 이 조사로 확인하지 못했다. 근거가 없다면 24시간은 재시도를 멈추는 기준보다 사용자와 지휘관에게 경고를 보이는 기준(CommCare의 5건, 5일 표시와 같은 쓰임)으로 바꾸는 방안을 검토할 수 있다. |
| 사건 종료 | 사건 종료 후 생성한 기록은 서버에서 거절될 것이므로 재시도 대상이 아니다. 하지만 원본은 기기에 있으므로, 파기 규칙과 별도로 내보내기나 관리자 확인 경로를 둘지 정해야 한다. 이 결정은 [데이터 보존과 파기 기능 정의 문서](../features/data-retention.md)와 [오프라인 동기화 기능 정의 문서](../features/offline-sync.md)의 기존 요구와 대조해야 한다. |

### (c) 로그아웃 UX

| 패턴 | 추론 |
| --- | --- |
| P1 | 로그아웃 화면에 A의 미전송 기록 수를 보이고, 먼저 보내기를 제공하는 쪽이 가장 흔한 방식이다. Power Apps의 대기 변경 아이콘, Fulcrum의 미동기 기록 배지, CommCare의 Unsent Forms 수도 같은 쓰임이다. |
| P3 | Apple Shared iPad에서는 새 사용자용 저장 공간이 부족하고 제거할 수 있는 계정도 없으면 다음 사람의 로그인이 실패할 수 있다. 수리맵에서 다음 사람의 로그인을 막으면 교대 현장에서 B의 업무가 멈출 수 있다. 조사한 앱의 공식 문서에서는 로그아웃이나 다음 로그인을 막는 사례를 찾지 못했다. 막기보다는 경고를 보이고 백그라운드 전송(P3)이나 사용자별 보존(P2)으로 처리하는 쪽이 비교 사례와 맞다. |
| P2, P7 | 경고 문구는 "로그아웃하면 기록이 사라진다"가 아니라 "A의 기록은 이 업무폰에 남아 A 이름으로 보낸다"처럼 실제 동작과 맞아야 한다. 기록을 지우지 않는 구조라면 삭제를 경고할 이유가 없다. B의 화면에는 A의 기록 내용을 보이지 않는다(Entra 공유 기기 모드 지침). A의 기록이 남아 있다는 사실과 건수를 보일지는 지휘 요구와 개인정보 범위를 보고 정한다. |
| P5 | `FAILED_FINAL`이나 격리 기록이 남았다면 로그아웃 화면에서 미전송 기록과 구분해 보여야 한다. 앞의 것은 자동으로 해결되지 않는다는 점을 사람이 알아야 하기 때문이다. |

## 한계

- 경찰, 공공 안전, 의료 제품(Axon, Mark43, Motorola Solutions, Tyler, Epic)은 공개 문서가 없어 비교하지 못했다. 수리맵과 가장 가까운 영역의 실제 동작은 이 조사에 없다.
- Survey123, Field Maps, QuickCapture, Fulcrum, ODK, CommCare의 로그아웃과 사용자 전환 동작은 공식 문서에 없다. 커뮤니티 포럼에는 사용자 경험담이 있지만 근거로 쓰지 않았다.
- CommCare와 Firestore의 사용자별 저장 구조는 소스 코드에서 확인했다. "원래 사용자가 다시 로그인할 때 보낸다"는 동작은 그 구조에서 한 추론이다. 이 동작은 실행해서 확인하지 않았다.
- 소스 코드는 위 커밋 기준이며 이후 바뀔 수 있다.
- Salesforce의 근거는 Salesforce 직원이 공식 커뮤니티에 쓴 공지다. 연결된 릴리스 노트 본문은 확인하지 못했다.
- Atlas Device Sync는 지원 중단이 공지됐다. Amplify Gen 1은 유지 보수 모드다. 설계 사례로는 참고할 수 있지만 현재 권장 제품은 아니다.
- 이 문서에서는 수리맵의 결정을 정하지 않는다. 토큰 보관, `FAILED_FINAL` 규칙, 로그아웃 UX는 사용자와 합의한 뒤 [오프라인 동기화 기능 정의 문서](../features/offline-sync.md)와 [인증과 접근 권한 기능 정의 문서](../features/authentication.md)에 반영한다.
