# DB 데이터의 의미와 확인 위치

사건·수색 기록·전송 상태를 왜 나누어 저장하는지 확인할 때 읽는다. 컬럼 전체 목록은 복제하지 않는다. 아래는 2026-09-21의 migration·Mapper·관련 코드 대조 결과이며, 실제 배포 DB를 조회하거나 기능을 실행한 검증은 아니다.

- 타입·nullable·인덱스·FK: [Flyway migration](../../backend/src/main/resources/db/migration/). 최초 생성뿐 아니라 뒤따르는 변경도 확인한다.
- 실제 저장·조회: [MyBatis Mapper SQL](../../backend/src/main/resources/mapper/)과 호출 Service.
- 주요 연결: [관계도](./db-design-erd.md). 업무상 관계와 DB가 강제하는 외래 키는 구분한다.

## 사건·계정·업무폰

| 데이터 | 의미와 구분 |
|---|---|
| `incident` | 원천 112/mock 사건을 수리맵에 반영한 기록. `source_incident_id`는 원천 식별자이고 `id`는 수리맵 내부 식별자다. 둘 다 UUID이지만 같은 역할은 아니다. |
| `missing_person` | 사건 진행에 필요한 실종자 정보. 사건당 최대 한 행이며, 종료 후 제거할 수 있도록 사건 자체와 분리했다. 사진은 `photo_object_key`로 참조한다. |
| `incident_assignment` | 사건에 배정된 계정과 사건 안에서의 역할. 구역 담당 배정과 다르며, 해제 시각으로 유효 배정을 구분한다. |
| `account` | 조작·기록의 계정 식별자. `login_id`는 로그인용 값이고 관계에 사용하는 `id`와 다르다. |
| `police_phone` | 현장 앱을 사용하는 물리 업무폰. 마지막 heartbeat 수신·동기화 시각, 수용한 순번·이벤트 ID를 보관한다. |
| `mock112_webhook_event` | 원천 알림의 중복 처리를 막고 처리 결과를 재사용하는 기록. 사용자의 쓰기 요청을 다루는 `idempotency_record`와 구분한다. |

개인 계정으로 경로를 기록하는 구현과 옛 공유 계정·팀/순찰차 seed를 같은 정책으로 해석하지 않는다. 현재 [PolicePhoneMapper](../../backend/src/main/resources/mapper/policephone/PolicePhoneMapper.xml)는 `police_phone.account_id`로 계정·사건 배정을 조회한다. 계정과 단말을 연결하는 이 저장 구조만으로 단말 소유권이나 계정 공유 정책이 정해지는 것은 아니다.

관련 요구와 차이: [사건 가져오기·배정·종료](../incident-lifecycle.md), [인증·업무폰·토큰](../authentication.md).

## 수색 차수·근무 구간·구역

| 데이터 | 나누어 둔 이유 |
|---|---|
| `operational_period` | 1차·2차 수색처럼 범위나 운영 방향을 나누는 차수. 근무자 교대와 별개다. |
| `duty_shift` | 특정 사건 배정 계정이 업무폰을 사용해 근무한 구간. 차수·사건 배정·업무폰을 연결하고 경로와 현장 기록의 근무 맥락을 남긴다. |
| `search_area` | 차수 안의 수색 구역. `area_level = OVERALL`이 전체 범위이며 별도 `map_boundary` 테이블은 두지 않는다. 상위 구역은 자기 참조로 연결한다. |
| `search_area_assignment` | 구역 담당 계정과 배정한 계정을 구분한다. 사건 접근을 위한 `incident_assignment`와 다르다. |
| `search_area_history` | 구역의 이전·이후 상태와 도형, 변경자·사유를 남긴다. 현재 구역 상태를 대신하는 테이블은 아니다. |
| `search_area_boundary_alert` | 담당 TEAM 구역 밖 위치·재진입을 기록한다. 사건·차수·구역·업무폰에 연결하고 경로 ID는 없을 수 있다. 경계 확인 안내용이지 위반 판정이나 다음 구역 추천이 아니다. |

관련 요구와 차이: [수색 차수·근무 교대](../handover.md), [수색 구역](../search-area.md). 담당 배정·경계 확인의 저장 구조가 있다는 사실만으로 권한 검사나 실제 알림 전달까지 완료됐다고 보지 않는다.

## 경로와 GPS 좌표

| 데이터 | 의미와 구분 |
|---|---|
| `search_path` | 시작부터 종료까지의 경로와 현재 기록 상태. 근무 구간에 속하고 기록 주체는 `account_id`다. |
| `search_path_gps_point` | 품질 검사를 통과한 좌표의 원본 측정값. `point_order`는 경로 안의 저장 순서이고 `point_id`는 앱이 붙인 좌표 식별자다. |
| `search_path_excluded_point` | 도형에서 제외한 좌표도 측정값과 제외 사유를 남긴다. 제외를 원본 폐기로 취급하지 않는다. |
| `search_path_segment` | 차량·도보·미확인 구간과 자동 분류·수동 보정의 출처. 업무폰 종류가 아니라 이동 기록을 나눈 결과다. |
| `search_path_lifecycle_event` | 시작·일시정지·재개·종료 이력. 경로 공백이 사용자의 일시정지 때문인지 구분하기 위한 기록이다. |

[좌표 추가·조회 코드](../../backend/src/main/java/com/surimap/api/service/path/SearchPathService.java)와 [Mapper](../../backend/src/main/resources/mapper/path/SearchPathMapper.xml)는 새 좌표·구간·제외 좌표를 추가하고, 전체 경로는 조회 시 좌표 순서로 조립한다. 누적 LineString을 매번 다시 저장하지 않기 위한 구조다. `client_ts`는 측정 시각이며 순서 번호가 아니다. `elapsed_realtime_nanos`는 단말의 경과시간 값으로, 재부팅을 넘어 비교할 수 있다고 가정하지 않는다.

`search_path.geometry`는 GPS 원본이 없는 옛 경로의 조회 대체값으로 남아 있다. 저장되지 않은 측정값을 만들어 채우지 않으며, 원본 좌표 없이 도형만 있는 경로에는 새 좌표 추가를 거부한다. 기록 중·일시정지 경로를 하나로 제한하는 현재 [고유 인덱스](../../backend/src/main/resources/db/migration/V20260711_002__enforce_single_active_search_path_per_account.sql)는 **사건별이 아니라 계정 전체**에 적용된다.

시각·수집 순서 검사의 남은 한계와 조회 비용은 [수색 경로](../search-path.md), 변경·측정 과정은 [Issue #16 기록](../issues/16-search-path-append-time-increases-with-length.md)에서 확인한다.

## 마커·사진·인수인계

| 데이터 | 의미와 구분 |
|---|---|
| `marker` | 사건·차수에 남긴 위치 기록. 작성 계정과 필요 시 근무 구간·업무폰을 함께 남긴다. `occurred_at`은 현장 기록 시각이며 DB 저장 시각과 다르다. |
| `photo` | 현재 스키마에서는 `marker_id`를 갖는 사진 메타데이터. 파일 본문은 object storage에 있고 업로드 대기·첨부·실패·삭제 상태를 구분한다. 실종자 사진까지 이 테이블로 연결된 것은 아니다. |
| `marker_notification` | 지원 요청·실종자 발견 알림의 내용과 수신자 스냅샷. 마커당 최대 한 행이며 전송 성공 영수증은 아니다. |
| `handover_memo` | 다음 근무자에게 남기는 정성 메모. 차수·근무 구간·경로·구역·마커 중 대상을 `memo_target_type`과 `memo_target_id`로 구분하고, 작성 당시 근무 구간은 별도로 남긴다. |
| `search_history_summary` | 차수 또는 근무 구간 기록의 요약. 원본 묶음의 해시·준비 상태와 생성 상태를 구분한다. 원래 요구는 실패를 `FAILED`로 남기고 임의의 대체 요약을 저장하지 않는 것이다. |
| `op_comparison_analysis` | 여러 차수의 계산 결과와 AI 관찰 문장. 계산 `status`와 문장 `narrative_status`를 나누어, 문장 생성 실패를 계산 결과의 부재와 혼동하지 않게 한다. |

사진 업로드와 마커 첨부, 알림 저장과 실제 수신은 서로 다른 완료 단계다. [PhotoMapper](../../backend/src/main/resources/mapper/photo/PhotoMapper.xml)·[MarkerNotificationMapper](../../backend/src/main/resources/mapper/marker/MarkerNotificationMapper.xml)와 [마커·사진 요구](../marker-photo.md)를 함께 확인한다.

메모 대상 ID와 비교 대상 차수의 JSON 목록은 각각의 대상 테이블을 FK로 연결한 구조가 아니다. 대상 유효성·동일 사건/차수 검사는 Service까지 확인한다. 요약·비교는 기록의 설명을 돕는 기능이며 자동 수색 판단·추천을 뜻하지 않는다. [인수인계 요구와 구현 차이](../handover.md)를 따른다.

## 전송·오프라인·파기

| 데이터 | 의미와 구분 |
|---|---|
| `idempotency_record` | 같은 쓰기 요청의 중복 생성 방지와 기존 응답 재사용. 현재 고유 키는 `idempotency_key + request_path + request_method`이며 키 하나만으로 전역 유일한 구조는 아니다. |
| `offline_package_manifest` | 사건·실종자·차수·구역·초기 마커·타일을 내려받기 위한 구성표. 구성 버전·해시와 단말 설치 상태를 분리한다. |
| `offline_package_installation` | 특정 manifest를 특정 업무폰에 적재한 상태. 서버가 받은 상태 보고이지 실제 오프라인 재열람 성공 증거는 아니다. |
| `fcm_token` | 계정·업무폰·앱 설치 인스턴스에 연결된 Android 푸시 토큰. 인증용 refresh token과 다르다. |
| `event_dispatch_job` | 업무 변경에 따라 전송할 이벤트와 작업 상태. 작업 완료를 브라우저 표시·FCM 수신 완료로 해석하지 않는다. |
| `incident_data_purge` | 사건별 민감 데이터 파기 진행·기한·오류. 사건 자체를 없애는 테이블이 아니다. |
| `incident_data_purge_hook_step` | 파기 작업 안에서 대상별 처리 결과·실패·삭제/보존 개수를 남긴다. 전체 상태만으로 개별 대상의 성공을 추정하지 않기 위한 기록이다. |
| `location_data_access_audit` | 위치 데이터에 접근한 계정·사건·채널·목적·시각·보관 기한. 일반 운영 로그와 다른 기록이다. |

[멱등성 SQL](../../backend/src/main/resources/mapper/sync/IdempotencyRecordMapper.xml), [패키지 SQL](../../backend/src/main/resources/mapper/offlinepackage/OfflinePackageMapper.xml), [전송 작업 SQL](../../backend/src/main/resources/mapper/event/EventDispatchJobMapper.xml), [파기 SQL](../../backend/src/main/resources/mapper/retention/PurgeRunMapper.xml)이 각 저장 경로다. 복구·보존 요구는 [오프라인 동기화](../offline-sync.md), [패키지](../offline-package.md), [데이터 파기·접근기록](../data-retention.md)에 남긴다.

### 구현으로 오해하면 안 되는 옛 설계

| 옛 문서의 설명 | 확인한 코드와 남은 요구 |
|---|---|
| `event_dispatch_target`에 대상별 재시도 상태 저장 | 해당 테이블의 migration·운영 SQL은 없다. 현재 Dispatcher는 SSE 작업 단위 상태를 기록한다. 대상별 실패·재시도 요구는 [이벤트 전달](../event-delivery.md)에서 후속 검토한다. |
| `sse_replay_event`에 사건별 순번·재전송 이력 저장 | 해당 테이블의 migration·운영 SQL은 없다. [EventStreamConfig](../../backend/src/main/java/com/surimap/eventhub/stream/EventStreamConfig.java)가 메모리 저장소를 등록한다. DB 전송 작업의 존재만으로 재시작 후 재전송을 보장하지 않는다. |
| `token_ciphertext`에 암호화된 FCM 토큰 저장 | [업무폰 Service](../../backend/src/main/java/com/surimap/policephone/PolicePhonePersistenceService.java)는 `cipher:` 접두사만 붙인다. 컬럼명을 암호화 구현의 증거로 쓰지 않으며 토큰 보호 요구는 유지한다. |

수리맵의 자체 `refresh_token` 테이블은 [migration](../../backend/src/main/resources/db/migration/V20260517_002__drop_legacy_refresh_token.sql)으로 제거됐다. 기존 읽기 문서도 Keycloak/OIDC의 인증 토큰 관리와 FCM을 구분했다. 현재 인증 연결과 남은 차이는 [인증 문서](../authentication.md)에서 확인한다.

기존 재전송 테이블 설계를 그대로 구현하자는 결정은 아니다. 필요한 복구 범위와 보존 조건을 사용자와 합의한 뒤 구현한다. 파기·감사 테이블도 스케줄러·각 대상의 삭제·접근 기록이 실제 연결됐는지 별도로 검증해야 한다. 옛 문서의 보관 기간은 법적 검토가 끝난 기준으로 취급하지 않는다.

## Android 로컬 저장

Room은 서버 PostgreSQL과 별도의 DB다. 현재 테이블 목록과 schema 버전은 [SuriMapDatabase](../../android/app/src/main/java/com/surimap/core/database/SuriMapDatabase.kt)·[내보낸 schema](../../android/app/schemas/)에서 확인한다.

| 데이터 | 현재 구현과 의미 |
|---|---|
| 옛 `android_outbox` | 실제 테이블명은 [OutboxEntity](../../android/app/src/main/java/com/surimap/core/database/OutboxEntity.kt)의 `android_outbox_row`다. 미전송 요청 본문·멱등성 키·의존 작업·재시도 상태를 보관한다. 서버와 DB 외래 키로 연결되는 것이 아니라 요청 식별자로 대응한다. |
| `android_sync_status` | [SyncStatusEntity](../../android/app/src/main/java/com/surimap/core/database/SyncStatusEntity.kt)는 업무폰 ID를 키로 미전송·재시도·최종 실패 수와 마지막 동기화 상태를 요약한다. 개별 쓰기 원본이나 서버 처리 결과 자체가 아니다. |

초안·로컬 마커·패키지·지도 응답·사건·경로 기록 상태도 별도 Room 엔티티로 관리한다. 옛 문서의 두 테이블만이 로컬 저장소의 전부는 아니다. ACK 전 원본 보존과 종료 후 처리는 [오프라인 동기화 요구](../offline-sync.md)에서 확인한다.

## 과거 설계와 검증 범위

- 상세 컬럼 설명·PRD 절/FR 번호: [축약 전 읽기 문서](https://github.com/sonic8-8/suri-map/blob/3cd777752e2178ebb3470e6f749b1521ce404e7a/docs/db-design/db-design-readable.md).
- 전체 옛 관계·컬럼 도식: [축약 전 ERD](https://github.com/sonic8-8/suri-map/blob/3cd777752e2178ebb3470e6f749b1521ce404e7a/docs/db-design/db-design-erd.md). 문서의 “26개” 표기를 현재 테이블 수로 사용하지 않는다.
- 두 문서의 경로는 [공용 fixture](../../test-fixtures/common-fixtures.json)의 출처 목록에 남긴다. 현재 검사기는 파일 존재를 확인하며 본문 식별자·컬럼 문구를 읽지 않는다.

이번 정리는 문서의 중복과 구현 오해를 줄이는 작업이다. migration·Mapper·Room schema·공개 필드·fixture 값은 변경하지 않았다.
