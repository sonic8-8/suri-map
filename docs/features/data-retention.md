# 사건 데이터 파기와 접근기록

사건 종료 후 데이터 제거, 미전송 기록 보존, 위치 조회 감사 기록을 바꿀 때 확인한다. 옛 S1-3 요구와 2026-09-21 정적 코드 대조 결과다. 보관 기간은 법무 검토가 끝난 운영 기준이 아니며, 이번 문서 정리로 확정하지 않는다.

## 종료와 파기 완료의 구분

- [사건 종료](./incident-lifecycle.md#사건-종료와-개인정보-제거)는 재오픈·새 쓰기를 막는 상태 변경이다. DB 행, 사진 파일, 업무폰의 좌표·사진·패키지까지 지웠다는 뜻은 아니다.
- **기존 요구**: 종료 이벤트로 파기 작업을 한 번 만들고, 경로·사진·로컬 동기화·패키지의 각 처리 결과를 기록한다. 일부 실패 시 성공한 작업은 재사용하고 실패·동기화 대기만 재시도한다. 필수 작업이 모두 성공한 뒤에만 `INCIDENT_PURGED`를 한 번 발행한다.
- **현재 연결 누락**: [종료 Handler](../../backend/src/main/java/com/surimap/retention/purge/IncidentClosedPurgeHandler.java)는 빈으로 등록돼 있으나 이벤트 소비 인터페이스·리스너가 없고, 운영 코드에서 `handle()` 호출도 찾지 못했다. [조율자](../../backend/src/main/java/com/surimap/retention/purge/PurgeCoordinator.java)의 `purgeIncident()`를 호출하는 운영 스케줄러도 찾지 못했다. 테스트의 직접 호출을 자동 파기 구현으로 간주하지 않는다.
- **완료 판정의 차이**: 조율자는 주입된 hook만 순회하고 성공 이력을 재사용한다. 필요한 hook이 모두 등록됐는지는 검사하지 않는다. [사진 어댑터](../../backend/src/main/java/com/surimap/api/service/photo/MarkerPhotoPurgeHookAdapter.java)는 전달 코드일 뿐 빈 등록·실제 삭제 구현이 별도로 필요하다. 경로·로컬 동기화 hook 연결도 확인해야 한다.
- [패키지 파기 어댑터](../../backend/src/main/java/com/surimap/offlinepackage/purge/PackagePurgeHookAdapter.java)의 존재와 실제 단말 파일 제거도 구분한다. [패키지 기록](./offline-package.md#접근-권한과-사건-종료-후-정리), [사진 기록](./marker-photo.md), [미전송 기록](./offline-sync.md#사건-종료-후-남은-기록)을 함께 확인한다.

## 삭제 기한과 남겨야 할 기록

- 옛 문서는 운영 즉시 파기·시연 24시간 유예를 제안했지만 미전송 원본의 최종 처리 조건은 미정이었다. 종료 전 기록 반영과 개인정보 제거가 충돌하지 않도록 서버·앱의 실제 상태를 보고 정한다.
- [옛 Architecture의 보존 표](../architecture.md#과거-설계와-출처)는 실종자 개인정보·사진의 종료 시 제거, 단서·증거 사진과 마커의 별도 보관, 비식별 사건 메타의 이력 보존을 구분했다. 같은 사건의 데이터라는 이유로 수명을 하나로 묶지 않는다. 단서 사진의 보관 범위·기간은 당시에도 운영 전 확인 대상이었으며 법적 적정성을 검증한 정책이 아니다.
- 현재 종료 Handler는 시연 정책을 고정 사용한다. [정책 enum](../../backend/src/main/java/com/surimap/retention/purge/PurgeEnvironmentPolicy.java)이 기한을 계산해도, 그 시각에 실행하는 처리까지 연결된 것은 아니다. 장애·재시작·동시 재시도·완료 이벤트 중복도 별도 확인 대상이다.
- 사용자에게는 개인정보를 제거한 종료 상태만 제공하고 파기 작업의 내부 상세·접근기록을 상황판·Room·오프라인 패키지에 복사하지 않는 요구를 보존한다. 서버 작업의 완료를 모든 업무폰의 삭제 완료로 표시하지 않는다.

## 위치 조회 감사와 운영 로그

- **기존 요구**: 위치 조회의 계정·사건·채널·목적·시각을 서버에서 기록하고, 기록에 실패하면 위치 응답도 내보내지 않는다. 감사 기록을 사건 좌표와 함께 삭제하지 않으며 로그에 실종자 개인정보·좌표 배열을 넣지 않는다.
- [접근기록 AOP](../../backend/src/main/java/com/surimap/retention/purge/RecordLocationAccessAspect.java)는 대상 메서드가 반환한 뒤 인증 정보로 기록하고, 기록 실패를 전파한다. 적용된 진입점은 현재 상황판·웹 마커 조회·앱 지도 revision 조회이며, 모든 위치 조회를 자동으로 포괄하지는 않는다. 직접 Service 호출·실패 응답·실제 반환 전 차단을 각각 확인한다.
- [기록 Service](../../backend/src/main/java/com/surimap/retention/purge/LocationAccessRecorder.java)는 6개월 뒤의 보관 기한을 계산한다. 이는 현재 코드와 옛 제안의 값이지 법적 적정성을 검증한 결과가 아니다. 보관·열람 권한·점검·삭제 정책은 운영 전에 별도로 정한다.
- **운영 로그 미연결**: 현재 등록된 [NoopOperationalLogSink](../../backend/src/main/java/com/surimap/retention/purge/NoopOperationalLogSink.java)는 입력을 버린다. `append()` 호출이나 파기 상태 DB가 있다는 이유로 운영 로그도 보존된다고 판단하지 않는다. 실제 저장소와 실패 기록·대기 시간·재시도 계측이 필요하다.

## 테스트 입력과 원문

- [공용 fixture](../../test-fixtures/common-fixtures.json)의 종료·tombstone 참조와 기존 `retention/purge` Java fixture를 유지한다. 원문에는 추가 고정 fixture ID가 없다. 메모리 저장소·가짜 hook 시험은 실제 DB·파일·단말 파기를 검증하지 않는다.
- 원문 entity의 상태 목록과 상태 전이표가 달랐으므로 한쪽을 자동 채택하지 않는다. due 작업 1분 이내 처리·감사 기록 p95 50ms는 옛 목표이지 실행 결과가 아니다.
- 과거 요구·상태표·실패 시나리오·정책 미정 사항: [S1-3 원문 (`7faaf964`)](https://github.com/sonic8-8/suri-map/blob/7faaf964112131e125f9e432e3306adb27a34f8e/docs/spec/specs/S1-3.json). 삭제 전 Git 원문과 바이트 일치를 확인했다.
