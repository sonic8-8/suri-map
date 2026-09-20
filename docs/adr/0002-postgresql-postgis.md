# 공간 데이터에 PostgreSQL·PostGIS 사용

이동 경로, 수색 완료 구역, 마커는 서로 다른 의미의 공간 데이터이며 지도 범위·겹침 조회가 필요했다. PostgreSQL에 PostGIS를 추가해 공간 타입과 SQL 연산을 사용하기로 했다. 대신 확장 운영과 공간 인덱스·쿼리 튜닝을 직접 맡는다.

현재 확인: [PostGIS migration](../../backend/src/main/resources/db/migration/V1__init.sql)과 [구역 Mapper](../../backend/src/main/resources/mapper/maparea/SearchAreaMapper.xml)가 이 선택을 사용한다. 경로는 지금 [좌표를 저장하고 조회 때 도형을 조립](../search-path.md)하므로, 원문의 LineString 설명을 매번 전체 도형을 저장하라는 지침으로 읽지 않는다. 공간 연산 채택과 [미수색 구역 자동 판단 제외](0027-human-search-judgment.md)는 별개다.

기록: 2026-04-21 · [ADR-0002 원문](https://github.com/sonic8-8/suri-map/blob/7f2ea69ad46fa47ce07a6db6c66861fd95731978/docs/adr.md#adr-0002-postgresql--postgis-채택).
