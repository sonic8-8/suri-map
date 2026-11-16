# 오프라인 패키지의 준비와 갱신

사건 자료의 사전 다운로드, 준비 완료 표시, 재다운로드·파기를 바꿀 때 확인할 기록이다. 옛 S7의 필요한 요구와 구현 차이를 남겼다. **기존 요구**는 후속 작업에서 유효성과 범위를 다시 확인할 대상이며, 이번 정리로 새 구현 기준을 확정한 것은 아니다.

현재 구현 설명은 2026-09-18 정적 코드 대조 결과다. 패키지를 단말로 내려받는 처리와 단말의 기록·설치 상태를 서버로 보내는 [Outbox 재전송](./offline-sync.md)은 구분한다.

## 타일뿐 아니라 사건 자료도 준비

- 필요한 자료의 범위는 [PRD의 오프라인 사용 요구](../prd.md#오프라인-사용동기화)에 남겼고, 타일만 받던 범위를 사건 자료로 넓힌 이유는 [ADR-0019·0028 이력](../adr/README.md#요약으로-남긴-이력)에 보존했다. 전체 수색 구역은 필요하지만, 담당 구역이 아직 없는 초동 수색도 준비할 수 있어야 한다는 요구가 있었다.
- [Backend Repository](../../backend/src/main/java/com/surimap/offlinepackage/service/OfflinePackageRepository.java)는 사건·실종자·수색 차수·구역·초기 마커를 조회해 응답을 조립한다. 원천 조회가 불가능할 때 고정 fixture로 이어지는 분기도 있으므로, 실제 사건 자료로 준비됐는지 별도로 확인한다.
- [Android 설치 처리](../../android/app/src/main/java/com/surimap/core/offline/RoomOfflinePackageWorkerInstaller.kt)는 타일 바이트를 캐시에 저장하고 항목별 진행 상태를 Room에 기록한다. URL 없는 항목은 [항목 설치기](../../android/app/src/main/java/com/surimap/core/offline/OfflinePackageItemInstaller.kt)에서 `SKIPPED`가 된다. 진행 상태만으로 실종자 정보·사진·지도 스타일·글꼴까지 통신 없이 다시 열 수 있다고 판단하지 않는다.

## 다운로드 완료와 서버 반영 완료를 구분

- **기존 요구**: 일부 실패 시 성공 항목은 보존하고 실패 항목만 재시도한다. 다운로드 중 중복 실행을 막고, 실패 항목·진행률·서버 반영 대기를 구분해 표시한다.
- **현재 차이**: Android 설치기는 받은 타일의 SHA-256을 비교한 뒤 저장한다. 그러나 작업자는 새로 읽은 manifest의 항목을 설치기에 전달하고, 설치기는 기존 완료 상태만으로 다운로드를 건너뛰지 않는다. 실패 항목만 재시도한다는 요구와 실제 작업 예약·캐시 사용을 함께 확인한다.
- [앱 준비 상태 판정](../../android/app/src/main/java/com/surimap/feature/offline/data/OfflinePackageStateLoader.kt)은 로컬 설치 상태나 완료 항목을 보고 `READY`를 표시할 수 있다. 설치 상태 보고는 Outbox에 넣으므로 로컬 사용 가능, 서버 ACK, 상황판 배지 반영을 하나의 완료로 취급하지 않는다. 옛 S7의 ACK 전 `READY` 금지와 어느 표시를 뜻하는지부터 맞춘다.

## 자료가 바뀌거나 유효 기간이 지난 경우

- **기존 요구**: 전체 수색 구역·수색 차수·담당 구역이 바뀌면 패키지 갱신 필요 여부를 판단한다. 옛 버전이나 만료된 manifest로 `READY`를 보고하면 거부하고 새 자료를 받도록 한다.
- **현재 구현**: [이벤트 소비자](../../backend/src/main/java/com/surimap/offlinepackage/consumer/OfflinePackageSearchAreaChangedConsumer.java)는 `SEARCH_AREA_CHANGED`를 받는다. [Service](../../backend/src/main/java/com/surimap/offlinepackage/service/OfflinePackageService.java)는 `opId`가 있는 이벤트를 제외한다. [SQL](../../backend/src/main/resources/mapper/offlinepackage/OfflinePackageMapper.xml)은 전체 구역 변경 시 기존 `READY`·`PARTIAL`·`DOWNLOADING`을 `STALE`로 바꾼다. 차수 전환·담당 구역 변경의 갱신 연결은 이 경로와 별도로 확인해야 한다.
- **현재 차이**: Repository의 설치 상태 저장은 현재 manifest ID·버전과 파기 표시를 검사하지만, `expiresAt`과 현재 시각을 비교하는 검사는 없다. 만료 거부 요구와 요청 상태·완료 개수의 검증을 후속 작업에서 대조한다.

## 접근 권한과 사건 종료 후 정리

- **기존 요구**: 배정된 계정과 등록·배정된 업무폰만 사건 패키지에 접근하고, 종료 사건의 개인정보·재다운로드 주소는 노출하지 않는다. [Controller](../../backend/src/main/java/com/surimap/offlinepackage/controller/OfflinePackageController.java)의 일부 헤더 검사만으로 이 요구가 충족됐다고 판단하지 않고 공용 인증·권한 처리와 함께 확인한다.
- **manifest의 사건 권한 확인과 업무폰 확인 (2026-11-16)**: 요청한 사건에 사건 권한이 있는 계정만 `GET /api/incidents/{incidentId}/offline-package/manifest`로 manifest를 받는다. 사건 권한의 기준은 수색 경로 조회와 같다([수색 경로 기능 정의 문서](./search-path.md) 참고). 사건 권한이 없으면 403 `incident_access_denied`로 거절된다. 요청에 `policePhoneId`를 보내면 그 업무폰이 등록된 활성 업무폰이어야 한다. 아니면 403 `police_phone_not_registered`로 거절된다.
  - 업무폰 하나를 여러 계정이 돌려쓴다. 한 계정도 여러 업무폰에 번갈아 로그인할 수 있다. 그래서 작업자는 `police_phone.account_id`와 토큰 계정을 비교하지 않고, 다른 APP 쓰기 API의 `@RequirePolicePhoneRegistered`와 같은 확인(`checkRegistered`)을 쓰도록 고쳤다. 업무폰을 넘겨받은 계정도 그 사건에 사건 권한이 있으면 manifest를 받는다.
  - 남는 한계: manifest의 배정 구역과 `policePhoneContext`는 지금도 업무폰에 연결된 계정(`police_phone.account_id`) 기준으로 조립된다. 그래서 업무폰을 넘겨받은 계정은 요청한 계정이 아니라 연결된 계정의 배정 구역을 받는다. 업무폰과 계정의 관계는 별도 작업에서 다시 정한다.
  - 업무폰 확인으로 알 수 있는 것은 요청에 적힌 업무폰 ID가 서버의 업무폰 목록에 등록된 활성 업무폰인지뿐이다. 요청이 실제로 그 업무폰에서 왔는지는 증명되지 않는다. 그래서 업무폰 확인을 보안 경계로 보지 않는다. 업무폰에 연결된 계정(`police_phone.account_id`)이 비활성이거나 연결된 계정이 없으면 그 업무폰으로 보낸 요청은 등록되지 않은 업무폰으로 거절된다. 기존 `checkRegistered`의 조회 조건이 그렇다. 이 내용은 아래 설치 보고에도 같다. 단말 신뢰를 어느 계층에서 확인할지는 [업무 단말 신뢰 책임 계층 조사](../research/device-trust-layer-research.md)를 바탕으로 따로 정한다.
  - `policePhoneId`를 보내지 않으면 고정 업무폰으로 manifest가 조립되는 동작은 바꾸지 않았다. 웹 오프라인 패키지 상태 화면의 요청이 이 경우다. 지휘 계정은 그 사건에 개인 배정이 없어도 조직 범위 안의 사건이면 웹으로 manifest를 받는다.
  - 거절 조건은 사건 권한, 업무폰, 준비 전(409 `package_manifest_not_ready`), 파기(410 `package_purged`) 순서로 확인된다. 작업자는 사건 권한이 없는 계정이 응답 차이로 패키지의 준비나 파기 상태를 알 수 없게 하려고 이 순서로 정했다.
  - 작업자는 [manifest API 테스트](../../backend/src/test/java/com/surimap/offlinepackage/OfflinePackageManifestApiTest.java)에서 MockMvc와 테스트용 PostGIS로 HTTP 요청을 보내 확인했다. 실서버에서는 확인하지 않았다. 설치 보고의 사건 권한 확인과 업무폰 확인은 아래 항목에 적었다.
- **설치 보고의 사건 권한 확인과 업무폰 확인 (2026-11-16)**: 요청한 사건에 사건 권한이 있는 계정만 `POST /api/incidents/{incidentId}/offline-package/installations`로 설치 상태를 바꿀 수 있다. 사건 권한의 기준은 manifest와 같다. 사건 권한이 없으면 403 `incident_access_denied`로 거절된다. 본문의 `policePhoneId`는 등록된 활성 업무폰이어야 한다. 아니면 403 `police_phone_not_registered`로 거절된다. 업무폰 확인의 기준은 manifest와 같다.
  - 작업자는 지금의 헤더 확인(`Authorization`, APP 채널, `Idempotency-Key`, 헤더와 본문의 업무폰 일치)을 그대로 두었다. 사건 권한 확인과 업무폰 확인은 헤더 확인 다음에 이뤄진다.
  - 같은 `Idempotency-Key`의 저장된 응답이 있어도 사건 권한과 업무폰이 먼저 확인된다. 기존 확인인 manifest 불일치(409 `write_conflict`)와 파기(409 `incident_closed`)도 그 뒤에 이뤄진다. 작업자는 사건 권한이 없는 계정이 저장된 응답이나 응답 차이로 다른 업무폰의 설치 상태와 패키지 상태를 알 수 없게 하려고 이 순서로 정했다.
  - 작업자는 사건이 종료됐지만 패키지가 파기되지 않은 경우의 처리를 바꾸지 않았다. 종료된 사건에도 사건 권한은 같은 기준으로 확인된다. 사건 권한이 있으면 설치 상태는 지금처럼 저장된다.
  - 남는 한계: 사건 권한이 있는 계정은 같은 사건 안의 다른 등록 업무폰의 설치 상태도 보고할 수 있다. 서버에는 지금 어느 계정이 어느 업무폰에 로그인했는지 기록이 없어서, 요청한 계정이 그 업무폰을 쓰고 있는지는 확인되지 않는다. 같은 이유로 같은 사건에 사건 권한이 있는 다른 계정이 같은 `Idempotency-Key`와 본문으로 요청하면 저장된 응답을 받는다. 업무폰에 연결된 계정(`police_phone.account_id`)이 비활성이거나 연결된 계정이 없으면 그 업무폰으로 보낸 요청은 등록되지 않은 업무폰으로 거절된다. 기존 `checkRegistered`의 조회 조건이 그렇기 때문이다.
  - 작업자는 [설치 보고 API 테스트](../../backend/src/test/java/com/surimap/offlinepackage/OfflinePackageInstallationApiTest.java)에서 MockMvc와 테스트용 PostGIS로 HTTP 요청을 보내 응답 상태, 오류 이름, 설치 상태 행을 확인했다. 실서버에서는 확인하지 않았다.
- [서버 파기 처리](../../backend/src/main/java/com/surimap/offlinepackage/purge/OfflinePackagePurgeService.java)는 사건의 manifest 내용과 설치 상태를 지운 상태로 표시한다. 이것은 Android의 Room 자료·다운로드 파일·진행 중인 작업 정리와 별개다. [단말 타일 캐시](../../android/app/src/main/java/com/surimap/core/map/MapLibreTileHttp.kt)는 사건별이 아니라 스타일·좌표별 파일을 사용하므로 공용 지도 캐시와 사건 개인정보의 파기 범위를 구분한다.
- ACK 전 자료 보존과 사건 종료 시 개인정보 파기가 충돌하는 조건은 [오프라인 저장·재전송의 종료 처리](./offline-sync.md#사건-종료-후-남은-기록)와 함께 정한다. 서버 파기 시험만으로 단말의 오프라인 재진입 차단까지 검증됐다고 판단하지 않는다.

## 실제 지도와 시험용 타일을 구분

- 자체 타일·출처 표시의 선택 이유는 [지도 SDK 결정](../adr/0004-maplibre-and-self-hosted-tiles.md), 공개 요청 형식은 [Tiles API 참고](../api/api-spec.md#49-tiles)를 참조한다. 출처 표시의 실제 Android·Web 노출 위치는 옛 S7에서도 미확정이었으므로 화면에서 확인한다.
- [설정](../../backend/src/main/resources/application.yml)의 `tileserver.mode`와 소비 구현을 확인한다. 아래 시험은 합성 타일을 반환하는 `LocalTileService`를 직접 사용하며, 실제 tileserver-gl·OSM 데이터·MapLibre 렌더링을 검증하지 않는다.
- 줌 15~16·최대 8장은 옛 하네스 조건이다. 실제 [범위 계산](../../backend/src/main/java/com/surimap/offlinepackage/service/OverallSearchAreaTileCoverage.java)의 타일 수 제한이나 제품 성능 목표로 옮기지 않는다. 다운로드 용량·manifest 생성 지연·실패 계측은 실제 범위와 타일 서버로 별도 확인한다.

## 테스트 입력과 원문

- [공용 fixture](../../test-fixtures/common-fixtures.json)의 `tileManifest`, `offlinePackageUi.manifestDownloadPending`, `outboxReplay.sc09PackageReplay`는 그대로 사용한다. 다운로드 대기 사례는 S7 원문과 같고, 재전송 사례는 공용 필드 구조로 정리돼 있다.
- 타일 3개의 좌표·주소·체크섬·크기는 기존 [Java fixture](../../backend/src/test/java/com/surimap/offlinepackage/fixture/OfflinePackageManifestFixtureBuilder.java)와 [정확값 검사](../../backend/src/test/java/com/surimap/offlinepackage/TileManifestFixtureExactnessTest.java)에 보존돼 있다. 공용 JSON에는 타일 본문 목록과 실패 주입의 세부 값이 없으며, 원문에만 있는 값은 아래 Git 기록에서 확인한다.
- [패키지·타일 검사](../../backend/src/test/java/com/surimap/offlinepackage/Sc03PackageTileHarnessRedTest.java)의 S7·하네스 문장 대조를 제거하고 공용 데이터 비교·손상 바이트·외부 URL 거부 검증을 유지했다. 남아 있는 `RedTest`·`ContractTest`·`FixtureTest` 등 이름과 테스트 구조는 [이름 정리 후보](../refactoring/codebase-naming-candidates.md)에서 따로 다룬다.
- 과거 요구·미정 사항·구현 계획과 전체 fixture: [S7 원문 (`6d17368c`)](https://github.com/sonic8-8/suri-map/blob/6d17368cb1d396d1b4177e147030657e1f8f7ef3/docs/spec/specs/S7.json). 기존 주석·시험 기록의 S7 표기는 이 원문을 가리킨다.
