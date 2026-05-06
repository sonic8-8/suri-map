# Jira / GitLab Flow

## 시작

1. `docs/tasks/index.md`의 전달 흐름과 브랜치 규칙을 먼저 확인한다.
2. Jira work item이 없으면 생성하고, 있으면 summary와 scope가 작업 범위를 정확히 담는지 확인한다.
3. Jira key를 기준으로 `docs/tasks/index.md`의 목적별 prefix를 선택해 브랜치를 만든다.
4. 자동화가 가능하면 `ticket-branch` skill로 Jira 생성, 브랜치 생성, `.agents/scratch` 기록을 한 번에 처리한다.
5. Jira 상태, sprint, milestone 같은 보드 필드는 자동화되지 않았으면 UI에서 확인하거나 수동 설정한다.

## 작업 중

- 구현 범위는 Jira key와 연결된 TODO 또는 Lane task로 제한한다.
- 기준 문서와 충돌하는 public API, fixture ID, event, entity는 임의 수정하지 않는다.
- 다른 Lane 파일을 건드려야 하면 owner LGTM이 먼저 필요하다.
- 커밋 전 `git status --short`로 의도한 파일만 포함되는지 확인한다.

## MR

1. 커밋 메시지는 `[Area] type[(scope)]: 한글 요약 (<JIRA-KEY>)` 형식을 쓴다. scope 생략은 `docs/tasks/index.md`의 전역 style/tooling 예외일 때만 허용한다.
2. MR 제목은 커밋 제목과 같은 형식으로 둔다.
3. MR 설명에는 Jira key, 완료한 task, 검증 명령/결과, 영향 Lane, 수정한 기준 문서를 적는다.
4. 자동화가 가능하면 `commit-mr` skill로 confirm 후 commit, push, MR 생성을 처리한다.
5. GitLab labels, milestone, assignee, reviewer는 MR 생성 후 실제 값이 맞는지 확인하고, 자동화가 빠뜨린 필드는 수동 설정한다.
6. MR merge 후 Jira 상태와 TODO 체크박스를 실제 완료 기준에 맞게 갱신한다.
