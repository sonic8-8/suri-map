# 업무 단말 신뢰 책임 계층 조사

- 조사일: 2026-11-16
- 질문: 기관이 지급한 업무용 단말을 쓰는 폐쇄망 환경에서 "이 요청이 기관 지급 단말에서 왔다"는 사실은 인프라 계층과 애플리케이션 업무 로직 중 어디가 책임지는가?
- 상태: 조사 결과다. 수리맵 설계 결정은 아직 하지 않았다.

이 문서에서 "사실"은 출처에 적힌 내용이다. 조사자의 해석은 문장 앞에 **추론:** 으로 표시한다. 출처의 접근일은 모두 2026-11-16이다.

이 문서에서 "인프라 계층"은 업무 API 바깥에서 단말을 관리하거나 확인하는 구성 요소를 말한다. MDM/EMM, 증명 서비스, 인증서 발급 체계, IdP, 망 접근 제어가 여기에 들어간다.

## 결론 요약

- 사실: 조사한 1차 자료에서 확인한 단말 근거는 세 가지다. MDM/EMM 등록과 준수 상태, 하드웨어 저장과 검증한 키 속성에 대한 증명(키 증명), 키 소유와 요청의 결속(mTLS 인증서 결속 토큰, DPoP)이다. ([NIST SP 800-124r2](https://csrc.nist.gov/pubs/sp/800/124/r2/final), [Android Key Attestation](https://developer.android.com/privacy-and-security/security-key-attestation), [RFC 8705](https://www.rfc-editor.org/rfc/rfc8705.html), [RFC 9449](https://www.rfc-editor.org/rfc/rfc9449.html), [Entra 조건부 접근 허용 제어](https://learn.microsoft.com/en-us/entra/identity/conditional-access/concept-conditional-access-grant))
- **추론:** 이 근거들은 대부분 인프라 계층의 구성 요소에서 만들어지거나 판정된다. 다만 출처에는 인프라와 업무 로직 사이의 책임 경계가 정해져 있지 않다. Android 안내에는 키 증명을 신뢰하는 별도 서버에서 검증하라고 적혀 있다. RFC 8705에서는 인증서 결속을 자원 서버에서 확인한다. 그래서 이 검증을 업무 서버 쪽에 둘 수도 있다. 수리맵에서 어느 구성 요소가 맡을지는 별도 결정으로 남긴다.
- 사실: NIST SP 800-207에는 네트워크 위치만으로 신뢰를 주지 않는다고 적혀 있다. 같은 절에는 기업 망 안의 요청도 기업 밖 망의 요청과 같은 보안 요건을 충족해야 한다고 적혀 있다. ([NIST SP 800-207 2.1절](https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-207.pdf))
- **추론:** 망 분리나 VPN 안에 있다는 사실만으로는 단말 신뢰의 근거로 부족하다고 볼 수 있다.
- 사실: NIST SP 800-207에서는 정책 엔진(PE)이 접근을 최종 결정한다. 정책 관리자(PA)는 그 결정에 따라 정책 집행 지점(PEP)에 명령해 통신 경로를 열거나 닫는다. PEP는 주체와 자원 사이의 연결을 열고, 감시하고, 끊는다. 사용자 신원과 요청 자산의 상태는 정책 엔진의 입력값이다. ([NIST SP 800-207 2.1절, 3.1절](https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-207.pdf))
- 사실: 조사한 공유 단말 방식은 등록과 사용자 인증 방법이 서로 다르다. Intune의 Entra 공유 모드 등록에서는 단말이 사용자 계정 없이 등록된다. Knox Manage 공유 단말은 등록할 때 스테이징 사용자 ID가 필요하다. Android 보조 사용자의 데이터는 유지된다. 임시 사용자만 삭제된다. Shared iPad에는 Managed Apple Account 로그인과 계정 없는 Guest 세션이 있다. 앱 개발자가 이전 사용자의 데이터를 지워야 한다는 요구는 MSAL 공유 단말 모드 안내에 있다. ([Intune 전용 단말 등록](https://learn.microsoft.com/en-us/intune/intune-service/enrollment/android-kiosk-enroll), [Knox Manage 공유 Android 단말](https://docs.samsungknox.com/admin/knox-manage/original-console/quickstart-guides/shared-android-device-quickstart/), [Android 다중 사용자](https://developer.android.com/work/dpc/dedicated-devices/multiple-users), [Apple Shared iPad](https://support.apple.com/guide/deployment/shared-ipad-overview-dep9a34c2ba2/web), [MSAL Android 공유 단말](https://learn.microsoft.com/en-us/entra/identity-platform/msal-android-shared-devices))
- **추론:** 네 방식의 공통점은 단말 관리와 실제 사용자 세션을 구분한다는 데까지다.
- 사실: Android Management API의 Device 자원에는 단말 이름, 상태, 정책 준수 여부, 마지막 상태 보고 시각이 있다. FCM 문서에는 앱 인스턴스 식별자를 타임스탬프와 함께 앱 서버에 저장하라고 적혀 있다. ([Android Management API Device](https://developers.google.com/android/management/reference/rest/v1/enterprises.devices), [FCM 토큰 관리](https://firebase.google.com/docs/cloud-messaging/manage-tokens))
- **추론:** 단말별 상태(푸시 토큰, 상태 보고, 단말 정책 기록)를 저장하는 1차 사례에서는 단말을 식별자로 찾는 자원 기록으로 다룬다.
- 한국 공공 자료의 1차 원문(국가 정보보안 기본지침, N2SF 가이드라인, 모바일 전자정부 서비스 관리 지침, 경찰 업무용 스마트폰 자료)은 이번 조사에서 읽지 못했다. 2차 자료에 따르면 국가 정보보안 기본지침 제56조는 단말기 장치 인증과 개별 사용자 인증을 따로 요구한다.

## 인프라 계층에서 단말을 확인하는 방법

각 방법마다 증명하는 것과 증명하지 않는 것을 나눠 적는다. "증명하지 않는 것" 중 출처에 직접 적힌 내용이 아니면 **추론:** 으로 표시한다.

### MDM/EMM 등록과 정책 집행

- 사실: NIST SP 800-124r2에 따르면 EMM은 기업 환경에서 모바일 단말을 배포하고, 설정하고, 관리하는 솔루션이다. ([NIST SP 800-124r2 4.2.1절](https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-124r2.pdf))
- 사실: 같은 문서에 따르면 EMM으로 단말에 기업 보안 정책을 강제할 수 있다. 정책 위반도 EMM으로 자동 감지하고 보고할 수 있다. ([NIST SP 800-124r2 4.2.1.1절](https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-124r2.pdf))
- 사실: 같은 문서는 사용자가 기관 시스템에 접근하기 전에 기관 지급 단말을 완전히 보안 설정하라고 권고한다. ([NIST SP 800-124r2](https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-124r2.pdf))
- 사실: Android Management API에서는 등록 토큰으로 단말을 기업에 묶는다. 한 사용자는 최대 10대의 단말에 연결될 수 있다. ([Android Management API 소개](https://developers.google.com/android/management/introduction))
- 사실: Android Management API의 Device 자원에는 소유 사용자(`userName`), 관리 모드, 상태(`state`, `appliedState`), 정책 준수 여부(`policyCompliant`), 마지막 상태 보고 시각, 보안 태세(`securityPosture`)가 있다. ([enterprises.devices](https://developers.google.com/android/management/reference/rest/v1/enterprises.devices))
- 증명하는 것: 관리 서버 쪽에서 "이 단말이 등록되어 있고 정책을 지키고 있다"를 알 수 있다.
- 증명하지 않는 것: **추론:** MDM 등록 기록만으로는 업무 서버에 들어온 특정 HTTP 요청이 그 단말에서 왔는지 알 수 없다. 이를 알려면 MDM의 판정이 요청 경로(인증서, IdP 토큰, 네트워크 접근 제어)에 연결되어야 한다.

### MDM 관리 구성(managed configuration)으로 넣은 값

- 사실: 관리 구성은 IT 관리자가 앱의 설정을 원격으로 지정하는 기능이다. ([Android 관리 구성](https://developer.android.com/work/managed-configurations))
- 사실: 설정값을 바꾸는 주체는 같은 단말에서 실행되는 관리 구성 제공 앱이다. 이 앱은 보통 IT 관리자가 통제한다. ([Android 관리 구성](https://developer.android.com/work/managed-configurations))
- 사실: 설정값은 `RestrictionsManager`로 읽는다. 값의 형식은 Boolean, int, String, String[]이다. ([Android 관리 구성](https://developer.android.com/work/managed-configurations))
- 증명하는 것: 앱 안에서는 "관리자가 이 값을 넣었다"를 믿을 수 있다.
- 증명하지 않는 것: **추론:** 관리 구성 문서에는 값을 서버에 증명하는 기능이 없다. 그 값을 HTTP 헤더에 담아 보내면 서버에는 문자열만 도착한다. 같은 값을 알면 다른 클라이언트에서도 같은 헤더를 보낼 수 있다.

### Android Key Attestation과 ID 증명

- 사실: 키 증명으로 앱이 쓰는 키가 단말의 하드웨어 기반 키 저장소에 있는지 더 확실하게 확인할 수 있다. ([Android Key Attestation](https://developer.android.com/privacy-and-security/security-key-attestation))
- 사실: 안내 문서에 따르면 증명을 신뢰하려면 아래 조건을 모두 확인해야 한다. ([Android Key Attestation](https://developer.android.com/privacy-and-security/security-key-attestation))
  - 체인의 각 인증서가 다음 인증서를 서명했는지 서명을 검증한다.
  - 루트 인증서가 Google 증명 루트 키인지 확인한다.
  - 체인 안의 어떤 인증서도 폐기되지 않았는지 확인한다.
  - 키 설명 데이터의 `attestationSecurityLevel`이 `TrustedEnvironment` 또는 `StrongBox`인지 확인한다.
  - 증명 확장 데이터를 꺼내 기대한 속성과 비교한다. 증명 확장은 체인에서 처음 나온 것만 신뢰할 수 있다.
- 사실: 위 조건을 만족하면 키가 Google이 안전하다고 보는 하드웨어에 있다는 것을 알 수 있다. 그 키가 인증서에 적힌 속성을 가진다는 것도 알 수 있다. ([Android Key Attestation](https://developer.android.com/privacy-and-security/security-key-attestation))
- 사실: 검증은 단말이 아니라 신뢰하는 별도 서버에서 하라고 적혀 있다. 단말이 손상되었으면 단말 안에서 한 검증도 믿을 수 없기 때문이다. ([Android Key Attestation](https://developer.android.com/privacy-and-security/security-key-attestation))
- 사실: 인증서의 폐기 여부는 Google이 `https://android.googleapis.com/attestation/status`에 게시하는 목록으로 확인한다. 이 목록의 응답은 `Cache-Control` 헤더에 따라 캐시할 수 있다. ([Android Key Attestation](https://developer.android.com/privacy-and-security/security-key-attestation))
- 사실: 증명 확장 데이터에는 Verified Boot 상태(`verifiedBootState`), 부트로더 잠금 여부(`deviceLocked`), 키를 쓸 수 있는 앱의 패키지와 서명 인증서 요약(`attestationApplicationId`)이 들어간다. ([AOSP 키와 ID 증명](https://source.android.com/docs/security/features/keystore/attestation))
- 사실: ID 증명으로 일련번호나 IMEI 같은 하드웨어 식별자를 증명할 수 있다. 증명 대상은 공장에서 TEE 저장소에 넣은 식별자뿐이다. ([AOSP 키와 ID 증명](https://source.android.com/docs/security/features/keystore/attestation))
- 2차 자료: AOSP 커밋 미러에 따르면 일련번호, IMEI, MEID 증명은 단말 소유자(device owner)나 그가 위임한 인증서 설치 앱만 요청할 수 있다. 이 내용은 developer.android.com 원문으로 확인하지 못했다.
- 사실: AOSP 문서에 따르면 증명은 증명 대상 키에 대한 공개키 작업으로 취급된다. 증명은 언제든 만들 수 있다. 키 사용 권한 제약을 충족하지 않아도 증명을 만들 수 있다. ([AOSP 키와 ID 증명](https://source.android.com/docs/security/features/keystore/attestation))
- 사실: 증명 확장의 `origin` 값은 KeyMint의 `Tag::ORIGIN` 태그에 해당한다. ([AOSP 키와 ID 증명](https://source.android.com/docs/security/features/keystore/attestation)) `KeyOrigin.aidl`에 따르면 `GENERATED`는 KeyMint 안에서 생성된 키를 뜻한다. `IMPORTED`는 Android에 평문으로 있던 키를 가져온 경우다. `SECURELY_IMPORTED`는 다른 곳에서 만든 키를 보안 하드웨어로 안전하게 가져온 경우다. ([KeyOrigin.aidl](https://android.googlesource.com/platform/hardware/interfaces/+/refs/heads/main/security/keymint/aidl/android/hardware/security/keymint/KeyOrigin.aidl))
- 증명하는 것: 위 검증 조건을 모두 만족하면 키가 하드웨어 기반 키 저장소에 저장되어 있다는 것을 확인할 수 있다. 증명 확장에서 검증한 키 속성도 확인할 수 있다. 부팅 상태와 앱 서명은 증명 확장에서 읽어 기대값과 비교한 속성으로 확인할 수 있다. 키가 하드웨어 안에서 생성되었다는 것은 하드웨어 강제 목록의 `origin`이 `GENERATED`인 것을 확인한 경우에만 말할 수 있다. 단말 소유자 권한이 있으면 하드웨어 식별자도 증명할 수 있다.
- 증명하지 않는 것: **추론:** 증명은 키 사용 권한 없이도 만들 수 있다. 그래서 증명 인증서 체인을 제시했다는 것만으로는 요청한 쪽이 그 개인키를 쓸 수 있다는 근거가 되지 않는다. 요청별 개인키 소유는 mTLS나 DPoP 같은 별도 단계에서 확인해야 한다. 그 단계가 없으면 개별 요청이 그 단말에서 왔다는 근거가 되지 않는다. **추론:** 폐쇄망에서는 Google 폐기 목록을 직접 받을 수 없을 수 있다. 그러면 목록을 주기적으로 반입하는 절차가 필요하다.

### Play Integrity API

- 사실: Play Integrity API로 요청이 Google Play에서 설치한 진짜 앱과 인증된 진짜 Android 단말에서 왔는지 확인할 수 있다. ([Play Integrity 개요](https://developer.android.com/google/play/integrity/overview))
- 사실: 판정에는 앱 무결성(`appIntegrity`), 계정 정보(`accountDetails`), 단말 무결성(`deviceIntegrity`)이 있다. ([Play Integrity 개요](https://developer.android.com/google/play/integrity/overview))
- 사실: Google은 이 API를 유일한 남용 방지 수단으로 쓰지 말고 다른 신호와 함께 쓰라고 적는다. ([Play Integrity 개요](https://developer.android.com/google/play/integrity/overview))
- 사실: Google은 판정을 캐시하지 말라고 권고한다. 캐시한 판정은 다른 환경에서 재사용될 위험이 있기 때문이다. ([Play Integrity 개요](https://developer.android.com/google/play/integrity/overview))
- 증명하는 것: 앱 바이너리가 변조되지 않았다는 판정을 받을 수 있다. 단말이 인증된 Android 단말이라는 판정도 받을 수 있다.
- 증명하지 않는 것: **추론:** 판정으로 알 수 있는 것은 "인증된 Android 단말"이라는 사실이다. "기관이 지급한 그 단말"인지는 알 수 없다. **추론:** 판정을 받으려면 Google Play 서비스와 Google 서버에 닿아야 하므로 인터넷이 막힌 폐쇄망에서는 쓰기 어렵다. 이 점은 개요 문서에 직접 적혀 있지 않으므로 실제 망 구성에서 확인해야 한다.

### Samsung Knox 증명

- 사실: Knox Device Health Attestation으로 하드웨어 신뢰 기반을 이용해 배포, 부팅, 운영 중의 단말 무결성을 검증할 수 있다. ([Knox Device Health Attestation](https://docs.samsungknox.com/admin/fundamentals/whitepaper/samsung-knox-mobile-security/system-security/device-health-attestation/))
- 사실: 검사를 시작하는 웹 서버가 Knox 서버에서 nonce를 받아 단말에 넘긴다. 단말 상태는 단말의 Keymaster 신뢰 앱에서 모아 서명된다. Knox 서버가 서명과 nonce를 확인한다. ([Knox Device Health Attestation](https://docs.samsungknox.com/admin/fundamentals/whitepaper/samsung-knox-mobile-security/system-security/device-health-attestation/))
- 사실: 결과를 보고 다음 조치를 하는 쪽은 검사를 요청한 시스템이다. ([Knox Device Health Attestation](https://docs.samsungknox.com/admin/fundamentals/whitepaper/samsung-knox-mobile-security/system-security/device-health-attestation/))
- 사실: 방화벽 안에서 운영하는 경우 로컬 서버에 Attestation Validator를 설치해 판정을 방화벽 안에 둘 수 있다. ([Knox Device Health Attestation](https://docs.samsungknox.com/admin/fundamentals/whitepaper/samsung-knox-mobile-security/system-security/device-health-attestation/))
- 증명하는 것: 삼성 단말의 부팅 상태와 비승인 펌웨어 이력(warranty bit)을 알 수 있다.
- 증명하지 않는 것: **추론:** 무결성 판정은 개별 업무 요청과 묶이지 않는다. 요청 단위의 근거가 되려면 판정 결과를 인증서 발급이나 세션 발급에 연결해야 한다.

### mTLS 클라이언트 인증서와 토큰 결속

- 사실: RFC 8705는 OAuth 접근 토큰을 클라이언트의 mTLS 인증서에 묶는 방법을 정의한다. ([RFC 8705](https://www.rfc-editor.org/rfc/rfc8705.html))
- 사실: 인가 서버는 인증서의 SHA-256 지문을 토큰의 `cnf` 클레임(`x5t#S256`)에 넣는다. 자원 서버는 TLS 연결에서 제시된 인증서가 이 지문과 맞는지 확인한다. ([RFC 8705](https://www.rfc-editor.org/rfc/rfc8705.html))
- 사실: 이 결속을 쓰면 인증서에 대응하는 개인키를 가진 쪽만 토큰을 쓸 수 있다. ([RFC 8705](https://www.rfc-editor.org/rfc/rfc8705.html))
- 사실: NIST SP 800-124r2에 따르면 인증서는 EMM 정책 프로필로 단말에 넣을 수 있다. 단말 인증서 저장소에 들어간 인증서는 인증에 쓸 수 있다. ([NIST SP 800-124r2](https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-124r2.pdf))
- 사실: Entra ID에서는 Windows, iOS, Android, macOS 단말을 등록할 때 발급된 클라이언트 인증서로 단말을 식별한다. ([Entra 조건부 접근 허용 제어](https://learn.microsoft.com/en-us/entra/identity/conditional-access/concept-conditional-access-grant))
- 증명하는 것: 요청마다 "이 개인키를 가진 클라이언트가 보냈다"는 것이 증명된다. 개인키가 MDM으로 배포되고 내보낼 수 없는 하드웨어 키라면, 요청이 그 단말에서 왔다는 근거에 가까워진다.
- 증명하지 않는 것: **추론:** 인증서만으로는 단말 상태(루팅, 패치)를 알 수 없다. 개인키를 내보낼 수 있게 발급하면 다른 기기로 복사될 수 있다.

### DPoP

- 사실: RFC 9449는 증명 소유(proof-of-possession)로 OAuth 토큰의 사용자를 제한하는 방법이다. 이 방법으로 접근 토큰과 갱신 토큰의 재전송 공격을 감지할 수 있다. ([RFC 9449](https://www.rfc-editor.org/rfc/rfc9449.html))
- 사실: RFC 9449 3절에는 DPoP가 클라이언트 인증 방법이 아니라고 적혀 있다. ([RFC 9449](https://www.rfc-editor.org/rfc/rfc9449.html))
- 사실: Keycloak에서는 public client의 경우 클라이언트가 만든 키 쌍에 접근 토큰과 갱신 토큰이 모두 묶인다. confidential client에서는 접근 토큰만 DPoP 키에 묶인다. confidential client의 갱신 토큰은 클라이언트 자격 증명으로 보호된다. ([Keycloak DPoP 갱신 토큰 처리](https://www.keycloak.org/securing-apps/dpop#_handling_refresh_tokens)) 관리 콘솔의 클라이언트 설정에서 **Require DPoP bound tokens**를 켜면 DPoP 증명이 없는 토큰 요청은 실패한다. ([Keycloak DPoP](https://www.keycloak.org/securing-apps/dpop))
- 증명하는 것: 토큰을 훔쳐도 그 키 없이는 쓸 수 없다.
- 증명하지 않는 것: **추론:** DPoP 키는 앱이 스스로 만든 키다. 키 증명과 함께 쓰지 않으면 그 키가 기관 지급 단말에 있다는 근거가 없다.

### 망 분리, VPN, 네트워크 접근 제어

- 사실: NIST SP 800-207의 두 번째 원칙은 네트워크 위치와 관계없이 모든 통신을 보호하라는 것이다. 이 원칙에는 네트워크 위치만으로 신뢰를 주지 않는다고 적혀 있다. ([NIST SP 800-207 2.1절](https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-207.pdf))
- 사실: 같은 절에는 기업 망 안의 요청도 기업 밖 망의 요청과 같은 보안 요건을 충족해야 한다고 적혀 있다. ([NIST SP 800-207 2.1절](https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-207.pdf))
- 사실: NIST SP 800-124r2는 기관이 VPN 말고 다른 기술도 검토해 제로 트러스트 구조를 구현하라고 권한다. ([NIST SP 800-124r2](https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-124r2.pdf))
- 사실: CISA 제로 트러스트 성숙도 모델 2.0의 Devices 영역 Advanced 단계에는 검증되지 않은 인증서나 등록되지 않은 MAC 주소를 가진 비준수 단말을 찾아 연결을 끊거나 격리하는 내용이 있다. ([CISA ZTMM 2.0](https://www.cisa.gov/sites/default/files/2023-04/zero_trust_maturity_model_v2_508.pdf))
- 증명하는 것: 망에 들어온 단말이 인증서나 등록 정보 확인을 통과했다는 사실이다.
- 증명하지 않는 것: **추론:** 망 안에 들어온 뒤의 개별 요청은 망 수준의 확인만으로 어느 단말이 보냈는지 알 수 없다. NAT나 게이트웨이 뒤에서는 애플리케이션 계층에서 출발지 단말을 구분할 수 없을 수 있다.

### 제로 트러스트의 정책 결정 구조

- 사실: NIST SP 800-207의 네 번째 원칙은 자원 접근을 동적 정책으로 결정한다는 것이다. 동적 정책에는 클라이언트 신원, 애플리케이션이나 서비스, 요청 자산의 관찰 가능한 상태가 들어간다. ([NIST SP 800-207 2.1절](https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-207.pdf))
- 사실: 같은 원칙에서 요청 자산의 상태에는 설치된 소프트웨어 버전, 네트워크 위치, 요청 시각, 이전 행동, 설치된 자격 증명이 들어갈 수 있다. ([NIST SP 800-207 2.1절](https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-207.pdf))
- 사실: 다섯 번째 원칙은 기업이 소유한 모든 자산의 무결성과 보안 태세를 감시하고 측정하라는 것이다. 이 원칙에는 어떤 자산도 본래부터 신뢰받지 않는다고 적혀 있다. ([NIST SP 800-207 2.1절](https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-207.pdf))
- 사실: 정책 엔진(PE)은 주체에게 자원 접근을 줄지 최종 결정한다. 정책 엔진은 결정을 내리고 기록한다. ([NIST SP 800-207 3.1절](https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-207.pdf))
- 사실: 정책 관리자(PA)는 정책 엔진의 결정을 실행한다. 정책 관리자는 관련 PEP에 명령해 주체와 자원 사이의 통신 경로를 열거나 닫는다. ([NIST SP 800-207 3.1절](https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-207.pdf))
- 사실: 정책 집행 지점(PEP)은 주체와 자원 사이의 연결을 열고, 감시하고, 끊는다. ([NIST SP 800-207 3.1절](https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-207.pdf))
- 사실: NIST SP 800-207에 따르면 요청 자산의 패치 상태, 승인 소프트웨어의 무결성, 알려진 취약점 정보는 CDM 시스템에서 정책 엔진으로 전달된다. ([NIST SP 800-207 3.1절](https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-207.pdf))
- 사실: 단말 에이전트와 게이트웨이 배치 모델에서는 기업이 지급한 각 자산에 단말 에이전트를 설치한다. 각 자원 앞에는 게이트웨이를 둔다. ([NIST SP 800-207 3.2.1절](https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-207.pdf))
- 사실: 신원 중심 접근에서 단말 신원과 상태는 접근 결정의 보조 자료라고 적혀 있다. ([NIST SP 800-207](https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-207.pdf))
- 사실: CISA 제로 트러스트 성숙도 모델 2.0의 Devices 영역에는 기관이 모든 기관 단말을 보호하고 비인가 단말의 자원 접근을 막아야 한다고 적혀 있다. Resource Access 기능의 Optimal 단계는 단말 안의 실시간 위험 분석을 접근 결정에 반영하는 것이다. ([CISA ZTMM 2.0 5.2절](https://www.cisa.gov/sites/default/files/2023-04/zero_trust_maturity_model_v2_508.pdf))
- **추론:** 이 구조에서 단말 상태 정보는 MDM, CDM, 증명 서비스에서 모인다. 판정은 정책 엔진이 내린다. 집행은 정책 관리자의 명령을 받은 정책 집행 지점(게이트웨이, IdP 등)이 맡는다. 업무 애플리케이션이 직접 단말 신뢰를 판정하는 구조는 문서에 나오지 않는다.

### IdP의 조건부 접근(Entra 예시)

- 사실: Entra ID의 단말 신원은 사용자, 그룹, 애플리케이션과 비슷한 디렉터리 객체다. ([Entra 단말 신원](https://learn.microsoft.com/en-us/entra/identity/devices/overview))
- 사실: 단말 신원은 단말 기반 조건부 접근 정책과 Intune MDM의 전제 조건이다. ([Entra 단말 신원](https://learn.microsoft.com/en-us/entra/identity/devices/overview))
- 사실: Intune의 준수 정보는 Entra ID로 전달된다. 조건부 접근 정책에서는 이 정보로 자원 접근이 허용되거나 차단된다. ([Entra 조건부 접근 허용 제어](https://learn.microsoft.com/en-us/entra/identity/conditional-access/concept-conditional-access-grant))
- 사실: 단말은 Entra ID에 등록된 뒤에만 준수 상태로 표시될 수 있다. ([Entra 조건부 접근 허용 제어](https://learn.microsoft.com/en-us/entra/identity/conditional-access/concept-conditional-access-grant))
- 사실: 디바이스 코드 흐름에서는 관리 단말 요구 조건을 쓸 수 없다. 인증하는 단말의 상태를 코드를 받는 단말로 전달할 수 없기 때문이다. 토큰 안의 단말 상태는 인증을 수행한 단말에 고정된다. ([Entra 조건부 접근 허용 제어](https://learn.microsoft.com/en-us/entra/identity/conditional-access/concept-conditional-access-grant))
- 사실: Entra 접근 토큰 클레임 참조 문서에는 `sub`와 `oid`를 권한 검사와 DB 키로 쓸 수 있다고 적혀 있다. 조사자가 읽은 페이로드 클레임 표에서는 단말 식별자 클레임을 찾지 못했다. ([Entra 접근 토큰 클레임](https://learn.microsoft.com/en-us/entra/identity-platform/access-token-claims-reference))
- **추론:** 이 모델에서 단말 확인은 토큰 발급 시점에 IdP에서 끝난다. 업무 API 단계에서는 토큰만 검증하고 단말은 다시 확인하지 않는다.

## 애플리케이션이 단말을 다루는 경우

### 단말을 인가 주체(authorization principal)로 다루는 경우

- 사실: Entra ID에서 단말은 사용자, 그룹과 같은 수준의 디렉터리 객체다. 관리자는 이 객체의 정보로 접근 결정과 설정 결정을 내린다. ([Entra 단말 신원](https://learn.microsoft.com/en-us/entra/identity/devices/overview))
- 사실: NIST SP 800-207에서 단말 상태는 정책 엔진의 입력값이다. 결정은 정책 엔진이 내린다. ([NIST SP 800-207 2.1절, 3.1절](https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-207.pdf))
- **추론:** 단말을 인가 주체로 다루는 1차 사례에서는 단말 객체에 반드시 암호학적 자격 증명(등록 인증서, 하드웨어 키)이 붙어 있다. 식별자 문자열만 가진 단말 객체를 인가 주체로 쓰는 사례는 찾지 못했다.

### 단말을 단말별 자원 기록으로 다루는 경우

- 사실: Android Management API의 Device 자원에는 단말의 읽기 전용 세부 정보가 담긴다. 여기에는 연결된 사용자, 정책, 관리 모드가 들어간다. ([Android Management API 소개](https://developers.google.com/android/management/introduction), [enterprises.devices](https://developers.google.com/android/management/reference/rest/v1/enterprises.devices))
- 사실: 앱 인스턴스는 FCM에 등록되고 식별자를 받는다. Firebase 문서에는 이 식별자를 업로드할 때마다 타임스탬프와 함께 앱 서버에 저장하라고 강하게 권고한다고 적혀 있다. ([FCM 토큰 관리](https://firebase.google.com/docs/cloud-messaging/manage-tokens))
- 사실: FCM 기준으로 한 달 동안 연결하지 않은 등록은 오래된 등록으로 간주된다. Android에서는 270일 동안 활동하지 않은 등록이 만료로 간주된다. ([FCM 토큰 관리](https://firebase.google.com/docs/cloud-messaging/manage-tokens))
- 사실: FCM 문서의 예시에서는 식별자를 사용자 ID 문서 아래에 저장한다. 이 배치는 예시일 뿐 요구 사항이 아니다. ([FCM 토큰 관리](https://firebase.google.com/docs/cloud-messaging/manage-tokens))
- 사실: Play Integrity의 device recall 기능으로 특정 단말에 묶인 값을 Google 서버에 저장하고 나중에 다시 읽을 수 있다. 앱을 다시 설치하거나 단말을 초기화해도 값이 남는다. ([Play Integrity 개요](https://developer.android.com/google/play/integrity/overview))
- 사실: Android 다중 사용자 안내에 따르면 새 사용자는 `UserHandle`이 아니라 재사용되지 않는 일련번호로 가리켜야 한다. `UserHandle`은 시스템에서 재활용되기 때문이다. ([Android 다중 사용자](https://developer.android.com/work/dpc/dedicated-devices/multiple-users))
- **추론:** 이 사례들에서 단말 식별자는 상태를 찾는 키 역할을 한다. 식별자가 요청의 진위를 증명하지 않아도 이 용도는 성립한다. 반대로 증명되지 않은 식별자로 접근을 허용하면, 그 식별자는 인가 근거가 아니라 단순한 입력값이 된다.

## 공유 단말 모델

### Android 전용 단말의 다중 사용자

- 사실: 완전 관리 단말에서는 DPC로 보조 사용자와 임시 사용자를 만들 수 있다. ([Android 다중 사용자](https://developer.android.com/work/dpc/dedicated-devices/multiple-users))
- 사실: 보조 사용자의 앱과 데이터는 세션 사이에 유지된다. 안내 문서에는 배송 기사나 보안 요원처럼 교대 시작 때 단말을 집어 드는 경우에 맞는다고 적혀 있다. ([Android 다중 사용자](https://developer.android.com/work/dpc/dedicated-devices/multiple-users))
- 사실: 임시 사용자는 사용자가 멈추거나, 전환되거나, 단말이 재부팅되면 삭제된다. ([Android 다중 사용자](https://developer.android.com/work/dpc/dedicated-devices/multiple-users))
- 사실: 관리자는 교대가 끝나기 전에 미전송 주문을 서버로 보내는 식으로 사용자 수명 주기를 직접 관리할 수 있다. ([Android 다중 사용자](https://developer.android.com/work/dpc/dedicated-devices/multiple-users))

### Microsoft Entra 공유 단말 모드와 Intune

- 사실: 공유 단말 모드에서 직원은 한 번 로그인해 지원 앱 전체에서 자기 데이터에 접근한다. 교대가 끝나 로그아웃하면 단말과 모든 지원 앱에서 로그아웃된다. ([Entra 공유 단말 모드](https://learn.microsoft.com/en-us/entra/identity-platform/msal-shared-devices))
- 사실: 단말 관리자는 MDM으로 단말을 공유 단말 모드로 설정한다. 앱 개발자는 MSAL로 공유 단말 모드 지원을 추가한다. ([Entra 공유 단말 모드](https://learn.microsoft.com/en-us/entra/identity-platform/msal-shared-devices))
- 사실: 관리자가 MDM 등록과 준수 단말을 요구하는 조건부 접근 정책을 두면, 사용자는 단말이 준수 상태일 때만 로그인할 수 있다. ([Entra 공유 단말 모드](https://learn.microsoft.com/en-us/entra/identity-platform/msal-shared-devices))
- 사실: Intune의 "Entra ID 공유 모드 전용 단말" 등록 방식에서는 단말이 사용자 계정 없이 등록된다. 이렇게 등록한 단말은 사용자와 연결되지 않는다. ([Intune 전용 단말 등록](https://learn.microsoft.com/en-us/intune/intune-service/enrollment/android-kiosk-enroll))
- 사실: Android MSAL에서는 `isSharedDevice`로 공유 단말인지 확인할 수 있다. 현재 로그인한 계정은 `getCurrentAccountAsync`로 확인할 수 있다. ([MSAL Android 공유 단말](https://learn.microsoft.com/en-us/entra/identity-platform/msal-android-shared-devices))
- 사실: 전역 로그아웃을 하면 계정과 캐시된 토큰이 지워진다. 앱의 데이터는 지워지지 않는다. MSAL 안내에는 앱 개발자가 이전 사용자의 데이터와 화면에 남은 캐시를 지워야 한다고 적혀 있다. ([MSAL Android 공유 단말](https://learn.microsoft.com/en-us/entra/identity-platform/msal-android-shared-devices))
- **추론:** 조사자가 읽은 MSAL 공유 단말 문서에는 단말 식별자를 받아 서버에 보내는 단계가 없다. 앱에서 알 수 있는 정보는 "공유 단말인가"와 "지금 누가 로그인했는가"뿐이다.

### Samsung Knox Manage 공유 단말

- 사실: 공유 단말에는 기본 설정을 들고 로그인 화면을 띄우는 스테이징 사용자가 있다. ([Knox Manage 공유 Android 단말](https://docs.samsungknox.com/admin/knox-manage/original-console/quickstart-guides/shared-android-device-quickstart/))
- 사실: 단말을 등록할 때 스테이징 사용자 ID가 필요하다. ID가 없으면 단말이 공유 모드로 들어가지 않는다. ([Knox Manage 공유 Android 단말](https://docs.samsungknox.com/admin/knox-manage/original-console/quickstart-guides/shared-android-device-quickstart/))
- 사실: 단말 사용자는 Knox Manage 계정으로 체크인해 세션을 시작한다. 25.04 버전부터는 웹 기반 IdP 인증으로도 체크인할 수 있다. ([Knox Manage 공유 Android 단말](https://docs.samsungknox.com/admin/knox-manage/original-console/quickstart-guides/shared-android-device-quickstart/))
- 사실: 지속형(persistent) 세션은 교대 근무자를 위한 방식이다. 지속형 세션에서는 데이터와 앱이 유지된다. 임시형(temporary) 세션에서는 로그아웃할 때 데이터와 앱이 지워진다. ([Knox Manage 공유 Android 단말](https://docs.samsungknox.com/admin/knox-manage/original-console/quickstart-guides/shared-android-device-quickstart/))

### Apple Shared iPad

- 사실: Shared iPad를 쓰려면 iPad가 감독(supervised) 상태여야 한다. 단말 관리 서비스도 필요하다. ([Apple Shared iPad](https://support.apple.com/guide/deployment/shared-ipad-overview-dep9a34c2ba2/web))
- 사실: 사용자는 Managed Apple Account로 기관 소유 Shared iPad에 로그인한다. ([Apple Shared iPad](https://support.apple.com/guide/deployment/shared-ipad-overview-dep9a34c2ba2/web))
- 사실: 계정 없이 쓰는 임시 Guest 세션도 있다. Guest가 로그아웃하면 그 세션의 데이터가 지워진다. ([Apple Shared iPad](https://support.apple.com/guide/deployment/shared-ipad-overview-dep9a34c2ba2/web))

### 공유 단말 모델의 공통점과 차이

- **추론:** 네 방식의 공통점은 단말 관리와 실제 사용자 세션을 구분한다는 것이다.
- 사실: 사용자 계정 없이 단말을 등록한다고 적힌 것은 Intune의 Entra 공유 모드 등록뿐이다. Knox Manage에서는 등록할 때 스테이징 사용자 ID가 필요하다. 출처는 위 각 절에 있다.
- 사실: 사용자 인증 수단은 방식마다 다르다. Entra 공유 단말 모드에서는 Entra 계정으로 로그인한다. Knox Manage에서는 Knox Manage 계정이나 웹 IdP로 체크인한다. Shared iPad에서는 Managed Apple Account로 로그인하거나 계정 없는 Guest 세션을 쓴다.
- 사실: 세션이 끝난 뒤 데이터를 지우는 쪽도 방식마다 다르다. Android 임시 사용자는 시스템에서 삭제된다. Android 보조 사용자의 데이터는 유지된다. Knox Manage 임시형 세션에서는 데이터와 앱이 지워진다. Knox Manage 지속형 세션에서는 데이터와 앱이 유지된다. MSAL 방식에서는 전역 로그아웃 뒤 앱 데이터를 앱 개발자가 지워야 한다.

## 한국 공공 자료

이번 조사에서 한국 공공 자료의 1차 원문 본문은 읽지 못했다. 아래에 확인한 범위와 출처의 종류를 적는다.

- 1차 자료: 행정안전부의 모바일 전자정부 구축 페이지에는 각 부처와 지자체가 모바일 서비스를 만들 때 쓸 수 있는 단말기 관리, 인증, 암호화 보안 기능을 모바일 공통기반으로 구축한다고 적혀 있다. 같은 페이지에는 관련 지침이 「모바일 전자정부 서비스 관리 지침」(행정안전부 예규)으로 적혀 있다. ([행정안전부 모바일 전자정부](https://mois.go.kr/frt/sub/a06/b04/mobileEgov/screen.do))
- 원문 미확인: 「모바일 전자정부 서비스 관리 지침」의 조문은 찾지 못했다.
- 2차 자료: IT위키의 국가 정보보안 기본지침 제56조 정리에 따르면, 각급기관의 장은 업무용 통신단말기로 중요정보를 다룰 때 보안대책을 세워야 한다. 그 대책에는 단말기 장치 인증과 개별 사용자 인증, 데이터 암호화, 분실과 탈취 대비책이 들어간다. ([IT위키 제56조](https://itwiki.kr/w/%EA%B5%AD%EA%B0%80_%EC%A0%95%EB%B3%B4%EB%B3%B4%EC%95%88_%EA%B8%B0%EB%B3%B8%EC%A7%80%EC%B9%A8_%EC%A0%9C56%EC%A1%B0))
- 원문 미확인: 국가 정보보안 기본지침의 현행 원문은 국가사이버안보센터 누리집에서 찾지 못했다.
- 2차 자료: AhnLab 해설에 따르면 국가정보원은 2026년 5월 1일자로 「국가 사이버보안 기본지침」을 개정해 공포했다. 이 해설에는 보안이 강화된 업무용 노트북 '온북'을 쓰면 민감 등급 시스템까지 원격 접속을 허용하는 제59조 신설 내용이 있다. 해설에 따르면 온북에는 기관이 제공한 보안 소프트웨어 설치와 다중 인증이 요구된다. ([AhnLab 해설, 2차](https://www.ahnlab.com/ko/contents/content-center/36176))
- 원문 미확인: AhnLab 해설에는 이전 이름이 「국가 정보보안 기본지침」이었다는 설명이 없다. 업무용 통신단말기 조항이 개정 뒤에 어떻게 되었는지도 확인하지 못했다.
- 2차 자료: 목포대 정보보안 게시판에 재게시된 기사에 따르면, 국가정보원은 2025년 9월 30일 「국가 망 보안체계(N2SF) 보안가이드라인」 1.0을 국가사이버안보센터 누리집에 공개했다. 보안통제 항목은 권한, 인증, 분리 및 격리, 통제, 데이터, 정보자산의 6개 영역으로 나뉜다. ([목포대 재게시 기사, 2차](https://mnuk2.mokpo.ac.kr/bbs/mnusecu/1544/278520/artclView.do))
- 원문 미확인: N2SF 가이드라인 본문과 부록1(보안통제 항목 해설서)의 단말 인증 항목은 읽지 못했다. 재게시 PDF는 있었지만 조사 환경에서 한글 PDF 본문을 추출하지 못했다.
- 원문 미확인: 경찰 업무용 스마트폰(폴폰, M-Police 등)의 보안 구조를 설명한 경찰청 1차 자료는 찾지 못했다.
- 제조사 자료: 삼성전자 비즈니스 사이트의 경찰청 사례에는 경찰청이 재난망폰을 도입했다고 적혀 있다. 그 재난망폰에는 Samsung Knox가 내장되어 있다고 적혀 있다. 이 자료는 제조사 홍보 자료다. 이 자료에는 MDM이나 단말 인증 구조가 나오지 않는다. ([삼성전자 경찰청 사례](https://www.samsung.com/sec/business/insights/case-study/reference-police-agency))
- **추론:** 2차 자료 기준으로는 한국 지침도 단말 인증과 사용자 인증을 별개 요구로 둔다. 이는 국제 자료의 구조와 맞는다. 다만 어느 계층이 단말 인증을 맡는지는 원문으로 확인하지 못했다.

## 수리맵에 비춰 본 점

이 절의 "현재 동작"은 조사 요청 내용과 Issue #48 검수에서 확인한 코드 동작을 옮긴 것이다. 조사자는 저장소 코드를 직접 읽지 않았다. 나머지는 모두 조사자의 해석이다. 설계 결정은 하지 않는다.

### 현재 업무폰 확인이 증명하는 범위

- 현재 동작: 공용 APP API에서는 `X-PolicePhone-Id` 헤더의 ID로 업무폰 등록을 확인한다.
- 현재 동작: 오프라인 패키지 manifest 조회에서는 query `policePhoneId`를 보낸 경우에만 업무폰 등록을 확인한다.
- 현재 동작: 오프라인 패키지 설치 보고에서는 본문 `policePhoneId`가 `X-PolicePhone-Id` 헤더와 같은지 확인한다. 그다음 본문 `policePhoneId`로 업무폰 등록을 확인한다.
- 현재 동작: 업무폰에 연결된 계정이 비활성이거나 연결된 계정이 없으면 그 업무폰 ID로 보낸 요청은 거절된다.
- **추론:** 어느 경로든 업무폰 등록 확인으로 알 수 있는 것은 "요청에 담긴 업무폰 ID가 DB의 registered=true, ACTIVE 업무폰 ID와 같다"는 사실이다. "요청이 그 업무폰에서 왔다"는 알 수 없다.
- **추론:** 업무폰 ID 값은 MDM 관리 구성에서 왔지만 서버에는 문자열로 도착한다. 유효한 사용자 토큰이 있고 등록된 ID를 알면 다른 클라이언트에서도 같은 값을 보낼 수 있다.
- **추론:** 이 확인은 앞에서 본 인프라 계층의 방법(키 증명, 클라이언트 인증서, IdP 조건부 접근) 중 어느 것에도 연결되어 있지 않다.

### 공유 단말 모델과 맞지 않을 수 있는 부분

- **추론:** 요청에 적힌 운영 방식은 한 업무폰을 여러 사람이 각자 계정으로 쓰는 방식이다. 한 계정이 여러 업무폰에 로그인할 수도 있다. 이는 단말 관리와 실제 사용자 세션을 구분하는 공유 단말 방식과 같은 구조다.
- **추론:** 확인한 방식 중 Intune의 Entra 공유 모드 등록에서는 단말이 사용자 계정 없이 등록된다. Knox Manage에서도 단말 사용자는 세션마다 따로 체크인한다. 현재 `police_phone.account_id`는 migration seed로 한 계정에 고정되어 있다. 업무폰 확인 단계에서 연결된 계정이 ACTIVE인지 보는 조건은 실제로 로그인한 사용자와 관계가 없을 수 있다. 이 조건이 어떤 요구에서 나왔는지 사용자와 확인해야 한다.

### 단말별 상태 기록

- **추론:** 오프라인 패키지 설치 상태, heartbeat, FCM 토큰, 근무 교대 기록은 1차 자료의 "단말별 자원 기록" 패턴에 해당한다. 이 기록들은 단말을 인가 주체로 만들지 않아도 업무폰 ID를 키로 저장할 수 있다.
- **추론:** FCM 식별자는 앱 인스턴스 단위다. Firebase 권고대로라면 업로드 시각과 함께 저장하고 오래된 식별자를 정리하는 기준이 필요하다.
- **추론:** 공유 단말에서는 업무폰 ID로 찾는 기록과 사용자 계정으로 찾는 기록을 구분해야 한다.
- **추론:** 이전 사용자의 데이터를 어디에서 지울지는 방식마다 다르다. Android 임시 사용자와 Knox Manage 임시형 세션에서는 OS나 MDM 에이전트 쪽에서 지워진다. MSAL 방식에서는 앱 개발자가 지워야 한다. 수리맵 앱이 어느 방식을 따를지는 정하지 않았다.

### 단말 신뢰를 실제로 증명해야 한다면 검토할 선택지

아래는 비교할 후보다. 어느 것을 고를지는 정하지 않았다.

1. 인프라에 맡긴다. 기관 MDM, VPN 또는 앱별 VPN, NAC로 기관 단말만 서버에 닿도록 한다. NIST SP 800-207 기준으로는 네트워크 위치만으로 신뢰를 주지 않으므로 이 선택만으로는 부족하다고 볼 수 있다.
2. 업무폰마다 MDM으로 클라이언트 인증서를 배포한다. Keycloak에서는 RFC 8705 인증서 결속 토큰을 쓴다. Keycloak의 지원 명세 목록에는 RFC 8705가 지원으로 적혀 있다. ([Keycloak 지원 명세](https://www.keycloak.org/securing-apps/specifications)) 이렇게 하면 요청마다 개인키 소유가 증명된다. 인증서 발급과 폐기 운영이 필요하다.
3. 업무폰 등록 시점에 Android Key Attestation(필요하면 ID 증명)을 서버 쪽에서 검증한다. 검증한 하드웨어 키는 DPoP나 mTLS에 쓴다. 폐쇄망이면 Google 폐기 목록 반입 절차가 필요하다.
4. 단말 신뢰는 서버에서 판단하지 않는다. 업무폰 ID는 단말별 자원 기록의 키로만 쓴다. 이 경우 업무폰 확인이 보안 경계가 아니라는 점을 문서에 명시해야 한다.

### 사용자와 정할 질문

- 실제 경찰 환경에서 단말 신뢰를 누가 책임지는가(기관 MDM 운영 주체, 망 운영 주체, 수리맵 서버)?
- 서버가 업무폰을 알아야 하는 이유가 인가인가, 단말별 기록인가, 둘 다인가?
- 업무폰 등록 API 없이 migration으로만 추가하는 방식을 유지하는가? 유지한다면 MDM의 단말 목록과 어떻게 맞추는가?
- 업무폰 확인 단계에서 연결된 계정의 ACTIVE 상태를 보는 조건은 어떤 요구에서 나왔는가?

## 출처

접근일은 모두 2026-11-16이다. 표시가 없는 항목은 1차 자료다.

### 표준과 정부 자료

- NIST SP 800-207, Zero Trust Architecture (2020-08). 랜딩: https://csrc.nist.gov/pubs/sp/800/207/final , 본문 PDF: https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-207.pdf
- NIST SP 800-124 Rev. 2, Guidelines for Managing the Security of Mobile Devices in the Enterprise (2023-05). 랜딩: https://csrc.nist.gov/pubs/sp/800/124/r2/final , 본문 PDF: https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-124r2.pdf
- CISA, Zero Trust Maturity Model Version 2.0 (2023-04): https://www.cisa.gov/sites/default/files/2023-04/zero_trust_maturity_model_v2_508.pdf
- IETF RFC 8705, OAuth 2.0 Mutual-TLS Client Authentication and Certificate-Bound Access Tokens: https://www.rfc-editor.org/rfc/rfc8705.html
- IETF RFC 9449, OAuth 2.0 Demonstrating Proof of Possession (DPoP): https://www.rfc-editor.org/rfc/rfc9449.html

### 플랫폼과 제조사 문서

- Android Developers, Verify hardware-backed key pairs with key attestation: https://developer.android.com/privacy-and-security/security-key-attestation
- AOSP, Key and ID attestation: https://source.android.com/docs/security/features/keystore/attestation
- AOSP, KeyMint `KeyOrigin.aidl`: https://android.googlesource.com/platform/hardware/interfaces/+/refs/heads/main/security/keymint/aidl/android/hardware/security/keymint/KeyOrigin.aidl
- Android Developers, Play Integrity API overview: https://developer.android.com/google/play/integrity/overview
- Android Developers, Set up managed configurations: https://developer.android.com/work/managed-configurations
- Android Developers, Manage multiple users (dedicated devices): https://developer.android.com/work/dpc/dedicated-devices/multiple-users
- Google, Android Management API introduction: https://developers.google.com/android/management/introduction
- Google, Android Management API REST `enterprises.devices`: https://developers.google.com/android/management/reference/rest/v1/enterprises.devices
- Firebase, Best practices for FCM registration token management: https://firebase.google.com/docs/cloud-messaging/manage-tokens
- Microsoft Learn, What is device identity in Microsoft Entra ID?: https://learn.microsoft.com/en-us/entra/identity/devices/overview
- Microsoft Learn, Conditional Access grant controls: https://learn.microsoft.com/en-us/entra/identity/conditional-access/concept-conditional-access-grant
- Microsoft Learn, Access token claims reference: https://learn.microsoft.com/en-us/entra/identity-platform/access-token-claims-reference
- Microsoft Learn, Shared device mode overview: https://learn.microsoft.com/en-us/entra/identity-platform/msal-shared-devices
- Microsoft Learn, Shared device mode for Android devices: https://learn.microsoft.com/en-us/entra/identity-platform/msal-android-shared-devices
- Microsoft Learn, Set up Intune enrollment for Android Enterprise dedicated devices: https://learn.microsoft.com/en-us/intune/intune-service/enrollment/android-kiosk-enroll
- Samsung Knox, Device Health Attestation (whitepaper): https://docs.samsungknox.com/admin/fundamentals/whitepaper/samsung-knox-mobile-security/system-security/device-health-attestation/
- Samsung Knox, Knox Manage shared Android device quickstart: https://docs.samsungknox.com/admin/knox-manage/original-console/quickstart-guides/shared-android-device-quickstart/
- Apple Platform Deployment, Shared iPad overview: https://support.apple.com/guide/deployment/shared-ipad-overview-dep9a34c2ba2/web
- Keycloak, Securing applications with DPoP: https://www.keycloak.org/securing-apps/dpop
- Keycloak, Specifications implemented: https://www.keycloak.org/securing-apps/specifications

### 한국 자료

- 행정안전부, 모바일 전자정부 구축: https://mois.go.kr/frt/sub/a06/b04/mobileEgov/screen.do
- 삼성전자, 경찰청 재난망폰 사례(제조사 홍보 자료): https://www.samsung.com/sec/business/insights/case-study/reference-police-agency
- 2차 자료, IT위키 국가 정보보안 기본지침 제56조: https://itwiki.kr/w/%EA%B5%AD%EA%B0%80_%EC%A0%95%EB%B3%B4%EB%B3%B4%EC%95%88_%EA%B8%B0%EB%B3%B8%EC%A7%80%EC%B9%A8_%EC%A0%9C56%EC%A1%B0
- 2차 자료, AhnLab 국가 사이버보안 기본지침 개정 해설: https://www.ahnlab.com/ko/contents/content-center/36176
- 2차 자료, 목포대 정보보안 게시판 N2SF 가이드라인 공개 기사 재게시: https://mnuk2.mokpo.ac.kr/bbs/mnusecu/1544/278520/artclView.do

### 찾았지만 쓰지 않은 자료

- AOSP 커밋 미러(gitlab.e.foundation)의 ID 증명 권한 설명은 원문 확인 전이라 2차 자료로만 언급했다.
- MDM과 N2SF에 관한 보안 업체 블로그와 언론 기사는 1차 원문을 대신할 수 없어 인용하지 않았다.
