# MapLibre와 자체 타일 서버 사용

Android 오프라인 지도가 필요했지만, 당시 Mapbox의 이용 조건·쿼터 확인이 MVP 일정의 위험으로 판단됐다. Android와 Web을 MapLibre로 통일하고 OSM·OpenMapTiles 기반 타일을 자체 배포해 스타일을 공유하기로 했다. 대신 지도 데이터 준비·갱신, 타일 서버 운영, 출처 표시를 직접 관리한다.

현재 확인: [타일 요청 흐름과 배포 설정](../architecture.md) 및 [실제 지도·시험용 타일의 차이](../offline-package.md#실제-지도와-시험용-타일을-구분)를 따른다. 산별 프리셋은 ADR-0024에서 사건별 범위로 바뀌었다. 원문의 ‘라이선스 리스크 제거’는 현재 검증 사실로 옮기지 않으며, 데이터별 이용 조건과 실제 출처 표시는 확인 대상이다.

기록: 2026-04-21 초안, 04-22 개정 · [ADR-0004 원문](https://github.com/sonic8-8/suri-map/blob/7f2ea69ad46fa47ce07a6db6c66861fd95731978/docs/adr.md#adr-0004-지도-sdk-maplibre-통일--자체-타일-서버). 사건별 지도 범위로 바뀐 이력은 [ADR 인덱스](README.md#요약으로-남긴-이력)에 남겼다.
