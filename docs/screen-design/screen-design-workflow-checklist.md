# 화면 설계 검토 기록

2026-05-05에 정리한 디자인 검토 항목의 요약이다. 전체 화면 명세를 완료한 뒤에만 개발을 시작하도록 하던 절차와 반복 프롬프트는 제거했다. 현재 작업 순서는 [AGENTS.md](../../AGENTS.md)를 따르며, 아래 자료는 필요한 화면을 검토할 때 참고한다.

## 당시 검토한 질문

| 질문 | 배경·상세 자료 |
|---|---|
| 야외·장갑·한 손 조작·통신 단절 상황에서 사용할 수 있는가? | [field-context.md](./field-context.md), [user-research-source.md](./user-research-source.md) |
| 사용자가 해야 할 행동과 화면에서 확인할 상태가 보이는가? | [wireframes.md](./wireframes.md), [screen-state-matrix.md](./screen-state-matrix.md) |
| 권한 없음·읽기 전용·종료 사건·오프라인·실패를 구분할 수 있는가? | [permission-matrix.md](./permission-matrix.md), [screen-state-matrix.md](./screen-state-matrix.md) |
| 라벨·색·아이콘이 서로 다른 업무 상태를 혼동시키지 않는가? | [screen-labels.md](./screen-labels.md), [anti-patterns.md](./anti-patterns.md) |
| 텍스트·터치 영역·정보 밀도가 사용 환경에 맞는가? | [dense-tokens.md](./dense-tokens.md), [measurement-gates.md](./measurement-gates.md) |

당시에는 Web 지휘 상황판과 Android 현장 입력의 목적을 구분하고, KRDS는 기본 UI 요소에, 지도 서비스는 지도 조작 패턴에 참고했다. 토큰 수치와 도메인·권한 가정은 현재 제품에서 다시 대조할 대상이다.

## 자료가 보여주는 범위

- 와이어프레임은 화면 구성 의도다.
- 프로토타입은 시연용 상호작용이다.
- 실기기 기록은 해당 환경·시점에 직접 확인한 결과다.

이 자료만으로 현재 API 연결·데이터 저장·알림 수신을 확인했다고 판단하지 않는다. 산출물은 [README.md](./README.md)에서 찾고, 제작 환경의 주의사항은 [prototype-agent-loop.md](./prototype-agent-loop.md)를 참고한다.
