# 상황판을 React SPA로 구성

로그인 후 지도를 오래 사용하는 상황판은 검색 노출보다 지도 조작·실시간 갱신이 중요했다. Next.js·Remix의 SSR과 별도 Node 서버를 추가하는 대신 React SPA를 정적 파일로 배포하기로 했다. 서버 렌더링 운영은 줄지만 초기 다운로드 크기와 화면 상태 관리는 직접 다뤄야 한다.

현재 확인: [Frontend Dockerfile](../../frontend/Dockerfile)은 Node로 빌드한 결과만 Nginx 이미지에 복사한다. 사용 라이브러리·버전은 [package.json](../../frontend/package.json), 갱신 흐름은 [상황판 문서](../situation-board.md)에서 확인한다. 원문의 내부망 전제는 ADR-0023에서 바뀌었으며, SPA 선택 자체가 화면 성능 검증은 아니다.

기록: 2026-04-22 · [ADR-0014 원문](https://github.com/sonic8-8/suri-map/blob/7f2ea69ad46fa47ce07a6db6c66861fd95731978/docs/adr.md#adr-0014-프론트-프레임워크-react-spa).
