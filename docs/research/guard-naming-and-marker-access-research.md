# Spring 서버의 마커 쓰기 권한 검사 명명 조사

- 조사일: 2026-09-10
- 질문: 우아한형제들·쿠팡·NAVER·LINE·카카오의 Spring/Spring Boot 서버는 권한·업무 전제조건 검사에 어떤 이름을 사용하며, 수리맵에는 무엇이 맞는가?
- 코드 기준: `9eb36d0f`와 조사 시점 작업 트리.
- 상태: 조사 당시의 비교를 보존한다. 이후 확정한 구현은 아래에 구분한다.

## 후속 결정

사용자와 책임을 다시 나눈 뒤 `MarkerWriteAccessValidator`로 확정했다. 기존 앱·웹 마커 서비스와 사진 서비스가 `MarkerAccessMapper`로 조회하고, [Validator](../../backend/src/main/java/com/surimap/domain/marker/MarkerWriteAccessValidator.java)는 전달받은 값만 검사한다. 새 `MarkerWriteAccessService`는 만들지 않았다.

[MarkerWriteAccessData](../../backend/src/main/java/com/surimap/domain/marker/MarkerWriteAccessData.java)는 사건·배정·근무교대 조회 결과를 전달하는 객체다. 기존 Guard와 사진 서비스의 중복 검사는 제거했다. 아래 Service 추천은 **기존 클래스 전체를 유지한다는 조사 당시 전제**에 한정되며, 최종 구현 결정이 아니다.

## 조사 당시 결론

- 기업 사례만으로 `Guard`를 표준 이름으로 추천할 근거는 없다. 실제 공개 자료에는 `MemberValidator`, `ValidationService`, 서비스의 `hasPermission`, `validateEmployeeAuth`, 상태 전이용 `TransitionGuard`가 각각 다른 역할로 등장한다.
- “검증하는 역할” 자체는 Validator(검증 담당)가 Guard(진행 조건을 지키는 역할)보다 직접적으로 드러낸다. 다만 단어가 명확한지와 현재 클래스 전체에 맞는지는 다른 질문이다.
- **현재 전체 책임을 그대로 유지한다면 `MarkerWriteAccessService`를 추천한다.** 이 클래스는 DB 조회·검사 순서 조율·실패 시 예외·근무교대 ID 반환까지 담당한다. 이는 저장소가 Service에 둔 책임과 맞는다.
- **`MarkerWriteAccessValidator`는 검사 규칙 자체를 표현할 때의 후보**다. 현재 클래스를 순수 검증 객체로 설명해서는 안 되며, 이름을 맞추려고 새 Service와 Validator를 둘 다 만들지는 않는다.

앞선 조사는 Google·Microsoft·Meta 등의 일반 OSS 용례까지 포함해 범위가 넓었다. “Guard라는 말이 틀리지는 않는다”는 설명과 “수리맵에서 가장 읽기 좋은 이름이다”라는 추천을 구분하고, 이번 본문은 국내 Spring 계열 자료와 현재 구현을 기준으로 다시 정리했다.

## 기업에서 실제로 확인한 이름

아래는 각 글이 공개한 시점의 특정 서비스·프로젝트 사례다. 회사 전체의 현재 명명 규칙이나 사용 빈도를 조사한 결과가 아니다. Spring MVC 사용만 확인한 자료를 Spring Boot 사례라고 표기하지 않는다.

| 기업·자료 성격 | 확인된 이름과 동작 | 비교할 때의 한계 |
| --- | --- | --- |
| 우아한형제들 정산플랫폼팀, Spring Boot 2.6.x 파일럿(2022) | `MemberValidator.validateCreation`, `validateChange`로 이메일·비밀번호·이름 등의 값 검증을 분리한다. [공식 회고](https://techblog.woowahan.com/8357/) | 팀 피드백을 받은 파일럿 코드다. 운영 서버의 자원 접근 권한 검사나 전사 표준은 아니다. |
| 우아한형제들 띠잉 채팅 서비스, Spring·Netty(2020) | `ValidationService.validateAndGet`은 메시지를 변환·검증한 뒤 반환하고, `validateChatLog`는 메시지 형식을 검증한다. [공식 개발기](https://techblog.woowahan.com/2681/) | 운영 경험의 설명용 발췌다. Spring 사용은 보이지만 해당 글의 Boot 버전은 확인하지 않았다. 사용자 권한 검사와는 다르다. |
| 우아한형제들 로봇 배달 관리 서비스, Spring Statemachine(2024) | `TransitionGuard`를 설정에 연결해 상태 전이 허용 조건을 판단한다. [공식 도입기](https://techblog.woowahan.com/19491/) | 실제 도입 경험을 단순화한 예제다. Guard가 쓰인다는 직접 사례지만 일반 마커 쓰기 권한 검사와 같은 구조는 아니다. |
| NAVER nGrinder, 실제 Spring MVC OSS 서버 | `PerfTestService.hasPermission`은 역할의 권한 또는 생성자 본인 여부를 boolean으로 판단한다. `delete`·`stop`은 false면 작업하지 않고 반환한다. [서비스 소스](https://github.com/naver/ngrinder/blob/develop/ngrinder-controller/src/main/java/org/ngrinder/perftest/service/PerfTestService.java) | 특정 OSS 제품의 실제 소스다. 이 조사에서 Boot 버전이나 NAVER 내부 전체 규칙은 확인하지 않았다. |
| NAVER nGrinder, 같은 서버의 Controller | `getOneWithPermissionCheck`는 조회 후 권한을 검사하고 `PerfTest`를 반환한다. 존재하는 테스트의 비소유자 접근은 조건에 따라 예외를 던진다. [구현](https://github.com/naver/ngrinder/blob/develop/ngrinder-controller/src/main/java/org/ngrinder/perftest/controller/PerfTestController.java) | 없는 테스트에는 null을 반환할 수 있다. 이름·동작 비교이지 이 Controller 배치를 수리맵에 도입하자는 뜻은 아니다. |
| 카카오뱅크 펀드 시스템, Spring Boot Starter(2025) | 예제의 `DepartmentValidationHandler`는 `validateDepartment`, `EmployeeAuthHandler`는 `validateEmployeeAuth`를 호출해 거래 가능한 부서·직원 권한을 검사한다. [공식 개발기](https://tech.kakaobank.com/posts/2511-spring-boot-starter-barcelona/) | 카카오 본사와 별도 회사다. 실제 운영 경험에 실린 설명용 예제이며, 배포 소스의 클래스명·검사 함수 내부·실패 예외까지 공개된 것은 아니다. |
| LINE/LY Corporation, 데마에칸 쿠폰 서비스의 Spring Boot 2.6.3 → 3.1.3 전환(2023) | `filterChain`의 `authorizeHttpRequests`, `permitAll`, `authenticated` 설정을 공개한다. [공식 전환기](https://techblog.lycorp.co.jp/ja/20231208a) | 운영 서비스의 Spring Boot·HTTP 인가 설정 사례다. 업무 검사 클래스 이름의 근거는 확보하지 못했다. |

우아한형제들의 OSORI 글은 Spring Boot 1.4 기반 권한관리 플랫폼에서 사용자 ID·URL로 접근 가능 여부를 판단하고 Spring 클라이언트를 Interceptor·Filter 형태로 제공했다고 설명한다. 권한 검사 목적과 HTTP 진입 구조를 확인할 수 있지만, 글에 공개되지 않은 업무 검사 클래스명을 추측하지 않는다. [공식 소개, 2017](https://techblog.woowahan.com/2519/)

## 조사에서 확인하지 못한 범위

- **쿠팡:** 공식 [Engineering Blog](https://medium.com/coupang-engineering/about)와 [GitHub 조직](https://github.com/coupang)을 대상으로 Spring·권한·검증 및 관련 식별자를 검색했으나, 이번 질문에 맞는 업무 검사 클래스명과 동작을 함께 확인하지 못했다. 상품 검색 결과나 쿠팡 클론 프로젝트는 근거에서 제외했다.
- **카카오 본사:** Spring 업무 서버의 해당 검사 구현이라는 연결 근거를 확보하지 못했다. 카카오뱅크 사례를 본사의 관행으로 대신하지 않는다.
- **LINE/LY Corporation:** 위 쿠폰 서비스에서는 보안 설정까지 확인했으나, 수리맵과 동등한 사건·배정 검사의 클래스명은 미확인이다.
- 찾지 못했다는 사실은 해당 회사가 Guard·Validator를 사용하지 않는다는 증거가 아니다. 비공개 코드나 공개 저장소 전체를 검사한 조사가 아니다.
- GitHub 링크는 이동 가능한 `develop` 브랜치다. HTML 검색 캐시와 raw 소스의 줄 번호가 달라, 식별자로 위치를 찾도록 행 번호를 고정하지 않았다. [nGrinder raw 소스](https://raw.githubusercontent.com/naver/ngrinder/develop/ngrinder-controller/src/main/java/org/ngrinder/perftest/service/PerfTestService.java)

## 수리맵에서 이름 붙일 대상

조사 대상은 `9eb36d0f`의 `marker/adapter/RuntimeMarkerWriteGuardAdapter.java`였다. Controller가 자동으로 호출하는 프레임워크 훅이 아니라 서비스가 직접 호출하는 Spring 빈이었다. 아래 표는 Validator로 분리하기 전의 동작이다.

| 메서드 | 현재 수행하는 일 | 성공 결과 |
| --- | --- | --- |
| `requireCreateAccess` | APP 인증 컨텍스트 → 사건 OPEN → 현재 OP 일치 → 계정의 활성 배정·해당 사건 배정 → 활성 근무교대 확인 | `dutyShiftId` 반환 |
| `requireIncidentAccess` | 사건 OPEN → 계정의 활성 배정·해당 사건 배정 확인 | 반환값 없이 계속 진행 |

[MarkerAccessMapper](../../backend/src/main/java/com/surimap/domain/marker/MarkerAccessMapper.java)로 데이터를 조회하고, 조건 불충족 시 예외를 던진다. 마커 저장·이벤트 발행은 하지 않지만, 단순 boolean 함수나 전달받은 값만 검사하는 객체도 아니다.

검사를 공유하는 곳은 [AppMarkerService](../../backend/src/main/java/com/surimap/app/service/marker/AppMarkerService.java), [웹 MarkerService](../../backend/src/main/java/com/surimap/api/service/marker/MarkerService.java), [PhotoService](../../backend/src/main/java/com/surimap/app/service/photo/PhotoService.java)다. 마커 자신의 작성자·출처·삭제 상태는 [Marker](../../backend/src/main/java/com/surimap/domain/marker/Marker.java)의 메서드로 판단한다. 현재 공통 검사 클래스가 모든 마커 쓰기 권한을 혼자 판정하는 것은 아니다.

## 후보 비교

아래 이름은 **수리맵을 위한 제안**이다. 조사한 기업에서 이 정확한 이름을 사용한다고 주장하지 않는다. 어휘 구성은 현대 단어의 뜻을 설명하는 보조 정보이며 역사적 어원에 대한 주장은 아니다.

| 후보 | 단어 구성·이미지 | 뉘앙스와 이름에서 예상할 행동 | 현재 코드 적합성 |
| --- | --- | --- | --- |
| `MarkerWriteAccessService` | Service(업무 수행): 필요한 정보를 가져와 마커 쓰기 접근 조건을 확인하는 업무 담당 | DB 조회, 검사 순서 조율, 실패 처리, 필요한 조회 결과 반환. 구체적인 검사 행동은 메서드에서 읽는다. | **현재 클래스 전체를 유지할 때 추천.** 기존 Service 책임 규칙과 맞는다. |
| `MarkerWriteAccessValidator` | validate(유효성을 검사하다) + 행위자 접미사: 통과 조건을 대조하는 검증 담당 | 마커 쓰기에 필요한 조건 검증. 검증 책임이 이름에서 가장 직접적으로 보인다. 반환·실패 방식은 메서드 계약으로 밝혀야 한다. | 검사 역할 자체를 명명할 때 유력하다. 단, 이 저장소의 순수 도메인 검증 객체로 현재 DB 조회 흐름 전체를 설명할 수는 없다. |
| `MarkerWriteAccessChecker` | check(확인하다) + er: 확인 담당 | 허용 여부를 물어보는 검사. boolean·결과 객체·예외 중 어느 방식인지는 이름만으로 정해지지 않는다. | 현재 Guard보다 반드시 명확하다고 보기 어렵다. 검증 목적에는 Validator, 전체 조율에는 Service가 더 선명하다. |

`Write`를 남기는 이유는 사건 종료 시 기록을 막는 조건을 일반 마커 조회 권한과 혼동하지 않게 하기 위해서다. `Access`는 좌표·메모 형식 같은 모든 마커 입력 검증을 이 클래스로 모으지 않게 범위를 제한한다. `Runtime`과 `Adapter`는 현재 검사 목적을 이해하는 데 추가 설명이 적다.

## 추천의 근거와 적용하지 않는 변경

[backend/AGENTS.md](../../backend/AGENTS.md)는 Service에 Mapper 사용·업무 흐름·권한 검사 순서 조율을 두고, 순수 계산·판단·검증은 도메인 객체나 Validator 등으로 분류한다. **현재 전체 객체에는 `MarkerWriteAccessService`, 검증 자체의 이름에는 `MarkerWriteAccessValidator`라는 구분은 이 프로젝트 규칙과 실제 동작을 적용한 판단이다.** “Validator는 절대로 DB를 조회하면 안 된다”는 업계 공통 규칙을 발견한 것은 아니다.

다음도 별도로 지켜야 한다.

- 이름을 정하기 위해 Service와 Validator 두 클래스를 새로 나누지 않는다. 현재 규모에서 분리할 실제 이유가 먼저다.
- 생성 검사가 근무교대 ID를 반환한다는 동작은 메서드 명명에서도 별도로 확인한다. `has...`로만 바꿔 boolean처럼 읽히게 하지 않는다.
- 공통 검사 클래스의 이름과 패키지 위치는 별도 결정이다. `marker/adapter` 정리 문제를 접미사 변경만으로 해결했다고 보지 않는다.
- DB 조회·권한 흐름을 `Marker` 도메인 객체에 넣지 않는다. 마커 자신의 값으로 판단 가능한 규칙만 해당 객체에 둔다.
- 기존 APP 마커 서비스 → 사진 서비스 의존 관계가 있다. 공통 검사를 APP 마커 서비스로 옮기고 사진 서비스가 다시 호출하도록 바꾸면 현재 구조에서는 순환 의존이 생긴다.
- 조사 단계에서는 문서 한 개만 수정했다. 이후 구현 결정은 이 문서의 '후속 결정'에 구분하며, 이 조사 자체를 동작 검증 결과로 보지 않는다.
