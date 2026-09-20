# Persistence를 MyBatis로 통일

PostGIS 쿼리와 멱등성·이벤트 저장의 SQL을 직접 제어해야 하므로, JPA·Hibernate Spatial 또는 두 방식의 혼용 대신 MyBatis 하나로 통일했다. 작은 팀이 ORM과 별도 SQL의 두 관례를 함께 관리하지 않도록 한 선택이다. 대신 명시적 Mapper·매핑·트랜잭션과 migration을 직접 관리한다.

현재 확인: [Backend 빌드](../../backend/build.gradle)와 [Mapper](../../backend/src/main/resources/mapper/)가 MyBatis를 사용한다. 이 선택은 JPA로 공간 데이터를 처리할 수 없다는 뜻이 아니며, Mapper 사용만으로 쿼리 성능이 보장되지도 않는다. 현재 변경 규칙은 [공통 Persistence 규칙](../../AGENTS.md#persistence)을 따른다.

기록: 2026-04-30 · [ADR-0033 원문](https://github.com/sonic8-8/suri-map/blob/7f2ea69ad46fa47ce07a6db6c66861fd95731978/docs/adr.md#adr-0033-persistence-layer로-mybatis-단일-채택).
