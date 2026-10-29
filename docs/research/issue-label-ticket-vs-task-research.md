# 작업 단위 Issue 라벨 이름 조사: `task`와 `ticket`

- 조사일: 2026-10-30
- 질문: 개인 계정 저장소 `sonic8-8/suri-map`에서 상위 계획 Issue에 `spec` 라벨을 붙인다. 그 아래 작업 단위 Issue(spec의 sub-issue나 spec 없이 혼자 있는 작업 Issue)에는 `task`와 `ticket` 중 어느 라벨이 1차 출처의 용법과 맞는가? GLOSSARY의 "티켓" 정의는 너무 좁은가?
- 근거 범위: Atlassian, GitHub, Linear, Microsoft의 공식 문서와 공식 발표, 설치된 Matt Pocock 스킬 원문, 저장소 파일만 근거로 쓴다. 블로그와 커뮤니티 답변은 근거로 쓰지 않는다.
- 상태: 선택지와 근거를 정리한 조사다. 라벨 이름과 GLOSSARY 수정은 사용자가 정한다.

## 요약

- 조사한 개발용 이슈 트래커는 모든 항목을 묶어 부르는 말로 "ticket"을 쓰지 않는다. Jira는 "work item"(예전 "issue"), GitHub과 Linear는 "issue", Azure DevOps는 "work item"을 쓴다.
- Atlassian 공식 문서에서 "ticket"은 주로 Jira Service Management, 즉 고객이나 상담원이 올리는 요청을 가리킬 때 나온다. GitHub 문서에서도 "ticket"은 GitHub Support 문의를 가리킨다.
- "Task"는 Jira, GitHub(기본 Issue Type), Azure DevOps 모두에서 작업 단위를 뜻하는 유형 이름이다. Matt Pocock 스킬은 spec을 나눈 조각을 "ticket"이라 부르지만, 그 저장소의 GLOSSARY는 모든 항목을 묶어 부를 때 "ticket" 대신 "Issue"를 쓰라고 적는다.

## 1. Jira (Atlassian)

### "issue"는 "work item"으로 바뀌었다

Atlassian은 2024-11-13 공식 블로그에서 Jira가 추적 항목을 "issue" 대신 "work"라고 부르고, 작업 흐름과 무관한 메뉴와 도움말에서는 "work items"라고 부른다고 발표했다. 이유로 "this term can be limiting and somewhat confusing in the context of your work"를 들었다. 이 글에는 "ticket"이 나오지 않는다. [Inside Atlassian, 'Work,' your way](https://www.atlassian.com/blog/jira/work-your-way)

Atlassian 개발자 커뮤니티의 공식 공지 제목도 "Work is the new collective term for items tracked in Jira"다. 검색 결과 요약에 따르면 기존 API와 JQL은 하위 호환을 위해 "issue"를 계속 쓴다. 공지 본문을 직접 읽어 이 문장을 확인하지는 못했다. [Atlassian Developer Community 공지](https://community.developer.atlassian.com/t/work-is-the-new-collective-term-for-items-tracked-in-jira/88552)

### Epic, Story, Task, Subtask

Jira Cloud 관리 문서는 work type을 "Work types distinguish different categories of work."라고 정의하고, 소프트웨어 스페이스의 기본 유형을 다음처럼 설명한다. [What are work types?](https://support.atlassian.com/jira-cloud-administration/docs/what-are-issue-types/)

| 유형 | 공식 설명 |
|---|---|
| Epic | "A big user story that needs to be broken down." |
| Story | "A user story is the smallest unit of work that needs to be done." |
| Task | "A task represents work that needs to be done." |
| Bug | "A bug is a problem which impairs or prevents the functions of a product." |
| Subtask | "A subtask is a piece of work that is required to complete a task." |

계층은 기본 3단계다. "a level for larger pieces of work (level 1, by default called Epic)", "a level for standard work items (level 0, called Story)", "a level for smaller pieces of work (level -1, called Subtask)". [Configure the work type hierarchy](https://support.atlassian.com/jira-cloud-administration/docs/configure-the-issue-type-hierarchy/)

Suri-Map에 대응하면 spec은 Epic 자리, 작업 단위 Issue는 Task 또는 Story 자리다. 위 페이지 두 곳 모두 "ticket"이라는 말을 쓰지 않는다.

### "ticket"을 쓰는 곳은 Jira Service Management다

- Jira Service Management 문서는 "Tickets are the requests raised in your service space either by customers, or by agents on your customer's behalf."라고 정의한다. [Manage your request types and ITSM categories](https://support.atlassian.com/jira-service-management-cloud/docs/manage-your-request-types-and-ticket-categories/)
- 같은 제품 문서는 request와 work item을 "two different views of the same unit of work"라고 설명한다. 고객은 request를, 상담원은 work item을 본다. 이 페이지에는 "ticket"이 나오지 않는다. [About requests and work items](https://support.atlassian.com/jira-service-management-cloud/docs/what-are-issues-and-requests/)
- 예전 "ticket categories" 문서는 주소(`what-are-ticket-categories`)만 남고 제목은 "What are work categories?"로 바뀌었다. [What are work categories?](https://support.atlassian.com/jira-service-management-cloud/docs/what-are-ticket-categories/)
- 제품 소개 페이지는 "Each ticket typically represents a specific task, inquiry, or problem that needs attention"이라고 쓴다. 이 문장도 서비스 데스크 맥락이다. [Ticketing Software by Jira Service Management](https://www.atlassian.com/software/jira/service-management/ticketing-software)

확인하지 못한 것: Atlassian 공식 문서에서 Jira Software의 Epic과 Task를 함께 "ticket"이라 부르는 문장은 찾지 못했다. 현장에서 Jira 항목을 두루 "티켓"이라 부르는 말버릇이 있다는 사용자 경험은 1차 출처로 확인하거나 반박할 수 없었다.

## 2. GitHub

### Issue Types

- 공식 문서: "You can use issue types to classify and manage different types of issues across your organization." 기본 유형은 "The default types are task, bug, and feature."이고, "Default issue types are included in every organization"이라고 적는다. 이 페이지에는 "ticket"이 나오지 않는다. [Managing issue types in an organization](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/managing-issue-types-in-an-organization)
- Issue Types REST API는 모두 `/orgs/{org}/issue-types` 경로이고 "Lists all issue types for an organization."처럼 조직 단위로만 설명한다. [REST API endpoints for issue types](https://docs.github.com/en/rest/orgs/issue-types)
- GitHub 직원 계정의 공개 미리보기 공지(2025-01-08)도 조직 관리자가 유형을 정하고 "These are available for all repositories in the organization."라고 적는다. [Issue Types Public Preview](https://github.com/orgs/community/discussions/148715)

확인하지 못한 것: 개인 계정 저장소에서 Issue Types를 쓸 수 없다고 직접 밝힌 공식 문장은 찾지 못했다. 공식 문서가 Issue Types를 조직 설정과 `/orgs` API로만 설명한다는 사실까지만 확인했다. 개인 계정에서 쓸 수 없다는 판단은 이 설명과 사용자 확인을 근거로 한 추론이다.

### Sub-issues

- "You can add sub-issues to an issue to break down larger pieces of work into tasks." 상한은 "up to 100 sub-issues per parent issue and create up to eight levels of nested sub-issues"다. [Adding sub-issues](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/adding-sub-issues)
- 개요 문서는 "break down larger pieces of work into smaller issues"라고 쓰고, Issues의 용도를 "track ideas, feedback, tasks, or bugs"라고 설명한다. [About issues](https://docs.github.com/en/issues/tracking-your-work-with-issues/learning-about-issues/about-issues)

GitHub은 sub-issue로 나눈 작업 조각을 "tasks"라고 부른다. 같은 문서들에 "ticket"은 나오지 않는다.

### 라벨과 "ticket"

- 기본 라벨은 `accessibility`, `bug`, `documentation`, `duplicate`, `enhancement`, `good first issue`, `help wanted`, `invalid`, `question`, `wontfix`다. 작업 단위를 가리키는 기본 라벨은 없고, 이 페이지에도 "ticket"과 "task"는 나오지 않는다. [Managing labels](https://docs.github.com/en/issues/using-labels-and-milestones-to-track-work/managing-labels)
- GitHub 문서에서 "ticket"은 GitHub Support 문의를 가리킨다. [Creating a support ticket](https://docs.github.com/en/support/contacting-github-support/creating-a-support-ticket)

## 3. Linear

- "Issues are the fundamental unit of work in Linear." 프로젝트는 "Projects group issues together around a shared outcome.", 그 위의 initiative는 "Initiatives sit above projects and represent broader strategic efforts."다. [Conceptual model](https://linear.app/docs/conceptual-model)
- "Use sub-issues to break down larger "parent" issues into smaller pieces of work." [Parent and sub-issues](https://linear.app/docs/parent-and-sub-issues)

두 페이지 모두 "ticket"을 쓰지 않는다. Linear 문서 전체를 검색하지는 않았다.

## 4. Azure DevOps (Azure Boards)

Agile 프로세스의 work item type은 "epics, features, user stories, tasks, issues, and bugs"다. "product owners and program managers map **user stories** to **features**. Teams working in sprints then define **tasks** that link to those user stories." 이 페이지에서 Issue는 진행을 막는 사건을 기록하는 유형이고("Use issues to track events that might block progress"), "ticket"은 나오지 않는다. [Agile Workflow in Azure Boards](https://learn.microsoft.com/en-us/azure/devops/boards/work-items/guidance/agile-process-workflow)

## 5. Matt Pocock 스킬 원문 (mattpocock-skills 1.3.1)

아래 경로의 기준은 `~/.claude/plugins/cache/claude-plugins-official/mattpocock-skills/1.3.1/`이다.

- `to-tickets`는 spec을 나눈 조각을 ticket이라 부른다. "Break a plan, spec, or conversation into a set of **tickets**: tracer-bullet vertical slices"(`skills/engineering/to-tickets/SKILL.md` 9행). 실제 트래커에서는 "publish one issue per ticket"이고, 원본이 Issue였으면 "make each ticket its sub-issue"라고 적는다(같은 파일 63행).
- `to-spec`은 spec을 "publish it to the project issue tracker"라고만 쓰고 spec을 ticket이라 부르지 않는다(`skills/engineering/to-spec/SKILL.md` 19행). `implement-spec`도 "This spec should have tickets associated with it"이라고 spec과 ticket을 나눈다(`skills/engineering/implement-spec/SKILL.md` 7행).
- 플러그인 저장소의 GLOSSARY는 모든 항목을 묶어 "Issue"라 부르고 ticket을 피하라고 적는다. "**Issue**: A single tracked unit of work inside an **Issue tracker**: a bug, task, spec, or slice produced by `to-tickets`." 이어서 "_Avoid_: ticket (use only when quoting external systems that call them tickets, or for a **Decision ticket**, see below)"(`GLOSSARY.md` 11~13행). 이 GLOSSARY는 플러그인 저장소를 관리하는 사람용 문서이고, 스킬이 사용자 저장소에 복사하는 파일은 아니다.
- 반대로 README는 triage 라벨을 설명하며 "what labels you apply to tickets when you triage them"이라고 쓴다(`README.md` 104행). triage는 버그를 포함한 모든 Issue를 다루므로 여기서는 ticket을 넓은 뜻으로 썼다. 같은 스킬 묶음 안에서도 용법이 한 가지로 정리되어 있지는 않다.
- `wayfinder`는 map Issue 아래 자식 Issue를 ticket이라 부르고 "Every map and ticket is an issue"라고 쓴다(`skills/engineering/wayfinder/SKILL.md` 17행). ticket 유형 가운데 하나가 **Task**이며 "Manual work that must happen before a _decision_ can be made"를 뜻한다(같은 파일 80행). 라벨은 `wayfinder:task`다(같은 파일 65행, 저장소의 `docs/agents/issue-tracker.md` 48행).

정리하면 Matt 스킬에서 ticket은 대부분 "spec이나 map을 나눈 자식 Issue"를 뜻하고, spec이나 map 자체는 ticket이라 부르지 않는다. 모든 항목을 묶는 말은 Issue다.

## 6. 저장소 안의 현재 용법

- `GLOSSARY.md` 39~41행: "**티켓**: 작업자에게 배정하는 작업 한 단위다." Avoid에는 "로컬 이슈, 작업 지시 파일"이 있다. 이 항목은 커밋 `abe7967d`(2026-10-28, "오케스트레이터가 로컬 티켓 대신 Matt 방식의 GitHub Issue 티켓을 배정하도록 역할 파일 수정") 때 들어왔다. 정의의 범위는 Matt `to-tickets`의 slice와 같다.
- `agents/orchestrator.md` 3행과 23~25행: "spec Issue와 티켓 Issue로 나눠", "`/mattpocock-skills:to-tickets`로 그 sub-issue인 티켓 Issue로 나눈다". spec과 티켓을 나눠 쓴다. 티켓에는 `ready-for-agent` 라벨을 붙인다(24행).
- `AGENTS.md` 145행: "티켓으로 나눈 작업은 spec Issue 번호를 쓴다." 147행: "관련 티켓을 연결할 때만 실제 티켓 ID를 덧붙인다." 158행: "티켓은 GitHub Issue(`sonic8-8/suri-map`)에 둔다." 147행과 158행은 spec을 포함한 모든 Issue를 가리키는 넓은 뜻으로 읽힌다. 144행의 "Lane task"는 옛 작업 단위를 가리킨다.
- `docs/agents/issue-tracker.md` 3행: "Issues and specs for this repo live as GitHub issues." 39행 "fetch the relevant ticket"과 45~52행 Wayfinding 절은 Matt 템플릿 문장이다.
- `docs/agents/triage-labels.md`: 상태 라벨 5개와 `Bug`, `enhancement`만 있고 ticket이나 task 라벨은 없다.

저장소 안에서도 "티켓"은 GLOSSARY와 orchestrator에서는 좁은 뜻(spec의 자식 작업), AGENTS.md 147행과 158행에서는 넓은 뜻(모든 Issue)으로 섞여 있다.

## 7. 결론과 선택지

### "티켓"은 포괄적인 말인가

조사한 1차 출처에서 모든 항목을 묶어 부르는 공식 용어는 "work item"(Jira, Azure DevOps)과 "issue"(GitHub, Linear, 예전 Jira)다. "ticket"은 Atlassian과 GitHub 모두 서비스 요청이나 지원 문의를 가리킬 때 쓴다. 따라서 "티켓이 업계에서 모든 항목을 묶어 부르는 공식 용어"라는 주장은 1차 출처로 뒷받침되지 않는다. 다만 Atlassian이 직접 정의한 ticket도 Epic과 Task를 나누는 계층 개념이 아니라 서비스 요청이므로, 1차 출처는 "티켓은 작업 단위만 뜻한다"는 저장소 정의도 뒷받침하지 않는다. 좁은 정의의 근거는 Matt `to-tickets` 스킬 하나다.

### 라벨: `task`와 `ticket`

- `task`: Jira의 Task 유형, GitHub 기본 Issue Type `task`, GitHub sub-issue 문서의 "break down larger pieces of work into tasks", Azure DevOps의 Task와 이름과 뜻이 맞는다. 나중에 저장소를 조직으로 옮기면 GitHub 기본 Issue Type `task`로 그대로 바꿀 수 있다. 약점은 Matt `wayfinder`의 Task 유형(결정 전에 하는 수작업)과 이름이 겹친다는 점이다. 다만 wayfinder 라벨은 `wayfinder:task`로 접두사가 붙어 문자열은 겹치지 않는다.
- `ticket`: Matt `to-tickets` 스킬 이름, 현재 GLOSSARY와 orchestrator 문장과 맞는다. 약점은 조사한 트래커 어디에도 작업 유형 이름으로 쓰지 않고, Atlassian과 GitHub에서는 서비스 요청이나 지원 문의를 뜻한다는 점이다. Matt 플러그인 GLOSSARY도 포괄적인 뜻으로는 ticket을 피하라고 한다.

### GLOSSARY 수정 방향

1. **정의를 유지하고 라벨과의 대응만 적는다.** "티켓"은 `to-tickets`로 만든 작업 단위라는 뜻을 그대로 두고, GitHub에서는 `task` 라벨을 붙인 Issue라고 덧붙인다. 모든 Issue를 묶어 부를 때는 "Issue"를 쓰고, 모든 Issue를 뜻하는 "티켓"을 Avoid에 넣는다. AGENTS.md 147행과 158행처럼 넓은 뜻으로 쓴 문장은 "Issue"로 고친다. orchestrator의 "티켓" 문장은 그대로 둘 수 있다.
2. **"티켓"을 모든 Issue로 넓히고 작업 단위는 "task"로 부른다.** 사용자 의견과 맞고 AGENTS.md 147행과 158행의 현재 용법과도 맞는다. 하지만 GitHub이 이미 "Issue"를 포괄 용어로 쓰므로 같은 뜻의 말이 두 개가 되고, Matt 플러그인 GLOSSARY의 Avoid와 반대 방향이다. `to-tickets` 스킬 이름과 orchestrator의 "티켓 Issue" 문장도 "task Issue"로 다시 써야 한다.
3. **정의를 유지하고 라벨도 `ticket`으로 한다.** 저장소 문장을 거의 바꾸지 않아도 되지만, 라벨 이름이 1차 출처의 트래커 용법과 맞지 않는다.

### 추천

**1안을 추천한다. 라벨은 `task`로 하고, GLOSSARY의 "티켓" 정의는 유지하되 `task` 라벨과의 대응과 "모든 Issue는 Issue라고 부른다"는 점을 적는다.** 근거는 다음과 같다. 작업 단위의 이름으로는 Jira, GitHub, Azure DevOps가 모두 Task를 쓴다([Jira work types](https://support.atlassian.com/jira-cloud-administration/docs/what-are-issue-types/), [GitHub issue types](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/managing-issue-types-in-an-organization), [Azure Boards](https://learn.microsoft.com/en-us/azure/devops/boards/work-items/guidance/agile-process-workflow)). 모든 항목을 묶는 말로는 GitHub과 Matt 플러그인 GLOSSARY가 모두 Issue를 쓴다([About issues](https://docs.github.com/en/issues/tracking-your-work-with-issues/learning-about-issues/about-issues), 플러그인 `GLOSSARY.md` 11~13행). 이렇게 하면 "티켓"을 넓히지 않아도 사용자가 지적한 혼동을 줄일 수 있고, `to-tickets`와 orchestrator 문장도 그대로 쓸 수 있다.

## 근거의 한계

- Atlassian 개발자 커뮤니티 공지와 GitHub Issue Types 미리보기 공지는 회사 계정이 올린 글이라 1차 출처로 보았지만, 제품 도움말 문서는 아니다.
- 몇몇 Atlassian 페이지(`What Are Jira Work Items?`, `IT Ticketing System`)는 본문을 가져오지 못해 근거로 쓰지 않았다.
- 개인 계정 저장소에서 Issue Types를 쓸 수 없다는 공식 문장, 그리고 실무자들이 Jira 항목을 두루 "티켓"이라 부르는지는 확인하지 못했다.
- 이 문서는 용어 조사이고 라벨을 만들거나 Issue를 바꾸지 않았다.
