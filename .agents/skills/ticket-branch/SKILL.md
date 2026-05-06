---
name: ticket-branch
description: Lane task ID(예 L1-T01)를 인자로 받아 Jira 티켓 발급 → GitLab 원격 feature 브랜치 생성 → 로컬 추적 브랜치 + 자동 체크아웃까지 처리한다. docs/spec/boundaries.md와 docs/tasks/L<n>-tasks.md를 기준으로 입력값을 자동 추론하고 confirm 후 acli/glab으로 진행한다. working tree에 무관 변경이 있으면 stash 여부를 사용자에게 확인한다. 사이클 메타는 .agents/scratch/<task-id>.json 에 저장한다.
arguments: task-id
argument-hint: "L1-T01"
---

# ticket-branch

## Purpose

Lane task ID를 인자로 받아 사이클 시작 묶음을 자동화한다.

- Jira 티켓 발급 (acli)
- GitLab 원격 feature 브랜치 생성 (glab, develop 기준)
- 로컬 추적 브랜치 생성 + 자동 체크아웃 (git fetch + git switch)
- 사이클 메타를 `.agents/scratch/<task-id>.json` 에 저장 (예: `L1-T01.json` — commit-mr 에서 같은 task-id 로 매칭하여 로드)

이 skill은 코드를 작성하지 않는다. 작성은 사용자가 한다.

---

## When to Use

- Lane task를 새로 시작할 때 (`/ticket-branch L1-T01`)

다음에는 사용하지 않는다.

- task ID 없는 임시·인프라 작업 (매뉴얼)
- 단순 질문
- 코드 구현

---

## Argument

```
/ticket-branch $task-id
```

- 형식: `L<lane>-<접두어><번호>[<접미어>]`
- 예: `/ticket-branch L1-T01`, `/ticket-branch L3-T05B`, `/ticket-branch L5-B01A`
- `$ARGUMENTS`, `$0` 도 동일 (fallback)
- 인자가 없거나 형식이 안 맞으면 `Failure / Ambiguity Format`으로 확인 후 중단

---

## Inputs to Resolve

| # | 입력값 | 추론 출처 |
|---|---|---|
| 1 | 티켓 제목 | area가 있으면 `[<area_tag>] <task 블록 헤더>` (예: `[BE] 배정 사건·소속·실종자 도메인 데이터 시드 데이터 준비`). 결합 area 시 `[BE/FE]` 슬래시 구분. 직접 영향받는 runtime/infra area가 없으면 area tag를 생략한다. type/scope 는 Jira 제목에 포함하지 않는다 (커밋·MR 제목에만) |
| 2 | 티켓 타입 | default `작업` |
| 3 | 티켓 설명 | task ID, task 파일, Phase, 담당 Spec, 구현 산출물, 완료 기준, 필수 참조를 요약한다. 기준 문서 위치를 남겨 Jira와 Lane task를 추적 가능하게 만든다 |
| 4 | 브랜치 슬러그 | 제목 → kebab-case |
| 5 | 작업 내용 | 구현 산출물 그대로 |
| 6 | area tag | 산출물 텍스트 + Lane 기본 영역 분석 |
| 7 | commit scope | 담당 Spec + 제목 → scope 원칙 기반 후보 |
| 8 | 제목 한국어 표현 | 보통 = 티켓 제목 |
| Derived | commit type | task 접두어 + Phase + 동사 |
| Derived | 브랜치 prefix | `docs/tasks/index.md` 브랜치 규칙과 작업 목적 → 일반 개발은 `feature/`, 릴리즈 전 버그 수정은 `fix/`, 운영 hotfix는 `hotfix/` |

8개 모두 추론하여 scratch에 저장한다 (commit-mr 가 다시 추론하지 않도록).

### Commit Rule Snapshot (`docs/tasks/index.md` §커밋 및 MR 규칙 인용)

권위는 `docs/tasks/index.md` §커밋 및 MR 규칙. 아래 목록은 예시 스냅샷이다. **실행 시 항상 `docs/tasks/index.md` 를 fresh 조회해 area/type whitelist 와 scope 원칙을 검증**한다 (그래도 실패하면 `Failure / Ambiguity Format` 으로 사용자 확인).

- **area_tag** (커밋·MR 제목용): `BE` `FE` `Android` `Infra`
  - 단일: `[BE]`, `[FE]` 등
  - 여러 area 혼합: `[BE/FE/Android/Infra]` 슬래시 구분
  - 문서·계약 변경은 별도 `Docs` area 를 쓰지 않고 영향을 받는 runtime/infra area 만 표시한다. 특정 runtime/infra 에 직접 귀속되지 않는 공통 문서·workflow·agent tooling 변경은 area tag 를 생략한다.
- **type**: `feat` `fix` `refactor` `style` `test` `docs` `chore` `ci` `build`
- **scope**: optional. 고정 whitelist 로 관리하지 않는다. 영향받는 제품 domain/module/package 가 명확할 때만 붙인다.
  - area tag(`BE`, `FE`, `Android`, `Infra`)를 scope 에 반복하지 않는다.
  - 문서 정리, agent/workflow/tooling, guardrail, repo-wide style 변경은 scope 를 비운다.
  - 어느 scope 가 맞는지 설명이 필요할 정도로 애매하면 scope 를 비운다.

---

## Inference Order

1. `$task-id` prefix → Lane (`L1-T01` → `L1`).
2. Lane → Spec/owner 범위는 `docs/spec/boundaries.md §1`, `§1.1`, `§1.2`를 기준으로 확인한다. `docs/tasks/index.md`의 Lane 표는 task 파일 인덱스로만 사용하고 소유권 기준으로 격상하지 않는다.
3. Lane → task 파일은 `docs/tasks/L<n>-tasks.md`를 직접 매핑한다. 예: `L1` → `docs/tasks/L1-tasks.md`.
4. task 파일에서 `$task-id` 헤더 블록 추출. Phase 위치도 기록.
5. 커밋 규칙 매칭. `docs/tasks/index.md` 를 Read 도구로 직접 읽어 §커밋 및 MR 규칙을 **fresh 추출** 후 area/type whitelist 와 scope 원칙을 매칭한다. 그래도 실패하면 `Failure / Ambiguity Format`.
6. area tag 추론. 산출물 키워드:
   - `API` / `DTO` / `publish request` / `Spring` / `repository` → `[BE]`
   - `Room` / `Outbox` / `WorkManager` / `Android` → `[Android]`
   - `React` / `MapLibre` / `board slot` / `상황판` → `[FE]`
   - `Docker` / `Compose` / `Jenkins` / `tile server` / `observability` → `[Infra]`
   - 특정 runtime/infra 에 직접 귀속되지 않는 공통 문서·workflow·agent tooling 변경 → area tag 생략
7. commit type 추론.
   - `B` 접두어 (bootstrap/seed) → `chore` 또는 `feat`
   - `T` + Phase 1~3 + "구현/추가" → `feat`
   - `I` (integration test) → `test`
   - `D` (demo/rehearsal) → `chore`
8. commit scope 추론. 담당 Spec + 제목 → scope 원칙 기반 후보 도출. 제품 domain/module/package 가 명확할 때만 scope 를 채우고, 문서 정리, agent/workflow/tooling, guardrail처럼 특정 제품 domain/module/package 로 좁히기 어려운 변경은 빈 scope 허용.
9. 브랜치 prefix derive. `docs/tasks/index.md` 브랜치 규칙을 fresh 확인한다. 일반 Lane 개발 task는 `feature/`를 기본으로, 릴리즈 전 버그 수정은 `fix/`, 운영 hotfix는 `hotfix/`를 사용한다. `release/`는 Jira task branch가 아니라 릴리즈 branch 자체라 자동 생성 대상에서 제외한다.
10. 슬러그 derive. 제목 → kebab-case (의미 기반 영문화).
11. 티켓 description 합성. Jira 티켓만 보고도 Lane task와 기준 문서를 역추적할 수 있도록 task ID, task 파일, Phase, 담당 Spec, 구현 산출물, 완료 기준, 필수 참조를 포함한다. 담당 Spec이 2개 이상이면 커밋 scope 후보도 함께 표시하고 사용자 confirm 을 받는다.

    **형식 — 태그 다음 줄에 본문이 와야 한다 (가독성)**:
    ```
    [Task]
    ID: <task-id>
    File: <task-file>
    Phase: <phase>
    Lane: <lane>
    Spec: <specs>

    [목표]
    <구현 산출물 요약>

    [완료 기준]
    <완료 기준 요약>

    [필수 참조]
    <필수 참조 요약>
    ```
    `[Task] ID: ...` 처럼 태그 옆에 본문을 붙여 쓰지 않는다.

---

## Flow

이 skill 의 결정적 부분(acli/glab/git 호출)은 `scripts/` 디렉토리의 스크립트로 분리되어 있다. SKILL 은 LLM 판단 영역(추론·confirm·상태 저장) 만 담당하고 외부 시스템 호출은 스크립트에 위임한다.

1. Inference Order 1~11 수행.
2. **PAUSE**: 추론 결과를 `Confirm Format` 으로 출력하고 사용자 confirm 대기.
3. confirm 후 다음을 순차 실행.
4. 티켓 description 을 `/tmp/ticket-branch-desc.txt` 에 작성.
5. 티켓 발급 — `scripts/ticket-create.sh` 호출:
   ```bash
   JIRA_KEY=$(.agents/skills/ticket-branch/scripts/ticket-create.sh \
     --project S14P31C106 \
     --type "<2번 ticket type>" \
     --summary "<Jira 티켓 제목>" \
     --desc-file /tmp/ticket-branch-desc.txt \
     --assignee "@me")
   ```
   - `--summary` 는 area가 있으면 `[<area_tag>] <task 제목>` 형식 (예: `[BE] 배정 사건·소속·실종자 도메인 데이터 시드 데이터 준비`), area가 없으면 `<task 제목>` 형식.
   - 결합 area: `[BE/FE]` 슬래시 구분.
   - type/scope 는 Jira 제목에 포함하지 않는다 (그 둘은 커밋·MR 제목에만 사용).
   - stdout 으로 발급된 issue key (예: `S14P31C106-77`) 가 출력된다. 실패 시 stderr 로그를 사용자에게 보고하고 중단.
6. 브랜치 셋업 — `scripts/branch-setup.sh` 호출:
   ```bash
   .agents/skills/ticket-branch/scripts/branch-setup.sh \
     --project s14-final/S14P31C106 \
     --branch "<prefix>${JIRA_KEY}-<슬러그>" \
     --ref develop
   ```
   - 종료 코드 분기:
     - `0` — 원격 생성 + git switch 까지 완료. 다음 step 진행.
     - `2` — working tree 에 무관 변경(tracked/untracked)이 있어 switch 보류. **PAUSE**: 변경 파일 목록을 사용자에게 보여주고 stash 여부 확인.
       - 동의 시 `git stash push -u -m "WIP: <간단 설명>" -- <변경 파일>` 직접 실행 후 `branch-setup.sh` 재호출.
       - 매뉴얼 처리 원하면 SKILL 중단.
       - **stash pop 은 자동으로 하지 않는다**. 사용자가 적절한 시점에 직접 처리.
     - `1` — 그 외 실패. stderr 로그를 사용자에게 보고 후 중단.
7. `.agents/scratch/<task-id>.json` 저장. 디렉토리가 없으면 `mkdir -p .agents/scratch/` 후 작성. `Scratch JSON Schema` 참조.
8. 완료 메시지 출력
   - 발급된 JIRA-KEY, 생성된 브랜치명
   - **수동 후속 안내**: "생성된 Jira 티켓 `<JIRA-KEY>`를 현재 스프린트에 직접 추가해주세요." — acli 로 발급한 티켓은 기본적으로 backlog 에 들어가므로 사용자가 Jira UI 에서 스프린트에 할당해야 한다.
   - 다음 단계 안내: "코드 작성 후 `/commit-mr <task-id>` 호출"

---

## Confirm Format

```markdown
Task ID: <task-id>
Lane: <Lane> (<담당 Spec 목록>)
Task 제목: <task 헤더>

추론 결과:
  티켓 타입:       <2>
  area tag:        <6>
  브랜치 prefix:   <derived>
  슬러그:          <4>
  최종 브랜치:     <prefix>(JIRA-KEY 발급 후)-<슬러그>
  Jira 티켓 제목:  <area가 있으면 [<area_tag>] <8>, 없으면 <8>>

티켓 description:
  [Task]
  ID: <task-id>
  File: <task-file>
  Phase: <phase>
  Lane: <Lane>
  Spec: <담당 Spec 목록>

  [목표]
  <구현 산출물 요약>

  [완료 기준]
  <완료 기준 요약>

  [필수 참조]
  <필수 참조 요약>

이대로 진행할까요? 수정할 항목 있으면 알려주세요.
```

> commit type / commit scope / 최종 커밋·MR 표기는 이 단계에서 사용자에게 노출하지 않는다. 추론은 수행하여 scratch 에 저장하고, 검토는 `/commit-mr` 단계에서 진행한다.

---

## Scratch JSON Schema

`.agents/scratch/<task-id>.json` 에 다음 키-값 저장 (파일명은 task-id 그대로, 예: `L1-T01.json`, `L3-T05B.json`).

```json
{
  "task_id": "L1-B01",
  "lane": "L1",
  "specs": ["S1-1"],
  "phase": "Phase -1",
  "jira_key": "S14P31C106-77",
  "branch": "feature/S14P31C106-77-incident-fixture-seed",
  "branch_prefix": "feature/",
  "slug": "incident-fixture-seed",
  "title": "배정 사건·소속·실종자 도메인 데이터 시드 데이터 준비",
  "ticket_type": "작업",
  "area_tag": "BE | FE | Android | Infra | '' (단일 또는 슬래시 결합, 예: 'BE' 또는 'BE/FE')",
  "commit_type": "feat | fix | refactor | style | test | docs | chore | ci | build",
  "commit_scope": "incident 또는 빈 문자열",
  "commit_scope_candidates": ["incident"],
  "task_file": "docs/tasks/L1-tasks.md",
  "created_at": "2026-05-06T..."
}
```

`area_tag` 는 대괄호 없이 약어로만 저장 (예: `BE`, 결합 시 `BE/FE`). area가 없으면 빈 문자열로 저장한다. 커밋·MR 제목엔 area가 있을 때만 `[BE]` 형식으로 사용. GitLab MR Label 은 `commit-mr` SKILL 에서 매핑 테이블(`⌨️ BE` 등 정식 라벨명)을 적용해 변환한다.

---

## Failure / Ambiguity Format

```markdown
Inference required

요청:
가능한 후보:
판별 근거:
애매한 지점:
추천 진행 범위:
선택지:
```

발생 가능 케이스:

- 인자 미입력 또는 정규식 `L\d+-[A-Z]\d+[A-Z]?` 불일치
- task ID 가 task 파일에 없음
- area tag 추론 모호 (산출물에 BE+Android 혼재 등)
- scope 원칙 적용 실패
- 담당 Spec 이 2개 이상인데 commit scope 를 하나로 확정할 근거가 부족함
- working tree 무관 변경(tracked/untracked, stash 여부 확인용)
- `acli` / `glab` 명령 실패

---

## Pause Points

1. 추론 결과 confirm 대기 (Flow step 2)
2. working tree 무관 변경 stash 여부 확인 (Flow step 6, 조건부 — `branch-setup.sh` 가 exit 2 로 알림)

---

## Constraints

- 코드를 작성하지 않는다.
- `docs/tasks/`, `docs/spec/` 등 문서를 수정하지 않는다.
- `git push --force` 등 destructive git 명령을 사용하지 않는다.
- 사용자 confirm 없이 acli/glab API 호출하지 않는다.
- task ID 없이 호출되면 즉시 중단.
- stash pop 은 자동 실행하지 않는다.
- **`.agents/scratch/` 아래 모든 파일은 절대 `git add` / `git commit` 대상에 포함하지 않는다.** 이 skill 은 git add 자체를 수행하지 않지만, 안전 장치 차원에서 명시한다.
