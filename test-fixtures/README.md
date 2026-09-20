# 공용 테스트 입력

Backend와 Android 테스트가 함께 읽는 고정 데이터다. 제품의 현재 동작이나 구현 완료 여부를 보장하는 명세가 아니다. 값 변경 전에는 소비 테스트와 실제 API·DB 타입을 함께 확인한다.

## 사용

| 파일 | 역할 |
|---|---|
| [common-fixtures.json](common-fixtures.json) | `confirmed`: 입력·기대값, `catalogIndex`: 대표 입력과 사용 시나리오, `confirmedIdIndex`: ID와 실제 JSON 경로, `usageRules.loadOrder`: 기존 로드 순서 |
| [pending-confirmation.json](pending-confirmation.json) | 확정하지 않은 입력과 공용 데이터에 섞이지 않게 하는 제한. 현재 항목은 비어 있다. |
| [preflight_common_fixtures.py](preflight_common_fixtures.py) | ID 색인·중복·참조 관계, 필수 데이터·상태·오류값, 상황판 슬롯, mock 112 seed 일치 검사 |

```bash
python3 test-fixtures/preflight_common_fixtures.py
```

Backend 로더는 저장소의 JSON을 읽는다. Android는 `app/build.gradle.kts`에서 이 디렉터리를 테스트 리소스로 등록하고 `AndroidHarnessFixtureCatalog`로 읽는다. 데이터 초기화·종료·파기 테스트는 각 테스트 저장소에만 적용하며 원본 JSON을 지우지 않는다.

검사 통과는 입력의 일관성 확인이다. 실제 DB·API·SSE·브라우저·Android 단말의 동작 검증은 별도로 실행한다. Markdown 표·문구가 JSON과 같은지는 검사하지 않는다.

## 값을 읽을 때 주의할 점

- 합성 데이터만 사용한다. 실제 개인정보로 교체하지 않는다.
- DB PK/FK와 공개 식별자는 UUID, 사람이 읽는 별칭은 `*Alias`·`*Code`로 구분한다. mock 112의 `accountCode`와 내부 `account.id`는 다르다. 상세 매핑은 `incidentSeed`·`accountAliases`와 기존 로더에서 확인한다.
- `SC-*`, `S*`, `L*`는 과거 시나리오·Spec·Lane 분류다. 현재 작업 담당자나 승인 규칙이 아니다. 데이터 식별자를 일괄 재명명하지 않는다.
- `sourceSpecEndpoint`는 과거 경로 비교값이고 `canonicalApiEndpoint`는 HTTP 비교값이다. 실제 호출 전에는 Controller와 대조한다.
- `boardAssembly.latestEventId`와 `opTransition`·`searchHistorySummary.expectedS4Events`의 이벤트 기대값은 용도가 다르다. 하나로 통일하지 않는다.
- OP1의 `memo-precinct-handover-001`은 seed·상황판 입력, OP2의 `memo-precinct-op2-001`은 인수인계 메모 입력이다. 서로 다른 행이다.
- `negativeOnlyInputs.forbiddenPhraseResponse`는 요약 저장·표시용이 아니라 금지 문구 차단 검증 입력이다.
- mock storage·FCM 수신자·네트워크 복구 스크립트는 대역 입력이다. 실제 업로드·푸시 수신·네트워크 전환의 성공 증거로 쓰지 않는다.

## 출처

필요한 요구와 구현 차이는 JSON의 `meta.sourceDocs`에 연결한 기능별 문서에서 확인한다. 원천 사건 입력은 [mock 112 seed](../mock-112/src/main/resources/seed/precinct-first-scenario.json)이며 검사기가 공용 데이터와 대조한다.

옛 분담표·시나리오·별칭 매핑은 아래 Git 원문으로 보존한다. 과거의 “canonical”·Lane 승인 문구를 현재 규칙으로 적용하지 않는다.

- [하네스 시나리오와 fixture 원문](https://github.com/sonic8-8/suri-map/blob/28f8d4dfc13096295142e3b807e1bc869ad268cf/docs/spec/harness-scenarios.md)
- [경계·권한·이벤트 표 원문](https://github.com/sonic8-8/suri-map/blob/28f8d4dfc13096295142e3b807e1bc869ad268cf/docs/spec/boundaries.md)
- [mock 112 별칭 매핑 원문](https://github.com/sonic8-8/suri-map/blob/c88365473bc459637f1e3c664b30e241f7489715/docs/spec/fixtures/l1/precinct-first-fixture.md)
