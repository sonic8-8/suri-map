# L1 Precinct-First Fixture Catalog

> **Canonical 기준**: [`docs/spec/harness-scenarios.md §6`](../../harness-scenarios.md). 본 파일은 §6의 fixture ID와 `mock-112/src/main/resources/seed/precinct-first-scenario.json`이 어떻게 매핑되는지 정리한 **mock-112 매핑 카탈로그**다. ID 명세 권위는 §6에 있고 이 문서는 §6에 없는 ID를 새로 만들지 않는다.

## 0. 사용 범위

- L1-B01 산출물. 모든 Lane(L1~L6)이 SC-01/02/10/12 fixture를 같은 ID로 소비할 수 있게 한다.
- 본 카탈로그가 가리키는 mock-112 seed가 변경되면 §6과 일관되게 유지하는 책임은 L1 Lane owner에게 있다.
- §6 자체를 수정하려면 `.agents/scripts/check-fixture-contract.py` 가드와 owner LGTM 경로를 따른다 (`AGENTS.md`, `docs/tasks/review-guide.md` 참조).

## 1. 사건 / 실종자

| 항목 | mock-112 source | Suri-Map internal canonical (§6) |
|---|---|---|
| Source incident ID | `00000000-0000-0000-0000-000000000001` | DB `incident.source_incident_id` UUID |
| Suri-Map internal `incident.id` | (import 시 결정) | `inc-precinct-first-001` |
| Active OP | (import 시 자동 생성) | `op-precinct-001-op1` |
| Team alias (지구대/파출소) | — | `team-precinct-jongno` |
| 실종자 | `가상 실종자 001` / 인왕산 북측 산책로 입구 | (개인정보, fixture ID 없음) |

> mock-112가 발급하는 식별자는 `sourceIncidentId`까지이며, 내부 ID(`inc-*`, `op-*`, `ia-*` 등)는 import 흐름이 deterministic하게 만들어 §6과 일치시킨다. 매핑 책임은 L1-T01(import API) 구현 단계에서 진다.

## 2. 계정 / PolicePhone

§6은 `COMMANDER`, `PATROL`, `TEAM`을 fixture alias로만 사용하며 실제 enum이 아니라고 명시한다. 실제 `account_type`은 `COMMAND` / `TEAM` / `PATROL_CAR`다. mock-112 `incidentRole`은 `INCIDENT_COMMANDER` / `FIELD_COMMANDER` / `MEMBER`이고, 내부 권한 role은 `MISSING_TEAM_COMMANDER` / `FIELD_COMMANDER` / `MEMBER`다.

아래 `accountCode=acct-*` 값은 mock-112/fixture 계정 코드다. 내부 `account.id`와 `incident_assignment.account_id` FK는 UUID이며, import 단계에서 S1-2 account fixture UUID로 매핑한다. `policePhoneAlias=dev-*`도 하네스 별칭이며 public contract field `policePhoneId`는 UUID다.

| 그룹 | accountCode | account_type alias | mock-112 incidentRole | policePhoneAlias |
|---|---|---|---|---|
| 지구대/파출소 | `acct-precinct-cmd` | COMMANDER / COMMAND | `FIELD_COMMANDER` | `dev-precinct-cmd-phone-01` |
| 지구대/파출소 | `acct-precinct-car` | PATROL / PATROL_CAR | `MEMBER` | `dev-precinct-car-01` |
| 지구대/파출소 | `acct-precinct-team` | TEAM / TEAM | `MEMBER` | `dev-precinct-phone-01` |
| 실종팀 인계 | `acct-cmd-alpha` | COMMANDER / COMMAND | `INCIDENT_COMMANDER` | `dev-alpha-cmd-phone-01` |
| 실종팀 인계 | `acct-team-alpha` | TEAM / TEAM | `MEMBER` | `dev-alpha-phone-01` |
| 지원 부대 | `acct-support-cmd` | COMMANDER / COMMAND | `FIELD_COMMANDER` | `dev-support-cmd-phone-01` |
| 지원 부대 | `acct-support-car` | PATROL / PATROL_CAR | `MEMBER` | `dev-support-car-01` |
| 지원 부대 | `acct-support-team` | TEAM / TEAM | `MEMBER` | `dev-support-phone-01` |

지휘 계정 policePhoneAlias(`dev-*-cmd-phone-01`)는 웹 지휘·`incident_assignment` fixture 식별자로만 쓰며 Android 앱 FCM recipient로 고정하지 않는다.

## 3. mock-112 → Suri-Map incident_assignment 매핑

mock-112 seed의 `externalAssignmentKey` ↔ Suri-Map `incident_assignment.id` (canonical row ID)는 import 흐름에서 deterministic하게 매핑된다.

| externalAssignmentKey (mock-112) | accountCode | 기대 `incident_assignment.id` | 단계 |
|---|---|---|---|
| `00000000-0000-0000-0000-000000000001:precinct-cmd` | `acct-precinct-cmd` | `ia-precinct-cmd-001` | 초동 (import 직후 ACTIVE) |
| `00000000-0000-0000-0000-000000000001:precinct-car` | `acct-precinct-car` | `ia-precinct-car-001` | 초동 (import 직후 ACTIVE) |
| `00000000-0000-0000-0000-000000000001:precinct-team` | `acct-precinct-team` | `ia-precinct-team-001` | 초동 (import 직후 ACTIVE) |
| `00000000-0000-0000-0000-000000000001:cmd-alpha` | `acct-cmd-alpha` | `ia-precinct-alpha-cmd-001` | 실종팀 인계 (polling/import 후 추가) |
| `00000000-0000-0000-0000-000000000001:team-alpha` | `acct-team-alpha` | `ia-precinct-alpha-team-001` | 실종팀 인계 (polling/import 후 추가) |
| `00000000-0000-0000-0000-000000000001:support-cmd` | `acct-support-cmd` | `ia-precinct-support-cmd-001` | 지원 배정 (polling/import 후 추가) |
| `00000000-0000-0000-0000-000000000001:support-car` | `acct-support-car` | `ia-precinct-support-car-001` | 지원 배정 (polling/import 후 추가) |
| `00000000-0000-0000-0000-000000000001:support-team` | `acct-support-team` | `ia-precinct-support-team-001` | 지원 배정 (polling/import 후 추가) |

모든 기대 row의 `revokedAt`은 null이며, 실종팀 인계·지원 배정 후에도 기존 row는 유지된다 (§6).

## 4. Seed Markers

mock-112 seed JSON `seedMarkers[]`는 외부 112 원천 위치 정보만 가진다. `source`는 S5 `marker_source` enum 기준
`MOCK_SEED`를 사용한다. Suri-Map 내부 canonical marker ID(`mk-*`)는
mock-112에 넣지 않고, L1 import/fixture layer가 원천 사건 ID와 seed marker 순서·내용을 기준으로 §6 canonical ID에 매핑한다.
`seedMarkers[]`는 배열이므로 다른 mock 사건에서는 N개가 될 수 있지만, 대표 `precinct-first-scenario`는 현재 1개만 가진다.

| index | canonical marker ID (§6) | mock-112 memo | 위치 (lon, lat) | type | source | 비고 |
|---|---|---|---|---|---|---|
| 0 | `mk-precinct-clue-001` | 신고자 진술 위치 | (126.9565, 37.5712) | CLUE | MOCK_SEED | 초기 기준 마커 (신고자 진술) |

S5 소유의 SC-06/08용 marker fixture (`mk-precinct-support-001`, `mk-precinct-person-found-001`, `mk-precinct-op-mismatch-001`)는 import seed marker가 아니라 현장 마커/알림 시나리오용이며 본 카탈로그 범위 밖이다 (`docs/spec/specs/S5.json` 참조).

## 5. Path / Memo seed ID

§6 명시 ID. 실제 row 생성은 L4(path, S3-1) / L3(memo, S8) 구현 단계에서 일어나며, 본 카탈로그는 ID 추적용으로만 둔다.

| 종류 | canonical ID | 비고 |
|---|---|---|
| Path (차량) | `path-precinct-car-001` | 순찰차 PolicePhone OP1 차량 구간 경로 |
| Path (도보) | `path-precinct-foot-001` | 팀 PolicePhone OP1 도보 구간 경로 |
| Handover memo | `memo-precinct-handover-001` | OP 전환·인수인계 시 사용 |

## 6. FCM recipient (mock)

| recipient | 대응 PolicePhone |
|---|---|
| `fcm:dev-alpha-phone-01` | `dev-alpha-phone-01` |
| `fcm:dev-support-car-01` | `dev-support-car-01` |
| `fcm:dev-support-phone-01` | `dev-support-phone-01` |

지휘 계정 policePhoneAlias는 Android FCM recipient로 고정하지 않는다 (§6).

## 7. 변경 / 확장 절차

1. §6에 새 fixture ID 추가가 필요하면 먼저 영향 Lane owner LGTM을 받고 `ALLOW_FIXTURE_ID_CHANGE=1`로 `check-fixture-contract.py` 통과 후 §6 수정.
2. §6이 갱신되면 본 카탈로그를 같은 MR에서 동기화한다.
3. mock-112 seed JSON 또는 schema 변경 시 본 카탈로그의 §1~§4 매핑이 깨지지 않는지 검증한다 (`mock-112` 모듈 unit test로 권장).
