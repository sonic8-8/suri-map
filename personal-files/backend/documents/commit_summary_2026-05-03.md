# 2026-05-03 커밋별 변경 요약

이 문서는 현재 브랜치에서 작성한 주요 커밋을 커밋 단위로 설명한다.

대상 커밋:

1. `0c45752` `[BE] feat(maparea): Mybatis 추가 및 PostGIS 공간 검증 추가`
2. `904e47c` `[BE] refactor(persistence): 사용하지 않는 JPA 설정 제거`
3. `2cbfc05` `[BE] feat(maparea): S2 도형 검증 규칙 추가`
4. `d033608` `[BE] feat(maparea): 조회 포트와 PostGIS 어댑터 추가`
5. `c35bf0b` `[BE] feat(operational-period): OP fixture 계약 추가`
6. `8a397d6` `[BE] test(build): PostGIS 통합 테스트를 기본 테스트에서 제외`
7. `a973f54` `[BE] chore(build): Gradle Kotlin DSL 설정으로 정리`
8. `071b49a` `[BE] docs(maparea): 공간 DB와 MyBatis 매핑 주석 보강`
9. `9c28a05` `[BE] test(maparea): 조회 계약 fixture를 DTO factory로 정리`
10. `e5ebce6` `[BE] feat(operational-period): current OP 계약 필드 확장`

아직 별도 커밋하지 않은 항목:

- `docs/tasks/L3-tasks.md`: L3-B01 완료 체크 변경
- `backend/src/main/java/com/surimap/maparea/query/AreaQuery.java`: source contract 주석 문구 변경
- `personal-files/`: 개인 기록 문서
- `*:Zone.Identifier`, `package.json`, `package-lock.json`, `CLAUDE.md`: 커밋 제외 검토 대상

---

## 1. `0c45752` `[BE] feat(maparea): Mybatis 추가 및 PostGIS 공간 검증 추가`

### 파일 관계도

```text
Spring Boot
  └─ SuriMapApplication
      └─ @MapperScan
          ├─ GeometrySpatialMapper interface
          │   └─ mapper/maparea/geometry/GeometrySpatialMapper.xml
          │       └─ PostGIS functions
          │           ├─ ST_Area(...::geography)
          │           ├─ ST_Covers
          │           └─ ST_Intersection
          │
          └─ MyBatis TypeHandlers
              ├─ JtsGeometryTypeHandler
              └─ UuidTypeHandler

Flyway migrations
  ├─ map_boundary table + GiST index
  ├─ search_area table + GiST index
  └─ search_area_history table

Integration tests
  ├─ PostGisExtensionIntegrationTest
  ├─ GeometrySpatialMapperIntegrationTest
  └─ JtsGeometryTypeHandlerIntegrationTest
```

### 파일 역할 표

| 파일 | 역할 |
|---|---|
| `backend/build.gradle` | MyBatis, JTS, Testcontainers 의존성을 추가한다. |
| `backend/src/main/java/com/surimap/SuriMapApplication.java` | MyBatis mapper scan 범위를 등록한다. |
| `backend/src/main/resources/application.yml` | MyBatis XML mapper 위치, TypeHandler package, 기본 설정을 추가한다. |
| `backend/src/main/java/com/surimap/config/mybatis/JtsGeometryTypeHandler.java` | PostGIS geometry 값을 JTS `Geometry`로 읽고, JTS geometry를 EWKT 문자열로 쓴다. |
| `backend/src/main/java/com/surimap/config/mybatis/UuidTypeHandler.java` | PostgreSQL UUID 컬럼과 Java `UUID` 타입을 매핑한다. |
| `backend/src/main/java/com/surimap/maparea/geometry/validation/PostGisGeometrySpatialAdapter.java` | `GeometrySpatialPort`를 PostGIS/MyBatis mapper 기반으로 구현한다. |
| `backend/src/main/java/com/surimap/maparea/geometry/validation/mapper/GeometrySpatialMapper.java` | 공간 검증 SQL을 호출하는 MyBatis mapper interface다. |
| `backend/src/main/resources/mapper/maparea/geometry/GeometrySpatialMapper.xml` | 면적, 포함, 겹침 검증용 PostGIS SQL을 정의한다. |
| `backend/src/main/resources/db/migration/V10__create_map_boundary.sql` | `map_boundary` 테이블과 active partial unique index, GiST geometry index를 만든다. |
| `backend/src/main/resources/db/migration/V11__create_search_area.sql` | `search_area` 테이블과 GiST geometry index를 만든다. |
| `backend/src/main/resources/db/migration/V12__create_search_area_history.sql` | `search_area_history` 테이블을 만든다. |
| `backend/src/test/java/com/surimap/maparea/support/PostGisIntegrationTestSupport.java` | Testcontainers PostGIS 기반 통합 테스트 공통 설정이다. |
| `backend/src/test/java/com/surimap/maparea/fixture/BoundaryAreaFixtures.java` | S2 map_boundary/search_area 고정 ID, 상태, 이벤트 fixture를 제공한다. |
| `backend/src/test/java/com/surimap/maparea/fixture/GeometryFixtures.java` | S2 canonical bbox, 좌표, valid/invalid geometry fixture를 제공한다. |
| `backend/src/test/java/com/surimap/maparea/fixture/SpatialSqlFixtures.java` | PostGIS SQL 통합 테스트용 WKT/JTS Polygon fixture를 제공한다. |
| `PostGisExtensionIntegrationTest` | PostGIS extension 활성화를 검증한다. |
| `GeometrySpatialMapperIntegrationTest` | PostGIS 면적, 포함, 겹침 함수 결과를 검증한다. |
| `JtsGeometryTypeHandlerIntegrationTest` | JTS Polygon을 MyBatis TypeHandler로 저장/조회할 수 있는지 검증한다. |
| `JtsGeometryTypeHandlerTestMapper.java` / XML | TypeHandler 검증 전용 test mapper다. |

### 커밋 요약

- L3-B01의 공간 DB 기반을 준비했다.
- PostGIS를 전제로 `map_boundary`, `search_area`, `search_area_history` schema를 추가했다.
- geometry 컬럼은 PostGIS `geometry(Polygon, 4326)`로 두고, 공간 검색을 위해 GiST index를 추가했다.
- MyBatis XML mapper와 JTS TypeHandler를 통해 PostGIS geometry를 Java 코드에서 다룰 수 있게 했다.
- Testcontainers 기반 통합 테스트로 PostGIS extension, 공간 함수, TypeHandler 동작을 검증할 수 있게 했다.

### 파일별 상세 설명

#### `backend/build.gradle`

MyBatis 기반 persistence와 PostGIS geometry 검증에 필요한 의존성을 추가했다.

추가된 주요 의존성:

- `mybatis-spring-boot-starter`
- `jts-core`
- `mybatis-spring-boot-starter-test`
- `testcontainers:junit-jupiter`
- `testcontainers:postgresql`

이 커밋에서는 JPA 제거를 같이 하지 않는다. JPA 제거는 다음 커밋인 `904e47c`에서 별도로 처리한다. 그래서 이 커밋 시점의 `build.gradle`은 MyBatis/JTS를 추가하되 기존 JPA/Hibernate Spatial 의존성은 아직 남아 있는 상태다.

#### `backend/src/main/java/com/surimap/SuriMapApplication.java`

MyBatis mapper scan 범위를 애플리케이션 시작 클래스에 등록했다.

등록한 mapper package:

- `com.surimap.maparea.geometry.validation.mapper`
- `com.surimap.maparea.query.mapper`

이 범위를 좁게 지정한 이유는 `com.surimap` 전체를 scan하면 mapper가 아닌 interface까지 MyBatis mapper 후보로 잡힐 수 있기 때문이다. 특히 `GeometrySpatialPort` 같은 port interface는 MyBatis mapper가 아니므로 scan 대상에서 제외되어야 한다.

#### `backend/src/main/resources/application.yml`

MyBatis mapper XML과 TypeHandler package 설정을 추가했다.

설정 내용:

- `mapper-locations: classpath*:mapper/**/*.xml`
- `type-handlers-package: com.surimap.config.mybatis`
- `map-underscore-to-camel-case: true`
- `default-fetch-size: 100`
- `default-statement-timeout: 30`

이 커밋에서는 기존 `spring.jpa` 설정을 삭제하지 않고 유지한다. 삭제는 JPA 제거 커밋에서 분리했다.

#### `JtsGeometryTypeHandler.java`

PostGIS geometry 값을 JTS `Geometry`로 변환하는 MyBatis TypeHandler다.

역할:

- Java -> DB: JTS `Geometry`의 SRID와 WKT를 EWKT 문자열로 만들어 parameter에 넣는다.
- DB -> Java: PostgreSQL에서 읽은 EWKT/WKT 문자열을 JTS `Geometry`로 파싱한다.
- `SRID=4326;POLYGON (...)` 형태와 일반 WKT 문자열을 모두 처리할 수 있게 한다.

이 TypeHandler는 query adapter에서 geometry 컬럼을 JTS `Polygon`으로 받은 뒤, 이후 canonical GeoJSON/bbox 변환에 사용된다.

주의점:

- 이 TypeHandler 자체가 geometry validity를 검증하지는 않는다.
- self-intersection, 면적, boundary 포함 여부는 `GeometryValidationService`와 PostGIS mapper 검증에서 처리한다.

#### `UuidTypeHandler.java`

PostgreSQL UUID 컬럼과 Java `UUID`를 매핑하는 MyBatis TypeHandler다.

필요한 이유:

- PostgreSQL `uuid` 컬럼은 JDBC driver에서 `UUID` 또는 문자열 형태로 반환될 수 있다.
- mapper result record에서 ID 계열 필드를 `UUID`로 유지하려면 명시 TypeHandler가 있으면 안전하다.

역할:

- parameter로 들어오는 `UUID`를 `PreparedStatement#setObject`로 바인딩한다.
- result에서 `UUID` 또는 문자열을 읽어 Java `UUID`로 변환한다.

#### `PostGisGeometrySpatialAdapter.java`

도메인 서비스가 직접 MyBatis mapper를 알지 않도록 `GeometrySpatialPort`를 구현한 adapter다.

호출 흐름:

```text
GeometryValidationService
  -> GeometrySpatialPort
    -> PostGisGeometrySpatialAdapter
      -> GeometrySpatialMapper
        -> GeometrySpatialMapper.xml
          -> PostGIS SQL
```

제공 기능:

- polygon 실제 면적이 기준 m2 이상인지 확인
- parent polygon이 child polygon을 cover하는지 확인
- 두 polygon이 면적 기준으로 겹치는지 확인

`Boolean.TRUE.equals(...)`를 사용하는 이유는 mapper 결과가 `null`로 들어와도 false로 안정적으로 처리하기 위해서다.

#### `GeometrySpatialMapper.java`

PostGIS 공간 검증 SQL을 호출하는 MyBatis mapper interface다.

메서드:

- `isAreaAtLeastM2`
- `covers`
- `overlapsByArea`

이 interface는 SQL을 직접 갖지 않고 XML mapper와 연결된다.

#### `GeometrySpatialMapper.xml`

핵심 PostGIS 공간 검증 SQL을 정의한다.

주요 SQL 의미:

- `ST_Area(geometry::geography)`: EPSG:4326 좌표를 meter 기반 면적으로 계산한다.
- `ST_Covers(parent, child)`: child가 parent 내부 또는 경계에 포함되는지 확인한다.
- `ST_Area(ST_Intersection(...)) > 0`: 경계만 닿는 경우는 overlap으로 보지 않고 실제 면적이 겹치는 경우만 true로 본다.

이 XML은 WKT 문자열을 입력으로 받아 `ST_GeomFromText(..., 4326)`로 PostGIS geometry를 생성한다.

#### `V10__create_map_boundary.sql`

`map_boundary` 테이블을 생성한다.

핵심 컬럼:

- `id uuid`
- `incident_id uuid`
- `geometry geometry(Polygon, 4326)`
- `status`
- `version`
- ADR-0012 계열 시간 필드

핵심 index:

- incident별 active boundary 중복 방지 partial unique index
- geometry GiST index

이 migration은 S2의 “incident별 active map_boundary 1개” 규칙과 viewport/spatial query를 위한 기반이다.

#### `V11__create_search_area.sql`

`search_area` 테이블을 생성한다.

핵심 컬럼:

- `id uuid`
- `incident_id uuid`
- `created_op_id uuid`
- `parent_area_id`
- `split_group_id`
- `geometry geometry(Polygon, 4326)`
- `state`
- `search_count`
- `version`

핵심 index:

- incident 기준 조회 index
- OP 기준 조회 index
- geometry GiST index

이 migration은 area query, bbox filter, split parent/child 관계의 기반이다.

#### `V12__create_search_area_history.sql`

`search_area_history` 테이블을 생성한다.

역할:

- search_area 생성, 상태 변경, geometry 변경, split 이력을 기록할 수 있는 schema 기반을 마련한다.

아직 write path가 완성된 것은 아니다. `GeometryValidationService`에는 API write path와 history transaction wiring이 후속 task라는 TODO가 남아 있다.

#### `PostGisIntegrationTestSupport.java`

PostGIS 기반 통합 테스트 공통 부모 클래스다.

역할:

- `postgis/postgis:16-3.5` Testcontainers를 띄운다.
- Spring datasource를 container JDBC URL로 동적 연결한다.
- 테스트 사이에 `search_area_history`, `search_area`, `map_boundary`를 정리한다.

이 support 덕분에 PostGIS extension, mapper SQL, TypeHandler 검증 테스트가 실제 PostgreSQL/PostGIS 위에서 실행된다.

#### `BoundaryAreaFixtures.java`

S2 map_boundary/search_area 관련 ID, 상태, 이벤트 fixture를 모은다.

제공하는 값:

- incident alias와 실제 UUID
- boundary UUID
- superseded boundary UUID
- area UUID
- OP1 UUID
- split child area UUID 목록
- event ID와 version/sequence
- persisted state 목록
- public status alias 기준

문서의 alias는 사람이 읽는 fixture 이름으로 남기고, 실제 DB ID는 UUID로 통일했다.

#### `GeometryFixtures.java`

S2 geometry rule의 canonical fixture를 제공한다.

제공하는 값:

- CRS `EPSG:4326`
- coordinate order `[lon, lat]`
- fixture bbox
- minimum polygon area `400`
- coordinate precision `6`
- valid map_boundary ring
- valid search_area ring
- shared marker point
- invalid coordinate fixtures

이 fixture는 geometry validation test와 query contract test 양쪽에서 공유된다.

#### `SpatialSqlFixtures.java`

PostGIS SQL 통합 테스트용 WKT/JTS fixture다.

제공하는 WKT:

- 정상 boundary polygon
- 정상 search area polygon
- 400m2 미만 tiny polygon
- boundary 밖 polygon
- 경계만 맞닿는 polygon

`searchAreaPolygon()`은 JTS `Polygon` 객체를 만들어 TypeHandler 저장/조회 테스트에 사용한다.

#### `PostGisExtensionIntegrationTest.java`

PostGIS extension이 실제 DB에서 활성화되어 있는지 확인한다.

검증 방식:

- `SELECT postgis_version()` 호출
- 결과가 blank가 아니면 통과

이 테스트는 “PostGIS extension 가정”을 자동 테스트 evidence로 만든다.

#### `GeometrySpatialMapperIntegrationTest.java`

PostGIS 핵심 공간 함수 mapper를 검증한다.

검증 내용:

- 정상 search_area는 400m2 이상이다.
- tiny polygon은 400m2 미만이다.
- boundary가 search_area를 cover한다.
- boundary 밖 polygon은 cover하지 않는다.
- 실제 면적으로 겹치는 경우와 경계만 닿는 경우를 구분한다.

#### `JtsGeometryTypeHandlerIntegrationTest.java`

JTS Polygon을 MyBatis TypeHandler로 저장하고 다시 조회하는 흐름을 검증한다.

검증 내용:

- 저장한 ID가 그대로 조회된다.
- geometry가 null이 아니다.
- SRID가 4326이다.
- geometry type이 Polygon이다.
- 좌표 개수가 저장 전후로 유지된다.

#### `JtsGeometryTypeHandlerTestMapper.java` / `JtsGeometryTypeHandlerTestMapper.xml`

TypeHandler 검증 전용 test mapper다.

역할:

- `search_area` 테이블에 JTS `Polygon`을 insert한다.
- geometry 컬럼을 TypeHandler로 다시 읽어온다.

별도 probe table을 만들지 않고 실제 `search_area` schema를 사용해 TypeHandler를 검증한다.

---

## 2. `904e47c` `[BE] refactor(persistence): 사용하지 않는 JPA 설정 제거`

### 파일 관계도

```text
Before
  Spring Boot
    ├─ JPA / Hibernate Spatial 설정
    └─ MyBatis / JTS 설정

After
  Spring Boot
    └─ MyBatis / JTS 설정
```

### 파일 역할 표

| 파일 | 역할 |
|---|---|
| `backend/build.gradle` | 사용하지 않는 JPA/Hibernate Spatial 의존성을 제거한다. |
| `backend/src/main/resources/application.yml` | 사용하지 않는 `spring.jpa` 설정을 제거한다. |

### 커밋 요약

- maparea geometry persistence가 MyBatis/JTS 기반으로 정리되면서, 남아 있던 JPA/Hibernate Spatial 설정을 제거했다.
- 이 커밋은 의존성 전환을 명확히 분리하기 위해 별도 커밋으로 구성했다.
- 1번 커밋은 MyBatis/PostGIS 기반 추가, 2번 커밋은 기존 JPA 설정 제거만 담당한다.

### 파일별 상세 설명

#### `backend/build.gradle`

제거한 의존성:

- `spring-boot-starter-data-jpa`
- `hibernate-spatial`

제거 이유:

- 이번 maparea geometry 작업은 MyBatis XML mapper와 JTS TypeHandler 기반으로 구현했다.
- PostGIS geometry query는 SQL을 명시적으로 관리하는 편이 테스트/검증 요구와 맞는다.
- JPA/Hibernate Spatial 설정이 남아 있으면 실제 사용하지 않는 persistence stack이 같이 올라와 설정과 오류 원인이 복잡해진다.

주의점:

- 다른 도메인이 JPA entity/repository를 사용하기 시작하면 이 제거는 재검토해야 한다.
- 이 커밋 시점 기준으로는 현재 변경 범위에서 JPA 기반 구현이 없다는 판단으로 제거했다.

#### `backend/src/main/resources/application.yml`

제거한 설정:

- `spring.jpa.hibernate.ddl-auto`
- `spring.jpa.open-in-view`
- `spring.jpa.properties.hibernate.*`

제거 이유:

- schema 관리는 Flyway migration으로 가져간다.
- query 구현은 MyBatis mapper XML로 가져간다.
- Hibernate SQL formatting/timezone 설정은 JPA 사용이 없으면 의미가 없다.

---

## 3. `2cbfc05` `[BE] feat(maparea): S2 도형 검증 규칙 추가`

### 파일 관계도

```text
GeoJsonPolygon input
  └─ GeometryValidationService
      ├─ GeometryValidator
      │   ├─ coordinate shape 검증
      │   ├─ EPSG:4326 lon/lat 범위 검증
      │   ├─ S2 fixture bbox 검증
      │   ├─ precision 6자리 이하 검증
      │   ├─ canonical scale 정규화
      │   └─ ring closure / 연속 중복 좌표 처리
      │
      ├─ JTS Polygon 생성
      │   ├─ SRID 4326 설정
      │   ├─ self-intersection 검증
      │   └─ zero-area 검증
      │
      └─ GeometrySpatialPort
          ├─ 최소 면적 400m2 검증
          ├─ active boundary covers 검증
          └─ split child overlap 검증

Failure
  ├─ InvalidGeometryException -> 400 invalid_geometry
  └─ MapBoundaryRequiredException -> 409 map_boundary_required
```

### 파일 역할 표

| 파일 | 역할 |
|---|---|
| `MapBoundaryRequiredException.java` | active `map_boundary`가 필요한데 없을 때 `map_boundary_required` 오류를 표현한다. |
| `GeometryValidationService.java` | map_boundary, search_area, split child 검증 흐름을 조립한다. |
| `GeometryValidator.java` | 좌표 구조, precision, bbox, ring closure 등 DB 없이 가능한 1차 검증을 담당한다. |
| `GeometryFixtureExactnessTest.java` | S2 문서의 canonical geometry fixture 값이 바뀌면 실패하도록 고정한다. |
| `GeometryPolicyTest.java` | S2 geometry policy 기본값과 보조 판정 메서드를 검증한다. |
| `GeometryValidationServiceTest.java` | 서비스 수준의 map_boundary/search_area/split 검증 흐름을 mock port로 검증한다. |
| `GeometryValidatorTest.java` | 좌표와 polygon ring 수준의 1차 검증을 검증한다. |

### 커밋 요약

- S2 geometry rule을 Java 검증 흐름으로 고정했다.
- 좌표 precision은 소수점 6자리 이하만 허용하고, 7자리 초과 fixture는 `invalid_geometry`로 실패하도록 정리했다.
- `activeBoundaryWkt`가 없을 때는 `invalid_geometry`가 아니라 `map_boundary_required` 전용 예외를 사용하도록 분리했다.
- 실제 DB 기반 공간 연산은 `GeometrySpatialPort`에 위임하고, 서비스 테스트에서는 mock으로 검증한다.
- `GeometryFixtureExactnessTest`를 추가해 문서 고정 좌표, bbox 순서, shared marker coordinate가 깨지면 테스트가 실패하도록 했다.

### 파일별 상세 설명

#### `MapBoundaryRequiredException.java`

active `map_boundary`가 필요한 요청에서 boundary가 없을 때 사용하는 예외다.

의미:

- search_area 생성/수정/split 같은 기능은 active map_boundary를 기준으로 containment 검증을 해야 한다.
- active boundary 자체가 없으면 geometry가 잘못된 것이 아니라 선행 boundary가 없는 상태다.
- 그래서 `invalid_geometry`가 아니라 `map_boundary_required`로 분리했다.

현재 상태:

- 예외 타입과 `errorCode()`만 추가되어 있다.
- 공통 exception handler에서 HTTP 409로 매핑하는 작업은 아직 별도 후속 범위다.

#### `GeometryValidationService.java`

S2 geometry 검증 흐름을 도메인 시나리오별로 조립한다.

주요 메서드:

- `validateMapBoundaryPolygon`
- `validateSearchAreaPolygon`
- `validateSplitChildren`

변경된 핵심:

- active boundary WKT가 없으면 `MapBoundaryRequiredException`을 던진다.
- API write path, `PublishRequest`, `search_area_history` transaction wiring은 아직 TODO로 남겼다.

검증 책임 분리:

- 좌표/ring 구조 검증은 `GeometryValidator`
- JTS polygon validity 검증은 service 내부
- m2 면적, covers, overlap 검증은 `GeometrySpatialPort`

#### `GeometryValidator.java`

DB 없이 가능한 1차 geometry 검증을 담당한다.

검증 내용:

- point가 `[lon, lat]` 형태인지 확인
- coordinate null 거부
- precision 6자리 초과 거부
- EPSG:4326 lon/lat 범위 검증
- S2 fixture bbox 내부 여부 검증
- 좌표 scale 6자리 canonicalization
- ring null/empty 거부
- ring 좌표 수 최소 4개 검증
- 연속 중복 좌표 제거
- ring closure 검증

이번 커밋의 중요 변경:

- 기존에는 7자리 좌표를 반올림해서 살릴 수 있었지만, 이제 validator에서 precision 초과를 `invalid_geometry`로 거부한다.
- `GeometryPolicy.canonicalizeCoordinate`는 utility로 남아 있지만, validator 진입점에서는 precision 초과를 먼저 막는다.

#### `GeometryFixtureExactnessTest.java`

S2 하네스 문서의 geometry fixture exactness를 검증한다.

검증 내용:

- CRS가 `EPSG:4326`인지
- 좌표 순서가 `[lon, lat]`인지
- fixture bbox 값과 순서가 문서와 같은지
- valid map_boundary ring 좌표가 문서와 같은지
- valid search_area ring 좌표가 문서와 같은지
- shared marker coordinate가 문서와 같은지
- invalid coordinate fixture 값이 문서와 같은지
- 정상 polygon 좌표 scale이 6자리인지

이 테스트는 L3-T04A의 `canonical geometry fixture tests` evidence 역할도 한다.

#### `GeometryPolicyTest.java`

S2 geometry policy의 고정값과 보조 메서드를 검증한다.

검증 내용:

- SRID 4326
- CRS `EPSG:4326`
- coordinate order `[lon, lat]`
- harness bbox
- coordinate precision 6
- minimum polygon area 400
- bbox 내부/경계/외부 판정
- lon/lat 유효 범위 판정
- lat/lon swapped 좌표가 bbox 밖으로 판정되는지
- precision 판정
- 잘못된 policy 생성 거부

#### `GeometryValidatorTest.java`

`GeometryValidator`의 순수 검증 로직을 검증한다.

검증 내용:

- point scale 6자리 정규화
- precision 7자리 좌표 거부
- null point 거부
- point shape 오류 거부
- null coordinate 거부
- lon/lat EPSG:4326 범위 밖 거부
- S2 bbox 밖 좌표 거부
- lat/lon swapped 좌표 거부
- null/empty ring 거부
- 좌표 수 부족 ring 거부
- ring canonicalization
- 연속 중복 좌표 제거
- ring closure 검증

#### `GeometryValidationServiceTest.java`

서비스 수준의 S2 geometry 검증 흐름을 검증한다.

검증 내용:

- map_boundary polygon 정상 검증 결과
- non-Polygon type 거부
- null polygon 거부
- outer ring 없는 polygon 거부
- self-intersection polygon 거부
- zero-area polygon 거부
- 400m2 미만 polygon 거부
- search_area가 active boundary 내부면 통과
- search_area가 active boundary 밖이면 거부
- active boundary WKT가 없으면 `map_boundary_required`
- split child가 2개 미만이면 거부
- split child가 parent 내부이고 서로 겹치지 않으면 통과
- split child가 parent 밖이면 거부
- split child끼리 면적 기준으로 겹치면 거부

이 테스트는 실제 PostGIS DB를 띄우지 않는다. `GeometrySpatialPort`를 mock으로 두고 service orchestration만 검증한다.

---

## 4. `d033608` `[BE] feat(maparea): 조회 포트와 PostGIS 어댑터 추가`

### 파일 관계도

```text
Consumer lane
  ├─ MapBoundaryQuery.of(incidentId)
  └─ AreaQuery.byIncident / byOp

Query port
  ├─ MapBoundaryQuery
  │   └─ PostGisMapBoundaryQuery
  │       └─ MapAreaQueryMapper
  │           └─ MapAreaQueryMapper.xml
  │               └─ map_boundary table
  │
  └─ AreaQuery
      └─ PostGisAreaQuery
          └─ MapAreaQueryMapper
              └─ MapAreaQueryMapper.xml
                  └─ search_area table

Mapper record
  ├─ MapBoundaryQueryRecord
  └─ AreaQueryRecord

Output DTO
  ├─ MapBoundaryQueryResult
  └─ AreaQueryResult
      └─ AreaRow

Geometry conversion
  └─ JTS Polygon -> canonical GeoJSON + bbox
```

### 파일 역할 표

| 파일 | 역할 |
|---|---|
| `MapBoundaryQuery.java` | active map_boundary 단건 조회 port다. |
| `MapBoundaryQueryResult.java` | `MapBoundaryQuery.of` 응답 shape이다. |
| `AreaQuery.java` | incident/op 기준 search_area 목록 조회 port다. |
| `AreaQueryFilter.java` | status, archived 포함 여부, bbox, version, updatedAfter 등 area 조회 필터다. |
| `AreaQueryResult.java` | area 목록 응답과 sourceVersion을 담는다. |
| `AreaRow.java` | 단일 search_area row 응답 shape이다. |
| `PostGisMapBoundaryQuery.java` | `MapBoundaryQuery`의 PostGIS 구현체다. |
| `PostGisAreaQuery.java` | `AreaQuery`의 PostGIS 구현체다. |
| `MapAreaQueryMapper.java` | map_boundary/search_area 조회용 MyBatis mapper interface다. |
| `MapBoundaryQueryRecord.java` | mapper에서 읽은 map_boundary DB row를 담는다. |
| `AreaQueryRecord.java` | mapper에서 읽은 search_area DB row를 담는다. |
| `MapAreaQueryMapper.xml` | active boundary, area by incident, area by op SQL을 정의한다. |
| `QueryFixtures.java` | query contract에서 기대하는 id/status/version/geometry/bbox/updatedAt fixture를 제공한다. |
| `MapBoundaryQueryContractTest.java` | `MapBoundaryQuery.of` mock contract를 검증한다. |
| `AreaQueryContractTest.java` | `AreaQuery.byIncident`, `AreaQuery.byOp` mock contract를 검증한다. |
| `MapAreaQueryIntegrationTest.java` | 실제 PostGIS DB 기반 query 결과가 fixture shape과 일치하는지 검증한다. |
| `MapBoundaryActivePartialUniqueIndexTest.java` | active boundary partial unique index와 GiST index 존재를 검증한다. |
| `SearchAreaSpatialIndexIntegrationTest.java` | search_area bbox filter와 GiST index 존재를 검증한다. |

### 커밋 요약

- L3-T04A mock contract와 L3-T04B 실제 PostGIS query 구현을 함께 추가했다.
- 소비 Lane이 의존하는 query shape을 `MapBoundaryQueryResult`, `AreaQueryResult`, `AreaRow`로 고정했다.
- 실제 DB query는 MyBatis XML mapper로 구현하고, JTS Polygon을 canonical GeoJSON/bbox로 변환해 반환한다.
- active `map_boundary`가 없으면 port는 empty를 반환하고, 호출자가 `map_boundary_required`로 변환하는 구조다.
- area query는 incident/op 기준 조회와 bbox filter를 지원한다.
- mock contract test는 fixture/shape가 깨지면 실패하고, integration test는 실제 PostGIS query가 같은 shape을 반환하는지 검증한다.

### 파일별 상세 설명

#### `MapBoundaryQuery.java`

active map_boundary 단건 조회 port다.

메서드:

- `Optional<MapBoundaryQueryResult> of(UUID incidentId)`

의미:

- active boundary가 있으면 query result를 반환한다.
- 없으면 empty를 반환한다.
- empty를 어떤 API error로 변환할지는 호출 유스케이스가 담당한다.

#### `MapBoundaryQueryResult.java`

`MapBoundaryQuery.of` 응답 shape이다.

필드:

- `id`
- `incidentId`
- `status`
- `version`
- `geometry`
- `bbox`
- `updatedAt`

소비 Lane은 이 shape을 기준으로 지도 기준 범위와 offline package/board fixture를 구성할 수 있다.

#### `AreaQuery.java`

search_area 목록 조회 port다.

메서드:

- `byIncident(UUID incidentId)`
- `byIncident(UUID incidentId, AreaQueryFilter filter)`
- `byOp(UUID opId)`

`byIncident` 기본 메서드는 filter 없이 전체 active/current 관점 조회를 쉽게 호출하기 위한 convenience method다.

#### `AreaQueryFilter.java`

area 조회 조건을 담는다.

필드:

- status 목록
- archived 포함 여부
- opId
- bbox
- minVersion
- updatedAfter

bbox는 `[minLon, minLat, maxLon, maxLat]` 순서의 viewport filter로 쓰인다.

#### `AreaQueryResult.java`

area 목록 조회 결과다.

필드:

- `incidentId`
- `sourceVersion`
- `areas`

`sourceVersion`은 area row들의 max version 기준으로 소비자가 변경 감지에 사용할 수 있다.

#### `AreaRow.java`

단일 search_area query row shape이다.

필드:

- id 계열
- persisted `state`
- public alias `status`
- searchCount
- version
- canonical GeoJSON geometry
- bbox
- updatedAt

현재는 `state`와 `status`를 동일하게 노출한다. 향후 completed alias 정책이 붙으면 `state`와 `status`가 분리될 수 있다.

#### `PostGisMapBoundaryQuery.java`

`MapBoundaryQuery`의 PostGIS 구현체다.

흐름:

1. mapper에서 active boundary row 조회
2. JTS Polygon을 canonical GeoJSON으로 변환
3. bbox 계산
4. `MapBoundaryQueryResult` 생성

active boundary가 없으면 `Optional.empty()`를 반환한다.

#### `PostGisAreaQuery.java`

`AreaQuery`의 PostGIS 구현체다.

흐름:

1. mapper에서 incident/op 기준 area row 목록 조회
2. 각 row의 JTS Polygon을 canonical GeoJSON으로 변환
3. bbox 계산
4. `AreaRow` 생성
5. `sourceVersion` 계산
6. `AreaQueryResult` 반환

#### `MapAreaQueryMapper.java`

map_boundary/search_area query용 MyBatis mapper interface다.

메서드:

- active boundary 조회
- incident 기준 area 조회
- op 기준 area 조회

SQL은 XML 파일에서 정의한다.

#### `MapBoundaryQueryRecord.java`

mapper가 읽은 map_boundary DB row를 담는 record/class다.

역할:

- MyBatis resultMap의 target type
- UUID, status, version, JTS geometry, updatedAt 값을 Java object로 받는다.

#### `AreaQueryRecord.java`

mapper가 읽은 search_area DB row를 담는다.

역할:

- MyBatis resultMap의 target type
- DB row shape과 query DTO shape 사이의 중간 모델이다.

#### `MapAreaQueryMapper.xml`

실제 map_boundary/search_area 조회 SQL을 정의한다.

주요 쿼리:

- incident의 active map_boundary 조회
- incident 기준 search_area 목록 조회
- op 기준 search_area 목록 조회
- bbox intersection filter
- archived 포함 여부 filter
- status filter
- version/updatedAfter filter

geometry 컬럼은 `JtsGeometryTypeHandler`를 통해 JTS Polygon으로 매핑된다.

#### `QueryFixtures.java`

query contract test에서 사용할 기대 응답 fixture다.

제공 내용:

- `MapBoundaryQuery.of` 기대 row
- `AreaQuery.byIncident` 기대 result
- `AreaQuery.byOp` 기대 result
- updatedAt fixture

`BoundaryAreaFixtures`와 `GeometryFixtures`를 조합해 id/status/version/geometry/bbox 값을 고정한다.

#### `MapBoundaryQueryContractTest.java`

`MapBoundaryQuery.of` mock contract test다.

검증 내용:

- active boundary가 있으면 id/status/version/geometry/bbox/updatedAt shape이 fixture와 일치한다.
- active boundary가 없으면 empty를 반환한다.

이 테스트는 실제 DB 없이도 소비 Lane이 기대 shape을 알 수 있게 한다.

#### `AreaQueryContractTest.java`

`AreaQuery.byIncident`, `AreaQuery.byOp` mock contract test다.

검증 내용:

- incident ID
- sourceVersion
- area row id/opId/state/status/searchCount/version
- canonical geometry
- bbox
- updatedAt

#### `MapAreaQueryIntegrationTest.java`

실제 PostGIS DB에 fixture row를 넣고 query adapter가 같은 shape을 반환하는지 검증한다.

검증 내용:

- `MapBoundaryQuery.of` active row 조회
- active boundary 없을 때 empty
- `AreaQuery.byIncident`
- `AreaQuery.byOp`
- filter 조건별 결과

mock contract와 실제 DB query가 같은 fixture shape으로 수렴하는지 확인하는 테스트다.

#### `MapBoundaryActivePartialUniqueIndexTest.java`

`map_boundary`의 index 규칙을 검증한다.

검증 내용:

- 같은 incident에 ACTIVE boundary 두 개는 허용하지 않는다.
- SUPERSEDED boundary는 여러 개 허용한다.
- active partial unique index가 존재한다.
- geometry GiST index가 존재한다.

#### `SearchAreaSpatialIndexIntegrationTest.java`

search_area spatial index와 bbox filter를 검증한다.

검증 내용:

- bbox filter가 viewport와 교차하는 area만 반환한다.
- `idx_search_area_geom` GiST index가 존재한다.

---

## 5. `c35bf0b` `[BE] feat(operational-period): OP fixture 계약 추가`

### 파일 관계도

```text
S8 consumer contract
  └─ OperationalPeriodQuery.currentOf(incidentId)
      └─ CurrentOpResult

Fixture
  ├─ OpAssignmentFixtures
  │   ├─ OP1 current fixture
  │   ├─ OP2 transition fixture
  │   ├─ op_required / op_mismatch guard fixture
  │   └─ assignment row shape fixture
  │
  └─ HandoverAiFixtures
      ├─ handover memo fixture
      ├─ AI summary fixture
      └─ SC-10/SC-11 related IDs

Contract test
  └─ Op1BootstrapContractTest
      ├─ current OP shape 검증
      ├─ bootstrap publish event shape 검증
      └─ guard error fixture 검증
```

### 파일 역할 표

| 파일 | 역할 |
|---|---|
| `CurrentOpResult.java` | 현재 active OP query 결과 shape이다. |
| `OperationalPeriodQuery.java` | incident 기준 current OP 조회 port다. |
| `Op1BootstrapContractTest.java` | OP1 bootstrap/current OP mock contract와 guard fixture를 검증한다. |
| `OpAssignmentFixtures.java` | OP1/OP2, transition, assignment, guard error fixture를 제공한다. |
| `HandoverAiFixtures.java` | handover memo와 AI summary 관련 S8 fixture를 제공한다. |

### 커밋 요약

- S8 current OP 소비 계약을 위한 최소 query port와 result shape을 추가했다.
- OP1 bootstrap 후 current OP가 어떤 id/status/sequenceNo/version을 가져야 하는지 fixture로 고정했다.
- OP2 transition, assignment, handover/AI summary 관련 fixture도 함께 준비했다.
- `op_required`, `op_mismatch` guard fixture를 제공해 field-write 소비 Lane이 같은 기준으로 실패 케이스를 만들 수 있게 했다.
- 실제 OP write 구현은 아직 아니며, L3-T05B/L3-T06A 이후 구현이 이 fixture 계약을 만족해야 한다.

### 파일별 상세 설명

#### `CurrentOpResult.java`

`OperationalPeriodQuery.currentOf`의 응답 shape이다.

필드:

- `opId`
- `status`
- `sequenceNo`
- `version`

이 shape은 L1, S3-1, S5, S7 같은 current OP 소비 Lane이 공통으로 기대할 최소 필드다.

#### `OperationalPeriodQuery.java`

current OP 조회 port다.

메서드:

- `Optional<CurrentOpResult> currentOf(UUID incidentId)`

의미:

- OP1 bootstrap 이후에는 ACTIVE OP를 반환한다.
- bootstrap 이전이거나 active OP가 없으면 empty를 반환한다.
- empty는 write guard에서 `op_required`로 변환될 수 있다.

#### `Op1BootstrapContractTest.java`

L3-T05A Phase 0 mock contract test다.

검증 내용:

- OP1 bootstrap 이후 `currentOf(incidentId)`가 sequenceNo=1, status=ACTIVE, version=1인 OP를 반환한다.
- OP1 bootstrap event는 `OP_TRANSITIONED`, `fromOpId=null`, `toOpId=OP1` shape을 가진다.
- current OP가 없을 때 write guard fixture는 `409 op_required`다.
- 요청 opId가 current OP와 다를 때 guard fixture는 `409 op_mismatch`다.

주의점:

- 실제 bootstrap handler 구현은 아직 없다.
- 실제 guard service/annotation 구현도 아직 없다.
- 현재는 fixture/mock contract로 소비 Lane이 사용할 shape과 실패 코드를 고정한 상태다.

#### `OpAssignmentFixtures.java`

S8 OP/assignment 관련 fixture를 제공한다.

주요 fixture:

- incident alias와 UUID
- OP1 current fixture
- OP2 transition fixture
- `OP_TRANSITIONED` event fixture
- board probe slot fixture
- idempotency replay fixture
- `op_required`, `op_mismatch` guard error fixture
- OP reason/status 목록
- assignment status/type 목록
- query row shape 목록

이 fixture는 L3-T05A뿐 아니라 이후 L3-T05B, L3-T06A, L3-T06B의 기준 데이터로도 쓰일 수 있다.

#### `HandoverAiFixtures.java`

handover memo와 AI summary 관련 S8 fixture다.

역할:

- SC-10/SC-11에서 사용할 handover memo ID, summary ID, context ID 등을 제공한다.
- AI summary success/fallback/guard 관련 기대값을 준비한다.
- OP 전환 이후 인수인계와 AI 요약 흐름에서 소비할 fixture 기반을 만든다.

이 커밋에서는 실제 handover write/query API나 AI summary 구현은 하지 않는다. 후속 task에서 이 fixture를 기준으로 구현을 붙일 수 있게 준비한 상태다.

---

## 6. `8a397d6` `[BE] test(build): PostGIS 통합 테스트를 기본 테스트에서 제외`

### 파일 관계도

```text
Gradle test task
  └─ useJUnitPlatform
      └─ excludeTags("integration")

PostGIS integration tests
  ├─ @Tag("integration")
  └─ PostGisIntegrationTestSupport
      ├─ @SpringBootTest
      ├─ @ActiveProfiles("test")
      └─ Testcontainers PostGIS
```

### 파일 역할 표

| 파일 | 역할 |
|---|---|
| `backend/build.gradle.kts` | 기본 `test` task에서 `integration` tag를 제외한다. |
| `PostGisIntegrationTestSupport.java` | PostGIS/Testcontainers 통합 테스트의 공통 support이며 `integration` tag를 가진다. |
| `*IntegrationTest.java` / index 검증 테스트 | Docker/PostGIS가 필요한 테스트임을 `@Tag("integration")`로 표시한다. |

### 커밋 요약

- Docker/PostGIS 인프라가 준비되지 않은 CI에서도 기본 backend 테스트가 통과할 수 있게 했다.
- Testcontainers 의존성은 유지하되, `./gradlew test` 기본 경로에서는 `@Tag("integration")` 테스트를 제외한다.
- PostGIS extension, MyBatis TypeHandler, 공간 SQL, index 검증 테스트는 삭제하지 않고 나중에 통합 테스트 환경이 준비되면 다시 실행할 수 있게 남겼다.

### 주의점

- 기본 테스트는 `integration` tag를 제외하므로 PostGIS 실제 동작을 검증하지 않는다.
- 통합 테스트를 돌릴 때는 별도 Gradle task나 tag include 설정이 필요하다.

---

## 7. `a973f54` `[BE] chore(build): Gradle Kotlin DSL 설정으로 정리`

### 파일 역할 표

| 파일 | 역할 |
|---|---|
| `backend/build.gradle` | 기존 Groovy DSL build file이며 이 커밋에서 삭제된다. |

### 커밋 요약

- backend 빌드 설정을 `build.gradle.kts` 기준으로 단일화했다.
- 중복 build file을 제거해 Gradle 설정 출처를 하나로 줄였다.

### 주의점

- 이 커밋은 빌드 동작을 새로 추가하지 않고, 이미 쓰고 있던 Kotlin DSL 쪽으로 정리하는 성격이다.

---

## 8. `071b49a` `[BE] docs(maparea): 공간 DB와 MyBatis 매핑 주석 보강`

### 파일 관계도

```text
MyBatis TypeHandlers
  ├─ JtsGeometryTypeHandler
  └─ UuidTypeHandler

Geometry spatial mapper
  ├─ GeometrySpatialMapper.java
  └─ GeometrySpatialMapper.xml

Flyway migrations
  ├─ V10__create_map_boundary.sql
  └─ V11__create_search_area.sql
```

### 파일 역할 표

| 파일 | 역할 |
|---|---|
| `JtsGeometryTypeHandler.java` | EWKT/JTS Geometry 변환 메서드별 Javadoc과 한글 예외 메시지를 추가한다. |
| `UuidTypeHandler.java` | UUID 변환 메서드별 Javadoc과 한글 예외 메시지를 추가한다. |
| `GeometrySpatialMapper.java` | PostGIS 공간 검증 mapper 메서드와 파라미터 의미를 설명한다. |
| `GeometrySpatialMapper.xml` | `ST_Area`, `ST_Covers`, `ST_Intersection` SQL의 목적을 짧게 주석으로 남긴다. |
| `V10__create_map_boundary.sql` | `clock_offset_ms` 의미를 주석으로 보강한다. |
| `V11__create_search_area.sql` | split, version, clock offset, GiST index 의미를 주석으로 보강한다. |

### 커밋 요약

- JPA 경험자가 MyBatis/SQL 기반 코드를 읽을 때 막히는 지점을 줄이기 위해 핵심 파일에 짧은 설명을 추가했다.
- TypeHandler는 각 override 메서드가 어떤 JDBC 입출력 지점을 담당하는지 Javadoc으로 설명한다.
- XML mapper에는 PostGIS 함수 이름만 보고는 의도를 알기 어려운 부분에 간단한 한국어 주석을 달았다.
- migration에는 `clock_offset_ms`, split 관련 컬럼, version, GiST index의 목적을 바로 읽을 수 있게 보강했다.

---

## 9. `9c28a05` `[BE] test(maparea): 조회 계약 fixture를 DTO factory로 정리`

### 파일 관계도

```text
QueryFixtures
  ├─ MapBoundaryQueryResult
  ├─ AreaQueryResult
  ├─ AreaRow
  └─ AreaQueryFilter

Contract tests
  ├─ MapBoundaryQueryContractTest
  └─ AreaQueryContractTest

Integration test
  └─ MapAreaQueryIntegrationTest
```

### 파일 역할 표

| 파일 | 역할 |
|---|---|
| `QueryFixtures.java` | `Expected*` record 대신 실제 query DTO를 직접 반환하는 deterministic fixture factory가 된다. |
| `MapBoundaryQueryContractTest.java` | `MapBoundaryQueryResult` fixture를 직접 mock 응답으로 사용한다. |
| `AreaQueryContractTest.java` | `AreaQueryResult` fixture를 직접 mock 응답으로 사용하고 filter overload shape도 검증한다. |
| `MapAreaQueryIntegrationTest.java` | 실제 DB query 결과와 DTO fixture shape을 비교한다. |

### 커밋 요약

- `ExpectedMapBoundaryQueryRow`, `ExpectedAreaQueryResult`, `ExpectedAreaQueryRow` 중간 record를 제거했다.
- fixture가 실제 DTO를 직접 반환하므로 테스트 내부의 수동 변환 코드가 사라졌다.
- DTO 필드가 바뀌면 fixture factory나 contract test가 컴파일 단계에서 깨지기 쉬워져, 문서 필드 누락을 더 빨리 잡을 수 있다.
- `AreaQuery.byIncident/byOp`의 filter overload도 같은 응답 shape을 유지하는지 mock contract에서 확인한다.

### 주의점

- fixture는 랜덤 생성기가 아니라 고정 UUID, 고정 시간, 고정 좌표를 반환하는 deterministic factory다.
- 실제 DB query 검증은 `@Tag("integration")`이므로 기본 테스트에서는 제외된다.

---

## 10. `e5ebce6` `[BE] feat(operational-period): current OP 계약 필드 확장`

### 파일 관계도

```text
OperationalPeriodQuery.currentOf
  └─ CurrentOpResult
      ├─ opId
      ├─ incidentId
      ├─ status
      ├─ sequenceNo
      ├─ startedAt
      ├─ endedAt
      ├─ reason
      └─ version

Fixture
  └─ OpAssignmentFixtures.currentOpResult()

Contract test
  └─ Op1BootstrapContractTest
```

### 파일 역할 표

| 파일 | 역할 |
|---|---|
| `CurrentOpResult.java` | S8 current OP query shape에 `incidentId`, `startedAt`, `endedAt`, `reason`을 추가한다. |
| `OpAssignmentFixtures.java` | OP1/OP2 fixture에 시간과 reason을 포함하고 `currentOpResult()` DTO factory를 제공한다. |
| `Op1BootstrapContractTest.java` | OP1 bootstrap 후 current OP 응답의 전체 필드를 검증한다. |

### 커밋 요약

- S8 문서의 `OperationalPeriodQuery.current` row shape에 맞춰 current OP 응답 필드를 확장했다.
- OP1 bootstrap fixture가 `opId/status/sequenceNo/version`뿐 아니라 사건 ID, 시작/종료 시각, 생성 reason까지 고정한다.
- 테스트에서 `new CurrentOpResult(...)`를 직접 만들지 않고 `OpAssignmentFixtures.currentOpResult()`를 사용하도록 정리했다.

### 주의점

- 실제 OP DB query 구현은 아직 아니다.
- `OperationalPeriodQuery.list` 관련 구현은 Phase 0 필수 범위가 아니므로 이 커밋에는 포함하지 않았다.

---

## 전체 흐름 요약

```text
1. PostGIS/MyBatis 기반
   └─ 공간 DB, TypeHandler, Mapper, Testcontainers 통합 테스트 준비

2. JPA 설정 제거
   └─ geometry persistence 방향을 MyBatis/JTS 중심으로 정리

3. S2 도형 검증 규칙
   └─ Java validator + service mock test + fixture exactness test

4. maparea 조회 포트/어댑터
   └─ 소비 Lane용 query contract + 실제 PostGIS query implementation

5. operational-period fixture 계약
   └─ S8 current OP / OP1 / OP2 / guard fixture 준비

6. 기본 테스트 안정화
   └─ PostGIS/Testcontainers 통합 테스트를 기본 test task에서 제외

7. 빌드 파일 단일화
   └─ Groovy build.gradle 제거, Kotlin DSL 기준으로 정리

8. MyBatis/PostGIS 설명 보강
   └─ TypeHandler, mapper, migration 주석 추가

9. query fixture factory 정리
   └─ Expected record 대신 실제 DTO를 반환하는 fixture 사용

10. current OP 계약 확장
   └─ S8 current OP row shape에 맞춰 필드와 fixture 검증 확장
```

## 커밋 간 의존 관계

```text
0c45752 PostGIS/MyBatis 기반
  ├─ 904e47c JPA 설정 제거
  ├─ 2cbfc05 S2 도형 검증 규칙
  │   └─ GeometrySpatialPort를 통해 PostGIS 기반 공간 검증과 연결
  └─ d033608 조회 포트와 PostGIS 어댑터
      ├─ map_boundary/search_area schema 사용
      ├─ JTS TypeHandler 사용
      └─ GeometryFixtures / BoundaryAreaFixtures 사용

c35bf0b OP fixture 계약
  └─ S2 area fixture의 OP1 ID와 S8 current OP fixture가 같은 사건 흐름을 공유

8a397d6 기본 테스트 안정화
  └─ 0c45752/d033608의 PostGIS 통합 테스트를 삭제하지 않고 기본 test에서만 제외

a973f54 Gradle Kotlin DSL 정리
  └─ d87121f 이후 Kotlin DSL 기준 backend build 설정을 단일화

071b49a 주석 보강
  └─ 0c45752에서 추가된 TypeHandler/mapper/migration을 읽기 쉽게 설명

9c28a05 query fixture factory 정리
  └─ d033608의 MapBoundaryQuery/AreaQuery contract fixture를 실제 DTO 반환 방식으로 정리

e5ebce6 current OP 계약 확장
  └─ c35bf0b의 OP current fixture를 S8 service contract 전체 필드 기준으로 확장
```

## 리뷰 시 보면 좋은 순서

1. `0c45752`: DB/schema/mapper/test 기반 확인
2. `904e47c`: JPA 제거가 의도된 전환인지 확인
3. `2cbfc05`: S2 geometry rule이 문서와 맞는지 확인
4. `d033608`: query contract와 실제 DB query 결과 shape이 일치하는지 확인
5. `c35bf0b`: S8 fixture가 OP1/OP2 소비 계약에 충분한지 확인
6. `8a397d6`: CI 기본 테스트에서 integration 제외가 의도와 맞는지 확인
7. `a973f54`: Groovy build file 삭제가 Kotlin DSL 단일화 방향과 맞는지 확인
8. `071b49a`: 주석이 실제 코드/SQL 의미와 어긋나지 않는지 확인
9. `9c28a05`: fixture가 실제 DTO를 반환해 contract test 가독성이 좋아졌는지 확인
10. `e5ebce6`: S8 current OP 필드 확장이 문서 shape과 일치하는지 확인
