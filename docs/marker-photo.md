# 마커·사진의 복구와 삭제

마커·사진의 재시도, 삭제, 수색 차수 충돌 처리를 바꿀 때 확인할 기록이다. 옛 S5에서 다른 문서로 설명되지 않는 요구와 구현 차이를 남겼다. **기존 요구**는 후속 작업에서 유효성과 범위를 다시 확인할 대상이며, 이번 정리로 새 구현 기준을 확정한 것은 아니다.

현재 구현 설명은 2026-09-18 정적 코드 대조 결과다. 실제 앱의 통신 복구·재시작·사진 삭제를 실행해 검증한 결과와 구분한다.

## 마커를 삭제한 뒤의 사진

- **기존 요구**: 마커 삭제 또는 사건 파기 시 첨부 사진을 `ATTACHED`에서 `DELETED`로 전환한다.
- **현재 차이**: [앱 마커 서비스](../backend/src/main/java/com/surimap/app/service/marker/AppMarkerService.java)와 [웹 마커 서비스](../backend/src/main/java/com/surimap/api/service/marker/MarkerService.java)의 삭제는 마커 상태·버전을 갱신하고 `MARKER_DELETED`를 발행한다. 이 흐름에는 사진 상태 변경·파일 삭제 호출이 없다.
- **확인할 범위**: 마커 숨김, 사진 메타데이터의 삭제 상태, 저장소 파일의 실제 파기를 구분해 정한다. 사건 파기도 [사진 파기 요청 전달 코드](../backend/src/main/java/com/surimap/api/service/photo/MarkerPhotoPurgeHookAdapter.java)만으로 실제 삭제가 연결됐다고 판단하지 않는다. 단서 사진 보관과 실종자 정보 파기는 [데이터별 보존 요구](./data-retention.md#삭제-기한과-남겨야-할-기록)와 대조해 별도로 합의한다.

## 수색 차수가 바뀐 미전송 마커

- **기존 요구**: `op_mismatch`를 받으면 현재 수색 차수를 다시 동기화하고 기록을 재검토 대상으로 남긴다. 과거 차수의 기록을 새 차수로 자동으로 옮기지 않는다.
- **현재 차이**: [HTTP 전송기](../android/app/src/main/java/com/surimap/core/network/NetworkOutboxSender.kt)는 이 응답을 일반 최종 실패로 분류한다. [로컬 재전송 처리](../android/app/src/main/java/com/surimap/core/sync/RoomLocalSyncServices.kt)는 `FAILED_FINAL`과 오류를 저장한다. 차수 불일치에 맞춘 재동기화·재검토 화면까지 연결됐는지는 별도 확인이 필요하다.

## 만료되거나 잘못 업로드된 사진의 재시도

- **기존 요구**: 업로드 주소가 만료되거나 파일 정보가 맞지 않으면 해당 사진을 실패 처리한다. 앱은 기존 object key를 재사용하지 않고 새 업로드 주소로 다시 시도한다.
- **현재 서버 처리**: [PhotoService](../backend/src/main/java/com/surimap/app/service/photo/PhotoService.java)는 대기 상태·버전이 일치하는 사진의 `FAILED` 상태를 별도 트랜잭션으로 저장한 뒤 첨부를 거부한다. 사진 첨부 거부와 실패 상태 저장은 구분한다.
- **확인할 앱 처리**: [업로드 처리](../android/app/src/main/java/com/surimap/feature/marker/data/MarkerPhotoUploadCoordinator.kt)와 [생성 화면](../android/app/src/main/java/com/surimap/ui/SuriMapApp.kt)은 수동 업로드 재시도를 제공한다. 첨부 요청의 `write_conflict` 이후 새 업로드까지 자동으로 이어지는지, 앱을 다시 열어도 미완료 사진을 복구하는지는 검증하지 않았다. 생성 화면의 사진 URI 목록은 현재 메모리에 보관한다.

## 관련 요구와 확인 위치

- 마커 생성 권한·좌표 검증·사진 선업로드·응답 필드는 [API 참고 문서의 마커 항목](./api/api-spec.md#46-marker--photo)에 있다. DB 관계·컬럼 의미는 [DB 참고 문서](./db-design/db-design-readable.md)를 실제 Mapper·migration과 대조한다.
- **기존 사진 복구 요구**: 전송에 실패한 사진은 단말에 임시 보관하고 통신 복구 후 다시 전송한다. 업로드 성공을 확인한 뒤 임시 파일을 삭제한다. 위 앱 구현의 재시도·재시작 복구 한계와 구분한다.
- 오프라인에서 만든 알림은 생성 단말의 전송 대기로 표시하며 수신 알림처럼 보이지 않아야 한다. 서버 저장 성공도 수신자의 알림 확인을 뜻하지 않는다. 지원 요청·발견의 수신 대상·출처·강조와 중복 노출은 [연결 검증 흐름](./tasks/scenario-exit-criteria.md#흐름별-확인-항목)에서 이어서 확인한다.
- 알림 표시 시각은 [마커 기록 시각](../CONTEXT.md#language)을 따른다. 전달 실패·중복·놓친 알림 복구는 [이벤트 전달과 재전송](./event-delivery.md)에서 다룬다.
- 사진 제한은 [PhotoService](../backend/src/main/java/com/surimap/app/service/photo/PhotoService.java)의 현재 값과 [PRD의 미정 기준](./prd.md#현장-기록알림)을 구분한다. S5의 전파 3초·조회 p95 1초도 과거 목표이며, 채택하거나 달성했다고 보지 않는다.
- 장변 2048px·JPEG 85%, EXIF 원본 보존·축소본 제거는 [옛 설계](./architecture.md#과거-설계와-출처)의 제안이다. 실제 앱 변환·원본 보관·개인정보 처리와 대조할 항목이며 이번 정리로 새 기준을 확정하지 않는다.

## 테스트 입력과 원문

- [공용 fixture](../test-fixtures/common-fixtures.json)의 `confirmed.markerAndNotification`을 유지한다. 지원 배정·지원 요청·실종자 발견 알림 3개는 S5의 값과 같고, `markerPhotoAttach`는 사진 첨부 사례의 핵심 7개 필드만 가진다. 전체 복사본은 아니다.
- S5에만 있던 사진 첨부의 추가 기대값, 권한 실패 사례, 전달 단계별 실패 설명은 직접 읽는 실행 코드가 없어 아래 원문에 보존한다. 새 fixture나 로더를 만들지 않으며 기존 공용·Java 테스트 입력과 식별자는 바꾸지 않는다.
- 과거 요구·미정 사항·구현 계획과 전체 fixture: [S5 원문 (`28f8d4df`)](https://github.com/sonic8-8/suri-map/blob/28f8d4dfc13096295142e3b807e1bc869ad268cf/docs/spec/specs/S5.json). 기존 주석·시험 기록의 S5 표기는 이 원문을 가리키며, 현재 구조나 구현 완료를 뜻하지 않는다.
