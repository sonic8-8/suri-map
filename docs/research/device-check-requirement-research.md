# 업무폰 등록 확인 요구 조사

- 조사일: 2026-11-17
- 질문: 업무 애플리케이션 서버가 "요청이 등록된 업무용 단말에서 왔는지"를 직접 확인해야 한다는 공식 요구가 있는가? 업무용 단말을 쓰는 다른 서비스는 이 확인을 어느 계층에서 하는가?
- 상태: 조사 결과다. 수리맵 설계 결정은 하지 않았다. [단말 신뢰 계층 조사](device-trust-layer-research.md)에서 다룬 NIST SP 800-207, SP 800-124r2, Entra 공유 단말 모드, Knox Manage, Play Integrity 내용은 반복하지 않는다.

이 문서에서 "사실"은 출처에 적힌 내용이다. 조사자의 해석은 문장 앞에 **추론:** 으로 표시한다. 출처의 접근일은 모두 2026-11-17이다. 한국 규정의 인용문은 원문 표기를 그대로 옮겼다.

## 결론 요약

- 사실: 「국가 사이버보안 기본지침」(국가정보원, 2026.5.1.) 제56조제1항제1호에는 "통신단말기에 대한 장치 및 개별사용자 인증"이 보안대책 항목으로 적혀 있다. 이 의무의 주체는 "각급기관의 장"이다. 조문에는 이 인증을 어느 구성 요소가 수행하는지 적혀 있지 않다. ([국가 사이버보안 기본지침 원문](https://www.ncsc.go.kr/template/resources/file/nis_guide_lines_2023_1_31.pdf))
- 사실: 「국가 망 보안체계 보안 가이드라인 보안통제 항목 해설서」 1.0의 단말인증(DA) 항목에는 등록된 단말만 접근하도록 하라고 적혀 있다. 같은 항목의 구현 예시와 적용 기술 표에는 NAC, 802.1X, X.509 클라이언트 인증서, TPM, IAM 연계가 적혀 있다. 업무 애플리케이션 서버가 단말 ID를 대조하는 방식은 예시에 없다. ([N2SF 해설서 사본](https://mnusecu.mokpo.ac.kr/bbs/mnusecu/1544/250570/download.do))
- 사실: CJIS Security Policy v6.1의 IA-3에는 기관 관리 단말을 "네트워크 연결을 맺기 전에" 고유하게 식별하고 인증하라고 적혀 있다. 5.20.7.3에는 단말에 둔 단말 인증서만으로는 권한 있는 사용자가 단말을 쓰고 있다는 증거가 되지 않는다고 적혀 있다. ([CJIS Security Policy v6.1](https://le.fbi.gov/file-repository/cjis_security_policy_v6-1_20260625.pdf))
- 사실: Microsoft 개발자 안내서에는 조건부 접근 정책이 서비스용 토큰을 요청하는 시점에 평가된다고 적혀 있다. 같은 안내서에는 대부분의 앱이 조건부 접근 때문에 코드를 바꿀 필요가 없다고 적혀 있다. ([Entra 조건부 접근 개발자 안내](https://learn.microsoft.com/en-us/entra/identity-platform/v2-conditional-access-dev-guide))
- **추론:** 조사한 공식 자료 가운데 업무 애플리케이션 서버가 클라이언트가 보낸 단말 ID를 등록 목록과 대조해야 한다고 요구한 자료는 찾지 못했다. 한국과 미국 자료는 모두 단말 인증을 기관의 의무로 정한다. 구현 예시는 망 접근 제어, 인증서, IdP와 IAM, MDM 쪽에 있다.
- **추론:** 단말 ID를 등록 목록과 대조하는 일을 "입력 검증"으로 직접 다룬 1차 자료는 찾지 못했다. OWASP의 의미 검증(semantic validation)과 허용 목록 원칙, OWASP API Security의 객체 수준 권한 확인(BOLA) 원칙이 가장 가까운 근거다.

## 1. 한국 공공 규정

### 국가 사이버보안 기본지침(2026.5.1.)

- 사실: 국가사이버안보센터 누리집에 있는 PDF의 표지에는 「국가 사이버보안 기본지침」, "2026. 5. 1", "국가정보원 지침"이 적혀 있다. 파일 이름은 `nis_guide_lines_2023_1_31.pdf`이지만 본문에는 2026.5.1. 개정 표시가 들어 있다. 이전 조사에서 2차 자료로만 본 「국가 정보보안 기본지침」 제56조와 같은 번호, 같은 제목의 조문이 이 판에 있다.
- 사실: 제3조에는 이 지침을 "각급기관의 사이버보안업무에 적용한다"고 적혀 있다.
- 사실: 제56조(업무용 통신단말기 보안) 제1항의 원문은 다음과 같다. "각급기관의 장은 업무용 통신단말기를 이용하여 업무자료 등 중요정보를 소통ㆍ관리하고자 할 경우 다음 각 호의 사항을 포함한 보안대책을 수립ㆍ시행하여야 한다. 1. 통신단말기에 대한 장치 및 개별사용자 인증 2. 제어신호 및 통화내용 등 데이터 암호화 3. 분실ㆍ탈취ㆍ훼손 등에 대비한 관리적ㆍ물리적ㆍ기술적 보안대책"
- 사실: 제56조제3항에는 개별사용자용 인증과 암호화 디지털정보를 발급할 수 없으면 「정보통신기기 암호기술 적용지침」을 따르라고 적혀 있다.
- 사실: 제57조(모바일 업무 보안) 제1항에는 휴대폰과 태블릿 PC로 "내부행정업무, 현장행정업무 및 대민서비스업무 등"의 모바일 업무환경을 구축할 때 보안대책을 세우라고 적혀 있다. 제2항에는 세부 사항은 「국가ㆍ공공기관 모바일 활용업무에 대한 보안가이드라인」을 따르라고 적혀 있다.
- 사실: 제43조제3항제4호에는 업무용 무선랜에 대해 "비(非)인가 단말기의 무선랜 접속 차단 및 무선랜 이용 단말 식별기술을 적용"하라고 적혀 있다. 같은 항 제5호에는 IEEE 802.1X와 AAA 기술로 상호인증을 하는 무선랜 인증제품을 쓰라고 적혀 있다.
- 사실: 제52조의2(통합인증체계) 제1호에는 SAML, OAuth, OIDC 같은 표준을 쓰라고 적혀 있다. 같은 조 제4호에는 "인증서버와 연계시스템 간 상호 신뢰 검증 및 세션무결성 확보"가 적혀 있다.
- 사실: 제53조(서버 보안) 제1항제2호에는 "개별사용자별 자료 접근범위를 서버에 등록하여 인가여부를 식별"하라고 적혀 있다. 이 조의 서버 보안대책 7개 호에는 단말 인증 항목이 없다.
- 사실: 제74조(단말기 보안) 제1항에는 기관이 지급한 스마트폰과 태블릿을 포함한 단말기의 보안관리 책임을 개별사용자가 진다고 적혀 있다.
- **추론:** 제56조는 장치 인증을 요구한다. 다만 의무의 주체는 기관장이다. 조문은 그 인증을 업무 서버가 해야 한다고 정하지 않는다. 서버에 대한 조문(제53조)이 요구하는 것은 개별사용자별 인가다. 연계시스템에 대한 조문(제52조의2)이 요구하는 것은 인증서버와의 상호 신뢰 검증이다.

### N2SF 보안통제 항목 해설서 1.0(2025.9.)

- 원문 확인 방법: 국가사이버안보센터 누리집의 게시판은 스크립트로 그려져 읽지 못했다. 대신 목포대 정보보안 게시판에 올라온 PDF 사본을 읽었다. 사본의 문서이력에는 "2025.9. 1.0 「국가 망 보안체계 보안 가이드라인 – 보안통제 항목 해설서」 발간"이 적혀 있다.
- 사실: 기본지침 제39조의5제1항에는 등급별 보안통제에 관해 「국가 망 보안체계 보안 가이드라인」의 보안통제 항목을 따르라고 적혀 있다.
- 사실: 해설서의 단말인증(DA) 정의는 "사용자 또는 관리자 단말의 접근을 허용하기에 앞서, 해당 단말의 식별, 무결성 검증, 보안 상태 평가를 수행하여 신뢰된 단말만 시스템 자원에 접근할 수 있도록 하는 통제항목"이다.
- 사실: N2SF-DA-3(단말 식별 및 인증)의 설명은 "단말의 고유 식별자(MAC, TPM, 인증서 등)를 통해 단말을 식별하고, 등록된 단말만 인증을 통해 시스템에 접근할 수 있도록 한다"이다. 적용 등급 표시는 기밀(C)과 민감(S)이다.
- 사실: N2SF-DA-3(1)(인가 단말 인증)에는 등록되어 인가된 단말에 한해 "인증수단(디지털 인증서, 장치 지문 등)을 통해" 접근을 허용하라고 적혀 있다.
- 사실: N2SF-DA-3(2)(비인가 단말 인증)에는 등록되지 않은 단말의 인증 요청을 차단하거나 별도 네트워크 구역으로 격리하라고 적혀 있다. 적용 등급 표시는 기밀(C)뿐이다.
- 사실: DA 항목의 구현 방법 예시에는 "등록되지 않았거나 관리되지 않은 단말의 접근 시도는 NAC 또는 802.1X 기반 인증 시스템을 통해 차단한다"가 적혀 있다. 같은 예시에는 "X.509 기반 클라이언트 인증서를 발급받아 사용자 계정과 단말을 연계"하라는 문장이 있다.
- 사실: DA 항목의 적용 기술 표에는 "단말 식별: MAC, TPM, X.509 인증서 기반 식별", "비인가 단말 차단: NAC, 802.1X", "인증된 단말의 범위 제한: IAM, NAC 등을 통한 접속 범위 제한"이 적혀 있다.
- 사실: 모바일 단말(MD) 항목의 N2SF-MD-M1(장비 식별 및 사용자 연동 관리)에는 "모바일 장치 고유 ID와 사용자 계정을 연동하여 통합 식별 및 통제를 수행한다"고 적혀 있다. 구현 예시에는 MDM 연동 계정, AD 기반 SSO, "통합 디바이스 관리 시스템과 IAM 시스템을 연계하여 단말 상태 기반 인증과 접근 통제를 구성"하라는 문장이 있다.
- 사실: 해설서의 해당 부분은 "구현 방법 예시"라는 제목 아래에 있다.
- **추론:** 해설서는 등록 단말 확인을 요구한다. 다만 확인 근거로 든 것은 하드웨어 키, 인증서, 장치 지문처럼 위조하기 어려운 값이다. 확인 위치로 든 것도 NAC, 802.1X, IAM, MDM이다. 클라이언트가 문자열로 보낸 ID를 업무 서버가 대조하는 방식은 예시에 없다. 예시는 구현 방법의 한 예이고 강제 목록은 아니다.

### 모바일 전자정부 서비스 관리 지침(행정안전부 예규)

- 원문 확인 범위: 행정안전부 누리집에 올라온 예규 제100호(2020.1.3. 일부개정) HWP 전문을 읽었다. 국가법령정보센터에는 현행 판이 예규 제366호(2026.5.12. 일부개정)로 나온다. 현행 판의 본문은 읽지 못했다.
- 사실: 2020년 판 제2조제10호에는 "행정업무 모바일 공통기반 시스템"을 행정기관등이 모바일 행정서비스를 안전하게 활용하고 배포하기 위해 공동으로 쓰는 시스템으로 정의되어 있다.
- 사실: 2020년 판 제14조제2항에는 기관의 통합관리자 역할로 "해당 기관에 등록된 모바일 기기에 대한 공통기반시스템 보안 정책 적용 및 운영ㆍ관리"와 "모바일 기기의 등록ㆍ변경ㆍ분실 등 관리"가 적혀 있다.
- 사실: 2020년 판 별표 2(모바일 서비스 서버 대상 보안취약점 점검기준)에는 "불충분한 인증 및 인가", "URL/파라미터 변조" 같은 항목이 있다. 이 표에는 단말 등록 확인 항목이 없다.
- **추론:** 이 지침에서 모바일 기기 등록은 공통기반 시스템과 기관 통합관리자의 일로 적혀 있다. 개별 업무 서버의 점검 항목은 사용자 인증과 인가, 입력 변조 쪽이다.

### 읽지 못한 국가정보원 모바일 가이드라인

- 원문 미확인: 기본지침 제57조제2항이 가리키는 「국가ㆍ공공기관 모바일 활용업무에 대한 보안가이드라인」의 원문은 공개 경로에서 찾지 못했다.
- 2차 자료: 2023년 기사에 따르면 국가정보원은 'iOS, iPadOS 모바일 단말 보안관리제품' 국가용 보안요구사항을 공개했다. 기사에 따르면 이 요구사항은 MDM 제품의 시험 기준이며, 관리서버의 식별과 인증 기능이 들어 있다. ([정보통신신문](https://www.koit.co.kr/news/articleView.html?idxno=108270), [데일리시큐](https://www.dailysecu.com/news/articleView.html?idxno=142762))
- **추론:** 이 요구사항은 MDM 제품의 요구사항이다. 업무 앱 서버의 요구사항으로 보기는 어렵다.

### 경찰청 폴리폰 자료

- 원문 미확인: 폴리폰의 단말 인증 구조를 설명한 경찰청 보도자료, 국회 자료, 조달 제안요청서 원문은 찾지 못했다. 조달 공고 "25년 경찰 모바일 단말기(폴리폰) 구매"(R25BK00781424-000)의 존재는 검색으로 확인했다. 공고 대행 사이트가 접근을 막아 규격서는 읽지 못했다.
- 2차 자료: 전자신문 2012년 4월 24일 기사에 따르면 경찰청 스마트폰 조회시스템은 국정원 보안적합성 검사를 통과했다. 같은 기사에 따르면 이 시스템에는 모바일 VPN(SSL), 백신, 보안스토리지, KT MDM이 도입되었다. ([전자신문 2012](https://www.etnews.com/201204240083?m=1))
- 2차 자료: 헤럴드경제 2022년 2월 14일 기사에 따르면 경찰 '모바일 오피스'는 경찰 내부망에 접속하므로 "보안성이 어느 정도 확보된 폴리폰과 업무용폰 4만대"에 먼저 적용한다. 같은 기사에는 SSO 적용 계획이 적혀 있다. ([헤럴드경제 2022, 다음 뉴스](https://v.daum.net/v/20220214101102798/))
- **추론:** 공개 자료로 본 경찰 모바일 업무의 단말 통제는 VPN, MDM, 단말 지급 범위, SSO 쪽에 있다. 업무 서버의 단말 ID 대조 여부는 공개 자료로 확인되지 않는다.

### 한국 자료 정리

| 자료 | 단말 관련 요구 | 적힌 주체 또는 구현 위치 |
|---|---|---|
| 기본지침 제56조제1항제1호 | 장치 및 개별사용자 인증 | 각급기관의 장. 구성 요소는 정하지 않음 |
| 기본지침 제43조제3항 | 비인가 단말 무선랜 접속 차단, 802.1X와 AAA | 무선랜 인증제품 |
| 기본지침 제52조의2제4호 | 인증서버와 연계시스템 간 상호 신뢰 검증 | 통합인증체계와 연계시스템 |
| 기본지침 제53조제1항 | 개별사용자별 인가 | 서버. 단말 인증 항목은 없음 |
| N2SF DA-3, DA-3(1), DA-3(2) | 등록 단말만 인증 후 접근 | 예시: NAC, 802.1X, X.509 인증서, TPM, IAM |
| N2SF MD-M1 | 장치 고유 ID와 사용자 계정 연동 | 예시: MDM, AD SSO, IAM 연계 |
| 모바일 전자정부 지침(2020) 제14조 | 모바일 기기 등록과 보안 정책 적용 | 공통기반 시스템과 기관 통합관리자 |

## 2. CJIS Security Policy

- 원문 확인: FBI LE 누리집의 CJIS Security Policy v6.1(2026-06-25)을 읽었다.
- 사실: IA-3(Device Identification and Authentication)의 통제 문장은 "Uniquely identify and authenticate agency-managed devices before establishing network connections."이다. 해설에는 MAC 주소, IP 주소, IEEE 802.1x와 EAP, EAP-TLS를 쓰는 RADIUS, Kerberos가 예로 적혀 있다.
- 사실: AC-19(Access Control for Mobile Devices)에는 기관이 통제하는 모바일 단말의 구성과 연결 요건을 세우고 조직 시스템에 대한 연결을 인가하라고 적혀 있다. 해설에는 "a system owner may withhold authorization for mobile device connection to specific applications or impose additional usage restrictions"라는 문장이 있다.
- 사실: 5.20.2에는 제한 기능 OS 단말에서 CJI에 직접 접근할 때 중앙 관리 MDM으로 원격 잠금, 원격 초기화, 루팅 탐지, 미패치 단말 차단 같은 통제를 하라고 적혀 있다. 같은 절에는 간접 접근 시스템이면 MDM이 필요하지 않다는 예외가 있다.
- 사실: 5.20.6에는 "Access control (Section 5.5 Access Control) shall be accomplished by the application that accesses CJI."라고 적혀 있다. 같은 절 앞 문장에는 제한 기능 모바일 OS가 다중 사용자 계정을 보통 지원하지 않는다고 적혀 있다.
- 사실: 5.20.7.2에는 권한 있는 모바일 단말에서 CJI에 접근할 때 사용자가 고급 인증(AA)을 하라고 적혀 있다.
- 사실: 5.20.7.2.1에는 기관 지급 스마트폰에서 AA 대신 쓸 보완 통제의 최소 항목이 적혀 있다. 그 항목은 "Possession and registration of an agency issued smartphone or tablet as an indication it is the authorized user", 5.20.7.3에 따른 단말 인증서 사용, CJI 저장 위치의 인증자 보호다. 이 절에는 보완 통제를 검토하기 전에 MDM을 갖추라고 적혀 있다. 같은 절에는 여러 사람이 쓰는 기관 지급 단말의 경우도 적혀 있다.
- 사실: 5.20.7.3에는 "a device certificate alone placed on the device shall not be considered valid proof that the device is being operated by an authorized user"라고 적혀 있다. 단말 인증 키는 단말에서 추출되지 않게 보호해야 한다고 적혀 있다.
- 사실: 부록 G.4에는 단말 고유 식별이나 인증서를 쓰는 단말은 분실 때 그 식별자나 인증서를 즉시 폐기하거나 비활성화하는 절차가 필요할 수 있다고 적혀 있다. 같은 부록에는 비밀번호나 단말 식별자만으로 인증하는 채팅 기능은 CJI 전송에 안전하지 않다고 적혀 있다.
- **추론:** CJIS는 단말 식별과 인증을 네트워크 연결 단계(IA-3)와 MDM(5.20.2)에 둔다. 애플리케이션에 명시적으로 맡긴 것은 사용자 접근 통제(5.20.6)다. "등록된 기관 단말 소지"를 인증 요소로 인정하는 경우(5.20.7.2.1)에도 단말 인증서와 MDM이 함께 요구된다. 그래서 클라이언트가 보낸 단말 ID 문자열을 등록 목록과 대조하는 것만으로 이 요건을 채운다고 보기는 어렵다. AC-19 해설은 시스템 소유자가 앱 단위로 단말 연결을 더 제한할 수 있다고 허용한다. 하지만 의무로 적지는 않았다.

## 3. 제품과 현장 앱

### Microsoft Entra 조건부 접근과 Intune

- 사실: Microsoft 개발자 안내서에는 조건부 접근으로 "Allowing only Intune enrolled devices to access specific services"를 할 수 있다고 적혀 있다.
- 사실: 같은 안내서에는 "In most common cases, Conditional Access doesn't change an app's behavior or requires any changes from the developer."라고 적혀 있다. 또 "Conditional Access policies are evaluated at the time the token is requested for the service"라고 적혀 있다.
- 사실: Microsoft identity platform 액세스 토큰 클레임 참조 문서의 기본 클레임 표에는 단말 준수 여부를 나타내는 클레임이 없다. 같은 문서에는 자원 API가 `aud`를 검증하라고 적혀 있다. `scp`, `roles`, `oid`, `sub`는 인가 판단에 쓸 수 있다고 적혀 있다. ([액세스 토큰 클레임 참조](https://learn.microsoft.com/en-us/entra/identity-platform/access-token-claims-reference))
- **추론:** Entra와 Intune 조합에서 단말 등록과 준수 확인은 토큰 발급 단계에서 끝난다. 자원 API에서는 토큰 서명, 대상, 권한이 검증된다. 단말 확인은 따로 하지 않는 구조로 볼 수 있다.

### Android Enterprise와 Android 식별자 지침

- 사실: Android Management API의 Device Trust 문서에는 COMPANION_APP 역할을 받은 앱이 Android Device Policy 앱에서 단말 신뢰 신호를 읽을 수 있다고 적혀 있다. 같은 문서에는 앱이 그 신호로 "compute the device's trust state and enact business logic as chosen"할 수 있다고 적혀 있다. ([AMAPI Device Trust](https://developers.google.com/android/management/device-trust-api))
- 사실: Android 식별자 모범 사례 문서의 Security, Abuse detection 항목에는 요청이 진짜 Android 단말에서 왔는지 확인하려면 Play Integrity API를 쓰라고 적혀 있다. 같은 문서에는 하드웨어 식별자를 피하고, 앱 인스턴스 식별에는 Firebase 설치 ID나 앱이 만든 GUID를 쓰라고 적혀 있다. ([Android 식별자 모범 사례](https://developer.android.com/identity/user-data-ids))
- **추론:** Android 지침은 "식별"과 "진짜 단말 증명"을 나눈다. 단말별 기록을 찾는 키에는 앱 범위 식별자를 권한다. 단말이 진짜인지 증명하는 데는 서명된 무결성 토큰을 권한다.

### 공유 단말 현장 앱(Zebra)

- 사실: Zebra Identity Guardian 문서에는 공유 단말에서 바코드나 NFC 카드로 사용자를 식별하고 얼굴 인식, 암호, SSO로 추가 확인을 한다고 적혀 있다. SSO 제공자로는 Microsoft Entra ID와 PingID가 적혀 있다. Entra SSO를 쓰려면 Microsoft Authenticator를 공유 단말 모드로 설정해야 한다고 적혀 있다. ([Zebra Identity Guardian 설정](https://techdocs.zebra.com/identityguardian/latest/setup/))
- **추론:** Zebra 공유 단말 모델에서 사용자 확인은 단말 쪽 로그인 앱과 IdP가 맡는다. 업무 앱 서버가 단말 등록을 따로 확인한다는 내용은 문서에 없다.

### Samsung Knox

- 사실: 이전 조사에 따르면 Knox Device Health Attestation에서 서명과 nonce는 Knox 서버나 기관 안의 Attestation Validator에서 확인된다. 결과를 보고 조치하는 쪽은 검사를 요청한 시스템이다. 자세한 내용은 [단말 신뢰 계층 조사의 Samsung Knox 증명 절](device-trust-layer-research.md#samsung-knox-증명)에 있다.
- 원문 미확인: Knox Manage가 업무 앱 서버에 단말 준수 상태를 넘기는 API나 IdP 연동 구조는 이번 조사에서 확인하지 못했다.

## 4. 단말 ID 대조를 입력 검증으로 다룬 자료

- 사실: OWASP Input Validation Cheat Sheet에는 입력 검증이 "malformed data, invalid business operations"의 위험을 줄인다고 적혀 있다. 같은 문서에는 구문(syntax)과 의미(semantics)를 함께 검증하라고 적혀 있다. 고정된 선택지에는 "Exact membership in the allowed set"을 확인하라고 적혀 있다. ([OWASP Input Validation Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html))
- 사실: OWASP Top 10 Proactive Controls C3에는 "Input validation does not always make data "safe""라고 적혀 있다. 같은 문서에는 입력 검증을 일반적인 보안 규칙으로 믿고 적용할 수 없다고 적혀 있다. ([OWASP Proactive Controls C3](https://top10proactive.owasp.org/archive/2024/the-top-10/c3-validate-input-and-handle-exceptions/))
- 사실: OWASP API Security Top 10 2023의 API1(BOLA)에는 공격자가 요청에 담긴 객체 ID를 바꿔 다른 객체에 접근한다고 적혀 있다. 예방 항목에는 "every function that uses an input from the client to access a record in the database"에서 권한을 확인하라고 적혀 있다. ([OWASP API1:2023](https://api-security.owasp.org/editions/2023/en/0xa1-broken-object-level-authorization))
- 원문 미확인: 단말 ID를 등록 목록과 대조하는 일을 "보안 통제가 아닌 데이터 무결성 검증"으로 명시한 1차 자료는 찾지 못했다.
- **추론:** OWASP 기준으로 보면 등록 목록 대조는 의미 검증에 해당한다. 없는 단말 키로 단말별 기록을 만들지 않게 하는 효과가 있다. 다만 OWASP C3에 따르면 이 검증은 요청의 출처를 증명하지 않는다. 단말 ID로 단말별 기록을 읽거나 쓰는 경우에는 BOLA 원칙에 따라 그 사용자가 그 단말 기록을 다룰 수 있는지 따로 확인하는 문제가 남는다. 업무폰을 여러 계정이 돌려 쓰는 모델에서 이 권한을 어떻게 정할지는 이 조사의 범위 밖이다.

## 확인하지 못한 것

- 「국가ㆍ공공기관 모바일 활용업무에 대한 보안가이드라인」 원문은 찾지 못했다. 공개 자료가 아닐 수 있다.
- 「모바일 전자정부 서비스 관리 지침」 현행 판(예규 제366호, 2026.5.12.)의 본문은 읽지 못했다. 이 문서의 인용은 2020년 판(예규 제100호)이다.
- N2SF 해설서는 국가사이버안보센터에서 직접 받지 못했다. 목포대 게시판의 사본을 읽었다.
- 「원격업무 통합보안매뉴얼」과 「정보통신기기 암호기술 적용지침」은 찾지 않았다.
- 경찰청 폴리폰과 모바일 오피스의 단말 인증 구조를 적은 1차 자료(보도자료, 국회 자료, 제안요청서)는 찾지 못했다. 폴리폰 구매 공고의 규격서는 접근이 막혀 읽지 못했다.
- 수리맵이 「국가 사이버보안 기본지침」 제3조의 "각급기관"에 해당하는지는 판단하지 않았다. 실제 기관에 배포할 때 운영 기관이 정할 문제다.
- Knox Manage와 Intune의 파트너 준수 연동, Android Management API의 단말 식별자 필드는 이번에 읽지 않았다.

## 출처

### 한국 1차 자료

- 국가정보원, 국가 사이버보안 기본지침(2026.5.1.), 국가사이버안보센터: https://www.ncsc.go.kr/template/resources/file/nis_guide_lines_2023_1_31.pdf
- 국가정보원, 국가 망 보안체계 보안 가이드라인 보안통제 항목 해설서 1.0(2025.9.), 목포대 정보보안 게시판 사본: https://mnusecu.mokpo.ac.kr/bbs/mnusecu/1544/250570/download.do
- 행정안전부, 모바일 전자정부 서비스 관리 지침(예규 제100호, 2020.1.3.) 게시글과 HWP 전문: https://www.mois.go.kr/frt/bbs/type001/commonSelectBoardArticle.do?bbsId=BBSMSTR_000000000016&nttId=75299
- 국가법령정보센터, 모바일 전자정부 서비스 관리 지침(예규 제366호, 2026.5.12.) 발령 정보: https://www.law.go.kr/행정규칙/모바일전자정부서비스관리지침

### 미국 1차 자료

- FBI CJIS Division, CJIS Security Policy v6.1(2026-06-25): https://le.fbi.gov/file-repository/cjis_security_policy_v6-1_20260625.pdf

### 제품과 표준 문서

- Microsoft, Developer guidance for Microsoft Entra Conditional Access: https://learn.microsoft.com/en-us/entra/identity-platform/v2-conditional-access-dev-guide
- Microsoft, Access token claims reference: https://learn.microsoft.com/en-us/entra/identity-platform/access-token-claims-reference
- Google, Android Management API Device Trust: https://developers.google.com/android/management/device-trust-api
- Android Developers, Best practices for unique identifiers: https://developer.android.com/identity/user-data-ids
- Zebra, Identity Guardian Setup: https://techdocs.zebra.com/identityguardian/latest/setup/
- OWASP, Input Validation Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/Input_Validation_Cheat_Sheet.html
- OWASP, Top 10 Proactive Controls 2024 C3: https://top10proactive.owasp.org/archive/2024/the-top-10/c3-validate-input-and-handle-exceptions/
- OWASP, API Security Top 10 2023 API1 BOLA: https://api-security.owasp.org/editions/2023/en/0xa1-broken-object-level-authorization

### 2차 자료

- 전자신문, 경찰청 모바일오피스, 국정원 보안적합성 최초 통과(2012-04-24): https://www.etnews.com/201204240083?m=1
- 헤럴드경제, 경찰 '모바일 오피스' 사업 착수(2022-02-14), 다음 뉴스: https://v.daum.net/v/20220214101102798/
- 정보통신신문, 공공기관 업무에 아이폰, 아이패드 이용 가능(2023): https://www.koit.co.kr/news/articleView.html?idxno=108270
- 데일리시큐, 국정원 아이폰용 MDM 보안요구사항 공개(2023): https://www.dailysecu.com/news/articleView.html?idxno=142762
