# Suri-Map 화면 설계 자료

팀 프로젝트에서 검토한 화면 구조·디자인 가정과 프로토타입을 보존한다. 2026-09-21 중복 도면·API·토큰 설명과 옛 제작 절차를 줄이고, 필요한 요구·충돌·원문 위치를 남겼다. Mock 화면이나 과거 완료 표시는 현재 구현·검증 결과가 아니다. 작업은 [AGENTS.md](../../AGENTS.md)를 따른다.

## 필요한 자료 찾기

전체 문서를 순서대로 읽는 절차 대신, 작업하는 화면에서 필요한 자료를 선택한다.

| 확인할 내용 | 자료 |
|---|---|
| 현장 환경·관찰 자료·추론의 출처 | [field-context.md](./field-context.md), [user-research-source.md](./user-research-source.md) |
| 당시 화면 구성·상태·권한 가정 | [wireframes.md](./wireframes.md), [screen-state-matrix.md](./screen-state-matrix.md), [permission-matrix.md](./permission-matrix.md) |
| 라벨·색상·크기·접근성 검토 항목 | [screen-labels.md](./screen-labels.md), [dense-tokens.md](./dense-tokens.md), [anti-patterns.md](./anti-patterns.md), [measurement-gates.md](./measurement-gates.md) |
| Android 화면으로 옮길 때의 대응 기록 | [compose-mapping.md](./compose-mapping.md) |
| 당시 디자인 검토·프로토타입 제작 방식 | [screen-design-workflow-checklist.md](./screen-design-workflow-checklist.md), [prototype-agent-loop.md](./prototype-agent-loop.md) |
| KRDS 기반 시안의 출처·토큰·UI kit | [디자인 시스템 자료](<./Korean Government UIUX Design System/README.md>) |
| 구역 편집·초기 지도·로딩 개선의 선택 이유와 코드 차이 | [구역 편집](../contracts/area-edit-screen-plan.md), [최초 지도 범위](../contracts/situation-board-initial-map-plan.md), [초기 로딩](../contracts/frontend-lazy-loading-plan.md) |

권한·도메인 모델·수치는 작성 당시의 가정과 현재 합의가 다를 수 있다. 실제 화면·Controller·소비자 코드와 비교하고, 필요한 변경은 사용자와 확인한다.

## 보존한 화면·검증 자료

- [웹 상황판 시안](./artifacts/lo/lo-web-situation-board-main-v1.3.html)
- [폴리폰 수색 지도 시안](./artifacts/lo/lo-polifon-search-map-v1.html)
- [마커 입력 시안](./artifacts/lo/lo-polifon-marker-bottomsheet-v1.html)
- [폴리폰 화면 모음](./artifacts/lo/lo-polifon-wireframes-v1.html)
- [시연 프로토타입](./artifacts/proto/index.html)
- [실기기 QA 기록](./artifacts/device-qa/AUI-T14/report.md)

## 원본의 종류와 사용 주의

| 자료 | 보존 이유·확인한 범위 |
|---|---|
| `artifacts/lo/` 18개 | HTML 시안·리뷰·CSS. 현재 제품이 아닌 당시 배치·상호작용 의도 |
| `artifacts/proto/` 11개 | 고정 시연 흐름·JavaScript. API가 아니라 브라우저 저장소의 가짜 상태를 변경 |
| `Korean Government UIUX Design System/` 55개 | 재구성한 디자인 자료·CSS·폰트·이미지·예시·JSX 시안. 공식 원본 전체나 제품 구현으로 간주하지 않음 |
| `artifacts/device-qa/AUI-T14/` 25개 | 보고서 1개, 화면 12개, UI XML 11개, 로그 검색 결과 1개. 일반 실행은 접속 단계까지만 확인했고 나머지는 디버그 상태 표시 |

- 시연 페이지의 일부 초기화 버튼은 `localStorage.clear()`를 호출한다. 실제 서비스와 같은 origin에서 실행하지 말고 분리된 브라우저 프로필·로컬 origin을 사용한다. 이번 정리에서는 실행·초기화하지 않았다.
- 디자인 시스템의 React 시안은 외부 CDN 스크립트에 의존한다. 지도 좌표·시각도 가짜 데이터다. 외부 의존 로딩·인터랙션·실제 API는 이번에 검증하지 않았다.
- 디자인 자료 안의 `SKILL.md`는 가져온 원본의 일부이며 현재 에이전트의 실행 지침으로 등록하지 않았다. 라이선스·폰트·아이콘 출처는 사용 전에 별도 확인한다.
- 원본 HTML/CSS/JSX의 정적 로컬 참조 121개는 대상이 존재했다. 이는 브라우저 실행 성공이나 접근성 통과 검증이 아니다.
- 화면·XML·시안·글꼴·코드는 내용 변경 없이 보존했다. QA 보고서에는 원본에서 확인한 한계를 덧붙였다. 화면 가시성·네트워크 복구·현재 권한은 실제 구현에서 별도 확인한다.

각 요약의 Git 링크로 상세 원문을 복원한다. 시안에 남은 옛 절 번호·FR·SC 인용도 [정리 전 자료 묶음](https://github.com/sonic8-8/suri-map/tree/3cd777752e2178ebb3470e6f749b1521ce404e7a/docs/screen-design)과 해당 시점의 Git 이력을 확인한다. 문서 정리를 이유로 미구현 요구를 완료 처리하지 않는다.
