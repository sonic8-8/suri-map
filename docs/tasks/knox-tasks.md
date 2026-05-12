# Knox / MDM Managed Device Tasks

상태: 초안. 이 문서는 폴리폰 Android 앱을 현업형 관리 단말 흐름으로 검증하기 위한 작업 체크리스트다. `mock-112`는 사건 원천 시스템 mock으로 유지하고, Knox/MDM 검증은 별도 provisioning / device management track으로 분리한다.

관련 Jira: `S14P31C106-245`

## 판단 요약

- 실제 Samsung Knox 연결은 SSAFY 시연 설득력 측면에서 가장 강한 증거다.
- 하지만 Android UI/앱 기능 개발의 blocker로 두면 리스크가 크다. Knox PoC는 별도 검증 트랙으로 병렬 진행한다.
- Knox/MDM은 Docker container로 대체할 수 없다. Docker는 backend fixture, DB, object storage, tile fixture에만 사용한다.
- 앱은 MDM agent가 아니다. Android 앱은 managed configuration을 읽고, 단말 정책은 Knox Manage / Knox Suite / 기관 MDM / Knox Service Plugin이 담당한다.
- `mock-112`는 112/실종프로파일링 원천 사건 mock이다. 여기에 Knox/MDM 기능을 추가하지 않는다.

## 전제

- 현재 테스트 후보 단말은 Samsung Galaxy S22 계열 `SM-S901N`이다. Knox 관련 시스템 패키지와 속성은 확인됐지만, 현재 상태는 Device Owner가 아니다.
- fully managed / Device Owner 등록 검증은 보통 factory reset이 필요하다.
- Knox Manage / Knox Suite trial은 공식 문서 기준 무료 trial이 가능하지만, 계정 review, tenant 생성, 단말 등록, 앱 배포, profile 설정이 필요하다.
- 상용 운영은 reseller를 통한 유료 license 구매가 필요하다.
- 경찰 내부망은 현재 직접 구축 대상이 아니다. 시연/개발에서는 노트북 또는 사내 LAN에서 접근 가능한 fixture backend로 내부망을 시뮬레이션한다.

## Phase 0. 범위 고정

- [ ] Knox/MDM 검증을 Android UI 구현과 별도 track으로 분리한다.
- [ ] `mock-112`는 사건/실종자/배정 원천 시스템 mock으로만 유지한다.
- [ ] provisioning lab 역할을 별도 문서 또는 도구로 정의한다.
  - 폴리폰 fixture 선택
  - managed configuration 값 생성
  - Test DPC / Knox Manage 입력값 정리
  - ADB evidence 수집 명령 정리
- [ ] Knox SDK 직접 연동은 v2 또는 별도 PoC로 보류한다.
  - 앱이 직접 firewall, kiosk, permission policy를 제어하지 않는다.
  - 단말 정책은 MDM/Knox Service Plugin으로 검증한다.
- [ ] Jira/MR 설명에는 "Knox 실연결은 최종 증거, Test DPC는 선검증"이라고 구분한다.

## Phase 1. Android managed configuration 준비

- [ ] `android/app/src/main/res/xml/app_restrictions.xml`을 추가한다.
  - `police_phone_id`
  - `api_base_url`
  - `tile_base_url`
  - `object_storage_base_url`
  - `allowed_hosts`
- [ ] `AndroidManifest.xml`의 `<application>`에 managed configuration meta-data를 연결한다.
  - `android:name="android.content.APP_RESTRICTIONS"`
  - `android:resource="@xml/app_restrictions"`
- [ ] 앱의 실제 read key와 XML schema key를 1:1로 맞춘다.
  - 현재 Android bootstrap 계약 후보: `police_phone_id`, `api_base_url`
- [ ] managed config 누락 시 앱 UI는 기술 용어를 노출하지 않는다.
  - 허용 문구: `관리 폴리폰 확인 필요`, `내부망 연결 필요`, `배정 사건 없음`
  - 금지 문구: `Knox`, `MDM`, `Device Owner`, `RestrictionsManager`
- [ ] debug build에는 개발 override를 둘 수 있으나, release/managed flow와 명확히 분리한다.
- [ ] 검증:
  - `cd android && ./gradlew :app:assembleDebug`
  - managed config reader unit test 또는 bootstrap contract test

## Phase 2. Backend fixture / 내부망 시뮬레이션 준비

- [ ] `docs/spec/fixtures/common-fixtures.json`의 fixture ID를 그대로 사용한다.
- [ ] 최소 fixture backend 흐름을 준비한다.
  - `POST /api/police-phones/{policePhoneId}/heartbeat`
  - `GET /api/incidents`
  - offline package manifest / installation report
  - marker / path / memo write
  - outbox replay
  - DutyShift 인수인계 조회 및 AI 요약 read
- [ ] 노트북 또는 개발 서버에서 폰이 접근 가능한 base URL을 정한다.
  - 같은 Wi-Fi/LAN: `http://<host-ip>:<port>/api`
  - USB reverse는 debug smoke 전용으로만 사용
- [ ] 외부망 차단 시나리오는 실제 Knox policy 전까지 개발망에서 근사한다.
  - DNS/block rule
  - allowed host 외 요청 차단 interceptor
  - 외부 font/icon/network resource 없는 UI fallback
- [ ] `mock-112`와 backend fixture의 책임을 분리한다.
  - `mock-112`: source incident, missing person, incident assignment
  - Suri-Map backend fixture: PolicePhone heartbeat, app auth context, offline package, outbox, marker/path/handover

## Phase 3. Test DPC / Android Enterprise 선검증

- [ ] Test DPC 또는 Android Enterprise 개발 흐름으로 managed configuration 주입을 먼저 검증한다.
- [ ] 이 단계는 Samsung Knox 정책 검증으로 부르지 않는다.
- [ ] 검증 범위:
  - 앱 설정 key 주입
  - `RestrictionsManager.applicationRestrictions` read
  - 앱 시작 -> 폴리폰 확인 -> 사건 목록 진입
  - missing config / invalid phone / unreachable backend 실패 분기
- [ ] evidence:
  - 단말 모델/OS
  - 주입한 managed configuration 값
  - ADB logcat 핵심 구간
  - 화면 캡처
  - backend fixture request log

## Phase 4. Knox Manage / Knox Suite trial PoC

- [ ] Samsung 계정 또는 Samsung account for Business 준비 가능 여부를 확인한다.
- [ ] Knox Manage 또는 Knox Suite trial tenant를 신청한다.
- [ ] trial 조건을 문서에 남긴다.
  - 무료 trial 가능
  - 공식 문서 기준 90일 또는 3개월
  - 최대 30대 device
  - 상용 운영은 reseller를 통한 유료 license 필요
- [ ] 현재 Galaxy S22 test 단말을 factory reset해도 되는지 사용자 확인 후 진행한다.
- [ ] Knox Manage에서 Android Enterprise fully managed 또는 company-owned profile 흐름을 선택한다.
- [ ] 앱 배포 방식을 결정한다.
  - Managed Google Play 등록
  - private app
  - APK 직접 배포가 가능한 profile
- [ ] 앱 managed configuration을 Knox Manage console에서 설정한다.
  - `police_phone_id`
  - `api_base_url`
  - `tile_base_url`
  - `object_storage_base_url`
  - `allowed_hosts`
- [ ] 단말 enrollment를 수행한다.
  - QR enrollment 또는 token enrollment
  - OOBE 흐름은 factory reset 전제
- [ ] 앱 시작부터 사건 진입까지 실기기 증거를 수집한다.
- [ ] 실패 시 fallback plan을 기록한다.
  - trial 승인 실패
  - Managed Google Play 등록 지연
  - 단말 factory reset 불가
  - 내부망 URL 접근 불가

## Phase 5. Knox Service Plugin 정책 PoC

- [ ] Knox Service Plugin(KSP)을 도입할지 별도 판단한다.
- [ ] KSP는 app configuration보다 무겁고, Samsung device policy 검증용임을 문서화한다.
- [ ] 후보 정책:
  - runtime permission 자동 부여
  - 외부망 차단 또는 허용 host 제한
  - kiosk / lock task 유사 제어
  - screen capture 제한
  - app uninstall 방지
- [ ] KSP 적용은 필수 범위가 아니라 가산점 범위로 둔다.
- [ ] KSP 검증 시 evidence:
  - KSP app 설치 여부
  - policy push 성공 표시 또는 log
  - 적용된 정책명
  - 앱 동작 영향

## Phase 6. QA evidence / MR 산출물

- [ ] `docs/screen-design/artifacts/device-qa/` 또는 별도 evidence 경로에 검증 결과를 저장한다.
- [ ] 최소 evidence report 항목:
  - Jira key / branch / commit
  - 단말 모델과 Android version
  - Knox capable 여부
  - Device Owner / Profile Owner 상태
  - MDM 도구(Test DPC, Knox Manage, KSP 등)
  - managed configuration 값
  - backend fixture URL
  - 검증 시나리오
  - 성공/실패 캡처
  - logcat 핵심 구간
  - 남은 risk
- [ ] MR에는 Test DPC와 Knox Manage 결과를 구분해서 적는다.
  - Test DPC: managed config 선검증
  - Knox Manage: 실제 Samsung Knox 관리 단말 PoC
  - KSP: Samsung policy 적용 PoC
- [ ] "문서만으로 Knox/MDM E2E 완료"라고 표현하지 않는다.

## 비대상

- [ ] 자체 MDM/DPC 구현
- [ ] Knox SDK를 Android 앱에 직접 붙여 정책 제어
- [ ] `mock-112`에 단말 관리 기능 추가
- [ ] Docker container로 Knox/MDM 단말 검증을 대체
- [ ] 경찰 내부망/PS-LTE 실제 구축
- [ ] 상용 Knox license 구매

## 완료 기준

- [ ] Android app이 managed configuration schema를 제공한다.
- [ ] debug fixture backend로 폴리폰 시작 흐름을 실기기에서 검증한다.
- [ ] Test DPC 또는 Android Enterprise 개발 흐름으로 managed config 주입을 검증한다.
- [ ] Knox Manage / Knox Suite trial 연결 가능 여부와 실제 수행 결과를 evidence로 남긴다.
- [ ] Knox가 불가능한 경우에도 사유가 남고, Test DPC + fixture backend 검증으로 대체 범위를 명확히 한다.
- [ ] mock-112와 MDM/provisioning 책임이 문서와 MR에서 섞이지 않는다.

## 공식 출처

- Samsung Knox Manage FAQ: https://docs.samsungknox.com/admin/knox-manage/original-console/faq/
- Knox Admin Portal license guide: https://docs.samsungknox.com/admin/knox-admin-portal/how-to-guides/manage-knox-licenses/
- Knox Mobile Enrollment device enrollment: https://docs.samsungknox.com/admin/knox-mobile-enrollment/how-to-guides/enroll-devices/complete-device-enrollment/
- Samsung Knox managed configurations: https://docs.samsungknox.com/dev/managed-configurations/
- Knox Service Plugin managed configurations: https://docs.samsungknox.com/dev/managed-configurations/knox-service-plugin/
- Android managed configurations: https://developer.android.com/work/managed-configurations
