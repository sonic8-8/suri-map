---
name: tdd-loop
description: Run a Suri-Map task through the shared RED/GREEN/VERIFY/REVIEW loop.
---

# TDD Loop

Use the shared TDD loop skill at `../../../.agents/skills/tdd-loop/SKILL.md`.

Claude-specific handling:

- Use `.claude/settings.json` project hooks for Bash guard, edit formatting, and stop quickcheck.
- Keep local-only overrides in `.claude/settings.local.json`; do not commit that file.
- When spawning subagents, use the wrappers in `.claude/agents/` and the shared prompts in `.agents/prompts/`.
