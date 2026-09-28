# 수리맵 호스트 Nginx 설정

외부 HTTPS 요청은 Hetzner App 서버의 호스트 Nginx → `127.0.0.1:18081`의 Frontend Nginx → Backend 순서로 전달된다. [Frontend 설정](../../frontend/nginx.conf)과 호스트 설정은 별개다.

## API 오류 상태 보존

[suri-map-api.locations.conf](suri-map-api.locations.conf)는 `/api`와 `/api/...` 요청의 오류 상태·본문을 그대로 전달한다. 인증·채널·멱등성·재전송 헤더와 요청 경로는 유지하며, 정상 웹 페이지로 대체하지 않는다. `/api-other`나 로그인·타일·사진 경로에는 적용하지 않는다.

현재 호스트의 `/etc/nginx/sites-available/apps.conf`에는 여러 서비스가 있다. 이 파일 전체를 저장소 설정으로 덮어쓰지 않는다. 조각 파일을 `/etc/nginx/snippets/suri-map-api.locations.conf`에 설치하고 **`suri-map.sonic8-8.com`의 HTTPS server 블록 안에서만** 다음과 같이 포함한다.

```nginx
include /etc/nginx/snippets/suri-map-api.locations.conf;
```

일반 웹 화면의 `location /`·대체 페이지와 다른 서비스의 server 블록은 유지한다. 프록시에서 생성한 502 오류 본문까지 새 JSON 형식으로 바꾸는 설정은 아니다. `proxy_intercept_errors off`의 오류 전달 동작은 [Nginx 공식 문서](https://nginx.org/en/docs/http/ngx_http_proxy_module.html#proxy_intercept_errors)를 따른다.

적용할 때는 실제 설정·포트·기존 location과 대조하고, 원본 백업 → 대상 블록만 변경 → `nginx -t` → reload 순서로 진행한다. 검사나 reload가 실패하면 백업을 복원한다. 적용 후에는 정상 API·로그인·SSE와 Backend 재시작 중의 오류 상태를 직접 확인한다. 설정 파일을 저장한 것만으로 적용·검증이 끝난 것은 아니다.

이 조각은 Jenkins에서 자동 설치하지 않는다. 커밋·푸시만으로 호스트 설정이 갱신되지 않으며, 애플리케이션 재배포와 호스트 설정 적용을 구분한다. 배경과 검증 결과는 [로컬 이슈 12](../../docs/issues/local/12-api-errors-replaced-with-successful-html-response.md)에 기록한다.

## 과거 환경의 설정

`apply-suri-map-host-nginx.sh`와 `suri-map-sse.locations.conf`는 과거 EC2의 단일 사이트·Backend 8081 포트 구성을 대상으로 한다. 현재 Hetzner의 다중 서비스 `apps.conf`에 그대로 실행하지 않는다. 타일 위치 예시도 현재 포트·인증 경로와 대조한 뒤 사용한다.
