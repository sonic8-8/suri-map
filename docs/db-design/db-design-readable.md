# Suri-Map DB 설계 읽기 문서

이 문서는 사람이 최종 DB 설계를 빠르게 이해하기 위한 문서다.

각 엔티티는 아래 4가지만 다룬다.

1. PRD의 어떤 요구사항에서 나왔는가
2. 다른 엔티티와 어떤 관계인가
3. 어떤 컬럼을 가지며 각 컬럼이 무엇을 뜻하는가
4. 이 엔티티가 무엇을 의미하는가

이 문서는 팀 공유용 DB 설계 개요이며, 타입, nullable, 제약, 인덱스는 후속 Flyway migration 작성 시 이 문서와 spec 문서를 함께 기준으로 확정한다.

## 한눈에 보는 엔티티 구조

```text
도메인 엔티티
├─ 운용 주체
│  ├─ account
│  └─ police_phone
├─ incident
│  ├─ missing_person                  [incident 하위 1:0..1]
│  ├─ incident_assignment             [incident-account 중간 테이블]
│  └─ operational_period              [incident 하위 수색 차수]
│     ├─ duty_shift                   [OP 하위 근무 구간, incident_assignment/police_phone 참조]
│     ├─ search_area                  [OP 하위 수색 구역, 하위 구역 자기참조 가능]
│     │  ├─ search_area_assignment    [search_area-account 중간 테이블]
│     │  ├─ search_area_history       [search_area 변경 이력]
│     │  └─ search_area_boundary_alert [담당 TEAM 구역 경계 확인 이벤트]
│     ├─ search_path                  [duty_shift 하위 수색 경로]
│     │  ├─ search_path_gps_point     [품질 검사를 통과한 GPS 원본 측정값]
│     │  ├─ search_path_segment       [search_path 하위 경로 구간]
│     │  ├─ search_path_excluded_point [품질 저하로 경로 도형에서 제외된 GPS point]
│     │  └─ search_path_lifecycle_event [수색 시작/일시정지/재개/종료 이력]
│     ├─ marker                       [OP 하위 현장 마커]
│     │  └─ photo                     [marker 하위 첨부 사진]
│     ├─ handover_memo                [OP/근무/경로/구역/마커에 붙는 메모]
│     ├─ search_history_summary       [OP 또는 근무 구간 요약]
│     └─ op_comparison_analysis       [OP 간 비교 분석 요청/결과]

운영 엔티티
├─ fcm_token                          [police_phone 참조]
├─ marker_notification                [marker에서 파생된 알림 스냅샷]
├─ idempotency_record                 [사건 쓰기 요청 중복 처리]
├─ offline_package_manifest           [incident 기준 오프라인 패키지 구성표]
├─ offline_package_installation       [manifest-police_phone 중간 상태 테이블]
├─ event_dispatch_job                 [사건 이벤트 전파 작업]
│  ├─ event_dispatch_target           [전파 대상별 상태]
│  └─ sse_replay_event                [SSE 재전송용 이벤트]
├─ incident_data_purge                [incident 민감 데이터 파기 상태]
└─ location_data_access_audit         [incident 위치 데이터 접근 감사]

Android Room 로컬 엔티티
├─ android_outbox                     [앱 로컬 미전송 요청 큐]
└─ android_sync_status                [앱 로컬 동기화 상태]
```

## 엔티티 구분 기준

- 도메인 엔티티: 경찰 수색 업무에서 직접 설명되는 사건, 계정, 폴리폰, OP, 근무 구간, 수색 구역, 경로, 마커, 사진, 인수인계, 요약을 표현한다.
- 운영 엔티티: 인증, 푸시, 알림 전파, 멱등성, 오프라인 패키지 적재 상태, 이벤트 전파, 파기, 감사처럼 서비스를 안정적으로 운영하기 위해 필요하다.
- Android Room 로컬 엔티티: 백엔드 PostgreSQL이 아니라 Android 앱 내부에서 관리한다.

## 1. 도메인 엔티티

### 사건

#### incident

**PRD 근거**

- PRD §2 용어 `사건(Incident)`
- PRD §5.1 시나리오 1 `배정 사건 가져오기`
- PRD §7.1 FR-01 `사건 관리`
- PRD §8.5 `사건 종료 정책`

**연관 관계**

- 하나의 `incident`는 0개 또는 1개의 `missing_person`을 가진다. 사건 종료 후 개인정보 파기로 없을 수 있다. (1:0..1)
- 하나의 `incident`는 여러 개의 `incident_assignment`를 가진다. (1:N)
- 하나의 `incident`는 여러 개의 `operational_period`를 가진다. (1:N)

**주요 컬럼**

- `id`: 사건 식별자
- `source_incident_id`: mock 112 원천 사건 ID. DB에는 UUID로 저장한다.
- `title`: 사건 표시 제목
- `status`: 사건 진행 상태
- `opened_at`: 사건 시작 시각
- `closed_at`: 사건 종료 시각
- `closed_by_account_id`: 사건을 종료한 계정
- `version`: 사건 상태 변경 버전
- `created_at`: 생성 시각
- `updated_at`: 수정 시각

**설명**

`incident`는 112/mock에서 가져온 실종 사건 1건이다. Suri-Map의 최상위 기준이며, 수색 차수, 구역, 경로, 마커, 인수인계, 파기의 기준이 된다.

#### missing_person

**PRD 근거**

- PRD §7.6 FR-21 `실종자 기본 정보`
- PRD §7.6 FR-22 `사건 종료 시 개인정보 삭제`
- PRD §8.5 `데이터 보존/파기`

**연관 관계**

- 하나의 `missing_person`은 하나의 `incident`에 속한다. (1:1)
- 하나의 `incident`는 진행 중일 때 하나의 `missing_person`을 가진다. (1:0..1)

**주요 컬럼**

- `incident_id`: 실종자 정보가 속한 사건
- `display_name`: 실종자 표시 이름
- `photo_object_key`: 실종자 사진 object key
- `appearance_text`: 인상착의 설명
- `last_seen_location_text`: 마지막 목격 위치 설명
- `last_seen_at`: 마지막 목격 시각
- `imported_at`: Suri-Map에 반영된 시각

**설명**

`missing_person`은 사건 진행 중 필요한 실종자 정보다. `cache`라는 이름은 쓰지 않는다. 사건 종료 후 Suri-Map active DB에서 제거될 수 있는 도메인 데이터다.

#### incident_assignment

**PRD 근거**

- PRD §5.1 시나리오 1 `배정 사건 가져오기`
- PRD §5.1 시나리오 2 `지원 부대 배정`
- PRD §8.4 `인증/권한`

**연관 관계**

- 하나의 `incident`는 여러 개의 `incident_assignment`를 가진다. (1:N)
- 하나의 `account`는 여러 개의 `incident_assignment`를 가진다. (1:N)
- 하나의 `incident_assignment`는 하나의 `incident`와 하나의 `account`를 연결한다. (N:1, N:1)

**주요 컬럼**

- `id`: 사건 배정 식별자
- `incident_id`: 배정 대상 사건
- `account_id`: 사건에 배정된 계정
- `incident_role`: 사건 안에서의 역할
- `assigned_at`: 사건 배정이 반영된 시각
- `revoked_at`: 사건 배정이 해제된 시각
- `created_at`: 생성 시각
- `updated_at`: 수정 시각

**설명**

`incident_assignment`는 112/mock에서 받은 사건 배정 결과다. 사건 접근 가능 여부를 판단하는 기준이며, `incident`와 `account`의 N:N 관계를 푸는 엔티티다.

### 계정과 폴리폰

#### account

**PRD 근거**

- PRD §3.1 `주요 사용자`
- PRD §3.1 `계정 모델 판단 근거`
- PRD §8.4 `계정 모델`

**연관 관계**

- 하나의 `account`는 여러 개의 `incident_assignment`를 가진다. (1:N)
- 하나의 `account`는 여러 개의 `search_area_assignment`를 받을 수 있다. (1:N)
- 하나의 `account`는 여러 개의 `marker`, `handover_memo`, `search_history_summary`, `op_comparison_analysis`를 작성하거나 요청할 수 있다. (1:N)

**주요 컬럼**

- `id`: 계정 식별자. 내부 참조와 FK는 UUID를 사용한다.
- `login_id`: 사람이 입력하는 로그인 ID. `acct-*` fixture 코드는 여기에 해당하며 FK로 사용하지 않는다.
- `password_hash`: 비밀번호 해시
- `display_name`: 화면에 표시할 계정 이름
- `account_type`: 개인 계정의 권한 projection 보조 분류. 팀/순찰차 공유 계정을 의미하지 않는다.
- `organization_type`: 실종팀, 지원부대, 지구대/파출소 구분
- `status`: 계정 활성 상태
- `created_at`: 생성 시각
- `updated_at`: 수정 시각

**설명**

`account`는 개인 사용자의 로그인 주체다. 팀, 순찰차, 지휘 맥락은 사건 배정 역할, 조직,
`account_type`, 현재 사용하는 `police_phone` 컨텍스트로 파생하며, 폴리폰을 계정 소유물로
판정하지 않는다.

#### police_phone

**PRD 근거**

- PRD §2 용어 `폴리폰`, `팀 업무폰`, `순찰차 업무폰`
- PRD §7.7 FR-33 `개인 계정 기준 수색 경로 기록`
- PRD §8.4 `폴리폰 장기 로그인`

**연관 관계**

- 하나의 `police_phone`은 여러 개의 `duty_shift`에서 사용될 수 있다. (1:N)
- 하나의 `police_phone`은 여러 개의 `fcm_token`을 가진다. (1:N)
- 하나의 `police_phone`은 여러 개의 `offline_package_installation`을 가진다. (1:N)

**주요 컬럼**

- `id`: 폴리폰 식별자
- `phone_code`: 폴리폰 시스템 식별 코드
- `display_name`: 화면에 표시할 폴리폰 이름
- `status`: 폴리폰 사용 가능 상태
- `registered`: 앱 폴리폰 등록 여부. `police_phone_not_registered` guard 판정 기준
- `last_heartbeat_at`: 마지막 생존 신호 시각
- `last_sync_at`: 마지막 동기화 완료 시각
- `heartbeat_sequence`: 마지막으로 수용한 heartbeat sequence. 낮거나 같은 sequence 재전송은 DB 상태와 event를 갱신하지 않는다.
- `last_heartbeat_event_id`: 마지막으로 수용한 heartbeat에서 발행한 event 식별자. REST 응답과 board freshness row의 `latestEventId` 수렴 기준이다.
- `version`: heartbeat 수용 시 증가하는 폴리폰 상태 변경 버전
- `created_at`: 생성 시각
- `updated_at`: 수정 시각

**설명**

`police_phone`은 현장 앱이 설치된 경찰 업무용 스마트폰이다. `account`는 조작 주체이고, `police_phone`은 위치와 경로를 기록한 물리 단말이다.

### 수색 차수와 근무 구간

#### operational_period

**PRD 근거**

- PRD §2 용어 `OP(Operation Period, 수색 차수)`
- PRD §7.7 FR-32 `OP 단위 수색 히스토리 레이어`
- PRD §5.1 시나리오 10, 11 `새 OP 열기`, `OP 비교`

**연관 관계**

- 하나의 `incident`는 여러 개의 `operational_period`를 가진다. (1:N)
- 하나의 `operational_period`는 여러 개의 `duty_shift`를 가진다. (1:N)
- 하나의 `operational_period`는 여러 개의 `search_area`, `marker`, `handover_memo`, `search_history_summary`, `op_comparison_analysis`의 입력이 될 수 있다. (1:N)

**주요 컬럼**

- `id`: OP 식별자
- `incident_id`: OP가 속한 사건
- `sequence_number`: 사건 안에서의 OP 차수
- `status`: OP 진행 상태
- `reason`: OP 생성 사유
- `reason_memo`: OP 생성 사유 상세
- `started_by_account_id`: OP를 시작한 계정
- `ended_by_account_id`: OP를 종료한 계정
- `started_at`: OP 시작 시각
- `ended_at`: OP 종료 시각
- `version`: OP 상태 변경 버전
- `created_at`: 생성 시각
- `updated_at`: 수정 시각

**설명**

`operational_period`는 1차 수색, 2차 수색 같은 큰 수색 차수다. 근무 교대가 아니라 수색 범위나 운영 방향이 바뀌는 단위다.

#### duty_shift

**PRD 근거**

- PRD §3.1 `같은 업무폰을 다음 근무자가 이어 사용`
- PRD §7.7 FR-32 `근무 교대·인수인계`
- PRD §7.7 FR-33 `같은 업무폰 경로가 OP·세션 단위로 구분`

**연관 관계**

- 하나의 `operational_period`는 여러 개의 `duty_shift`를 가진다. (1:N)
- 하나의 `incident_assignment`는 여러 개의 `duty_shift`를 가진다. (1:N)
- 하나의 `police_phone`은 여러 개의 `duty_shift`에서 사용될 수 있다. (1:N)
- 하나의 `duty_shift`는 여러 개의 `search_path`, `marker`, `handover_memo`의 작성 맥락이 된다. (1:N)

**주요 컬럼**

- `id`: 근무 구간 식별자
- `operational_period_id`: 근무 구간이 속한 OP
- `incident_assignment_id`: 근무하는 사건 배정 계정
- `police_phone_id`: 인수해서 사용하는 폴리폰
- `status`: 근무 구간 진행 상태
- `started_at`: 업무 인수/근무 시작 시각
- `ended_at`: 업무 인계/근무 종료 시각
- `created_at`: 생성 시각
- `updated_at`: 수정 시각

**설명**

`duty_shift`는 특정 사건 배정 계정이 특정 폴리폰을 인수해 근무한 구간이다. 사건 전체 교대가 아니라 계정/폴리폰 운용 단위의 근무 구간이다.

### 수색 구역

#### search_area

**PRD 근거**

- PRD §5.1 시나리오 4 `구역 분할 / 할당`
- PRD §7.2 FR-11, FR-12 `수색 구역 관리`
- PRD §7.5 FR-23 `OP별 경로 히스토리와 재확인 판단 보조`

**연관 관계**

- 하나의 `operational_period`는 여러 개의 `search_area`를 가진다. (1:N)
- 하나의 상위 `search_area`는 여러 개의 하위 `search_area`를 가진다. (1:N)
- 하나의 `search_area`는 여러 개의 `search_area_assignment`를 가진다. (1:N)
- 하나의 `search_area`는 여러 개의 `search_area_history`를 가진다. (1:N)

**주요 컬럼**

- `id`: 수색 구역 식별자
- `operational_period_id`: 구역이 속한 OP
- `parent_search_area_id`: 상위 수색 구역
- `name`: 구역 이름
- `area_level`: 전체 범위, 부대 권역, 팀 구역 구분
- `color_token`: 웹/앱 공통 팔레트에서 사용할 표시 색상 슬롯
- `geometry`: 지도상 구역 Polygon
- `status`: 구역 상태
- `version`: 구역 변경 버전
- `created_by_account_id`: 구역을 만든 계정
- `created_at`: 생성 시각
- `updated_at`: 수정 시각

**설명**

`search_area`는 OP 안에서 사용하는 지도상 수색 구역이다. `area_level = OVERALL`인 `search_area`가 전체 수색 범위 역할을 한다.

#### search_area_assignment

**PRD 근거**

- PRD §5.1 시나리오 4 `팀별 담당 구역 분할·할당`
- PRD §8.4 권한 매트릭스 `구역 할당/완료 처리`

**연관 관계**

- 하나의 `search_area`는 여러 개의 `search_area_assignment`를 가진다. (1:N)
- 하나의 `account`는 여러 개의 `search_area_assignment`를 받을 수 있다. (1:N)
- 하나의 `account`는 여러 개의 `search_area_assignment`를 생성할 수 있다. (1:N)

**주요 컬럼**

- `id`: 수색 구역 배정 식별자
- `search_area_id`: 배정 대상 수색 구역
- `assigned_account_id`: 구역을 담당하는 계정
- `assigned_by_account_id`: 구역을 배정한 계정
- `assigned_at`: 배정 시각
- `revoked_at`: 배정 취소 시각
- `status`: 배정 상태
- `memo`: 배정 관련 지시사항
- `created_at`: 생성 시각
- `updated_at`: 수정 시각

**설명**

`search_area_assignment`는 수색 구역을 계정에 배정한 기록이다. 112의 사건 배정인 `incident_assignment`와 다르다.

#### search_area_history

**PRD 근거**

- PRD §5.1 시나리오 10 `구역 완료 처리`
- PRD §7.7 FR-32 `OP별 완료 구역 히스토리`
- PRD §7.7 FR-39 `수색 이력 요약 입력 데이터`

**연관 관계**

- 하나의 `search_area`는 여러 개의 `search_area_history`를 가진다. (1:N)
- 하나의 `search_area_history`는 하나의 `search_area`에 속한다. (N:1)
- 하나의 `account`는 여러 개의 `search_area_history`를 남길 수 있다. (1:N)

**주요 컬럼**

- `id`: 구역 이력 식별자
- `search_area_id`: 변경된 수색 구역
- `change_type`: 변경 종류
- `previous_status`: 변경 전 상태
- `next_status`: 변경 후 상태
- `previous_geometry`: 변경 전 구역 Polygon
- `next_geometry`: 변경 후 구역 Polygon
- `change_memo`: 변경 사유
- `changed_by_account_id`: 변경한 계정
- `changed_at`: 변경 시각
- `created_at`: 이력 row 생성 시각

**설명**

`search_area_history`는 구역 생성, 수정, 분할, 완료, 취소 같은 변경 이력을 남긴다. 현재 상태는 `search_area`가 가지고, 변경 맥락은 이 테이블이 가진다.

### 수색 경로

#### search_path

**PRD 근거**

- PRD §5.1 시나리오 5 `수색 시작 및 GPS 기록`
- PRD §7.7 FR-33 `개인 계정 기준 수색 경로 기록`
- PRD §7.7 FR-34 `수색 시작/종료 단위 관리`

**연관 관계**

- 하나의 `duty_shift`는 여러 개의 `search_path`를 가진다. (1:N)
- 하나의 `search_path`는 하나의 `duty_shift`에 속한다. (N:1)
- 하나의 `account`는 여러 개의 `search_path`를 기록할 수 있다. (1:N)
- 하나의 `search_path`는 여러 개의 `search_path_gps_point`를 가진다. (1:N)
- 하나의 `search_path`는 여러 개의 `search_path_segment`를 가진다. (1:N)
- 하나의 `search_path`는 여러 개의 `search_path_excluded_point`를 가진다. (1:N)
- 하나의 `search_path`는 여러 개의 `search_path_lifecycle_event`를 가진다. (1:N)

**주요 컬럼**

- `id`: 수색 경로 식별자
- `duty_shift_id`: 경로가 기록된 근무 구간
- `account_id`: 경로를 기록한 로그인 계정
- `status`: 경로 기록 상태 (`RECORDING`, `PAUSED`, `ENDED`)
- `started_at`: 경로 기록 시작 시각
- `ended_at`: 경로 기록 종료 시각
- `geometry`: 지도에 표시할 경로 LineString
- `version`: 경로 변경 버전
- `created_at`: 생성 시각
- `updated_at`: 수정 시각

**설명**

`search_path`는 수색 시작/일시정지/재개/종료 버튼으로 관리되는 하나의 수색 경로다. 근무 구간 전체는 `duty_shift`, 실제 GPS 기록 주체는 `account_id`가 맡는다. 업무폰은 APP 요청이 등록된 단말 설정을 사용했는지 확인하는 데만 쓰며 경로의 기록 주체로 저장하지 않는다. 일시정지는 경로 공백이 의도된 운영 상태였음을 남기는 상태이며, 상세 전이 이력은 `search_path_lifecycle_event`가 가진다.

#### search_path_gps_point

**PRD 근거**

- PRD §5.1 시나리오 5 `수색 시작 및 GPS 기록`
- PRD §7.7 FR-33 `개인 계정 기준 수색 경로 기록`

**연관 관계**

- 하나의 `search_path`는 여러 개의 `search_path_gps_point`를 가진다. (1:N)
- 하나의 `search_path_gps_point`는 하나의 `search_path`에 속한다. (N:1)

**주요 컬럼**

- `search_path_id`: GPS 측정값이 속한 수색 경로
- `point_order`: 수색 경로 안에서 좌표가 수집된 순서
- `point_id`: 앱이 좌표마다 붙인 식별자
- `client_ts`: 앱이 좌표를 수집한 시각
- `lon`, `lat`: 앱이 측정한 경도와 위도
- `speed_mps`: 앱이 측정한 초당 이동 속도
- `horizontal_accuracy_m`: 앱이 전달한 GPS 오차 범위
- `location_provider`: 좌표를 만든 Android 위치 제공자
- `elapsed_realtime_nanos`: 기기가 켜진 뒤 좌표가 만들어질 때까지 흐른 시간. 같은 앱 실행 중 좌표 순서를 확인하는 데 사용
- `created_at`: 서버가 측정값을 저장한 시각

**설명**

`search_path_gps_point`는 GPS 품질 검사를 통과한 원본 측정값을 수집 순서대로 저장한다. `client_ts`는 앱이 기록한 실제 시각이며, 좌표 순서는 `elapsed_realtime_nanos`로 확인한다. `search_path.geometry`는 지도에 경로 선을 그리는 데 사용하고, 이 테이블은 DB를 다시 읽은 뒤에도 좌표 식별자, 수집 시각, 속도, 정확도를 실제 값 그대로 돌려주는 데 사용한다.

이 테이블을 만들기 전에 저장된 경로는 기존 `search_path.geometry`와 `search_path_segment`의 좌표·시각만 사용한다. DB에 남아 있지 않은 좌표 식별자, 수집 시각, 속도, 정확도는 임의로 만들지 않는다. 기존 경로의 좌표를 덮어쓰지 않도록 GPS 원본 측정값이 없는 기록 중 경로에는 새 좌표를 추가하지 않는다.

#### search_path_lifecycle_event

**PRD 근거**

- PRD §5.1 시나리오 5 `수색 시작 및 GPS 기록`
- PRD §7.7 FR-34 `수색 시작/종료 단위 관리`

**연관 관계**

- 하나의 `search_path`는 여러 개의 `search_path_lifecycle_event`를 가진다. (1:N)
- 하나의 `search_path_lifecycle_event`는 하나의 `search_path`에 속한다. (N:1)

**주요 컬럼**

- `id`: 수색 경로 lifecycle event 식별자
- `search_path_id`: 전이가 발생한 수색 경로
- `event_type`: `STARTED`, `PAUSED`, `RESUMED`, `ENDED`
- `client_ts`: 폴리폰에서 전이를 요청한 시각
- `server_received_at`: 서버가 전이를 기록한 시각
- `version`: 전이 후 `search_path.version`
- `created_at`: 이력 row 생성 시각

**설명**

`search_path_lifecycle_event`는 경로 선이 끊긴 시간이 실제 GPS 누락인지, 사용자가 수색 기록을 일시정지한 것인지 구분하기 위한 감사 이력이다. 현재 상태는 `search_path.status`가 가지고, 상태가 어떻게 바뀌었는지는 이 테이블이 가진다.

#### search_path_segment

**PRD 근거**

- PRD §2 용어 `순찰차 업무폰`
- PRD §7.7 FR-33 `GPS 속도 기반 차량/도보 자동 분리`
- PRD §7.7 FR-35 `순찰차 이동 경로 표시`

**연관 관계**

- 하나의 `search_path`는 여러 개의 `search_path_segment`를 가진다. (1:N)
- 하나의 `search_path_segment`는 하나의 `search_path`에 속한다. (N:1)
- 하나의 `account`는 여러 개의 `search_path_segment`를 수동 보정할 수 있다. (1:N)

**주요 컬럼**

- `id`: 수색 경로 구간 식별자
- `search_path_id`: 구간이 속한 수색 경로
- `movement_type`: 차량, 도보, 알 수 없음 구분
- `movement_type_source`: 자동 분류 또는 수동 보정 구분
- `geometry`: 구간 LineString
- `started_at`: 구간 시작 시각
- `ended_at`: 구간 종료 시각
- `corrected_by_account_id`: 구간을 수동 보정한 계정
- `corrected_at`: 수동 보정 시각
- `version`: 구간 변경 버전
- `created_at`: 생성 시각
- `updated_at`: 수정 시각

**설명**

`search_path_segment`는 수색 경로를 차량, 도보, 알 수 없음 구간으로 나눈 결과다. 폴리폰 종류가 아니라 실제 이동 패턴을 기준으로 분리한다.

#### search_path_excluded_point

**PRD 근거**

- PRD §7.7 FR-33 `GPS 속도 기반 차량/도보 자동 분리`
- S3-1 품질 정책 `low-quality point는 경로 도형과 segment geometry에서 제외하고 excludedPoints로 노출`

**연관 관계**

- 하나의 `search_path`는 여러 개의 `search_path_excluded_point`를 가진다. (1:N)
- 하나의 `search_path_excluded_point`는 하나의 `search_path`에 속한다. (N:1)

**주요 컬럼**

- `id`: 제외 point evidence 식별자. 내부 참조와 FK는 UUID를 사용한다.
- `search_path_id`: 제외 point가 속한 수색 경로
- `point_id`: 앱 batch 요청의 point 식별자. 사람이 입력하는 값은 아니지만 DB 내부 PK/FK가 아니므로 문자열로 저장한다.
- `reason`: 제외 사유. API 응답에서 사용하는 `low_accuracy`, `clock_skew`, `invalid_speed`, `distance_jump`, `out_of_order` 값이다.
- `client_ts`: 앱이 수집한 point 시각
- `lon`, `lat`: 앱이 보낸 경도와 위도
- `speed_mps`: 앱이 보낸 초당 이동 속도
- `horizontal_accuracy_m`: 앱이 보낸 GPS 오차 범위
- `location_provider`: 좌표를 만든 Android 위치 제공자
- `elapsed_realtime_nanos`: 기기가 켜진 뒤 좌표가 만들어질 때까지 흐른 시간
- `created_at`: 생성 시각
- `updated_at`: 수정 시각

**설명**

`search_path_excluded_point`는 품질 저하나 확인할 수 없는 수집 순서 때문에 서버 canonical LineString과 `search_path_segment.geometry`에 들어가지 않은 GPS point evidence다. 제외한 좌표도 원본 측정값과 사유를 함께 저장한다. 지도에서 수색 완료 경로로 그리지는 않지만, 앱·상황판·인수인계가 저품질/제외 상태를 표시할 수 있게 `PathQuery`의 `excludedPoints` source가 된다.

### 마커와 사진

#### marker

**PRD 근거**

- PRD §5.1 시나리오 6 `단서 또는 실종자 발견`
- PRD §5.1 시나리오 8 `지원 요청 마커 생성`
- PRD §7.4 FR-14, FR-15, FR-16, FR-17 `현장 마커`

**연관 관계**

- 하나의 `operational_period`는 여러 개의 `marker`를 가진다. (1:N)
- 하나의 `duty_shift`는 여러 개의 `marker`를 만들 수 있다. (1:N)
- 하나의 `account`는 여러 개의 `marker`를 작성할 수 있다. (1:N)
- 하나의 `marker`는 여러 개의 `photo`를 가진다. (1:N)
- 하나의 `marker`는 0개 또는 1개의 `marker_notification`을 만든다. (1:0..1)

**주요 컬럼**

- `id`: 마커 식별자
- `operational_period_id`: 마커가 속한 OP
- `duty_shift_id`: 현장 앱에서 마커가 생성된 근무 구간
- `marker_type`: 마커 유형
- `support_request_type`: 지원 요청 세부 유형
- `location`: 마커 위치 Point
- `memo`: 마커 메모
- `occurred_at`: 현장 발생/작성 시각
- `created_by_account_id`: 마커 작성 계정
- `police_phone_id`: 마커를 생성한 폴리폰
- `marker_source`: 앱, 웹, mock seed, 시스템 출처
- `status`: 마커 상태
- `version`: 마커 변경 버전
- `created_at`: 생성 시각
- `updated_at`: 수정 시각

**설명**

`marker`는 지도 위 특정 위치에 남기는 현장 기록이다. 단서, 실종자 발견, 현장 상태, 지원 요청 위치, 일반 메모를 표현한다.

#### photo

**PRD 근거**

- PRD §7.4 FR-20 `현장 마커 사진 첨부`
- PRD §8.5 `사진과 개인정보 파기`

**연관 관계**

- 하나의 `marker`는 여러 개의 `photo`를 가진다. (1:N)
- 하나의 `photo`는 하나의 `marker`에 속한다. (N:1)

**주요 컬럼**

- `id`: 사진 식별자
- `marker_id`: 사진이 첨부된 마커
- `object_key`: MinIO/S3 object key
- `status`: 사진 업로드/첨부 상태
- `content_type`: 파일 MIME type
- `size_bytes`: 파일 크기
- `width`: 이미지 가로 크기
- `height`: 이미지 세로 크기
- `checksum_sha256`: 파일 무결성 checksum
- `captured_at`: 사진 촬영 시각
- `upload_url_expires_at`: 업로드 URL 만료 시각
- `attached_at`: 마커 첨부 완료 시각
- `deleted_at`: 삭제 처리 시각
- `version`: 사진 상태 변경 버전
- `created_at`: 생성 시각
- `updated_at`: 수정 시각

**설명**

`photo`는 마커에 첨부되는 사진 메타데이터다. 사진 파일은 DB에 저장하지 않고 MinIO/S3 호환 저장소에 저장한다.

### 인수인계와 수색 이력 요약

#### handover_memo

**PRD 근거**

- PRD §2 용어 `인수인계 메모`
- PRD §7.7 FR-37 `수색 경로·구역·OP 인수인계 메모`
- PRD §7.7 FR-39 `수색 이력 요약 입력 데이터`

**연관 관계**

- 하나의 `operational_period`는 여러 개의 `handover_memo`를 가진다. (1:N)
- 하나의 `account`는 여러 개의 `handover_memo`를 작성할 수 있다. (1:N)
- 하나의 `handover_memo`는 하나의 대상에 붙는다. 대상은 OP, 근무 구간, 경로, 구역, 마커 중 하나다. (polymorphic)

**주요 컬럼**

- `id`: 인수인계 메모 식별자
- `operational_period_id`: 메모가 속한 OP
- `memo_target_type`: 메모 대상 종류
- `memo_target_id`: 메모 대상 row 식별자
- `content`: 메모 본문
- `created_by_account_id`: 메모 작성 계정
- `duty_shift_id`: 메모 작성 당시 근무 구간
- `created_at`: 생성 시각
- `updated_at`: 수정 시각

**설명**

`handover_memo`는 다음 근무자에게 넘겨주기 위한 정성 메모다. 근무 교대 순간뿐 아니라 수색 중에도 작성할 수 있다.

#### search_history_summary

**PRD 근거**

- PRD §7.7 FR-39 `수색 이력 자동 요약`
- PRD §5.1 시나리오 11 `인수인계 / OP 비교 / 수색 이력 요약`

**연관 관계**

- 하나의 `operational_period`는 여러 개의 `search_history_summary`를 가진다. (1:N)
- 하나의 `duty_shift`는 여러 개의 `search_history_summary`를 가질 수 있다. (1:N)
- 하나의 `account`는 여러 개의 `search_history_summary` 생성을 요청할 수 있다. (1:N)

**주요 컬럼**

- `id`: 수색 기록 요약 식별자
- `operational_period_id`: 요약 대상 OP
- `duty_shift_id`: 요약 대상 근무 구간
- `generation_status`: 수색 이력 요약 생성 상태
- `content`: 요약 본문
- `source_data_hash`: 요약 원본 데이터 묶음 hash
- `requested_by_account_id`: 요약 생성을 요청한 계정
- `generated_at`: 요약 생성 완료 시각
- `version`: 요약 상태 변경 버전
- `created_at`: 생성 시각
- `updated_at`: 수정 시각

**설명**

`search_history_summary`는 OP 또는 근무 구간의 수색 기록을 AI로 요약한 결과다. 판단이나 추천이 아니라 기록을 읽기 쉽게 줄여주는 기능이다. 생성 실패 시 `generation_status = FAILED`로 남기며, 템플릿·규칙 기반 대체 요약 문장은 저장하지 않는다.

#### op_comparison_analysis

**PRD 근거**

- PRD §7.2 FR-11 `수색 차수(OP) 비교`
- PRD §7.7 FR-32 `OP 단위 수색 히스토리 레이어`
- PRD §5.1 시나리오 11 `인수인계 / OP 비교 / 수색 이력 요약`

**연관 관계**

- 하나의 `incident`는 여러 개의 `op_comparison_analysis`를 가진다. (1:N)
- 하나의 `op_comparison_analysis`는 같은 사건의 여러 `operational_period`를 비교 입력으로 가진다. (N:1 배열 참조)
- 하나의 `account`는 여러 개의 `op_comparison_analysis` 생성을 요청할 수 있다. (1:N)

**주요 컬럼**

- `id`: OP 비교 분석 식별자
- `incident_id`: 비교 대상 OP들이 속한 사건
- `operational_period_ids`: 비교 대상 OP ID 배열. request hash 계산 전에 정렬된 순서로 저장한다.
- `request_hash`: 같은 incident, OP 목록, source data hash 조합의 중복 요청 방지 키
- `source_data_hash`: 경로·마커·메모 등 비교 원본 데이터 묶음 hash
- `status`: 결정적 비교 분석 상태. `GENERATING`, `READY`, `FAILED` 중 하나다.
- `metrics_json`: OP별 결정적 metric 결과
- `diff_facts_json`: 임계값을 통과한 결정적 차이 fact
- `common_regions_geojson`: UI highlight용 공통/차이 영역 GeoJSON
- `narrative_status`: AI 관찰 문장 생성 상태. `SKIPPED`, `GENERATING`, `READY`, `FAILED` 중 하나다.
- `observations_json`: 검증을 통과한 AI 관찰 문장과 evidence 배열
- `failure_reason`: provider, schema, guard 실패 사유
- `requested_by_account_id`: 분석 생성을 요청한 계정
- `requested_at`: 요청 시각
- `generated_at`: 결정적 분석 또는 narrative 생성 완료 시각
- `version`: 분석 상태 변경 버전
- `created_at`: 생성 시각
- `updated_at`: 수정 시각

**설명**

`op_comparison_analysis`는 여러 OP의 metric, 차이 fact, 공통 영역, 검증된 관찰 문장을 저장하는 분석 결과다. 차이 계산과 영역 계산은 결정적 코드가 수행하며, AI는 임계값을 통과한 사실을 근거가 달린 관찰 문장으로 옮기는 데만 사용한다. 임계값 미달 또는 provider 실패 시에도 metric과 fact는 유지하고 `narrative_status`만 별도로 표시한다.

## 2. 운영 엔티티

### 인증과 푸시 채널

인증 토큰 발급과 refresh token 관리는 Keycloak/OIDC가 담당한다. Suri-Map 운영 DB는 자체
`refresh_token` 테이블을 소유하지 않고, API 요청 시 Keycloak access token의 claim을 검증해
`account`/`police_phone` 컨텍스트로 사용한다.

#### fcm_token

**PRD 근거**

- PRD §8.3 `알림`
- PRD §5.1 시나리오 8 `지원 요청 마커 생성`
- PRD §7.4 FR-17, FR-20 및 §8.3 `실종자 발견/지원 요청 알림`

**연관 관계**

- 하나의 `police_phone`은 여러 개의 `fcm_token`을 가진다. (1:N)
- 하나의 `fcm_token`은 하나의 `police_phone`에 속한다. (N:1)
- 하나의 `account`는 여러 개의 `fcm_token` 등록 이력을 가질 수 있다. (1:N)

**주요 컬럼**

- `id`: FCM token 식별자
- `account_id`: token 등록 당시 로그인 계정
- `police_phone_id`: token이 연결된 폴리폰
- `app_instance_id`: 앱 설치 인스턴스 식별자
- `token_hash`: token 식별용 해시
- `token_ciphertext`: 암호화된 FCM token
- `status`: token 활성 상태
- `created_at`: 생성 시각
- `last_registered_at`: 마지막 등록/갱신 시각
- `revoked_at`: token 회수 시각
- `version`: token 상태 변경 버전

**설명**

`fcm_token`은 Android 폴리폰에 푸시를 보내기 위한 토큰이다. 웹 상황판은 FCM이 아니라 SSE를 사용한다.

### 알림

#### search_area_boundary_alert

**PRD 근거**

- PRD §7.2 FR-36 `담당 구역 경계 확인`
- PRD §5.1 시나리오 5 `수색 경로·PolicePhone GPS 경로`

**연관 관계**

- 하나의 `incident`는 여러 개의 `search_area_boundary_alert`를 가진다. (1:N)
- 하나의 `operational_period`는 여러 개의 `search_area_boundary_alert`를 가진다. (1:N)
- 하나의 `search_area`는 여러 개의 `search_area_boundary_alert`를 가진다. (1:N)
- 하나의 `police_phone`은 여러 개의 `search_area_boundary_alert`를 남길 수 있다. (1:N)
- 하나의 `search_path`는 0개 이상의 `search_area_boundary_alert`와 연결될 수 있다. (1:N)

**주요 컬럼**

- `id`: 담당 구역 경계 확인 이벤트 식별자
- `incident_id`: 이벤트가 속한 사건
- `operational_period_id`: 이벤트가 발생한 OP
- `search_area_id`: 기준이 된 TEAM 수색구역
- `police_phone_id`: 위치를 수집한 폴리폰
- `search_path_id`: 수색 중인 경로. 경로 기록 전/후 이벤트는 비어 있을 수 있다
- `alert_type`: `OUTSIDE_ASSIGNED_AREA`, `REENTERED_ASSIGNED_AREA`
- `status`: 이벤트 저장 상태
- `location`: GPS 기준 현재 위치
- `client_ts`: 단말 수집 시각
- `server_received_at`: 서버 수신 시각
- `version`: 이벤트 버전
- `created_at`: 생성 시각

**설명**

`search_area_boundary_alert`는 현장 단말이 담당 TEAM 구역 경계 밖 위치로 표시됐다는 사실을 운영 참고용으로 남기는 이벤트다. 이 데이터는 징계성 위반 판단이나 다음 수색 구역 추천이 아니라, 현장 앱의 중립적인 경계 확인 안내와 FCM data message 재전파를 위한 기준이다.

#### marker_notification

**PRD 근거**

- PRD §8.3 `알림`
- PRD §5.1 시나리오 6 `실종자 발견 알림`
- PRD §5.1 시나리오 8 `지원 요청 알림`

**연관 관계**

- 하나의 `marker`는 0개 또는 1개의 `marker_notification`을 가진다. (1:0..1)
- 하나의 `marker_notification`은 하나의 `marker`에서 파생된다. (1:1)

**주요 컬럼**

- `id`: 마커 알림 식별자
- `marker_id`: 알림을 만든 마커
- `notification_type`: 알림 종류
- `recipient_rule`: 수신자 계산 규칙
- `recipient_account_ids`: 수신 계정 목록
- `recipient_police_phone_ids`: 수신 폴리폰 목록
- `notification_payload`: 알림 payload
- `created_at`: 생성 시각

**설명**

`marker_notification`은 지원 요청 또는 실종자 발견 마커에서 파생되는 알림 기능 엔티티다. 사용자가 받게 될 알림 내용과 수신자 스냅샷을 저장하고, 실제 FCM/SSE 전송 성공 여부는 이벤트 전파 테이블에서 다룬다.

### 멱등성

#### idempotency_record

**PRD 근거**

- PRD §5.1 시나리오 7 `통신 단절`
- PRD §5.1 시나리오 9 `통신 복구`
- PRD §8.2 `오프라인/동기화`

**연관 관계**

- 하나의 `incident`는 여러 개의 `idempotency_record`를 가질 수 있다. (1:N)
- 하나의 `police_phone`은 여러 개의 `idempotency_record`를 만들 수 있다. (1:N)

**주요 컬럼**

- `id`: 멱등성 기록 식별자
- `client_operation_id`: Android outbox 작업 식별자
- `incident_id`: 요청이 속한 사건
- `police_phone_id`: 요청을 보낸 폴리폰
- `idempotency_key`: 중복 요청 판별 key
- `request_body_hash`: 요청 본문 hash
- `request_path`: 요청 API path
- `request_method`: 요청 HTTP method
- `idempotency_status`: 멱등성 처리 상태
- `response_status_code`: replay할 응답 status code
- `response_body_json`: replay할 응답 본문
- `response_body_format_version`: 응답 본문 형식 버전
- `result_entity_type`: 요청 결과 엔티티 종류
- `result_entity_id`: 요청 결과 엔티티 식별자
- `result_entity_status`: 응답 당시 결과 엔티티 상태
- `result_entity_version`: 응답 당시 결과 엔티티 버전
- `client_requested_at`: 클라이언트 요청 생성 시각
- `clock_offset_ms`: 클라이언트와 서버 시각 차이
- `replay_expires_at`: 응답 replay 보장 만료 시각
- `created_at`: 생성 시각
- `updated_at`: 수정 시각

**설명**

`idempotency_record`는 같은 쓰기 요청이 여러 번 서버에 도착해도 중복 생성되지 않도록 하는 서버 기술 테이블이다.

### 오프라인 패키지

#### offline_package_manifest

**PRD 근거**

- PRD §5.1 시나리오 3 `사건 오프라인 패키지 사전 적재`
- PRD §7.6 FR-19 `오프라인 지도`
- PRD §7.6 FR-31 `사건 오프라인 패키지`

**연관 관계**

- 하나의 `incident`는 여러 개의 `offline_package_manifest`를 가진다. (1:N)
- 하나의 `operational_period`는 여러 개의 `offline_package_manifest`의 기준이 될 수 있다. (1:N)
- 하나의 `search_area`는 여러 개의 `offline_package_manifest`의 전체 수색 범위 기준이 될 수 있다. (1:N)
- 하나의 `offline_package_manifest`는 여러 개의 `offline_package_installation`을 가진다. (1:N)

**주요 컬럼**

- `id`: 오프라인 패키지 manifest 식별자
- `incident_id`: 패키지가 속한 사건
- `manifest_version`: 사건 내 패키지 구성 버전
- `operational_period_id`: 패키지 기준 OP
- `overall_search_area_id`: 전체 수색 범위 기준 구역
- `overall_search_area_version`: 전체 수색 범위 버전
- `manifest_hash`: manifest 구성 hash
- `manifest_format_version`: manifest JSON 형식 버전
- `expires_at`: manifest 만료 시각
- `manifest_payload`: 앱이 내려받을 패키지 구성 JSON
- `created_at`: 생성 시각

**설명**

`offline_package_manifest`는 오프라인 패키지 구성표다. 지도 타일뿐 아니라 사건 메타, 실종자 정보, OP, 수색 구역, 초기 마커를 포함할 수 있다.

#### offline_package_installation

**PRD 근거**

- PRD §5.1 시나리오 3 `오프라인 패키지 사전 적재`
- PRD §7.6 FR-31 `적재 진행률, 실패 재시도, 경고 배지`

**연관 관계**

- 하나의 `offline_package_manifest`는 여러 개의 `offline_package_installation`을 가진다. (1:N)
- 하나의 `police_phone`은 여러 개의 `offline_package_installation`을 가진다. (1:N)
- 하나의 `offline_package_installation`은 하나의 `offline_package_manifest`와 하나의 `police_phone`을 연결한다. (N:1, N:1)

**주요 컬럼**

- `id`: 오프라인 패키지 설치 상태 식별자
- `offline_package_manifest_id`: 설치 대상 manifest
- `police_phone_id`: 패키지를 적재하는 폴리폰
- `last_reported_by_account_id`: 마지막 상태 보고 당시 계정
- `status`: 설치 상태
- `total_item_count`: 전체 항목 수
- `completed_item_count`: 완료 항목 수
- `failed_item_count`: 실패 항목 수
- `failed_item_keys`: 실패 항목 key 목록
- `last_error_code`: 마지막 오류 코드
- `last_reported_at`: 마지막 상태 보고 시각
- `version`: 설치 상태 변경 버전
- `created_at`: 생성 시각
- `updated_at`: 수정 시각

**설명**

`offline_package_installation`은 특정 폴리폰이 특정 오프라인 패키지를 얼마나 적재했는지 나타내는 상태 엔티티다.

### 이벤트 전파

#### event_dispatch_job

**PRD 근거**

- PRD §7.5 FR-08 `상황판 수색 현황`
- PRD §7.5 FR-18 `공용 참고 화면`
- PRD §8.3 `알림`

**연관 관계**

- 하나의 `incident`는 여러 개의 `event_dispatch_job`을 가진다. (1:N)
- 하나의 `event_dispatch_job`은 여러 개의 `event_dispatch_target`을 가진다. (1:N)
- 하나의 `event_dispatch_job`은 0개 또는 1개의 `sse_replay_event`를 가진다. (1:0..1)

**주요 컬럼**

- `id`: 이벤트 전파 작업 식별자
- `event_id`: 외부로 전달되는 공개 이벤트 식별자
- `incident_id`: 이벤트가 속한 사건
- `event_type`: 이벤트 종류
- `payload_format_version`: payload 형식 버전
- `payload`: 전파할 이벤트 내용
- `source_entity_type`: 이벤트를 발생시킨 엔티티 종류
- `source_entity_id`: 이벤트를 발생시킨 엔티티 식별자
- `occurred_at`: 이벤트 발생 시각
- `dispatch_status`: 전파 작업 상태
- `created_at`: 생성 시각
- `updated_at`: 수정 시각

**설명**

`event_dispatch_job`은 도메인 변경을 SSE, FCM 같은 대상으로 전파하기 위한 작업이다. 도메인 write와 같은 트랜잭션에서 만들어진다.

#### event_dispatch_target

**PRD 근거**

- PRD §8.3 `알림`
- PRD §7.5 `상황판 실시간 반영`

**연관 관계**

- 하나의 `event_dispatch_job`은 여러 개의 `event_dispatch_target`을 가진다. (1:N)
- 하나의 `event_dispatch_target`은 하나의 `event_dispatch_job`에 속한다. (N:1)

**주요 컬럼**

- `id`: 이벤트 전파 대상 식별자
- `event_dispatch_job_id`: 대상이 속한 이벤트 전파 작업
- `target_type`: 전파 대상 종류
- `target_identifier`: 대상별 식별값
- `is_required`: 필수 전파 대상 여부
- `dispatch_status`: 대상별 전파 상태
- `attempt_count`: 전파 시도 횟수
- `next_retry_at`: 다음 재시도 시각
- `last_error_code`: 마지막 오류 코드
- `last_attempted_at`: 마지막 전파 시도 시각
- `completed_at`: 대상 처리 완료 시각
- `created_at`: 생성 시각
- `updated_at`: 수정 시각

**설명**

`event_dispatch_target`은 하나의 이벤트 전파 작업이 보내야 하는 대상 1건이다. 대상은 SSE 재전송 저장, 실시간 SSE, FCM이다.

#### sse_replay_event

**PRD 근거**

- PRD §7.5 FR-08 `상황판 수색 현황`
- PRD §7.5 FR-18 `공용 참고 화면`
- PRD §8.1 `실시간성`

**연관 관계**

- 하나의 `event_dispatch_job`은 0개 또는 1개의 `sse_replay_event`를 가진다. (1:0..1)
- 하나의 `sse_replay_event`는 하나의 `event_dispatch_job`에서 만들어진다. (1:1)

**주요 컬럼**

- `id`: SSE 재전송 이벤트 식별자
- `event_dispatch_job_id`: 원천 이벤트 전파 작업
- `incident_id`: 재전송 이벤트가 속한 사건
- `replay_sequence`: 사건별 SSE 재전송 순번
- `envelope`: SSE로 다시 보낼 event envelope
- `replay_status`: 재전송 가능 상태
- `purged_at`: 파기 시각
- `created_at`: 생성 시각

**설명**

`sse_replay_event`는 웹 상황판이 재연결했을 때 놓친 이벤트를 다시 받을 수 있도록 저장하는 SSE 재전송 데이터다.

### 파기와 감사

#### incident_data_purge

**PRD 근거**

- PRD §5.1 시나리오 12 `사건 종료`
- PRD §7.6 FR-22 `개인정보 삭제`
- PRD §8.5 `데이터 보존/파기`

**연관 관계**

- 하나의 `incident`는 0개 또는 1개의 `incident_data_purge`를 가진다. (1:0..1)
- 하나의 `incident_data_purge`는 하나의 `incident`에 속한다. (1:1)

**주요 컬럼**

- `id`: 사건 데이터 파기 상태 식별자
- `incident_id`: 파기 대상 사건
- `status`: 파기 진행 상태
- `closed_at`: 사건 종료 시각
- `purge_due_at`: 파기 예정/기한 시각
- `completed_at`: 파기 완료 시각
- `last_error_code`: 마지막 오류 코드
- `created_at`: 생성 시각
- `updated_at`: 수정 시각

**설명**

`incident_data_purge`는 사건 관련 민감 데이터 파기 상태를 추적한다. 사건 자체를 삭제하는 테이블이 아니다.

#### location_data_access_audit

**PRD 근거**

- PRD §8.5 `위치정보 접근 로그 최소 6개월`
- PRD §11.1 `위치정보법/개인정보보호법 검토`

**연관 관계**

- 하나의 `incident`는 여러 개의 `location_data_access_audit`을 가진다. (1:N)
- 하나의 `account`는 여러 개의 `location_data_access_audit`을 남길 수 있다. (1:N)
- 하나의 `police_phone`은 여러 개의 `location_data_access_audit`의 접근 출처가 될 수 있다. (1:N)

**주요 컬럼**

- `id`: 위치 데이터 접근 감사 식별자
- `incident_id`: 접근한 위치 데이터의 사건
- `account_id`: 위치 데이터에 접근한 계정
- `police_phone_id`: 앱 접근에 사용된 폴리폰
- `access_channel`: 접근 경로
- `access_purpose`: 접근 목적
- `accessed_at`: 접근 시각
- `retention_until`: 감사 기록 보관 기한

**설명**

`location_data_access_audit`은 위치 데이터에 누가 접근했는지 감사 목적으로 남기는 기록이다.

## 3. 백엔드 DB가 아닌 Android Room 로컬 엔티티

### android_outbox

**PRD 근거**

- PRD §7.6 FR-28 `미전송 기록 상태`
- PRD §8.2 `오프라인/동기화`

**연관 관계**

- `android_outbox`는 백엔드 PostgreSQL 테이블이 아니다.
- `android_outbox`는 Android Room 로컬 DB에서 `idempotency_record`와 요청 단위로 연결된다.

**주요 컬럼**

- `id`: 로컬 outbox row 식별자
- `idempotency_key`: 서버 중복 처리에 사용할 key
- `request_body_hash`: 요청 본문 hash
- `request_path`: 전송할 API path
- `request_method`: 전송할 HTTP method
- `payload`: 서버로 보낼 요청 내용
- `status`: 로컬 전송 상태
- `retry_count`: 재시도 횟수
- `next_retry_at`: 다음 재시도 예정 시각
- `created_at`: 로컬 생성 시각
- `updated_at`: 로컬 수정 시각

**설명**

`android_outbox`는 앱이 서버로 아직 보내지 못한 쓰기 요청을 로컬에 저장하는 테이블이다.

### android_sync_status

**PRD 근거**

- PRD §7.6 FR-28 `미전송 기록 상태와 동기화 진행 상황`
- PRD §7.6 FR-29 `로컬 경고`

**연관 관계**

- `android_sync_status`는 백엔드 PostgreSQL 테이블이 아니다.
- `android_sync_status`는 Android Room 로컬 DB에서 앱 화면 표시용으로 관리한다.

**주요 컬럼**

- `id`: 로컬 동기화 상태 식별자
- `incident_id`: 동기화 상태를 표시할 사건
- `pending_count`: 미전송 항목 수
- `failed_count`: 실패 항목 수
- `last_synced_at`: 마지막 동기화 성공 시각
- `last_error_code`: 마지막 오류 코드
- `updated_at`: 로컬 상태 갱신 시각

**설명**

`android_sync_status`는 앱 사용자에게 보여줄 동기화 상태 요약이다. 백엔드 Flyway 대상이 아니다.
