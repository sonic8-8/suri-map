# 오프라인에서 UUID를 먼저 발급

앱이 서버에 연결되지 않아도 경로·마커를 만들고 후속 요청에서 참조할 수 있어야 한다. 서버의 자동 증가 ID를 받은 뒤 로컬 임시 ID를 변환하는 대신, 앱이 UUID를 먼저 발급해 로컬 저장·전송·서버 응답에서 같은 대상을 가리키기로 했다. 리소스 ID와 요청 멱등키, 외부 원천 키는 역할이 다르며 UUID 인덱스의 비용도 별도로 고려한다.

현재 확인: [경로 기록기](../../android/app/src/main/java/com/surimap/feature/search/data/SearchPathLocalRecorder.kt)와 [마커 기록기](../../android/app/src/main/java/com/surimap/feature/marker/data/MarkerLocalRecorder.kt)가 UUID와 요청 키를 만들어 전송한다. UUID 형식만 확인했다고 권한·중복 반영·동시 요청까지 검증된 것은 아니다. 재전송 검증 범위는 [오프라인 기록](../features/offline-sync.md), 외부 키와 내부 키의 관계는 [사건 가져오기](../features/incident-lifecycle.md)에서 확인한다.

기록: 2026-04-27 · [ADR-0036 원문](https://github.com/sonic8-8/suri-map/blob/7f2ea69ad46fa47ce07a6db6c66861fd95731978/docs/adr.md#adr-0036-서버android-동기화-경계-uuid-식별자-전략).
