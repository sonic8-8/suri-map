# Suri-Map observability

부하 테스트 중 App 서버와 Suri-Map 상태를 Ops 서버의 Prometheus와 Grafana에서 확인하기 위한 설정이다.

## App 서버

- `app/docker-compose.yml`: 호스트, 컨테이너, PostgreSQL 지표를 사설망에 노출한다.
- `../nginx/suri-map-metrics.conf`: Backend Actuator 지표만 Ops 서버에 전달한다.
- `app/backup-before-load-test.sh`: 부하 테스트 전 PostgreSQL 백업을 한 번 만든다. 기본 저장 경로는 `/srv/apps/suri-map/backups`다.

수집 포트는 Hetzner 사설망에서 Ops 서버 `10.0.0.3`만 접근할 수 있어야 한다.

## Ops 서버

`ops/prometheus-suri-map.yml`의 네 job을 `/srv/ops/prometheus/prometheus.yml`의 `scrape_configs` 아래에 추가한다. 설정을 반영하기 전에 `promtool check config`로 확인한다.

`ops/grafana/provisioning/dashboards`는 `Suri-Map Load Test` 대시보드를 추가한다. Backend, PostgreSQL, App 서버와 k6 지표를 한 화면에서 확인한다.

연속해서 실행한 Breakpoint Test를 볼 때는 `Breakpoint RPS`로 목표 RPS를 선택해 인접 실행의 k6 metric이 섞이지 않게 한다. Backend 응답 시간은 histogram이 없어 평균을 표시하고, p95·p99는 k6 지표를 사용한다.

k6 지표는 Ops 서버의 `http://127.0.0.1:9090/api/v1/write`로 전송한다. Prometheus 실행 옵션에는 `--web.enable-remote-write-receiver`가 필요하다.
