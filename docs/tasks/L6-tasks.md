# L6 Tasks · Board Shell, Offline Package, Tiles

상태: 초안. 담당 Lane: L6. 담당 Spec: S3-2, S7. 주요 SC: SC-03, SC-11, board convergence.

## 작업 범위

- web board route/layout/MapLibre root/shared state/final merge
- board slot registry, board API assembly, BoardDTO assembly
- offline package manifest/installation, package badge, tileserver endpoints
- MapLibre style, local tile fixture, manifest stale/revision
- FR-23 rendering guard against automatic missing-area/recommendation UI

## 외부 계약

- Consumes all domain read models/events but does not own their writes.
- Consumes L2/S4 SSE refetch signal contract.
- Consumes L3/S2 overall_search_area/area query and L3/S8 OP/handover query.
- Consumes L5/S5 marker query and L4/S3-1 path query.
- Provides board slot registry and board API convergence expectations to all lanes.

## Phase -1

- [x] L6-B01 웹 상황판 MapLibre 실행 기반 준비
  - 담당 Spec: S3-2, S7
  - 필수 참조: `architecture.md §2`, `architecture.md §4`, `adr.md ADR-0035`, `spec/specs/S3-2.json`, `spec/specs/S7.json`
  - 연관 Lane: All lanes
  - 시나리오: SC-03, board paths for SC-02 through SC-12
  - 구현 산출물: npm/Vite React app base, React Router route scaffold, TanStack Query provider, Zustand board display store scaffold, MapLibre root base, Docker Compose service map, board app entrypoint
  - 예상 작업량: 1d
  - 완료 기준: domain slot 또는 tile endpoint 구현 전에 npm 기반 React/MapLibre base, router/query/store provider, Docker Compose service map, board app entrypoint가 기동된다.

## Phase 0

- [x] L6-T01B 상황판 슬롯 등록부 계약과 마운트 실패 테스트 작성
  - 담당 Spec: S3-2
  - 필수 참조: `spec/specs/S3-2.json`, `spec/boundaries.md §9.2`, `spec/harness-scenarios.md §0.1`
  - 연관 Spec: S1-1, S1-2, S1-3, S2, S3-1, S4, S5, S6, S7, S8
  - 시나리오: SC-02 through SC-12 board paths
  - 관련 FR: FR-04, FR-05, FR-08, FR-09, FR-11, FR-18, FR-19, FR-24, FR-25, FR-26, FR-27, FR-35, FR-37, FR-39
  - 구현 산출물: slot registry contract fixture, slot mounting rule red tests, slot-scoped renderer failure tests
  - 예상 작업량: 1d
  - 완료 기준: board shell 구현 전에 slot mounting 기대 조건이 failing test 또는 fixture로 고정된다.

- [x] L6-T05 오프라인 패키지 목록 계약 fixture 작성
  - 담당 Spec: S7
  - 필수 참조: `spec/specs/S7.json`, `spec/harness-scenarios.md §2 SC-03`, `spec/harness-scenarios.md §6 mock tile catalog/server`
  - 연관 Spec: S1-1, S1-2, S2, S5, S8
  - 시나리오: SC-03
  - 관련 FR: FR-21, FR-31
  - 구현 산출물: offline_package_manifest contract fixture, manifest fixture builder, missing_person/OP/area/marker/overall_search_area/tile item contract tests, SC-03 tile fixture exactness tests
  - 예상 작업량: 1d
  - 완료 기준: real package API 구현 전에 Phase 0 fixture가 incident metadata, missing_person, OP, assigned area, initial marker, overall_search_area, tile reference, police_phone context를 담고, tile fixture는 `tile-manifest-inc-precinct-001`, zoom `15..16`, `local://tiles/inc-precinct-first-001/{z}/{x}/{y}.pbf` 기준으로 검증된다.

## Phase 1

- [x] L6-T01A 상황판 경로·레이아웃·MapLibre 루트 구현
  - 담당 Spec: S3-2
  - 필수 참조: `spec/specs/S3-2.json`, `spec/boundaries.md §9.2`, `spec/harness-scenarios.md §0.1`
  - 연관 Spec: S1-1, S1-2, S1-3, S2, S3-1, S4, S5, S6, S7, S8
  - 시나리오: SC-02 through SC-12 board paths
  - 관련 FR: FR-04, FR-05, FR-08, FR-09, FR-11, FR-18, FR-19, FR-24, FR-25, FR-26, FR-27, FR-35, FR-37, FR-39
  - 구현 산출물: board route/layout, MapLibre root, shared state wiring, shell ownership tests
  - 예상 작업량: 1d
  - 완료 기준: board route, layout, MapLibre root, shared state가 S3-2 소유로 구현되고 source owner data를 변경하지 않는다.

- [x] L6-T01C 상황판 슬롯 등록부와 마운트 규칙 구현
  - 담당 Spec: S3-2
  - 필수 참조: `spec/specs/S3-2.json`, `spec/boundaries.md §9.2`, `spec/harness-scenarios.md §0.1`
  - 연관 Spec: S1-1, S1-2, S1-3, S2, S3-1, S4, S5, S6, S7, S8
  - 시나리오: SC-02 through SC-12 board paths
  - 관련 FR: FR-04, FR-05, FR-08, FR-09, FR-11, FR-18, FR-19, FR-24, FR-25, FR-26, FR-27, FR-35, FR-37, FR-39
  - 구현 산출물: slot registry, slot mounting rules, slot-scoped renderer tests
  - 예상 작업량: 1d
  - 완료 기준: 모든 feature Lane이 `spec/boundaries.md §9.2`의 slot-scoped renderer만 mount하고, final merge 소유권은 S3-2에 남는다.

- [x] L6-T03A 전체 수색 구역·수색 구역 slot 구현
  - 담당 Spec: S3-2
  - 필수 참조: `spec/specs/S3-2.json`, `spec/boundaries.md §9.2`, `spec/boundaries.md §10`
  - 연관 Spec: S2
  - 시나리오: SC-04
  - 관련 FR: FR-05, FR-07
  - 구현 산출물: overall_search_area slot renderer, area slot renderer, S2 source-owner immutability tests
  - 예상 작업량: 1d
  - 완료 기준: overall_search_area/area slot이 source owner를 변경하지 않고 S2 source data를 렌더링하며 관찰 가능한 loading/failure state를 노출한다.

- [x] L6-T03B 경로·마커 slot 구현
  - 담당 Spec: S3-2
  - 필수 참조: `spec/specs/S3-2.json`, `spec/boundaries.md §9.2`, `spec/boundaries.md §10`
  - 연관 Spec: S3-1, S5
  - 시나리오: SC-05, SC-06, SC-09
  - 관련 FR: FR-04, FR-11, FR-35
  - 구현 산출물: path slot renderer, marker slot renderer, S3-1/S5 source-owner immutability tests
  - 예상 작업량: 1d
  - 완료 기준: path/marker slot이 source owner를 변경하지 않고 source data를 렌더링하며 관찰 가능한 loading/failure state를 노출한다.

- [x] L6-T03C 단말 최신성 슬롯과 공통 지연 상태 검증
  - 담당 Spec: S3-2
  - 필수 참조: `spec/specs/S3-2.json`, `spec/boundaries.md §9.2`, `spec/boundaries.md §10`
  - 연관 Spec: S1-2, S4
  - 시나리오: SC-05, SC-09
  - 관련 FR: FR-24, FR-25
  - 구현 산출물: police_phone_freshness slot renderer, common stale/loading/failure state tests, stale badge fixture
  - 예상 작업량: 1d
  - 완료 기준: source owner data를 변경하지 않고 police_phone freshness slot과 shared stale/loading/failure state가 표시된다.

- [x] L6-T08A 로컬 타일·스타일 API 구현
  - 담당 Spec: S7
  - 필수 참조: `spec/specs/S7.json`, `spec/harness-scenarios.md §6 mock tile catalog/server`, `adr.md ADR-0004`
  - 연관 Spec: S3-2
  - 시나리오: SC-03
  - 관련 FR: FR-19, FR-31
  - 구현 산출물: `/tiles/styles/{styleId}.json`, `/tiles/{style}/{z}/{x}/{y}.pbf`, local tile fixture tests
  - 예상 작업량: 1d
  - 완료 기준: `/tiles`와 style endpoint가 local tile fixture를 사용하고 external tile host를 요구하지 않는다.

- [ ] L6-T08B MapLibre 로컬 스타일·저작자 표시·외부 타일 서버 차단 구현
  - 담당 Spec: S7
  - 필수 참조: `spec/specs/S7.json`, `spec/harness-scenarios.md §6 mock tile catalog/server`, `adr.md ADR-0004`
  - 연관 Spec: S3-2
  - 시나리오: SC-03
  - 관련 FR: FR-19, FR-31
  - 구현 산출물: local MapLibre style, OSM attribution check, external tile host rejection tests
  - 예상 작업량: 1d
  - 완료 기준: MapLibre style이 local tile endpoint만 로드하고 OSM attribution을 유지하며 external tile host를 거부한다.

## Phase 2

- [ ] L6-T02A 상황판 board API 조립 모델 구현
  - 담당 Spec: S3-2
  - 필수 참조: `spec/specs/S3-2.json`, `spec/specs/S4.json`, `spec/harness-scenarios.md §0.3`, `spec/harness-scenarios.md §6 mock board API refetch/assembly`
  - 연관 Spec: S1-1, S1-2, S1-3, S2, S3-1, S4, S5, S6, S7, S8
  - 시나리오: SC-04, SC-05, SC-06, SC-08, SC-09, SC-10, SC-11, SC-12
  - 구현 산출물: BoardDTO assembly model, owner response version mapping tests, board response row fixture
  - 예상 작업량: 1d
  - 완료 기준: source owner data를 변경하지 않고 owner response version에서 BoardDTO row를 조립할 수 있다.

- [ ] L6-T02B eventId 중복 제거와 오래된 refetch 차단 구현
  - 담당 Spec: S3-2
  - 필수 참조: `spec/specs/S3-2.json`, `spec/specs/S4.json`, `spec/harness-scenarios.md §0.3`
  - 연관 Spec: S4
  - 시나리오: SC-04, SC-05, SC-06, SC-08, SC-09, SC-10, SC-11, SC-12
  - 구현 산출물: eventId dedupe policy, stale event guard, reordered/duplicate event tests
  - 예상 작업량: 1d
  - 완료 기준: duplicate/stale event는 무시되고 더 최신 owner version만 board API response를 갱신한다.

- [ ] L6-T02C SSE refetch 수렴과 API 조립 지연 관측 검증 구현
  - 담당 Spec: S3-2
  - 필수 참조: `spec/specs/S3-2.json`, `spec/specs/S4.json`, `spec/harness-scenarios.md §0.3`, `spec/harness-scenarios.md §6 mock board API refetch/assembly`
  - 연관 Spec: S4
  - 시나리오: SC-04, SC-05, SC-06, SC-08, SC-09, SC-10, SC-11, SC-12
  - 구현 산출물: SSE refetch convergence tests, board API assembly lag state, lag observation fixture
  - 예상 작업량: 1d
  - 완료 기준: SSE event 이후 board API response가 수렴하고, 수렴이 지연될 때 board API assembly lag state를 관찰할 수 있다.

- [ ] L6-T06A offline_package_installation API·이벤트·조회 구현
  - 담당 Spec: S7
  - 필수 참조: `spec/specs/S7.json`, `spec/specs/S6.json`, `spec/specs/S3-2.json`
  - 연관 Spec: S1-2, S4, S3-2, S6
  - 시나리오: SC-03, SC-07, SC-09, SC-12
  - 관련 FR: FR-19, FR-29, FR-31
  - 구현 산출물: offline_package_installation API test, `OFFLINE_PACKAGE_INSTALLATION_CHANGED` publish request test, OfflinePackageInstallationQuery fixture/contract test
  - 예상 작업량: 1d
  - 완료 기준: status report API test가 manifest item state를 갱신하고, publish request test가 `OFFLINE_PACKAGE_INSTALLATION_CHANGED`를 capture하며, OfflinePackageInstallationQuery contract test가 기대 package installation status field를 노출한다.

- [ ] L6-T06B package_badge API 조립과 S6 로컬 경고 입력 검증
  - 담당 Spec: S7, S3-2
  - 필수 참조: `spec/specs/S7.json`, `spec/specs/S6.json`, `spec/specs/S3-2.json`
  - 연관 Spec: S3-2, S6
  - 시나리오: SC-03, SC-07, SC-09, SC-12
  - 관련 FR: FR-19, FR-29, FR-31
  - 구현 산출물: package_badge API assembly tests, S6 local warning input fixture, package unavailable/stale evidence
  - 예상 작업량: 1d
  - 완료 기준: offline_package_installation status가 board package badge로 조립되고 unavailable/stale package에 대한 S6 local warning input을 제공한다.

## Phase 3

- [ ] L6-T04A OP 비교·이력·인수인계 slot 구현
  - 담당 Spec: S3-2
  - 필수 참조: `spec/specs/S3-2.json`, `spec/specs/S8.json`, `spec/boundaries.md §6 FR-23`, `spec/harness-scenarios.md §2 SC-11`
  - 연관 Spec: S8, S3-1, S2, S5, S4
  - 시나리오: SC-10, SC-11
  - 관련 FR: FR-11, FR-23, FR-37, FR-39
  - 지원 FR: FR-32 through S8 OP history source data
  - 구현 산출물: op_toggle/op_history/handover_memo/handover_status slot renderers, unavailable UI state, S8 source-owner immutability tests
  - 예상 작업량: 1d
  - 완료 기준: S8 source data를 변경하지 않고 OP toggle/history와 handover slot이 source evidence와 unavailable state를 표시한다.

- [ ] L6-T04B 수색 이력 요약 대체 표시와 FR-23 금지 행동 버튼 차단 구현
  - 담당 Spec: S3-2
  - 필수 참조: `spec/specs/S3-2.json`, `spec/specs/S8.json`, `spec/boundaries.md §6 FR-23`, `spec/harness-scenarios.md §2 SC-11`, `adr.md ADR-0034`
  - 연관 Spec: S8, S3-1, S2, S5, S4
  - 시나리오: SC-11
  - 관련 FR: FR-23, FR-39
  - 구현 산출물: search_history_summary slot renderer, OpenAI-generated summary success state, loading state, unavailable UI state, source evidence links, forbidden recommendation CTA tests
  - 예상 작업량: 1d
  - 완료 기준: search_history_summary slot이 OpenAI-generated 요약과 unavailable 상태를 구분해 표시하고, source prompt/provider secret을 노출하지 않으며 자동 누락 구역 확정, 위험도 판단, 다음 구역 CTA를 차단한다.

- [ ] L6-T07 전체 수색 구역 버전과 만료 패키지 처리 구현
  - 담당 Spec: S7
  - 필수 참조: `spec/specs/S7.json`, `spec/specs/S2.json`, `spec/boundaries.md §10 SC-03`, `spec/boundaries.md §10 SC-04`
  - 연관 Spec: S2, S4, S3-2
  - 시나리오: SC-03, SC-04
  - 관련 FR: FR-31
  - 구현 산출물: manifest revision policy, stale package transition, `SEARCH_AREA_CHANGED` consumer test, re-download requirement UI state
  - 예상 작업량: 1d
  - 완료 기준: `SEARCH_AREA_CHANGED`가 manifest revision을 증가시키고 기존 READY/PARTIAL offline_package_installation status를 STALE로 표시하며 re-download requirement를 노출한다.

- [ ] L6-T09A offline_package_manifest/offline_package_installation 삭제 후크와 삭제 표식 전이 구현
  - 담당 Spec: S3-2, S7
  - 필수 참조: `spec/specs/S3-2.json`, `spec/specs/S7.json`, `spec/specs/S1-3.json`, `spec/harness-scenarios.md §2 SC-12`
  - 연관 Spec: S1-1, S1-3, S4, S6
  - 시나리오: SC-12
  - 관련 FR: FR-31
  - 지원 FR: FR-22 through sanitized terminal/package purge board state
  - 선행 task: L1-T06, L2-T08
  - 구현 산출물: package purge hook, package installation tombstone transition, S1-3 purge hook contract test
  - 예상 작업량: 1d
  - 완료 기준: package manifest/installation purge hook이 실행되고 missing_person package data를 보존하지 않은 채 package state를 sanitized tombstone으로 전이한다.

- [ ] L6-T09B incident_terminal/package_badge board state와 개인정보 제거 검증
  - 담당 Spec: S3-2, S7
  - 필수 참조: `spec/specs/S3-2.json`, `spec/specs/S7.json`, `spec/specs/S1-3.json`, `spec/harness-scenarios.md §2 SC-12`
  - 연관 Spec: S1-1, S1-3, S4, S6
  - 시나리오: SC-12
  - 관련 FR: FR-31
  - 지원 FR: FR-22 through sanitized terminal/package purge board state
  - 선행 task: L1-T06, L2-T08, L6-T09A
  - 구현 산출물: terminal/tombstone board state, incident_terminal/package_badge board states, missing_person data absence tests
  - 예상 작업량: 1d
  - 완료 기준: board가 sanitized terminal/package badge state를 표시하고 terminal board state에 missing_person data가 남지 않는다.

## Phase 4

- [ ] L6-T10A SC-03 패키지·타일 하네스 작성
  - 담당 Spec: S3-2, S7
  - 필수 참조: `spec/specs/S7.json`, `spec/harness-scenarios.md §2 SC-03`
  - 연관 Lane: All lanes
  - 시나리오: SC-03
  - 구현 산출물: package/tile harness runner, package manifest tests, local tile endpoint/style tests, tile fixture exactness report
  - 예상 작업량: 1d
  - 완료 기준: package/tile harness test가 mocked source contract와 local tile fixture 기준으로 통과하고, tileManifestId/zoomRange/tile URI는 `spec/harness-scenarios.md`와 `spec/specs/S7.json`에 정렬된 값으로 검증된다.

- [ ] L6-T10B 상황판 board API/SSE 수렴 하네스 작성
  - 담당 Spec: S3-2
  - 필수 참조: `spec/specs/S3-2.json`, `spec/specs/S4.json`, `spec/harness-scenarios.md §0.3`
  - 연관 Lane: All event-producing lanes
  - 시나리오: board convergence for SC-02 through SC-12
  - 구현 산출물: board API assembly harness runner, SSE convergence tests, slot merge tests, board API refetch lag tests
  - 예상 작업량: 1d
  - 완료 기준: board API response, slot merge, SSE convergence, board API refetch lag test가 mocked source contract 기준으로 통과한다.

- [ ] L6-T10C SC-11 OP·수색 이력 요약·FR-23 렌더링 차단 하네스 작성
  - 담당 Spec: S3-2
  - 필수 참조: `spec/specs/S3-2.json`, `spec/specs/S8.json`, `spec/boundaries.md §6 FR-23`, `spec/harness-scenarios.md §2 SC-11`, `adr.md ADR-0034`
  - 연관 Lane: L3, L4, L5
  - 시나리오: SC-11
  - 구현 산출물: OP/search_history_summary harness runner, OpenAI success/failure slot fixtures, FR-23 rendering guard tests, forbidden CTA evidence
  - 예상 작업량: 1d
  - 완료 기준: OP/search_history_summary rendering guard test가 OpenAI success/failure 상태 모두에서 통과하고 금지된 recommendation/risk/missing-area CTA state가 숨겨진 상태로 유지된다.

- [ ] L6-T10D 상황판·패키지·타일 시연 스모크 절차 작성
  - 담당 Spec: S3-2, S7
  - 필수 참조: `prd.md §2.2`, `prd.md §2.3`, `spec/harness-scenarios.md`
  - 연관 Lane: All lanes
  - 시나리오: SC-03, SC-11, board convergence for SC-02 through SC-12
  - 구현 산출물: board/package/tile demo smoke checklist, local tile network capture plan, terminal state convergence capture target
  - 예상 작업량: 1d
  - 완료 기준: Phase 5 실행 전에 demo smoke procedure가 준비되며 final pass/fail evidence는 기록하지 않는다.

## Phase 5

- [ ] L6-D01 상황판·패키지·타일·단말 시연 종료 검증
  - 담당 Spec: S3-2, S7
  - 필수 참조: `prd.md §2.2`, `prd.md §2.3`, `spec/harness-scenarios.md`
  - 연관 Lane: All lanes
  - 시나리오: SC-03, SC-11, board convergence for SC-02 through SC-12
  - 선행 task: L6-T10D
  - 구현 산출물: local tile network evidence, terminal state convergence evidence, linked defect task list
  - 예상 작업량: 3d+
  - 완료 기준: 12-step rehearsal 중 board slot이 수렴하고, local tile/package state가 표시되며, terminal incident state가 sanitized 상태이고, external tile host network request가 필요하지 않으며, defect fix는 별도 task로 추적된다.

## 담당하지 않음

- Domain writes for incident, area, path, marker, OP, handover, purge
- Event dispatch job/SSE dispatch internals
- Local Android Outbox implementation
- Source DTO field invention outside source owner contract
