# Suri-Map Architecture Decision Record

## 0. 문서 정보

- 문서 유형: Architecture Decision Record (ADR) — 현재 구현 기준
- 작성일: 2026-04-23 (업데이트 2026-04-27, archive 분리)
- 상위 문서: [prd.md](./prd.md), [architecture.md](./architecture.md)

## 문서 규칙

- 결정이 추가되면 맨 아래에 추가 (번호 오름차순)
- `adr.md`에는 현재 구현 기준인 `Accepted` ADR만 상세히 둔다.
- 대체되거나 폐기된 ADR 상세 기록은 [adr-archive.md](./adr-archive.md)로 이동한다.
- Accepted된 결정은 수정하지 않는다. 대체가 필요하면 새 결정을 추가하고, 기존 결정은 archive로 옮긴다.
- archive로 이동한 ADR 번호는 재사용하지 않는다. 따라서 `adr.md`의 ADR 번호 공백은 정상이다.

## 현재 구현 기준 요약

- MVP 계정 모델: 팀 계정·순찰차 계정·지휘 계정 (ADR-0031)
- 경로 주체: 개인이 아니라 팀 업무폰/순찰차 업무폰 `police_phone` (ADR-0025)
- OP/인수인계: OP별 경로·마커·구역 상태·메모·수색 이력 요약 (ADR-0029)
- 112/mock·seed polling/import 기반 사건·지원 배정 반영, 내부 지원 배정 workflow 없음 (ADR-0030)
- Refresh token은 PostgreSQL `refresh_token`에 저장하고 Redis는 사용하지 않음 (ADR-0006)
- 별도 board DB 없이 원본 테이블 기반 API assembly + SSE refetch 신호 사용 (ADR-0033)
- 서버와 Android 동기화 경계는 UUID 식별자를 기본으로 사용 (ADR-0036)
- ADR 문서 구조: 현재 구현 기준과 archive 분리 (ADR-0032)
- Persistence Layer: MyBatis 단일 채택 (ADR-0033)
- Search History Summary Provider: OpenAI API + FAILED 상태 처리 (ADR-0034)
- 잔여 기술 선택: Spring MVC+SseEmitter, Kotlin DSL, npm/Vite, React Router, TanStack Query+Zustand, MinIO dev adapter (ADR-0035)

## ADR-0001. 단일 EC2 + Docker Compose 배포

- **Status**: Accepted
- **Date**: 2026-04-21

### Context

- 가용 인프라: EC2 xlarge 1대
- 개발 기간: 5주, MVP 시연 중심
- 팀: 6명, Agentic Engineering 기반 풀스택 개발
- 요구사항의 핵심은 고가용성이 아니라 **오프라인 기록 보존과 복구 동기화**

### Decision

- 전체 서비스(edge-nginx, frontend-nginx, Spring Boot, PostgreSQL/PostGIS, Jenkins, SonarQube, Prometheus/Grafana/Loki)를 단일 EC2에서 Docker Compose로 운영
- 서비스별 이미지 분리로 개별 롤백 가능성 확보
- edge-nginx는 LB가 아닌 Reverse Proxy 역할

### Consequences

- **+** 인프라 단순, 5주 일정에 적합, 복구 경로 명확
- **+** 배포/롤백 단위가 이미지로 명확
- **−** 단일 장애점, 리소스 경합 가능성
- **−** Jenkins/SonarQube 동거 시 빌드 부하가 서비스에 영향
- 대응: Docker resource limit, 장기적으로 CI/CD·App·DB 노드 분리 검토

---

## ADR-0002. PostgreSQL + PostGIS 채택

- **Status**: Accepted
- **Date**: 2026-04-21
- **Note**: ADR-0027로 자동 사각지대 연산 범위는 제외

### Context

- 수색 경로는 LineString, 수색 완료 구역은 Polygon, 단서/지원 요청은 Point
- 요구사항 정의서에서 **"이동 경로와 수색 완료 구역을 같은 의미로 다루면 안 된다"** 명시
- OP별 경로·구역·마커를 공간 타입으로 저장하고, 겹침 조회·렌더링 범위 필터링·구간 보정에 활용할 필요가 있음
- 자동 미확인 구역 확정/사각지대 하이라이트는 PRD v3에서 제외했으며 ADR-0027을 따른다

### Decision

- PostgreSQL을 주 DB로 사용
- PostGIS 확장 필수 도입 (선택 사항 아님)

### Consequences

- **+** 공간 데이터 타입과 연산 기본 지원
- **+** LineString/Polygon/Point를 일반 RDB처럼 저장 가능
- **+** 경로·구역·마커의 겹침 조회와 지도 렌더링 범위 필터링이 SQL 레벨에서 가능
- **−** PostGIS 운영/튜닝 경험 요구
- **−** PostGIS 확장된 이미지로 컨테이너 구성 필요

---

## ADR-0003. 오프라인 우선 스택 (Room + Outbox + WorkManager + Idempotency Key)

- **Status**: Accepted
- **Date**: 2026-04-21

### Context

- 산악 지형의 통신 불안정이 핵심 Pain Point
- 소방 인터뷰에서도 "산길샘" 같은 오프라인 앱 실사용 확인
- 기록 유실이 가장 큰 위험
- 요구사항: "통신이 끊겨도 기록 유지 후 복구 시 동기화"

### Decision

Android 앱에서 다음 스택 조합 채택:

- **Room/SQLite**: 로컬 DB (경로, 마커, 구역 상태, 동기화 상태)
- **Outbox Pattern**: 서버 미전송 작업 큐잉
- **WorkManager**: 네트워크 복구 감지 및 자동 재전송
- **Idempotency Key**: 중복 전송 방지 (서버가 키 기준으로 dedup)
- **충돌 정책**: last-write-wins (서버 타임스탬프 기준, 상세는 Spec)

### Consequences

- **+** 통신 단절 30분 이상에서도 포인트 유실 0건 달성 가능
- **+** 중복 전송으로 인한 데이터 오염 방지
- **+** 배터리 효율 (상시 소켓 불필요)
- **−** 서버 측에 idempotency 처리 로직 필요
- **−** last-write-wins는 충돌 시 이전 편집 덮어씀 (수동 병합/승자 명시 요구 시 재검토)

---

## ADR-0004. 지도 SDK: MapLibre 통일 + 자체 타일 서버

- **Status**: Accepted
- **Date**: 2026-04-21 (초안) / 2026-04-22 (개정)
- **Note**: 2026-04-22 개정 전 결정은 Android=Mapbox/Web=MapLibre

### Context

- Android는 **오프라인 타일 다운로드**가 핵심 (산악 통신 불안정)
- Web 상황판은 내부망 PC, 경로/구역/마커 시각화가 핵심
- 초안 결정은 Android=Mapbox, Web=MapLibre 분리였으나 Mapbox 라이선스·쿼터 리스크가 5주 MVP 블로커로 부상 (G-15)
- SSAFY가 EC2 xlarge를 제공하므로 자체 타일 서버 운영 여력 있음
- 베이스맵 선정 갭(G-17)과 연동

### Decision

- **Android**: MapLibre Native Android SDK — 오픈소스, 오프라인 타일 관리 지원
- **Web**: MapLibre GL JS — 오픈소스
- **타일 서버**: 자체 호스팅 (OSM + OpenMapTiles, `tileserver-gl` 컨테이너)
- 오프라인 타일 프리셋은 자체 타일 서버에서 배포

### Consequences

- **+** 라이선스 리스크 제거 (오픈소스만 사용)
- **+** Android·Web이 동일 스타일 체계를 공유 (베이스맵 일관성 비용 ↓)
- **+** 내부망·업무 단말 장기 보관 약관 우려 소거
- **−** MapLibre Native Android의 오프라인 관리 성숙도가 Mapbox보다 다소 낮음 (초기 개발 비용)
- **−** 타일 서버 컨테이너 운영 부담 (EC2 리소스·모니터링)
- **−** 산림청 등산로 등 오버레이 레이어 파이프라인을 직접 구성해야 함
- 구체적 타일 소스·전체 수색 구역 전략은 ADR-0024 참조

---

## ADR-0005. 실시간 채널 분리 (SSE for Web, FCM for Android)

- **Status**: Accepted
- **Date**: 2026-04-21

### Context

- 현장 단말: 통신 불안정, 배터리 제약, 상시 소켓 부담
- 웹 상황판: 내부망 안정 환경, 단방향 이벤트 수신 충분
- 지원 요청 알림은 앱 백그라운드 상태에서도 도달해야 함

### Decision

| 방향 | 채널 |
|---|---|
| Android → Server | REST API + 재전송 (상시 연결 없음) |
| Server → Web | **SSE** (단방향, 단순, HTTP 위에 구현) |
| Server → Android | **FCM** (백그라운드 푸시, 배터리 친화) |

WebSocket은 MVP 범위 외.

### Consequences

- **+** 각 경로의 네트워크 특성에 맞는 채널 선택
- **+** SSE는 프록시/방화벽 호환성이 WebSocket보다 단순
- **+** FCM은 백그라운드 알림을 OS 레벨에서 보장
- **−** SSE는 단방향 — 양방향 통신 필요 시 WebSocket 재검토
- **−** FCM 의존 (Google 서비스) — 폴리폰에서 GMS 가용성 확인 필요

---

## ADR-0006. Redis 미도입 (MVP)

- **Status**: Accepted
- **Date**: 2026-04-21

### Context

- 현재 핵심 문제는 캐시가 아니라 오프라인 동기화, 공간 데이터 처리
- 단일 Spring Boot 인스턴스
- 실시간 이벤트 fan-out/pub-sub 필요 없음 (SSE로 충분)

### Decision

- MVP에서는 Redis를 도입하지 않는다
- Refresh token은 PostgreSQL `refresh_token` 테이블에 저장하고 Redis를 토큰 저장소로 사용하지 않는다.

### 도입 검토 시점

- 다중 Spring Boot 인스턴스 운영 → 세션 공유, pub/sub
- 실시간 이벤트 fan-out 증가
- 분산 락, rate limit, 짧은 TTL 상태값 필요

### Consequences

- **+** 기술 복잡도 절감, 5주 일정 내 구현 가능한 범위 유지
- **+** 핵심 문제(동기화, 공간 데이터)에 집중
- **−** 향후 수평 확장 시 세션 저장소 재설계 필요
- **−** 실시간 이벤트 팬아웃 증가 시 단일 인스턴스 병목 가능

---

## ADR-0007. 도메인 수직 슬라이스 모듈 경계 (계층 분리 지양, 횡단 관심사 예외 허용)

- **Status**: Accepted
- **Date**: 2026-04-21 (초안) / 2026-04-22 (개정)
- **Note**: 2026-04-22 횡단 관심사 조항 추가

### Context

- 팀 구성: 6명이 각자 Agentic Engineering으로 풀스택 개발
- 개발 기간: 5주
- 초기에 Android/Backend/Web으로 **계층 기반 분할**을 검토했으나, 후반 통합 시 충돌 위험이 큼
- Spec 분할 설계 과정에서 **인증·동기화·지도** 등 여러 도메인이 공통 소비하는 기반 기능은 특정 도메인 슬라이스에 귀속하기 어렵다는 현실이 드러남

### Decision

- 모듈 경계는 **도메인 기능 단위 수직 슬라이스**로 분할 (원칙)
- 하나의 API 엔드포인트 개발 시 **백엔드 + 웹 UI + Android UI**를 함께 처리
- 예: "단서 마커 생성" 기능 = 백엔드 엔드포인트 + 웹 상황판 표시 + Android 입력 폼을 한 슬라이스로 묶음

#### 횡단 관심사 예외 조항

- **다수 도메인이 공통 소비하는 기반 기능(cross-cutting concern)**은 단독 Spec으로 분리할 수 있다
- 해당 Spec 역시 **백엔드·웹·Android 3스택 산출물을 모두 포함**해야 한다 (계층 단독 Spec 금지 원칙은 유지)
- 적용 대상 (MVP 기준): 계정·권한·운영 기록, 오프라인 동기화 엔진, 오프라인 지도/타일
- 판별 기준:
  1. 2개 이상의 다른 Spec이 동일한 계약을 소비하는가
  2. 단독으로 Acceptance 판정 가능한 3스택 산출물이 존재하는가
  3. 다른 Spec에 흡수하면 특정 도메인 오너에게 과도한 부담이 가는가
- 위 3조건 중 2개 이상 충족 시 횡단 Spec으로 분리

### Consequences

- **+** 도메인 단위 통합이 자연스러움 (머지 충돌 감소)
- **+** 각 개발자가 end-to-end 흐름을 이해
- **+** PR/코드 리뷰 단위가 "기능 하나"로 명확
- **+** 횡단 관심사의 중복 구현·계약 분기 방지
- **−** 한 사람이 Android + Web + Backend 세 스택을 함께 다뤄야 함 (Agentic Engineering 전제로 수용)
- **−** 횡단 Spec 오너와 도메인 Spec 오너 간 **인터페이스 계약 선행 동결**이 필수 (1주차 병목 가능)

---

## ADR-0012. 시계 보정 정책 (client_ts + server_ts 병기)

- **Status**: Accepted
- **Date**: 2026-04-22

### Context

- ADR-0003은 충돌 판정을 "last-write-wins (서버 타임스탬프 기준)"으로만 명시
- 오프라인 단말은 서버 타임스탬프가 없음 — 단말 시계 기반으로 기록 생성
- 단말 시계는 조작 가능·오차 가능 (G-07, G-A)
- 오프라인 30분 후 복구 시 "30분 전 단서"가 "지금 단서"로 표시되면 현장 판단 왜곡

### Decision

모든 기록 이벤트에 다음 3가지 시각 필드를 병기:

| 필드 | 의미 | 기준 |
|---|---|---|
| `client_ts` | 이벤트가 단말에서 발생한 시각 | 단말 로컬 시계 (ISO 8601 + timezone) |
| `server_ts` | 서버가 이벤트를 수신한 시각 | 서버 UTC 시계 |
| `clock_offset_ms` | 단말-서버 시계 차이 (동기화 시점 측정) | `client_ts - server_ts` 밀리초 |

**용도 분리**:
- **화면 표시 시각**: `client_ts` 사용 (사용자가 기록한 시점)
- **충돌 판정 (last-write-wins)**: `server_ts` 사용 (서버 수신 순서)
- **운영 분석**: 3개 필드 모두 저장, `clock_offset_ms`로 단말 시계 조작 추적

### Consequences

- **+** 오프라인 기록의 "발견 시점"이 올바르게 표시됨
- **+** 충돌 판정은 서버 시각 기준으로 일관성 보장
- **+** 단말 시계 조작 시도가 `clock_offset_ms`로 탐지 가능
- **+** 도메인 이력과 운영 기록에 시각 출처가 명시되어 추적성 확보
- **−** 모든 이벤트 스키마에 3개 시각 필드 추가 필요 (저장 용량 소폭 증가)
- **−** 클라이언트가 "지금 몇 시지?" 표시 시 `client_ts` 기준인지 `server_ts` 기준인지 UX 규칙 필요

### 적용 범위

- SearchPath 포인트, Marker, SearchArea 상태 전이, 도메인 이력 등 **모든 타임스탬프 보유 이벤트**에 적용
- Spec 단계에서 공통 시각 필드 컨트랙트로 고정

---

## ADR-0013. 언어 / 런타임 (JDK 17 + Kotlin + React)

- **Status**: Accepted
- **Date**: 2026-04-22

### Context

- 팀 구성: SSAFY 교육생 6명, 전원 동일 역량
- 개발 기간: 5주 MVP
- 팀이 익숙한 스택: Spring Boot(Java/Kotlin), Kotlin Android, React
- 언어/런타임 미지정 상태로 남으면 Spec·하네스 작성 시 가정이 갈림

### Decision

| 스택 | 언어 | 런타임 / 버전 |
|---|---|---|
| 백엔드 | Java | **JDK 17 (LTS)** + Spring Boot 3.x |
| Android | **Kotlin** (최신 안정 버전) | Android 12+ (Galaxy S22 이상) |
| 웹 | TypeScript | Node.js LTS (빌드 전용), React 18+ |

### Consequences

- **+** 팀 익숙함 기반 선택으로 학습 비용 최소화
- **+** JDK 17은 LTS로 장기 지원·안정성 보장
- **+** Kotlin Android는 Google 공식 권장
- **+** TypeScript는 React SPA의 사실상 표준
- **−** JDK 21 최신 기능(가상 스레드 정식 등) 미사용 (5주 일정에서 안정성 우선)
- **−** 백엔드를 Kotlin으로 통일할 기회 포기 (팀 Java 경험 우선 판단)

### 검토 필요 항목

- 빌드 도구 (Gradle Kotlin DSL vs Groovy DSL) — ADR-0035에서 Kotlin DSL로 확정
- 프론트 패키지 매니저 (npm / yarn / pnpm) — ADR-0035에서 npm으로 확정

---

## ADR-0014. 프론트 프레임워크 (React SPA)

- **Status**: Accepted
- **Date**: 2026-04-22

### Context

- 웹 상황판은 **경찰 내부망 PC**에서 운영
- SEO·소셜 미리보기 불필요
- 한 번 로그인 후 장시간 사용 패턴
- 핵심 기능: 지도(MapLibre GL JS) + 실시간(SSE/EventSource)
- 팀 익숙함: React

### Decision

- React 18+ 기반 **SPA** (Single Page Application)
- Next.js/Remix 등 SSR/SSG 프레임워크 미도입
- 정적 빌드 결과물은 `frontend-nginx` 컨테이너에서 서빙

### Consequences

- **+** 지도/실시간 중심 화면은 어차피 클라이언트 렌더링이라 SSR 이득 없음
- **+** 추가 Node 서버 컨테이너 불필요 (EC2 리소스·배포 복잡도 ↓)
- **+** 팀 익숙함 기반 (학습 비용 최소)
- **+** `frontend-nginx` 단일 컨테이너 정적 서빙이 배포·롤백 단위로 깔끔
- **−** 향후 SEO가 필요한 외부 공개 페이지 추가 시 전환 비용
- **−** 초기 번들 크기가 커질 경우 코드 스플리팅 전략 필요

### 검토 필요 항목

- 라우터 선택 — ADR-0035에서 React Router로 확정
- 상태 관리 — ADR-0035에서 TanStack Query(React Query) + Zustand로 확정

---

## ADR-0020. GPS 수집·전송 주기 (수집 5초 / 전송 10초 배치)

- **Status**: Accepted
- **Date**: 2026-04-23

### Context

- architecture.md §3.1에 "GPS 수집 5초 주기", §3.7에 "GPS 전송 10초 배치"가 본문으로만 기록되어 있었고 별도 결정 근거가 없었음
- PRD2 A.2(원본 확인 필요 항목)에 "온오프라인 상태 보고 주기 5분 수준 가정이 현장에 맞는지" 항목이 있어 수치 근거가 요구됨
- 산악 수색 배터리 제약(PRD2 §8.6)과 상황판 위치 갱신 목표 10초(architecture.md §7.2)의 균형 필요

### Decision

- **수집 주기**: 5초 — GPS 포인트를 로컬 DB에 기록
- **전송 주기**: 10초 배치 — 네트워크 호출을 절반으로 줄여 배터리·트래픽 절감
- 상시 소켓 연결 없이 REST API 배치 전송 (ADR-0005)
- 오프라인 상태에서는 수집만 지속, 복구 시 Outbox 경로로 동기화 (ADR-0003)
- 주기 조정이 필요하면 Spec 단계에서 현장 측정 기반 재조정

### Consequences

- **+** 상황판 10초 갱신 목표(architecture §7.2)와 전송 주기가 일치
- **+** 수집/전송 분리로 배터리·네트워크·데이터 정확도가 각각 최적화됨
- **+** 오프라인 시에도 5초 해상도의 궤적 기록 유지
- **−** 수집·전송 주기 불일치로 전송 직전 최대 10초 지연 발생 (허용 범위)
- **−** 수집 5초는 고속 이동 시 궤적이 들쭉날쭉할 수 있음 (도보 수색 전제에서는 허용)

### 검토 필요 항목

- 산악 환경 GPS 수신 품질에 따른 수집 실효 주기 측정 (Spec 단계 현장 검증)
- 배터리 소모 실측 후 필요 시 8초/15초 등으로 재조정

---

## ADR-0022. 사건 종료는 terminal, 실종자 정보 원본은 112에 두고 Suri-Map은 운영 도메인 데이터만 유지

- **Status**: Accepted
- **Date**: 2026-04-23

### Context

- 사용자 확인 결과, **112 계열 시스템이 사건과 실종자 정보의 장기 보존 원본**이며 Suri-Map은 운영 보조 성격임
- PRD2 초기본에는 `FR-22 즉시 삭제`와 `장기 보존` 문구가 함께 있어 Suri-Map의 데이터 수명주기가 혼재되어 있었음. **PRD2 §8.5는 본 ADR 도출 이후 2026-04-23 업데이트로 정합화되었으며, 본 ADR은 해당 정책의 근거 문서로 유지**
- 산악 수색 운영 보조 도구에서 `재오픈` 기능은 가치가 낮고, 종료/복구/권한 모델만 복잡하게 만들 가능성이 큼

### Decision

- Suri-Map은 실종자 개인정보/사진을 **운영 도메인 데이터**로만 저장한다. 장기 보존의 원본 시스템은 112 계열 시스템이다
- 사건 종료 시 실종자 개인정보/사진은 Suri-Map의 active DB, API 응답, 단말 오프라인 패키지에서 즉시 제거한다
- 사건 종료는 **terminal** 상태로 본다. 사용자 대상 재오픈 UI/API는 제공하지 않는다
- 종료 후에도 비식별 사건 메타, 구역/마커/OP 이력, 운영 기록, 위치정보 접근기록은 각 보존 정책에 따라 유지할 수 있다
- 종료 처리 실수에 대한 복구가 필요하면 일반 사용자 재오픈이 아니라 **관리자 운영 절차**로 대응한다

### Consequences

- **+** Suri-Map에 불필요한 개인정보 장기 잔존을 줄일 수 있음
- **+** 사건 lifecycle이 `open → closed`로 단순해져 구현과 권한 모델이 간결해짐
- **+** 공식 기록/장기 보존의 책임 소재가 112 계열 시스템에 명확히 남음
- **−** 종료 후 Suri-Map만으로 실종자 개인정보를 다시 조회할 수 없음
- **−** 112 가져오기 또는 공식 시스템 조회가 막히면 과거 개인정보를 이 서비스에서 복구할 수 없음

---

## ADR-0023. 웹 상황판 접근 환경은 공개 도메인 기반 데스크톱 브라우저

- **Status**: Accepted
- **Date**: 2026-04-23

### Context

- PRD2 §3.2는 웹 상황판 접근 환경을 **공개된 도메인 주소**로 정의함
- 기존 문서 일부는 웹 상황판을 내부망 안정 환경으로 가정했으나, 최신 제품 정의와 불일치함
- 다만 PRD2 §8.8은 웹 상황판을 여전히 **데스크톱 전용**으로 제한하고, 폴리폰에서의 웹 접근은 지원 대상에서 제외함

### Decision

- 웹 상황판은 공개 HTTPS 도메인으로 제공한다
- 지원 클라이언트는 데스크톱 브라우저(Chrome/Edge 최신 2버전)로 한정한다
- 실시간 채널은 계속 SSE를 사용한다. 공개 도메인 배포가 WebSocket 도입 근거가 되지는 않는다
- 오프라인 우선 구조는 웹에 도입하지 않는다. 현장 오프라인 요구는 Android 앱이 담당한다
- 이 ADR은 **접근 환경 가정에 한해** ADR-0005, ADR-0014의 구형 `내부망` 문맥을 대체한다. SSE와 React SPA 자체 결정은 계속 유효하다

### Consequences

- **+** 본부/사무실 등 다양한 위치에서 동일 URL로 접근 가능
- **+** PRD2의 공개 도메인 전제와 아키텍처 문서가 일치함
- **−** 내부망 보호에 기대던 단순 가정이 사라지므로 인증, 세션, TLS, 운영 접근제어를 더 엄격히 관리해야 함
- **−** SSE 연결 안정성은 프록시/타임아웃 설정을 명시적으로 검증해야 함

---

## ADR-0024. 수색 범위 확장과 사건별 전체 수색 구역

- **Status**: Accepted
- **Date**: 2026-04-27
- **Supersedes**: ADR-0011

### Context

PRD v3는 산악 중심 제품 정의를 도심·도심 외곽·논밭·하천·산악 전반의 실종 수색으로 확장했다. 대상 산 프리셋만으로는 지구대/파출소 초동 대응과 순찰차 수색 시나리오를 설명할 수 없다.

### Decision

- MapLibre Android/Web + 자체 `tileserver-gl` 선택은 유지한다.
- 오프라인 타일 다운로드 기준을 산 프리셋에서 **사건별 전체 수색 구역**로 바꾼다.
- 전체 수색 구역은 자동 누락 판단 기준이 아니라 초기 뷰포트, 오프라인 패키지, 구역 작성의 기준으로만 사용한다.
- 등산로·임도 등 환경별 보조 레이어는 후속 데이터셋 확보 여부에 따라 붙인다.

### Consequences

- **+** 도심·외곽·하천·산악을 같은 지도 패키지 구조로 처리한다.
- **−** 전체 수색 구역이 넓거나 부정확하면 타일 용량·누락 문제가 생긴다.

---

## ADR-0025. 경로 주체는 PolicePhone, 조작 주체는 운영 계정

- **Status**: Accepted
- **Date**: 2026-04-27

### Context

인터뷰 결과 팀 업무폰과 순찰차 업무폰을 교대자가 이어 쓰는 운영이 확인됐다. 개인별 GPS 경로 전제는 현장과 맞지 않으며, MVP는 개인 식별보다 공유 폴리폰 운용 편의를 우선한다.

### Decision

- 수색 경로의 주체는 개인이 아니라 `police_phone`다.
- `police_phone`는 팀 업무폰 또는 순찰차 업무폰으로 구분한다.
- 경로는 사건·OP·`police_phone`에 귀속하며 `search_path` 상태로 시작·일시정지·재개·종료를 표현한다.
- 마커·메모·구역·OP 등 조작 주체는 개인이 아니라 팀 계정·순찰차 계정·지휘 계정으로 기록한다.

### Consequences

- **+** 실제 업무폰 운용과 경로 모델이 맞아진다.
- **+** "어느 단말/팀/순찰차가 무엇을 했는지"를 단순하게 기록한다.
- **−** 개인 단위 조작자 식별은 포기한다.

---

## ADR-0026. 지휘 권한과 채널별 조작 경계 v3

- **Status**: Accepted
- **Date**: 2026-04-27
- **Supersedes**: ADR-0016, ADR-0017

### Context

PRD v3는 지구대/파출소 팀장 또는 당직자를 현장 지휘관 범위에 포함했다. 또한 앱은 현장 기록, 웹은 지휘 판단이라는 채널 경계가 더 명확해졌다.

### Decision

- 현장 지휘관은 사건당 N명이며 실종팀 간부, 지원 부서 간부, 지구대/파출소 팀장 또는 당직자를 포함한다.
- 웹 전용: 사건 가져오기/종료, 전체 수색 구역, 구역 분할·할당·완료, 새 OP, 차량/도보 구간 보정.
- 앱 전용: 수색 경로, GPS 기록, 현장 마커 생성, 사진 첨부.
- 앱+웹 공통: 사건 조회, 마커 조회·수정·삭제(권한 범위), OP/경로/구역 인수인계 메모 작성.
- 서버는 역할과 클라이언트 출처를 함께 검증한다.

### Consequences

- **+** 초동 대응부터 실종팀 인계까지 같은 권한 모델로 다룬다.
- **+** 앱과 웹의 구현 책임이 분리된다.
- **−** 동작별 권한 검증이 세밀해진다.

---

## ADR-0027. 자동 사각지대 하이라이트 폐기

- **Status**: Accepted
- **Date**: 2026-04-27
- **Supersedes**: ADR-0018

### Context

PRD v3는 확인 누락 확정과 다음 구역 추천을 금지한다. 자동 사각지대 하이라이트는 파라미터 튜닝과 오판 리스크가 커서 MVP의 인수인계 문제에 비해 우선순위가 낮다.

### Decision

- MVP에서 PostGIS 기반 자동 사각지대 하이라이트를 구현하지 않는다.
- 상황판은 OP별 경로, 완료 구역, NOTE 마커, 메모, 수색 이력 요약을 함께 보여주고 판단은 사람이 한다.
- PostGIS는 공간 저장·조회·렌더링 범위 필터링에 계속 사용한다.

### Consequences

- **+** 자동 판단 금지 원칙과 맞다.
- **+** OP 히스토리와 인수인계 UX에 집중할 수 있다.
- **−** 빈 영역을 자동 계산해 보여주는 기능은 사라진다.

---

## ADR-0028. 사건 오프라인 패키지 v3

- **Status**: Accepted
- **Date**: 2026-04-27
- **Supersedes**: ADR-0019

### Context

오프라인 패키지는 더 이상 산악 프리셋 다운로드가 아니라 사건 수행에 필요한 최소 데이터 묶음이다.

### Decision

- 패키지는 사건 메타, 실종자 정보, OP, 담당 구역, 초기 기준점 마커, 전체 수색 구역과 타일, `police_phone` 식별 정보를 포함한다.
- 적재 진행률과 실패 재시도를 제공하고, 미완료 상태는 상황판에 경고 배지로 보고한다.
- 사건 종료 시 동기화 ack를 확인한 뒤 로컬 패키지와 실종자 로컬 데이터를 삭제한다.

### Consequences

- **+** 도심·외곽·산악 모두 같은 사전 적재 흐름을 쓴다.
- **−** 패키지 범위 산정과 민감정보 로컬 보관 정책이 중요해진다.

---

## ADR-0029. OP 기반 인수인계와 수색 이력 요약

- **Status**: Accepted
- **Date**: 2026-04-27
- **Supersedes**: ADR-0021

### Context

PRD v3에서 OP는 재수색 차수뿐 아니라 근무 교대와 인수인계의 히스토리 레이어가 됐다. 수색 보고서 AI는 제외되고, 기록 기반 수색 이력 요약만 MVP에 남았다.

### Decision

- 사건 가져오기 시 OP1을 자동 생성하고, OP2 이후는 웹에서 사유와 함께 수동 생성한다.
- OP 생성 사유는 `RE_SEARCH`, `AREA_CHANGED`, `OTHER`로 제한한다. OP1 자동 생성에는 `INITIAL`을 사용하고, 근무 교대는 `duty_shift`로 별도 기록한다.
- `search_path`, 마커, 구역 이력, 수색 구역 배정, 인수인계 메모, 수색 이력 요약은 OP에 귀속한다.
- 수색 이력 요약은 내부 기록만 입력으로 사용하고, 누락 확정·다음 구역 지시·위험도 판단을 하지 않는다.
- 요약 생성 실패 시 `generation_status = FAILED`로 남기고 대체 요약 문장을 저장하지 않는다. UI는 실패 안내, 재시도, 원본 기록 확인을 제공한다.

### Consequences

- **+** 인수인계 문제를 OP 단위로 다룰 수 있다.
- **+** AI 기능을 판단 자동화가 아닌 기록 요약으로 제한한다.
- **−** OP 전환, 근무 구간, 경로, 메모, 요약의 생명주기를 일관되게 설계해야 한다.

---

## ADR-0030. 112/mock·seed polling/import 기반 사건·배정 반영

- **Status**: Accepted
- **Date**: 2026-04-27

### Context

MVP는 실제 경찰 내부망·112·실종프로파일링시스템과 직접 연동하지 않는다. 다만 제품 흐름은 사용자가 사건을 직접 생성하거나 Suri-Map 내부에서 지원 부대를 배정하는 것이 아니라, 112 계열 배정 결과를 polling/import로 반영하는 구조여야 한다.

### Decision

- MVP는 mock API 또는 seed adapter로 사건과 지원 배정 polling/import를 구현한다.
- 백엔드는 실제 연동 가능성을 위해 `ExternalIncidentAdapter` 경계를 둔다.
- 사건 가져오기 성공 시 내부 사건, `missing_person`, `incident_assignment`, 초기 기준점 마커, OP1을 생성한다.
- 지원 부대 배정은 Suri-Map public/admin write API가 아니라 112/mock polling/import 결과로 `incident_assignment`에 반영한다.

### Consequences

- **+** 실제 경찰 시스템 접근 없이 end-to-end 시연이 가능하다.
- **+** 향후 실연동은 adapter 교체로 확장할 수 있다.
- **−** mock 데이터 품질이 시연 설득력에 직접 영향을 준다.

---

## ADR-0031. MVP 계정 모델은 팀/순찰차/지휘 계정

- **Status**: Accepted
- **Date**: 2026-04-27
- **Supersedes**: ADR-0015

### Context

ADR-0015는 개인 계정 기반 책임 추적을 채택했지만, PRD v3의 실제 운용 전제는 팀 업무폰·순찰차 업무폰 공유 사용이다. 현장 편의와 MVP 구현 범위를 우선하면 개인 로그인보다 팀/순찰차/지휘 계정이 더 적합하다.

### Decision

- 현장 앱은 팀 계정 또는 순찰차 계정으로 로그인한다.
- 웹 상황판의 지휘 기능은 실종팀/지원 부서/지구대·파출소 지휘 계정으로 로그인한다.
- 개인 계정은 MVP 필수 범위에서 제외하고, 운영 전 법무·현장 UX 검토 후 후속 확장으로 둔다.
- 계정 발급은 경찰 IT 부서 또는 운영자가 일괄 관리한다.

### Consequences

- **+** 공유 폴리폰 교대 사용 흐름이 단순해진다.
- **+** 경로 주체(PolicePhone)와 로그인 계정(팀/순찰차)이 자연스럽게 맞는다.
- **−** 누가 실제로 버튼을 눌렀는지 개인 단위로 식별할 수 없다.

---

## ADR-0032. ADR 본문과 archive 분리

- **Status**: Accepted
- **Date**: 2026-04-27
- **Supersedes**: ADR-0010

### Context

PRD v3 반영으로 대체된 ADR이 많아지면서 `adr.md` 하나에 현재 구현 기준과 과거 결정 원문이 함께 섞였다. `Status: Superseded`가 있어도 구현자가 제목과 본문을 먼저 읽기 때문에 개인 계정, 자동 사각지대 하이라이트 같은 폐기된 전제가 계속 눈에 걸린다.

### Decision

- `adr.md`에는 현재 구현 기준인 `Accepted` ADR만 상세히 유지한다.
- 대체·폐기된 ADR 상세 기록은 `adr-archive.md`에 보관한다.
- `adr-archive.md` 상단에는 archive index를 두어 어떤 ADR이 어디로 대체됐는지 빠르게 확인한다.
- 파일명은 날짜 없는 신규 문서에 한해 하이픈 기반 `kebab-case`를 기본으로 한다.

### Consequences

- **+** 구현자가 현재 기준만 빠르게 읽을 수 있다.
- **+** 과거 의사결정 기록은 유지된다.
- **−** ADR 번호 순서와 파일 내 위치가 완전히 일치하지 않으므로 archive index 관리가 필요하다.

---

## ADR-0033. Persistence Layer로 MyBatis 단일 채택

- **Status**: Accepted
- **Date**: 2026-04-30

### Context

Suri-Map은 PostgreSQL + PostGIS를 핵심 저장소로 사용한다. 전체 수색 구역, 수색 구역, 수색 경로, 경로 구간, 마커는 LineString/Polygon/Point 계열 geometry를 저장하고, 상황판과 오프라인 패키지는 bbox, OP별 필터, board API assembly/source query 쿼리를 반복적으로 사용한다.

5주 MVP에서 JPA + Hibernate Spatial을 도입하면 공간 타입 매핑, native query 혼용, lazy loading/transaction 경계, 조회 DTO 쿼리 복잡도가 동시에 발생한다. JPA와 MyBatis를 섞는 하이브리드도 단순 CRUD에는 편하지만 6명이 수직 슬라이스로 개발하는 조건에서는 mapper/entity/repository 규칙이 이중화된다.

### Decision

- 백엔드 persistence layer는 MyBatis 단일로 구현한다.
- MVP 범위에서는 Spring Data JPA/Hibernate ORM을 도입하지 않는다.
- SQL은 mapper interface + XML mapper를 기본으로 관리한다. 복잡한 PostGIS 쿼리, board API assembly source query, `event_dispatch_job`, `idempotency_record` 조회는 명시적 SQL로 작성한다.
- Flyway가 schema 변경의 기준이며, MyBatis mapper는 Flyway schema와 `spec/specs/*.json`의 entity 계약을 따라간다.
- PostGIS geometry는 공용 TypeHandler로 매핑한다. 도메인 내부 표현은 JTS Geometry 또는 명시적 GeoJSON DTO로 제한하고, API/board/package 계약에서는 spec의 GeoJSON shape를 유지한다.
- Service layer transaction은 Spring `@Transactional`을 사용한다. domain row와 `event_dispatch_job` 생성 처리는 같은 transaction 경계 안에서 명시적으로 검증한다.
- Mapper 패키지는 Spec 또는 수직 슬라이스 단위로 나누되, 다른 Spec 소유 table을 직접 write하지 않는다.

### Consequences

- **+** PostGIS 함수, bbox 필터, spatial index 사용, board API assembly 쿼리를 SQL로 직접 제어할 수 있다.
- **+** persistence 기술이 하나로 고정되어 6명 병렬 개발 시 리뷰 기준이 단순해진다.
- **+** JPA/Hibernate Spatial 학습과 native query 혼용 비용을 피한다.
- **+** `event_dispatch_job`, `idempotency_record`, API assembly source query처럼 명시적 SQL이 필요한 경계와 잘 맞는다.
- **−** 단순 CRUD도 mapper/XML을 작성해야 하므로 boilerplate가 늘어난다.
- **−** 객체 그래프 자동 추적이 없으므로 service layer에서 write 순서와 transaction 경계를 명확히 관리해야 한다.
- **−** geometry TypeHandler와 mapper test 기반을 Phase -1에서 먼저 안정화해야 한다.

---

## ADR-0034. Search History Summary Provider로 OpenAI API 채택

- **Status**: Accepted
- **Date**: 2026-04-30

### Context

Suri-Map MVP는 평가 포인트상 실제 AI 기능을 최소 1개 이상 포함해야 한다. PRD v3에서 남은 AI 범위는 수색 보고서 자동 작성이 아니라 OP 기반 수색 이력 요약(FR-39)이며, FR-23에 따라 누락 구역 확정, 다음 수색 구역 추천, 위험도 판단은 금지된다.

단순 규칙 기반 요약만으로는 AI 기능 구현으로 보기 어렵다. 반대로 AI 출력이 지휘 판단을 대체하거나 공개 API 계약을 흔들면 기존 Spec과 시나리오 계약이 불안정해진다.

### Decision

- S8 `search_history_summary` MVP의 기본 provider는 **OpenAI API**로 한다.
- 백엔드는 `SearchHistorySummaryPort`를 두고 `OpenAiSearchHistorySummaryAdapter`를 기본 구현으로 사용한다.
- OpenAI 응답은 Structured Outputs 또는 동등한 JSON schema 검증 방식으로 제한한다.
- OpenAI 호출 실패, timeout, schema validation 실패, 금지 문구 검출 시 `generation_status = FAILED`로 남기고 `content`를 저장하지 않는다. 공개 조회는 `summary_unavailable` 상태와 재시도 가능 여부만 노출한다.
- `search_history_summary` input은 Suri-Map 내부 OP/path/marker/area/handover memo 기록에서 만든 최소화된 source bundle로 제한한다.
- `search_history_summary` output은 저장 전 guard를 통과해야 하며, 추천·누락 확정·위험도 판단·다음 구역 지시 표현은 저장하지 않는다.
- OpenAI API key와 model명은 환경 변수 또는 secret으로 주입하고 코드·fixture·로그에 남기지 않는다.
- 공개 REST response, `SEARCH_HISTORY_SUMMARY_CHANGED` event payload, board `search_history_summary` slot 계약은 S8 Spec을 유지한다. Provider 정보는 필요한 경우 내부 로그·메트릭·운영 evidence로만 다룬다.

### Consequences

- **+** MVP에서 실제 외부 AI 호출 기반 happy path를 구현할 수 있다.
- **+** `SearchHistorySummaryPort` 경계 덕분에 OpenAI happy path와 실패 경로를 mock adapter로 테스트할 수 있다.
- **+** Structured output과 guard를 통해 FR-23 금지 판단을 저장 전에 차단할 수 있다.
- **−** OpenAI API key 발급, 사용량 제한, timeout/retry 정책, 네트워크 장애 처리가 필요하다.
- **−** 시연 환경에서 외부망/API quota 문제가 있으면 요약은 실패 상태로 남으므로 happy path evidence를 사전에 확보해야 한다.

---

## ADR-0035. 잔여 런타임·프론트·개발 어댑터 기술 선택 확정

- **Status**: Accepted
- **Date**: 2026-04-30

### Context

ADR-0013과 ADR-0014는 큰 축인 JDK 17, Spring Boot 3.x, Kotlin Android, React SPA를 확정했지만 Gradle DSL, 프론트 패키지 매니저, router/state management, SSE 구현 방식, 개발용 object storage adapter가 후속 결정으로 남아 있었다.

팀원 대부분이 npm 경험만 있고, 5주 MVP에서는 기술 선택을 줄여야 한다. S4 SSE는 WebFlux 도입 근거가 없고, S5 사진 업로드는 운영 S3와 같은 계약을 개발 단계에서도 재현할 수 있어야 한다.

### Decision

- Backend web stack은 Spring MVC 중심으로 구현한다.
- SSE endpoint는 Spring MVC `SseEmitter` 기반으로 구현하고 WebFlux는 MVP 범위에서 도입하지 않는다.
- Gradle DSL은 Kotlin DSL로 통일한다.
- Frontend package manager는 npm으로 통일하고, build tool은 Vite를 사용한다.
- Web router는 React Router를 사용한다.
- Web server state는 TanStack Query(React Query), board display/client state는 Zustand를 사용한다.
- 개발·하네스 object storage adapter는 MinIO(S3-compatible)를 기본으로 하고, harness는 mock object storage/upload URL(presigned URL for upload) fixture를 사용한다. 운영 배포는 S3-compatible port를 통해 S3로 전환한다.
- Android public baseline은 AGP 8.13.x, minSdk 31, targetSdk 34를 유지하고, 테스트는 Robolectric + real hardware smoke를 기준으로 한다.

### Consequences

- **+** 팀 경험과 현재 Spec의 React Query/Zustand 전제를 맞춘다.
- **+** SSE 구현이 Spring MVC 하나로 고정되어 S4 테스트와 bootstrap 기준이 단순해진다.
- **+** MinIO를 통해 운영 S3와 같은 presigned/object key 계약을 개발 환경에서 검증할 수 있다.
- **−** WebFlux 기반 backpressure/리액티브 스트림은 MVP에서 사용하지 않는다.
- **−** pnpm/yarn의 workspace 성능 이점은 포기한다.
- **−** Android compileSdk/AGP 세부 patch version은 로컬 SDK 설치 가능성에 맞춰 bootstrap에서 검증해야 한다.

---

## ADR-0036. 서버·Android 동기화 경계 UUID 식별자 전략

- **Status**: Accepted
- **Date**: 2026-04-27

### Context

Android는 오프라인 상태에서 경로, 마커, 사진 업로드 요청을 먼저 로컬에 기록하고 네트워크 복구 후 재전송한다. 서버 auto-increment ID만 사용하면 로컬 임시 ID와 서버 ID 매핑, 재전송 중복 처리, harness fixture 병합이 복잡해진다.

### Decision

- 백엔드 공개 API, Android Room 로컬 DB, Outbox 동기화 경계의 주 식별자는 UUID를 기본으로 사용한다.
- 서버는 UUID를 authoritative identifier로 저장하고, Android는 오프라인 생성 시 UUID를 먼저 발급한다.
- 멱등성은 resource UUID와 `Idempotency-Key`를 함께 사용해 동일 요청 재전송을 같은 결과로 수렴시킨다.
- 외부 112/mock 사건 키는 별도 external key로 보존하고, 내부 도메인 식별자와 혼용하지 않는다.

### Consequences

- **+** 오프라인 생성, 재전송, 병합 fixture를 같은 식별자 체계로 검증할 수 있다.
- **+** 서버 ID 매핑 테이블 없이 Android 로컬 상태와 서버 응답을 맞출 수 있다.
- **−** UUID 정렬성과 index locality를 고려해 생성 전략과 DB index를 설계해야 한다.
