# 국내 기술 기업의 Java 테스트 클래스명 조사

- 관찰일: 2026-09-15
- 질문: 네이버·카카오·LINE·쿠팡·우아한형제들이 Java/Spring 테스트 파일명에 `ScenarioTest`, `E2ETest`를 실제 사용하는가? 이들 회사의 사례가 Suri-Map에서 해당 접미사를 의무화할 근거인가?
- 범위: 공식 기술 블로그와 회사·교육 조직의 공개 저장소. 비공개 서비스 코드와 전사 내부 규칙은 조사 대상에 접근할 수 없었다.

## 결론

이번에 확인한 공개 자료에서는 다섯 회사의 실제 Java 파일명으로 `*ScenarioTest.java`, `*E2ETest.java`를 검증하지 못했다. **사용하지 않는다는 뜻은 아니다.** 검색과 직접 열람으로 확보한 표본의 한계이며, 공개 저장소 전체를 전수 조사한 결과도 아니다.

반면 실제 Java 코드에서 `UserControllerTest`, `HttpServerTest`, `ApplicationTest`를 확인했고, 우아한형제들 공식 글에는 `UnitCreateV2DocumentationTests.java`라는 파일명과 클래스 선언이 나온다. 아래 자료는 해당 이름의 존재를 보여 줄 뿐, 어느 접미사가 업계 다수이거나 전사 표준이라는 증거는 아니다.

따라서 이번 회사 사례 조사만으로 Suri-Map에 `ScenarioTest`나 `E2ETest` 접미사를 의무화할 근거는 확보하지 못했다. `ControllerTest`·`ServiceTest`·`MapperTest` 같은 이름을 사용하는 것과 필요한 통합·사용자 흐름 검증을 갖추는 것은 별도로 판단해야 한다.

## 회사별 확인 결과

| 회사 | 확인한 공개 자료 | 실제 Java 접미사에 관해 말할 수 있는 범위 |
| --- | --- | --- |
| 네이버 | NAVER 기원 공개 OSS Pinpoint의 `UserControllerTest.java` 원문. 예전 [`naver/pinpoint`](https://github.com/naver/pinpoint)는 현재 `pinpoint-apm/pinpoint`로 연결되며, 파일에 NAVER 저작권 표기가 있다. | 아래 코드에서 `ControllerTest`를 확인했다. `ScenarioTest`·`E2ETest`는 이번 표본에서 미확인. 현재 네이버의 비공개 서비스나 전사 규칙으로 일반화하지 않는다. |
| LINE | [`line/armeria`](https://github.com/line/armeria)의 `HttpServerTest.java` 원문과 LINE 저작권 표기. | `HttpServerTest`를 확인했다. Java/Armeria 테스트이며, 이 파일은 Spring Boot 테스트 사례가 아니다. `ScenarioTest`·`E2ETest`는 이번 표본에서 미확인. |
| 우아한형제들 | 공식 Spring REST Docs 글의 Java 파일명·클래스 선언, 별도의 우아한테크코스 교육 저장소 코드. | `DocumentationTests`, `ApplicationTest`를 확인했다. 공식 글의 예시와 교육 코드를 배민 서비스 전체의 명명 규칙으로 일반화하지 않는다. `ScenarioTest`·`E2ETest`는 이번 표본에서 미확인. |
| 카카오 | [공식 GitHub 조직](https://github.com/kakao)과 공식 글 검색. [브런치개발파트 소개](https://tech.kakao.com/posts/549)는 Java/Spring 사용과 테스트 코드 운영을 설명하고, [Angular E2E 경험기](https://tech.kakao.com/posts/492)는 프런트엔드 사례다. | 이번 조사에서 해당 접미사를 가진 Java 테스트 파일·클래스 선언을 확보하지 못했다. 테스트를 한다는 설명이나 Angular E2E 사례를 Java 파일명 증거로 세지 않는다. |
| 쿠팡 | [공식 GitHub 조직](https://github.com/coupang), 공개 Spring 라이브러리 [`spring-data-requery`](https://github.com/coupang/spring-data-requery)의 저장소 소개, [모바일 CI/CD 개선 글](https://medium.com/coupang-engineering/improving-the-ci-cd-pipeline-for-mobile-app-development-80912546a4fd). | Java/Spring OSS의 존재와 모바일 통합 테스트 설명은 확인했지만, 해당 접미사를 가진 Java 테스트 원문은 확보하지 못했다. 저장소 소개나 배포 과정 설명을 파일명 증거로 세지 않는다. |

## 실제 이름을 직접 확인한 근거

### 네이버 기원 OSS: Pinpoint

- 경로: `web/src/test/java/com/navercorp/pinpoint/web/controller/UserControllerTest.java`
- 원문: [UserControllerTest.java](https://raw.githubusercontent.com/pinpoint-apm/pinpoint/master/web/src/test/java/com/navercorp/pinpoint/web/controller/UserControllerTest.java)
- 검증: 파일명과 `public class UserControllerTest` 선언을 대조했다. Spring XML 컨텍스트, `SpringExtension`, `MockMvc`를 사용하며 한 테스트 메서드에서 사용자 생성·조회·삭제 요청을 연결한다. 파일명에 `ScenarioTest`가 없어도 여러 동작을 연결하는 테스트를 표현한 예다.
- 한계: 관찰한 코드에는 클래스 수준의 `@Disabled`가 있다. 이름과 구현의 존재만 확인했으며, 현재 자동 실행된다는 근거가 아니다. `@SpringBootTest` 예시도 아니다.

### LINE OSS: Armeria

- 경로: `core/src/test/java/com/linecorp/armeria/server/HttpServerTest.java`
- 원문: [HttpServerTest.java](https://raw.githubusercontent.com/line/armeria/main/core/src/test/java/com/linecorp/armeria/server/HttpServerTest.java)
- 검증: `class HttpServerTest` 선언과 JUnit 코드를 확인했다. `ServerExtension`으로 HTTP/HTTPS 테스트 서버와 여러 서비스 경로를 구성한다.
- 한계: Java 서버 프레임워크 OSS의 사례다. LINE의 Spring Boot 서비스 명명 관행이나 회사 전체의 E2E 분류를 보여 주는 자료가 아니다.

### 우아한형제들 공식 글: Spring REST Docs

- 원문: [Spring REST Docs에 날개를… (feat: Popup)](https://techblog.woowahan.com/2678/).
- 확인한 이름: 글의 소제목에 `UnitCreateV2DocumentationTests.java`가 나오며, Java 코드 블록의 클래스 선언도 일치한다. `@WebMvcTest`와 REST Docs를 사용하는 예다.
- 한계: 블로그 코드 발췌이고 저장소 내 전체 경로는 공개된 본문에서 확인하지 못했다. 함께 등장하는 `ApiDocumentationTest`는 추상 지원 클래스이므로 독립 실행 테스트 사례로 세지 않는다.

### 우아한테크코스 교육 저장소: 키친포스

- 저장소·경로: [`woowacourse/jwp-refactoring`](https://github.com/woowacourse/jwp-refactoring)의 `src/test/java/kitchenpos/ApplicationTest.java`.
- 원문: [ApplicationTest.java](https://raw.githubusercontent.com/woowacourse/jwp-refactoring/master/src/test/java/kitchenpos/ApplicationTest.java)
- 검증: `class ApplicationTest`, `@SpringBootTest`, `contextLoads()` 테스트를 직접 확인했다.
- 한계: 교육용 기본 컨텍스트 로딩 테스트다. 업무 시나리오나 E2E 검증 사례가 아니며, 배민 프로덕션 코드의 관행으로 세지 않는다.

## 개념 설명과 Java 파일명 증거는 다르다

| 자료 | 확인한 내용 | 잘못 도출하면 안 되는 결론 |
| --- | --- | --- |
| 우아한형제들 [리뷰프로덕트팀 신입 개발자의 파일럿 프로젝트](https://techblog.woowahan.com/10600/) | 이 파일럿에서는 Acceptance Test를 API 접점의 E2E 검증, Service Test를 통합 검증, Controller Test를 MockMvc 기반 슬라이스 검증으로 설명한다. | E2E를 한다고 해서 파일명이 `*E2ETest.java`라는 뜻이 아니다. 여기 나온 테스트 전략은 파일럿 사례이지 전사 규칙이 아니다. |
| 우아한형제들 [서버사이드 테스트 파랑새를 찾아서](https://techblog.woowahan.com/14874/) | **Kotlin/Spring/Kotest** 글이다. `GiftcardExpireServiceTest` 안에 `scenario(...)`를 작성하며, 같은 클래스명에 `@SpringBootTest`를 붙인 예도 설명한다. | `scenario`라는 테스트 구성과 `ScenarioTest`라는 클래스 접미사는 다르다. Kotlin 클래스 선언을 실제 Java 파일명 근거로 바꿔 인용하면 안 된다. |
| 네이버 D2 [웹 E2E 테스트 생성·수행 소개](https://d2.naver.com/helloworld/4003712) | 브라우저·JavaScript 오류와 웹 E2E 자동화 발표다. | 네이버 Java/Spring 테스트 파일에 `E2ETest`를 사용한다는 증거가 아니다. |
| LINE 계열 LY 기술 블로그 [Flava API Gateway 검증 전략](https://techblog.lycorp.co.jp/ko/techverse2026-223) | 실제 인증·게이트웨이·테넌트 격리를 포함한 E2E 검증을 설명한다. 컨트롤 플레인은 **Go**로 작성했다고 명시한다. | Java 테스트 접미사 사용 사례가 아니다. |

## 확인 방법과 한계

- 공식 도메인·조직으로 범위를 좁혀 `ScenarioTest`, `E2ETest`, Spring 테스트 관련 검색을 하고, 확보한 Java 원문에서는 파일명·클래스 선언·테스트 구성을 대조했다. 검색 결과가 없는 경우를 미사용의 증명으로 취급하지 않았다.
- GitHub의 일부 코드 목록·API 접근과 셸 네트워크 요청에 실패했다. 재귀 트리·전체 이력·모든 브랜치를 조사하지 못했으므로 접미사별 개수나 사용 비율을 제시하지 않는다.
- 코드 링크는 관찰 당시 열람한 `main` 또는 `master` 원문이다. 고정 커밋 SHA를 확보하지 못했으므로 이후 내용이 바뀔 수 있다. 테스트를 다운로드해 실행하거나 실행 이력을 검증하지 않았다.
- 회사가 보유한 외부 프로젝트의 fork, 개인 블로그, 교육생 개인 프로젝트를 해당 회사 내부 관행으로 집계하지 않았다. OSS·교육·공식 글의 예시·개별 팀 경험은 각각 다른 성격의 증거다.

## Suri-Map에 대한 해석

접미사는 이름이고, 단위·통합·E2E는 실제로 무엇을 연결해 검증하는지의 문제다. `ControllerTest`도 설정과 대역 사용에 따라 검증 범위가 달라질 수 있으며, `E2ETest`라는 이름만 붙인다고 빠진 연결이 검증되지는 않는다.

이번 자료는 특정 접미사를 추가하거나 기존 테스트를 새 분류로 확정하는 결정문이 아니다. 기존 이름으로 대상이 명확하다면 회사 이름을 근거로 접미사를 늘릴 필요는 없고, 필요한 검증 범위가 실제 테스트에 포함됐는지는 별도로 확인해야 한다.

## 저장소 규칙이 추가된 이력

`backend/AGENTS.md`의 `ScenarioTest`·`E2ETest` 규칙은 2026-07-11 커밋 `ad434c2cbad3bb2e05c0ea30c5847a9d503931c3`(`wip: 수색 경로 리팩토링 테스트와 작성 기준 보강`)에서 추가됐다. 이전에는 Domain·Mapper·Service·Controller 네 종류를 기본으로 하고, 별도 계약·하네스가 있을 때만 ContractTest·HarnessRunner를 허용했다.

해당 커밋은 테스트 이름 기준을 반영했다고 기록하지만, 사용자가 Scenario/E2E 분류를 선택한 이유나 외부 참고 자료는 남기지 않았다. 추가 시점은 확인할 수 있으나 사용자 합의나 작성 의도까지 입증할 수는 없다.

조사 후 사용자 승인으로 [Backend 테스트 명명 기준](../../backend/AGENTS.md#테스트-기준)을 정리했다(2026-09-15). 기존 테스트 클래스의 이름 변경은 후속 작업으로 남겼다.
