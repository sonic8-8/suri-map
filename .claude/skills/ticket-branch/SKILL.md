---
name: ticket-branch
description: Lane task ID(예 L1-T01)를 인자로 받아 Jira 티켓 발급 → GitLab 원격 feature 브랜치 생성 → 로컬 추적 브랜치 + 자동 체크아웃까지 처리한다. docs/tasks/L<n>-tasks.md를 파싱해 8개 입력값을 자동 추론하고 confirm 후 acli/glab으로 진행한다. working tree에 무관 변경이 있으면 stash 여부를 사용자에게 확인한다. 사이클 메타는 .claude/scratch/<task-id>.json 에 저장한다 (예: L1-T01.json).
arguments: task-id
argument-hint: "L1-T01"
---

# ticket-branch

## Purpose

Lane task ID를 인자로 받아 사이클 시작 묶음을 자동화한다.

- Jira 티켓 발급 (acli)
- GitLab 원격 feature 브랜치 생성 (glab, develop 기준)
- 로컬 추적 브랜치 생성 + 자동 체크아웃 (git fetch + git switch)
- 사이클 메타를 `.claude/scratch/<task-id>.json` 에 저장 (예: `L1-T01.json` — commit-mr 에서 같은 task-id 로 매칭하여 로드)

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
| 1 | 티켓 제목 | `[<area_tag>] <task 블록 헤더>` (예: `[BE] 배정 사건·소속·실종자 도메인 데이터 시드 데이터 준비`). 결합 area 시 `[BE/FE]` 슬래시 구분. type/scope 는 Jira 제목에 포함하지 않는다 (커밋·MR 제목에만) |
| 2 | 티켓 타입 | default `작업` |
| 3 | 티켓 설명 | `[배경]` + `[목표]` 두 섹션만 합성. 다른 메타(필수 참조·시나리오·FR·구현 범위·완료 기준 등)는 task 문서에 이미 있어 Jira 본문 중복을 피한다 |
| 4 | 브랜치 슬러그 | 제목 → kebab-case |
| 5 | 작업 내용 | 구현 산출물 그대로 |
| 6 | area tag | 산출물 텍스트 + Lane 기본 영역 분석 |
| 7 | commit scope | 담당 Spec + 제목 → 화이트리스트 매칭 |
| 8 | 제목 한국어 표현 | 보통 = 티켓 제목 |
| Derived | commit type | task 접두어 + Phase + 동사 |
| Derived | 브랜치 prefix | type → `feature/` / `fix/` / `release/` / `hotfix/` |

8개 모두 추론하여 scratch에 저장한다 (commit-mr 가 다시 추론하지 않도록).

### Whitelists (`docs/tasks/index.md` §커밋 및 MR 규칙 인용)

권위는 `docs/tasks/index.md` §커밋 및 MR 규칙. 아래 인용은 참고용. **매칭 실패 시 docs/tasks/index.md 를 Read 도구로 fresh 조회 후 재매칭**한다 (그래도 실패하면 `Failure / Ambiguity Format` 으로 사용자 확인).

- **area_tag** (커밋·MR 제목용): `BE` `FE` `Android` `Infra` `Docs`
  - 단일: `[BE]`, `[FE]` 등
  - 여러 area 혼합: `[BE/FE/Android/Infra]` 슬래시 구분 (팀 컨벤션 — index.md 본문 표기 `[BE][FE]` 대신 슬래시 통일)
  - 순수 문서·계약 변경은 `[Docs]` 하나
- **type**: `feat` `fix` `refactor` `style` `test` `docs` `chore` `ci` `build`
- **scope**: `incident` `auth` `police_phone` `retention` `event` `overall_search_area` `area` `path` `sync` `marker` `photo` `notification` `board` `package` `tiles` `op` `handover` `search_history_summary` `contract` `infra` `docs`
  - scope 는 domain/module/package 자리다. 특정 domain 으로 좁히기 어려운 전역 style 또는 tooling 변경은 scope 를 비울 수 있다.

---

## Inference Order

1. `$task-id` prefix → Lane (`L1-T01` → `L1`). `docs/tasks/lane-registry.md` §2.
2. Lane → task 파일 (`L1` → `docs/tasks/L1-tasks.md`). `lane-registry.md` §3.
3. task 파일에서 `$task-id` 헤더 블록 추출. Phase 위치도 기록.
4. 화이트리스트 매칭. SKILL 본문 `Whitelists` 섹션 인용을 **1차** 로 사용. **매칭 실패 시** `docs/tasks/index.md` 를 Read 도구로 직접 읽어 §커밋 및 MR 규칙 표를 **fresh 추출** 후 재매칭. 그래도 실패하면 `Failure / Ambiguity Format`.
5. area tag 추론. 산출물 키워드:
   - `API` / `DTO` / `publish request` / `Spring` / `repository` → `[BE]`
   - `Room` / `Outbox` / `WorkManager` / `Android` → `[Android]`
   - `React` / `MapLibre` / `board slot` / `상황판` → `[FE]`
   - `Docker` / `Compose` / `Jenkins` / `tile server` / `observability` → `[Infra]`
   - 순수 문서·계약 변경 → `[Docs]`
6. commit type 추론.
   - `B` 접두어 (bootstrap/seed) → `chore` 또는 `feat`
   - `T` + Phase 1~3 + "구현/추가" → `feat`
   - `I` (integration test) → `test`
   - `D` (demo/rehearsal) → `chore`
7. commit scope 추론. 담당 Spec + 제목 → 화이트리스트 매칭. 전역 style/tooling 변경은 빈 scope 허용.
8. 브랜치 prefix derive. `feat` → `feature/`, `fix` → `fix/`, 그 외 default `feature/`.
9. 슬러그 derive. 제목 → kebab-case (의미 기반 영문화).
10. 티켓 description 합성. **`[배경]` + `[목표]` 두 섹션만** 작성한다. 그 외 메타(구현 범위·필수 참조·시나리오·FR·완료 기준 등)는 task 문서에 이미 있어 Jira 본문 중복을 피한다.

    **형식 — 태그 다음 줄에 본문이 와야 한다 (가독성)**:
    ```
    [배경]
    <배경 설명 본문>

    [목표]
    <목표 설명 본문>
    ```
    `[배경] 모든 Lane이...` 처럼 태그 옆에 본문을 붙여 쓰지 않는다.

---

## Flow

이 skill 의 결정적 부분(acli/glab/git 호출)은 `scripts/` 디렉토리의 스크립트로 분리되어 있다. SKILL 은 LLM 판단 영역(추론·confirm·상태 저장) 만 담당하고 외부 시스템 호출은 스크립트에 위임한다.

1. Inference Order 1~10 수행.
2. **PAUSE**: 추론 결과를 `Confirm Format` 으로 출력하고 사용자 confirm 대기.
3. confirm 후 다음을 순차 실행.
4. 티켓 description 을 `/tmp/ticket-branch-desc.txt` 에 작성.
5. 티켓 발급 — `scripts/ticket-create.sh` 호출:
   ```bash
   JIRA_KEY=$(.claude/skills/ticket-branch/scripts/ticket-create.sh \
     --project S14P31C106 \
     --type "<2번 ticket type>" \
     --summary "[<area_tag>] <task 제목>" \
     --desc-file /tmp/ticket-branch-desc.txt \
     --assignee "@me")
   ```
   - `--summary` 는 반드시 `[<area_tag>] <task 제목>` 형식 (예: `[BE] 배정 사건·소속·실종자 도메인 데이터 시드 데이터 준비`).
   - 결합 area: `[BE/FE]` 슬래시 구분.
   - type/scope 는 Jira 제목에 포함하지 않는다 (그 둘은 커밋·MR 제목에만 사용).
   - stdout 으로 발급된 issue key (예: `S14P31C106-77`) 가 출력된다. 실패 시 stderr 로그를 사용자에게 보고하고 중단.
6. 브랜치 셋업 — `scripts/branch-setup.sh` 호출:
   ```bash
   .claude/skills/ticket-branch/scripts/branch-setup.sh \
     --project s14-final/S14P31C106 \
     --branch "<prefix>${JIRA_KEY}-<슬러그>" \
     --ref develop
   ```
   - 종료 코드 분기:
     - `0` — 원격 생성 + git switch 까지 완료. 다음 step 진행.
     - `2` — working tree 에 무관 tracked 변경이 있어 switch 보류. **PAUSE**: 변경 파일 목록을 사용자에게 보여주고 stash 여부 확인.
       - 동의 시 `git stash push -u -m "WIP: <간단 설명>" -- <변경 파일>` 직접 실행 후 `branch-setup.sh` 재호출.
       - 매뉴얼 처리 원하면 SKILL 중단.
       - **stash pop 은 자동으로 하지 않는다**. 사용자가 적절한 시점에 직접 처리.
     - `1` — 그 외 실패. stderr 로그를 사용자에게 보고 후 중단.
7. `.claude/scratch/<task-id>.json` 저장. 디렉토리가 없으면 `mkdir -p .claude/scratch/` 후 작성. `Scratch JSON Schema` 참조.
8. 완료 메시지 출력
   - 발급된 JIRA-KEY, 생성된 브랜치명
   - **수동 후속 안내**: "생성된 Jira 티켓 `<JIRA-KEY>`를 현재 스프린트에 직접 추가해주세요." — acli 로 발급한 티켓은 기본적으로 backlog 에 들어가므로 사용자가 Jira UI 에서 스프린트에 할당해야 한다.
   - 다음 단계 안내: "코드 작성 후 `/commit-mr <task-id>` 호출"

---

## Confirm Format

```markdown
Task ID: <task-id>
Lane: <Lane> (<담당 Spec>)
Task 제목: <task 헤더>

추론 결과:
  티켓 타입:       <2>
  area tag:        <6>
  브랜치 prefix:   <derived>
  슬러그:          <4>
  최종 브랜치:     <prefix>(JIRA-KEY 발급 후)-<슬러그>
  Jira 티켓 제목:  [<area_tag>] <8>

티켓 description:
  [배경]
  <배경 설명 본문>

  [목표]
  <목표 설명 본문>

이대로 진행할까요? 수정할 항목 있으면 알려주세요.
```

> commit type / commit scope / 최종 커밋·MR 표기는 이 단계에서 사용자에게 노출하지 않는다. 추론은 수행하여 scratch 에 저장하고, 검토는 `/commit-mr` 단계에서 진행한다.

---

## Scratch JSON Schema

`.claude/scratch/<task-id>.json` 에 다음 키-값 저장 (파일명은 task-id 그대로, 예: `L1-T01.json`, `L3-T05B.json`).

```json
{
  "task_id": "L1-B01",
  "lane": "L1",
  "spec": "S1-1",
  "phase": "Phase -1",
  "jira_key": "S14P31C106-77",
  "branch": "feature/S14P31C106-77-incident-fixture-seed",
  "branch_prefix": "feature/",
  "slug": "incident-fixture-seed",
  "title": "배정 사건·소속·실종자 도메인 데이터 시드 데이터 준비",
  "ticket_type": "작업",
  "area_tag": "BE | FE | Android | Infra | Docs (단일 또는 슬래시 결합, 예: 'BE' 또는 'BE/FE')",
  "commit_type": "feat | fix | refactor | style | test | docs | chore | ci | build",
  "commit_scope": "incident 또는 빈 문자열",
  "task_file": "docs/tasks/L1-tasks.md",
  "created_at": "2026-05-06T..."
}
```

`area_tag` 는 대괄호 없이 약어로만 저장 (예: `BE`, 결합 시 `BE/FE`). 커밋·MR 제목엔 `[BE]` 형식으로 사용. GitLab MR Label 은 `commit-mr` SKILL 에서 매핑 테이블(`⌨️ BE` 등 정식 라벨명)을 적용해 변환한다.

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
- scope 화이트리스트 매칭 실패
- working tree 무관 tracked 변경 (stash 여부 확인용)
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
- **`.claude/scratch/` 아래 모든 파일은 절대 `git add` / `git commit` 대상에 포함하지 않는다.** 이 skill 은 git add 자체를 수행하지 않지만, 안전 장치 차원에서 명시한다.
