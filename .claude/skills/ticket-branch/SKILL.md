---
name: ticket-branch
description: Start a Suri-Map Lane task by creating the Jira work item, branch, and shared scratch metadata.
arguments: task-id
argument-hint: "L1-T01"
---

# ticket-branch

Use the shared ticket-branch skill at `../../../.agents/skills/ticket-branch/SKILL.md`.

Claude-specific handling:

- Use shared scratch metadata under `.agents/scratch/`, not `.claude/scratch/`.
- Use the shared scripts in `.agents/skills/ticket-branch/scripts/`.
- Keep Claude-local overrides in `.claude/settings.local.json`; do not commit that file.
