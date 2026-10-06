# 상황판 경로 페이지 조회

2026-10-06 Backend 구현·로컬 검증 대상 계약이다. 2026-10-07 Hetzner Backend·웹 배포, DB 보완·인증 HTTP 검사를 마쳤다. 실제 브라우저에서는 부분 경로 표시를 확인했지만 전체 이력 로딩을 완료하지 못한 채 관찰을 종료했다. 새 좌표 반영·부하는 미검증이다. [서버 전환·브라우저 부분 결과](../test-results/search-path-pages-deployment-20261007.md)를 참고한다.
동작의 선택 이유는 [상황판 기능 문서](../features/situation-board.md#경로-변경분-조회-설계)에 둔다.
아래 UUID는 설명용이며 실제 사건·차수·경로 ID로 요청해야 한다.

## 공통

- 웹 인증과 사건 접근 권한이 필요하다. `X-Client-Channel: WEB`을 사용한다.
- 두 API 모두 읽기용 POST다. 경로 쓰기·멱등성 예약·SSE 발행을 하지 않는다. 성공한 위치 조회는 감사 기록을 남긴다.
- 매 요청에 `opIds`, `paths`를 보낸다. `opIds: []`는 조회 대상 없음이다.
- `paths`에는 현재 범위에서 이미 알고 있는 모든 경로의 ID와 해당 조회의 진행만 보낸다. 좌표를 다시 보내지 않는다.
- 경로·구간·진행 버전은 10진 정수 문자열, GPS 순번은 JSON 숫자다.
- 응답을 반영한 뒤 진행 정보를 갱신한다. 응답에 없는 경로는 삭제하지 않는다.
- 인증·종료·파기 제한은 빈 조회에도 적용한다.

## 최초 구간과 과거 이력

`POST /api/incidents/{incidentId}/board/search-paths/segments/query`

최초 요청:

```json
{
  "opIds": ["65000000-0000-0000-0000-000000002621"],
  "paths": [],
  "nextSearchPathId": null
}
```

응답 예시:

```json
{
  "paths": [{
    "id": "ffffffff-ffff-ffff-ffff-ffffffffffff",
    "accountId": "62000000-0000-0000-0000-000000002621",
    "opId": "65000000-0000-0000-0000-000000002621",
    "status": "RECORDING",
    "version": "3",
    "baselineVersion": "3",
    "segments": [{
      "id": "00000000-0000-0000-0000-000000000002",
      "version": "1",
      "startPointOrder": 2,
      "endPointOrder": 3,
      "movementType": "FOOT",
      "geometry": {"type": "LineString", "coordinates": [[126.9002, 35.1], [126.9003, 35.1]]},
      "startedAt": "2026-04-28T00:00:10Z",
      "endedAt": "2026-04-28T00:00:15Z"
    }],
    "segmentsProgress": {"beforeStartPointOrder": 2, "completed": false}
  }],
  "nextSearchPathId": "ffffffff-ffff-ffff-ffff-ffffffffffff",
  "hasMore": true
}
```

이어받기 요청의 경로 항목:

```json
{
  "id": "ffffffff-ffff-ffff-ffff-ffffffffffff",
  "segmentsProgress": {"beforeStartPointOrder": 2, "completed": false}
}
```

아직 구간을 받지 않은 알려진 경로는 `segmentsProgress: null`이다.
`baselineVersion`은 첫 구간 응답에서만 제공하고 이후 이력 응답에서는 생략한다.
웹은 최초 값을 계속 보관하며, 이력 다음 페이지의 경로 버전으로 바꾸지 않는다.
기준 버전·구간·GPS는 한 요청의 REPEATABLE READ 트랜잭션에서 읽는다.

원본 GPS가 하나인 구간은 `startPointOrder`와 `endPointOrder`가 같다.
이 경우 표시용 `geometry.coordinates`에 같은 좌표를 두 번 담아 LineString으로 표현한다.
DB 원본·GPS 순번·구간 버전은 바꾸지 않으며, 좌표 응답 한도는 원본 GPS 수로 계산한다.
이 표현은 두 신규 조회 API에만 적용하고 기존 공용 경로 API는 유지한다.

## 변경분

`POST /api/incidents/{incidentId}/board/search-paths/changes/query`

첫 변경분 요청:

```json
{
  "opIds": ["65000000-0000-0000-0000-000000002621"],
  "paths": [{
    "id": "ffffffff-ffff-ffff-ffff-ffffffffffff",
    "baselineVersion": "3",
    "changesProgress": null
  }],
  "nextSearchPathId": null
}
```

응답의 경로 기본 정보·구간은 이력 조회와 같은 형태다.
`segmentsProgress` 대신 아래 진행 정보를 반환한다.
예를 들어 기준 3 이후 상한 5까지 아직 일부만 받았다면:

```json
{
  "baselineVersion": "3",
  "changesProgress": {
    "appliedVersion": null,
    "targetVersion": "5",
    "beforeStartPointOrder": 8,
    "completed": false
  }
}
```

이 회차를 모두 받으면 `appliedVersion: "5"`, `completed: true`가 된다.
다음 요청에도 완료한 객체를 그대로 보내며 서버가 새 회차를 연다.
변경 구간이 없어도 경로 상태·완료 버전이 갱신될 수 있다.

변경분에서 새 경로를 발견하면 `segments: []`, `baselineVersion: null`,
`changesProgress: null`과 기본 정보를 반환한다.
이 경로는 이력 API로 첫 구간을 받은 뒤 변경 추적을 시작한다.
구간 없음과 최초 구간 미수신을 혼동하지 않는다.

## 완료·오류

- 정상 빈 결과: `{"paths":[],"nextSearchPathId":null,"hasMore":false}`, HTTP 200.
- `hasMore`는 현재 API의 남은 처리 여부다. 아직 반환하지 않은 경로도 고려한다.
- 다른 API의 진행 정보나 응답에 없는 경로의 진행을 덮어쓰지 않는다.
- HTTP 400 `invalid_search_path_query`: 형식·중복·범위·버전 모순·요청 한도 오류. 전체 거부하며 자동 초기화하지 않는다.
- HTTP 403 `incident_access_denied` 또는 `channel_not_allowed`: 접근 거부.
- HTTP 409 `incident_closed`: 종료 또는 파기 완료로 조회 불가.
- HTTP 503 `search_path_query_not_ready`: 한도 미설정 또는 구간 순번/변경 버전/원본 보완 미완료.
- HTTP 422 `search_path_segment_too_large`: 구간 하나가 전체 좌표 응답 한도보다 큼. 분할·누락·빈 페이지 반복으로 숨기지 않는다.
- 오류 본문은 `{"error":"코드"}`다. 다른 사건의 경로 존재 여부나 원본 좌표를 오류에 담지 않는다.

## 시험 한도와 활성화

`surimap.board.search-path.enabled`의 기본은 비활성이다.
다음 속성은 기본값 0(미설정)이다. 2026-10-07 Hetzner Backend에 아래 시험값과 활성화 설정을 적용해 인증 HTTP를 검증했다. 웹 읽기 검사는 부분 결과이며 새 쓰기 Smoke·부하는 아직 하지 않았다.

| 속성 접미사 | 시험값 | 역할 |
|---|---|---|
| `max-paths` | 80 | 응답에 담을 경로 수 |
| `max-segments` | 80 | 응답 전체 구간 수 |
| `max-coordinates` | 480 | 응답 전체 원본 좌표 수. 정상 앱 묶음 최대 120개 이상을 수용해야 함 |
| `segments-per-path` | 1 | 한 경로의 한 차례에 읽을 구간 수 |
| `max-known-paths` | 1,000 | 요청의 경로/차수 목록 및 조회 범위의 경로 수 상한 |

모두 `surimap.board.search-path.` 아래에 둔다.
한 응답에서는 순환 순서로 각 대상 경로에 한 차례씩 기회를 주고, 미완료 경로는 다음 요청에 이어받는다.
작은 시험 한도는 페이지 경계를 검증하기 위한 값이지 운영 권장값이 아니다.
기존 자료 전수 보완·검증과 측정 결과 검토 전에는 서버에서 활성화하지 않는다.

2026-10-07에 `max-known-paths`도 로컬 시험과 같은 1,000으로 선택했다. 이는 업무폰·VU 수나 응답당 경로 수가 아니다. 선택한 차수의 전체 경로가 상한을 넘으면 HTTP 400으로 거부하며 일부만 반환하지 않는다. 1,000개 경로의 성능을 검증했다는 뜻은 아니며, 실행 전에 실제 대상 수를 다시 확인한다. HTTP 요청 제한시간은 아직 미정이다.

[Runtime Compose](../../infra/docker/docker-compose.runtime.yml)는 [환경 변수 예시](../../infra/docker/.env.runtime.example)의 `SURIMAP_BOARD_SEARCHPATH_*`를 Backend에 전달한다. 예시는 시험값을 담되 활성화는 `false`이며, 환경 변수 없이 실행하면 비활성·한도 0을 유지한다. 이 설정 연결은 서버 전환 완료를 뜻하지 않는다.

## 서버 전환 전 확인

새 Backend 시작 시 Flyway가 `V20261006_001`부터 `003`까지 실행한다. API 활성화 설정은 migration을 막지 않는다. 다음은 실행할 순서이며 완료 기록이 아니다.

1. 실제 Jenkins inline 설정·진행 중 빌드를 확인하고 자동 배포가 전환 절차를 앞지르지 않게 통제한다. 저장소의 [Jenkinsfile 사본](../../infra/ci/hetzner.Jenkinsfile)만 수정해서는 실제 작업이 바뀌지 않는다.
2. 이전 이미지·설정을 보존하고 전체 경로 자료를 사전 검사한다. 기존 Backend·시험 writer를 중단하고 진행 중 쓰기가 끝난 뒤 DB를 백업한다. 백업 파일 생성과 복원 가능 검증은 구분한다.
3. 새 Backend를 시작해 migration 성공과 원본 좌표·구간·버전의 보존을 확인한다. API 설정을 적용하고 인증된 실제 HTTP 조회를 검증한 뒤 새 웹을 공개하고 쓰기를 재개한다. Compose의 `up -d` 성공만으로 이 검증을 대신하지 않는다.
4. 실제 브라우저에서 첫 경로 표시·과거 이력 완료·새 좌표 반영·재접속을 확인한다. 그 결과로 제한시간과 후속 부하 조건을 정한다.

보완 실패 시 새 웹 공개·쓰기 재개를 중단한다. migration 하나의 실패가 앞서 성공한 migration까지 취소한다는 보장은 없다. 성공 후 구 Backend로만 되돌려 쓰기를 재개하면 새 구간 추적값이 누락되거나 보정 버전이 오래된 값으로 남을 수 있다. 되돌릴 때도 쓰기를 멈춘 상태에서 DB·앱 버전의 일치와 복구 범위를 확인하며, 백업을 무조건 덮어써 이후 기록을 잃지 않도록 한다.
