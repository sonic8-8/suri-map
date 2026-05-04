# maparea 테스트 안내

이 패키지는 S2 지도/영역 관련 테스트 자산과 테스트를 담는다.

## 디렉터리 역할

- `fixture/`: 테스트에서 쓰는 고정 값, DTO factory, 도형 상수
- `fixturetest/`: fixture 값이 문서 계약과 정확히 일치하는지 확인하는 테스트
- `geometry/policy/`: 좌표계, 정밀도, 폴리곤 조건 같은 정책 테스트
- `geometry/validation/`: 도형 검증과 mapper 관련 테스트
- `support/`: PostGIS 기반 통합 테스트 공통 지원 코드

## 파일 설명

### `fixture/`

- `GeometryFixtures.java`
  - 주요 상수: `CRS`, `COORDINATE_ORDER`, `FIXTURE_BBOX_*`, `MINIMUM_POLYGON_AREA_M2`, `COORDINATE_PRECISION_DECIMALS`
  - 주요 메서드: `validMapBoundaryRing()`, `validSearchAreaRing()`, `referenceMarkerPoint()`, `invalidCoordOutsideEnvelope()`, `invalidCoordLatLonSwapped()`, `overPrecisionPoint()`, `validMapBoundaryPolygon()`, `validSearchAreaPolygon()`
  - 용도: S2 geometry 검증에서 쓰는 기준 좌표와 실패 좌표를 제공

- `BoundaryAreaFixtures.java`
  - 주요 상수: `INCIDENT_ALIAS`, `INCIDENT_ID`, `BOUNDARY_ALIAS`, `BOUNDARY_ID`, `AREA_ALIAS`, `AREA_ID`, `OP1_ALIAS`, `OP1_ID`, `OP2_ALIAS`, `OP2_ID`, `HISTORY_ID`, `MAP_BOUNDARY_STATUSES`, `SEARCH_AREA_PERSISTED_STATES`, `SEARCH_AREA_HISTORY_EVENT_TYPES`
  - 주요 메서드: `mapBoundaryChangedEvent()`, `areaCreatedEvent()`, `areaArchivedAfterSplitEvent()`
  - 용도: boundary/area 상태와 SC-04 이벤트 envelope fixture 제공

- `SpatialSqlFixtures.java`
  - 주요 상수: `BOUNDARY_WKT`, `SEARCH_AREA_WKT`, `TINY_AREA_WKT`, `OUTSIDE_BOUNDARY_WKT`, `TOUCHING_AREA_WKT`
  - 주요 메서드: `searchAreaPolygon()`
  - 용도: PostGIS 저장/조회, type handler round-trip, containment 테스트용 spatial fixture 제공

### `fixturetest/`

- `GeometryFixtureExactnessTest.java`
  - 주요 검증 메서드: `geometry_policy_fixture_값을_고정한다()`, `map_boundary_fixture_exactness를_검증한다()`, `search_area_fixture_exactness를_검증한다()`, `shared_marker와_invalid_coordinate_fixture를_고정한다()`
  - 용도: geometry fixture가 문서 계약과 어긋나면 바로 실패하게 만드는 exactness 테스트

### `geometry/policy/`

- `GeometryPolicyTest.java`
  - 용도: 허용 CRS, 정밀도, 면적, 폴리곤 조건 같은 정책 검증

### `geometry/validation/`

- `GeometryValidatorTest.java`
  - 용도: geometry 허용/거부 규칙 검증

- `GeometryValidationServiceTest.java`
  - 용도: 검증 서비스 동작 테스트

- `GeometrySpatialMapperIntegrationTest.java`
  - 용도: spatial 값과 mapper 동작 검증

- `JtsGeometryTypeHandlerTestMapper.java`
  - 용도: type handler 검증용 테스트 mapper

- `JtsGeometryTypeHandlerIntegrationTest.java`
  - 용도: JTS type handler 통합 테스트

- `PostGisExtensionIntegrationTest.java`
  - 용도: PostGIS extension 사용 가능 여부와 동작 확인

### `support/`

- `PostGisIntegrationTestSupport.java`
  - 주요 메서드: `registerDataSourceProperties(...)`, `cleanS2Tables()`
  - 용도: PostGIS 통합 테스트 공통 설정과 테이블 초기화 헬퍼
