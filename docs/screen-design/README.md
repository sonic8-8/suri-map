# Suri-Map 화면 설계 자료

팀 프로젝트에서 검토한 화면 구조·디자인 가정과 프로토타입을 보존한다. 문서의 완료 표시나 Mock 화면을 현재 제품의 구현·검증 결과로 대신하지 않는다. 현재 코드 변경은 [AGENTS.md](../../AGENTS.md)와 해당 플랫폼의 규칙을 따른다.

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

권한·도메인 모델·수치는 작성 당시의 가정과 현재 합의가 다를 수 있다. 실제 화면·Controller·소비자 코드와 비교하고, 필요한 변경은 사용자와 확인한다.

## 보존한 화면·검증 자료

- [웹 상황판 시안](./artifacts/lo/lo-web-situation-board-main-v1.3.html)
- [폴리폰 수색 지도 시안](./artifacts/lo/lo-polifon-search-map-v1.html)
- [마커 입력 시안](./artifacts/lo/lo-polifon-marker-bottomsheet-v1.html)
- [폴리폰 화면 모음](./artifacts/lo/lo-polifon-wireframes-v1.html)
- [시연 프로토타입](./artifacts/proto/index.html)
- [실기기 QA 기록](./artifacts/device-qa/AUI-T14/report.md)

화면 시안·리뷰·실기기 자료는 각각 검증한 범위가 다르다. 현재 기능의 완료 여부는 해당 Issue와 실제 실행 결과에서 확인한다. 원본 화면에 남은 옛 문서 인용은 해당 시점의 Git 이력에서 확인한다.
