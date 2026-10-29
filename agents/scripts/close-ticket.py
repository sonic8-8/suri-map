#!/usr/bin/env python3
"""검수를 통과한 티켓 Issue를 완료로 표시한다.

본문의 `## Acceptance criteria` 체크박스를 체크하고, 댓글 하나를 쓰고, Issue를 닫는다.
옵션 없이 실행하면 바뀔 본문 diff와 댓글만 보여 준다. 사용자 승인을 받은 뒤 `--apply`로 실행한다.

usage: close-ticket.py <issue> <comment-file> [--apply]
       close-ticket.py --self-check
"""
import difflib
import json
import subprocess
import sys
import tempfile

HEADING = '## Acceptance criteria'


def section_bounds(lines):
    try:
        start = lines.index(HEADING)
    except ValueError:
        raise SystemExit(f'no "{HEADING}" heading')
    end = next((i for i in range(start + 1, len(lines)) if lines[i].startswith('## ')), len(lines))
    return start, end


def check_acceptance_criteria(body):
    """AC 절의 `- [ ]`만 `- [x]`로 바꾼다. 다른 절의 체크박스는 그대로 둔다."""
    lines = body.split('\n')
    start, end = section_bounds(lines)
    section = [line.replace('- [ ] ', '- [x] ', 1) for line in lines[start:end]]
    return '\n'.join(lines[:start] + section + lines[end:])


def gh(*args):
    return subprocess.run(['gh', *args], check=True, capture_output=True, text=True).stdout


def main(issue, comment_file, apply):
    with open(comment_file, encoding='utf-8') as f:
        comment = f.read()
    current = json.loads(gh('issue', 'view', issue, '--json', 'body,state'))
    if current['state'] != 'OPEN':
        raise SystemExit(f'#{issue} is {current["state"]}')
    checked = check_acceptance_criteria(current['body'])
    sys.stdout.writelines(difflib.unified_diff(
        current['body'].splitlines(True), checked.splitlines(True), f'#{issue} before', f'#{issue} after'))
    print(f'\n--- comment on #{issue}\n{comment}\n--- then close #{issue} as completed')
    if not apply:
        return
    with tempfile.NamedTemporaryFile('w', suffix='.md', encoding='utf-8') as body_file:
        body_file.write(checked)
        body_file.flush()
        gh('issue', 'edit', issue, '--body-file', body_file.name)
    gh('issue', 'comment', issue, '--body-file', comment_file)
    gh('issue', 'close', issue, '--reason', 'completed')
    after = json.loads(gh('issue', 'view', issue, '--json', 'body,state'))
    if after['state'] != 'CLOSED' or '- [ ] ' in after_section(after['body']):
        raise SystemExit(f'#{issue} not fully closed: {after["state"]}')
    print(f'#{issue} closed')


def after_section(body):
    lines = body.split('\n')
    start, end = section_bounds(lines)
    return '\n'.join(lines[start:end])


def self_check():
    body = '## What\n\n- [ ] keep\n\n## Acceptance criteria\n\n- [ ] a\n  - nested\n- [x] b\n- [ ] c\n\n## Blocked by\n\n- [ ] keep too'
    got = check_acceptance_criteria(body)
    assert got == body.replace('- [ ] a', '- [x] a').replace('- [ ] c', '- [x] c'), got
    assert check_acceptance_criteria('## Acceptance criteria\n- [ ] last') == '## Acceptance criteria\n- [x] last'
    try:
        check_acceptance_criteria('## Other\n- [ ] x')
        raise AssertionError('missing heading accepted')
    except SystemExit:
        pass
    print('PASS only the Acceptance criteria section is checked, and a body without it is refused')


if __name__ == '__main__':
    if sys.argv[1:] == ['--self-check']:
        self_check()
    elif len(sys.argv) in (3, 4) and sys.argv[3:] in ([], ['--apply']):
        main(sys.argv[1], sys.argv[2], sys.argv[3:] == ['--apply'])
    else:
        print(__doc__, file=sys.stderr)
        sys.exit(2)
