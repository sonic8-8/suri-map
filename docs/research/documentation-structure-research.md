# 문서 목적과 폴더 구조 조사

조사 기준일: 2026-09-21. 공식 문서의 지침과 Suri-Map에 적용한 선택을 구분한다. 비교한 후보 중 C안과 `docs/index.md`를 사용자와 합의해 반영했다. 현재 안내는 [문서 목차](../index.md)에서 확인한다.

## 요약

문서의 **목적**, 파일의 **배치**, 독자가 들어오는 **목차·링크**는 별도로 판단해야 한다. Diátaxis는 독자의 필요에 따른 네 종류를 제시하지만, 시작부터 빈 네 분류를 만들지 말라고 명시한다. GitLab도 문서 안의 유형과 독자·기능 중심 폴더를 별도로 정의한다. 따라서 이 자료들로 모든 저장소에 동일한 네 폴더가 필수라는 결론을 낼 수 없다. [Diátaxis의 적용 방식](https://diataxis.fr/how-to-use-diataxis/), [GitLab 문서 유형](https://docs.gitlab.com/development/documentation/topic_types/), [GitLab 폴더 구조](https://docs.gitlab.com/development/documentation/site_architecture/folder_structure/)

Suri-Map에서는 기능별 문서를 묶고, 폴더명과 실제 용도가 맞지 않는 문서를 재배치했다. ADR·조사·이슈·검증 자료처럼 목적이 분명한 분류는 유지했다. 이는 아래 지침을 저장소에 맞게 적용한 선택이지 보편적인 필수 폴더 구조는 아니다.

## 1차 자료에서 확인한 원칙

### Diátaxis: 문서가 답할 질문을 구분한다

네 종류는 다음과 같이 독자가 수행하는 일과 학습에 필요한 것을 구분한다. 폴더 이름부터 고르는 분류표로 한정되지 않는다. [공식 입문](https://diataxis.fr/start-here/)

| 종류 | 주된 필요 |
|---|---|
| Tutorial | 안내를 따라 직접 해 보며 배운다. |
| How-to guide | 이미 이해한 도구로 특정 일을 끝낸다. |
| Reference | 작업에 필요한 정확한 사실·명세를 찾아본다. |
| Explanation | 배경과 이유, 다른 개념과의 관계를 이해한다. |

처음부터 전체 체계를 완성하려 하지 말고, 눈앞의 작은 문서를 개선하며 구조가 드러나게 하라고 권한다. 구조를 중요하게 보지만, 빈 tutorial/how-to/reference/explanation부터 만들라는 뜻은 아니다. [공식 작업 지침](https://diataxis.fr/how-to-use-diataxis/)

Suri-Map 적용 해석: ADR은 이유를 설명하지만 과거 결정의 기록이기도 하고, 기능 문서는 요구와 구현 차이를 함께 보존한다. 모든 파일을 네 종류 중 하나로 강제 분리할 필요가 있는지는 실제 독자의 질문으로 판단한다.

### Google: 작고 정확한 문서와 탐색 안내를 유지한다

Google의 저장소 문서 지침은 짧고 유용한 문서, 코드와 같은 변경에서의 갱신, 중복 대신 링크를 강조한다. 디렉터리 README의 역할은 그곳에 무엇이 있고 무엇부터 읽을지 알려 주는 것이다. 구현 후 설계 문서는 부정확한 사용 설명서로 남기지 말고 결정의 기록으로 다루라고 설명한다. [Documentation Best Practices](https://google.github.io/styleguide/docguide/best_practices.html)

긴 한 문서와 연결된 짧은 문서 중 하나가 항상 정답은 아니다. Google의 기술 문서 교육은 독자의 경험·읽는 목적에 따라 나누고, 제목·목차·관련 자료 링크로 탐색을 돕도록 한다. 처음부터 모든 배경을 읽게 하지 말고 필요한 때 상세 설명을 연결한다. [Organizing large documents](https://developers.google.com/tech-writing/two/large-docs)

제목은 문서의 주된 목적을, 절 제목은 해당 내용의 성격을 드러내야 한다. 같은 문서 안에서도 개념 설명과 작업 안내의 제목 형태가 다를 수 있다. [Headings and titles](https://developers.google.com/style/headings)

다만 Google의 README 세부 지침은 README를 제품·라이브러리의 문서 디렉터리가 아니라 코드 최상위에 두도록 한다. 따라서 `docs/README.md`라는 위치까지 Google의 권장이라고 주장해서는 안 된다. [README 배치 지침](https://google.github.io/styleguide/docguide/READMEs.html#where-to-put-your-readme)

GitHub는 `.github` → 루트 → `docs` 순으로 README를 찾아 저장소 첫 화면에 표시한다. 현재 Suri-Map에는 앞의 두 위치에 README가 없으므로 `docs/README.md`를 추가하면 문서 목차가 저장소 소개 화면에도 나타난다. [GitHub의 README 표시 순서](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-readmes#about-readmes)

Suri-Map 적용: 루트 README는 프로젝트 소개로 별도 관리하고, 문서 목차는 `docs/index.md`로 선택했다. 소개 화면을 문서 목차로 대신하지 않으며, 목차에는 본문을 복제하지 않고 원문으로 연결한다.

### GitLab: 실제 폴더는 독자와 기능을 따라간다

GitLab의 기본 topic type은 Concept·Task·Reference·Troubleshooting이다. 짧은 페이지도 개념 설명 뒤에 작업이나 참고 정보를 포함할 수 있다. 따라서 topic type 하나가 반드시 파일 하나 또는 폴더 하나를 뜻하지 않는다. [공식 문서 유형](https://docs.gitlab.com/development/documentation/topic_types/)

실제 폴더 지침은 사용자·관리자·개발 기여자를 구분하고, 그 아래는 주로 UI·API 기능 구조를 따른다. 새 문서·이름이 바뀐 문서는 상위 안내와 관련 문서에서 연결하고 중복을 피하도록 한다. [공식 폴더 구조](https://docs.gitlab.com/development/documentation/site_architecture/folder_structure/)

GitLab은 안내 파일에 `_index.md`를 쓰고 README를 쓰지 않도록 정한다. 이는 해당 사이트의 구체적인 운영 규칙이다. Suri-Map이 그 파일명·사이트 도구까지 가져와야 한다는 근거는 아니다. [GitLab의 파일·디렉터리 규칙](https://docs.gitlab.com/development/documentation/site_architecture/folder_structure/#work-with-directories-and-files)

## 정리 전 Suri-Map에서 확인한 조건

- 정리 전 `docs/` 루트 Markdown은 PRD·Architecture와 기능별 문서 11개였으며, `docs/README.md`는 없었다. 아래에 `adr`, `api`, `db-design`, `screen-design`, `contracts`, `tasks`, `refactoring`, `research`, `evidence`, `issues`, `assets`가 있었다. 저장소 파일 목록을 확인한 결과다.
- [PRD](../prd.md)는 제품 요구를, [Architecture](../architecture.md)는 구성과 코드 위치를 안내한다. [오프라인 동기화](../features/offline-sync.md)처럼 기능 문서는 기존 요구·현재 차이·미확인 사항을 보존한다. 폴더 정리가 이 구분을 없애면 안 된다.
- [저장소 규칙](../../AGENTS.md#문서-사용)에 따라 현재 동작은 코드·SQL·실행 결과와 대조하고, 문서는 의도·필요한 동작·검증 한계를 남긴다. 테스트도 실제 검증 범위를 읽어야 한다.
- 같은 규칙에서 Issue는 발견한 문제, 로컬 TODO는 개인 작업 순서, ADR은 중요한 선택 이유를 기록한다. 문서 종류를 단순화한다는 이유로 모든 요구와 계획을 Issue로 옮기지 않는다.
- 기존 [ADR 안내](../adr/README.md)처럼 하위 묶음의 진입점은 이미 일부 있다. 새 문서 체계를 처음부터 만드는 상황은 아니다.
- 폴더명과 실제 용도가 어긋나 있었다. `contracts`에 있던 [구역 편집 문서](../screen-design/area-edit.md)는 화면 설계 배경이며, `tasks`에 있던 [mock 112 문서](../guides/mock-112-demo.md)는 역할·시연 안내다. [API 상태표](../api/api-implementation-status.md)는 API 문서와 함께 읽는 테스트 입력이다. 단순히 옛 문서라고 일괄 보관하거나 삭제할 대상은 아니다.
- [ApiImplementationStatusCoverageTest](../../backend/src/test/java/com/surimap/architecture/ApiImplementationStatusCoverageTest.java)가 API 명세와 상태표를 직접 읽는다. 상태표 이동과 함께 테스트의 파일 경로를 갱신했다. 공용 fixture의 출처 경로도 검사기가 실제 파일 존재를 확인하므로 함께 갱신했다.

## 비교한 구조 후보

세 후보 모두 루트 README의 프로젝트 소개 역할, 기존 문서의 고유 요구·출처, 실제 결함 기록을 보존한다. 아래 경로명은 비교용 예시다.

| 후보 | 최소 변경 | 얻는 점 | 남는 비용·한계 |
|---|---|---|---|
| A. 배치는 유지하고 진입점만 추가 | `docs/index.md` 또는 `docs/README.md`에서 제품·기능·결정·조사·검증 기록으로 연결 | 기존 경로가 안정적이고 바로 시도할 수 있음 | 파일 트리의 루트 13개와 기존 폴더명 혼재는 그대로 남음 |
| B. 기능 문서만 한 묶음으로 이동 | A + 기능별 11개를 예컨대 `docs/features/`로 묶고 PRD·Architecture는 루트에 유지 | 읽는 목적이 가까운 문서를 함께 찾고 루트를 줄임 | 문서 내부·외부 링크와 AGENTS 등 실제 참조 수정 필요; `contracts`·`tasks`의 의미는 따로 판단 |
| C. 목적에 맞지 않는 배치까지 정리 | B + `contracts`·`tasks` 등의 문서를 실제 용도로 재분류. 목적이 분명한 ADR·조사·이슈·검증 기록은 유지 | 새 문서 위치를 예측하기 쉽고 폴더명·내용의 불일치도 줄임 | 다목적 문서의 경계·이동표와 관련 테스트를 확인해야 함. 분류를 채우기 위한 빈 폴더는 만들지 않음 |

선택 이유: 진입점만 부족하다면 A, 최상위 파일이 문제라면 B가 작다. 이번에는 `contracts`·`tasks`의 용도 불일치도 있으므로 C를 선택했다. 모든 폴더를 새 체계로 바꾸지 않고 이미 명확한 분류는 유지했다. 외부 표준의 의무 사항은 아니다.

## 적용 원칙

1. **질문으로 찾는다.** 예를 들어 “왜 만들었나 → PRD”, “어떻게 연결되나 → Architecture”, “동기화에서 무엇이 남았나 → 기능 문서”, “왜 이 기술인가 → ADR”, “무엇을 실제로 확인했나 → 검증 기록”으로 진입점을 정한다.
2. **목차는 원문을 복제하지 않는다.** 링크와 짧은 사용 안내만 두고, 같은 요구·상태를 여러 표에 반복하지 않는다.
3. **과거 문서를 이름만 보고 치우지 않는다.** `contracts`·`tasks`에도 필요한 미구현 요구가 있을 수 있다. 유효한 요구를 남긴 뒤, 과거 계획·현재 안내·실행 입력을 구분한다.
4. **이동은 내용 삭제가 아니다.** 채택 후 파일별 입력 참조·상대 링크·AGENTS·빌드/스크립트 사용처를 확인한다. JSON·fixture·실행 자료도 별도로 보존한다.
5. **작업한 부분만 갱신한다.** 새로운 요구나 구현 차이가 생긴 기능 문서를 고치되, 모든 작업마다 새 spec·ADR·보고서를 요구하지 않는다.

위 원칙 중 중복 최소화·탐색 안내·코드와 함께 갱신은 Google·GitLab 지침을 적용한 제안이다. 요구와 실행 입력의 보존은 외부 지침보다 [저장소 규칙](../../AGENTS.md#문서-사용)을 우선한다. [Google 문서 관리](https://google.github.io/styleguide/docguide/best_practices.html), [GitLab 탐색·중복 기준](https://docs.gitlab.com/development/documentation/site_architecture/folder_structure/)

## 반영한 선택

- 프로젝트 소개와 개발용 탐색 안내를 분리한다. 새 진입점은 [docs/index.md](../index.md)이며 루트 README는 건드리지 않는다.
- 기능 문서 11개를 `features/`로 묶고, `contracts`·`tasks`의 8개는 API·화면 설계·작업 안내·리팩토링·보류 제안으로 옮겼다. 루트에는 PRD·Architecture·목차만 남긴다.
- 문서의 고유 요구·원문 출처와 실행 입력은 보존한다. 링크·AGENTS·API 소비 테스트·공용 fixture의 출처 경로를 함께 갱신한다.

## 근거의 한계와 이번 검증

공식 지침은 해당 저자·조직의 권장 방식이다. 어떤 구조가 Suri-Map에서 더 빨리 검색되는지 측정한 결과는 아니며, 대규모 제품 사용자 문서와 개인 학습·개발 기록은 독자가 다르다. Diátaxis와 GitLab의 서로 다른 유형 체계를 하나의 보편 표준으로 섞지 않는다.

Google의 오래된 문서 제거 권고를 근거로 미구현 요구·결정 이유·실행 입력을 지우지 않는다. 보존해야 할 내용은 이 저장소의 규칙과 실제 사용처로 판단한다. [Google의 정리 지침](https://google.github.io/styleguide/docguide/best_practices.html#delete-dead-documentation), [저장소 문서 사용 기준](../../AGENTS.md#문서-사용)

재배치 후 공유 Markdown 95개와 로컬 작업 메모의 링크·앵커·Git 원문 참조, 공용 fixture 검사, API 소비 테스트 2개·Backend fixture 테스트 8개·Android fixture 테스트 2개를 확인했다. 이는 문서 참조와 입력의 일관성 확인이며 제품 전체의 동작·배포·단말·부하 성능 검증을 대신하지 않는다. 별도 문서 사이트나 새 빌드 도구는 도입하지 않았다.
