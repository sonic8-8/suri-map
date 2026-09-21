# 수리맵 문서 안내

읽으려는 목적에 맞는 문서부터 선택한다. 현재 동작은 연결된 코드·SQL·설정·실행 결과와 대조하고, 문서에 남은 요구나 과거 계획을 구현 완료로 해석하지 않는다.

## 제품과 시스템 이해

| 확인할 내용 | 문서 |
|---|---|
| 누구의 어떤 문제를 해결하는가 | [제품 요구사항](prd.md) |
| 기술 스택·구성 요소·데이터 흐름은 어디서 확인하는가 | [아키텍처](architecture.md) |
| 프로젝트에서 용어를 어떤 뜻으로 쓰는가 | [도메인 용어](../CONTEXT.md) |

프로젝트 소개용 루트 README와 이 문서의 역할은 구분한다. 여기는 개발·검증에 필요한 자료의 위치를 안내한다.

## 기능별 요구와 구현 차이

`features/`에는 기능별로 보존한 요구·현재 구현과의 차이·미확인 사항을 둔다.

| 확인할 기능 | 문서 |
|---|---|
| 로그인·채널·사건 접근 | [인증과 접근 권한](features/authentication.md) |
| 사건 가져오기·배정·종료 | [사건 생명주기](features/incident-lifecycle.md) |
| 수색 차수·근무 교대·인수인계 | [인수인계와 수색 이력](features/handover.md) |
| 수색 구역의 도형·담당 배정·완료 | [수색 구역](features/search-area.md) |
| GPS 수집·묶음 저장·경로 조회 | [수색 경로](features/search-path.md) |
| 마커·사진의 기록·재시도·삭제 | [마커와 사진](features/marker-photo.md) |
| 상황판 조회·갱신·종료 표시 | [상황판](features/situation-board.md) |
| SSE·FCM 전달·재연결·실패 복구 | [이벤트 전달](features/event-delivery.md) |
| 미전송 기록·중복 요청·종료 후 정리 | [오프라인 동기화](features/offline-sync.md) |
| 오프라인 자료의 다운로드·갱신·파기 | [오프라인 패키지](features/offline-package.md) |
| 사건 데이터 파기·위치 조회 감사·운영 로그 | [데이터 보존과 파기](features/data-retention.md) |

## 설계·작업·검증 자료

| 읽는 목적 | 위치·주의점 |
|---|---|
| 기술·구조의 선택 이유를 확인한다 | [adr/README.md](adr/README.md) — 결정과 과거 원문 안내 |
| HTTP 요청·응답·오류를 대조한다 | [API 명세](api/api-spec.md), [구현 상태표](api/api-implementation-status.md) — 코드 기반 문서화로 전환하기 전까지 유지하는 참고 자료·검사 입력 |
| DB 구조·컬럼의 의미를 확인한다 | [DB 설계](db-design/db-design-readable.md), [ERD](db-design/db-design-erd.md) — Mapper·migration과 대조 |
| 화면 요구·설계 이유·시안을 찾는다 | [screen-design/README.md](screen-design/README.md) — Android UI, 웹 화면과 과거 QA 자료 안내 |
| 시연 입력을 준비하거나 기능을 연결해 확인한다 | [mock 112 안내](guides/mock-112-demo.md), [기능 검증 안내](guides/feature-verification.md) — 확인 절차이며 통과 기록은 아님 |
| 리팩토링 후보·기존 변경 이유를 확인한다 | [refactoring/](refactoring/) — 이름 후보, 수색 경로 구조, 웹 초기 로딩 검토 |
| 외부 근거·비교 조사를 읽는다 | [research/](research/) — 조사 시점·출처·적용 범위 확인 |
| 발견한 문제의 현상·원인·수정 결과를 찾는다 | [issues/](issues/) — 일반 작업의 필수 티켓 목록으로 사용하지 않음 |
| 실제로 무엇을 검증했는지 확인한다 | [test-results/](test-results/) — 시험 조건·결과·한계·캡처. 실패·부분 결과도 포함하며 과거 원본은 각 요약의 고정 Git 링크에서 확인 |
| 아직 채택하지 않은 안을 검토한다 | [Knox·MDM 도입 검토안](proposals/knox-mdm.md) — 보류 자료이며 실행 지침이 아님 |
| 공용 테스트 데이터를 사용·수정한다 | [test-fixtures/README.md](../test-fixtures/README.md) — 입력·검사기·소비 테스트 안내 |

## 문서를 추가하거나 갱신할 때

- 실제 내용과 읽는 목적에 맞는 기존 위치를 우선한다. 빈 분류나 모든 작업에 필요한 문서 묶음을 미리 만들지 않는다.
- 요구·설명은 한곳에 두고 관련 문서에서 링크한다. 이 목차에 상세 내용이나 완료 상태를 복제하지 않는다.
- 과거 계획·미구현 요구·검증 결과를 구분하고, 구현을 바꾸면 관련 설명과 확인 결과도 갱신한다.
- 파일을 옮기거나 지우기 전에는 필요한 내용과 실행 입력을 보존하고, 링크·테스트·스크립트의 참조를 함께 확인한다.

분류 근거와 비교한 대안은 [문서 구조 조사](research/documentation-structure-research.md)에 남겼다. 작업 규칙은 [AGENTS.md](../AGENTS.md)를 따른다.
