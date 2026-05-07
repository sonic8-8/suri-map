#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path


PATCH_FILE_RE = re.compile(r"^\*\*\* (?:Add|Update|Delete) File: (.+)$|^\*\*\* Move to: (.+)$")


def git_root() -> Path:
    output = subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True)
    return Path(output.strip())


def collect_paths_from_mapping(mapping: dict[str, object]) -> list[str]:
    paths: list[str] = []
    for key in ("file_path", "path", "old_path", "new_path"):
        value = mapping.get(key)
        if isinstance(value, str):
            paths.append(value)

    value = mapping.get("files")
    if isinstance(value, list):
        paths.extend(item for item in value if isinstance(item, str))

    for key in ("command", "patch", "diff"):
        value = mapping.get(key)
        if not isinstance(value, str):
            continue
        for line in value.splitlines():
            match = PATCH_FILE_RE.match(line.strip())
            if match:
                paths.append((match.group(1) or match.group(2)).strip())

    return paths


def collect_paths(payload: object) -> list[str]:
    if not isinstance(payload, dict):
        return []

    paths = collect_paths_from_mapping(payload)
    tool_input = payload.get("tool_input")
    if isinstance(tool_input, dict):
        paths.extend(collect_paths_from_mapping(tool_input))

    return sorted(set(paths))


def main() -> int:
    raw = sys.stdin.read()
    try:
        payload = json.loads(raw) if raw.strip() else {}
    except json.JSONDecodeError:
        return 0

    paths = collect_paths(payload)
    if not paths:
        return 0

    root = git_root()
    core = root / ".agents" / "scripts" / "format-after-edit.sh"
    proc = subprocess.run(
        ["bash", str(core), *paths],
        cwd=root,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if proc.returncode == 0:
        return 0

    reason = (proc.stderr or proc.stdout or "format-on-edit guard failed").strip()
    print(
        json.dumps(
            {
                "decision": "block",
                "reason": reason,
                "hookSpecificOutput": {
                    "hookEventName": "PostToolUse",
                    "additionalContext": reason,
                },
            },
            ensure_ascii=False,
        )
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
