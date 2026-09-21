# Suri-Map Agent Rules

## 범위

저장소 전체 공통 규칙이다. 플랫폼별 세부 규칙은 가까운 `AGENTS.md`를 따른다: `backend/AGENTS.md`, `frontend/AGENTS.md`, `android/AGENTS.md`. 같은 주제에서 충돌하면 더 가까운 디렉터리의 `AGENTS.md`를 우선한다.

## 작업 판단 기준

- 현재 동작은 코드·SQL·설정과 실행 결과로 확인한다. 테스트도 어떤 동작을 실제로 검증하는지 읽는다.
- 구현할 동작은 사용자와 합의한 요구사항을 기준으로 정한다. 기존 코드가 곧 올바른 요구사항은 아니다.
- 코드·문서·요구사항이 다르면 현재 동작과 원하는 동작을 구분해 보고한다. 어느 한쪽에 자동으로 맞추지 않는다.
- 작업은 합의한 동작을 구현·검증하고 사용자 피드백으로 다음 범위를 정하는 순서로 진행한다. 문서에 적혔다는 이유로 구현 완료를 판단하지 않는다.

## 문서 사용

- 문서를 찾거나 새 문서의 위치를 정할 때는 `docs/index.md`에서 읽는 목적에 맞는 자료를 선택한다. 프로젝트 소개용 루트 README와 문서 목차는 분리한다.
- 제품 목적·핵심 기능·규칙은 `docs/prd.md`에서 확인한다. 과거 절·FR 번호를 대조할 때는 해당 문서의 원문 링크를 따른다.
- 구성 요소·데이터 흐름·배포 설정의 위치를 찾을 때 `docs/architecture.md`를 확인하고 연결된 코드·설정과 대조한다. 이 요약은 실제 서버의 배포·검증 완료 기록이 아니다.
- 구조의 선택 이유나 과거 ADR 번호를 찾을 때 `docs/adr/README.md`를 확인한다. 과거 계획은 각 문서의 상태와 원문 출처를 확인하고 현재 구현·작업 절차와 구분한다.
- 화면의 설계 이유·옛 수치·권한 가정·시안·실기기 QA를 찾을 때 `docs/screen-design/README.md`에서 해당 자료를 선택한다. 시안의 가짜 상태와 디버그 화면 표시를 실제 API·동기화·현장 사용성 검증으로 대신하지 않는다.
- 기능을 연결해 검증하거나 과거 시연 기록·도구·SC·경계 문서의 출처를 찾을 때 `docs/guides/feature-verification.md`를 확인한다. 확인 항목은 통과 기록이 아니며 옛 도구는 실행 환경·부작용부터 확인한다.
- 공용 테스트 입력·로더를 바꿀 때 `test-fixtures/README.md`를 확인하고 Backend·Android 소비 테스트를 함께 검증한다. JSON의 옛 Spec/Lane 표기는 현재 작업 소유권이 아니다.
- SSE·FCM의 재연결·실패 복구·사건 종료 처리를 바꿀 때는 `docs/features/event-delivery.md`에서 기존 요구와 현재 구현의 차이를 확인한다.
- 마커·사진의 재시도·삭제·수색 차수 충돌 처리를 바꿀 때는 `docs/features/marker-photo.md`에서 기존 요구와 현재 구현의 차이를 확인한다.
- 앱의 미전송 기록·중복 요청·사건 종료 후 정리를 바꿀 때는 `docs/features/offline-sync.md`에서 기존 요구와 현재 구현의 차이를 확인한다.
- 오프라인 패키지의 다운로드·준비 완료·갱신·파기를 바꿀 때는 `docs/features/offline-package.md`에서 기존 요구와 현재 구현의 차이를 확인한다.
- 수색 차수·근무 교대·인수인계 메모·수색 이력 요약을 바꿀 때는 `docs/features/handover.md`에서 기존 요구와 현재 구현·테스트 입력의 차이를 확인한다.
- 사건 가져오기·배정·종료를 바꿀 때는 `docs/features/incident-lifecycle.md`에서 원천 시스템과의 역할 분담, 접근 범위, 종료 후 개인정보 제거의 기존 요구와 구현 차이를 확인한다.
- 로그인·채널·사건 접근·업무폰 최신성·FCM 토큰을 바꿀 때는 `docs/features/authentication.md`에서 인증 결합·권한 검사·암호화의 기존 요구와 구현 차이를 확인한다.
- 사건 데이터 파기·위치 조회 감사·운영 로그를 바꿀 때는 `docs/features/data-retention.md`에서 미전송 원본 보존, 실제 삭제 연결과 기록 실패 처리의 차이를 확인한다.
- 수색 구역의 도형·분할·담당 배정·완료 처리를 바꿀 때는 `docs/features/search-area.md`에서 실제 API에 연결된 검증과 기존 요구의 차이를 확인한다.
- GPS 수집·묶음 저장·경로 조회를 바꿀 때는 `docs/features/search-path.md`에서 측정 시각·수집 순서, 좌표 저장·도형 조립, 품질 검사의 요구와 구현 차이를 확인한다.
- 상황판 조회·갱신·종료 표시를 바꿀 때는 `docs/features/situation-board.md`에서 실제 재조회 흐름, 원본 추적 값, 빈 응답·개인정보 제거의 요구와 구현 차이를 확인한다.
- HTTP 요청·응답·오류는 임시 보존 중인 `docs/api/api-spec.md`를 Controller·소비자 코드와 대조한다. 이 문서를 바꾸거나 REST Docs로 전환할 때는 `docs/api/api-implementation-status.md`에서 소비 테스트·검증 한계·제거 조건을 확인한다.
- DB 변경 배경은 `docs/db-design/db-design-readable.md`를 Mapper·migration과 대조한다. 문서에만 있는 필요한 동작은 미구현 요구로 구분해 합의한다.
- GitHub Issue는 실제로 발견한 문제의 현상·가설·원인·수정·검증을 기록한다. 일반 기능 개발이나 리팩토링의 작업 관리용으로 자동 생성하지 않는다.
- `TODO.md`는 Git으로 공유하지 않는 로컬 작업 순서·진척 메모다. 공유 문서의 참조 대상으로 삼지 않는다.
- 지속해서 필요한 기능 요구·사용 이유·미구현 요구는 관련 공유 문서에 남긴다. Spec·Tasks를 Issue·로컬 TODO로 일괄 대체하지 않는다.
- 합의한 용어는 `CONTEXT.md`에 남긴다. ADR은 되돌리기 어렵고, 배경 없이 이해하기 어려우며, 실제 대안을 비교해 선택한 결정에만 제안한다.
- 문서는 이름이 아니라 내용과 사용처를 보고 유지·축약·삭제를 정한다. 이동·삭제 전에는 테스트·빌드·스크립트의 실제 파일 참조를 확인하고, 필요한 요구·결정·검증 근거와 실행 입력을 보존한다. `docs` 아래의 JSON·fixture도 실행에 필요한 데이터일 수 있다.

## 공통 원칙

- 기존 패턴과 명명 규칙을 우선한다. 과한 추상화보다 명확한 구현을 택한다.
- 문서·코드를 검토할 때 해당 영역의 이름도 확인한다. 정리 후보를 발견하면 `docs/refactoring/codebase-naming-candidates.md`의 등록 기준에 따라 별도 요청 없이 추가한다. 후보 등록과 실제 이름 변경은 구분한다.
- 자동 판단 금지 원칙을 지킨다. 확인 누락 확정, 다음 구역 추천, 위험도 판단 API/배치/UI 문구를 추가하지 않는다.
- Android는 현장 입력, Web은 지휘 상황판이다. 앱 전용 write와 웹 전용 command를 섞지 않는다.
- 사건은 사용자가 직접 생성하지 않는다. MVP 사건은 mock/seed import 결과를 소비한다.
- 사건 종료는 terminal이다. 사용자 재오픈 UI/API를 전제하지 않는다.

## API 규칙

| 항목 | 규칙 |
|---|---|
| JSON API prefix | `/api` |
| Tileserver prefix | `/tiles` |
| URL 형식 | kebab-case, 기존 URL 변경은 소비자와 호환성 확인 |
| Channel header | `X-Client-Channel: APP`, `WEB`, 또는 서버 내부 호출용 `INTERNAL` |
| Idempotency | domain write, outbox replay, lifecycle write는 `Idempotency-Key` 필요 |
| Error body | 기본형 `{ "error": "incident_closed" }` |

## 자주 틀리는 용어

합의한 용어의 뜻은 `CONTEXT.md`에서 확인한다. 공개 필드명은 아래 용어 정리를 이유로 임의 변경하지 않는다.

| 쓰지 않음 | 사용 |
|---|---|
| `Device` | `PolicePhone` |
| `TeamDevice`, `PatrolCarDevice` | `PolicePhone` + account/team/patrol car context |
| `MapBoundary`, `map_boundary` | `search_area.area_level = OVERALL` |
| `MapBoundaryQuery` | `SearchAreaQuery.overallOf` |
| `MissingPersonCache` | `missing_person` |
| `SearchSession` 남용 | 의미에 맞게 `SearchPath`, `OperationalPeriod`, `DutyShift` |
| `presigned upload URL` | `presigned URL for upload` 또는 `uploadUrl` |

## 변경 범위와 호환성

- 변경 범위는 합의한 작업과 실제 호출·데이터·소비자 관계로 판단한다. 범위를 넓혀야 하면 이유와 영향을 먼저 설명한다.
- API·이벤트·DB schema·fixture 변경은 Backend·Frontend·Android의 실제 사용처와 호환성을 확인하고 승인된 범위에서 구현·검증한다. 승인 없이 기존 식별자나 공개 필드명을 바꾸지 않는다.
- `CODEOWNERS`와 적용 중인 승인 절차를 준수한다. 소유권이 불명확하면 사용자에게 확인하며, 옛 Lane 문서로 현재 승인자를 추정하지 않는다.

## Persistence

- Backend persistence는 MyBatis 단일 기준이다. JPA, Hibernate, Spring Data JPA를 새로 도입하지 않는다.
- PostGIS geometry, idempotency, event dispatch, board query는 명시적 SQL과 mapper 경계로 다룬다.
- 이 방침을 바꾸려면 영향과 대안을 사용자와 먼저 합의한다.

## 검증과 보고

제품 코드 변경 시 아래 명령으로 해당 플랫폼을 직접 검증한다.

- Backend: `cd backend && ./gradlew test`
- Frontend: `cd frontend && npm run typecheck && npm run build`
- Android: `cd android && ./gradlew :app:assembleDebug`; 테스트 추가 후 `./gradlew test`

- 문서만 바꿨다면 diff·링크·실행 참조를 확인한다. 이를 제품 동작 검증으로 보고하지 않는다.
- 실행한 명령·결과와 미실행·건너뛴 항목을 구분한다. 완료 상태는 실제로 확인한 검증 결과에 맞춰 기록한다.
- 턴을 마칠 때 변경한 파일명, 핵심 변경, 검증 결과와 다음 작업을 보고한다.

## Git / Issue / PR

- Jira 발급·브랜치 생성·커밋·푸시·PR/MR 생성은 사용자 요청 범위에서만 수행한다. 일반 작업에 Jira나 Lane task를 필수로 요구하지 않는다.
- 커밋 형식은 `[Area] type[(scope)]: 한글 요약`이다. 직접 영향받는 runtime/infra area가 없으면 area를 생략한다. 관련 티켓을 연결할 때만 실제 티켓 ID를 덧붙인다.
- 제목은 **문제와 목적**을 설명한다. 상세 메시지는 **원인과 구현 방식**을 설명하고 필요한 호환성·검증 결과를 뒤에 덧붙인다.
- area는 `BE`, `FE`, `Android`, `Infra` 중 실제 영향받는 것만 이 순서로 `/`로 연결한다. 공통 문서·에이전트 지침에 `Infra`를 대신 붙이거나 별도 `Docs`·`Spec`·`Contract` tag를 만들지 않는다.
- type은 `feat`, `fix`, `refactor`, `style`, `test`, `docs`, `chore`, `ci`, `build`를 사용한다. scope는 제품 도메인·모듈이 분명할 때만 쓰고 area를 반복하지 않는다.
- PR 제목도 같은 형식을 사용한다. 본문에는 변경 이유·내용, 호환성 영향, 검증 결과와 미확인 사항을 적고, 관련 Issue가 있으면 연결한다.
