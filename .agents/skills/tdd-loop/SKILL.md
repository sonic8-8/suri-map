---
name: tdd-loop
description: Run a Suri-Map task through preflight, RED, GREEN, VERIFY, and REVIEW with Jira/Lane guardrails.
---

# TDD Loop

Use this skill when a Suri-Map implementation, guardrail, or documentation task must be completed with observable evidence. The core is the RED -> GREEN -> VERIFY -> REVIEW loop; preflight only exists to make the loop safe.

## Preflight

1. Read root `AGENTS.md` and the closest platform `AGENTS.md` for files you will touch.
2. Read `docs/tasks/index.md` for Jira, branch, commit, MR, and task completion rules.
3. Identify the Jira key and exact TODO or Lane task.
4. Check the branch prefix follows the purpose-specific table in `docs/tasks/index.md`.
5. Read only the relevant source-of-truth docs from root `AGENTS.md`.
6. Before edits, state the intended file set and why it is in scope.

For Jira, branch, MR, label, milestone, assignee, and reviewer operations, follow `.agents/workflows/jira-gitlab-flow.md`.

## Loop

1. RED: Create or identify the failing test, fixture, harness assertion, lint check, or documented acceptance check before changing behavior.
2. GREEN: Make the smallest change that closes the RED failure. Do not broaden scope to adjacent tasks.
3. VERIFY: Re-run the same failing command, then run the platform validation commands required by the nearest `AGENTS.md`.
4. REVIEW: Check the diff against Lane ownership, source-of-truth contracts, fixture exactness, API/event names, and completion criteria.
5. Repeat the loop until the task's observable completion criteria are proven.

## Guardrails

- Do not invent public API, entity, event, fixture ID, error, annotation, board slot, or DB meaning.
- Do not edit another Lane contract without owner LGTM.
- Do not mix Android app writes with Web command flows.
- Do not introduce JPA/Hibernate/Spring Data JPA in backend persistence.
- Do not check TODO or Lane checkboxes until RED/GREEN/VERIFY/REVIEW evidence proves the completion criteria.

## Expected Output

- Jira key and branch
- Task scope
- Source documents read
- RED evidence and command
- GREEN implementation summary
- VERIFY commands and results
- REVIEW findings or explicit no-finding statement
- Remaining blockers, owner LGTM needs, or MR/Jira follow-up
