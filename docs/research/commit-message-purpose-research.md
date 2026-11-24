# 커밋 메시지 제목과 본문의 목적 배치 조사

- 조사일: 2026-11-24
- 질문: 널리 알려진 오픈소스 저장소와 서비스 회사는 커밋 메시지에서 목적(왜 바꿨는가, 어떤 문제인가)과 바꾼 내용(무엇을 바꿨는가)을 제목과 본문에 어떻게 나누는가? 제목에 목적을 항상 쓰는가, 바꾼 내용만 쓰는가? 이유는 본문에 쓰는가? 제목 길이, 문장 형태(명령형 등), 접두어(Conventional Commits의 type과 scope, 하위 시스템 접두어)는 어떻게 정하는가?
- 배경: 조사 당시 이 저장소의 [커밋, PR, Issue 글쓰기 기준](../agents/commit-issue-pr-writing.md) 초안에는 제목의 요약을 `목적, 바꾼 내용` 순서로 쓰라고 적혀 있었다. 예시는 `팀원 누구나 업무폰에서 팀의 수색 구역을 볼 수 있게, 수색 구역을 계정 대신 관서와 팀에 배정하도록 변경`이었다. 루트 `AGENTS.md`의 Git 절에도 "제목에는 목적과 바꾼 내용만 담고, 원인은 상세 메시지에 쓴다"고 적혀 있었다. 사용자는 제목에 목적을 항상 넣는 규칙이 오히려 나쁜 선택인지 물었다.
- 근거 범위: 각 프로젝트의 공식 기여 안내와 스타일 가이드, 실제 git 이력(`gh api repos/<owner>/<repo>/commits`와 Chromium Gitiles 로그), 한국 회사의 공식 기술 블로그와 그 회사가 공개한 저장소 이력만 근거로 쓴다. 다른 프로젝트의 관례를 요약한 개인 블로그는 근거로 쓰지 않는다.
- 상태: 2026-11-24 사용자가 리눅스 커널 커밋처럼 제목의 바꾼 내용 안에 이유가 드러나게 쓰고, 상세한 내용은 본문에 쓰기로 정했다. 본문은 지금 동작과 문제, 바꾼 방법과 이유 순서로 쓴다. 아래 선택지 3에 가깝다. 제목 길이 상한은 정하지 않았다.

## 요약

- 조사한 규칙 대부분에는 제목에 바꾼 내용을 쓰고 이유는 본문에 쓰라고 적혀 있다. Google, Go, Angular, Conventional Commits, Kubernetes, Node.js, Chromium, Git이 이 쪽이다.
- 제목(summary phrase)이 "what"과 "why"를 모두 담아야 한다고 적힌 곳은 리눅스 커널 문서뿐이다. 그래도 커널의 실제 제목 20개 가운데 "to ..."나 "so that ..." 같은 목적 절을 단 제목은 없었다. 커널 작성자들은 "Fix TOCTOU kernel stack disclosure"처럼 바꾼 내용의 동사와 목적어에 문제를 담는다.
- 실제 이력 13개 저장소에서 20개씩 제목을 세었다. 목적 절을 단 제목은 11개 저장소에서 0개나 1개였다. Mozilla는 4개였고, 그중 2개는 되돌린 이유를 적는 백아웃 제목이었다. kakao/kakao_flutter_sdk만 7개로 많았다.
- 제목 길이는 조사한 규칙 대부분에 50자를 권장하고 72자를 넘기지 말라고 적혀 있다. 이 저장소 규칙의 예시는 요약만 59자다. `[BE] feat: `까지 붙이면 터미널 폭으로 약 112칸이 된다. 제목 길이는 이 저장소의 `.githooks/` 훅으로 검사되지 않는다.
- 한국 회사 공식 기술 블로그에서 제목에 목적을 쓰라고 정한 글은 찾지 못했다. 토스페이먼츠는 "변경 내용"과 "변경의 이유"를 따로 나눠 쓰는 예시를 공개했다. LINE의 Armeria는 본문을 Motivation, Modifications, Result로 나눈다. 카카오 기술 블로그와 당근 기술 블로그에서는 커밋 메시지 규칙 글을 찾지 못했다.

## 1. 리눅스 커널: submitting-patches.rst

출처: [Documentation/process/submitting-patches.rst](https://github.com/torvalds/linux/blob/master/Documentation/process/submitting-patches.rst)

### Describe your changes

- 커널 문서에는 문제부터 설명하라는 요구가 있다. "Describe your problem. Whether your patch is a one-line bug fix or 5000 lines of a new feature, there must be an underlying problem that motivated you to do this work."
- 바꾼 내용은 문제를 세운 뒤에 쓴다. "Once the problem is established, describe what you are actually doing about it in technical detail."
- 문장 형태는 명령형이다. "Describe your changes in imperative mood, e.g. "make xyzzy do frotz" instead of "[This patch] makes xyzzy do frotz""
- 패치 하나에는 문제 하나만 담는다. "Solve only one problem per patch."

### 제목 (The canonical patch format, Subject Line)

- 제목 형식은 `Subject: [PATCH 001/123] subsystem: summary phrase`다. 접두어는 하위 시스템 이름이다. "The ``subsystem`` in the email's Subject should identify which area or subsystem of the kernel is being patched."
- 커널 문서에는 제목 길이와 내용이 함께 정해져 있다. "the ``summary`` must be no more than 70-75 characters, and it must describe both what the patch changes, as well as why the patch might be necessary."
- 커널 문서에는 이 요구가 어렵다는 점도 적혀 있다. "It is challenging to be both succinct and descriptive, but that is what a well-written summary should do."
- 좋은 제목 예시로 `ext2: improve scalability of bitmap searching`과 `x86: fix eflags tracking`을 든다. 두 예시 모두 목적 절 없이 바꾼 내용의 동사와 목적어로 쓰였다.
- 본문은 이유를 담는 곳이다. "it can give the reader the needed details to grasp the reasoning for **why** the patch was created."

### 실제 이력

`torvalds/linux`의 병합 커밋이 아닌 최근 커밋 20개를 봤다(2026-11-20부터 2026-11-21까지, 릴리스 태그 커밋 `Linux 7.4-rc4`는 뺐다). 목적 절을 단 제목은 0개였다. 대신 `Fix TOCTOU kernel stack disclosure ...`, `Do not allow user events to add a system name`처럼 문제나 금지할 동작을 제목의 목적어로 쓴 제목이 많았다.

| 커밋 | 제목 | 본문 첫 문단 |
|---|---|---|
| [bd6d2f87c223](https://github.com/torvalds/linux/commit/bd6d2f87c223) | KVM: PPC: Fix TOCTOU kernel stack disclosure via kvm_run->mmio.len | struct kvm_run is mmap'd and writable by host userspace at any time. kvmppc_complete_mmio_load() re-reads run->mmio.len from the shared page on the KVM_RUN completion path. A malicious userspace can: |
| [fc91dacd0c2b](https://github.com/torvalds/linux/commit/fc91dacd0c2b) | riscv: mm: reserve the DTB before scanning reserved memory | early_init_fdt_scan_reserved_mem() allocates memory from memblock while it scans /reserved-memory: ... On RISC-V the DTB was only reserved after that scan, so memblock could hand out the memory the DTB occupies while the DTB was still being parsed. |

두 커밋 모두 본문 첫 문단에 지금 코드의 문제가 있다. 두 번째 커밋의 제목에는 이유가 없다. 이유("DTB가 파싱되는 동안 그 메모리를 내줄 수 있다")는 본문에만 있다.

## 2. Git: Documentation/SubmittingPatches

출처: [Documentation/SubmittingPatches](https://github.com/git/git/blob/master/Documentation/SubmittingPatches), [git-commit 문서 DISCUSSION](https://git-scm.com/docs/git-commit#_discussion)

- Git 프로젝트는 로그 메시지의 목표를 이유 전달로 정한다. "The goal of your log message is to convey the _why_ behind your change to help future developers."
- 첫 줄은 짧은 설명이다. "The first line of the commit message should be a short description (50 characters is the soft limit, see DISCUSSION in linkgit:git-commit[1]), and should skip the full stop."
- 접두어는 영역 이름이다. "It is also conventional in most cases to prefix the first line with "area: " where the area is a filename or identifier for the general area of the code being modified"
- 본문은 문제와 근거를 담는다. "explains the problem the change tries to solve, i.e. what is wrong with the current code without the change." 이어서 "justifies the way the change solves the problem, i.e. why the result with the change is better."
- 바꾼 내용은 명령형으로 쓴다. "Describe your changes in imperative mood"
- git-commit 문서에도 첫 줄을 바꾼 내용의 요약으로 쓰라고 적혀 있다. "it's a good idea to begin the commit message with a single short (no more than 50 characters) line summarizing the change, followed by a blank line and then a more thorough description."

### 실제 이력

`git/git`의 병합 커밋이 아닌 최근 커밋 가운데, 메인테이너가 릴리스 노트를 갱신하는 `The 24th batch` 같은 커밋을 빼고 20개를 봤다. 목적 절을 단 제목은 1개였다. 20개 중 15개는 같은 시리즈의 `<area>: handle NULL value for ...` 커밋이다.

| 커밋 | 제목 | 본문 첫 문단 |
|---|---|---|
| [3681fccf0aa5](https://github.com/git/git/commit/3681fccf0aa5) | t: add "test-tool genbytes" to avoid slow tr(1) on macOS | Several tests create fixture data consisting of a single repeated character with "test-tool genzeros <count> \| tr '\000' <char>". This works well on platforms where tr(1) is implemented efficiently. |
| [7d792a25622f](https://github.com/git/git/commit/7d792a25622f) | config: handle NULL values when listing colors and expiry dates | Since 2d4ab5a885 (config: format colors quietly, 2026-02-23) and 9cb4a5e1ba (config: format expiry dates quietly, 2026-02-23), "git config list --type=color" and "--type=expiry-date" pass every value to color_parse_quietly() or parse_expiry_date(), which do not handle NULL. So both segfault when any key in the configuration has no value. |

## 3. Go: go.dev/doc/contribute

출처: [Contribution Guide, Good commit messages](https://go.dev/doc/contribute#commit_messages), [Go Wiki: Commit messages](https://go.dev/wiki/CommitMessage)

- Go 프로젝트는 첫 줄을 바꾼 내용의 요약으로 정한다. "The first line of the change description is conventionally a short one-line summary of the change, prefixed by the primary affected package."
- 첫 줄은 정해진 문장을 완성하도록 쓴다. "it should be written so to complete the sentence "This change modifies Go to _____.""
- 그래서 첫 줄은 대문자로 시작하지 않고 완전한 문장도 아니다. "That means it does not start with a capital letter, is not a complete sentence, and actually summarizes the result of the change."
- 본문은 맥락과 바꾼 내용을 설명한다. "The rest of the description elaborates and should provide context for the change and explain what it does."
- 길이는 엄격하지 않다. Wiki에는 "it should be kept as short as possible (many git viewing tools prefer under ~72 characters, though Go isn't super strict about this)."라고 적혀 있다.
- 안내서의 예시에서도 이유는 본문에 있다. 제목은 `math: improve Sin, Cos and Tan precision for very large arguments`이고, 본문은 "The existing implementation has poor numerical properties for large arguments, so use the McGillicutty algorithm to improve accuracy above 1e10."이다.

### 실제 이력

`golang/go`의 최근 커밋 20개(2026-11-23부터 2026-11-24까지)에서 목적 절을 단 제목은 0개였다. `test: update simd.go for the operand order after CL 829165`처럼 원인 커밋을 가리키는 제목은 있었다.

| 커밋 | 제목 | 본문 첫 문단 |
|---|---|---|
| [6d621ce35283](https://github.com/golang/go/commit/6d621ce35283) | bufio: stop Scanner permanently after terminal errors | The terminal error paths in Scan do not set done. Another call can therefore invoke the split function again and, after ErrTooLong, return part of the oversized token as though scanning had resumed. |
| [2b0a0b9f45de](https://github.com/golang/go/commit/2b0a0b9f45de) | go/types, types2: avoid repeated checking of same type in validType | Checker.validType checks that named types do not contain themselves via invalid recursive definitions. Previously, Checker.valids cached *Named types already found to be valid to avoid exponential traversal times on diamond-shaped type graphs ... |

## 4. Google engineering practices: CL descriptions

출처: [Writing good CL descriptions](https://google.github.io/eng-practices/review/developer/cl-descriptions.html)

- Google은 CL 설명이 "What"과 "Why"를 모두 전해야 한다고 적는다. "**What** change is being made?"와 "**Why** are these changes being made?"를 나란히 든다.
- 첫 줄은 무엇을 하는지만 요약한다. "The **first line** of a CL description should be a short summary of *specifically* **what** *is being done by the CL*, followed by a blank line."
- 첫 줄은 혼자 읽혀야 한다. "the first line should stand alone, allowing readers to skim through code history much faster."
- 첫 줄은 명령문이다. "By tradition, the first line of a CL description is a complete sentence, written as though it were an order (an imperative sentence)."
- 이유는 본문에 쓴다. "It might include a brief description of the problem that's being solved, and why this is the best approach."
- 좋은 예시의 설명도 같다. 제목 `RPC: Remove size limit on RPC server message freelist.` 아래에 "Servers like FizzBuzz have very large messages and would benefit from reuse."가 온다. Google은 이 예시를 "The first few words describe what the CL actually does. The rest of the description talks about the problem being solved"라고 풀이한다.
- 나쁜 예시로 "Fix bug"를 든다. "What bug? What did you do to fix it?"

## 5. Conventional Commits 1.0.0

출처: [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/)

- 형식은 `<type>[optional scope]: <description>`이다.
- description은 바꾼 코드의 요약이다. "The description is a short summary of the code changes, e.g., _fix: array parsing issue when multiple spaces were contained in string_."
- 본문은 선택이다. "A longer commit body MAY be provided after the short description, providing additional contextual information about the code changes."
- 명세에 정해진 type은 `feat`과 `fix`뿐이다. 나머지 type은 "Additional types are not mandated by the Conventional Commits specification"이다.
- scope는 맥락을 더하는 용도다. "A scope may be provided to a commit's type, to provide additional contextual information"
- 명세에는 제목 길이와 문장 형태 규칙이 없다.

## 6. Angular commit message guidelines

출처: [Commit Message Format](https://github.com/angular/angular/blob/main/contributing-docs/commit-message-guidelines.md)

- 헤더는 `<type>(<scope>): <short summary>`다. summary는 "Summary in present tense. Not capitalized. No period at the end."로 쓴다.
- summary는 바꾼 내용의 설명이다. "Use the summary field to provide a succinct description of the change"
- 문장 형태는 명령형 현재 시제다. "use the imperative, present tense: "change" not "changed" nor "changes""
- 이유는 본문에 쓴다. "Explain the motivation for the change in the commit message body. This commit message should explain _why_ you are making the change."
- 본문은 docs를 빼고 필수다. "The `body` is mandatory for all commits except for those of type "docs"." 본문이 있으면 "it must be at least 20 characters long"이다.
- scope는 바뀐 npm 패키지 이름이다. "The scope should be the name of the npm package affected (as perceived by the person reading the changelog generated from commit messages)."

### 실제 이력

`angular/angular`의 최근 커밋 20개(2026-11-23)에서 목적 절을 단 제목은 0개였다. `fix(...)` 커밋 작성자 상당수는 `avoid`, `do not`, `guard` 같은 동사로 막을 동작을 제목에 담았다.

| 커밋 | 제목 | 본문 첫 문단 |
|---|---|---|
| [f5bd414a7c62](https://github.com/angular/angular/commit/f5bd414a7c62) | fix(forms): treat infinite numbers as empty for required validation | Align the semantics of isEmpty and the required() validator with native number controls clearing infinite values. Non-finite numbers (NaN, Infinity, -Infinity) are now considered empty. |
| [9cb2a962eda2](https://github.com/angular/angular/commit/9cb2a962eda2) | fix(animations): avoid closure over hostElement in createRenderer | Commit a62ae5b04d extracted `getOrCreateBaseRenderer` out of `AnimationRendererFactory.createRenderer` so that `onRendererDestroy` would not share a V8 closure context with `registerTrigger` (which captured `hostElement`). |

## 7. Kubernetes

출처: [Pull Request Process, Commit Message Guidelines](https://github.com/kubernetes/community/blob/master/contributors/guide/pull-requests.md#commit-message-guidelines)

- Kubernetes는 커밋 메시지가 무엇과 왜를 정확히 담아야 한다고 적는다. "their commit messages should accurately describe both _what_ and _why_ it is being done."
- 한 줄 메시지는 무엇과 왜가 그 몇 단어에 다 들어갈 때만 쓴다. "but only if the what and especially why can be fully described in that few words."
- 제목은 50자 이내를 권장하고 72자를 넘기지 않는다. 50자 제한의 이유는 "It should be just enough to describe what is being done."이다.
- 제목은 명령형이다. Chris Beams의 "If applied, this commit will <your subject line here>" 문장을 인용한다.
- 접두어로 kind나 area를 쓸 수 있다. 예시는 `etcd: update default server to 3.4.7`과 `kube-proxy: add a test case for HostnameOverride`이다. 안내서에는 이런 제목을 두고 "These can serve as a good subject before expanding further on the what and why within the commit message body."라고 적혀 있다.
- 본문의 역할은 "Use the commit message body to explain the _what_ and _why_ of the commit"이다.

### 실제 이력

`kubernetes/kubernetes`의 최근 커밋 20개(2026-11-20부터 2026-11-21까지)에서 목적 절을 단 제목은 0개였다. 샘플로 본 `c7035d980d94`(`kubelet/kuberuntime: wait for dynamic volumes before starting containers`)와 `cb14d3ff20f4`(`subpath: clean up container subPath mounts on dynamic container removal`)는 본문이 없었다. 이 두 커밋의 이유는 커밋이 아니라 PR 설명에 있다. 안내서의 "PR comments are not represented in the commit history." 문장과는 다른 실제 운영이다.

## 8. Node.js

출처: [Pull requests, Commit message guidelines](https://github.com/nodejs/node/blob/main/doc/contributing/pull-requests.md#commit-message-guidelines)

- Node.js는 좋은 커밋 메시지가 무엇과 왜를 담는다고 적는다. "A good commit message should describe what changed and why."
- 첫 줄은 바꾼 내용의 짧은 설명이다. "contain a short description of the change (preferably 50 characters or less, and no more than 72 characters)"
- 첫 줄은 하위 시스템 접두어와 명령형 동사로 시작한다. "be prefixed with the name of the changed [subsystem](https://github.com/nodejs/node/blob/main/doc/contributing/pull-requests.md#appendix-subsystems) and start with an imperative verb."
- 예시는 `net: add localAddress and localPort to Socket`과 `src: fix typos in async_wrap.h`이다.

### 실제 이력

`nodejs/node`의 최근 커밋 20개(2026-11-23부터 2026-11-24까지)에서 목적 절을 단 제목은 0개였다.

| 커밋 | 제목 | 본문 첫 문단 |
|---|---|---|
| [642300f7aa7f](https://github.com/nodejs/node/commit/642300f7aa7f) | http: do not reuse expired free sockets | A free socket with an idle timeout is normally destroyed by its timer. When the event loop is blocked long enough for that timeout to expire, the timer cannot run, and `addRequest()` can reuse the socket even though the server may already have closed the connection. This can cause the request to fail with `ECONNRESET`. |
| [e7db48902e56](https://github.com/nodejs/node/commit/e7db48902e56) | src: speed up heap profile serialization | 본문 없이 `Signed-off-by`, `PR-URL`, `Reviewed-By` 트레일러만 있다. |

## 9. Chromium

출처: [Contributing to Chromium](https://chromium.googlesource.com/chromium/src/+/main/docs/contributing.md)

- Chromium의 형식은 `Summary of change (one line)` 다음에 본문이 오는 구조다.
- 이유는 본문에 쓴다. "Longer description of change addressing as appropriate: why the change is made, context if it is part of many changes, description of previous behavior and newly introduced differences, etc."
- 제목은 짧아야 한다. "A short subject and a blank line after the subject are crucial: `git` uses this as a heuristic for tools like `git log --oneline`."
- 본문은 72칸에서 줄을 바꾼다.

### 실제 이력

[Gitiles 로그](https://chromium.googlesource.com/chromium/src/+log/main)의 병합이 아닌 최근 커밋 20개에서 목적 절을 단 제목은 0개였다. 20개 가운데 9개 정도는 `Roll WebRTC from ... to ...` 같은 자동 롤 커밋이라 사람이 쓴 제목은 절반쯤이다. 사람이 쓴 제목의 예는 `Throttle BeginFrame on slower monitors on Windows`, `[Gardener] Disable failing wpt/svg/path/arc-tiny-radii.html on Win10`이다.

## 10. Mozilla (Firefox)

출처: [How to submit a patch](https://firefox-source-docs.mozilla.org/contributing/how_to_submit_a_patch.html), [Firefox Contributors' Quick Reference](https://firefox-source-docs.mozilla.org/contributing/contribution_quickref.html)

- 제목은 `Bug 123456 - Change this thing to work better by doing something. r=reviewers` 형식이다. 버그 번호가 접두어이고 리뷰어 표시가 뒤에 붙는다.
- 메시지에는 고친 내용을 쓰라고 적혀 있다. "The text of the message should be what you did to fix the bug, not a description of what the bug was."
- 이유는 필요할 때만 쓴다. "If it is not obvious why this change is appropriate, then explain why in the commit message."
- 한 줄에 다 들어가지 않으면 본문을 쓴다. "If this does not fit on one line, then leave a blank line and add further lines for more detail and/or reasoning."
- Quick Reference의 좋은 예시에는 제목에 목적이 함께 있다. "Bug 123456 - Null-check presentation shell so we don't crash when a button removes itself during its own onclick handler."
- Mozilla 문서에서 제목 길이 제한은 찾지 못했다.

### 실제 이력

GitHub 미러 `mozilla-firefox/firefox`의 최근 커밋 20개(2026-11-24)에서 목적이나 이유를 밝힌 제목은 4개였다. 2개는 `Revert "..." for causing xpcshell failures ...` 형식의 백아웃 제목이다. 1개는 `Back out ... until the upstream WPT is updated.`이다. 남은 1개만 `update component_schema.json to support images in existing blocks`처럼 목적 절을 단 일반 제목이다. 샘플로 본 커밋의 본문은 대부분 `Differential Revision:` 줄뿐이었다. 이유는 Bugzilla 버그와 Phabricator 리뷰에 남는다.

| 커밋 | 제목 | 본문 첫 문단 |
|---|---|---|
| [51f79e080264](https://github.com/mozilla-firefox/firefox/commit/51f79e080264) | Bug 2089208 - update component_schema.json to support images in existing blocks r=rrando,jlewis | Differential Revision: https://phabricator.services.mozilla.com/D339790 |
| [900d588e8d05](https://github.com/mozilla-firefox/firefox/commit/900d588e8d05) | Bug 2096426 - Avoid SearchSERPTelemetry.observeActivity dispatching to the main thread when there's no channel browser. r=search-reviewers,daleharvey | Depends on D345051 |

## 11. Rust (rust-lang)

[rustc-dev-guide의 git 안내](https://rustc-dev-guide.rust-lang.org/git.html)에서 커밋 제목 규칙은 찾지 못했다. 이 안내에는 bors가 PR의 커밋 메시지를 합치는 방법("By default, [bors] combines all commit messages of the PR into the squashed commit message.")만 적는다.

`rust-lang/rust`의 병합이 아닌 최근 커밋 20개(2026-11-23)에서 목적 절을 단 제목은 0개였다. `Add regression test`, `Update comment`처럼 PR 안의 작은 커밋이 그대로 남아 있었다. 이유는 PR과 Rollup 병합 커밋에 있다.

| 커밋 | 제목 | 본문 첫 문단 |
|---|---|---|
| [cdaa8e70f15e](https://github.com/rust-lang/rust/commit/cdaa8e70f15e) | std: Fix locking test on GNU/Hurd | GNU/Hurd supports locking downgrade from EX to SH, but on the same file handle only. |
| [ad221ac0f9e8](https://github.com/rust-lang/rust/commit/ad221ac0f9e8) | Check for errors before encoding metadata | 본문 없음 |

## 12. 한국 서비스 회사

### NHN Cloud: 좋은 git 커밋 메시지를 작성하기 위한 7가지 약속

출처: [NHN Cloud Meetup, 2017-03-16](https://meetup.nhncloud.com/posts/106)

- 이 글은 NHN이 자체로 정한 규칙이 아니다. 글쓴이는 "본 글은 Chris Beams의 How to Write a Git Commit Message 블로그 내용을 이해하기 빠르고 쉽게 전달하기 위해 번역, 편집하고 도움이 될만한 내용을 추가한 것입니다."라고 밝힌다.
- 7가지 약속에는 "제목은 영문 기준 50자 이내로", "제목은 `명령조`로", "본문은 `어떻게`보다 `무엇을`, `왜`에 맞춰 작성하기"가 있다. 이유를 다루는 약속은 본문에 대한 것뿐이다.
- 한국어 메시지에는 유연하게 적용하라고 적는다. "이하 약속은 커밋 메시지를 `English`로 작성하는 경우에 최적화되어 있습니다. 한글 커밋 메시지를 작성하는 경우에는 더 유연하게 적용하셔도 좋을 것 같네요."
- 글쓴이는 한국어 제목에 명사형 구문도 쓸 수 있다고 본다. "우리말로 제목을 요약하는 경우라면 때로는 '문장'보다 '구문'을 더 선호할 때도 있습니다." 예시는 `인증 메소드 수정`이다.

### 토스페이먼츠: 100년 가는 프론트엔드 코드, SDK

출처: [Toss Tech, 2025-11-14, 최진영](https://toss.tech/article/payments-legacy-3)

- 토스페이먼츠 프론트엔드 팀은 SDK 계약을 관리하는 저장소에 커밋 컨벤션을 만들었다. 목적은 "git diff만 봤을 때 누구에 의해 어떤 계약이 언제 변경됐는지 알 수 있게" 하는 것이다.
- 컨벤션에는 "하나의 커밋은 단 하나의 변경만을 담아야 합니다."와 "커밋 메시지만 보고도 변경의 의도를 충분히 파악할 수 있도록 자세히 작성해 주세요."가 있다.
- 작성 예시는 세 부분이다. 첫째는 "(변경 내용) 제품 [결제위젯, 브랜드페이, 결제창] 요소 [API, 파라미터, 응답]의 목적을 [추가, 변경, 삭제]"이다. 둘째는 "(변경의 이유) XXX 가맹점의 YYY 유즈케이스를 지원하기 위해 ZZZ 를 변경합니다."이다. 셋째는 "(설계 의도)"다.
- 변경 내용의 예는 "결제위젯 WidgetPaymentMethodWidget Model 에 'customRequest' 이벤트가 삭제됩니다."이다.
- 글에는 세 부분 가운데 어느 것이 제목인지 적혀 있지 않다. 변경 내용이 첫 줄에 오고 이유가 따로 온다는 배치만 확인했다.

토스가 공개한 [toss/es-toolkit](https://github.com/toss/es-toolkit) 최근 커밋 20개에서 목적 절을 단 제목은 0개였다. 제목은 `fix(invoke): return undefined when an intermediate path is missing (#2217)`처럼 Conventional Commits 형식에 PR 번호를 붙인다. 이 커밋은 GitHub squash 병합으로 들어와 본문이 PR 안 커밋 제목 목록이다.

### 우아한형제들: 우린 Git-flow를 사용하고 있어요

출처: [우아한형제들 기술블로그, 2017-10-30, 나동호](https://techblog.woowahan.com/2553/)

- 이 글에는 커밋 메시지 규칙이 없다. 명령 예시만 있다.
- 예시는 `git commit -m "BFM-100 로그인 화면 레이아웃 생성"`과 `git commit -m "BFM-101 로그인 아이디 길이 제한 버그 수정"`이다. 티켓 번호를 접두어로 쓰고 바꾼 내용만 쓴다.
- 같은 블로그의 [훅으로 Git에 훅 들어가기](https://techblog.woowahan.com/2530/)에도 메시지 규칙은 없었다.

### LINE: 광고 서버 개발 팀과 Armeria

출처: [LINE 광고 서버 개발 팀의 DevOps 문화, 2021-04-28, 김남우](https://engineering.linecorp.com/ko/blog/line-ads-devops-culture)

- LINE 광고 서버 팀은 커밋 메시지를 영어로 쓴다. "커밋 메시지와 PR(Pull Request)에 대한 설명은 전부 영어로 작성하고 있습니다."
- 접두어는 이슈 번호다. "커밋 로그에는 이슈 트래커의 번호(예: LADS-2797)를 말머리에 포함하도록 하고 있는데요."
- 이 글에는 제목에 목적을 쓰는지에 대한 규칙이 없다.

LINE이 공개한 [Armeria 개발자 안내](https://armeria.dev/community/developer-guide#how-to-write-pull-request-description)에는 PR 설명을 세 부분으로 나누라고 적혀 있다. "Usually, you need to fill the following 3 sections: Motivation, Modifications and Result." Motivation에는 "Explain why you're sending the pull request and what problem you're trying to solve."라고 적혀 있다. Result에는 "Describe the consequences that a user will face after this pull request is merged."라고 적혀 있다. Armeria는 squash 병합을 하므로 PR 제목이 커밋 제목이 되고, PR 설명이 커밋 본문이 된다. 안내서에 PR 제목 규칙은 없다.

`line/armeria`의 최근 커밋 20개에서 목적 절을 단 제목은 1개였다.

| 커밋 | 제목 | 본문 첫 문단 |
|---|---|---|
| [bfd010e02c7c](https://github.com/line/armeria/commit/bfd010e02c7c) | Bound session establishment when a proxy disconnects (#7140) | Motivation: An HTTPS request can remain pending indefinitely if its CONNECT proxy closes the connection before returning `200 Connection Established`. |
| [2be51ee8cbe4](https://github.com/line/armeria/commit/2be51ee8cbe4) | Add `HealthChecker.ofDeadlock()` to detect deadlocked threads (#7081) | Motivation: #1854 asks for a `HealthChecker` that reports unhealthy when the JVM has deadlocked threads, and it has not been implemented yet. |

두 번째 커밋은 제목에 목적 절이 있다. 그래도 작성자는 본문 Motivation에 이유를 다시 썼다.

### 카카오

카카오 기술 블로그(tech.kakao.com)에서 커밋 메시지 규칙 글은 찾지 못했다. 카카오가 공개한 [kakao/kakao_flutter_sdk](https://github.com/kakao/kakao_flutter_sdk)의 최근 커밋 20개(2026-08-25부터 2026-10-23까지)에서는 목적 절을 단 제목이 7개였다. 조사한 저장소 가운데 가장 많다.

| 커밋 | 제목 | 본문 |
|---|---|---|
| [e5202ef86bba](https://github.com/kakao/kakao_flutter_sdk/commit/e5202ef86bba) | add the 'showSendToMe' property to PickerSettings class to allow to hide forMe button when the KakaoTalk sharing feature is triggered. | 본문 없음 |
| [211c741540a5](https://github.com/kakao/kakao_flutter_sdk/commit/211c741540a5) | fix the addChannel feature to record referer | 본문 없음 |
| [96f23bd6d96b](https://github.com/kakao/kakao_flutter_sdk/commit/96f23bd6d96b) | fix the code to ensure that the kakaotalk sharing scheme does not change in the Android web | 본문 없음 |

이 저장소의 커밋에는 본문이 거의 없다. 작성자가 본문 대신 제목에 이유를 넣은 것으로 보인다. 첫 예시의 제목은 130자가 넘는다. 이 저장소에서 공개된 커밋 메시지 규칙 문서는 찾지 못했다.

### 당근

당근 기술 블로그(medium.com/daangn)에서 커밋 메시지 규칙 글은 찾지 못했다. 당근이 공개한 [daangn/stackflow](https://github.com/daangn/stackflow)의 최근 커밋 20개에서 목적 절을 단 제목은 0개였다. 20개 중 10개는 자동 생성된 `Version Packages` 커밋이다. 사람이 쓴 제목은 `fix(react): stop retrying rejected lazy loads (#753)`처럼 Conventional Commits 형식이다.

### 찾지 못한 곳

- 카카오 기술 블로그: 커밋 메시지 규칙 글을 찾지 못했다.
- 당근 기술 블로그: 커밋 메시지 규칙 글을 찾지 못했다.
- 네이버 D2: 검색에서 커밋 메시지 규칙 글이 나오지 않았다.
- 우아한형제들: 메시지 규칙을 다룬 글은 없고 예시만 찾았다.
- 토스: 제목과 본문을 나누는 규칙은 없고, 변경 내용과 변경의 이유를 나누는 예시만 찾았다.

## 비교

"목적 절 수"는 병합이 아닌 최근 커밋 20개 가운데 제목에 `to ...`, `so that ...`, `until ...`, `for causing ...`처럼 목적이나 이유를 밝히는 절이 있는 커밋 수다. "fix X crash"처럼 바꾼 내용의 목적어로 문제를 쓴 제목은 세지 않았다.

| 출처 | 제목에 쓰는 것 | 이유를 쓰는 곳 | 제목 길이 | 문장 형태 | 접두어 | 목적 절 수 |
|---|---|---|---|---|---|---|
| 리눅스 커널 | 바꾼 내용과 이유 모두 ("must describe both what ... as well as why") | 본문 | 70~75자 이하 | 명령형 | `subsystem:` | 0/20 |
| Git | 짧은 설명 | 본문 (문제, 근거, 대안) | 50자 권장 | 명령형, 소문자 시작 | `area:` | 1/20 |
| Go | 바꾼 결과의 요약 | 본문 (맥락) | 약 72자 미만 권장, 엄격하지 않음 | "This change modifies Go to ___" 완성 | 패키지 경로 | 0/20 |
| Google CL | 무엇을 하는지 | 본문 | 짧게 | 명령문 | 규칙 없음 | 해당 없음 |
| Conventional Commits | 코드 변경 요약 | 본문 (선택) | 규칙 없음 | 규칙 없음 | `type(scope):` | 해당 없음 |
| Angular | 변경 설명 | 본문 (필수, docs 제외) | 규칙 없음(이 문서 기준) | 명령형 현재 | `type(scope):` | 0/20 |
| Kubernetes | 무엇을 하는지 ("just enough to describe what is being done") | 본문 | 50자 권장, 72자 이하 | 명령형 | kind나 area (선택) | 0/20 |
| Node.js | 변경의 짧은 설명 | 본문 | 50자 권장, 72자 이하 | 명령형 | 하위 시스템 | 0/20 |
| Chromium | 변경 요약 한 줄 | 본문 | 짧게 | 규칙 없음 | 관례적 `[Area]` | 0/20 |
| Mozilla | 고친 내용, 필요하면 이유 | 제목이나 본문, 실제로는 Bugzilla와 Phabricator | 규칙 없음 | 규칙 없음 | `Bug N -` | 4/20 (2개는 백아웃) |
| Rust | 규칙 없음 | PR | 규칙 없음 | 규칙 없음 | 규칙 없음 | 0/20 |
| NHN Cloud (번역 글) | 변경 요약 | 본문 | 영문 50자 | 명령조 (한국어는 유연하게) | 규칙 없음 | 해당 없음 |
| 토스페이먼츠 | 변경 내용 | 변경의 이유를 따로 | 규칙 없음 | 규칙 없음 | 규칙 없음 | es-toolkit 0/20 |
| LINE Armeria | 규칙 없음 | 본문 Motivation | 규칙 없음 | 규칙 없음 | 광고 팀은 이슈 번호 | 1/20 |
| 카카오 Flutter SDK | 문서 없음 | 문서 없음 | 문서 없음 | 문서 없음 | 일부 `type:` | 7/20 |
| 당근 stackflow | 문서 없음 | 문서 없음 | 문서 없음 | 문서 없음 | `type(scope):` | 0/20 |

## 이 저장소 규칙에 주는 의미

### 현재 규칙과 조사 결과가 겹치는 곳

- 리눅스 커널 문서에는 제목이 이유도 담아야 한다고 적혀 있다. 현재 규칙의 방향과 같은 문장이다.
- Mozilla의 좋은 예시와 카카오 Flutter SDK 이력도 제목에 목적 절을 쓴다.
- 리눅스 커널, Git, Go, Google, Kubernetes, Node.js, Mozilla, 토스페이먼츠, Armeria의 안내서에는 커밋 메시지 어딘가에 이유를 쓰라고 적혀 있다. 차이는 이유를 제목과 본문 가운데 어디에 두느냐다. Conventional Commits에서 본문은 선택 사항이다. Rust와 우아한형제들에서는 관련 규칙을 찾지 못했다.

### 현재 규칙과 조사 결과가 다른 곳

- 조사한 규칙 대부분에서는 제목에 바꾼 내용만 쓰고 이유는 본문에 쓴다. Google은 첫 줄을 "what"으로, Kubernetes는 "just enough to describe what is being done"으로 정한다.
- 이유를 요구하는 리눅스 커널도 실제 제목 20개 가운데 목적 절이 있는 제목은 0개였다. 커널 작성자들은 문제를 동사의 목적어로 담는다(예: `Fix TOCTOU kernel stack disclosure`). 작성자가 문제를 동사의 목적어로 쓰면 목적 절 없이도 왜 바꿨는지 짐작할 수 있다.
- 조사한 규칙에서 제목 앞에 목적을 먼저 쓰라고 정한 곳은 없었다. 현재 규칙은 `목적, 바꾼 내용` 순서다. 그래서 `git log --oneline`에서 바꾼 대상이 문장 뒤쪽에 온다.
- 길이 제한과 부딪친다. 조사한 규칙 대부분에는 50자를 권장하고 72자를 넘기지 말라고 적혀 있다. 현재 규칙의 예시 요약은 59자다. 한글은 터미널에서 두 칸을 차지하므로, `[BE] feat: `를 붙인 제목은 약 112칸이다. 제목 길이는 이 저장소의 `.githooks/` 훅으로 검사되지 않는다.
- 현재 규칙에서는 목적이 제목과 본문 첫 문단(before)에 겹쳐 나온다. Armeria `2be51ee8cbe4`의 작성자도 제목의 목적 절을 본문 Motivation에서 다시 썼다.

### 선택지

아래 선택지는 사용자가 고른다. 어느 쪽을 고르든 `docs/agents/commit-issue-pr-writing.md`의 "쓰는 순서", "되풀이" 기준, "리뷰와 검수 점검"과 루트 `AGENTS.md` Git 절의 "제목에는 목적과 바꾼 내용만 담고" 문장을 함께 맞춰야 한다.

1. **현재 규칙 유지 (`목적, 바꾼 내용`)**
   - 근거: 리눅스 커널 문서의 "what ... as well as why" 문장, Mozilla 예시, 카카오 Flutter SDK 이력.
   - 장점: 제목만 읽어도 누가 무엇을 얻는지 안다. 이 저장소가 겪은 "사건 접근 검사 추가" 같은 모호한 제목을 리뷰에서 걸러낼 수 있다.
   - 비용: 제목이 50~72자 권장을 자주 넘는다. 바꾼 대상이 제목 뒤쪽에 밀린다. 목적이 제목과 본문에 겹친다. 조사한 출처 가운데 이 순서를 규칙으로 정한 곳은 없다.

2. **제목은 바꾼 내용만, 목적은 본문 첫 문단에**
   - 근거: Google, Go, Git, Angular, Conventional Commits, Kubernetes, Node.js, Chromium, Armeria, 토스페이먼츠 예시.
   - 장점: 제목이 짧아지고 `git log --oneline`에서 바꾼 대상을 먼저 읽는다. 목적이 한 곳에만 있다.
   - 비용: 한 줄 로그에서 이유가 보이지 않는다. 현재 리뷰 점검("제목만 읽고 누구에게 무엇이 달라지는가")을 본문 기준으로 바꿔야 한다. 본문이 없는 커밋을 막는 장치가 필요할 수 있다(Angular는 docs를 뺀 모든 커밋에 본문을 요구한다).

3. **제목은 바꾼 내용 중심으로 쓰되, 문제를 바꾼 내용에 담거나 길이 안에서만 목적을 붙임**
   - 근거: 리눅스 커널의 실제 이력(`Fix ... disclosure`, `Do not allow ...`), Kubernetes의 "only if the what and especially why can be fully described in that few words", Mozilla의 "If it is not obvious why this change is appropriate, then explain why".
   - 장점: 리눅스 커널 문서와 실제 관례를 함께 따른다. 목적이 바꾼 내용에서 드러나면 제목이 짧아진다.
   - 비용: "목적을 언제 제목에 붙이는가"를 사람이 판단해야 한다. 현재 규칙보다 리뷰 기준이 덜 기계적이다. 바꾼 대상을 앞에 둘지 목적을 앞에 둘지 순서도 다시 정해야 한다.

4. **(1~3과 함께 고를 수 있음) 제목 길이 상한 추가**
   - 근거: Git 50자 권장, Kubernetes와 Node.js 72자 상한, 리눅스 커널 70~75자 상한.
   - 고를 것: 한글 기준 글자 수로 셀지 터미널 칸 수로 셀지, `[Area] type: ` 접두어를 포함할지, 훅으로 검사할지 리뷰에서만 볼지.

### 확인하지 못한 것

- 표본은 2026-11-24에 각 저장소의 GitHub API 첫 페이지(Chromium은 Gitiles 로그)에서 병합이 아닌 커밋 20개만 봤다. 저장소마다 시기와 시리즈가 치우칠 수 있다. 예를 들어 Git 표본의 15개는 같은 시리즈 커밋이다.
- 목적 절 판단은 조사자가 제목을 하나씩 읽고 했다. 문제를 목적어로 쓴 제목을 목적으로 볼지는 기준에 따라 수가 달라진다.
- 한국 회사의 내부 저장소 규칙은 공개되지 않아 확인할 수 없었다. 공개 저장소의 관례가 그 회사 서비스 저장소의 관례와 같다고 볼 근거는 없다.
- 한국어 제목에서 "목적, 바꾼 내용" 순서가 읽기에 더 나은지 나쁜지를 측정한 1차 자료는 찾지 못했다.
