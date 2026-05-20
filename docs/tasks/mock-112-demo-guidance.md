# mock 112 시연/구현 가이드

상태: 초안. 목적은 mock 112 담당자가 구현 범위와 금지선을 빠르게 이해하도록 돕는 것이다. 이 문서는 `docs/spec`, `docs/api/api-spec.md`, `docs/db-design`의 기존 계약을 바꾸지 않는다.

## 결론

`mock 112에 사건 등록 -> Suri-Map이 polling으로 감지 -> 웹에서 배정 사건 가져오기 -> OP1 생성` 흐름은 시연 가치가 있다.

다만 mock 112는 Suri-Map 내부 기능이 아니라 외부 원천 시스템 시뮬레이터로 다뤄야 한다. Suri-Map에서 사건을 직접 생성하는 UI/API처럼 보이면 기존 spec과 충돌한다.

에이전트 한 명에게 독립 작업으로 맡긴다면 **별도 mock 112 서버**가 기본안이다. 기존 Suri-Map backend 안에 dev endpoint를 추가하는 방식은 빠르지만, 제품 API와 외부 원천 시스템의 경계가 흐려질 위험이 크다.

별도 서버로 간다면 mock 112 조작 화면도 Suri-Map 웹 안에 넣기보다 mock 112 쪽에 두는 것이 맞다. 다만 별도 React/Vite 프론트 프로젝트까지 만들 필요는 낮다. 1차 권장은 mock 112 서버가 작은 admin HTML 화면을 함께 제공하는 방식이다.

배포 public route로 `/mock-112/`를 열 때는 mock 112 서버 자체에 Keycloak OAuth2 login을 붙인다. Suri-Map backend가 mock 112 UI/API를 프록시하지 않고, frontend nginx는 `/mock-112/`를 별도 mock 112 서버로 전달만 한다. `/mock-112/health`는 컨테이너 healthcheck와 배포 smoke를 위해 공개할 수 있지만, seed/reset/배정 변경 API는 로그인 세션이 필요해야 한다.

## 기준 문서

- `docs/spec/boundaries.md`
  - 사건은 사용자가 직접 만들지 않는다.
  - MVP/시연은 mock 112 배정 사건을 가져온다.
  - 지원 부대 배정은 Suri-Map public/admin write API가 아니라 112/mock polling/import로 반영한다.
- `docs/api/api-spec.md`
  - 사건 가져오기 canonical API는 `POST /api/incidents/import`다.
  - request body는 `sourceIncidentId`다.
  - `incident-imports` 같은 별도 resource를 만들지 않는다.
  - 내부 배정 생성 API는 만들지 않는다.
- `docs/db-design`
  - `incident.source_incident_id`는 mock 112 원천 사건 ID다.
  - `incident_assignment`는 112/mock에서 받은 사건-계정 배정 결과다.

## 권장 구현 방식

### 기본안: 독립 mock 112 서버

mock 112를 별도 프로세스 또는 별도 container로 띄운다.

- 예: `mock-112`
- Suri-Map backend는 `MOCK_112_BASE_URL`로 mock 112를 호출한다.
- mock 112는 Suri-Map DB에 직접 접근하지 않는다.
- Suri-Map은 HTTP polling/import adapter로만 mock 112 데이터를 가져온다.
- mock 112 조작 화면이 필요하면 mock 112 서버가 자체 admin 화면을 서빙한다.
- 배포 public route로 노출되는 조작 화면과 상태 변경 API는 Keycloak 로그인 뒤에 둔다.
- mock 112가 Suri-Map webhook을 사용한다면 전송 실패를 로그로만 삼키지 않고 outbox에 남겨 재시도와 상태 확인이 가능해야 한다.

Spring Boot로 만들어도 되고, Node/Express 같은 가벼운 서버로 만들어도 된다. 팀이 Spring Boot에 익숙하고 백엔드 에이전트가 맡는다면 별도 Spring Boot mock 서버가 가장 설명하기 쉽다.

## mock 112 화면 판단

### 권장: mock 112 서버 내장 admin 화면

별도 mock 112 서버가 아래 화면을 직접 제공한다.

- 사건 등록
- 등록 사건 목록
- sourceIncidentId 복사
- 인증 서버 fixture 기준 배정 가능 조직/계정 조회
- 실종팀 인계 버튼
- 지원 부대 배정 추가 버튼
- reset/seed scenario 실행 버튼
- polling 대상 상태 확인
- webhook outbox pending/failed 상태 확인

이 방식이 가장 균형이 좋다.

- 외부 시스템처럼 보인다.
- Suri-Map 제품 화면과 섞이지 않는다.
- 서버 한 개만 띄우면 API와 조작 화면이 같이 뜬다.
- 별도 프론트 빌드/라우팅/배포 부담이 없다.

예:

- `GET /` 또는 `GET /admin`: mock 112 admin 화면
- `POST /mock-112/incidents`: 사건 등록
- `GET /mock-112/incidents?status=READY`: Suri-Map polling 대상 조회
- `POST /mock-112/incidents/{sourceIncidentId}/assignments`: 인계/지원 배정 추가
- `GET /mock-112/assignable-organizations`: 배정 가능 조직과 소속 계정 조회
- `POST /mock-112/incidents/{sourceIncidentId}/assignment-organizations`: 조직 단위 배정 추가
- `POST /mock-112/scenarios/precinct-first`: 시연용 대표 사건 seed
- `POST /mock-112/reset`: 시연 상태 초기화
- `GET /mock-112/health`: 사건 상태와 webhook outbox pending/failed 카운트 확인

### 가능하지만 과함: 별도 mock 112 프론트 프로젝트

React/Vite 같은 별도 프론트 프로젝트를 만들 수도 있지만 1차 작업으로는 과하다.

- 프론트 빌드와 배포 단위가 늘어난다.
- 시연용 화면인데 디자인/상태관리/라우팅 작업이 커질 수 있다.
- Suri-Map 화면 설계 작업과 별도 디자인 이슈가 생긴다.

mock 112 화면이 여러 페이지, 다중 사용자, 복잡한 검색/수정/감사 로그까지 필요해질 때만 고려한다.

### 비권장: Suri-Map 웹 안에 mock 112 조작 화면 삽입

Suri-Map 웹 안에 mock 112 사건 등록 UI를 넣으면 시연자는 편하지만 제품 경계가 흐려진다.

- 사용자가 Suri-Map에서 사건을 직접 만든다는 오해가 생긴다.
- `docs/api/api-spec.md`에 없는 후보 목록/등록 product API가 생긴 것처럼 보일 수 있다.
- 실제 운영 범위와 시연 보조 도구가 섞인다.

정말 필요하면 `dev/demo only` feature flag 아래에 두고, product route와 명확히 분리한다.

### 대안: Suri-Map backend 내부 dev/demo 모듈

시간이 부족할 때만 선택한다.

- `/mock-112/**` 같은 dev/demo endpoint로 제한한다.
- product API로 문서화하지 않는다.
- 운영 profile에서는 비활성화한다.
- 실제 import는 여전히 `POST /api/incidents/import`만 사용한다.

이 대안은 빠르지만, “Suri-Map이 사건을 직접 만든다”는 오해를 만들 수 있으므로 1차 권장안은 아니다.

## 역할 분리

### mock 112

외부 원천 시스템 역할이다.

- 배정 사건을 등록한다.
- 실종자 기본 정보를 가진다.
- 마지막 목격 위치, 신고자 진술 위치 등 초기 기준점을 제공한다.
- 지구대/파출소, 실종팀, 지원 부대 배정 정보를 제공한다.
- 같은 `sourceIncidentId` 기준으로 인계/지원 배정 갱신을 제공한다.

### Suri-Map

수색 운영 시스템 역할이다.

- mock 112 원천 사건을 polling 또는 import adapter로 조회한다.
- 웹 지휘 계정이 `POST /api/incidents/import`를 호출하면 내부 사건으로 가져온다.
- import 성공 시 `incident`, `missing_person`, `incident_assignment`, OP1을 같은 트랜잭션에서 생성한다.
- 이후 mock 112 배정 갱신은 `incident_assignment` 추가/갱신으로 반영한다.

## 권장 시연 흐름

1. mock 112 화면에서 배정 사건을 등록한다.
   - 일반 등록 화면은 `sourceIncidentId`를 입력받지 않는다. mock 112 서버가 UUID 원천 ID와 표시용 사건번호를 만든다.
   - 사용자는 초기 배정 조직을 선택한다. 예를 들어 수완지구대를 선택하면 해당 조직의 fixture 계정이 일괄 배정된다.
2. Suri-Map backend가 mock 112를 짧은 주기로 polling한다.
   - webhook이 활성화된 환경에서는 mock 112가 사건/배정 이벤트를 outbox에 저장하고 즉시 전송을 시도한다.
   - 전송 실패 시 outbox `PENDING` 상태로 남기고 재시도한다. 시연자는 health의 pending/failed 카운트로 누락 가능성을 확인한다.
3. Suri-Map 웹에서 새 배정 사건 도착 상태를 확인한다.
4. 지휘 계정이 배정 사건 가져오기를 누른다.
5. Suri-Map이 `POST /api/incidents/import`에 `sourceIncidentId`를 보내 내부 사건으로 가져온다.
6. 사건 목록에 `OPEN` 사건이 생긴다.
7. OP1이 자동 생성되고, 초기 배정 계정이 사건을 조회할 수 있다.
8. mock 112에서 실종팀 인계 또는 지원 부대 배정을 추가한다.
9. Suri-Map polling/import가 같은 사건의 `incident_assignment`를 추가 반영한다.
10. 웹 상황판에서 인계 완료/지원 부대 합류 상태를 확인한다.

## 최소 산출물

### mock 112 쪽

- 사건 등록 API
- mock 112 admin 화면
  - 별도 프론트 프로젝트가 아니라 mock 112 서버 내장 화면을 기본으로 한다.
  - 사람 입력 필드는 제목, 실종자 기본 정보, 초기 배정 조직 중심으로 둔다.
  - `sourceIncidentId`, `externalAssignmentKey`, 개별 계정 payload는 서버가 생성한다.
- mock 112 사건 저장소
  - JSON seed, in-memory store, test DB 중 하나
- source incident 조회 API
  - 예: `GET /mock-112/incidents?status=READY`
  - 예: `GET /mock-112/incidents/{sourceIncidentId}`
- 인계/지원 배정 갱신 API
  - 예: `POST /mock-112/incidents/{sourceIncidentId}/assignments`
  - 운영자 화면은 개별 assignment payload 대신 조직 단위 배정 API를 우선 사용한다.
- webhook outbox
  - eventId, eventType, sourceIncidentId, payload, status, attemptCount, nextAttemptAt, lastError를 저장한다.
  - status는 `PENDING`, `SENT`, `FAILED` 중 하나로 수렴한다.
  - 등록/배정 API 응답은 이번 요청의 webhook delivery 요약을 포함해 전송 누락을 숨기지 않는다.
- 배정 가능 조직 조회 API
  - Keycloak realm fixture의 `accountCode`, `organizationCode`, `organizationName`, `displayName`, `accountType` projection을 사용한다.
- 고정 seed 데이터
  - `sourceIncidentId`
  - 실종자 정보
  - 초기 지구대/파출소 배정
  - 실종팀 인계 배정
  - 지원 부대 배정
  - 초기 기준점 마커 후보

### mock 112 payload 기준

mock 112 payload는 Suri-Map DB 설계로 무리 없이 변환 가능해야 한다.

아래 JSON은 `docs/spec/fixtures/common-fixtures.json`의 `confirmed.mock112SourceContract`로 승격된 canonical mock 112 fixture payload다. 제품 API에 사건 생성 기능을 추가한다는 뜻이 아니며, Suri-Map은 이 원천 사건을 `POST /api/incidents/import`의 `sourceIncidentId`로 가져온다.

모든 예시는 합성 데이터만 사용한다. 실제 신고자, 실종자, 연락처, 주민등록번호, 실제 얼굴 사진, 실제 사건 기록을 fixture나 로그에 넣지 않는다. `photoObjectKey`도 실제 사진이 아니라 mock object key 또는 placeholder asset을 가리켜야 한다.

```json
{
  "sourceIncidentId": "00000000-0000-0000-0000-000000000001",
  "title": "광주 무등산 실종 신고",
  "openedAt": "2026-04-28T09:00:00+09:00",
  "missingPerson": {
    "displayName": "가상 실종자 001",
    "photoObjectKey": "mock-112/missing-person/mock-112-incident-001.jpg",
    "appearanceText": "남색 점퍼, 회색 등산화",
    "lastSeenLocationText": "무등산 서측 탐방로 입구",
    "lastSeenAt": "2026-04-28T08:30:00+09:00"
  },
  "assignments": [
    {
      "externalAssignmentKey": "00000000-0000-0000-0000-000000000001:precinct-cmd",
      "accountId": "acct-precinct-cmd",
      "incidentRole": "FIELD_COMMANDER",
      "assignedAt": "2026-04-28T09:00:00+09:00"
    },
    {
      "externalAssignmentKey": "00000000-0000-0000-0000-000000000001:precinct-car",
      "accountId": "acct-precinct-car",
      "incidentRole": "MEMBER",
      "assignedAt": "2026-04-28T09:00:00+09:00"
    },
    {
      "externalAssignmentKey": "00000000-0000-0000-0000-000000000001:precinct-team",
      "accountId": "acct-precinct-team",
      "incidentRole": "MEMBER",
      "assignedAt": "2026-04-28T09:00:00+09:00"
    }
  ],
  "seedMarkers": [
    {
      "type": "CLUE",
      "source": "MOCK_SEED",
      "memo": "신고자 진술 위치",
      "lon": 126.9134,
      "lat": 35.1631
    }
  ]
}
```

매핑 기준:

- `sourceIncidentId` -> `incident.source_incident_id`
- `title` -> `incident.title`
- `missingPerson.*` -> `missing_person.*`
- `assignments[].accountId` -> `incident_assignment.account_id`
- `assignments[].incidentRole` -> `incident_assignment.incident_role`
- `assignments[].assignedAt` -> `incident_assignment.assigned_at`
- `seedMarkers` -> S5 `ReferenceMarkerSeed.createForIncident(...)` 입력

`accountId`는 S1-2 계정 fixture에 존재하는 값이어야 한다. mock 112 payload에 개인 사용자 식별자, 개인 생성자, 자체 조직 마스터를 새로 넣지 않는다.

### Suri-Map 연동 쪽

- `ExternalIncidentAdapter` 또는 동등한 mock 112 client
- mock 112 polling job
- import 후보 감지 상태
- 기존 `POST /api/incidents/import` 호출 연결
- assignment polling/import handler
- 중복 import 방지
- import 실패 시 rollback 확인

## 중복 처리 기준

### 사건 import

`sourceIncidentId`는 mock 112 원천 사건의 유일 키다. Suri-Map DB는 `incident.source_incident_id`를 unique로 가진다.

따라서 같은 `sourceIncidentId`를 다시 가져오면 다음 중 하나로 수렴해야 한다.

- 같은 idempotency key와 같은 body면 기존 응답 replay
- 같은 idempotency key와 다른 body면 `idempotency_mismatch`
- 같은 `sourceIncidentId` 재호출이면 기존 사건 재사용 또는 `write_conflict`

사건, OP1, `incident_assignment`, 초기 마커가 중복 생성되면 안 된다.

### 인계/지원 배정 webhook

`docs/spec/harness-scenarios.md`는 같은 112 assignment key 기준으로 `incident_assignment`가 중복 생성되지 않아야 한다고 본다.

현재 DB 설계에는 `incident_assignment.external_assignment_key` 컬럼이 없다. 대신 `incident_id + account_id` active row unique 기준이 있다.

따라서 1차 구현은 다음 중 하나로 제한한다.

1. `incident_id + account_id + revoked_at IS NULL` 기준으로 중복 upsert
2. adapter 내부 processed key store로 `externalAssignmentKey`를 추적

DB에 `external_assignment_key` 컬럼을 추가하려면 `docs/db-design`과 S1-1 spec 변경 승인이 먼저 필요하다.

## 중요한 제약

### 하지 말 것

- Suri-Map에 사건 직접 생성 UI를 만들지 않는다.
- Suri-Map product API로 지원 부대 배정 생성/수정 API를 만들지 않는다.
- `/api/incident-imports` 같은 새 canonical resource를 만들지 않는다.
- mock 112가 Suri-Map DB에 직접 쓰지 않는다.
- WEB/APP 채널이 mock 112 webhook API를 직접 호출하도록 만들지 않는다.
- `sourceIncidentId`와 Suri-Map 내부 `incidentId`를 혼용하지 않는다.
- 개인 사용자 식별자를 `incident_assignment` 기준으로 쓰지 않는다.
- 실제 개인정보나 실제 사건 자료를 mock 112 seed, fixture, 로그에 넣지 않는다.

### 주의할 점

Suri-Map 웹에서 “새 배정 사건 도착”을 즉시 보여주려면 후보 목록 API가 아니라 계정/조직 단위 refetch signal이 필요하다. 현재 기준은 `GET /api/incidents/events` SSE가 신규 사건/배정 변경을 알리고, Web은 `/api/incidents`를 refetch해 최종 상태를 표시하는 것이다.

따라서 1차 구현은 다음으로 제한하는 것이 안전하다.

1. **webhook 자동 import**
   - mock 112 `INCIDENT_READY`는 `POST /api/internal/mock-112/events`로 들어와 자동 import된다.
   - 같은 `eventId` 또는 같은 `sourceIncidentId` 재전송은 중복 row 없이 수렴한다.
   - 기존 `POST /api/incidents/import`는 운영 보조/재처리 fallback으로 둔다.

2. **Web refetch signal**
   - Web은 `GET /api/incidents/events` SSE를 구독한다.
   - SSE payload는 PII 없는 refetch signal이며, UI source of truth는 `/api/incidents` 응답이다.

3. **Android FCM + REST recovery**
   - Android는 SSE를 만들지 않는다.
   - 신규 배정은 FCM data message로 알리고, 앱은 `/api/incidents` refetch로 최종 상태를 복구한다.

## PASS 기준

- mock 112에 등록한 사건이 Suri-Map에서 import 가능해야 한다.
- mock 112 서버가 Suri-Map DB에 직접 쓰지 않아야 한다.
- 기본 import는 `POST /api/internal/mock-112/events` `INCIDENT_READY` webhook으로 자동 수행되어야 한다.
- webhook import 성공 시 `incident`, `missing_person`, `incident_assignment`, OP1이 생성되어야 한다.
- 같은 `sourceIncidentId`를 다시 import해도 사건, OP, assignment가 중복 생성되지 않아야 한다.
- OP1 생성 실패를 주입하면 전체 import가 rollback되어야 한다.
- 지원 부대 배정은 Suri-Map 직접 API가 아니라 mock 112 webhook으로만 반영되어야 한다.
- 지원 부대 배정 반영 후 `INCIDENT_ASSIGNMENT_CHANGED`가 발행되어야 한다.
- 같은 `externalAssignmentKey` 또는 같은 active `incident_id + account_id` 배정이 중복 반영되지 않아야 한다.
- 웹/앱에는 지원 부대 배정 write CTA가 노출되지 않아야 한다.

## 대원에게 전달할 한 문장

mock 112는 Suri-Map 안에서 사건을 만드는 기능이 아니라, 외부 112 시스템처럼 사건과 배정 변경을 흘려주는 시연용 원천 시스템이다. Suri-Map은 `POST /api/internal/mock-112/events` webhook으로 원천 사건을 수신하고, 내부 사건 생성은 `sourceIncidentId` 기준의 자동 import use case로만 처리해야 한다.
