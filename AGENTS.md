# Suri-Map Agent Rules

## 범위

저장소 전체 공통 규칙이다. 플랫폼별 세부 규칙은 가까운 `AGENTS.md`를 따른다: `backend/AGENTS.md`, `frontend/AGENTS.md`, `android/AGENTS.md`. 같은 주제에서 충돌하면 더 가까운 디렉터리의 `AGENTS.md`를 우선한다.

## 기준 문서

계약 충돌은 관심사별 단일 출처를 따른다. 기준 문서에 없는 public API, entity, event, error, annotation, board slot, fixture ID는 임의로 만들지 않는다.

| 관심사 | 우선 기준 |
|---|---|
| Public HTTP URL, request/response, error | `docs/api/api-spec.md` |
| Spec/Lane 소유권, entity/event/slot, channel/role matrix | `docs/spec/boundaries.md` |
| 하네스 SC, fixture ID, e2e red test | `docs/spec/harness-scenarios.md` |
| Spec별 owns/provides/consumes 실행 계약 | `docs/spec/specs/*.json` |
| DB 엔티티·관계·컬럼 의미 | `docs/db-design/db-design-readable.md` |
| Lane/Phase, 브랜치, 커밋, MR 규칙 | `docs/tasks/index.md` |

`docs/prd.md`, `docs/architecture.md`, `docs/adr.md`는 배경 문서다. 위 기준과 충돌하면 기준 문서 수정을 먼저 보고한다.

## 공통 원칙

- 기존 패턴과 명명 규칙을 우선한다. 과한 추상화보다 명확한 구현을 택한다.
- 자동 판단 금지 원칙을 지킨다. 확인 누락 확정, 다음 구역 추천, 위험도 판단 API/배치/UI 문구를 추가하지 않는다.
- Android는 현장 입력, Web은 지휘 상황판이다. 앱 전용 write와 웹 전용 command를 섞지 않는다.
- 사건은 사용자가 직접 생성하지 않는다. MVP 사건은 mock/seed import 결과를 소비한다.
- 사건 종료는 terminal이다. 사용자 재오픈 UI/API를 전제하지 않는다.

## API 규칙

| 항목 | 규칙 |
|---|---|
| JSON API prefix | `/api` |
| Tileserver prefix | `/tiles` |
| URL 형식 | kebab-case, canonical URL은 `docs/api/api-spec.md` 기준 |
| Channel header | `X-Client-Channel: APP`, `WEB`, 또는 서버 내부 호출용 `INTERNAL` |
| Idempotency | domain write, outbox replay, lifecycle write는 `Idempotency-Key` 필요 |
| Error body | 기본형 `{ "error": "incident_closed" }` |

## 자주 틀리는 용어

상세 용어는 `docs/db-design/db-design-readable.md`와 `docs/api/api-spec.md`를 따른다.

| 쓰지 않음 | 사용 |
|---|---|
| `Device` | `PolicePhone` |
| `TeamDevice`, `PatrolCarDevice` | `PolicePhone` + account/team/patrol car context |
| `MapBoundary`, `map_boundary` | `search_area.area_level = OVERALL` |
| `MapBoundaryQuery` | `SearchAreaQuery.overallOf` |
| `MissingPersonCache` | `missing_person` |
| `SearchSession` 남용 | 의미에 맞게 `SearchPath`, `OperationalPeriod`, `DutyShift` |
| `presigned upload URL` | `presigned URL for upload` 또는 `uploadUrl` |

## Lane 경계

- 다른 Lane의 entity, API, event 정의 파일은 `docs/spec/boundaries.md §1.2`의 Spec/Lane owner LGTM 없이 수정하지 않는다.
- `/CODEOWNERS`가 없거나 미확정이면 `docs/spec/boundaries.md §1.2`를 owner 기준으로 삼는다.
- API path, fixture ID, schema field, event/API payload 이름은 `docs/api/api-spec.md`, `docs/spec/harness-scenarios.md §6`, `docs/spec/specs/*.json` 그대로 사용한다. 임의 변경, 축약, 재명명 금지.

## Persistence

- Backend persistence는 MyBatis 단일 기준이다. JPA, Hibernate, Spring Data JPA를 새로 도입하지 않는다.
- PostGIS geometry, idempotency, event dispatch, board query는 명시적 SQL과 mapper 경계로 다룬다.
- 예외가 필요하면 새 ADR과 팀 합의가 먼저 필요하다.

## 검증

- Backend: `cd backend && ./gradlew test`
- Frontend: `cd frontend && npm run typecheck && npm run build`
- Android: `cd android && ./gradlew :app:assembleDebug`; 테스트 추가 후 `./gradlew test`

## Git / Jira / MR

브랜치, 커밋 메시지(`[Area] type[(scope)]: 한글 요약 (<JIRA-KEY>)` 또는 area 생략형), MR 제목·설명, area tag, scope 원칙은 `docs/tasks/index.md`를 따른다.

## 자동화 가드레일

컨벤션 위반 검증은 로컬 lint/format/test 명령, CODEOWNERS, MR template, agent hook이 담당한다. GitLab CI는 runner가 안정화될 때까지 사용하지 않는다.

## graphify

이 프로젝트는 `graphify-out/`에 지식 그래프를 둔다. 그래프에는 주요 노드, 커뮤니티 구조, 파일 간 관계가 포함된다.

규칙:
- `graphify-out/GRAPH_REPORT.md`가 있으면 소스 파일을 읽거나 `rg`/glob 검색을 실행하거나 코드베이스 질문에 답하기 전에 먼저 확인한다. 그래프는 코드베이스를 파악하는 1차 지도다.
- `graphify-out/GRAPH_REPORT.md`가 없거나 현재 커밋보다 오래됐으면 `graphify update .`로 생성 또는 갱신한 뒤 사용한다.
- `graphify-out/wiki/index.md`가 있으면 원본 파일을 바로 읽기 전에 wiki를 먼저 탐색한다.
- 여러 모듈에 걸친 "X와 Y가 어떻게 연결되는가" 유형의 질문은 `rg`보다 `graphify query "<질문>"`, `graphify path "<A>" "<B>"`, `graphify explain "<개념>"`을 우선한다. 이 명령은 그래프의 EXTRACTED/INFERRED edge를 따라 탐색한다.
- 코드를 수정한 뒤에는 `graphify update .`를 실행해 그래프를 최신 상태로 맞춘다. 이 업데이트는 AST 기반이며 API 비용이 없다.
