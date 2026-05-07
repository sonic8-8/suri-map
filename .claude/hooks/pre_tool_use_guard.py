#!/usr/bin/env python3
from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path


def git_root() -> Path:
    output = subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True)
    return Path(output.strip())


def main() -> int:
    raw = sys.stdin.read()
    root = git_root()
    core = root / ".agents" / "scripts" / "guard-bash-command.py"
    proc = subprocess.run(
        [sys.executable, str(core)],
        input=raw,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if proc.returncode == 0:
        return 0

    reason = (proc.stderr or proc.stdout or "Bash command blocked by project guardrail").strip()
    if proc.returncode != 1:
        print(reason, file=sys.stderr)
        return proc.returncode

    print(
        json.dumps(
            {
                "systemMessage": reason,
                "hookSpecificOutput": {
                    "hookEventName": "PreToolUse",
                    "permissionDecision": "deny",
                    "permissionDecisionReason": reason,
                },
            },
            ensure_ascii=False,
        )
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
