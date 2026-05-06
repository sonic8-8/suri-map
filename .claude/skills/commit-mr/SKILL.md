---
name: commit-mr
description: Lane task ID(예 L1-T01)를 인자로 받아 사이클 마무리 묶음을 자동화한다. .claude/scratch/<task-id>.json 에서 사이클 메타를 읽고 git diff 기반으로 커밋 메시지를 작성한 뒤 사용자 confirm 후 commit + push + GitLab MR 생성을 수행한다. MR 본문은 .gitlab/merge_request_templates/default.md 양식을 영역에 맞게 채워 작성한다. MR 생성 성공 시 해당 scratch 파일을 삭제해 다음 사이클 사고를 막는다.
arguments: task-id
argument-hint: "L1-T01"
---

# commit-mr

## Purpose

`/ticket-branch <task-id>` 로 시작한 사이클을 같은 task-id 로 마무리한다.

- `.claude/scratch/$task-id.json` 에서 메타 로드 (없으면 즉시 차단)
- git diff 기반 커밋 메시지 LLM 작성 → confirm → commit
- push
- MR 본문 LLM 작성 (영역 체크리스트 적용) → confirm → MR 생성
- MR 생성 성공 시 해당 scratch 파일 삭제 (사이클 종료)

이 skill 은 사용자 코드 작성을 마친 뒤 호출된다.

---

## When to Use

- `/ticket-branch <task-id>` 로 시작한 사이클의 코드 작성을 마치고 머지로 보낼 때 (`/commit-mr <task-id>`)

다음에는 사용하지 않는다.

- 인자(task-id) 미입력
- `.claude/scratch/<task-id>.json` 이 없거나 비어 있음 (`/ticket-branch` 미실행 또는 이미 사이클 종료된 task)
- 변경 파일이 없음 (commit 할 게 없음)

---

## Argument

```
/commit-mr $task-id
```

- 형식: `/ticket-branch` 와 동일한 Lane task-id (`L<lane>-<접두어><번호>[<접미어>]`)
- 예: `/commit-mr L1-T01`, `/commit-mr L3-T05B`
- `$ARGUMENTS`, `$0` 도 동일 (fallback)
- 사이클 메타는 `.claude/scratch/$task-id.json` 에서 로드한다.
- 인자 미입력이거나 해당 파일이 없으면 `Failure / Ambiguity Format` 으로 즉시 중단.

---

## Whitelists (`docs/tasks/index.md` §커밋 및 MR 규칙 인용)

권위는 `docs/tasks/index.md` §커밋 및 MR 규칙. 아래 인용은 참고용. **매칭 실패 시 docs/tasks/index.md 를 Read 도구로 fresh 조회 후 재매칭**한다 (그래도 실패하면 `Failure / Ambiguity Format`).

### area_tag (제목용)

- 단일: `BE` `FE` `Android` `Infra` `Docs` → 제목엔 `[BE]` 등
- 결합: `BE/FE/Android/Infra` 슬래시 구분 (팀 컨벤션) → 제목엔 `[BE/FE]` 등
- 순수 문서·계약 변경은 `Docs` 하나

### type

`feat` `fix` `refactor` `style` `test` `docs` `chore` `ci` `build`

### scope

`incident` `auth` `police_phone` `retention` `event` `overall_search_area` `area` `path` `sync` `marker` `photo` `notification` `board` `package` `tiles` `op` `handover` `search_history_summary` `contract` `infra` `docs`

scope 는 domain/module/package 자리다. 특정 domain 으로 좁히기 어려운 전역 style 또는 tooling 변경은 scope 를 생략할 수 있다.

### MR Label 매핑 (GitLab 정식 라벨명)

`scratch.area_tag` 를 다음 표로 변환해 `mr-create.sh --label` 에 전달한다.

| area_tag | GitLab Label |
|---|---|
| `BE` | `⌨️ BE` |
| `FE` | `🖥️ FE` |
| `Android` | `📱 Android` |
| `Infra` | `🌏 Infra` |
| `Docs` | `📄 Docs` |

- 결합 area (예: `BE/FE`) → 콤마 구분 결합 라벨 문자열로 변환 (`⌨️ BE,🖥️ FE`). `mr-create.sh` 가 콤마 split 후 여러 `--label` 플래그로 glab 에 전달.
- 매핑 실패 시 `glab label list -R s14-final/S14P31C106` 으로 fresh 조회 후 재매칭.
- **type 라벨(`✨ Feature`, `🚨 Bug`, `🔧 Refactor`, `🧪 Test`, `⚙️ Chore` 등)은 추가하지 않는다**. type 정보는 커밋·MR 제목 안의 `<commit_type>` 표기 (`feat(...)`, `fix(...)` 등)로 표현한다. MR Label 은 영역 분류용으로만 사용한다.

---

## Flow

이 skill 의 결정적 부분(git/glab 호출)은 `scripts/` 디렉토리의 스크립트로 분리되어 있다. SKILL 은 LLM 판단 영역(메시지 작성·confirm·정리) 만 담당.

1. `.claude/scratch/$task-id.json` 로드. 파일이 없거나 손상되면 `Failure / Ambiguity Format` 으로 중단 (사용자가 `/ticket-branch $task-id` 부터 다시 시작해야 함을 안내).
2. 현재 git 브랜치가 scratch 의 `branch` 와 일치하는지 검증. 불일치 시 중단 (다른 task 로 swap 했다면 해당 브랜치로 먼저 switch 후 재호출).
3. `git status --short` 와 `git diff` 로 변경 확인.
   - 변경이 0 이면 중단 (commit 할 게 없음).
   - **`.claude/scratch/` 아래 파일은 변경 목록 표시·커밋 후보에서 항상 제외**.
   - 의도와 무관해 보이는 변경(다른 영역 파일) 있으면 사용자에게 확인.
4. 커밋 메시지 LLM 작성. 단일 또는 분리(파일별 의미 단위) 결정.
   - 형식: `[<area_tag>] <commit_type>(<commit_scope>): <한글 요약> (<jira_key>)`, scope 생략 시 `[<area_tag>] <commit_type>: <한글 요약> (<jira_key>)`
   - **화이트리스트 검증 강제**: scratch 의 `area_tag` / `commit_type` / `commit_scope` 를 본문 `Whitelists` 인용과 매칭. 매칭 실패 시 `docs/tasks/index.md` 를 Read 도구로 fresh 조회 후 재매칭. 그래도 실패하면 `Failure / Ambiguity Format`.
   - area 결합 표기: `[BE/FE]` 처럼 슬래시 구분 (팀 컨벤션).
   - 본문에는 `왜 / 어떻게` 를 짧게 정리.
5. **PAUSE**: 작성한 모든 커밋 메시지를 한 번에 출력하고 사용자 confirm 대기.
   - 커밋이 N개면 N개 모두 번호(`커밋 1/N`, `커밋 2/N` …)와 함께 **한 번에** 보여준다. 하나씩 묻지 않는다.
   - 출력 형식 예시 (scope 가 있으면 `[<area_tag>] <type>(<scope>): <한글> (<jira_key>)`, scope 가 없으면 `[<area_tag>] <type>: <한글> (<jira_key>)` 컨벤션 준수):

     **단일 area 예시 (scratch.area_tag = "BE"):**
     ```
     커밋 1/3: [BE] feat(incident): 사건 도메인 엔티티 추가 (S14P31C106-77)
       파일: backend/src/main/java/.../Incident.java
       본문: <왜/어떻게>

     커밋 2/3: [BE] feat(incident): incident_assignment 매핑 추가 (S14P31C106-77)
       파일: backend/src/main/java/.../IncidentAssignment.java
       본문: ...

     커밋 3/3: [BE] test(incident): 사건 도메인 단위 테스트 추가 (S14P31C106-77)
       파일: backend/src/test/java/.../IncidentTest.java
       본문: ...
     ```

     **결합 area 예시 (scratch.area_tag = "BE/FE"):**
     ```
     커밋 1/2: [BE/FE] refactor(contract): BaseEvent 스키마 필드명 정렬 (S14P31C106-NN)
       파일: backend/.../BaseEvent.java, frontend/.../baseEvent.ts
       본문: ...
     ```
   - 사용자가 특정 커밋 메시지 수정 요청 시 (예: "3번 본문만 짧게") 해당 항목만 재작성 후 동일 시점에 재 confirm.
   - 모든 메시지가 OK 일 때만 step 6 진행.
6. confirm 후 — `scripts/commit-push.sh commit` 호출 반복:
   ```bash
   .claude/skills/commit-mr/scripts/commit-push.sh commit \
     --file "<path>" \
     --message "$(printf '%s' '<커밋 메시지 본문>')"
   ```
   - 의존 순서대로 한 파일씩 호출.
   - 메시지는 multi-line OK (heredoc 또는 따옴표로 보존).
   - 스크립트는 `.claude/scratch/` 아래 파일 add 를 자체 거부한다.
7. push — `scripts/commit-push.sh push` 호출:
   ```bash
   .claude/skills/commit-mr/scripts/commit-push.sh push \
     --branch "<scratch.branch>" \
     --upstream
   ```
   원격 추적이 이미 설정돼 있으면 `--upstream` 생략 가능.
8. MR 본문 LLM 작성 → `/tmp/commit-mr-body.md` 에 저장. `MR Body Composition` 참조.
9. **PAUSE**: MR 본문 confirm 대기.
10. confirm 후 MR 생성 — `scripts/mr-create.sh` 호출.
    - **`scratch.area_tag` → MR Label 매핑** (위 `MR Label 매핑` 표 적용):
      - 단일: `BE` → `⌨️ BE` 등
      - 결합(`BE/FE`) → 콤마 구분 결합 (`⌨️ BE,🖥️ FE`). 스크립트가 콤마 split 후 여러 `--label` 호출.
      - 매핑 실패 시 `glab label list -R s14-final/S14P31C106` 으로 fresh 조회 후 재매칭.
    ```bash
    MR_URL=$(.claude/skills/commit-mr/scripts/mr-create.sh \
      --source "<scratch.branch>" \
      --target develop \
      --title "<커밋 제목과 동일>" \
      --desc-file /tmp/commit-mr-body.md \
      --label "<매핑된 GitLab 라벨, 결합 시 콤마 구분>" \
      --assignee "@me")
    ```
11. **MR 생성 성공 시** `rm -f .claude/scratch/$task-id.json` (해당 사이클 종료 명시). 다른 task 의 scratch 파일은 건드리지 않는다.
    - 중간 step 실패(commit/push/MR create 실패)로 중단된 경우에는 **삭제하지 않는다** — 재시도 시 scratch 가 필요하다.
12. `Final Report` 출력.

---

## Commit Message Rules

- 형식: `[<area_tag>] <commit_type>(<commit_scope>): <한글 요약> (<jira_key>)`, scope 생략 시 `[<area_tag>] <commit_type>: <한글 요약> (<jira_key>)`
- area_tag, commit_type, jira_key 는 scratch 에서 가져오되 **반드시 Whitelists 와 매칭 검증** 후 사용. commit_scope 가 있으면 scope whitelist 와 매칭하고, 비어 있으면 전역 style/tooling 변경인지 확인한다. 미매칭이면 fresh 조회 → 그래도 실패면 사용자 확인.
- area_tag 결합: 슬래시 구분 (`[BE/FE]`).
- 한글 요약은 git diff 기반 LLM 작성. 명사형 어미 권장 ("정정", "추가", "구현").
- 본문은 짧게 `왜 / 어떻게`. 마크다운 list 형식.
- 단일 vs 분리:
  - 변경 파일이 한 가지 의미 단위면 단일 커밋.
  - 의미 단위가 분리 가능하면 파일별 분리 커밋 (롤백 단위 확보).

---

## MR Body Composition

`.gitlab/merge_request_templates/default.md` 의 양식을 따른다. 4개 섹션:

1. **변경 사항** — 표 형식 권장 (파일 / 정정 내용)
2. **관련 Jira 티켓** — `[<jira_key>](https://ssafy.atlassian.net/browse/<jira_key>)` 링크
3. **작성자 확인** — 실제로 한 검증 항목 (체크된 것)
4. **<영역> 리뷰 체크리스트** — area 에 맞춰 갈아끼움
   - `BE` 라면 템플릿의 백엔드 체크리스트 그대로
   - `Infra`/`FE`/`Android` 등이면 영역에 맞는 4~6개 항목으로 LLM 재구성

영역에 무관한 항목이 있으면 N/A 표시 또는 항목 제거.

---

## MR Options Default

| 옵션 | 값 |
|---|---|
| Source | scratch.branch |
| Target | `develop` |
| Title | 커밋 제목과 동일 |
| Labels | scratch.area (대괄호 제거: `Infra` / `BE` / `FE` / `Android` / `Docs`) |
| Assignees | `@me` |
| Squash | OFF |
| Delete source branch | ON (`--remove-source-branch`) — 머지 후 원격 feature 브랜치 자동 삭제 |
| Draft | OFF |

---

## Pause Points

1. 커밋 메시지 confirm 대기 (Flow step 5)
2. MR 본문 confirm 대기 (Flow step 9)

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

- 인자(task-id) 미입력 또는 정규식 `L\d+-[A-Z]\d+[A-Z]?` 불일치
- `.claude/scratch/<task-id>.json` 부재 또는 손상
- 현재 브랜치가 scratch.branch 와 불일치
- 변경 파일 0개
- 변경 파일 영역이 scratch.area 와 명백히 어긋남
- `git push` 실패 (충돌, 권한 등)
- `glab mr create` 실패
- area_tag / commit_type / commit_scope 가 Whitelists 에 매칭 실패 (fresh 조회 후에도)
- MR Label 매핑 실패 (`glab label list` fresh 조회 후에도)

---

## Final Report

```markdown
Task ID:
Lane:
Jira Key:
Branch:
Commits:
  - <SHA> <메시지>
MR URL:
변경 파일:
Cross-lane impact:
Remaining risks:
```

---

## Constraints

- scratch 에 의존하므로 `/ticket-branch` 가 선행되지 않으면 동작하지 않는다.
- `git push --force` 등 destructive git 명령을 사용하지 않는다.
- 사용자 confirm 없이 commit / push / glab API 호출하지 않는다.
- MR 생성 성공 시 `.claude/scratch/<task-id>.json` 을 삭제한다. 다음 사이클은 반드시 `/ticket-branch <task-id>` 부터 다시 시작하도록 강제한다.
- 중간 step 실패로 중단된 경우에는 scratch 를 보존한다 (재시도 가능).
- 다른 task 의 scratch 파일은 건드리지 않는다 (여러 사이클 동시 진행 가능).
- **`.claude/scratch/` 아래 모든 파일은 절대 `git add` / `git commit` 대상에 포함하지 않는다. 사용자에게 커밋 제안조차 하지 않는다.** 변경 파일 목록을 사용자에게 보여줄 때도 scratch 파일은 제외해 표시한다.
- Jira 티켓 transition 은 자동 수행하지 않는다 (사용자가 GitLab MR 머지 후 직접).
