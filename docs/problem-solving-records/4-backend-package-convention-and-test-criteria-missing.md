# [BE] 백엔드 패키지 컨벤션 미준수 및 테스트 코드 작성 기준 부재

# 본문

## 문제 배경

수리맵은 SSAFY에서 약 8주 동안 진행했던 프로젝트다. 이 중 4주에서 5주는 현직 경찰관 인터뷰와 기획에 썼고, 실제 구현은 약 2주에서 3주 동안 진행했다.

짧은 구현 기간 안에 MVP를 완성해야 했기 때문에 코드 품질보다 속도를 우선했다. 반복 구현을 빠르게 만드는 하네스 엔지니어링을 적극적으로 사용했지만, 그 과정에서 패키지 컨벤션을 지키지 않은 코드와 기준 없이 추가된 테스트가 많이 남았다.

수색 경로(path) 코드를 리팩토링하면서 이 문제가 먼저 드러났다. 요청을 받는 controller, 업무 흐름을 처리하는 service, 핵심 규칙을 담는 domain, DB에 접근하는 persistence 성격의 코드가 한 패키지 안에 섞여 있었다.

테스트도 기준이 분명하지 않다. 어떤 테스트는 작은 단위만 확인하고, 어떤 테스트는 SpringBootTest로 실제 애플리케이션에 가깝게 확인하며, 어떤 테스트는 WebMvcTest로 HTTP 요청과 응답만 확인한다. 특히 service 테스트에서 가짜 mapper로 DB 접근을 대신하는 구조가 있어, 실제 MyBatis XML, PostgreSQL/PostGIS, 트랜잭션이 함께 동작하는지는 확인하기 어렵다.

이 상태가 남아 있으면 리팩토링할 때 어떤 테스트를 믿어야 하는지 판단하기 어렵다. 이번 이슈는 MVP 이후 백엔드 코드를 계속 유지보수하기 위해 패키지 구조와 테스트 기준을 다시 세우는 작업이다.

## 원인 분석과 선택지

문제의 원인은 TDD로 테스트 코드를 추가하자는 기준만 있었고, 레이어별 테스트 기준은 정하지 않았다는 점이다. 테스트를 먼저 작성하더라도 controller, service, persistence를 같은 방식으로 확인하면 테스트의 목적이 흐려진다.

나는 [Classicist](https://martinfowler.com/articles/mocksArentStubs.html#ClassicalAndMockistTesting)에 가까운 테스트를 더 신뢰한다. 실제 객체와 실제 실행 흐름을 사용해야 코드가 실제로 동작하는지 확인할 수 있다고 보기 때문이다. 반대로 [Mockist](https://martinfowler.com/articles/mocksArentStubs.html#ClassicalAndMockistTesting) 관점처럼 의존성을 적극적으로 대체하면 빠르게 테스트할 수는 있지만, 실제 연결 지점이 함께 동작하는지는 놓칠 수 있다.

다만 모든 레이어에서 mock을 쓰지 않겠다는 뜻은 아니다. controller 테스트에서는 service를 mocking하는 편이 맞다. controller 테스트의 목적은 HTTP 요청, 응답, header, validation, status code, error body가 맞는지 확인하는 것이기 때문이다.

반대로 service 테스트에서는 mapper를 가짜 객체로 바꾸는 방식이 맞지 않다고 본다. 수리맵의 service는 단순 계산만 하지 않는다. mapper 호출, DB 저장, 트랜잭션, 이벤트 발행 흐름을 함께 조합한다. 이 부분을 가짜 객체로 바꾸면 실제 MyBatis XML, PostgreSQL/PostGIS, 트랜잭션이 함께 동작하는지 확인하기 어렵다.

그래서 이번 작업에서는 레이어별 테스트 기준을 명확히 나눈다. domain은 단위 테스트로 확인한다. persistence와 service는 SpringBootTest로 실제 DB와 MyBatis까지 함께 확인한다. controller는 WebMvcTest로 HTTP 계층만 확인하고, 이 범위 안에서는 service mocking을 허용한다.
