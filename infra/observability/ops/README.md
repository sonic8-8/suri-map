# Grafana image rendering

Grafana 공식 image renderer는 Ops 서버에서 대시보드 이미지를 만들 때만 실행한다. 먼저 저장소의 최신 `grafana/provisioning/dashboards/json/suri-map-load-test.json`을 같은 Ops 서버 경로에 배포한다. 다음 세 파일도 `/srv/ops`에 복사한다.

- `docker-compose.image-renderer.yml`
- `setup-grafana-rendering.sh`
- `render-load-test-dashboard.sh`

처음 한 번만 setup wizard를 실행한다. Grafana Viewer 서비스 계정 토큰은 `/srv/ops/.env`에 저장하고, Grafana와 renderer 사이의 토큰은 자동 생성한다. 두 토큰은 Ops 서버에만 보관하며 GitHub나 CI secret으로 복사하지 않는다.

```bash
cd /srv/ops
sudo ./setup-grafana-rendering.sh
```

이후에는 목표 RPS, UTC 시작·종료 시각과 출력 경로를 지정해 PNG를 만든다. 명령이 renderer를 시작하고 PNG signature를 확인한 뒤 renderer를 다시 중지한다.

```bash
/srv/ops/render-load-test-dashboard.sh \
  453 \
  2026-09-01T03:48:30Z \
  2026-09-01T03:56:30Z \
  /srv/ops/k6/results/issue-8-server-breakpoint-test.png
```

Ops 서버는 Grafana의 [image renderer 권장 사양](https://grafana.com/docs/grafana/latest/setup-grafana/image-rendering/#before-you-begin)보다 작다. 이 대시보드에서 확인한 2 CPU·2GB 한도로 renderer를 상시 실행하지 않고 요청할 때만 사용한다.
