# 국내 기술 기업의 Service 조회 메서드 이름 조사

조사일: 2026-09-30

## 질문과 범위

사용자 요청에 따라 `aside.exe` 브라우저 에이전트로 Service 클래스에 선언된 조회 메서드만 다시 조사했다. Java/Spring을 우선하고 Kotlin은 언어를 구분한다. Controller·Repository·Mapper·DAO·HTTP GET·엔티티 getter는 Service 동사의 근거에서 제외한다. 회사 전체 규칙, 공개 프로젝트 구현, 발표·블로그 예제를 구분하며 이 문서는 채택된 명명 규칙이 아니다.

## 요약

- 확인한 공개 자료만으로 “네카라쿠배당토직야는 조회를 특정 동사 하나로 통일한다”는 결론을 낼 수 없다.
- 직접 작성한 Service에도 `get…`와 `find…`가 쓰인다. `get…`가 null을 반환하는 구현과 `find…`가 예외를 던지는 구현이 모두 확인된다. 따라서 `get = 없으면 예외`, `find = Optional`을 업계 공통 계약으로 단정하면 안 된다. 아래 Pinpoint·우아한형제들 사례가 각각 반례다.
- `read…`가 일반 Service 조회의 공통 표준이라는 근거도 이번 확인 범위에서는 확보하지 못했다. 이는 사용하지 않는다는 뜻이 아니다.
- Aside 추가 조사로 LINE의 `RpServiceImpl.get`, 쿠팡의 `CityService.findAll`, 당근 공식 발표 예제의 `ForumQueryService.loadTopics`·`loadPosts` 선언을 확인했다. 기존 Repository 사례와 호출부만 보이는 사례는 아래 표에서 제외했다.

## 확인한 사례

| 회사·자료 범위 | 실제 메서드·계층 | 실제 동작·미조회 처리 | 출처 |
|---|---|---|---|
| 네이버 저작권 표기가 있는 공개 프로젝트 Pinpoint, `v2.5.3`, Java | `AgentInfoServiceImpl.getAgentInfo(String, long)`, Spring `@Service` | DAO 기반 조회를 호출하고 결과가 null이면 명시적으로 null 반환. 상태 조회·결과 조립도 하므로 단순 필드 getter가 아님 | [고정 버전 구현](https://github.com/pinpoint-apm/pinpoint/blob/v2.5.3/web/src/main/java/com/navercorp/pinpoint/web/service/AgentInfoServiceImpl.java#L311-L320) |
| 같은 Pinpoint 버전, Java | `UserServiceImpl.selectUserByUserId(String)`, Spring `@Service` | `UserDao.selectUserByUserId` 조회 후 decoder에 위임. 기본 decoder는 입력을 그대로 반환하므로 그 경로에서는 null도 그대로 전달. 커스텀 decoder의 계약까지 일반화하지 않음 | [Service](https://github.com/pinpoint-apm/pinpoint/blob/v2.5.3/web/src/main/java/com/navercorp/pinpoint/web/service/UserServiceImpl.java#L77-L82), [기본 decoder](https://github.com/pinpoint-apm/pinpoint/blob/v2.5.3/web/src/main/java/com/navercorp/pinpoint/web/util/DefaultUserInfoDecoder.java#L44-L47) |
| 카카오페이 정산플랫폼팀 공식 글의 설명 코드, Kotlin | `PartnerClientService.getPartnerBy(String)`, Service | 협력 HTTP client 응답이 2xx가 아니면 `IllegalArgumentException`. 성공 본문은 `!!`로 반환. 문서가 제시한 Service 코드·테스트의 계약이지 모든 카카오 계열사의 규칙이 아님 | [실무에서 적용하는 테스트 코드 작성 방법과 노하우 Part 2](https://tech.kakaopay.com/post/mock-test-code-part-2/) |
| LINE FIDO2 공식 공개 데모, Java | `RpServiceImpl.get(String rpId)`·`getAll()`, Spring `@Service`, `base.service` 패키지 | 단건은 RP 식별자로 조회하여 응답 객체로 변환하며 없으면 null 반환. getAll은 전체 조회 후 목록으로 변환 | [커밋 고정 Service](https://github.com/line/line-fido2-server/blob/1c5b7e0ee11eabc0dc44cf5a286ce126c87cae39/fido2-demo/base/src/main/java/com/linecorp/line/auth/fido/fido2/base/service/RpServiceImpl.java) |
| 쿠팡 공개 Spring Boot 데모, Java | `CityService.findAll()`, Spring `@Service`, `requery.demo.services` 패키지 | 전체 도시 조회 결과를 반환. Service 본문 자체에는 별도 미조회 분기 없음. `examples` 코드로 운영 코드·사내 표준과 구분 | [커밋 고정 Service](https://github.com/coupang/spring-data-requery/blob/36bc2d6f1d7a5e37af18aa1912533749bdbfc07a/examples/requery-spring-boot-demo/src/main/java/requery/demo/services/CityService.java) |
| 우아한형제들 정산시스템팀 공식 파일럿 회고, Java·Spring Boot | `OrderService.findById(long)`, Service; 내부 `findOrderById(long)` | Repository의 `findOrderByIdWithDetails` 결과에 `orElseThrow(EntityNotFoundException::new)`를 적용. Service의 `findById`도 미조회 시 예외가 되는 직접 작성 사례. 운영 시스템 전체 규칙이 아니라 파일럿 구현 | [정산지기를 향한 첫걸음](https://techblog.woowahan.com/2668/) |
| 당근 공식 SERVER 밋업 예제, Kotlin·Spring Boot | `ForumQueryService.loadTopics()`·`loadPosts(topicId: UUID)`, Spring `@Service`, 읽기 전용 트랜잭션 | 토픽·게시물·작성자를 조회하여 결과 조립. 토픽 목록이 비면 loadTopics는 빈 배열. 특정 토픽·작성자의 미존재 정책은 Service 본문만으로 미확인 | [커밋 고정 Service](https://github.com/arawn/kotlin-support-in-spring/blob/8ed9ff234ee49a059dae8d65b6357dec413f4f78/src/main/kotlin/com/github/forum/application/ForumQueryService.kt#L53-L73), [공식 발표](https://www.youtube.com/watch?v=RBQOlv0aRl4) |
| 토스증권 배너 기능 공식 글, Kotlin | `IntelligenceService.get(request: IntelligenceRequest)`, Spring `@Service` | 요청 지면에 맞는 handler로 조회 위임. handler가 없으면 IllegalArgumentException. handler 내부 데이터의 미존재 정책은 미확인. 실제 기능을 설명하는 축약 예시 | [공식 글](https://toss.tech/article/intelligence_banner) |

Pinpoint는 네이버에서 시작한 프로젝트이며 현재 저장소는 `pinpoint-apm/pinpoint`다. 네이버의 현재 내부 서비스 전체를 대표하지 않는다. 카카오페이 사례도 카카오 본사의 규칙으로 대체하지 않는다. 해당 PartnerClientService 선언에는 @Service가 보이지 않지만 글이 비즈니스 로직을 담당하는 Service 객체라고 명시한다. [Pinpoint 역사](https://pinpoint-apm.gitbook.io/pinpoint/want-a-quick-tour/history)

당근 예제는 개인 계정 저장소지만, 당근 팀 공식 영상 설명란이 해당 저장소를 밋업코드로 직접 연결한다. 따라서 공식 발표용 예제로 채택하되 운영 코드·사내 표준으로 분류하지 않는다. [공식 발표와 코드 연결](https://www.youtube.com/watch?v=RBQOlv0aRl4)

우아한형제들 글의 같은 OrderService에는 `search(OrderSearchRequest)` 선언도 있다. `findById`의 helper 예외 처리는 As-Is 본문에서 확인했고, To-Be에서는 helper 본문이 생략돼 있다. [공식 파일럿 회고](https://techblog.woowahan.com/2668/)

## 미확인 범위와 제외 기준

- 조사한 회사들의 명문화된 전사 Service 조회 동사 규칙은 확인하지 못했다. 확인된 선언도 회사 전체 사용 빈도의 통계 표본이 아니다.
- 카카오 본사·직방·야놀자는 이번 공개 탐색에서 채택할 Service 조회 선언을 확보하지 못했다. 카카오페이·쿠팡 데모·당근 발표 예제를 각각 본사 규칙·운영 코드로 확대 해석하지 않는다.
- 직방은 공식 Mockito·Spring/NestJS 글과 공개 저장소, 야놀자는 공식 기술블로그·그룹 블로그·공개 저장소를 탐색했다. 기술스택 소개나 코드 생성 getter는 Service 조회 사례에서 제외했다. [직방 블로그](https://medium.com/zigbang), [직방 공개 저장소](https://github.com/zigbang), [야놀자클라우드 블로그](https://medium.com/yanoljacloud-tech), [야놀자 공개 저장소](https://github.com/yanolja)
- 쿠팡 공개 API의 HTTP `GET` 명세는 서버 내부 Java 메서드 동사를 보여주지 않으므로 조회 메서드 명명 근거로 채택하지 않았다.
- 개인 블로그의 회사 경험담, 채용 준비·클론 프로젝트, 출처 회사가 확인되지 않는 GitHub 저장소는 제외했다. 개인 저장소라도 공식 발표에서 직접 지정한 예제는 연결 근거와 함께 별도 분류한다.
- “공식 블로그에 코드가 있다”와 “회사가 이 이름을 규칙으로 강제한다”는 다르다. 이번에 읽은 자료는 대부분 실제 사용례 또는 설명 예제이며 사내 명명 표준 문서가 아니다.
- 일부 공식 저장소 탐색 URL은 접근되지 않았다. 접근 실패를 메서드나 규칙의 부재로 해석하지 않았다.

## 수리맵에 적용할 때의 판단

아래는 조사 결과에서 도출한 제안이며 외부 회사 규칙의 인용이 아니다.

1. `get`을 단순 getter에만 쓸 수 있다고 제한하거나, Service 조회를 무조건 `read`로 바꾸어야 할 근거는 없다.
2. 한 클래스 안에서 조회 동사를 일관되게 고르는 것은 로컬 설계 선택으로 합의할 수 있다. 다만 연속성 검증 여부, 첫 페이지와 다음 페이지, 보존된 이력만 조회하는 차이는 동사보다 목적어·수식어에 드러내는 편이 중요하다.
3. 현재 후보인 `readContiguousHistoryPage`와 `readRetainedHistoryPage`의 차이를 유지하는 안은 가능하지만, `read`가 위 기업들의 표준이라서 추천하는 것은 아니다. `get`이나 `find` 계열을 선택하더라도 동일한 동작 구분과 실패 계약을 보존해야 한다.
4. 이름 변경은 실제 반환형·예외·SQL·페이지 경계를 바꾸는 작업과 분리한다.
5. 이번 SSE Service 조회에는 `get…`도 적합하다. 결과를 얻는 서비스 호출임을 클래스와 목적어가 드러내므로 DB 조회라는 이유만으로 `read`를 고집하지 않는다. Q25에서 사용자가 get 통일안을 선택했으며, 앞서 합의한 `readFirstPageAfter`·`readNextPage`도 `getFirstPageAfter`·`getNextPage`로 수정하기로 했다. 기존 MyBatis Mapper의 `find…`는 유지한다. 이는 회사 공통 규칙이 아닌 수리맵의 선택이며, 확정 이름과 구현 여부는 [이름 후보 목록](../refactoring/codebase-naming-candidates.md#마커알림--남은-확인)에서 관리한다.

## 확인 방법과 검증 한계

`aside.exe exec` 세션 `MqmTkF3vAghdxBNh`에서 공식 블로그 본문·Service 선언·구현·발표와 예제의 연결을 조사했다. 검색 결과 요약·호출부만으로 Service 동사를 확정하지 않았다. Repository·Controller와 Spring Data 파생 쿼리 키워드는 이번 비교 근거에서 제외했다. 원격 예제와 제품 테스트는 실행하지 않았다. 문서 검사와 제품 동작 검증을 구분한다.
