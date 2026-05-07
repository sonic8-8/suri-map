#!/usr/bin/env python3
"""Shared Bash command guard for agent hooks.

Exit codes:
- 0: command is allowed
- 1: command violates policy
- 64: hook input is malformed or unsupported
"""

from __future__ import annotations

import json
import os
import re
import sys
from dataclasses import dataclass


@dataclass(frozen=True)
class Rule:
    name: str
    pattern: re.Pattern[str]
    reason: str


RULES = [
    Rule(
        "force-push",
        re.compile(r"\bgit\s+push\b[^\n]*(?:--force\b|--force-with-lease\b|-f\b)"),
        "force push is blocked; coordinate explicitly before rewriting remote history",
    ),
    Rule(
        "hard-reset",
        re.compile(r"\bgit\s+reset\s+--hard\b"),
        "git reset --hard is blocked because it can discard user or agent work",
    ),
    Rule(
        "git-clean",
        re.compile(r"\bgit\s+clean\b[^\n]*-[^\n\s]*[fdx][^\n\s]*"),
        "git clean with delete flags is blocked; list files and get explicit approval first",
    ),
    Rule(
        "dangerous-rm",
        re.compile(
            r"\brm\s+[^\n]*(?:-[^\n\s]*r[^\n\s]*f|-[^\n\s]*f[^\n\s]*r|"
            r"-[^\n\s]*r\b[^\n]*-[^\n\s]*f\b|-[^\n\s]*f\b[^\n]*-[^\n\s]*r\b)"
        ),
        "recursive force delete is blocked",
    ),
    Rule(
        "docker-volume-delete",
        re.compile(r"\bdocker\s+(?:compose\s+down\b[^\n]*(?:-v|--volumes)|volume\s+rm\b)"),
        "docker volume deletion is blocked; data loss risk must be reviewed first",
    ),
    Rule(
        "prod-data-command",
        re.compile(
            r"\b(?:psql|mysql|kubectl|aws|gcloud|flyway)\b[^\n]*(?:prod|production)|"
            r"(?:prod|production)[^\n]*\b(?:psql|mysql|kubectl|aws|gcloud|flyway)\b",
            re.IGNORECASE,
        ),
        "commands targeting production data or infrastructure are blocked in agent hooks",
    ),
]


def _extract_command(payload: object) -> str | None:
    if not isinstance(payload, dict):
        return None

    tool_input = payload.get("tool_input")
    if isinstance(tool_input, dict):
        for key in ("command", "cmd", "shell_command"):
            value = tool_input.get(key)
            if isinstance(value, str) and value.strip():
                return value

    for key in ("command", "cmd"):
        value = payload.get(key)
        if isinstance(value, str) and value.strip():
            return value

    return None


def _load_command() -> str | None:
    stdin = sys.stdin.read()
    if stdin.strip():
        try:
            payload = json.loads(stdin)
        except json.JSONDecodeError as exc:
            print(f"invalid hook JSON: {exc}", file=sys.stderr)
            sys.exit(64)
        return _extract_command(payload)

    if len(sys.argv) > 1:
        return " ".join(sys.argv[1:])

    return None


def main() -> int:
    command = _load_command()
    if not command:
        print("missing Bash command in hook input", file=sys.stderr)
        return 64

    if os.environ.get("AGENT_ALLOW_DANGEROUS_COMMAND") == "1":
        return 0

    normalized = re.sub(r"\s+", " ", command.strip())
    for rule in RULES:
        if rule.pattern.search(normalized):
            print(f"{rule.name}: {rule.reason}", file=sys.stderr)
            print(f"command: {normalized}", file=sys.stderr)
            return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
