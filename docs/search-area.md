# 수색 구역과 담당 배정

전체 수색 범위, 하위 구역, 담당 배정·완료·분할을 바꿀 때 확인한다. 옛 S2 요구와 2026-09-21 정적 코드 대조 결과다. 문서 정리 중 API·상태·도형 규칙을 새로 확정하거나 구현에 맞춰 없애지 않았다.

## 사람이 정하는 수색 범위와 상태

- 전체 수색 범위는 `search_area.area_level=OVERALL`, 하위 구역은 `UNIT`·`TEAM`으로 표현한다. 사건 참여 배정과 구역 담당 배정은 다른 개념이다. 경로가 지나갔다고 구역을 자동 완료하거나 누락·다음 투입 구역을 판단하지 않는다.
- **기존 요구**: 웹 지휘자가 생성·수정·분할·담당 배정·완료를 결정하고 앱은 받은 구역을 조회한다. 상태·도형 변경에는 이전/이후 값, 처리 계정, 시각, 메모를 남긴다. 완료 후 재개는 명시적 결정이며, 취소 상태의 임의 전이는 허용하지 않는다.
- 원문은 `OVERALL` 통합 모델과 옛 별도 `overall_search_area`·`SUPERSEDED`·`state` 설명이 섞여 있었다. 현재 [SQL](../backend/src/main/resources/mapper/maparea/SearchAreaMapper.xml)의 `status`·계층 구조와 대조하며 과거 설명을 그대로 복원하지 않는다. `historyCount`는 전체 이력 행 수이므로 완료 횟수와 동일하다고 가정하지 않는다.

## 실제 API에 연결된 검증

- [Controller](../backend/src/main/java/com/surimap/api/controller/searcharea/SearchAreaController.java)는 [SearchAreaApiService](../backend/src/main/java/com/surimap/api/service/searcharea/SearchAreaApiService.java)를 호출한다. 별도 구역·상태 전이·공간 검증 클래스가 있다는 사실만으로 공개 API가 그 규칙을 사용하는 것은 아니다.
- **기존 도형 요구**: `[경도, 위도]`, EPSG:4326, 닫힌 Polygon, 연속 중복점 제거·소수 6자리 정규화, 빈 도형·자기 교차·최소 면적 미달 거부, 상위 범위 내부 포함, 분할 자식 간 면적 중복 거부다.
- **현재 차이**: API Service는 [기본 Validator](../backend/src/main/java/com/surimap/maparea/geometry/validation/GeometryValidator.java)를 호출하지만 반환된 정규화 좌표를 저장에 사용하지 않는다. 면적·상위 포함·자식 겹침을 검사하는 [GeometryValidationService](../backend/src/main/java/com/surimap/maparea/geometry/validation/GeometryValidationService.java)도 이 API의 호출 경로에 없다. 실제 HTTP 입력부터 DB 제약까지 재현해 확인해야 한다.
- **범위값의 차이**: 원문 fixture bbox는 서울 일대지만 [현재 기본 정책](../backend/src/main/java/com/surimap/maparea/geometry/policy/GeometryPolicy.java)의 `s2HarnessDefault()`는 광주 일대 좌표를 사용한다. 400㎡·소수 6자리·시험 bbox를 실제 모든 사건에 적용할 정책으로 자동 채택하지 않는다.
- 사건별 권한·종료 차단·현재 차수 검증도 각 write에서 확인한다. 공용 권한 검사의 한계는 [인증 기록](./authentication.md#권한-검사와-실패-순서)에 있다. 현재 API의 상태 변경은 허용 문자열을 확인하지만 기존 상태에서의 전이표를 검사하지 않는다.

## 분할·담당 변경·중복 요청

- **기존 분할 요구**: 일반 구역의 원본 도형을 남긴 채 부모를 취소하고 자식을 활성 상태로 만든다. 부모·자식 이력과 이벤트를 함께 저장하며 실패 시 전체를 되돌린다.
- 현재 API는 일반 부모는 취소하지만 `OVERALL` 부모는 유지하고 `UNIT` 자식을 만든다. 원문의 일괄 부모 취소 설명과 구분한다. 분할 차수·부모 포함·겹침 검사, 반복 분할과 기존 담당 배정 처리는 함께 확인할 대상이다.
- 담당 배정은 현재 기존 활성 배정을 취소한 뒤 요청된 계정 목록을 새로 저장한다. 사건·차수·구역 ID의 일치 외에 대상 계정의 사건 참여 권한과 변경 이력을 확인한다.
- **기존 멱등성·동시성 요구**: 같은 키·본문은 결과를 재사용하고 다른 본문은 거부한다. 버전 충돌 시 최신 상태를 조회하며 초안은 보존한다. API Service에는 DB와 메모리 대체 경로, `synchronized`와 요청 `toString()` 기반 해시가 섞여 있으므로 메모리 테스트를 실제 DB 롤백·다중 서버 충돌 방지 검증으로 보지 않는다.

## 화면 반영과 테스트 입력

- 저장 중 중복 클릭을 막고 실패 시 편집 초안을 유지하는 요구, 저장 응답보다 상황판 버전이 낮으면 갱신 대기를 표시하는 요구를 보존한다. 실제 화면·SSE·재조회 연결은 [구역 편집 화면](../frontend/src/features/areaEdit/presentation/pages/AreaEditPage.tsx)에서 확인하며, 도형·응답 단위 테스트 통과와 구분한다.
- [공용 fixture](../test-fixtures/common-fixtures.json)의 `geometryReference`·`ownerReferences`·`boardAssembly`와 기존 [구역 fixture](../backend/src/test/java/com/surimap/maparea/fixture/BoundaryAreaFixtures.java)·[배정 fixture](../backend/src/test/java/com/surimap/maparea/fixture/SearchAreaAssignmentFixtures.java)를 그대로 유지했다. 과거 별칭을 DB UUID나 현재 기본 bbox에 덮어쓰지 않는다.
- 조회 500ms와 상태·버전 수렴은 옛 목표다. 실제 공간 인덱스 사용·이벤트 실패 시 롤백·쓰기/거부 계측은 별도로 검증한다.
- 과거 요구·도형 규칙·고정 입력·상태 전이: [S2 원문 (`8bffc0b3`)](https://github.com/sonic8-8/suri-map/blob/8bffc0b3cc2859f96ae6fe13ac5a0cb36ebe9dbe/docs/spec/specs/S2.json). 삭제 전 Git 원문과 바이트 일치를 확인했다.
