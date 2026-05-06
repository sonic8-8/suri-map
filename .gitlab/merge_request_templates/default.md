## 변경 사항
<!-- 이 MR에서 변경한 내용을 간단히 설명해주세요 -->

## 관련 Jira 티켓
<!-- <JIRA-KEY> -->

## 작성자 확인
- [ ] 변경한 플랫폼의 AGENTS.md 검증 명령을 실행했다.
- [ ] AGENTS.md와 가까운 플랫폼별 AGENTS.md 규칙을 확인했다.
- [ ] 수정한 기준 문서를 MR 설명에 적었다.
- [ ] 기준 문서에 없는 public API, entity, event, error, fixture ID, board slot을 새로 만들지 않았다.
- [ ] Lane owner 경계를 넘는 변경은 CODEOWNERS 또는 owner LGTM 경로로 확인했다.
- [ ] 검증 명령과 결과를 MR 설명에 적었다.

## 백엔드 리뷰 체크리스트
- [ ] 변경한 일반 JSON 성공 응답이 공통 `ApiResponse`를 사용한다.
- [ ] `ApiResponse`를 사용하지 않은 성공 응답이 있다면, 그 이유를 MR 설명에 적었다.
- [ ] 예외 응답 형식을 추가하거나 수정했다면, 공통 `ErrorResponse`를 사용한다.
- [ ] Validation을 추가하거나 수정했다면, Validation 실패 응답에 `errors` 목록이 포함된다.
- [ ] 보호 API를 수정했다면, `userId`/`userKey` 요청값 대신 인증 principal 기반으로 처리한다.
- [ ] 컨트롤러가 엔티티를 직접 반환하지 않고 Response DTO를 반환한다.
- [ ] Controller Request DTO와 Service DTO를 분리해야 하는 흐름에서 DTO를 혼용하지 않았다.
- [ ] DTO를 추가하거나 수정했다면, validation 메시지가 하드코딩되지 않고 `backend/src/main/resources/ValidationMessages.properties` 키를 사용한다.
- [ ] 컨트롤러 테스트를 추가하거나 수정했다면 `@WebMvcTest` 기반으로 작성했다.
- [ ] 서비스 테스트를 추가하거나 수정했다면 `@SpringBootTest` 기반으로 작성했다.
- [ ] 서비스 테스트를 Mockito로 작성하거나 유지했다면, `@SpringBootTest` 대신 선택한 이유를 MR 설명에 적었다.
- [ ] 새 domain 패키지를 추가했다면, 관련 Entity/Repository/Enum을 같은 도메인 패키지에 두고 기술별 폴더로 나누지 않았다.
- [ ] 클래스명이 `Controller`, `Service`, `Repository`, `Request`, `Response`, `Client`, `Config`, `Test` 규칙을 따른다.
- [ ] 커밋 메시지가 `docs/tasks/index.md`의 `[Area] type(scope): 설명 (<JIRA-KEY>)` 형식을 따른다.

## 프론트엔드 / Android 리뷰 체크리스트
- [ ] Web은 지휘 상황판, Android는 현장 입력이라는 채널 경계를 지켰다.
- [ ] API path, request/response, error body는 `docs/api/api-spec.md`를 기준으로 했다.
- [ ] fixture ID와 하네스 시나리오 값은 `docs/spec/harness-scenarios.md`를 그대로 사용했다.
- [ ] frontend 변경 시 `npm run typecheck`와 `npm run build`를 확인했다.
- [ ] Android 변경 시 `./gradlew :app:assembleDebug`와 필요한 test 명령을 확인했다.
