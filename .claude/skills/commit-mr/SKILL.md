---
name: commit-mr
description: Finish a Suri-Map Lane task by committing, pushing, and creating a GitLab MR from shared scratch metadata.
arguments: task-id
argument-hint: "L1-T01"
---

# commit-mr

Use the shared commit-mr skill at `../../../.agents/skills/commit-mr/SKILL.md`.

Claude-specific handling:

- Read and delete shared scratch metadata under `.agents/scratch/`, not `.claude/scratch/`.
- Use the shared scripts in `.agents/skills/commit-mr/scripts/`.
- Keep Claude-local overrides in `.claude/settings.local.json`; do not commit that file.
