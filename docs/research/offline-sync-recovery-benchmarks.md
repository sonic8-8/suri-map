# 오프라인 동기화·복구 시간 비교 조사

- 조사 기준일: 2026-08-31
- 목적: 수리맵의 오프라인 위치 데이터 복구 목표를 정하기 전에 비교 가능한 공식 동작, 수치와 용어를 확인한다.
- 외부 서비스의 수치·정책은 조사 시점의 기록이다. 수리맵의 후속 측정은 아래 적용 항목에서 구분한다.

## 결론

공개된 공식 문서에서 수리맵과 직접 비교할 수 있는 **재연결 후 전체 위치 backlog 완료 시간 SLA/SLO는 찾지 못했다**. CalTopo, ArcGIS Field Maps, Firestore, Power Apps, Shopify POS, Square와 Stripe Terminal 등은 재연결 후 자동 동기화 또는 전송 재개를 설명하지만, `재연결 → 첫 전송`, `재연결 → 최신 상태 표시`, `재연결 → 전체 backlog 완료`를 하나의 숫자로 보장하지 않는다.

공개된 `60초`, `5분`, `24시간`, `72시간` 같은 숫자도 대부분 다음 중 하나다.

- 위치 수집·업로드·down-sync의 기본 또는 설정 주기
- 오프라인 결제나 메시지를 잃지 않기 위한 보존·업로드 기한
- 장애 뒤 다음 연결 시도까지의 backoff
- 짧은 연결 단절을 투명하게 복구할 수 있는 세션 window
- 서버·리전 재해에 대한 RTO/RPO

이 숫자들은 전체 backlog 완료 목표와 의미가 다르므로 수리맵의 `5분` 근거로 그대로 사용할 수 없다. 외부 사례에서 가져올 것은 특정 숫자보다 다음 구조다.

1. **최신 상태의 신선도와 전체 이력 복구를 분리한다.** ArcGIS는 last known location과 track history 업로드를, CalTopo는 shared location과 recorded track을 별도로 다룬다.
2. **동기화 시작 주기와 완료 시간을 분리한다.** Power Apps는 5분 delta sync 주기를 제시하면서 초기 sync는 데이터 규모에 따라 수분 이상 걸릴 수 있다고 명시한다.
3. **보존 기한과 복구 성능을 분리한다.** Square의 72시간, Shopify·Toast의 24시간 권고는 결제 손실 위험을 줄이는 기한이지 복구 시간 SLA가 아니다.
4. **짧은 연결 복구와 긴 이력 reconciliation을 분리한다.** Ably와 PubNub는 짧은 gap은 세션 buffer로, 긴 gap은 History/Storage API로 복구한다.
5. **완료 여부를 관측 가능하게 만든다.** Stripe Terminal은 pending 건수·금액을, Couchbase Lite는 진행률과 `IDLE` 상태를 노출한다.

따라서 수리맵의 최종 목표는 타사의 숫자를 복사해서 정할 수 없다. 지휘관이 허용할 최신 위치 지연, 전체 경로 이력이 필요한 시점, 실제 오프라인 분포와 업무폰 수를 먼저 확인한 뒤 각각의 SLI에 임시 SLO를 정해야 한다.

수리맵과의 관련성은 다음 순서로 보는 것이 안전하다.

1. **직접 비교**: ArcGIS Field Maps, CalTopo, Samsara와 Traccar의 위치·이력 처리
2. **처리 구조 비교**: AWS IoT, PowerSync, Couchbase Lite의 FIFO, 처리율, retry와 완료 상태
3. **운영 위험 비교**: POS·결제의 durable queue, 만료 기한과 pending 관측
4. **범위 대조**: webhook retry와 데이터베이스·리전 DR의 SLA/RTO/RPO

## 조사 범위와 방법

공식 제품 문서, 공식 API·프로토콜 명세, 제품사가 공개한 소스 코드, NIST와 클라우드 사업자의 신뢰성 지침만 사용했다. 블로그를 사용한 경우에도 제품사가 직접 운영하는 공식 문서로 한정했다. 검색 범주는 다음과 같다.

- 현장 위치·지도·수색·fleet 제품
- POS·결제·주문·재고·webhook
- 모바일 offline-first·동기화 플랫폼
- 메시징·협업·IoT store-and-forward
- 데이터베이스 복제·재해 복구
- SLI·SLO·SLA·RTO·RPO·MTD 표준 용어

조사는 공개된 대표 제품과 범주가 반복되는 지점까지 확장했지만, 세상의 모든 서비스를 문자 그대로 전수 조사한 것은 아니다. 공개 문서에 숫자가 없다는 것은 외부 보장이 없다는 뜻이며, 해당 회사 내부에 목표가 없다는 뜻은 아니다.

## 숫자를 읽는 분류 기준

| 분류 | 이 문서에서의 의미 | 수리맵 복구 목표로 바로 쓸 수 있는가? |
| --- | --- | --- |
| **기본·설정 주기/한도** | 수집, 업로드, polling, 동기화 시작 주기나 처리 한도 | 아니요. 시작 조건이나 용량 설정이다. |
| **보존·업로드 기한** | 지나면 데이터가 만료되거나 손실 위험이 커지는 시간 | 아니요. 완료 성능이 아니라 손실 방지 범위다. |
| **재시도 정책** | 실패 뒤 다시 시도하는 횟수·간격·기간 | 아니요. 다음 시도 시각이지 완료 시각이 아니다. |
| **관측·광고 목표** | 공식 문서가 설명하는 typical 값, 예상 처리 시간 또는 비계약 목표 | 비교 자료일 뿐 보장이 아니다. |
| **계약 SLA/SLO** | 측정 조건과 불이행 결과가 있는 외부 계약 또는 명시적 목표 | 범위가 동일할 때만 참고할 수 있다. |
| **DR 목표** | 시스템·리전 장애에서 서비스와 데이터를 되살리는 RTO/RPO | 모바일 한 대의 backlog 복구와 직접 비교할 수 없다. |

`즉시`, `자동`, `as soon as connectivity returns`도 숫자 보장이 아니다. 이는 재연결이 동기화의 trigger라는 동작 설명이며 Android scheduler, 네트워크, backlog 크기와 서버 부하까지 포함한 완료 percentile을 뜻하지 않는다.

## 목표 용어

관련 공식 문서를 찾을 때 사용한 핵심 키워드는 `offline sync`, `store-and-forward`, `reconnect/resume`, `catch-up`, `backlog drain`, `data freshness/staleness`, `last known location`, `delta/base sync`, `reconciliation`, `retry/backoff/jitter`, `retention/TTL/upload deadline`, `SLI/SLO/SLA`, `RTO/RPO/MTD`다. 같은 숫자라도 어느 키워드의 문맥에서 나온 값인지 확인해야 한다.

### 동기화 SLI

- **재연결 후 첫 시도 시간**: 앱이 네트워크 복구를 감지한 시각부터 첫 HTTP 요청을 시작한 시각까지다.
- **최신 위치 가시화 시간**: 최신 좌표의 수집 시각부터 지휘 상황판에 보인 시각까지의 end-to-end 지연이다. 재연결 사건만 평가할 때는 재연결 뒤 생성한 최신 좌표를 대상으로 한다.
- **데이터 freshness**: 현재 시각과 상황판에서 확인 가능한 가장 최신 위치의 수집 시각 차이다.
- **전체 이력 복구 시간**: 재연결 시점에 대기 중이던 모든 요청이 서버와 앱에서 완료된 시각까지다.
- **정합성**: 누락, 중복, 순서 위반과 충돌이 없는지 별도로 평가한다.

Google SRE는 data freshness SLO를 `X%의 데이터가 Y 안에 처리됨`, `가장 오래된 데이터가 Y보다 오래되지 않음`, `job이 Y 안에 완료됨`처럼 표현하고 correctness를 별도 목표로 둔다. 여러 처리 단계가 있으면 사용자에게 보이는 end-to-end 값을 측정하라고 권고한다. [Google SRE Workbook: Data Processing Pipelines](https://sre.google/workbook/data-processing/)

### SLI·SLO·SLA

- **SLI**는 제공된 서비스 수준을 나타내는 정량 지표다.
- **SLO**는 SLI가 만족해야 할 목표값이나 범위다.
- **SLA**는 SLO 불이행의 명시적 결과까지 포함한 제공자와 사용자 사이의 합의다.

Google SRE는 사용자가 중요하게 생각하는 동작에서 출발하고, client-side/end-to-end 지표와 percentile을 사용하며, interactive latency와 bulk throughput처럼 workload가 다르면 목표를 분리하라고 권고한다. 현재 성능을 그대로 목표로 채택하지 말라는 지침도 제시한다. [Google SRE: Service Level Objectives](https://sre.google/sre-book/service-level-objectives/)

### RTO·RPO·MTD

- **RTO**는 장애 뒤 애플리케이션이 사용할 수 없는 최대 허용 시간이다.
- **RPO**는 장애에서 허용할 수 있는 데이터 손실의 시간 범위다.
- **MTD**는 mission/business process 중단이 중대한 피해를 만들기 전까지 허용할 수 있는 총 시간이다.

이 용어는 전체 서비스나 업무 중단을 다루므로, 네트워크가 돌아온 특정 업무폰의 queue 완료 시간에 `RTO`를 바로 붙이면 범위를 혼동할 수 있다. [Azure Well-Architected의 정의](https://learn.microsoft.com/en-us/azure/well-architected/reliability/metrics), [Google Cloud DR의 RTO/RPO 설명](https://docs.cloud.google.com/architecture/disaster-recovery), [NIST MTD 정의](https://csrc.nist.gov/glossary/term/maximum_tolerable_downtime)

## 현장 위치·지도·field service 제품

| 서비스 | 공식 동작과 공개 수치 | 수치 분류 | 전체 backlog 완료 보장 |
| --- | --- | --- | --- |
| **ArcGIS Field Maps** | 오프라인 track은 업로드될 때까지 기기에 보관하고 연결 뒤 자동 업로드한다. 충전 중 track history 업로드 기본 `60초`, 그 외 `10분`, last known location 기본 `60초`; 일반 offline map auto-sync는 연결되고 앱이 foreground일 때 `15분`마다다. [위치 공유](https://doc.arcgis.com/en/field-maps/android/use-maps/track.htm), [offline map sync](https://doc.arcgis.com/en/field-maps/android/use-maps/sync.htm) | **기본·설정 주기** | 없음 |
| **CalTopo/SARTopo** | recorded track은 오프라인에서 로컬 저장한 뒤 재연결 시 자동 sync한다. shared location은 이력을 기기에 보존하지 않아 단절 구간을 완전 복원하지 못하므로 recorded track 병행을 권고한다. 기록 간격은 `30초·10초·5초·2.5초`다. [Team Tracking](https://training.caltopo.com/all_users/team-accounts/team-tracking), [GPS 설정](https://training.caltopo.com/all_users/tools/config) | **기본·설정 주기** | 없음 |
| **TAK/ATAK** | 연결이 성립하면 위치와 chat이 나타난다고 설명하고, 공개 프로토콜에는 `1분` negotiation wait와 `2분` stale 판정 등이 있다. 이는 protocol liveness 값이다. [TAK FAQ](https://tak.gov/faq), [TAK protocol source](https://github.com/TAK-Product-Center/atak-civ/blob/main/takproto/README.txt) | **기본·설정 주기/한도** | 없음 |
| **Samsara Driver App** | 오프라인 action을 queue에 두고, 재연결 뒤 `Saving your changes` 상태가 끝나야 dashboard가 최신임을 설명한다. 짧은 지연을 오프라인으로 오인하지 않도록 배너는 약 `30초` 뒤 표시한다. [Connectivity states](https://kb.samsara.com/hc/en-us/articles/360044700152-Internet-Connectivity-States-and-Best-Practices) | `30초`는 **기본 UI 감지 임계값** | 없음 |
| **Traccar Client SDK** | SQLite FIFO에 위치를 저장하고 실패 시 `5초`에서 `5분`까지 지수 backoff하며 연결을 기다린다. [공식 Client SDK 설명](https://www.traccar.org/traccar-client-sdk/) | **재시도 정책** | 없음 |
| **OsmAnd** | online tracking 간격은 `0초~5분`이며 Time Buffer에 보관한 점을 재연결 후 전송한다. 앱 종료·강제 종료·재부팅에는 미전송 메모리 데이터가 손실될 수 있다. [Trip recording](https://www.osmand.net/docs/user/plugins/trip-recording/) | **기본·설정 주기**, buffer는 **보존 정책** | 없음 |
| **ODK Collect** | finalized form을 queue에 두고 연결이 생기면 auto-send하지만 완료 시간은 제시하지 않는다. [Collect settings](https://docs.getodk.org/collect-settings/) | 수치 없음 | 없음 |
| **ArcGIS Survey123** | 오프라인 응답은 Outbox에 저장하고 재연결 뒤 사용자가 `Send`를 눌러 보낸다. [Submit survey results](https://doc.arcgis.com/en/survey123/capture/field-app/submitsurveyresults.htm) | 수치 없음; **사용자 trigger** | 없음 |
| **Fulcrum** | 로그인, 앱 실행, record 저장 등에서 자동 sync하고 사용자가 full sync할 수도 있다. record 저장 시 해당 record만 push한다. [Mobile sync](https://help.fulcrumapp.com/en/articles/8104466-how-does-syncing-on-the-mobile-app-work) | 수치 없음 | 없음 |
| **Dynamics 365 Field Service / Power Apps** | offline write는 연결 복구 시 자동 push한다. table down-sync는 `5분~1일` 설정이고, canvas app의 delta sync는 online에서 `5분`마다다. 초기 sync는 설계에 따라 수분 이상 걸릴 수 있다. [Field Service sync](https://learn.microsoft.com/en-us/dynamics365/field-service/mobile/offline-data-sync), [Power Apps offline 동작](https://learn.microsoft.com/en-us/power-apps/mobile/mobile-offline-works-overview), [Canvas offline sync](https://learn.microsoft.com/en-us/power-apps/mobile/canvas-mobile-offline-working) | **기본·설정 주기**, 초기 sync는 **관측·안내 값** | 없음 |
| **ServiceNow Mobile** | Outbox는 원래 순서대로 sync하며, 일부 동일 form의 연속 항목은 합쳐 최신 상태만 남길 수 있다. cache 기본 만료 `48시간`, refresh 기본 `240분`은 cache 정책이다. [Offline action](https://www.servicenow.com/docs/r/mobile/action-item-general-guideline.html), [Offline mode](https://www.servicenow.com/docs/r/mobile/mobile-offline-mode.html) | **기본·설정 주기/보존 정책** | 없음 |
| **SAP Service and Asset Manager** | offline→online 전환, foreground, DB save 등 구성한 사건에서 auto-sync한다. 공식 예시의 `15분` download는 구현 예시다. [Auto-sync](https://help.sap.com/docs/service-asset-manager/sap-service-and-asset-manager-configuration-guide/auto-sync-configuration), [Offline OData](https://help.sap.com/doc/f53c64b93e5140918d676b927a3cd65b/Cloud/en-US/docs-en/guides/features/offline/mdk/initialization-and-sync.html) | **설정/예시 주기** | 없음 |

직접 비교에서 가장 중요한 점은 ArcGIS와 CalTopo가 **현재 위치를 빨리 보여 주는 경로**와 **완전한 이동 이력을 보존하는 경로**를 같은 값으로 취급하지 않는다는 것이다.

## POS·결제·주문·재고

| 서비스 | 공식 동작과 공개 수치 | 수치 분류 | 전체 backlog 완료 보장 |
| --- | --- | --- | --- |
| **Shopify POS** | 오프라인 주문·재고·결제를 로컬에 두고 재연결 후 자동 sync한다. 카드 결제는 가능한 빨리, 이상적으로 `24시간` 안에 연결하라고 권고한다. 초기 카탈로그 sync는 규모에 따라 `수분~1시간 이상`으로 안내한다. [Offline payments](https://help.shopify.com/en/manual/sell-in-person/shopify-pos/selling-offline/offline-payments), [Offline features](https://help.shopify.com/en/manual/sell-in-person/shopify-pos/selling-offline/offline-features), [Launch checklist](https://help.shopify.com/en/manual/intro-to-shopify/shopify-pos-launch-checklist) | `24시간`은 **업로드 위험 완화 권고**, 초기 sync는 **관측·안내 값** | 없음 |
| **Square Offline Payments** | 연결 뒤 자동 처리한다. pending 결제는 offline session 시작 후 `72시간` 안에 업로드해야 하고 `24시간` 안을 권고하며, `72시간` 뒤에는 만료된다. [Offline payments](https://squareup.com/help/us/en/article/7777-process-card-payments-with-offline-mode) | `72시간`은 **업로드 기한**, `24시간`은 **위험 완화 권고** | 없음 |
| **Stripe Terminal** | SDK/reader가 재연결 후 저장 결제 전송을 자동 시작하고 pending 건수·금액과 network 상태를 제공한다. reader는 `30일` 안에 같은 location에서 online update가 필요하다. [Offline payments](https://docs.stripe.com/terminal/features/operate-offline/collect-card-payments), [Overview](https://docs.stripe.com/terminal/features/operate-offline/overview) | `30일`은 **오프라인 사용 자격/캐시 기한** | 없음 |
| **Adyen** | 재연결하면 저장 결제를 전송한다. 모바일 store-and-forward는 보안 attestation 후 `24시간` 연속 offline 운용 가능하고, Auto Rescue는 eligible failure를 `1 calendar month` 재시도한다. [Offline payments](https://docs.adyen.com/point-of-sale/offline-payment/) | `24시간`은 **운영 window**, 한 달은 **재시도 정책** | 없음 |
| **Toast** | offline payment를 queue에 두고 online 후 background 처리한다. offline 활동 `40초` 뒤 UI를 표시하고, POS 기기는 가능하면 `24시간`, 늦어도 `3일` 이내 연결하도록 권고한다. 복구 시 주문 burst가 주방을 압도할 수 있다고 경고한다. [Offline mode](https://doc.toasttab.com/doc/platformguide/adminOfflineModeOverview.html), [Offline card payments](https://doc.toasttab.com/doc/platformguide/adminOfflineCCPayments.html) | `40초`는 **감지/UI 임계값**, `24시간·3일`은 **위험 완화 권고** | 없음 |
| **Clover** | Mini/Flex는 기본적으로 최대 `7일`간 offline payment를 받을 수 있다. [Handle offline payments](https://docs.clover.com/dev/docs/handling-offline-payments) | **오프라인 운영/위험 한도** | 없음 |
| **Lightspeed Retail** | 연결 복구 후 저장 결제를 자동 전송한다. offline payment는 Retail 판매 이력·재고에는 sync하지 않고 Payments report에만 반영한다. [Offline payments](https://retail-support.lightspeedhq.com/hc/en-us/articles/27640682216731-Processing-payments-in-standalone-and-offline-mode) | 수치 없음; **일관성 범위 축소** | 없음 |
| **Amazon SP-API Feeds** | 같은 feed type은 `20분`보다 자주 제출하지 말라고 권고하며, 순차 처리되는 작은 feed를 자주 보내면 backlog가 생긴다고 설명한다. 고부하에서는 처리가 `최대 8시간` 걸리는 경우가 드물지 않다고 안내한다. [Feeds best practices](https://developer-docs.amazon.com/sp-api/lang-US/docs/feeds-api-best-practices) | `20분`은 **권고 cadence**, `8시간`은 **관측·안내 값** | SLA 아님 |
| **Amazon inventory events** | inventory change event는 매시 시작 `5분` 뒤 이전 시간 데이터를 보내며, 최대 `24시간` 지연 데이터가 포함될 수 있다. [Notification types](https://developer-docs.amazon.com/sp-api/docs/notification-type-values) | **batch cadence·late-data window** | freshness SLA 아님 |
| **Google Merchant inventory** | local inventory는 적어도 매일 갱신을 권고하고 hosted file은 `24시간`마다 가져온다. regional inventory는 `14일` 안에 refresh하지 않으면 stale 처리된다. [Local inventory](https://support.google.com/merchants/answer/7677785?hl=en), [Hosted file](https://support.google.com/merchants/answer/15182106?hl=en), [Regional inventory](https://support.google.com/merchants/answer/16786149?hl=en) | **권고 cadence·보존/만료 정책** | 없음 |

결제 서비스의 `24시간·72시간`은 카드 승인 실패·chargeback·데이터 만료 위험에서 나온 값이다. 지휘관이 보는 위치 freshness와 사용자 여정도, 실패 결과도 다르므로 수리맵의 분 단위 목표 근거가 될 수 없다.

## Offline-first 데이터와 IoT store-and-forward

| 서비스 | 공식 동작과 공개 수치 | 수치 분류 | 전체 backlog 완료 보장 |
| --- | --- | --- | --- |
| **Firestore** | 재연결 시 local change를 backend와 sync하고 같은 document 충돌은 last-write-wins로 처리한다. [Offline data](https://firebase.google.com/docs/firestore/manage-data/enable-offline) | 수치 없음 | 없음 |
| **Firebase Realtime Database** | offline write를 queue에 두고 persistence 사용 시 앱 재시작 뒤에도 보존해 재연결 시 전송한다. transaction은 앱 재시작을 넘겨 보존되지 않는다. [Offline capabilities](https://firebase.google.com/docs/database/android/offline-capabilities), [Read and write](https://firebase.google.com/docs/database/android/read-and-write) | 수치 없음; persistence 범위만 보장 | 없음 |
| **Couchbase Lite** | transient error를 지수 backoff하며 최대 retry wait와 heartbeat 기본값은 각각 `300초`, one-shot 기본 `9회`, continuous는 계속 retry한다. `IDLE`은 현재 변경분 catch-up 완료 상태다. [Android replication](https://docs.couchbase.com/couchbase-lite/current/android/replication.html) | **재시도 정책** | 없음 |
| **AWS AppSync / Amplify DataStore** | offline 뒤 delta query로 따라잡고 delta-log TTL보다 오래 offline이면 base query를 다시 수행한다. reconnect는 exponential backoff+jitter다. DataStore base sync 기본 `24시간`은 global catch-up cadence다. [Delta Sync](https://docs.aws.amazon.com/appsync/latest/devguide/tutorial-delta-sync.html), [Amplify conflict resolution](https://docs.amplify.aws/gen1/android/build-a-backend/more-features/datastore/conflict-resolution/) | **재시도·기본 catch-up 정책** | 없음 |
| **PowerSync** | reconnect 뒤 upload를 시작하고 pending upload를 약 `20초`마다 깨우며 오류 retry 기본 `5초`, Kotlin upload throttle 기본 `1초`다. queue는 blocking FIFO다. [Client integration](https://docs.powersync.com/configuration/app-backend/client-side-integration), [Client architecture](https://docs.powersync.com/architecture/client-architecture) | **재시도·throttle 기본값** | 없음 |
| **AWS IoT Core MQTT** | persistent session 재연결 후 저장 QoS 1 메시지를 client당 최대 `10건/초`로 전송한다. MQTT 3 session expiry 기본 `1시간`, 설정 상한 `7일`이다. [MQTT persistent sessions](https://docs.aws.amazon.com/iot/latest/developerguide/mqtt.html), [IoT quotas](https://docs.aws.amazon.com/en_en/general/latest/gr/iot-core.html) | `10건/초`는 **제품 처리 한도**, `1시간·7일`은 **보존 설정** | 시간 SLO 없음 |
| **Azure IoT Edge** | upstream message를 local store에 두고 재연결 후 저장 순서대로 즉시 전달하지만, 완료는 connection speed와 IoT Hub latency 등에 달려 있다고 명시한다. TTL 기본 `7,200초`다. [Offline capabilities](https://learn.microsoft.com/en-us/azure/iot-edge/offline-capabilities) | TTL은 **보존 설정** | 없음 |
| **MQTT 5 표준** | session expiry, message expiry와 QoS 완료 조건을 정의하지만 reconnect/catch-up latency는 정하지 않는다. [OASIS MQTT 5.0](https://docs.oasis-open.org/mqtt/mqtt/v5.0/mqtt-v5.0.html) | **프로토콜 정책** | 없음 |
| **FCM** | offline message TTL은 기본·최대 `4주`이며 연결 뒤 pending message를 전달한다. collapse key를 사용하면 같은 그룹에서 과거 메시지를 최신 메시지로 대체할 수 있다. [Message lifespan](https://firebase.google.com/docs/cloud-messaging/customize-messages/setting-message-lifespan) | **보존 기한·coalescing 정책** | 없음 |

AWS IoT Core는 backlog 처리율을 공개한 드문 사례다. `10건/초`는 완료 시간이 아니라 처리 한도이므로 queue가 클수록 완료가 오래 걸린다. 일반화하면 안정 상태의 catch-up 시간은 backlog 크기뿐 아니라 **지속 drain rate와 복구 중 새 요청률의 차이**에 의해 결정된다. 이는 제품 문서에서 도출한 용량 모델이지 수리맵 목표 숫자가 아니다.

대규모 device reconnect는 동시에 시작하면 다시 서버를 압박할 수 있다. Azure IoT는 기본 exponential backoff with jitter를 권고하며, 대규모 동시 재접속이 DDoS와 유사한 부하를 만들 수 있다고 설명한다. [Azure IoT device reconnection](https://learn.microsoft.com/en-us/azure/iot-hub/concepts-manage-device-reconnections)

## 메시징·협업·event delivery

| 서비스 | 공식 동작과 공개 수치 | 수치 분류 | 전체 backlog 완료 보장 |
| --- | --- | --- | --- |
| **Ably** | disconnect가 `2분` 미만이면 세션을 resume해 누락 메시지를 순서대로 전달하고, 더 길면 History API를 사용한다. SDK는 `15초`마다 최대 `2분` 재연결을 시도한다. [Recovery](https://ably.com/docs/platform/architecture/connection-recovery), [Connection states](https://ably.com/docs/connect/states), [History](https://ably.com/docs/storage-history/history) | `2분`은 **transparent recovery eligibility window**, `15초`는 **재시도 정책** | 없음 |
| **PubNub** | 자동 catch-up buffer 기본 `100건`, 최대 약 `16분`이며 더 긴 단절에는 persistent history 사용을 권고한다. test keyset retention 기본 `7일`이다. [Connection management](https://www.pubnub.com/docs/general/setup/connection-management), [Storage](https://www.pubnub.com/docs/general/storage) | **buffer 한도·보존 정책** | 없음 |
| **Azure Web PubSub** | reliable client가 상태와 누락 메시지를 복구하며 recovery failure를 최대 `1분` retry한다. [Reliable clients](https://learn.microsoft.com/en-us/azure/azure-web-pubsub/howto-develop-reliable-clients) | **재시도/window 정책** | 없음 |
| **Microsoft Teams** | unsent chat message를 최대 `24시간` 저장하고 online 뒤 보낸다. 이후에는 사용자가 resend/delete를 결정한다. [Connectivity issues](https://learn.microsoft.com/en-us/microsoftteams/connectivity-issues) | **로컬 보존·자동 전송 window** | 없음 |
| **Discord / Matrix** | Discord는 sequence로 session을 resume해 gap을 replay하고, Matrix는 `since`/`next_batch`와 `/messages`로 제한된 timeline gap을 복구한다. [Discord Gateway](https://docs.discord.com/developers/events/gateway), [Matrix sync](https://spec.matrix.org/latest/client-server-api/#get_matrixclientv3sync) | 수치 없음; **delta/reconciliation protocol** | 없음 |
| **Notion / Google Sheets / OneNote** | offline 변경을 local에 두고 reconnect 뒤 delta·merge sync한다. [Notion offline](https://www.notion.com/en-gb/help/guides/working-offline-in-notion-everything-you-need-to-know), [Google Sheets offline](https://support.google.com/docs/answer/12111392?hl=en), [OneNote sync](https://support.microsoft.com/en-US/OneNote/onenote-help-and-learning/sync-a-notebook-in-onenote) | 수치 없음 | 없음 |
| **Square webhooks** | 대부분 event가 원 event 뒤 `60초 훨씬 이내` 도착한다고 SLA라는 표현을 사용한다. 실패 delivery는 지수 backoff로 최대 `24시간` 재시도한다. [Webhooks overview](https://developer.squareup.com/docs/webhooks/overview) | `60초`는 **광고/명명된 SLA**, `24시간`은 **재시도 정책** | mobile sync와 범위가 다름 |
| **Shopify webhooks** | 실패 delivery를 최대 `8회/4시간` 재시도하고 계속 실패하면 subscription을 제거해 별도 reconciliation이 필요하다. [Troubleshooting](https://shopify.dev/docs/apps/build/webhooks/troubleshoot) | **재시도 정책** | 없음 |
| **Stripe webhooks** | live event를 최대 `3일` 지수 backoff로 재시도하고 event 순서는 보장하지 않는다. [Webhooks](https://docs.stripe.com/webhooks) | **재시도 정책** | 없음 |
| **Slack / Microsoft Graph webhooks** | Slack은 `3초` 안의 ACK와 거의 즉시·`1분`·`5분` retry를, Graph는 `3초` ACK와 최대 `4시간` retry를 요구한다. [Slack Events API](https://docs.slack.dev/apis/events-api/), [Graph change notifications](https://learn.microsoft.com/en-us/graph/change-notifications-delivery-webhooks) | ACK는 **ingestion contract**, 나머지는 **재시도/backpressure 정책** | end-user freshness와 다름 |

메시징 제품은 짧은 session recovery와 긴 history fetch를 분리하고, 최신 상태만 필요하면 collapse/coalescing을 선택한다. 이는 위치 이력 전체를 버리라는 뜻이 아니라, 최신 위치와 과거 경로에 서로 다른 우선순위와 완료 기준을 둘 수 있다는 근거다.

## 데이터베이스 복제와 재해 복구

서버·리전 복제 수치는 정교하게 정의되어 있지만 모바일 queue 복구와 측정 경계가 다르다. 비교 목적은 숫자를 채택하는 것이 아니라 범위 차이를 확인하는 것이다.

| 서비스·지침 | 공식 수치 | 수치 분류 | 수리맵과의 범위 차이 |
| --- | --- | --- | --- |
| **DynamoDB Global Tables** | MREC write는 보통 `1초` 안에 다른 리전으로 전파되지만 latency SLA는 없고, MREC RPO/RTO는 초 단위, MRSC RPO는 `0`이다. [Global table design](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/bp-global-table-design.html) | `1초`는 **관측·광고 값**, 나머지는 **DR 목표** | server-region replication이며 device backlog가 아니다. |
| **Aurora Global Database** | cross-region replication typical `<1초`, promotion `<1분`을 안내한다. [Availability and durability](https://docs.aws.amazon.com/rds/latest/auroraextendedcontent/aurora-faq-availability-and-durability.html) | **관측·광고 값/DR** | service usable 시점과 모든 secondary 복구 완료가 다르다. |
| **Azure SQL** | active geo-replication 안내에는 failover trigger 뒤 RTO `30초`, RPO `5초` 등의 값이 있다. [SQL Server DR options](https://learn.microsoft.com/en-us/azure/site-recovery/site-recovery-sql) | **DR 목표** | database failover이며 앱 전송·Web 표시를 포함하지 않는다. |
| **Azure Cosmos DB** | bounded staleness는 version 수 K 또는 시간 T로 읽기 지연을 제한하고, multi-region의 최소 T는 `300초`다. strong consistency는 RPO `0`이다. [Consistency levels](https://learn.microsoft.com/en-us/azure/cosmos-db/consistency-levels), [Cosmos reliability](https://learn.microsoft.com/en-us/azure/reliability/reliability-cosmos-db-nosql) | **일관성 설정·DR 목표** | 읽을 수 있는 최신 버전 경계이며 client queue drain이 아니다. |
| **AWS DR 전략** | backup/restore는 RPO hours·RTO `24시간 이하`, pilot light는 RPO minutes·RTO tens of minutes, warm standby는 RPO seconds·RTO minutes, active-active는 near-zero다. [AWS Well-Architected DR](https://docs.aws.amazon.com/wellarchitected/2022-03-31/framework/rel_planning_for_recovery_disaster_recovery.html) | **DR 전략별 목표 범위** | 비용·복잡도별 disaster architecture 예시다. |
| **Google Cloud DR 예시** | 한 예시 조직은 e-commerce·real-time payment를 Tier 1로 분류해 zone/region outage RTO·RPO `0`을 가정한다. Google은 이를 요구사항 수집 예시라고 명시한다. [Cloud infrastructure DR](https://docs.cloud.google.com/architecture/disaster-recovery) | **가상 조직의 DR 목표 예시** | 이커머스 업계 표준이나 mobile sync benchmark가 아니다. |

`RTO 5분`과 `전체 오프라인 좌표를 5분 안에 반영`은 같은 문장이 아니다. 전자는 시스템 사용 불능을 허용하는 범위이고, 후자는 이미 로컬에 안전하게 보존된 데이터가 end-to-end로 따라잡는 시간이다.

## 범주를 가로지르는 패턴

### 최신 상태와 전체 이력은 다른 사용자 여정이다

- ArcGIS는 last known location과 track history를 다른 주기로 전송한다.
- CalTopo는 live shared location과 durable recorded track을 구분한다.
- FCM은 collapse key로 과거 상태를 최신 상태로 대체할 수 있다.
- ServiceNow는 일부 같은 form의 연속 mutation을 합칠 수 있다.
- Google SRE는 우선순위가 다른 data를 queue나 job으로 분리해 높은 우선순위를 먼저 처리할 수 있다고 설명한다. [Data isolation/load balancing](https://sre.google/workbook/data-processing/)

수리맵에서 지휘관의 최신 위치와 감사·분석용 전체 수색 경로가 다른 시간 목표를 가질 가능성이 높다는 근거다. 다만 좌표 이력을 coalesce하거나 queue를 분리할지는 현장 요구를 확인한 뒤 별도 설계 판단으로 다뤄야 한다.

### 주기·보존·재시도 숫자는 완료 시간을 만들지 않는다

- `5분마다 sync`는 5분 안에 완료한다는 뜻이 아니다.
- `72시간 보존`은 72시간 걸려도 된다는 뜻이 아니다.
- `최대 5분 backoff`는 전체 복구가 5분이라는 뜻이 아니다.
- `RPO 5초`는 모든 mobile data가 5초 안에 상황판에 보인다는 뜻이 아니다.

### 복구 burst는 정상 traffic과 함께 설계해야 한다

Toast는 복구된 주문 burst의 downstream 영향을 경고하고, Amazon은 작은 feed를 너무 자주 보내면 순차 queue가 막힌다고 설명한다. Azure IoT는 대규모 동시 reconnect에 jitter를 권고한다. 따라서 수리맵도 backlog drain만 따로 재는 것이 아니라 복구 중 생기는 새 위치 요청, 서버 queue와 상황판 반영을 함께 측정해야 한다.

### 완료 신호가 있어야 운영자가 복구를 판단할 수 있다

`자동 sync` 문구만으로는 복구 여부를 알 수 없다. pending 수, oldest pending age, 마지막 ACK 시각, 최신 위치의 수집 시각, 실패·재시도 상태와 backlog empty 시각을 관측해야 한다.

## 수리맵 의사결정에 적용

### 조사 당시 측정값과 5분의 의미

[Issue #8의 최적화 전 혼합 복구 테스트](../issues/8-gps-collection-transmission-basis.md)는 업무폰 모델 468개가 30분치 재전송 요청 56,160건을 4분 14초에 처리했고, 복구 중 새 요청을 포함한 65,520건은 약 5분 6초에 처리했다고 기록한다. 새 요청의 전송 시작 지연은 p95 3분 32초, 최대 3분 59초였다.

이 결과는 당시 부하 모델·구현에서 얻은 **측정값**이다. Issue에도 명시됐듯 `5분`은 해당 시험의 목표였으며 현장 복구 기준이나 SLA가 아니다. 이번 외부 조사에서도 이를 정식 목표로 승격할 근거는 발견되지 않았다.

이후 경로 저장을 최적화한 [2026-09-04 재측정](../issues/8-gps-collection-transmission-basis.md#재측정-복구-시간-단축과-처리량-증가)에서는 30분치 재전송 완료 최댓값이 1분 5초, 새 요청의 전송 시작 지연 p95가 36.27초로 줄었다. 이 역시 해당 시험의 측정값이며, 실제 업무폰의 재연결부터 상황판 표시까지 보장하는 목표가 아니다. 이전 수치를 현재 성능으로 사용하지 않는다.

### 별도로 정의할 SLI

| SLI | 시작 | 종료 | 답하는 질문 |
| --- | --- | --- | --- |
| `reconnect_to_first_attempt` | 앱이 유효한 network를 감지 | 첫 요청 시작 | 업무폰은 복구를 얼마나 빨리 시작하는가? |
| `latest_position_end_to_end` | 최신 좌표 수집 | 지휘 상황판 표시 | 지휘관이 보는 위치는 얼마나 오래됐는가? |
| `reconnect_to_latest_visible` | network 복구 감지 | 복구 뒤 생성한 최신 좌표 표시 | backlog가 있어도 현재 위치를 언제 볼 수 있는가? |
| `reconnect_to_backlog_drained` | network 복구 감지 | 복구 시점의 pending 요청이 모두 ACKED·반영 | 전체 이력은 언제 완성되는가? |
| `oldest_pending_age` | 가장 오래된 pending 생성 | 현재 시각 | queue가 따라잡고 있는가? |
| `recovery_correctness` | 기대 좌표·요청 집합 | 저장·표시된 집합 비교 | 누락·중복·순서 위반이 없는가? |

Android WorkManager는 constraint 충족 뒤 작업이 언젠가 실행됨을 보장할 뿐 즉시 실행 시각을 보장하지 않는다. 따라서 `reconnect_to_first_attempt`를 0으로 가정하지 말고 실제 업무폰에서 측정해야 한다. [WorkManager API](https://developer.android.com/reference/androidx/work/WorkManager.html)

### 목표를 정하기 전에 필요한 입력

1. **현장 허용 stale time**: 수색 지휘 중 최신 위치가 몇 초·몇 분 늦으면 의사결정에 문제가 생기는가?
2. **전체 이력의 업무 기한**: 완전한 경로가 실시간 지휘, 교대 인계, 사건 종료, 사후 검토 중 어느 시점까지 필요한가?
3. **우선순위**: 재연결 뒤 과거 좌표 FIFO 보존과 새 위치 우선 표시 중 어느 동작이 필요한가? 둘 다 필요하면 서로 다른 queue/SLO가 필요한가?
4. **현실적인 workload**: 업무폰 수, offline 지속 시간 분포, 좌표·batch 생성률, reconnect 동시성, foreground/background·재부팅 조건은 무엇인가?
5. **목표 미달 결과**: 현장 경고, 자동 재시도, 운영자 개입, 기능 제한 중 무엇이 필요한가?

### 조사 당시 제안한 결정 순서

1. 현장 지휘 경험자에게 최신 위치와 전체 이력 각각의 허용 지연을 확인한다.
2. Android 수집부터 Web 표시까지 측정 지점을 넣고 위 SLI의 p50·p95·p99와 최대값을 기록한다.
3. 실제에 가까운 offline 시간·업무폰 수·새 요청률로 baseline을 측정한다.
4. 사용자 영향, 현재 측정값과 구현 비용을 함께 검토해 **임시 SLO**를 정한다.
5. 임시 SLO를 만족하지 못할 때만 최신 위치 우선 처리, queue 분리·coalescing, 처리량 증설 등을 별도 Issue로 검토한다.

이번 조사만으로는 `최신 위치 N초`, `전체 이력 N분` 같은 숫자를 확정하지 않는다. 외부 서비스의 수치는 질문 범위와 테스트 시나리오를 만드는 비교 자료이며, 최종 숫자는 수리맵 사용자와 end-to-end 측정에서 나와야 한다.

조사 후 사용자는 현장 허용 지연을 별도로 확인하기 어렵다고 밝혔다. 따라서 현장 인터뷰를 다음 시험의 선행 조건으로 삼지 않는다. 가능한 빠른 복구를 지향하되, 먼저 측정 조건·정합성·최신 요청의 지연을 함께 확인하고 목표 수치는 따로 합의한다.

## 한계

- 제품별 public 문서의 깊이와 표현이 달라 동일한 측정 창으로 비교하지 못했다.
- 지역, 요금제, SDK와 버전에 따라 기본값이 달라질 수 있다.
- `즉시`, `near-real-time`, `대부분 60초 이내`처럼 percentile과 측정 지점이 없는 표현은 계약 보장으로 취급하지 않았다.
- POS 결제 손실, webhook delivery, DB region failover와 경찰 위치 freshness는 사용자 영향이 달라 숫자를 직접 이전할 수 없다.
- 공개 문서에 없는 내부 SLO와 고객별 계약은 확인할 수 없다.
- 수리맵의 현장 인터뷰와 추가 실기기·상황판 측정은 이 문서 범위에 포함하지 않았다.
