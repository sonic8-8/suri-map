# Knox·MDM 도입 검토안

상태: 보류. 과거 팀 시연에서 폴리폰 Android 앱을 관리 단말 환경으로 검증하려던 제안이다. 도입·실행 승인이 아니며, 단말 초기화·외부 계정 등록·라이선스 구매는 별도 합의가 필요하다.

## 검토 목적과 역할

- 앱은 관리자가 주입한 설정(managed configuration)을 읽는다. 단말의 권한·네트워크·키오스크 정책을 관리하는 MDM agent 역할은 맡지 않는다.
- 설정 주입을 확인하는 Test DPC 시험과 Samsung Knox 정책을 적용하는 시험을 구분한다. 전자를 통과해도 Knox 연동까지 검증한 것은 아니다.
- `mock-112`는 사건·실종자·배정의 원천 시스템 mock으로 유지한다. 단말 관리 기능을 추가하지 않는다.
- Knox 검토는 일반 앱 개발과 분리한다. 자체 MDM/DPC 구현, 앱의 Knox SDK 직접 정책 제어, 경찰 내부망·PS-LTE 구축은 이 제안의 범위가 아니다. Docker 기반 시험 환경도 실제 관리 단말 검증을 대신하지 않는다.

## 코드에서 확인한 범위

2026-09-22 파일을 확인한 결과다. 실기기 등록·정책 적용·앱 연결 시험을 수행한 것은 아니다.

| 확인한 파일 | 확인한 내용 |
|---|---|
| [app_restrictions.xml](../../android/app/src/main/res/xml/app_restrictions.xml) | `police_phone_id`·`api_base_url`·`tile_base_url`·`object_storage_base_url`·`allowed_hosts` 설정 키가 선언돼 있다. |
| [AndroidManifest.xml](../../android/app/src/main/AndroidManifest.xml) | `android.content.APP_RESTRICTIONS`가 위 XML을 가리킨다. |

따라서 이 파일들을 새로 추가하는 옛 작업 목록은 사용하지 않는다. 키 선언·Manifest 연결만으로 모든 설정의 앱 적용이나 실제 Knox 연결이 확인된 것은 아니다.

## 재개할 때 정할 사항

| 선택할 내용 | 판단에 필요한 확인 |
|---|---|
| 검증 범위 | 앱 설정 주입까지만 확인할지, Knox 관리 단말 연결·정책 적용까지 확인할지 정한다. KSP 정책 검증은 별도 범위다. |
| 단말·계정·비용 | 사용할 단말의 관리 상태와 초기화 가능 여부를 확인한다. trial·라이선스 조건은 공식 자료에서 다시 확인하고 등록·구매 전 합의한다. |
| 앱·서버 연결 | 실제로 읽는 설정 키와 schema, 개발용 override와 관리 단말 동작, 앱 배포 방식·서버 접근 조건을 대조한다. |
| 완료 근거 | 단말·OS·관리 도구·시험 조건, 설정 누락·잘못된 업무폰·서버 접속 실패, 성공·실패 결과와 미검증 범위를 남긴다. 기록 위치·공유 범위는 실행 전에 정한다. |

## 과거 검토 기록

[옛 Phase 0~6 작업 목록](https://github.com/sonic8-8/suri-map/blob/403b383dc2693fd15d1616ee7206e6cb0e420df5/docs/tasks/knox-tasks.md)에 당시 단말 가정·trial 조건·정책 후보·검증 순서를 보존한다. 관련 Jira는 `S14P31C106-245`였다. 이 기록의 수치·미완료 체크박스를 현재 환경이나 작업 순서로 사용하지 않는다.

## 재개할 때 확인할 공식 자료

아래는 과거 검토에 사용한 출처다. 이번 문서 정리에서는 현재 상품·등록 조건을 다시 조사하지 않았다.

- [Samsung Knox Manage FAQ](https://docs.samsungknox.com/admin/knox-manage/original-console/faq/)
- [Knox 라이선스 관리](https://docs.samsungknox.com/admin/knox-admin-portal/how-to-guides/manage-knox-licenses/)
- [Knox 단말 등록](https://docs.samsungknox.com/admin/knox-mobile-enrollment/how-to-guides/enroll-devices/complete-device-enrollment/)
- [Samsung managed configurations](https://docs.samsungknox.com/dev/managed-configurations/)
- [Knox Service Plugin 설정](https://docs.samsungknox.com/dev/managed-configurations/knox-service-plugin/)
- [Android managed configurations](https://developer.android.com/work/managed-configurations)
