# 기업 공개 저장소의 k6 스크립트 언어 조사

- 관찰일: 2026-08-31
- 표본: 사전에 한정한 공식 조직 소유 공개 저장소 4개
- 질문: 공개적으로 확인 가능한 조직은 k6 스크립트의 작성 언어로 JavaScript와 TypeScript 중 무엇을 쓰는가?

## 결론

조직당 대표 사례 하나를 세면 JavaScript 2곳, TypeScript 2곳이다. GitLab은 운영형 JavaScript 도구이고, Determined AI와 Duende Software는 제품 저장소의 TypeScript 성능 시험 코드다. Google Cloud의 JavaScript는 공식 튜토리얼 안의 샘플일 뿐 내부 프로덕션 사용 근거가 아니다.

이 네 곳은 무작위 표본이 아니다. 따라서 “기업은 어느 언어를 더 많이 쓴다”는 비율이나 추세를 도출할 수 없고, 두 언어 모두 공식 조직의 공개 자료에서 실제로 확인된다는 범위까지만 결론 낼 수 있다.

## 고정 커밋 검증

| 조직·저장소 | 자료 성격 | 작성 언어 | 확인한 근거와 한계 |
| --- | --- | --- | --- |
| `gitlab-org/quality/performance` | 운영형 GitLab Performance Tool | JavaScript | 고정 커밋의 [`k6/tests/api/api_v4_groups.js`](https://gitlab.com/gitlab-org/quality/performance/-/blob/bb7cdef4a125184fa88057f08ed5a400a071e261/k6/tests/api/api_v4_groups.js)가 `k6/http`, `k6`, `k6/metrics`를 가져와 GitLab API를 시험한다. 같은 커밋의 [README](https://gitlab.com/gitlab-org/quality/performance/-/blob/bb7cdef4a125184fa88057f08ed5a400a071e261/README.md)는 이 도구가 k6 기반이며 파이프라인으로 Reference Architecture 성능을 자동·지속 검증한다고 밝힌다. |
| `determined-ai/determined` | 제품 저장소의 성능 시험 묶음 | TypeScript 원본 | [`performance/k6/src/api_performance_tests.ts`](https://github.com/determined-ai/determined/blob/c1e9c6d7b821ef246d612bb9922f7fb1b58dcf2e/performance/k6/src/api_performance_tests.ts)가 `k6`를 가져와 Determined API 시험·시나리오·threshold를 정의한다. [README](https://github.com/determined-ai/determined/blob/c1e9c6d7b821ef246d612bb9922f7fb1b58dcf2e/performance/k6/README.md)는 nightly 성능 시험 용도라고 설명하지만, TypeScript를 빌드한 `build/api_performance_tests.js`를 k6로 실행한다. [빌드 설정](https://github.com/determined-ai/determined/blob/c1e9c6d7b821ef246d612bb9922f7fb1b58dcf2e/performance/k6/package.json)도 Babel·Webpack을 사용한다. 따라서 TypeScript **작성**의 운영형 근거이지 k6의 TypeScript 직접 실행 근거는 아니다. README가 가리키는 workflow는 이 고정 커밋의 트리에 없어 관찰일 현재 nightly 실행 여부까지는 확정하지 않았다. |
| `DuendeSoftware/foss` | 제품 저장소의 perf 코드 | TypeScript | [`access-token-management/perf/Perf.K6/token.ts`](https://github.com/DuendeSoftware/foss/blob/6eaad5d969799f3a7eb388238fecaa655c66bd19/access-token-management/perf/Perf.K6/token.ts)가 `k6/http`를 가져오고 VU·실행 시간과 `/token` 요청을 정의한다. 공개 트리에서 CI나 실행 설명은 확인되지 않았으므로 TypeScript 성능 시험 파일의 존재만 근거로 삼고 정기 실행이나 프로덕션 사용은 주장하지 않는다. |
| `GoogleCloudPlatform/gke-fleet-management` | 공식 GKE Hello World 샘플 | JavaScript 예제 | [`multi-cluster-orchestrator/samples/hello_world/README.md`](https://github.com/GoogleCloudPlatform/gke-fleet-management/blob/dbf58b5eaa0d5eb0a4f780582e217ee28ab920b3/multi-cluster-orchestrator/samples/hello_world/README.md#L326-L371)가 `loadtest.js`를 작성해 `k6 run`으로 부하를 만들도록 안내한다. 해당 디렉터리에 실제 `.js` 파일은 없고 README 코드 블록만 있으므로 Google 내부 프로덕션 관행의 증거가 아니다. |

네 해시는 관찰일에 각 원격 저장소의 `HEAD`로 다시 확인했으며, 위 링크는 브랜치명이 아닌 40자리 commit SHA를 사용한다.

## 조직 수와 파일 수

| 집계 단위 | JavaScript | TypeScript | 합계 |
| --- | ---: | ---: | ---: |
| 조직별 대표 사례 | 2 | 2 | 4개 조직 |
| 커밋된 대표 k6 진입 파일 | 1 | 2 | 3개 파일 |
| README 안의 실행 예제 | 1 | 0 | 1개 코드 블록 |

Google 사례는 파일이 아니라 문서가 독자에게 만들도록 한 `loadtest.js`다. 반대로 GitLab 고정 커밋의 `k6/tests` 아래에는 `.js` 파일이 97개다. 파일을 모두 합산하면 한 저장소의 크기가 결과를 지배하므로, 언어 비교는 파일 비율이 아니라 조직당 대표 사례 하나로 제한했다.

## k6 TypeScript 직접 실행의 역사

- [k6 v0.52.0](https://github.com/grafana/k6/blob/20f8febb5becbdc571f46adc8cdcfc0df1113a5f/release%20notes/v0.52.0.md#L52-L63)은 `experimental_enhanced` 모드에서 esbuild로 `.ts`를 읽는 실험 지원을 도입했다. 처음부터 타입 정보만 제거할 뿐 타입 안전성은 제공하지 않는다고 명시했다.
- [k6 v0.57.0](https://github.com/grafana/k6/blob/50afd82c18d5a66f4b2bfd1f8d266218bfdeaede/release%20notes/v0.57.0.md#L9-L13)은 `.ts` 파일을 자동 인식하도록 바꾸고 실험 호환 모드를 불필요하게 만들었다.
- [k6 v1.0.0](https://github.com/grafana/k6/blob/41b4984b7594a8828bfa724ae32b49c15c76b13d/release%20notes/v1.0.0.md#L31-L33)은 이를 “First-Class TypeScript Support”라 부르며 `k6 run script.ts` 직접 실행을 안내했다.
- 관찰일의 [k6 v2.2 문서 원본](https://github.com/grafana/k6-docs/blob/ce3ae5982e8563a9af4b6bfd91edb8d014a031f0/docs/sources/k6/v2.2.x/using-k6/javascript-typescript-compatibility-mode.md#L55-L71)도 내부적으로 esbuild가 TypeScript를 변환하며 지원은 부분적이고 타입 안전성을 제공하지 않는다고 명시한다.

따라서 여기서 “직접 지원”은 사용자가 별도 변환 명령 없이 `.ts` 파일을 k6에 전달할 수 있다는 뜻이지, k6가 TypeScript 타입 검사를 수행한다는 뜻이 아니다. 타입 오류를 막으려면 `tsc --noEmit` 같은 별도 검사가 필요하다.

## 해석 한계

- 공식 조직이 공개한 현재 저장소만 보므로 비공개 사내 시험, 삭제된 코드와 검색되지 않는 저장소는 관찰할 수 없다.
- 운영 도구, 제품 저장소의 perf 파일, 튜토리얼을 함께 찾은 편의 표본이라 조직 규모·산업·k6 사용 강도를 통제하지 않았다.
- 한 파일의 존재는 해당 조직 전체의 표준, 현재 실행 빈도 또는 내부 프로덕션 채택을 증명하지 않는다.

따라서 이 조사만으로 수리맵의 JavaScript를 TypeScript로 바꿀 근거는 없다. 선택 기준은 공개 사례 수가 아니라 별도 타입 검사와 필요한 경우 빌드 구성을 감수할 만큼 타입 정보가 실제 유지보수에 도움이 되는지여야 한다.
