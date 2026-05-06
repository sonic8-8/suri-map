#!/usr/bin/env python3
"""Block unreviewed edits to harness fixture definitions.

The guard intentionally treats docs/spec/harness-scenarios.md section 6 as a
locked contract surface. Set ALLOW_FIXTURE_ID_CHANGE=1 only after the owner
review path in AGENTS.md and docs/spec/boundaries.md has been followed.
"""

from __future__ import annotations

import os
import re
import subprocess
import sys
from pathlib import Path


TARGET = Path("docs/spec/harness-scenarios.md")
SECTION_RE = re.compile(r"^##\s+6\.\s+")
NEXT_SECTION_RE = re.compile(r"^##\s+\d+[\s.]")
HUNK_RE = re.compile(r"@@ -(?P<old_start>\d+)(?:,(?P<old_count>\d+))? \+(?P<new_start>\d+)(?:,(?P<new_count>\d+))? @@")


def git_root() -> Path:
    output = subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True)
    return Path(output.strip())


def read_head_file(root: Path) -> str:
    try:
        return subprocess.check_output(["git", "show", f"HEAD:{TARGET.as_posix()}"], cwd=root, text=True)
    except subprocess.CalledProcessError:
        return ""


def section_range(text: str) -> tuple[int, int] | None:
    lines = text.splitlines()
    start = None
    for idx, line in enumerate(lines, start=1):
        if start is None and SECTION_RE.match(line):
            start = idx
            continue
        if start is not None and idx > start and NEXT_SECTION_RE.match(line):
            return start, idx - 1
    if start is None:
        return None
    return start, len(lines)


def ranges_overlap(start_a: int, end_a: int, start_b: int, end_b: int) -> bool:
    return max(start_a, start_b) <= min(end_a, end_b)


def parse_hunks(diff: str) -> list[tuple[int, int, int, int]]:
    hunks: list[tuple[int, int, int, int]] = []
    for line in diff.splitlines():
        match = HUNK_RE.search(line)
        if not match:
            continue
        old_start = int(match.group("old_start"))
        old_count = int(match.group("old_count") or "1")
        new_start = int(match.group("new_start"))
        new_count = int(match.group("new_count") or "1")
        old_end = old_start + max(old_count, 1) - 1
        new_end = new_start + max(new_count, 1) - 1
        hunks.append((old_start, old_end, new_start, new_end))
    return hunks


def git_diff(root: Path, cached: bool) -> str:
    args = ["git", "diff", "--unified=0"]
    if cached:
        args.append("--cached")
    args.extend(["--", TARGET.as_posix()])
    return subprocess.check_output(args, cwd=root, text=True)


def main() -> int:
    if os.environ.get("ALLOW_FIXTURE_ID_CHANGE") == "1":
        return 0

    root = git_root()
    target_path = root / TARGET
    working_text = target_path.read_text(encoding="utf-8") if target_path.exists() else ""
    head_text = read_head_file(root)

    working_range = section_range(working_text)
    head_range = section_range(head_text)
    if working_range is None and head_range is None:
        return 0

    offenders: list[str] = []
    for label, diff in (("working tree", git_diff(root, cached=False)), ("index", git_diff(root, cached=True))):
        for old_start, old_end, new_start, new_end in parse_hunks(diff):
            old_hits = head_range and ranges_overlap(old_start, old_end, head_range[0], head_range[1])
            new_hits = working_range and ranges_overlap(new_start, new_end, working_range[0], working_range[1])
            if old_hits or new_hits:
                offenders.append(label)
                break

    if offenders:
        print(
            "docs/spec/harness-scenarios.md §6 fixture contract changed. "
            "Get owner LGTM and rerun with ALLOW_FIXTURE_ID_CHANGE=1 if this is intentional.",
            file=sys.stderr,
        )
        print(f"detected in: {', '.join(sorted(set(offenders)))}", file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
