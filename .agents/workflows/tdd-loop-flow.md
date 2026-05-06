# Agentic TDD Loop

Entry point skill: `.agents/skills/tdd-loop/SKILL.md`. This workflow is the detailed tester/coder/reviewer role guide for that skill.

## 역할

- tester: 기준 문서와 task 완료 기준을 읽고 실패 테스트 또는 실패 fixture를 먼저 만든다.
- coder: tester 산출물을 통과시키는 최소 구현을 한다. 도구별 wrapper에서 constructor라고 부를 때도 같은 역할이다.
- reviewer: diff, 테스트 증거, Lane 침범, fixture exactness를 검토한다.

## 루프

1. RED: 실패해야 하는 테스트, fixture, harness assertion을 먼저 만든다.
2. GREEN: 실패 원인 하나를 닫는 최소 구현만 한다.
3. VERIFY: 같은 명령을 다시 실행하고 실패가 재현되지 않는지 확인한다.
4. REVIEW: 변경 범위가 task owner 범위 안인지 확인한다.

## 완료 기준

- 테스트 명령과 결과가 MR에 남아야 한다.
- TODO 또는 Lane task 체크박스는 완료 기준이 관찰 가능하게 증명된 뒤에만 체크한다.
- fixture ID, public API, event payload, DB column 의미가 기준 문서와 일치해야 한다.
