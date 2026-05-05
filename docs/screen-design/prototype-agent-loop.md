# 프로토타입 생성 Agent Loop

상태: 초안. 이 문서는 AI(Claude/Codex 등)에게 Suri-Map 화면 프로토타입 생성을 시킬 때의 프롬프트 골격과 반복 루프를 정의한다.

## 목적

- AI가 KRDS 시민포털 톤이나 네이버 지도 톤으로 흘러가는 것을 막는다.
- 매 요청마다 같은 컨텍스트가 주입되도록 골격을 표준화한다.
- 결과물 검수를 객관화하고, 실패 시 재요청 루프를 명시한다.

## 코드 작업용 agent loop와의 관계

- 코드 작업 (TDD): `docs/tasks/agent-loop-guide.md`, `docs/tasks/recommendations.md`
- 화면/프로토타입: 이 문서

두 루프는 분리한다. 화면 루프는 RED test가 아니라 **anti-pattern 검수**가 evaluator 역할.

## 1. 패턴

화면 프로토타입은 **Evaluator-Optimizer** 패턴을 쓴다.

- **Optimizer**: AI 디자인/프로토타입 생성기
- **Evaluator**: anti-pattern 카탈로그 + measurement gates 기반 검수자(사람 또는 별도 AI)
- 루프: 생성 → 검수 → reject 시 negative example과 함께 재생성

```
[입력: 컨텍스트 골격 + 화면 타깃]
   │
   ▼
generator (AI)  ──생성──▶  화면 산출물
                              │
                              ▼
                        reviewer (사람)
                              │
              ┌───────────────┴───────────────┐
              ▼                                ▼
           PASS                              FAIL
              │                                │
              ▼                                ▼
       다음 화면 또는 high-fi      negative example 추가 후 재생성
                                  (max N회, 초과 시 사람 직접 작업)
```

## 2. 매 요청에 주입할 컨텍스트 골격

AI에 프로토타입 요청 시 반드시 다음을 컨텍스트로 제공한다. 빠지면 일반 지도 앱·시민포털 톤으로 흐른다.

### 2.1 필수 컨텍스트 블록

```
[제품 정의]
Suri-Map은 경찰 실종 수색 운영 보조 시스템이다.
112 공식 기록 시스템이 아니다. 네이버 지도 앱이 아니다.
웹은 지휘 상황판(데스크톱 전용), 폴리폰은 현장 단말이다.
폴리폰에서 웹 상황판 접근은 지원하지 않는다 (PRD §8.8).

[필수 도메인 사실 — 자주 틀림]
- 1팀(예: 6명)이 폴리폰 1대를 공유한다 (PRD FR-33). 폴리폰 수 ≠ 인원 수.
- OP는 재수색·범위 변경 단위 (`RE_SEARCH`/`AREA_CHANGED`/`OTHER`). 인수인계 단위 아님 (FR-12).
- 인수인계·근무 교대는 DutyShift (FR-32, FR-37). OP와 다르다.
- 동시 활성 OP는 사건당 1개. 과거 OP는 readonly 보존.
- 현장 마커 생성은 모든 폴리폰 계정에 허용. 수정·삭제만 권한 차등 (PRD §8.4).
- 사건 가져오기 권한은 실종팀 간부 + 지구대/파출소 팀장·당직자만.
- 사건 종료는 terminal. 재오픈 UI 없음 (ADR-0022).

[디자인 시스템 역할]
- KRDS를 base 디자인 시스템으로 사용한다.
- 단, 시민포털 기본 토큰이 아니라 KRDS-Ops dense variant를 사용한다.
  (`docs/screen-design/dense-tokens.md` 참조)
- 네이버 지도는 지도 조작 패턴(컨트롤 위치, 바텀시트 조작)만 참고한다.
- 검색·POI·길찾기·장소 저장·리뷰·즐겨찾기·마이페이지는 만들지 않는다.

[현장 사용 맥락]
- 웹: 본부/지구대 PC, 1920×1080, 마우스+키보드, 다중 사건 동시 운영.
- 폴리폰: Android, 야외, 장갑, 무전기 동시 사용, 단손/엄지 조작, 30분+ 오프라인 가정.
- 자세한 환경 가정은 `docs/screen-design/field-context.md` 참조.

[금지 사항]
- 자동 누락 확정, 다음 구역 추천, 위험도 판단 시각화 금지 (PRD FR-23, ADR-0027).
- AI 요약에 추측·지시·위험도 표현 금지 (ADR-0034).
- 빈 영역 hatch/빨간 패턴, "추천 구역" 카드, "위험" 색 자동 부여 금지.
- OP를 시프트/인수인계로 표현 금지 (anti-patterns §1.6.1).
- 자세한 anti-pattern은 `docs/screen-design/anti-patterns.md` 참조.

[라벨 표기 규칙]
- 도메인 객체·액션·상태 라벨은 `docs/screen-design/screen-labels.md` 규칙을 따른다.

[권한 매트릭스]
- PRD §8.4 권한 매트릭스 (6 계정 유형 × 14 동작) 정렬.
- 액션 가시성·활성화는 계정 유형별로 분기.

[정보 밀도 목표]
- dense-tokens.md §1의 목표를 만족해야 한다.
- 시민포털 카드 padding 40px·본문 17px을 그대로 쓰지 않는다.

[상태 완전성]
- screen-state-matrix.md의 해당 화면 행에 정의된 모든 상태를 표현해야 한다.

[연결 SC]
- 화면이 어느 SC(SC-01 ~ SC-12, harness-scenarios.md)를 검증하는지 명시.

[측정 게이트]
- 결과물은 measurement-gates.md의 G1~G6을 통과해야 한다.
```

### 2.2 화면 타깃 블록

위 골격에 더해 화면별로 다음을 명시한다.

```
[화면 타깃]
- 화면 이름: <예: 사건 상황판 메인>
- 플랫폼: <Web | Polifon>
- 연결 SC ID: <예: SC-05, SC-06> (harness-scenarios.md 참조)
- 주 사용자 역할: <예: 실종팀 지휘 계정>
  (PRD §8.4 권한 매트릭스 6종 중 명시)
- 핵심 행동 (1~3개): <예: OP별 경로 확인, 폴리폰 stale 판단, 마커 확인>
- 즉시 파악해야 하는 상태: <예: 사건 진행 단계, 활성 OP·사유, 폴리폰 동기화 상태>
- 금지 행동: <화면별 추가>
- 표현해야 하는 상태 분기: <screen-state-matrix.md의 해당 행 셀들>
- 사용해야 하는 board_shell_slots: <boundaries.md §9.2 참조>
```

### 2.3 negative example 블록 (재요청 시)

```
[직전 시도의 anti-pattern]
- 발견된 anti-pattern: <어떤 항목이 어떻게 잘못됐는지>
- anti-patterns.md의 어느 항목과 충돌: <§번호>
- 다음 시도에서는: <대체 패턴>
```

## 3. 단계별 루프

### 3.1 Low-fi 루프

- **목표**: 색·장식 없이 IA·구조만
- **요청 골격**: §2.1 + §2.2 + "low-fi 와이어프레임만. 색·아이콘·이미지 금지. 박스·라인·텍스트만 사용. 컨트롤은 한국어 텍스트 라벨 (예: `확대`, `축소`, `현재 위치`, `전체 화면`, `주의`). +/−/⌖/⛶/⚠ 같은 유니코드 기호도 사용 금지 — 의미가 모호해질 수 있다."
- **검수**: anti-patterns.md §2(KRDS/네이버 패턴 오용), §5(웹 dashboard 패턴), §3.4(simple_mode), screen-state-matrix.md 행 채워졌는지
- **PASS 기준**: 핵심 행동·상태 분기·정보 밀도 목표가 구조 수준에서 만족
- **검수 환경**: HTML mock은 1920×1080 viewport 기준으로 검수 가능해야 한다. 검수용 메타·variant·self-review는 **별도 파일**로 분리한다 (`<screen>-vN-review.html`). main canvas는 body padding 0으로 viewport에 정확히 들어오게 한다.
- **폰트**: 한국어 렌더링을 위해 `Pretendard, "Noto Sans KR", "Malgun Gothic", "Apple SD Gothic Neo", sans-serif` stack 명시. low-fi라도 monospace 폰트 단독 사용 금지 (한글 깨짐).
- **검수 환경 폰트**: 검수자(특히 Playwright 기반 자동 검수)는 OS에 Pretendard 또는 Noto Sans KR 중 하나 설치 필요. 검수 환경에 한국어 폰트가 없으면 모든 한글이 box glyph로 보임. CI 환경은 Noto Sans CJK KR 패키지 설치를 default로.
- **마커/아이콘 의미 글리프 OK**: low-fi 원칙은 "색·이미지 배제"이지 "의미 없는 추상 도형 강제"가 아니다. 도메인 의미가 직결되는 단순 SVG 글리프(예: 마커 5종, sub-type 시각 구분)는 low-fi에서도 사용한다. 단 흑백·1~2px stroke만 사용. 모든 마커는 아이콘 + 짧은 텍스트 라벨 조합으로 표현 (anti-patterns §1.6.11).

### 3.2 High-fi 루프

- **목표**: 토큰 적용 + 실 데이터 시뮬레이션
- **선행**: dense-tokens.md 토큰 값 확정
- **요청 골격**: §2.1 + §2.2 + "dense-tokens.md의 토큰만 사용. 임의 색·폰트 추가 금지" + (low-fi 결과를 reference로 첨부)
- **검수**: G1~G6 모든 게이트 적용
- **PASS 기준**: measurement-gates.md 통과 + anti-pattern 0건

### 3.3 인터랙티브 프로토타입 루프

- **목표**: 사용자 흐름이 클릭 가능한 형태
- **요청 골격**: §2.1 + 사용자 흐름 정의(checklist §2) + 화면 간 전환 명시
- **검수**: SC-01 ~ SC-12의 주요 흐름 관찰 가능 + G3 (인지 부하·태스크 효율) 게이트
- **PASS 기준**: 사용자 흐름이 끊김 없이 진행 + 상태 분기 표현 가능

## 4. 재시도 정책

| 시도 | 처리 |
|---|---|
| 1차 FAIL | anti-pattern 명시 + negative example로 재요청 |
| 2차 FAIL | 컨텍스트 골격에 빠진 항목 있는지 확인 + 재요청 |
| 3차 FAIL | 사람이 직접 그리거나, 화면 정의를 더 작게 분할 |

루프 중단 사유는 anti-patterns.md §7 실수 사례 로그에 기록한다.

## 5. AI 도구별 주의

### Claude Code

- 컨텍스트 골격 전체를 세션 시작 시 주입하면 토큰 소모가 큼. 외부 파일을 `@docs/screen-design/...`로 참조하는 게 효율적.
- 복잡한 화면은 한 번에 1개씩 요청. 여러 화면 동시 요청 시 정체성이 흐려짐.

### Codex / 기타

- 도구별 시스템 프롬프트 차이로 KRDS-Ops 정체성이 더 약하게 적용될 수 있음. 매 요청마다 §2.1 컨텍스트 명시 필수.

### 일반

- 첨부 이미지 reference 사용 시: 네이버 지도·구글 맵 캡처를 reference로 넣지 않는다 (정체성 오염).
- 첨부할 reference: KRDS 자료, 자체 low-fi 결과, anti-patterns.md의 ASCII 도식.

## 6. 산출물 명명

| 단계 | 파일명 형식 | 위치 |
|---|---|---|
| Low-fi | `lo-<screen-name>-v<N>.{html,png}` | `docs/screen-design/artifacts/lo/` |
| High-fi | `hi-<screen-name>-v<N>.{html,png}` | `docs/screen-design/artifacts/hi/` |
| 프로토타입 | `proto-<flow-name>-v<N>.html` | `docs/screen-design/artifacts/proto/` |

- `<screen-name>`: kebab-case, screen-state-matrix.md 화면 행 이름과 동일 (예: `web-situation-board-main`, `polifon-marker-bottomsheet`).
- `<flow-name>`: 사용자 흐름 이름 (예: `polifon-search-start-to-marker`, `web-handover-flow`).
- `<N>`: 1부터 시작. 큰 변경 시 증가.
- Figma 등 외부 도구 사용 시 같은 이름으로 export하고 위 위치에 png/html 사본 보관.
- 산출물 git tracking: 무거운 PNG는 LFS 또는 별도 저장소 검토 (운영 측 결정).
- 보관 정책: 같은 화면은 최신 승인 후보 1개와 review 1개만 작업 디렉토리에 남긴다. 이전 vN 파일은 Git 이력으로 보관하고 handoff 전 삭제한다.

## 7. 검수 기록 템플릿

매 PASS/FAIL 결정 시 기록.

```markdown
- 화면: <name>
- 시도 번호: N
- 시점: YYYY-MM-DD
- 검수자: <name>
- 결과: PASS | FAIL
- 통과/미달 게이트: G1-1 PASS, G2-1 FAIL, ...
- 발견 anti-pattern: <항목>
- 다음 액션: <재요청 / 사람 직접 / 다음 화면 진행>
```

## 8. 변경 시 영향

- §2.1 컨텍스트 골격 변경 → 모든 진행 중 프로토타입 작업에 통보
- 게이트 추가 → 이미 PASS된 화면 재검수 필요 여부 결정
- anti-pattern 추가 → §2.3에 자동 반영 가능하도록 anti-patterns.md §7와 연동
