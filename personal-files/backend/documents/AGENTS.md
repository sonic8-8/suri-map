# Suri-Map 작업 규칙

이 저장소에서 코드나 문서를 수정할 때는 `docs` 디렉토리의 SDD 문서를 먼저 기준으로 삼는다. 구현 세션이 새로 시작되거나 컨텍스트가 길어진 경우에도 아래 순서로 문서를 다시 확인한 뒤 작업한다.

## 시작 전 필수 확인

1. `docs/tasks/index.md`를 먼저 읽어 현재 작업 방식, Lane, Phase, 완료 기준을 확인한다.
2. 작업 대상 Lane이 있으면 해당 `docs/tasks/L*-tasks.md`를 읽는다.
3. 구현 계약은 `docs/spec/boundaries.md`, `docs/spec/harness-scenarios.md`, `docs/spec/specs/*.json`를 1차 기준으로 삼는다.
4. `docs/prd.md`, `docs/architecture.md`, `docs/adr.md`, `docs/adr-archive.md`는 배경과 설계 의도 확인용으로 읽되, public API, event, entity, error, annotation, board slot, fixture 값은 spec 문서를 우선한다.
5. spec 문서와 task 또는 기존 코드가 충돌하면 임의로 선택하지 말고, 충돌 내용과 영향 범위를 먼저 보고한다.
6. `docs` 문서가 변경되었다는 요청을 받으면 관련 원문을 다시 읽고, Codex persistent memory의 Suri-Map 요약도 함께 갱신한다. 단, 최종 source of truth는 항상 저장소의 `docs` 원문이다.
7. 새 세션마다 `docs/` 전체를 기본으로 모두 읽지 않는다. 우선 위 시작 순서와 Lane 3 핵심 문서를 확인하고, 현재 작업과 직접 관련된 섹션만 추가로 읽는다.

## 사용자 구현 범위

- 이 사용자는 기본적으로 **Lane 3만 구현**한다.
- Lane 3의 담당 Spec은 **S2(Map Boundary & Search Area)**, **S8(Operational Period & Handover)** 이다.
- 구현 전에 `docs/tasks/L3-tasks.md`, `docs/spec/specs/S2.json`, `docs/spec/specs/S8.json`, `docs/spec/boundaries.md`, `docs/spec/harness-scenarios.md`를 우선 확인한다.
- 다른 Lane의 entity, API, event, board shell, domain write는 직접 구현하지 않는다. Lane 3 작업에 필요한 경우에는 소비 계약, mock, fixture, blocker로만 다룬다.
- 요청이 모호하면 Lane 3 범위 안에서 해석하고, Lane 3 밖 구현이 필요해 보이면 작업을 넓히기 전에 먼저 보고한다.

<!-- ## 절대 유지할 제품/도메인 기준 -->

<!-- - 사건은 사용자가 직접 생성하지 않는다. MVP/시연은 112/실종프로파일링 mock/seed 배정 사건 import 기준이다. -->
<!-- - 계정은 팀 계정, 순찰차 계정, 지휘 계정 기준이다. 개인 계정을 기본 전제로 두지 않는다. -->
<!-- - GPS 경로 기록 주체는 개인이 아니라 팀 업무폰 또는 순찰차 업무폰 `Device`다. -->
<!-- - `OP(Operation Period)`는 사건 내 수색 차수와 인수인계 기준이다. OP1은 bootstrap에서 자동 생성되고, 이후 OP는 권한 보유자가 수동 생성한다. -->
<!-- - 지도 범위 용어와 계약은 `map_boundary`를 사용한다. -->
<!-- - 시스템은 확인 누락 확정, 다음 수색 구역 추천, 위험도 판단 같은 자동 판단을 수행하지 않는다. -->
<!-- - Suri-Map은 운영 보조 시스템이며 112 공식 기록 시스템을 대체하지 않는다. -->
<!-- - 사건 종료는 terminal이다. 재오픈 API/UI를 임의로 만들지 않는다. -->
<!-- - 실종자 정보는 장기 보존 원본이 아니라 운영 캐시로 취급한다. -->

<!-- ## 채널/소유권 경계 -->

<!-- - Android 앱은 현장 입력, 수색 세션, 경로, 현장 마커, 오프라인 기록 중심이다. -->
<!-- - Web 상황판은 지휘, 상황 공유, 구역/OP 관리, 인수인계, AI 요약 중심이다. -->
<!-- - 앱 전용 쓰기를 Web에서 허용하지 않고, Web 전용 지휘 쓰기를 앱에서 허용하지 않는다. -->
<!-- - 현장 마커 생성은 앱 전용이다. 초기 기준 마커와 지휘용 보정 UI는 Web 권한 범위에서 별도 취급한다. -->
<!-- - 구역 완료, OP 생성, 지도 기준 범위 변경, 차량/도보 구간 보정은 Web 전용으로 본다. -->
<!-- - 다른 Lane의 entity, API, event, board shell, domain write를 직접 구현하지 않는다. 필요한 경우 mock/fixture 소비 또는 blocker로 남긴다. -->

<!-- ## 작업 루프 -->

<!-- - Phase 1~3 구현은 `docs/tasks/agent-loop-guide.md`의 RED -> GREEN -> 재검증 순서를 따른다. -->
<!-- - RED test 또는 실패 fixture가 의도한 이유로 실패한 뒤 최소 구현으로 GREEN을 만든다. -->
<!-- - task checkbox는 완료 기준이 자동 test, 하네스 로그, screenshot, MR evidence 등 관찰 가능한 증거로 증명될 때만 완료 처리한다. -->
<!-- - 기준 문서 수정이 필요해 보이면 구현을 멈추고 사유, 영향 범위, 대안을 먼저 보고한다. -->
<!-- - 기준 문서를 수정했거나 사용자가 문서 변경을 알려주면, 다음 구현 전에 변경된 문서와 Lane 3 관련 계약 요약을 메모리에 갱신한다. -->

<!-- ## Fixture 작성 기준 -->

<!-- - fixture 클래스에는 테스트가 직접 사용하는 고정 ID, enum/status 목록, alias 매핑, canonical geometry 값, 이벤트 factory처럼 실제 test data 또는 contract 값만 둔다. -->
<!-- - spec 문장의 복사본, 정책 설명, normalization 단계, rejection rule 설명, ownership 금지 목록처럼 사람이 읽기 위한 내용은 상수보다 한글 Javadoc/comment로 남긴다. -->
<!-- - `S2GeometryFixtures`는 CRS, 좌표 순서, bbox, 최소 면적, precision, 정상 좌표, 고정 invalid 좌표, 이름이 고정된 invalid case를 fixture 값으로 둔다. -->
<!-- - `S2BoundaryAreaFixtures`는 고정 ID, persisted state, history field, alias mapping, split 초기 상태/version, 이벤트 factory를 fixture 값으로 둔다. -->
<!-- - 테스트가 문자열 자체를 검증해야 하는 경우에만 설명성 문자열을 상수로 승격한다. -->

## 코드 스타일

- 기존 구조와 네이밍을 우선한다.
- 새 추상화는 실제 중복이나 교체 경계가 분명할 때만 추가한다.
- 백엔드 작업은 `backend/AGENTS.md`의 패키지, DTO, 테스트, 커밋 규칙도 함께 따른다.
