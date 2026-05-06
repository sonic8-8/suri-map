#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path


def git_root() -> Path:
    output = subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True)
    return Path(output.strip())


def run(cmd: list[str], root: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        cmd,
        cwd=root,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )


def main() -> int:
    if os.environ.get("AGENT_QUICKCHECK_SKIP") == "1":
        return 0

    root = git_root()
    checks = [
        [sys.executable, str(root / ".agents" / "scripts" / "check-fixture-contract.py")],
        ["bash", str(root / ".agents" / "scripts" / "quickcheck-on-stop.sh")],
    ]

    failures: list[str] = []
    for cmd in checks:
        proc = run(cmd, root)
        if proc.returncode != 0:
            output = (proc.stderr or proc.stdout or f"{cmd[0]} failed").strip()
            failures.append(output)

    if not failures:
        return 0

    reason = "\n\n".join(failures)
    print(
        json.dumps(
            {
                "continue": False,
                "stopReason": "Project quickcheck failed",
                "systemMessage": reason,
            },
            ensure_ascii=False,
        )
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
