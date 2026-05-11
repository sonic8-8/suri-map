# `docs/screen-design` 직접 하위 md 파일 요약

이 문서는 `docs/screen-design/` 바로 아래에 있는 md 파일들만 정리한 메모다.

- 포함: `docs/screen-design/*.md`
- 제외: 하위 디렉토리 안의 md 파일들

## 파일별 요약

| 파일 | 한 줄 요약 | 담고 있는 핵심 내용 |
|---|---|---|
| `README.md` | 이 디렉토리의 진입점 | 문서 구조, 읽는 순서, 의존 그래프, 변경 영향표, 핵심 도메인 사실, 산출물 정책, 외부 참조를 정리한다. |
| `screen-design-workflow-checklist.md` | 화면 설계 작업 순서표 | 전제 확인부터 화면 목록, 사용자 흐름, 상태 매트릭스, 권한 매트릭스, low-fi, 토큰, 프로토타입, 검수 순서까지 전체 진행 절차를 고정한다. |
| `field-context.md` | 현장 사용 맥락 정의 | 폴리폰과 웹 상황판의 물리·인지·네트워크 환경, 정보 밀도 목표, 운영 시나리오, 금지 가정을 정리한다. |
| `anti-patterns.md` | 하면 안 되는 패턴 모음 | PRD 금지 행동의 시각적 누설, KRDS/지도 앱 패턴 오용, 정보 밀도·가독성 실패 사례, 실수 사례를 모은다. |
| `screen-state-matrix.md` | 화면 × 상태 정의표 | 각 화면의 `default/loading/empty/error/offline/stale` 등 상태별 분기와 화면별 처리 규칙을 매핑한다. |
| `permission-matrix.md` | 권한 × 화면 액션표 | 계정 유형별로 어떤 액션을 보여주고 비활성화할지, `permission_denied`와 `permission_partial`을 어떻게 나눌지 정리한다. |
| `screen-labels.md` | UI 라벨 규칙서 | 도메인 객체 표기, 상태 라벨, 액션 라벨, 알림/토스트, empty state 카피의 일관된 표현 규칙을 정의한다. |
| `dense-tokens.md` | KRDS-Ops 토큰 명세 | KRDS를 기반으로 한 dense variant의 색상, 타이포그래피, 간격, 터치 타깃, 정보 밀도 목표와 freshness 임계값을 정의한다. |
| `measurement-gates.md` | 정량 검수 게이트 | 정보 밀도, 가독성, 인지 부하, 환경 견고성, 상태 완전성, 금지 행동 누설을 G1~G6으로 측정하는 기준을 정리한다. |
| `prototype-agent-loop.md` | 프로토타입 생성 루프 | AI 프로토타입 생성 시 넣어야 할 컨텍스트 골격, low-fi/high-fi/proto 루프, 재시도 정책, 산출물 명명 규칙을 정의한다. |
| `user-research-source.md` | 리서치 출처 추적 | MVP 시연 baseline freeze 정책, 현재 보유 자료, v2 이후 참고용 누적 항목, 인터뷰/관찰 기록 형식을 정리한다. |
| `wireframes.md` | 비-Hero 화면 IA/와이어프레임 | Hero 3개를 제외한 13개 화면의 IA 명세와 ASCII 와이어프레임, 재사용 컴포넌트, 권한 분기를 정리한다. |

## 한 줄 정리

이 디렉토리는 Suri-Map 화면 설계의 `문서 허브 + 작업 순서 + 권한/상태/라벨/토큰/검수 기준 + 프로토타입 루프`를 나눠 담고 있다.

## 읽는 순서

1. `README.md`
2. `screen-design-workflow-checklist.md`
3. `field-context.md`
4. `anti-patterns.md`
5. `screen-labels.md`
6. `screen-state-matrix.md`
7. `permission-matrix.md`
8. `dense-tokens.md`
9. `measurement-gates.md`
10. `prototype-agent-loop.md`
11. `wireframes.md`
12. `user-research-source.md`

