# 국내 Spring 서버의 도메인 계층 DTO 배치 조사

- 조사일: 2026-09-10
- 질문: NAVER·카카오·LINE·쿠팡·우아한형제들 같은 회사는 도메인 계층에 DTO를 두는가?
- 범위: 공식 기술 블로그와 회사 소유 OSS. 특정 팀의 운영 경험, 설명용 예제, 공개 소스를 구분한다.
- 상태: 조사 결과이며, 패키지 변경이나 새로운 설계 규칙을 확정한 문서가 아니다.

## 결론

**“도메인 계층에는 DTO를 절대로 두지 않는다”와 “국내 기업은 도메인에 DTO를 둔다” 중 하나로 일반화할 수 없다.** 공개 사례에서 확인한 것은 데이터의 용도와 경계에 따른 구분이다.

- LINE의 설명용 예제는 HTTP DTO를 `interfaces`, 서비스 입력 객체를 `application`, 업무 객체를 `domain`에 둔다. [공식 설명](https://engineering.linecorp.com/ko/blog/port-and-adapter-architecture/)
- 카카오페이 여신코어는 도메인 객체 내부에 명령용 `data class`를 정의해 입력을 받는다. HTTP DTO나 SQL 조회 결과를 도메인에 넣은 사례와는 다르다. [공식 구축기](https://tech.kakaopay.com/post/backend-domain-driven-design/)
- 우아한형제들 WMS는 도메인 모듈 안의 코어 서비스가 DTO로 데이터를 주고받도록 정리했다. 다만 DTO의 정확한 패키지는 공개하지 않았다. [공식 운영 경험](https://techblog.woowahan.com/22151/)

**도메인이 소유하는 입력·출력 객체가 존재할 수 있다는 사실은, 특정 SQL의 결과를 묶은 DTO를 업무 도메인 계층에 두어야 한다는 근거가 아니다.** 이것은 위 사례를 비교한 판단이다.

## 이번 조사에서 구분한 대상

여기서 도메인 계층은 업무 객체와 그 규칙을 구현하는 계층을 뜻한다. 사용자가 말한 “구현 레이어”도 이 의미로 읽는다. 회사마다 같은 용어를 사용한다고 가정하지 않는다.

| 대상 | 무엇 때문에 형태가 정해지는가? | 이번 조사에서 확인할 사항 |
| --- | --- | --- |
| HTTP 요청·응답 DTO | 클라이언트와 약속한 API 형식 | Controller 밖으로 같은 객체가 전달되는가? |
| Service 입력·출력 객체 | 해당 업무 흐름의 입출력 | HTTP 계약과 별도로 정의하는가? |
| 도메인 명령용 입력 객체 | 도메인 동작에 필요한 값 | 도메인이 자기 인터페이스로 소유하는가? |
| DB 조회 결과 DTO | SQL에서 선택·조합한 컬럼 | 조회 구현의 결과인지, 도메인 개념을 표현하는지 구분되는가? |

`domain`이라는 패키지 이름, `domain-*`이라는 빌드 모듈, 업무 규칙만 담은 도메인 계층은 같은 범위를 뜻하지 않을 수 있다. 실제 클래스와 의존 관계를 확인한 범위까지만 판단한다.

## LINE: HTTP DTO와 서비스 입력을 구분한 예제

- 출처: [지속 가능한 소프트웨어 설계 패턴: 포트와 어댑터 아키텍처 적용하기](https://engineering.linecorp.com/ko/blog/port-and-adapter-architecture/), 2020-12-01.
- 성격: 개발 경험을 배경으로 작성한 아키텍처 설명용 예제. Spring Data JPA와 MVC를 논하지만 특정 운영 Spring Boot 프로젝트의 전체 소스는 아니다.

공개한 패키지와 호출 관계는 다음과 같다.

| 데이터 | 배치·사용 |
| --- | --- |
| `RegisterRequest`, `RegisterResponse` | `interfaces.member`의 Controller와 함께 배치 |
| `StoredItemDto`, `UserDto` | `interfaces.common`에 배치 |
| `RentalTarget` | `application`에 정의하고 서비스 입력으로 사용 |
| `Customer`, `Rental`, `Item` | `domain`의 업무 모델 |

Controller는 HTTP 입력인 `RentParam`을 `RentalTarget`으로 변환한다. 외부 API 응답인 `StoredItem`도 내부에서 사용하는 `Item`으로 변환한다. 외부 통신 형식이 서비스·도메인의 인터페이스를 결정하지 않게 하려는 예제다. **LINE 전체의 DTO 금지 규칙이나 DB 조회 결과 DTO의 배치 규칙까지 확인한 것은 아니다.**

## 카카오페이: 도메인 객체가 명령용 입력 객체를 소유

- 출처: [카카오페이 여신코어 DDD로 구축하기](https://tech.kakaopay.com/post/backend-domain-driven-design/), 2025-05-23.
- 성격: 후불결제 여신코어의 실제 구축·운영 경험과 설명용 코드. 카카오 본사가 아니라 카카오페이 사례다. Spring 멀티모듈 환경을 명시한다.

`gaia-core-app`은 `gaia-account-domain` 같은 도메인 모듈에 의존한다. `RegisterService`는 `RegisterRequestV1`을 받은 뒤 `Account.CreateCommand`를 만들어 `Account.create()`에 전달한다. 실제 import는 `com.kakaopay.gaia.account.domain.entity.Account`다.

`Account` 내부에는 `ConfirmOverdueCommand`, `ReleaseOverdueCommand` 같은 `data class`도 선언한다. 업무 동작에 필요한 날짜들을 묶은 공개 입력 객체다. 따라서 **도메인 객체가 전달받을 값을 별도 객체로 묶는 방식 자체를 배제하지 않는다.** 단, 글은 이것을 Command라고 부르며 API 응답 DTO나 DB projection이라고 부르지 않는다.

도메인 모듈은 `DomainEntity`뿐 아니라 `internal` JPA Entity·Repository도 포함한다. JPA Entity는 DomainEntity와 변환하고 외부 직접 접근을 제한한다. 즉, 이 자료의 **도메인 모듈 전체를 순수 도메인 계층 하나로 읽으면 안 된다.**

## 우아한형제들: 도메인 모듈의 코어 서비스가 DTO로 전달

- 출처: [전체 서비스를 관통하는 도메인 모듈을 안전하게 분리하기](https://techblog.woowahan.com/22151/), 2025-05-29.
- 성격: B마트 WMS플랫폼팀의 실제 리팩토링·배포 경험.

`domain-inventory` 모듈로 재고 관련 객체·Repository·서비스를 모은 뒤, 모듈 내부의 역할도 나눴다. 유즈케이스 구현체는 검증, 코어 서비스용 DTO 변환, 서비스 조합을 맡는다. 코어 서비스는 엔터티를 직접 반환하지 않고 필요한 데이터를 DTO나 인터페이스로 전달한다.

이는 **도메인 모듈의 서비스 경계에 DTO를 사용하는 실제 사례**다. 그러나 DTO 클래스명·package·모듈 의존성 전체가 공개된 것은 아니다. “순수 Entity 계층에 HTTP DTO를 둔다”거나 “조인 조회 결과를 domain 패키지에 둔다”는 주장으로 확대하지 않는다.

보조 자료인 [코드리뷰 적응기](https://techblog.woowahan.com/2614/)(2019-02-28)는 Spring Boot 2.x 파일럿의 리뷰 경험이다. 응답용 날짜 표현은 Entity가 아니라 ResponseDto에서 처리하도록 권하고, DTO를 사용 용도와 Controller·Service 등 사용 레벨에 맞게 배치하도록 설명한다. 운영 서비스 전체의 공통 규칙으로 취급하지 않는다.

## NAVER: `domain`이라는 패키지명만으로는 계층을 알 수 없음

NAVER nGrinder 공개 소스에는 `org.ngrinder.monitor.share.domain.SystemInfo`가 있다. `Serializable`을 구현하며 모니터링 값과 JMX `CompositeData` 파싱·기록 문자열 변환을 포함한다. 해당 패키지 설명은 모니터 서버와 클라이언트가 공유하는 클래스라고 명시한다. [SystemInfo 소스](https://raw.githubusercontent.com/naver/ngrinder/develop/ngrinder-core/src/main/java/org/ngrinder/monitor/share/domain/SystemInfo.java), [패키지 설명](https://raw.githubusercontent.com/naver/ngrinder/develop/ngrinder-core/src/main/java/org/ngrinder/monitor/share/domain/package-info.java)

이는 전달·변환 역할을 하는 객체가 `domain`이라는 이름 아래 존재하는 보조 사례다. **순수 업무 도메인 계층에 SQL 조회 DTO를 배치한 직접 근거는 아니다.** 해당 객체 자체는 Spring 서비스가 아니며, 이를 NAVER Spring Boot 전체의 규칙으로 확대하지 않는다. 소스 링크는 이동 가능한 `develop` 브랜치다.

## 확인하지 못한 범위

- **쿠팡:** [캐시 운영 경험](https://medium.com/coupang-engineering/lessons-learned-from-operating-our-data-serving-layer-4e9e4f68fe85)(2022-08-19)에서 GC 부담을 줄이기 위해 DTO 대신 바이트 배열을 저장한 사실은 확인했다. 그러나 DTO의 패키지나 Spring 업무 서버의 도메인 계층 배치는 공개하지 않아, 이번 질문의 직접 근거로 사용하지 않는다.
- **카카오 본사·카카오뱅크:** 카카오페이 사례를 이 회사들의 규칙으로 대신하지 않는다. 현재 질문에 맞는 DTO의 실제 배치 근거는 미확인이다.
- **NAVER·네이버페이:** 위 OSS 외에 순수 업무 도메인 계층의 DB 조회 DTO 배치를 직접 판단할 공개 근거는 미확인이다.
- 공개 자료에서 찾지 못했다는 것은 해당 방식이 없거나 금지됐다는 의미가 아니다. 전사 비공개 코드나 모든 공개 저장소를 조사한 결과가 아니다.
- 회사별 사례는 현재 수리맵의 클래스 위치를 자동으로 정당화하거나 금지하지 않는다. 동일한 DTO 종류와 동일한 계층을 비교해야 한다.

## 수리맵에 대입하면

조사일의 미커밋 구현을 확인한 결과다. 다음은 기업의 규칙이 아니라 현재 코드에 대한 판단이다.

- [MarkerWriteAccessData](../../backend/src/main/java/com/surimap/domain/marker/MarkerWriteAccessData.java)는 HTTP DTO가 아니다. [Mapper SQL](../../backend/src/main/resources/mapper/marker/MarkerAccessMapper.xml)이 사건 상태·현재 수색 차수·배정 개수·근무교대 ID를 한 행으로 반환할 때 사용하는 조회 결과 객체다.
- [MarkerWriteAccessValidator](../../backend/src/main/java/com/surimap/domain/marker/MarkerWriteAccessValidator.java)는 이 객체에서 값을 꺼내 허용 조건을 검사한다. 현재는 DB 조회 결과와 검사 입력을 같은 클래스로 표현한다.
- [backend/AGENTS.md](../../backend/AGENTS.md)는 `domain`에 Mapper를 함께 두고, 기존 도메인 객체로 표현할 수 없는 복합 조회 결과에 별도 projection을 허용한다. 따라서 현재 `domain` 패키지는 업무 객체만 담은 순수 계층과 범위가 다르다.
- 이 규칙에 부합한다는 사실과 이 클래스가 꼭 필요하다는 판단은 별개다. Validator 공통화 자체가 조회 통합이나 새 DTO를 요구하지는 않는다. 여러 조회값을 한 객체로 다룰 필요와 조회 결과를 Validator가 직접 받을 필요를 검토한 뒤 유지 여부를 결정한다.

이번 조사는 이 파일만 추가한다. 코드·AGENTS·TODO의 수정이나 클래스 이동은 하지 않는다.

## 추가 조사: 통합 조회 결과와 내부 클래스

- 조사일: 2026-09-10
- 질문을 좁혔다. 여러 테이블의 정보를 SQL로 조합해 객체로 직접 받는지, 그 객체를 독립 클래스와 내부 클래스 중 어디에 두는지, 성능 효과를 측정했는지 확인한다.
- 위 본문의 도메인 Command·HTTP DTO 배치 사례는 DB 조회 결과 타입의 직접 근거로 재사용하지 않는다.

### 직접 확인한 조회 결과 객체

| 기업·자료 성격 | 실제 확인한 구현 | 확인하지 못한 내용 |
| --- | --- | --- |
| NAVER가 개발·공개한 Pinpoint, Spring/MyBatis OSS v2.5.4 | 두 테이블을 조인한 결과를 `UserGroupMember`라는 독립 Java 클래스로 받는다. | 여러 개별 SQL을 이 SQL로 줄였다는 변경 이력, 개선 전후 성능, 전사 클래스 배치 규칙 |
| 우아한형제들 배민페이플랫폼팀, 운영 포인트 시스템의 Kotlin 전환(2025) | QueryDSL 조회 결과를 DTO에 직접 매핑한다. Kotlin 전환 후 `Projections.constructor()`를 선택했다. | DTO의 정확한 패키지, 내부 클래스 여부, SQL 통합 횟수, DTO 매핑만의 성능 효과 |
| 우아한형제들 정산플랫폼팀, Spring Boot 2.6.x 파일럿(2022) | `OwnerResponseDto`에 필요한 컬럼을 직접 조회하고, 배치 Reader는 `PaymentAggregationDto`를 받는다. | 운영 시스템의 전사 표준, 내부 클래스 여부, 조회 성능 개선 수치 |

Pinpoint는 NAVER D2의 [공식 공개 공지](https://d2.naver.com/news/7885839/)로 회사와 프로젝트의 관계를 확인했다. 현재 저장소 주소는 `pinpoint-apm/pinpoint`다. 이번에는 v2.5.4에 해당하는 커밋 `4568766840110085652924c3a76bdd2ff9a26d80`(2024-04-18)을 기준으로 다음 호출 관계를 확인했다.

1. `MysqlUserGroupDao.selectMember()`는 Spring의 `@Repository` 안에서 MyBatis `SqlSessionTemplate.selectList()`를 호출하고 `List<UserGroupMember>`를 반환한다. [DAO 소스](https://github.com/pinpoint-apm/pinpoint/blob/4568766840110085652924c3a76bdd2ff9a26d80/web/src/main/java/com/navercorp/pinpoint/web/dao/mysql/MysqlUserGroupDao.java#L93)
2. `UserGroupMapper.xml`의 `selectMemberList`는 `user_group_member`와 `puser`를 조인해 그룹 ID·회원 ID·이름·부서를 함께 선택하고 `resultType="UserGroupMember"`로 매핑한다. [SQL 소스](https://github.com/pinpoint-apm/pinpoint/blob/4568766840110085652924c3a76bdd2ff9a26d80/web/src/main/resources/mapper/UserGroupMapper.xml#L75)
3. 반환 객체는 `com.navercorp.pinpoint.web.vo.UserGroupMember`의 독립 클래스다. 필드·생성자·접근자를 가진 데이터 전달 객체이며 내부 클래스가 아니다. 같은 객체를 저장 입력에도 사용하므로 조회 전용 DTO라고 단정하지 않는다. [타입 소스](https://github.com/pinpoint-apm/pinpoint/blob/4568766840110085652924c3a76bdd2ff9a26d80/web/src/main/java/com/navercorp/pinpoint/web/vo/UserGroupMember.java#L1)

배민페이플랫폼팀은 DTO 직접 매핑을 실제 운영 코드에서 사용하고 있으며, Kotlin의 기본 생성자·nullable 처리 문제를 줄이기 위해 생성자 매핑을 선택했다고 설명한다. 이는 데이터 전달 객체의 존재와 매핑 방식의 근거이지, 독립 클래스를 만들면 빨라진다는 근거가 아니다. [Kotlin 전환기, QueryDSL DTO 매핑 절](https://techblog.woowahan.com/22586/)

파일럿 사례는 Entity → Service DTO → Presentation DTO의 반복 변환 대신, 필요한 조회값을 DTO로 바로 받는 방향을 설명한다. 공개 코드의 `findById()`가 `Projections.constructor(OwnerResponseDto.class, ...)`를 호출한다. 이는 단일 테이블의 컬럼 선택 예제이므로 여러 SQL을 하나로 통합한 사례와 구분한다. [파일럿 회고](https://techblog.woowahan.com/7828/)

### 카카오페이가 공개한 내부 클래스 사용 범위

카카오페이 홈 서버는 Controller와 Infrastructure의 DTO 구분을 유지하면서, 응답 DTO 클래스를 이름 공간으로 삼아 Nested Class를 활용했다고 명시한다. 데이터베이스 접근과 외부 API에서 사용하는 `Image`·`Link` 같은 응답도 섞지 않도록 구분한 운영 경험이다. [홈 서버 아키텍처 회고, Controller와 Infrastructure 간 DTO 분리 유지 절](https://tech.kakaopay.com/post/home-hexagonal-architecture/)

다만 이 글은 특정 SQL이 특정 내부 클래스로 매핑되는 소스나 Java의 `static` 선언을 공개하지 않는다. 따라서 **“DTO를 내부 클래스로 묶는 실제 선택이 있다”까지 확인했으며, “DB 조회 DTO는 Mapper의 static 내부 클래스로 만든다”는 규칙까지 확인한 것은 아니다.** 이름 충돌과 책임 구분을 위한 선택이지 성능 개선 수단으로 제시한 것도 아니다.

### 조회 최적화는 무엇을 측정했는가?

우아한형제들 셀러시스템의 배치 개선은 먼저 실제 SQL에서 N+1을 확인했다. 이후 여러 가게 ID를 모아 임시 휴무·정기 휴무·운영 시간을 **각각의 Repository로 묶음 조회**하고 가게별 Map으로 조합했다. 모두 한 SQL로 합친 것이 아니다. 도메인 계산·변경 이벤트 최적화도 함께 적용한 뒤 테스트에서 5배 이상 개선됐다고 보고하며, 서버 CPU·I/O와 DB CPU·쿼리 지연도 확인했다. 이 결과를 DTO 하나나 SQL 통합 하나의 효과로 해석하면 안 된다. [배치 개선기](https://techblog.woowahan.com/13569/)

LINE Plus 주문 시스템은 반대로 17개 테이블의 조인이 부담이 된 상황에서 조회용 비정규화 모델을 분리했다. 운영 전후 평균 단일 조회는 Oracle 158ms에서 MySQL 4ms로 바뀌었고, 글에서 한국·일본 간 지연을 대략 제외한 Oracle 값은 약 60ms다. **DB·모델·네트워크 조건이 함께 바뀐 비교**이므로 DTO projection만의 효과나 “SQL 한 번이 항상 빠르다”는 근거가 아니다. [주문 DB 마이그레이션 경험기](https://techblog.lycorp.co.jp/ko/experience-in-migrating-order-db-on-ecommerce-platform)

### 이번 질문에서 얻은 결론과 한계

- **조회 결과를 전용 형태의 객체로 직접 받는 방식은 실제로 사용한다.** MyBatis 조인 결과와 QueryDSL DTO 매핑의 직접 근거를 확인했다.
- **독립 클래스와 내부 클래스 중 하나를 정답으로 삼는 업계 공통 규칙은 확인하지 못했다.** 독립 조회 결과 클래스의 실제 소스와 응답 DTO를 내부 클래스로 구성했다는 운영 경험은 확인했지만, 같은 SQL 결과를 두 형태로 비교한 기업 규칙·성능 자료는 확보하지 못했다.
- **조회 최적화도 언제나 하나의 SQL로 합치는 방식은 아니다.** 필요한 컬럼의 직접 조회, 여러 ID의 묶음 조회, 조회용 모델 분리 등 실제 병목에 따라 선택이 달랐다. 이는 위 사례의 비교 결과다.
- 쿠팡·카카오 본사·카카오뱅크에서 이번 조건에 해당하는 SQL 결과 타입과 선언 위치를 함께 보여주는 공식 근거는 미확인이다. 공개 자료를 찾지 못했다는 사실을 금지 규칙으로 해석하지 않는다.
- 수리맵의 최대 5개 조회를 1개 SQL로 묶은 변경이 얼마나 빨라졌는지는 이 기업 사례로 알 수 없다. 실제 비교 측정 전에는 성능 개선을 확정하지 않는다.
- 이번 후속 조사는 이 절만 덧붙였다. 코드·테스트·AGENTS·TODO 수정, 부하테스트, 커밋은 수행하지 않았다.
