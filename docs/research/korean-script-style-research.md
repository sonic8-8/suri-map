# k6 스크립트의 한글 사용 범위

## 결론

k6 스크립트의 한글 주석과 문자열은 JavaScript 문법상 사용할 수 있다. 다만 이 저장소에서는 실행·검색·메트릭 식별에 쓰이는 이름은 영어 ASCII로 유지하고, 한글은 설명용 주석에 제한하는 편이 가장 근거가 강하다.

- 권장: 한글 주석
- 문법상 가능하지만 기본값은 영어: 일반 문자열, `check` 이름, `group` 이름
- 문법상 가능하지만 사용하지 않음: 한글 식별자
- k6 제약상 사용 불가: 한글 사용자 정의 메트릭 이름
- 사용하지 않음: 한글 HTTP 헤더 이름, 별도 인코딩 규칙이 없는 한글 헤더 값
- 이식성을 위해 영어 ASCII 유지: 파일명, CLI 인자에 넣는 이름, 환경 변수 이름

## 경계별 판단

| 위치 | 기술 판단 | 이 저장소의 기준 |
|---|---|---|
| 주석 | [ECMAScript 단일 행 주석](https://tc39.es/ecma262/2025/multipage/ecmascript-language-lexical-grammar.html#sec-comments)은 줄바꿈을 제외한 모든 Unicode 코드 포인트를 담을 수 있다. | 도메인 맥락이나 `왜`를 설명할 때 한글 허용 |
| 일반 문자열 | [문자열 리터럴](https://tc39.es/ecma262/2025/multipage/ecmascript-language-lexical-grammar.html#sec-literals-string-literals)은 Unicode 코드 포인트를 담을 수 있다. | 로그·오류 설명처럼 사람만 읽는 값은 한글 허용 가능 |
| `check` / `group` 이름 | k6에서 각각 [`check`의 검사 이름](https://grafana.com/docs/k6/latest/javascript-api/k6/check/)과 [`group`의 문자열 `name`](https://grafana.com/docs/k6/latest/javascript-api/k6/group/)으로 받으므로 한글 문자열 자체는 유효하다. 두 이름은 [시스템 태그 값](https://grafana.com/docs/k6/latest/using-k6/tags-and-groups/)이 된다. | 기본은 영어로 유지. 한글이 필요하면 CLI, Prometheus, Grafana까지 한 번의 스모크 테스트로 출력 확인 |
| 식별자 | [ECMAScript 식별자](https://tc39.es/ecma262/2025/multipage/ecmascript-language-lexical-grammar.html#sec-names-and-keywords)는 Unicode `ID_Start`/`ID_Continue`를 허용하고, [Unicode 속성 파일](https://www.unicode.org/Public/UCD/latest/ucd/DerivedCoreProperties.txt)에서 완성형 한글 `AC00..D7A3`은 두 속성에 포함된다. | 변수·함수·시나리오·메트릭·태그 키는 영어 ASCII 사용 |
| 사용자 정의 메트릭 이름 | [k6 사용자 정의 메트릭 이름](https://grafana.com/docs/k6/latest/using-k6/metrics/#create-custom-metrics)은 ASCII 영문자·숫자·밑줄만 허용하며 영문자나 밑줄로 시작해야 한다. | 한글 사용 불가 |
| HTTP 헤더 이름 | [HTTP field name](https://www.rfc-editor.org/rfc/rfc9110.html#section-5.1)은 ASCII 문자 집합으로 구성된 [`token`](https://www.rfc-editor.org/rfc/rfc9110.html#section-5.6.2)이어야 한다. | 한글 금지 |
| HTTP 헤더 값 | [HTTP field value](https://www.rfc-editor.org/rfc/rfc9110.html#section-5.5)는 보통 US-ASCII 범위를 쓰며 더 넓은 문자가 필요하면 해당 필드가 정의한 인코딩을 사용한다. | 필드별 규격이 없다면 한글 원문을 넣지 않음 |
| 파일명 / CLI / 환경 변수 | ECMAScript는 파일 시스템·셸·환경 변수 전달 인코딩을 규정하지 않는다. [k6 환경 변수 문서](https://grafana.com/docs/k6/latest/using-k6/environment-variables/)도 `script.js`, `-e NAME=value`, `__ENV.NAME` 같은 ASCII 예만 제시한다. | 파일명과 환경 변수 이름은 영어 ASCII. 한글 환경 변수 값이 꼭 필요하면 실제 컨테이너·셸에서 별도 검증 |

k6는 [내장 JavaScript 런타임](https://grafana.com/docs/k6/latest/using-k6/javascript-typescript-compatibility-mode/)을 사용한다. 현재 구현은 읽은 바이트를 [Go 문자열로 파서에 전달](https://github.com/grafana/k6/blob/87a096e58bfd0af585315ab6b0b0433b2b2a2233/js/modules/resolution.go#L108-L124)하고, Sobek은 [UTF-8로 해독하며 잘못된 입력을 거부](https://github.com/grafana/sobek/blob/7781506a890f62def3f4853c015cdab8df37dfb4/parser/lexer.go#L534-L549)한다. 따라서 한글 소스는 UTF-8과 NFC로 저장한다. [ECMAScript source text 규칙](https://tc39.es/ecma262/2025/multipage/ecmascript-language-source-code.html#sec-source-text)은 구현에 정규화를 요구하지 않으므로, 화면상 같아 보이는 완성형 한글과 분해된 자모가 식별자나 문자열 비교에서 서로 다를 수 있다.

## 관행 표본

2026-08-30에 SK텔레콤 DEVOCEAN의 연속된 k6 튜토리얼 시리즈 01~07 전체를 표본으로 정했다. 회사 소유 도메인에 있고 복사 가능한 k6 코드를 포함한 문서만 대상으로, 7편의 렌더링된 `<pre><code>` 블록 77개를 모두 확인했다.

- 표본 7편: [01 HTTP 요청 및 메트릭](https://devocean.sk.com/blog/techBoardDetail.do?ID=164303), [02 Check와 Threshold](https://devocean.sk.com/blog/techBoardDetail.do?ID=164306), [03 Options와 구조](https://devocean.sk.com/blog/techBoardDetail.do?ID=164310), [04 Tag와 Group](https://devocean.sk.com/blog/techBoardDetail.do?ID=164347), [05 Shared/Per-VU iterations](https://devocean.sk.com/blog/techBoardDetail.do?ID=164358), [06 Constant/Ramping VUs](https://devocean.sk.com/blog/techBoardDetail.do?ID=164374), [07 Arrival rate](https://devocean.sk.com/blog/techBoardDetail.do?ID=164527)
- 77개 중 15개 코드 블록에 한글이 있었고, 발견된 한글은 모두 `//` 주석이었다.
- 한글 문자열·`check`/`group` 이름·식별자·헤더·파일명·CLI·환경 변수 이름은 0건이었다.
- 이 저장소의 `infra/k6` JavaScript/CJS 3개 파일도 한글이 0건이었다.

이 표본은 한 회사 기술 블로그의 튜토리얼 예시이며 운영 코드 저장소가 아니다. 따라서 “한국 기업 전체의 관행”이나 한글 `check`/`group` 이름의 실제 출력 호환성을 증명하지 않는다. 관찰 결과는 이 저장소의 영어 실행 어휘를 유지할 근거로만 사용한다.

## 적용 예시

```javascript
// 각 반복은 한 경찰폰의 경로 배치를 순서대로 전송한다.
group('send search path batch', () => {
  check(response, {
    'status is 200': (res) => res.status === 200,
  });
});
```

한글 설명은 가까이 두되, 대시보드와 임계값에서 재사용될 이름은 안정적인 영어 키로 남긴다.
