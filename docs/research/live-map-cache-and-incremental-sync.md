# 실시간 지도 캐시와 증분 동기화: CalTopo·ArcGIS·Traccar 조사

조사일: 2026-10-02. 공식 제품 문서·API와 공급자 공개 소스만 대조했다. 외부 서비스의 공개 동작과 수리맵에 적용할 수 있다는 추론을 구분한다. 도입 결정·제품 구현·성능 검증 완료 기록이 아니다. 기존 [오프라인 동기화·복구 조사](offline-sync-recovery-benchmarks.md)는 복구 시간과 최신성 목표를 다루며, 이 문서는 지도 조회·갱신 단위를 다룬다.

## 결론

공개 근거는 **전체 화면 캐시 하나**보다 **현재 위치·과거 이력·변경 객체를 구분하는 방식**을 보여 준다. CalTopo에는 변경 객체 조회 API가 있고, ArcGIS에는 현재 위치와 경로 이력의 별도 레이어가 있으며, Traccar에는 위치별 메모리 갱신과 실시간 메시지가 있다. 세 서비스가 같은 캐시·전송 구조를 사용한다는 뜻은 아니다. [CalTopo Team API](https://training.caltopo.com/all_users/team-accounts/teamapi), [ArcGIS Track layers](https://doc.arcgis.com/en/arcgis-online/reference/use-tracks.htm), [Traccar Architecture](https://www.traccar.org/architecture/)

| 대상 | 직접 확인한 공개 근거 | 이 근거만으로 말할 수 없는 것 |
| --- | --- | --- |
| CalTopo | 지도 객체를 서버 시각 이후 변경분으로 조회하는 API | 실제 웹의 모든 요청 경로, 좌표 하나씩 보내는지, Redis·Kafka 사용 여부, 내부 SQL·캐시 적중률 |
| ArcGIS Field Maps / Online | 최신 위치·원본 점·생성된 경로 선을 별도 레이어로 제공 | Track Viewer가 모든 갱신에 어떤 SQL·내부 캐시를 쓰는지 |
| ArcGIS StreamLayer | 관측값 스트림을 받아 화면 갱신, 보존량 제한·재연결 설정 | 재연결만으로 과거의 누락 관측값까지 복원된다는 보장 |
| Traccar | 초기 최신 위치 조회, 후속 단일 위치 메시지, 서버·브라우저의 위치별 상태 유지 | 실시간 화면의 짧은 꼬리가 끊김 없는 전체 경로 이력을 보장한다는 주장 |

표의 상세 근거와 한계는 아래 절에서 각각 연결한다. 수리맵에 대한 추론은 마지막 절에 별도로 적었다.

## 1. CalTopo: 객체 증분 조회와 오프라인 기록은 공식 기능이다

### 공개 API에서 확인되는 조회 단위

Team API의 `GET /api/v1/map/{map_id}/since/{timestamp}`는 `0`이면 현재 전체 데이터를, 이후에는 응답에 담긴 서버 timestamp를 넣어 변경 객체를 조회하도록 정의한다. 잦은 호출에는 증분 조회를 사용하라고 명시한다. 응답은 GeoJSON FeatureCollection이며, 삭제 여부가 불확실하면 객체 ID 목록도 제공해 클라이언트가 삭제된 객체를 대조하게 한다. 계정 객체 조회도 같은 `since` 방식을 제공한다. [Supported API for Teams — Retrieve Map Data / Get Account Data](https://training.caltopo.com/all_users/team-accounts/teamapi)

이 계약은 **지도 전체가 아닌 변경 객체를 받을 수 있다**는 근거다. 변경된 긴 선 객체 안에서 좌표 배열 전체를 보내는지, 새 좌표만 보내는지까지 설명하지 않는다. 해당 Team API가 CalTopo 자체 웹·앱의 모든 동기화 경로라는 주장도 하지 않는다. 확인한 API 문서에는 DB 조회 방식이나 서버 캐시 제품·키·TTL이 없어, “CalTopo는 Redis로 전체 트랙 조회를 피한다” 또는 “매번 모든 트랙을 DB에서 읽는다”는 결론은 낼 수 없다. [같은 API 계약의 공개 범위](https://training.caltopo.com/all_users/team-accounts/teamapi)

### 현재 위치 공유와 완전한 경로 기록은 다르다

- **Recorded mobile track**: 통신이 없으면 단말에 저장하고 연결이 복구되면 자동 동기화한다. 저장한 지도에 기록할 경우 통신 중 다른 지도 열람자가 경로를 볼 수 있다. [Recording Tracks](https://training.caltopo.com/all_users/mobile/tracks)
- **Share Location**: 통신에 의존하고 이동 이력을 단말에 저장하지 않는다. 지도에 기록하더라도 통신 단절 전후 위치가 직선으로 연결될 수 있어, 완전한 이동 이력이 필요하면 mobile track 기록을 사용하라고 구분한다. [Live Team Tracking](https://training.caltopo.com/all_users/team-accounts/team-tracking)
- **배경 지도 오프라인 사용**: Offline Mode는 지도 레이어를 다운로드 저장소에서만 읽게 한다. 통신 자체를 끄는 기능은 아니므로 통신 가능 시 live track·지도 객체 동기화는 계속할 수 있다. 배경 타일의 로컬 보관과 실시간 객체 동기화는 별개 기능이다. [New Feature: Offline Mode](https://blog.caltopo.com/2025/08/25/new-feature-offline-mode/)

이 사례는 “최신 위치가 다시 보인다”와 “단절 중 전체 경로가 복구됐다”가 서로 다른 검증 항목이라는 근거다. 자동 동기화 설명은 수십만 좌표의 처리 시간 보장이 아니다.

## 2. ArcGIS: 최신 위치·원본 점·표시용 선을 분리한다

### Field Maps / ArcGIS Online의 저장·조회 모델

Location sharing layer는 세 sublayer로 나뉜다. 원본 track points는 위치 기록별 행이고, last known locations는 사용자당 최신 위치 한 행이다. Track lines는 서버가 업로드된 점으로 생성하는 선이며, 약 10분마다 갱신되고 선 하나가 나타내는 시간은 최대 1시간이다. `location_timestamp`는 단말 측정 시각, `last_edited_date`는 서버 변경 시각으로 구분된다. [Track layers](https://doc.arcgis.com/en/arcgis-online/reference/use-tracks.htm)

이는 최신 위치 표시와 누적 이력 선 생성을 독립적으로 다루는 공개 사례다. “선 하나 최대 1시간”은 조회 대상 전체 이력을 최근 1시간으로 버린다는 뜻이 아니다. 또한 이 자료만으로 내부 Redis 캐시나 매번 실행하는 SQL을 확인할 수 없다.

Field Maps의 문서상 기본 업로드 주기는 충전 중 60초, 그 외에는 10분이며, 최신 위치 갱신은 별도로 기본 60초다. 오프라인 위치는 업로드할 수 있을 때까지 단말에 보관하고 통신 복구 시 자동 업로드한다. 이 숫자는 업로드·갱신 주기이지 전체 backlog 처리 완료 시간이나 수리맵에 적용할 목표값이 아니다. [Share location — Upload tracks](https://doc.arcgis.com/en/field-maps/ios/use-maps/track.htm)

### 실시간 StreamLayer는 또 다른 경로다

ArcGIS Maps SDK의 StreamLayer는 WebSocket 관측값을 받아 화면을 갱신한다. 문서는 일반 FeatureLayer의 명시적 조회와 이를 구분하며, 구독 전에 흘러간 feature를 자동으로 볼 수 없다고 설명한다. 재연결 횟수·간격 설정, 클라이언트 적용 속도 제한, `purgeOptions`를 통한 표시 관측값 제한도 제공한다. [StreamLayer](https://developers.arcgis.com/javascript/latest/references/core/layers/StreamLayer/)

따라서 StreamLayer는 변경 전송과 브라우저 보존량 제한의 사례이지, Field Maps Track Viewer가 내부적으로 StreamLayer를 쓴다는 근거가 아니다. 스트림 재연결과 과거 이력 보충도 별도로 설계해야 한다는 시사점이 있다.

## 3. Traccar: 캐시와 변경 전송을 함께 확인할 수 있는 공개 구현

소스는 서버 `2dbb05a6b2dbccad508cd095096df33d43e5d17a`, 웹 `d597947ad631d1f69183cdc5bef7102fe2d9d96c`에 고정했다. 공급자의 현재 공개 코드에 대한 정적 확인이며 실제 설치·부하 시험은 하지 않았다.

### 서버의 캐시는 전체 상황판 응답 캐시가 아니다

공식 구조 문서는 `CacheManager`가 활성 단말·최근 위치·연관 객체를 메모리에 유지하며 관련 API 변경 때 객체·권한을 무효화한다고 설명한다. 다중 인스턴스의 선택적 Redis/multicast broadcast는 위치 갱신·알림·무효화 전달용이며 공유 DB를 대체하지 않는다. 단일 인스턴스에 broadcast backend는 필수가 아니다. [Traccar Architecture — Storage and Cache](https://www.traccar.org/architecture/)

실제 `CacheManager`는 단말 ID별 위치 deque를 갖고 수신 위치를 추가·정리한다. 설정에 따라 최신 위치 하나 또는 trip 처리에 필요한 최근 범위를 유지한다. 최초 단말 등록 시 DB에서 현재 위치를 읽고, 관련 설정이 켜져 있으면 필요한 최근 구간을 추가로 읽는다. 즉 초기 로드 비용은 존재하지만 일반 갱신이 매번 전체 경로를 재조립하는 방식은 아니다. [고정 소스: CacheManager](https://github.com/traccar/traccar/blob/2dbb05a6b2dbccad508cd095096df33d43e5d17a/src/main/java/org/traccar/session/cache/CacheManager.java)

`PostProcessHandler`는 새 위치가 기존 최신 위치보다 오래되지 않았을 때 DB의 현재 위치 참조와 메모리 위치를 갱신하고 연결 관리자에게 알린다. 따라서 뒤늦게 도착한 옛 위치로 최신 위치가 후퇴하지 않도록 검사한다. 이 코드만으로 위치 이력 저장·DB 오류 복구 전체의 정합성을 보장한다는 해석은 하지 않는다. [고정 소스: PostProcessHandler](https://github.com/traccar/traccar/blob/2dbb05a6b2dbccad508cd095096df33d43e5d17a/src/main/java/org/traccar/handler/PostProcessHandler.java)

### 초기 snapshot 뒤 위치 변경을 보내고, 브라우저도 위치별로 반영한다

WebSocket 연결 시 `AsyncSocket`은 사용자가 볼 수 있는 최신 위치들을 조회해 보낸다. 이후 `onUpdatePosition`은 위치 한 건을 담은 `positions` 메시지를 보낸다. 전체 이력 배열을 매번 전송하는 계약이 아니다. [고정 소스: AsyncSocket](https://github.com/traccar/traccar/blob/2dbb05a6b2dbccad508cd095096df33d43e5d17a/src/main/java/org/traccar/api/AsyncSocket.java)

브라우저는 수신 위치를 `positions[position.deviceId]`에 반영한다. live route 표시를 켜면 단말별 꼬리를 유지하되 설정된 길이로 자르며 기본값은 10개다. 이 짧은 표시용 이력은 서버의 전체 이동 경로와 다르다. [고정 소스: session store](https://github.com/traccar/traccar-web/blob/d597947ad631d1f69183cdc5bef7102fe2d9d96c/src/store/session.js)

연결이 닫히면 웹은 `/api/devices`, `/api/positions`를 조회하고 일반 재시도 타이머를 설정한다. 온라인 복귀·탭 재표시에도 연결 상태를 확인한다. `/api/positions`의 파라미터 없는 요청은 최신 위치를 반환하며, 특정 단말과 `from`·`to`를 준 요청은 해당 이력 범위를 조회한다. [고정 소스: SocketController](https://github.com/traccar/traccar-web/blob/d597947ad631d1f69183cdc5bef7102fe2d9d96c/src/SocketController.jsx), [고정 소스: PositionResource](https://github.com/traccar/traccar/blob/2dbb05a6b2dbccad508cd095096df33d43e5d17a/src/main/java/org/traccar/api/resource/PositionResource.java)

따라서 이 실시간 화면의 재연결은 최신 상태 회복의 근거다. 단절 동안 발생한 모든 위치를 live route에 빠짐없이 재생한다는 근거는 아니다. 완전한 경로가 필요한 수리맵에서 이 짧은 꼬리 제한을 그대로 복사하면 요구를 잃을 수 있다.

## 4. 수리맵에 가져올 판단 기준 — 외부 사례에서의 추론

다음은 외부 제품의 내부 구현을 단정한 사실이 아니라, 위 공개 계약·코드를 비교해 얻은 설계 검토 기준이다.

1. **캐시와 증분 전송은 대체재가 아니다.** Traccar처럼 서버 캐시를 유지하면서도 변경 위치를 전송할 수 있다. 서버 계산만 캐시해도 큰 응답의 반복 전송·브라우저 전체 교체는 남을 수 있다.
2. **경계는 현재 위치·변경 중 구간·누적 이력으로 나누어 본다.** ArcGIS의 별도 레이어와 시간별 선은 이 구분의 공개 사례다. 수리맵에서 어떤 경로·구간이 실제로 불변인지, 늦은 업로드로 과거 구간이 바뀌는지는 별도로 확인해야 한다.
3. **객체 증분과 좌표 증분을 구분한다.** CalTopo의 `since`처럼 변경 객체만 받아도 그 객체가 거대한 전체 경로라면 비용이 계속 증가할 수 있다. 응답 단위·객체 크기·직렬화량까지 측정해야 한다.
4. **최초 조회·일상 갱신·재접속 보충을 각각 검증한다.** 최신 위치 snapshot을 받는 것과 누락 이력을 복구하는 것은 다르다. 삭제·접근 권한 상실·사건 종료도 클라이언트에 남은 데이터를 제거하는 경로가 필요하다.
5. **캐시 도입 여부보다 무효화·갱신 범위를 먼저 정한다.** 전체 응답 캐시를 쓰기마다 버리는 안, 변경 경로만 다시 만드는 안, 작은 구간별 결과를 재사용하는 안은 유지 비용과 cold rebuild 비용이 다르다. 이 조사에는 수리맵 부하에서 어느 안이 충분한지 입증하는 성능 결과가 없다.

### 캐시·지도 라이브러리의 공식 지침과 대조

Azure의 Cache-Aside 지침은 miss 때 원본 저장소를 읽고 캐시를 채우며, 쓰기 뒤에는 관련 항목을 무효화하는 흐름을 설명한다. 자동 정합성을 보장하지 않고, 낮은 적중률에서는 조회·적재 비용이 이득을 넘을 수 있다. 원본 갱신보다 먼저 무효화하면 오래된 값이 다시 적재되는 경쟁도 경고한다. 로컬 메모리 캐시 역시 가능한 구현이므로 캐시 채택이 곧 Redis 채택은 아니다. [Cache-Aside pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/cache-aside)

MapLibre의 대용량 GeoJSON 지침은 많이 바뀌지 않는 데이터와 실시간 데이터를 별도 chunk/source로 나누어, live source만 갱신하는 방식을 제시한다. 이것은 브라우저 갱신 비용을 줄이는 근거이며 서버 DB 비용까지 자동으로 줄여 주는 기능은 아니다. [MapLibre — Data chunking](https://maplibre.org/maplibre-gl-js/docs/guides/large-data/#data-chunking)

수리맵 웹에는 이미 TanStack Query 캐시가 있다. 다만 `refreshIncidentBoards`는 조회를 stale로 표시한 뒤 활성 board query를 다시 실행하고, 조회 함수는 전체 board API를 호출한다. 따라서 단순히 “웹 캐시를 추가한다”보다 **기존 상태 중 무엇을 유지하고 무엇만 갱신할지**가 검토 대상이다. [현재 incidentBoardApi](../../frontend/src/features/board/api/incidentBoardApi.ts)

서버 캐시도 배제할 이유는 없다. 다만 전체 응답 캐시·경로별 버전 캐시·구간별 캐시와 증분 응답을 비교할 때는 cold/warm, 상황판 한 개/여러 개, 지속 쓰기 중 적중률·갱신 비용, 동시 miss의 중복 재구성, 메모리 상한, 응답 bytes·지도 반영 시간을 함께 측정해야 한다. “초당 31건의 쓰기마다 전체 응답을 무효화하면 재생성이 잦을 수 있다”는 가설이지, 이 조사에서 측정한 적중률은 아니다. DB를 원본으로 유지하고 늦은 업로드·수정·개인정보 파기·접근 권한 변경 뒤 오래된 캐시가 노출되지 않는지도 검증해야 한다. 이는 비교 시험의 제안이며 Redis 도입이나 특정 캐시 경계를 확정하는 결론은 아니다.

## 확인 범위와 남은 불확실성

- 공식 웹 문서·API를 열어 읽고 Traccar의 서버·웹 소스를 고정 커밋에서 대조했다. 비공개 계정·실제 위치 데이터·서비스 내부 DB에는 접근하지 않았다.
- CalTopo Team API의 문서상 증분 계약을 확인했지만, 인증이 필요한 API의 실제 응답 크기·GPS 객체 표현은 실행 확인하지 않았다.
- ArcGIS의 문서상 레이어·주기와 StreamLayer 기능을 확인했지만, Track Viewer의 비공개 서버 캐시·조회 계획을 추정하지 않았다.
- 외부 서비스의 설치·성능·복구 시험은 수행하지 않았다. 공개된 주기·구조는 수리맵의 처리량·최신성 보장이 아니다.
