# 웹·앱의 공통 수정·삭제를 어디까지 나눌 것인가

- 조사일: 2026-09-09
- 질문: 웹과 앱이 같은 자원을 수정·삭제할 때, 기업들은 요청 진입점과 업무 처리·저장 로직을 어디까지 나누는가?
- 범위: 기업이 직접 공개한 구현 경험 4곳, Shopify의 공개 API, 공식 패턴 설명. 과거 경험담은 해당 글이 설명하는 당시 구조이며, 현재 전사 표준이나 업계 채택 비율을 뜻하지 않는다.
- 상태: 설계 판단을 위한 조사 기록. 수리맵의 코드 구조·URL 변경은 확정하지 않았다.

## 회사마다 나누는 범위가 다르다

웹·앱의 API를 나눈 사례와 공통 API를 사용하는 사례가 모두 있다. 특히 SoundCloud는 클라이언트별 API를 유지하면서 중복된 업무·인가 로직을 공통 서비스로 옮겼다. 따라서 **요청을 받는 곳을 구분하는 것과 같은 저장·검증 처리를 두 벌로 구현하는 것은 별개**다. [SoundCloud의 분리 후 개선](https://developers.soundcloud.com/blog/service-architecture-2/), [Slack의 공통 API](https://slack.engineering/slack-bug-bounty-three-years-later/)

| 기업·자료 시점 | 나눈 단위 | 공유한 처리 | 자료로 확인할 수 없는 것 |
| --- | --- | --- | --- |
| SoundCloud, 2021 | 모바일용·웹용 등 별도로 운영·배포하는 BFF API | 엔티티 조합과 인가를 Tracks·Playlists 서비스로 통합 | 동일 CRUD의 Java Controller·Service 클래스 배치. [분리 구조](https://developers.soundcloud.com/blog/service-architecture-1/), [공통 처리](https://developers.soundcloud.com/blog/service-architecture-2/) |
| Netflix, 2020 | 한 API 서비스에 있던 Android 전용 라우트를 별도 서비스로 이전 | 기존 `api`와 하위 서비스의 기능을 호출 | 수정·삭제 트랜잭션을 채널별 Java Service에 어떻게 배치했는지. [이전 경험](https://netflixtechblog.com/seamlessly-swapping-the-api-backend-of-the-netflix-android-app-3d4317155187) |
| Slack, 2017·2020 수정 | 웹·데스크톱·모바일이 공통 Slack API를 사용 | 팀 관리·소통 등의 핵심 API 기능 | 모든 URL·처리 함수까지 같은지, 별도 클라이언트 처리가 전혀 없는지. [API 구성 설명](https://slack.engineering/slack-bug-bounty-three-years-later/) |
| 우아한형제들 개발자의 경험, 2019 | 내부 API·외부 API·배치 등 실행 가능한 애플리케이션 모듈 | 하위 도메인 모듈의 규칙과 저장 기능 | 웹·앱에 대한 회사 전체의 Controller·Service 규칙. [멀티모듈 설계 경험](https://techblog.woowahan.com/2637/) |

## SoundCloud는 API를 나누되 업무·인가 로직의 중복을 줄였다

2021-07-29 글은 Android·iOS가 사용하는 Mobile API와 웹·위젯이 사용하는 Web API를 구분한다. 모바일과 웹의 응답 크기·조합 방식이 달랐고, 팀별 변경과 배포의 독립성이 분리 이유였다. 단일 애플리케이션 안의 패키지 구분이 아니라, 여러 BFF를 실제 운영·배포한 사례다. [Service Architecture — Part 1](https://developers.soundcloud.com/blog/service-architecture-1/)

하지만 BFF마다 트랙·플레이리스트 조합과 접근 허용 판단이 반복되면서 구현이 서로 달라지는 문제가 생겼다. 2021-08-20 후속 글에서는 이를 Tracks·Playlists의 공통 업무 서비스인 VAS로 옮기고, BFF는 내부 결과를 클라이언트용 응답으로 변환하게 했다고 설명한다. 플레이리스트에 트랙을 추가하는 쓰기에서도 해당 트랙의 접근 허용 판단은 Tracks 서비스에 요청한다. [Service Architecture — Part 2](https://developers.soundcloud.com/blog/service-architecture-2/)

수리맵에 적용할 수 있는 해석은 **웹·앱 진입점을 나눠도 공통 규칙까지 복제할 필요는 없다**는 것이다. 다만 VAS는 네트워크로 호출하는 별도 서비스다. 수리맵에도 같은 배포 구조나 새로운 계층 이름을 도입해야 한다는 근거는 아니다.

분리의 한계도 함께 봐야 한다. 첫 글은 특정 애플리케이션의 접근 허용 문제를 해결하려고 좁은 용도의 BFF를 계속 만드는 것을 경계한다. 따라서 권한 차이가 있다는 사실만으로 별도 백엔드 배포가 필요하다는 결론은 나오지 않는다. [BFF 증가의 비용과 접근 제어](https://developers.soundcloud.com/blog/service-architecture-1/)

## Netflix는 코드 구분과 별도 서비스 배포를 단계적으로 진행했다

2020-09-08 글은 Android·iOS·TV·웹의 클라이언트별 백엔드 구분을 설명한다. Android 팀의 라우트 코드는 처음부터 존재했지만, 당시에는 API 팀이 운영하는 하나의 서비스 안에 배포되어 있었다. 이후 Android 라우트를 독립 배포하는 Node.js 서비스로 옮겼고, 그 서비스는 기존 `api`를 호출해 응답을 조합했다. [Android API 이전 경험](https://netflixtechblog.com/seamlessly-swapping-the-api-backend-of-the-netflix-android-app-3d4317155187)

분리 목적은 클라이언트 팀의 요청 처리 제어권, 관측 가능성, 로컬 개발·배포 경험이었다. 별도 네트워크 구간이 생겨 지연과 부분 실패도 증가했다. 이 사례는 **클라이언트별 코드가 있다는 사실만으로 서버도 별도 배포해야 하는 것은 아님**을 보여준다. 다만 설명의 중심은 UI용 조회 데이터 조합이다. 동일 수정·삭제의 트랜잭션 구현 위치를 입증하는 자료로 확대해서는 안 된다. [이전의 이점과 비용](https://netflixtechblog.com/seamlessly-swapping-the-api-backend-of-the-netflix-android-app-3d4317155187)

## Slack은 서로 다른 클라이언트에서 공통 API를 사용했다

2017-03-15 게시·2020-06-26 수정된 보안팀의 공식 글은 웹·데스크톱·모바일 앱과 외부 연동이 모두 Slack API와 통신한다고 설명한다. 팀 관리와 소통 등의 핵심 기능이 이 API에 있고, 웹·데스크톱에는 각 환경에만 있는 기능도 있다고 구분한다. [Slack API와 클라이언트 구성](https://slack.engineering/slack-bug-bounty-three-years-later/)

여기서 확인되는 것은 **클라이언트가 다르더라도 공통 핵심 API를 사용하는 실제 사례가 있다**는 사실까지다. 모든 엔드포인트가 공통이거나 내부 Service가 하나라는 뜻은 아니다. 또한 API 공유를 선택한 의사결정 과정은 이 글에 없다. 수리맵의 웹·앱을 반드시 합쳐야 한다는 근거로 사용할 수 없다.

## 우아한형제들 사례는 애플리케이션별 처리와 도메인 공유를 구분한다

2019-07-01 권용근 개발자의 글은 내부 API·외부 API·배치를 따로 개발하면서 같은 도메인 규칙을 복사해야 했던 경험을 다룬다. 이후 실행 애플리케이션은 구분하면서 중심 도메인을 하위 모듈로 공유했다. 반대로 애플리케이션별 기능과 설정까지 거대한 `common` 모듈에 모았을 때는 변경 영향과 의존성이 커졌다고 설명한다. [멀티모듈 설계 경험](https://techblog.woowahan.com/2637/)

저자가 제시한 구조에서 실행 애플리케이션은 하위 기능을 조합하고, 도메인 모듈은 저장·규칙을 담당한다. 필요한 경우 Domain Service에 트랜잭션 단위·검증·이벤트 처리를 두었지만, 업무가 단순하면 이 계층이 없을 수도 있다고 밝힌다. [도메인·애플리케이션 모듈의 책임](https://techblog.woowahan.com/2637/)

수리맵과 가까운 점은 **사용 목적별 처리를 구분하면서 같은 도메인의 규칙·저장 구현은 공유할 수 있다**는 것이다. 다만 이는 독립 실행 가능한 애플리케이션과 Gradle 모듈에 관한 당시 개발자의 경험이다. 웹·앱 분리의 전사 표준도 아니고, 수리맵에 멀티모듈·Domain Service를 반드시 추가하라는 규칙도 아니다.

## Shopify는 기기보다 사용 목적이 다른 API를 구분한다

조사일의 2026-07 API 문서에서 Storefront API는 구매자가 상품을 보고 장바구니를 변경하는 기능을 웹·앱 등에 제공한다. 반면 Admin API는 Shopify 관리 기능을 확장하는 앱·연동용이며 별도 접근 권한을 요구한다. 같은 플랫폼의 데이터라도 구매 경험과 관리 업무는 구분하고, 구매 경험 안에서는 웹·앱에 같은 API를 제공하는 사례다. [Storefront API](https://shopify.dev/docs/api/storefront/2026-07), [Admin API](https://shopify.dev/docs/api/admin-graphql/2026-07)

이는 공개 API의 대상·기능 구분을 확인한 것이며, 내부 DB·저장 메서드가 어떻게 공유되는지는 이 문서로 알 수 없다. 수리맵도 단말 종류뿐 아니라 현장 기록과 상황판 업무가 같은지를 살펴야 한다는 비교 근거다.

## 공식 패턴 설명도 무조건 분리를 권하지 않는다

- Microsoft는 클라이언트별 요구·변경 일정에 맞추기 위해 BFF를 나누는 방법을 설명하지만, 동일하거나 비슷한 요청만 하는 경우에는 적합하지 않을 수 있다고 명시한다. 추가 배포·운영과 네트워크 지연도 비용으로 든다. 이는 별도 BFF 서비스에 관한 지침이지, 같은 Spring 애플리케이션의 Controller 두 개를 금지하는 규칙이 아니다. [Azure BFF 패턴](https://learn.microsoft.com/en-us/azure/architecture/patterns/backends-for-frontends)
- Sam Newman은 사용자 경험과 팀의 소유 범위를 분리 기준으로 설명한다. 또한 같은 프로세스 안의 중복을 공통화하는 일과 별도 서비스끼리 공유 코드를 통해 결합되는 일을 구분한다. 수리맵에서 메서드를 공유하는 비용을 별도 마이크로서비스 도입 비용과 동일하게 취급하면 안 된다. [BFF 원문, 2015-11-18](https://samnewman.io/patterns/architectural/bff/)

## 수리맵에서 확인한 사실과 적용 제안

조사 당시 코드 확인 기준은 `ddd2c1f7`이다. 이후 변경된 구조는 아래 표가 아니라 현재 코드에서 확인한다. 아래 권한 구분은 기존 구현을 읽은 결과이며, 그 정책 자체가 올바르다고 새로 확정한 것은 아니다.

| 확인 대상 | 당시 구현 |
| --- | --- |
| 공개 요청 | APP·WEB 모두 같은 `PATCH /api/markers/{markerId}`, `DELETE /api/markers/{markerId}`와 입력 형식을 사용한다. [Controller](https://github.com/sonic8-8/suri-map/blob/ddd2c1f70d44c375334c46f90593a2642aa173eb/backend/src/main/java/com/surimap/api/controller/marker/MarkerController.java) |
| 앱의 변경 대상 | 본인 계정이 생성한 `APP` 출처 마커만 허용한다. [권한 검사](https://github.com/sonic8-8/suri-map/blob/ddd2c1f70d44c375334c46f90593a2642aa173eb/backend/src/main/java/com/surimap/marker/adapter/RuntimeMarkerWriteGuardAdapter.java) |
| 웹의 변경 대상 | `MOCK_SEED` 또는 `SYSTEM` 출처 마커만 허용한다. 앱보다 모든 권한이 넓은 관리자라고 단정할 수 없다. [권한 검사](https://github.com/sonic8-8/suri-map/blob/ddd2c1f70d44c375334c46f90593a2642aa173eb/backend/src/main/java/com/surimap/marker/adapter/RuntimeMarkerWriteGuardAdapter.java) |
| 공통 처리 | 버전 검사, 수정·논리 삭제, 이벤트 발행, 멱등 응답 처리를 같은 Service가 수행한다. [수정·삭제 Service](https://github.com/sonic8-8/suri-map/blob/ddd2c1f70d44c375334c46f90593a2642aa173eb/backend/src/main/java/com/surimap/marker/service/MarkerUpdateDeleteService.java) |
| 저장소 규칙 | 채널별 Controller·Service를 구분하고, 도메인 모델·정책·Mapper는 공유한다. [Backend 규칙](https://github.com/sonic8-8/suri-map/blob/ddd2c1f70d44c375334c46f90593a2642aa173eb/backend/AGENTS.md) |

**적용 제안은 웹·앱의 진입점과 업무 흐름은 구분하되, 공통 마커 규칙과 저장 구현은 공유하는 것이다.** 별도 서버나 Gradle 모듈을 추가하지 않고, 기존 `api`·`app`·`domain/marker` 배치 원칙 안에서 검토한다. 기업 사례를 그대로 복제하는 것이 아니라, 현재 업무 차이와 합의한 구조를 함께 고려한 판단이다.

직전의 “공통 수정·삭제이므로 `api`의 Service에 모두 합치자”는 제안은 저장 처리의 공통성과 호출자의 업무 구분을 한 결정으로 묶었다. 위임 코드가 생긴다는 이유만으로 분리를 불필요하다고 판단할 수는 없다. 반대로 아무 역할 없이 같은 인자를 전달하는 계층을 반복해서 만들 필요도 없다. 각 채널의 Service가 어떤 업무를 책임지고, 공유할 처리가 어디까지인지 실제 호출 흐름으로 정해야 한다.

다음 설계에서 확인할 항목은 세 가지다.

1. 앱·웹의 현재 변경 대상과 권한 정책이 의도한 동작인지 확인한다.
2. 같은 저장·이벤트·멱등성 처리를 복제하지 않으면서 채널별 Service가 맡을 책임과 트랜잭션 범위를 정한다. 새 공통 클래스 이름이나 추가 계층은 아직 확정하지 않는다.
3. 기존 URL·오류 응답을 유지할 요청 매핑을 정한다. Spring은 URL 외에 헤더 등의 조건으로도 매핑을 구분할 수 있지만, 조건 추가만으로 기존 오류 동작까지 보존되는 것은 아니다. 인증된 세션의 채널 검증과 누락·불일치 요청에 대한 테스트가 필요하다. [Spring MVC 6.2 요청 매핑](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-controller/ann-requestmapping.html), [당시 세션 인증 해석](https://github.com/sonic8-8/suri-map/blob/ddd2c1f70d44c375334c46f90593a2642aa173eb/backend/src/main/java/com/surimap/marker/photo/adapter/SecurityContextSuriMapAuthenticationResolver.java)

이번 작업은 조사 문서만 추가했다. 코드·API 계약·AGENTS.md·TODO와 커밋은 변경하지 않았다.

## 이 조사만으로 확정할 수 없는 것

- BFF는 이 사례들에서 클라이언트용 API를 제공·조합하는 서버 측 부분을 가리킨다. 수리맵의 `Controller`나 Spring `Service` 클래스와 일대일로 대응하지 않는다.
- 코드 패키지 분리, Gradle 모듈 분리, URL 분리, 별도 서비스 배포는 서로 다른 결정이다. 기업이 BFF를 운영한다는 사실만으로 네 가지를 모두 선택할 수는 없다.
- 동일한 요청 JSON을 받는다는 사실만으로 같은 업무라고 단정할 수 없다. 반대로 허용 대상이나 권한이 다르다는 이유만으로 트랜잭션·멱등성·저장·이벤트 구현을 전부 복제해야 한다고도 결론 낼 수 없다. 이는 회사 사례를 수리맵에 적용할 때 확인해야 할 설계 질문이다.
- 이번 자료에는 수리맵과 동일한 마커 수정·삭제를 한 Spring Boot 애플리케이션의 웹·앱 Controller와 Service에 어떻게 배치했는지 보여주는 공개 코드가 없다. 클래스별 배치안은 로컬 호출 흐름과 합의한 경계를 바탕으로 별도로 결정해야 한다.
