# 인수인계의 확인과 되돌리기 사례 조사

- 조사일: 2026-11-23
- 질문: 넘겨주는 팀이 받는 팀을 골라 인수인계를 기록하면 수색 구역 배정이 곧바로 옮겨지고, 받는 팀이 확인 기간 안에 "확인"이나 "되돌리기"를 고르는 설계와 비슷한 사례가 있는가? 있다면 각 사례에서는 무엇을 정했는가?
- 상태: 조사 결과다. 수리맵 설계 결정은 하지 않았다.
- 배경: 수색 차수, 근무 교대, 인수인계 메모의 기존 요구와 현재 구현 차이는 [수색 차수와 인수인계 기능 정의 문서](../features/handover.md)에 있다. 이번 조사의 대상 설계는 아래와 같다.
  - 넘겨주는 팀이 앱에서 받는 팀을 골라 인수인계를 기록한다.
  - 기록하면 넘겨주는 팀의 수색 구역 배정이 곧바로 받는 팀으로 옮겨진다.
  - 받는 팀 앱에는 "확인"과 "되돌리기" 버튼이 있는 확인 카드가 뜬다. 되돌리기는 확인 기간(예: 30분) 안에만 할 수 있다. 되돌리면 배정이 넘겨준 팀으로 돌아간다.
  - 확인을 누르거나 확인 기간이 지나면 확정된다. 확정 전에는 받은 팀이 그 구역을 다른 팀에 다시 넘길 수 없다.
  - 현장은 산악 지역이라 오프라인일 때가 있다.
- 이어지는 조사: 오프라인 동기화의 재연결 동작과 공개 수치는 [오프라인 동기화와 복구 시간 비교 조사](offline-sync-recovery-benchmarks.md)에 있다. 그 문서에서 다룬 ServiceNow Mobile의 Outbox와 cache 정책은 다시 쓰지 않는다.

이 문서에서 **사실:** 은 출처에 적힌 내용이다. **추론:** 은 조사자의 해석이다. 출처의 접근일은 모두 2026-11-23이다. 영어 출처의 상태 이름, 필드 이름, 화면 이름은 원문 표기를 그대로 옮겼다. 출처 종류는 표준(국제 표준과 업계 표준), 정부 지침(법령과 공공기관 문서), 제품 문서(제품 공식 문서), 학술(논문)로 나눴다.

## 결론 요약

1. **안전 업무에서는 인계 절차를 마쳐야 책임이 넘어간다.** 재난 지휘 체계(ICS)에서는 브리핑을 마치고 효력 일시를 알린 때 지휘권이 넘어간다. 응급 의료 이송과 병원 교대 인수인계에서는 받는 쪽이 보고를 받고 돌봄을 맡은 때 책임이 넘어간다. 캘리포니아 구급차 환자 인계 규정에서는 응급실 의료진이 전자 서명으로 확인해야 인계가 끝난다. 조사한 안전 업무 사례 중에 "먼저 옮기고, 받는 쪽이 나중에 되돌릴 수 있게 하는" 흐름은 찾지 못했다.
2. **즉시 이관은 같은 조직 안의 배정에서 보인다.** 출동 지령(CAD)에서는 지령으로 단위를 사건에 먼저 붙이고 수신 확인(acknowledgment)을 따로 기록한다. Google Workspace의 같은 조직 안 소유권 이관과 Dynamics 365의 레코드 라우팅도 받는 쪽 수락 없이 배정이 바로 된다. 다만 이 사례들에서 받는 쪽이 거절하면 배정은 넘겨준 쪽이 아니라 배정 담당자(디스패처, 관리자, 대기열)에게 돌아간다.
3. **시간이 지나면 일어나는 일은 분야마다 다르다.** 도메인 이관(ICANN, EPP)과 고객 응대 라우팅(Dynamics 365)에는 기간이 지나면 자동으로 승인하거나 배정하는 설정이 있다. 온콜 도구(PagerDuty, Opsgenie)와 출동 지령 기능 표준(LEITSC)에서는 시간이 지나면 다음 사람이나 디스패처에게 알림이 간다. 이 경우 확정 상태로 바뀌지 않는다. PagerDuty의 acknowledgement timeout이 지나면 incident는 오히려 확인 전 상태(triggered)로 돌아간다.
4. **확인 기간 값의 근거는 찾지 못했다.** 확인한 기본값은 Dynamics 365 알림 120초, Gmail 보내기 취소 5초에서 30초, PagerDuty escalation timeout 30분, GitHub 저장소 이관 초대 1일, ICANN 무응답 승인 5일이다. 캘리포니아 구급차 환자 인계 표준은 30분(90%)이다. 조사한 출처 중에 특정 분 값을 고른 이유를 적은 곳은 없었다.
5. **확정 전 재이관 제한과 오프라인 처리는 근거가 약하다.** 이관 뒤 다시 넘기는 것을 제한하는 1차 출처는 ICANN의 이관 후 60일 거부 사유 하나만 찾았다. 이 제한은 확정 전이 아니라 이관이 끝난 뒤에 적용된다. 오프라인에서는 웹 시스템이 안 될 때 종이 양식을 쓰는 INSARAG 지침, 음성이나 차량 단말로 지령을 확인하는 CAD 기능 표준, 대면이 어려우면 전화나 화상으로 실시간 인계하라는 Joint Commission 권고를 확인했다. 받는 쪽이 오프라인인 동안 확인 기간을 어떻게 세는지 다룬 출처는 찾지 못했다.

## 조사 범위

| 분야 | 확인한 출처 | 출처 종류 |
|---|---|---|
| 병원 교대 인수인계 | Joint Commission Sentinel Event Alert 58, I-PASS 원 논문(Pediatrics 2012), IHI SBAR 도구 | 정부 지침에 준하는 인증기관 문서, 학술, 비영리기관 도구 |
| 응급 의료 이송 | 캘리포니아 EMS Authority AB 40 시행 규정과 규정 제정 이유서(ISOR) | 정부 지침 |
| 재난 지휘 체계 | FEMA NIMS 3판(2017), FEMA IS-0200.c 교재 발췌본 | 정부 지침 |
| 국제 수색 구조 | INSARAG Guidelines 2020 Volume II Manual B | 국제기구 지침 |
| 국내 경찰 | 지역경찰의 조직 및 운영에 관한 규칙(경찰청예규 제602호) | 정부 지침 |
| 출동 지령 | LEITSC 법집행 CAD 표준 기능 명세 | 정부 지원 업계 표준 |
| 현장 업무 관리 | ServiceNow Field Service Management | 제품 문서 |
| 고객 응대 라우팅 | Dynamics 365 Contact Center, Salesforce Omni-Channel | 제품 문서 |
| 온콜 이관 | PagerDuty, Opsgenie | 제품 문서 |
| 일반 소유권 이관 | Google Drive, GitHub, Gmail, IETF RFC 5730과 RFC 5731, ICANN Transfer Policy | 제품 문서, 표준, 정부 지침에 준하는 정책 |

IAMSAR, 미국 Land SAR Addendum, NWCG, NFPA 표준은 원문을 읽지 못했다. 자세한 내용은 [확인하지 못한 것](#확인하지-못한-것)에 적었다.

## 1. 받는 쪽의 확인과 거절

### 병원 교대 인수인계

- **사실:** Joint Commission의 Sentinel Event Alert 58(2017-09)에는 보내는 쪽(sender)이 환자 정보를 전달하고 환자 돌봄을 받는 쪽(receiver)에게 넘긴다고 적혀 있다. 받는 쪽은 "those who will receive patient data and accept care of the patient"로 정의된다. ([SEA 58](https://digitalassets.jointcommission.org/api/public/content/a05e74ef89484e2084b6511189b73a99))
- **사실:** 같은 문서에는 인증 기준 PC.02.02.01 EP 2가 인용되어 있다. 이 기준은 인수인계 절차에 "the opportunity for discussion between the giver and receiver of patient information"을 두라고 요구한다. ([SEA 58](https://digitalassets.jointcommission.org/api/public/content/a05e74ef89484e2084b6511189b73a99))
- **사실:** 권고 2에는 "Avoid making hand-offs using solely electronic or paper communications."라고 적혀 있다. 대면이 어려우면 전화나 화상으로 실시간 인계하라고 적혀 있다. 질문할 시간과 기회를 충분히 주라는 권고도 있다. ([SEA 58](https://digitalassets.jointcommission.org/api/public/content/a05e74ef89484e2084b6511189b73a99))
- **사실:** 권고 3에는 이후 확인을 위해 보내는 쪽과 받는 쪽의 연락처를 남기라고 적혀 있다. 인수인계에 담을 최소 항목의 첫 줄도 "Sender contact information"이다. ([SEA 58](https://digitalassets.jointcommission.org/api/public/content/a05e74ef89484e2084b6511189b73a99))
- **사실:** 같은 문서에서 I-PASS는 Illness severity, Patient summary, Action list, Situation awareness and contingency plans, Synthesis by receiver의 약자로 소개된다. 9개 의료기관에서 I-PASS를 도입한 기간에 예방 가능한 위해 사건은 30%, 의료 오류는 23% 줄었다고 적혀 있다. ([SEA 58](https://digitalassets.jointcommission.org/api/public/content/a05e74ef89484e2084b6511189b73a99))
- **사실:** I-PASS 원 논문(Starmer 외, Pediatrics 2012)에는 시범 연구에서 받는 레지던트의 되읽기(read-back)가 자주 빠졌다고 적혀 있다. 저자들은 이 점을 니모닉에 넣을 요소로 골랐다. 논문 본문에서 "Synthesis by receiver"의 구체적 절차는 확인하지 못했다. ([Starmer 2012, PMC](https://pmc.ncbi.nlm.nih.gov/articles/PMC9923540), [PSNet 요약](https://psnet.ahrq.gov/issue/i-pass-mnemonic-standardize-verbal-handoffs))
- **사실:** IHI의 SBAR 도구는 Situation, Background, Assessment, Recommendation 네 요소로 이뤄진다. 이 도구 설명에는 받는 쪽이 확인하거나 되읽는 단계가 없다. ([IHI SBAR](https://www.ihi.org/library/tools/sbar-tool-situation-background-assessment-recommendation))
- **추론:** 병원 인수인계 지침에서 받는 쪽 확인은 정보를 이해했는지 맞춰 보는 대화다. 버튼 한 번으로 수락을 기록하는 절차는 아니다. 시간이 지나면 자동으로 인계가 끝나는 규칙은 이 문서들에 없다.

### 응급 의료 이송

- **사실:** 캘리포니아 AB 40 시행 규정 § 100002.18은 "Transfer of care"를 환자를 응급실 침대 등에 옮기고, 응급실 의료진이 보고를 받고 ePCR 안의 전자 서명으로 인계를 확인한 때로 정의한다. ([AB 40 APOT 규정 승인본](https://emsa.ca.gov/wp-content/uploads/sites/71/2026/03/Approved-_-AB-40-APOT-Regulations-Reg-Text-12.05.25-OAL-Edit.pdf))
- **사실:** 같은 규정 § 100002.02에서 APOT(ambulance patient offload time)는 구급차가 응급실 구급차 진입로에 도착한 때부터 "the emergency department assumes responsibility for care of the patient" 때까지의 시간이다. ([AB 40 APOT 규정 승인본](https://emsa.ca.gov/wp-content/uploads/sites/71/2026/03/Approved-_-AB-40-APOT-Regulations-Reg-Text-12.05.25-OAL-Edit.pdf))
- **사실:** § 100002.09에는 전자 서명이 인계 시점에 응급실 의료진에게서 받는 것으로 적혀 있다. ([AB 40 APOT 규정 승인본](https://emsa.ca.gov/wp-content/uploads/sites/71/2026/03/Approved-_-AB-40-APOT-Regulations-Reg-Text-12.05.25-OAL-Edit.pdf))
- **추론:** 이 규정에서는 받는 쪽 확인 전까지 책임이 넘어가지 않는다. 시간 기준(아래 4절)은 받는 쪽이 얼마나 빨리 받아야 하는지에 대한 성과 기준이다. 시간이 지나도 인계가 자동으로 끝나지 않는다.

### 재난 지휘 체계(NIMS, ICS)

- **사실:** NIMS 3판(2017)에는 지휘권이 넘어갈 때 "the transfer process includes a briefing that captures essential information for continuing safe and effective operations, and notifying all personnel involved in the incident"라고 적혀 있다. ([NIMS 2017](https://www.fema.gov/sites/default/files/2020-07/fema_nims_doctrine-2017.pdf), 22쪽)
- **사실:** FEMA IS-0200.c 교재 발췌본(2018-03)에는 가능하면 지휘권 이전을 대면으로 하고 완전한 브리핑을 포함하라고 적혀 있다. 지휘권 이전의 효력 일시를 사건에 참여한 모든 인원에게 알리라고 적혀 있다. 이 절차는 감독 직위의 사람이 바뀔 때마다 쓸 수 있다고 적혀 있다. ([FEMA Transfer of Command](https://training.fema.gov/emiweb/is/icsresource/assets/Transfer%20of%20Command.pdf))
- **사실:** 같은 발췌본에는 장기 사건에서 인원을 교대해야 할 때를 지휘권 이전 사유의 하나로 든다. ([FEMA Transfer of Command](https://training.fema.gov/emiweb/is/icsresource/assets/Transfer%20of%20Command.pdf))
- **사실:** NIMS에는 현장에 도착한 인원이 배정을 받으려면 체크인해야 한다고 적혀 있다. 철수할 때는 진행 중인 일의 상태와 배정을 다음 인원에게 브리핑하라고 적혀 있다. ([NIMS 2017](https://www.fema.gov/sites/default/files/2020-07/fema_nims_doctrine-2017.pdf), 14쪽에서 15쪽)
- **사실:** NIMS에서 unity of command는 "each individual only reports to one person"이다. ([NIMS 2017](https://www.fema.gov/sites/default/files/2020-07/fema_nims_doctrine-2017.pdf), 23쪽)
- **사실:** NIMS 용어집에서 operational period는 보통 12시간에서 24시간이다. 긴 operational period 안에서는 교대 브리핑을 할 수 있다고 적혀 있다. ([NIMS 2017](https://www.fema.gov/sites/default/files/2020-07/fema_nims_doctrine-2017.pdf), 용어집과 부록)
- **추론:** ICS에서는 브리핑을 마치고 효력 일시를 알린 때 지휘권이 넘어간다. 이전 일시를 먼저 정하고 나중에 받는 쪽이 되돌리는 절차는 이 문서들에 없다. 이 문서들은 지휘권 이전을 다룬다. 수색 구역 같은 현장 배정을 팀끼리 넘기는 절차를 직접 다루지는 않는다.

### 국제 도시 수색 구조(INSARAG)

- **사실:** INSARAG Guidelines 2020 Volume II Manual B의 철수(Demobilisation) 절에는 "Ensure proper hand-over is conducted to USAR Teams that take over the tasks of the departing team."이라고 적혀 있다. 수색과 구조 담당에게는 작업을 멈추고 이어받을 조직에 넘길 준비를 하라고 적혀 있다. ([INSARAG Manual B](https://insarag.org/wp-content/uploads/2021/09/INSARAG20Guidelines20Vol20II2C20Man20B.pdf), 4.4절)
- **사실:** 같은 문서의 Worksite Report Form은 "Report of activity at a worksite for a specific work period or to handover the worksite"다. ([INSARAG Manual B](https://insarag.org/wp-content/uploads/2021/09/INSARAG20Guidelines20Vol20II2C20Man20B.pdf), 표 8)
- **사실:** 팀을 배정할 때 UCC(USAR Coordination Cell)는 Assignment Briefing Package를 작성한다. 이 묶음의 부록 C는 이전 Worksite Report Form이다. ([INSARAG Manual B](https://insarag.org/wp-content/uploads/2021/09/INSARAG20Guidelines20Vol20II2C20Man20B.pdf), 표 9)
- **사실:** 같은 문서에는 계획 과정의 목표가 "assign and reassign teams to prioritised worksites"라고 적혀 있다. UCC는 팀을 지휘 통제하지 않고 조정한다고 적혀 있다. 팀 배정은 UCC가 이메일, 전화, 대면으로 직접 알린다. ([INSARAG Manual B](https://insarag.org/wp-content/uploads/2021/09/INSARAG20Guidelines20Vol20II2C20Man20B.pdf), 5절과 5.9절)
- **추론:** INSARAG에서 작업 현장의 배정과 재배정은 조정 조직(UCC)이 한다. 떠나는 팀은 Worksite Report로 인계 자료를 남긴다. 팀끼리 직접 현장을 넘기고 받는 팀이 확인하는 절차는 이 문서에서 찾지 못했다.

### 국내 지역경찰

- **사실:** 지역경찰의 조직 및 운영에 관한 규칙(경찰청예규 제602호, 2022-05-31 시행) 제8조 제2항 제1호에는 순찰팀장의 직무로 "근무교대시 주요 취급사항 및 장비 등의 인수인계 확인"이 적혀 있다. ([국가법령정보센터](https://www.law.go.kr/행정규칙/지역경찰의조직및운영에관한규칙))
- 원문 미확인: 인수인계 확인의 구체적 방법, 확인 시점, 거절 절차를 정한 공개 규정이나 매뉴얼은 찾지 못했다.
- **추론:** 국내 지구대 교대에서 인수인계 확인은 순찰팀장의 직무다. 받는 팀장이 확인하는 쪽인지, 넘기는 팀장이 확인하는 쪽인지는 이 조문만으로 알 수 없다.

### 출동 지령(CAD)

- **사실:** LEITSC의 법집행 CAD 표준 기능 명세 1.6절에는 추천되거나 선택된 단위를 지령하고 "their acknowledgment recorded"라고 적혀 있다. 여러 단위를 지령하면 한 단위를 사건이 끝날 때까지 책임지는 주 대응 단위로 정한다. ([LEITSC CAD 명세](https://bja.ojp.gov/sites/g/files/xyckuh186/files/media/document/leitsc_law_enforcement_cad_systems.pdf))
- **사실:** 1.6.4 Recognize Acknowledgement에는 배정된 단위가 지령 정보를 받았다고 디스패처에게 응답한다고 적혀 있다. 이 응답은 음성 통신이나 차량용 데이터 단말(mobile data computer)로 한다. ([LEITSC CAD 명세](https://bja.ojp.gov/sites/g/files/xyckuh186/files/media/document/leitsc_law_enforcement_cad_systems.pdf))
- **사실:** 1.6.2에는 단위를 새 사건에 다시 배정하면 이전 사건에서 자동으로 배정이 풀린다고 적혀 있다. 사건에서 모든 단위가 빠지면 그 사건은 지령 대기열(pending dispatch queue)로 돌아간다. ([LEITSC CAD 명세](https://bja.ojp.gov/sites/g/files/xyckuh186/files/media/document/leitsc_law_enforcement_cad_systems.pdf))
- **추론:** CAD에서는 배정이 먼저 일어나고 수신 확인은 따로 기록된다. 이 점은 수리맵의 즉시 이관과 비슷하다. 다만 배정과 재배정은 디스패처가 한다. 현장 단위끼리 배정을 넘기는 흐름은 이 명세에 없다.

### 현장 업무 관리(ServiceNow Field Service Management)

- **사실:** ServiceNow FSM에서 담당자는 작업 지시 과업(work order task) 알림에서 Accept나 Reject를 누른다. 알림에서 거절하면 "the task assignment is deleted and the task returns to the Pending Dispatch state"라고 적혀 있다. (Brazil 릴리스 문서, [ServiceNow 푸시 알림에서 수락과 거절](https://www.servicenow.com/docs/r/field-service-management/work-order-management/accept-reject-wot-push-notification.html))
- **사실:** 외부 협력사 흐름에서는 담당자가 거절하면 과업이 그 담당자의 관리자에게 간다. 관리자가 거절하면 다음으로 맞는 그룹의 관리자에게 간다. 맞는 그룹이 없으면 Pending dispatch 상태로 남아 디스패처가 직접 배정한다. 거절할 때는 사유를 고른다. (Xanadu 릴리스 문서, [ServiceNow 모바일에서 수락과 거절](https://www.servicenow.com/docs/r/xanadu/field-service-management/self-assign.html))
- 원문 미확인: 수락이나 거절에 기한이 있는지, 기한이 지나면 자동 처리가 있는지는 읽은 두 문서에 없다.
- 원문 미확인: Salesforce Field Service 모바일 앱에서 담당자가 배정을 거절하는 공식 절차는 찾지 못했다.

### 고객 응대 라우팅(Dynamics 365, Salesforce Omni-Channel)

- **사실:** Dynamics 365 Contact Center의 알림 템플릿에는 Accept와 Reject 버튼이 있다. Countdown 기본값은 120초다. 이 시간이 지나면 알림이 사라진다. ([Dynamics 365 알림 템플릿](https://learn.microsoft.com/en-us/dynamics365/customer-service/administer/notification-templates))
- **사실:** 같은 문서에는 대화의 전달(transfer)과 상담(consult)에도 기본 템플릿이 따로 있다고 적혀 있다. ([Dynamics 365 알림 템플릿](https://learn.microsoft.com/en-us/dynamics365/customer-service/administer/notification-templates))
- **사실:** 같은 문서에는 레코드 라우팅에 쓰는 사용자 템플릿에 거절 버튼을 넣지 말라고 권고한다. 이유는 "By design, the record will be assigned to the routed representative even if you provide the option to the representative to reject the assignment."다. ([Dynamics 365 알림 템플릿](https://learn.microsoft.com/en-us/dynamics365/customer-service/administer/notification-templates))
- **사실:** Auto-assign work items를 켜면 알림이 뜰 때 대화가 담당자에게 자동으로 배정된다. 배정은 바로 되거나 시간이 지난 뒤에 된다. 이때 Accept 버튼 이름은 Open으로 바뀌고 Reject 버튼은 꺼진다. 문서에는 이 설정이 Facebook이나 SMS처럼 응답 기한이 긴 요청에 맞는다고 적혀 있다. ([Dynamics 365 자동 배정](https://learn.microsoft.com/en-us/dynamics365/customer-service/administer/enable-auto-assign-conversations))
- **사실:** 같은 문서에는 담당자가 들어온 대화를 거절하면 대화가 대기열로 돌아간다고 적혀 있다. ([Dynamics 365 자동 배정](https://learn.microsoft.com/en-us/dynamics365/customer-service/administer/enable-auto-assign-conversations))
- **사실:** 알림을 놓친 담당자의 상태를 자동으로 inactive로 바꾸는 설정이 있다. 이 설정을 켜면 그 담당자에게 새 과업이 배정되지 않는다. ([Dynamics 365 놓친 알림](https://learn.microsoft.com/en-us/dynamics365/customer-service/administer/manage-missed-notifications))
- 2차 확인: Salesforce AgentWork 객체의 PushTimeout은 담당자가 응답해야 하는 초 단위 제한이다. 시간이 지나면 작업이 다른 담당자에게 간다. Status 값은 명시적 거절인 Declined와 시간 초과 거절인 DeclinedOnPushTimeout으로 나뉜다. 이 내용은 검색 결과에 나온 문서 발췌로만 확인했다. 문서 본문은 접근이 거부되어(403) 직접 읽지 못했다. ([Salesforce AgentWork](https://developer.salesforce.com/docs/atlas.en-us.object_reference.meta/object_reference/sforce_api_objects_agentwork.htm))

### 온콜 이관(PagerDuty, Opsgenie)

- **사실:** PagerDuty 문서에는 "The user who acknowledges an incident claims ownership of the issue and halts the escalation process."라고 적혀 있다. ([PagerDuty Incidents](https://docs.pagerduty.com/incident-management/incidents/overview))
- **사실:** incident를 특정 사용자에게 재배정하면 escalation policy가 더 이상 쓰이지 않는다. 그 사용자가 확인하지 않으면 다른 사람에게 알림이 가지 않는다. escalation policy에 재배정하면 누군가 확인할 때까지 단계가 계속 돈다. ([PagerDuty 재배정](https://docs.pagerduty.com/incident-management/incidents/reassign-incidents))
- 원문 미확인: 이미 확인된 incident를 재배정할 때 상태가 triggered로 돌아가는지, 새 담당자의 확인이 필요한지는 재배정 문서에 적혀 있지 않다.
- **사실:** PagerDuty의 on-call handoff notification은 당번 시작이나 종료 최대 48시간 전에 push나 이메일로 간다. 문서에는 당번 인수를 수락하는 절차가 없다. ([PagerDuty 알림 규칙](https://docs.pagerduty.com/account-admin/user-profile/notification-rules))
- **사실:** Opsgenie의 escalation 규칙 조건은 "Alert is not acknowledged"와 "Alert is not closed" 두 가지다. alert를 확인하거나 닫으면 escalation policy가 멈춘다. ([Opsgenie escalation](https://support.atlassian.com/opsgenie/docs/how-do-escalations-work-in-opsgenie/))

### 일반 소유권 이관

- **사실:** Google Drive에서 개인 계정끼리 소유권을 넘기면 받는 사람이 수락해야 한다. 수락 전까지는 보내는 사람이 소유자로 남는다. 받는 사람이 거절하면 보내는 사람이 소유권을 유지한다. ([Google Drive 소유권 이관](https://support.google.com/drive/answer/2494892?hl=en))
- **사실:** 같은 문서에는 회사나 학교 계정에서는 "The transfer doesn't require the new owner to accept."라고 적혀 있다. 이 경우 같은 조직 안의 사람에게만 넘길 수 있다. ([Google Drive 소유권 이관](https://support.google.com/drive/answer/2494892?hl=en))
- **사실:** GitHub에서 개인 계정끼리 저장소를 넘기면 받는 사람에게 확인 이메일이 간다. 하루 안에 수락하지 않으면 초대가 만료된다. ([GitHub 저장소 이관](https://docs.github.com/en/repositories/creating-and-managing-repositories/transferring-a-repository))
- **사실:** 도메인 등록 정보 표준 EPP(RFC 5730) 2.9.3.4에는 "A server MAY automatically approve or reject all transfer requests that are not explicitly approved or rejected by the current sponsoring client within a fixed amount of time."라고 적혀 있다. 기다리는 시간과 기본 동작은 서버 운영자가 정하는 local matter다. ([RFC 5730](https://www.rfc-editor.org/rfc/rfc5730.html))
- **사실:** RFC 5731 3.1.3에서 대기 중인 이관의 acDate는 "the date and time by which a response is required before an automated response action will be taken by the server"다. ([RFC 5731](https://www.rfc-editor.org/rfc/rfc5731.html))
- **사실:** ICANN Transfer Policy I.A.3.5에는 기존 등록 대행자가 5일 안에 응답하지 않으면 "will result in a default "approval" of the transfer"라고 적혀 있다. I.A.6.2에는 5일 안에 NACK을 받지 않으면 등록 관리 기관이 이관을 마친다고 적혀 있다. ([ICANN Transfer Policy](https://www.icann.org/resources/pages/transfer-policy-2016-06-01-en))
- **추론:** 소유권 이관 사례에서 받는 쪽 수락이 필요한 경우에는 수락 전까지 넘겨주는 쪽이 소유자로 남는다. 수리맵 설계처럼 먼저 옮기고 받는 쪽이 되돌리는 흐름은 이 사례들에 없다. 같은 조직 안에서는 수락 없이 바로 넘기는 경우(Google Workspace)가 있다. 이 경우 되돌리기 기간은 문서에 없다.

## 2. 시간이 지나면 상태가 바뀌는 흐름

| 사례 | 기간이 지나면 일어나는 일 | 기본값 | 출처 |
|---|---|---|---|
| ICANN 도메인 이관 | 기존 등록 대행자가 응답하지 않으면 승인으로 본다. | 5일 | [ICANN Transfer Policy](https://www.icann.org/resources/pages/transfer-policy-2016-06-01-en) |
| EPP 표준 | 서버가 자동 승인하거나 자동 거절할 수 있다. 기간은 운영자가 정한다. | 표준에 없음 | [RFC 5730](https://www.rfc-editor.org/rfc/rfc5730.html) |
| Dynamics 365 자동 배정 | 설정을 켜면 시간이 지난 뒤 담당자에게 배정된다. 끄면 알림만 사라진다. | 알림 120초 | [자동 배정](https://learn.microsoft.com/en-us/dynamics365/customer-service/administer/enable-auto-assign-conversations), [알림 템플릿](https://learn.microsoft.com/en-us/dynamics365/customer-service/administer/notification-templates) |
| Salesforce Omni-Channel | 시간이 지나면 다른 담당자에게 간다. 상태는 DeclinedOnPushTimeout이다. | 확인하지 못함 | 2차 확인, [AgentWork](https://developer.salesforce.com/docs/atlas.en-us.object_reference.meta/object_reference/sforce_api_objects_agentwork.htm) |
| GitHub 저장소 이관 | 수락하지 않으면 초대가 만료된다. 저장소는 넘어가지 않는다. | 1일 | [GitHub](https://docs.github.com/en/repositories/creating-and-managing-repositories/transferring-a-repository) |
| PagerDuty escalation | 확인하지 않으면 다음 escalation 단계로 넘어간다. | 30분 | [Escalation Policies](https://docs.pagerduty.com/incident-management/schedules/escalation-policies) |
| PagerDuty acknowledgement timeout | 확인한 incident가 처리되지 않으면 triggered로 돌아가고 escalation이 다시 시작된다. | 기본으로 꺼짐 | [Incidents](https://docs.pagerduty.com/incident-management/incidents/overview), [서비스 설정](https://docs.pagerduty.com/incident-management/services/configurable-service-settings) |
| Opsgenie escalation | 확인하지 않거나 닫지 않으면 다음 대상에게 알린다. | 문서 예시 5분, 10분 | [Opsgenie](https://support.atlassian.com/opsgenie/docs/how-do-escalations-work-in-opsgenie/) |
| LEITSC CAD 상태 타이머 | 디스패처에게 소리나 화면으로 알린다. 디스패처의 확인이나 조치를 기록하면 타이머가 다시 시작된다. | 명세에 없음 | [LEITSC CAD 명세](https://bja.ojp.gov/sites/g/files/xyckuh186/files/media/document/leitsc_law_enforcement_cad_systems.pdf), Timed Alerts 항목 |

- **사실:** PagerDuty 문서에는 acknowledgement timeout의 목적이 "help ensure that an acknowledged incident is not forgotten"이라고 적혀 있다. ([PagerDuty 서비스 설정](https://docs.pagerduty.com/incident-management/services/configurable-service-settings))
- **사실:** PagerDuty escalation policy는 9번까지 반복할 수 있다. 반복이 끝나면 incident는 마지막 사용자에게 배정된 채 남고 알림은 더 가지 않는다. Opsgenie는 한 alert에 20번까지 반복할 수 있다. ([PagerDuty Escalation Policies](https://docs.pagerduty.com/incident-management/schedules/escalation-policies), [Opsgenie](https://support.atlassian.com/opsgenie/docs/how-do-escalations-work-in-opsgenie/))
- **추론:** 조사한 사례는 두 무리로 나뉜다. 도메인 이관과 응답 기한이 긴 고객 응대에서는 기간이 지나면 자동으로 승인하거나 배정한다. 온콜과 출동 지령에서는 기간이 지나면 사람에게 다시 알리거나 다음 사람에게 넘긴다. 뒤쪽 무리에서 확인 없이 확정 상태로 바뀌는 사례는 찾지 못했다.

## 3. 확정 전 재이관 제한

- **사실:** ICANN Transfer Policy I.A.3.7.6에는 이관된 지 60일(또는 더 짧게 정할 기간) 안의 도메인이면 기존 등록 대행자가 이관 요청을 거부할 수 있다고 적혀 있다. II.C.2에는 등록자 변경 뒤 60일 동안 등록 대행자 간 이관을 잠그라고 적혀 있다. 등록자는 변경 요청 전에 이 잠금을 빼 달라고 할 수 있다. ([ICANN Transfer Policy](https://www.icann.org/resources/pages/transfer-policy-2016-06-01-en))
- 2차 자료: ICANN Transfer Policy Review 작업반은 이관 후 60일 제한을 30일로 줄이라고 권고했다. ICANN 공개 의견 요약 자료와 업계 기사로 확인했다. ICANN 이사회가 이 권고를 채택했는지와 시행일은 확인하지 못했다. ([공개 의견 요약](https://itp.cdn.icann.org/en/files/public-comment/summary-report-transfer-policy-review-wg-final-report-icann-board-consideration-02-07-2025-en.pdf), [DomainIncite](https://domainincite.com/?p=30891))
- **사실:** RFC 5731 2.3에는 pendingTransfer 상태를 다른 pending 상태(pendingCreate, pendingDelete, pendingRenew, pendingUpdate)와 함께 둘 수 없다고 적혀 있다. 같은 절에서 pendingTransfer 중에 어떤 명령을 거절하는지는 정하지 않는다. ([RFC 5731](https://www.rfc-editor.org/rfc/rfc5731.html))
- **사실:** LEITSC CAD 명세에서는 디스패처가 단위를 새 사건에 다시 배정할 수 있다. 이때 이전 사건의 배정은 자동으로 풀린다. 수신 확인 전에 재배정을 막는다는 내용은 없다. ([LEITSC CAD 명세](https://bja.ojp.gov/sites/g/files/xyckuh186/files/media/document/leitsc_law_enforcement_cad_systems.pdf))
- **사실:** PagerDuty에서는 incident를 사용자나 escalation policy에 재배정할 수 있다. 확인 전 재배정을 막는다는 내용은 문서에 없다. 저긴급 incident는 특정 escalation 단계로 재배정할 수 없다는 제한만 있다. ([PagerDuty 재배정](https://docs.pagerduty.com/incident-management/incidents/reassign-incidents))
- **추론:** 재이관 제한의 1차 근거는 ICANN 하나만 찾았다. 그 제한은 이관이 끝난 뒤의 제한이다. 목적은 도메인 탈취 같은 사기 이관을 막는 것으로 보인다. 확정 전 재이관을 막는 사례는 찾지 못했다. 배정 담당자가 따로 있는 사례(CAD, PagerDuty)에서는 확인 전에도 재배정을 허용한다.

## 4. 확인 기간 길이의 근거

| 사례 | 값 | 값의 의미 | 문서에 적힌 이유 |
|---|---|---|---|
| Gmail 보내기 취소 | 5초, 10초, 20초, 30초 중 선택 | 보낸 사람이 보낸 메일을 되돌릴 수 있는 시간 | 없음 ([Gmail](https://support.google.com/mail/answer/2819488?hl=en)) |
| Dynamics 365 알림 | 기본 120초 | 알림이 사라지는 시간, 자동 배정을 켜면 배정 시점 | 없음 ([알림 템플릿](https://learn.microsoft.com/en-us/dynamics365/customer-service/administer/notification-templates)) |
| Salesforce Omni-Channel 음성 | 0초에서 20초 | 담당자가 응답해야 하는 시간 | 없음, 2차 확인 ([AgentWork](https://developer.salesforce.com/docs/atlas.en-us.object_reference.meta/object_reference/sforce_api_objects_agentwork.htm)) |
| PagerDuty escalation timeout | 기본 30분, 대상 한 명이면 최소 1분, 여러 명이면 최소 3분 | 다음 단계로 넘어가기 전까지 기다리는 시간 | 없음 ([Escalation Policies](https://docs.pagerduty.com/incident-management/schedules/escalation-policies)) |
| 캘리포니아 구급차 환자 인계 | 30분 이내, 90% | 받는 쪽(응급실)이 인계를 받아야 하는 성과 기준 | "clear, measurable benchmark"라고만 적혀 있다 ([ISOR](https://emsa.ca.gov/wp-content/uploads/sites/71/2026/01/ISOR-_-Implementation-of-AB-40.pdf)) |
| GitHub 저장소 이관 | 1일 | 받는 사람이 수락해야 하는 기한 | 없음 ([GitHub](https://docs.github.com/en/repositories/creating-and-managing-repositories/transferring-a-repository)) |
| ICANN 도메인 이관 | 5일 | 기존 등록 대행자가 응답해야 하는 기한 | 없음 ([ICANN Transfer Policy](https://www.icann.org/resources/pages/transfer-policy-2016-06-01-en)) |

- **사실:** 캘리포니아 AB 40 시행 규정 § 100002.04에서 APOT standard는 "not to exceed thirty (30) minutes, 90% of the time"이다. 지역 EMS 기관(LEMSA)이 이 범위 안에서 정한다. ([AB 40 APOT 규정 승인본](https://emsa.ca.gov/wp-content/uploads/sites/71/2026/03/Approved-_-AB-40-APOT-Regulations-Reg-Text-12.05.25-OAL-Edit.pdf))
- **사실:** 규정 제정 이유서에는 30분 기준이 감시와 준수에 쓰는 명확하고 측정 가능한 기준이라고 적혀 있다. 왜 30분인지는 적혀 있지 않다. ([ISOR](https://emsa.ca.gov/wp-content/uploads/sites/71/2026/01/ISOR-_-Implementation-of-AB-40.pdf))
- **추론:** PagerDuty와 캘리포니아 규정에 30분이 나온다. 그러나 두 값 모두 "되돌릴 수 있는 기간"이 아니다. PagerDuty 값은 다음 사람에게 알리기 전 대기 시간이다. 캘리포니아 값은 받는 쪽의 성과 기준이다. 수리맵의 30분을 이 값으로 뒷받침할 수는 없다.
- **추론:** 값의 단위는 업무의 시간 감각을 따르는 것으로 보인다. 실시간 응대는 초 단위다. 장애 대응과 구급차 인계는 분 단위다. 소유권 이관은 일 단위다. 이 해석을 적은 출처는 찾지 못했다.

## 5. 오프라인과 통신 단절

- **사실:** INSARAG Manual B에는 종이 양식을 웹 시스템으로 옮기는 중이지만 "paper forms will still be kept within the coordination system, in the event that the web-based system is not operational"이라고 적혀 있다. ICMS는 웹 기반 도구이면서 웹 버전이 안 될 때 쓰는 종이 도구라고 적혀 있다. ([INSARAG Manual B](https://insarag.org/wp-content/uploads/2021/09/INSARAG20Guidelines20Vol20II2C20Man20B.pdf), 5.4절과 5.9절)
- **사실:** 같은 문서에는 정보 관리 원칙으로 "Field oriented"와 "Reliable"을 든다. Reliable에는 "even under adverse conditions and limited resources"라는 조건이 붙어 있다. ([INSARAG Manual B](https://insarag.org/wp-content/uploads/2021/09/INSARAG20Guidelines20Vol20II2C20Man20B.pdf), 5.9절)
- **사실:** LEITSC CAD 명세에는 지령 수신 확인을 음성 통신이나 차량용 데이터 단말로 한다고 적혀 있다. ([LEITSC CAD 명세](https://bja.ojp.gov/sites/g/files/xyckuh186/files/media/document/leitsc_law_enforcement_cad_systems.pdf), 1.6.4)
- **사실:** FEMA IS-0200.c 발췌본에는 지휘권 이전을 "Whenever possible" 대면으로 하라고 적혀 있다. 대면이 어려울 때의 방법은 이 발췌본에 없다. ([FEMA Transfer of Command](https://training.fema.gov/emiweb/is/icsresource/assets/Transfer%20of%20Command.pdf))
- **사실:** Joint Commission SEA 58에는 전자 문서나 종이로만 인계하지 말라고 적혀 있다. 대면이 어려우면 전화나 화상으로 실시간 인계하라고 적혀 있다. ([SEA 58](https://digitalassets.jointcommission.org/api/public/content/a05e74ef89484e2084b6511189b73a99))
- **사실:** Dynamics 365의 놓친 알림 설정에서는 알림을 놓친 담당자를 inactive로 바꿔 새 과업 배정을 막는다. 담당자가 오프라인인 동안 이미 배정된 작업을 어떻게 다루는지는 문서에 없다. ([Dynamics 365 놓친 알림](https://learn.microsoft.com/en-us/dynamics365/customer-service/administer/manage-missed-notifications))
- 원문 미확인: 받는 쪽이 오프라인인 동안 확인 기간을 어떻게 세는지, 오프라인에서 한 확인이나 되돌리기가 나중에 서버에 도착했을 때 어떻게 처리하는지를 다룬 출처는 찾지 못했다. ServiceNow FSM의 수락과 거절 문서에도 오프라인 동작은 없다.
- **추론:** 안전 업무 사례에서는 앱이나 웹 시스템이 안 될 때 사람끼리 직접 주고받는 길(무선 음성, 전화, 종이 양식)을 함께 둔다. 앱 안의 확인 버튼만으로 인계를 끝내는 사례는 찾지 못했다.
- **추론:** 확인 기간을 서버 시각으로 세면, 받는 팀이 오프라인인 동안에도 기간이 흐른다. 이 경우 받는 팀이 확인 카드를 보지 못한 채 확정될 수 있다. 넘겨주는 팀이 오프라인에서 기록하면 서버에 도착할 때까지 배정이 옮겨지지 않는다. 그 사이에 업무폰 화면과 상황판의 배정이 서로 다르게 보일 수 있다.

## 확인하지 못한 것

- IAMSAR Manual Volume II의 SMC 교대 절차: IMO 유료 간행물이라 원문을 읽지 못했다. 영국 해양경비청(MCA) 문서에 SMC 교대 인계 자료를 준비한다는 내용과 IAMSAR Volume II 3.8절을 가리키는 내용이 있다는 검색 결과를 봤지만, 문서는 열리지 않았다.
- 미국 Land SAR Addendum(2011)의 팀 브리핑과 디브리핑 절, NWCG의 transfer of command 페이지: 서버가 접근을 거부해(403) 읽지 못했다.
- NFPA 1221, NFPA 1710의 출동 처리 시간 기준: 유료 표준이라 확인하지 않았다.
- Motorola, Hexagon 같은 CAD 제품의 지령 확인 타이머 기본값: 공개 1차 문서를 찾지 못했다.
- Salesforce Field Service 모바일 앱의 배정 거절 절차: 공식 문서를 찾지 못했다.
- PagerDuty에서 확인된 incident를 재배정할 때의 상태 변화: 문서에 적혀 있지 않았다.
- 국내 소방, 119 구급, 경찰 지구대의 인수인계 확인 절차 세부: 공개 규정을 찾지 못했다.
- 기업 기술 블로그: 팀 간 이관의 확인과 되돌리기를 다룬 국내외 글은 찾지 못했다.

## 수리맵 설계에 주는 시사점

아래 판단은 조사자의 추론이다. "지지"는 같은 결정을 한 사례가 있다는 뜻이다. "반대"는 다른 결정을 한 사례가 있다는 뜻이다. "근거 없음"은 비교할 사례를 찾지 못했다는 뜻이다.

| 설계 요소 | 판단 | 근거 |
|---|---|---|
| 즉시 이관 | 일부 지지, 일부 반대 | 지지: CAD에서는 배정이 먼저 일어나고 수신 확인을 따로 기록한다. Google Workspace의 같은 조직 안 이관과 Dynamics 365 레코드 라우팅도 수락 없이 배정된다. 반대: ICS에서는 브리핑과 효력 일시 공지 뒤에 지휘권이 넘어간다. 캘리포니아 구급차 환자 인계와 Joint Commission에서는 받는 쪽이 보고를 받고 돌봄을 맡은 때 책임이 넘어간다. Google Drive 개인 계정, GitHub, ICANN에서도 수락 전까지 넘겨주는 쪽이 소유자로 남는다. 지지 사례는 모두 배정 담당자가 따로 있거나 같은 조직 안의 배정이다. 현장 팀끼리 직접 넘기는 사례는 찾지 못했다. |
| 확인과 되돌리기 | 확인은 지지, 넘겨준 팀으로 되돌리기는 근거 없음 | 받는 쪽 확인 버튼은 ServiceNow, Dynamics 365, Salesforce, GitHub, Google Drive에 있다. 거절하면 대부분 넘겨준 쪽이 아니라 디스패처, 관리자, 대기열로 돌아간다(ServiceNow는 Pending Dispatch, Dynamics 365는 대기열). 수락 전까지 넘겨준 쪽에 남는 사례(Google Drive, ICANN)는 결과가 같지만, 먼저 옮기지 않는다는 점이 다르다. 먼저 옮긴 뒤 받는 쪽이 되돌리는 사례는 찾지 못했다. Gmail 보내기 취소는 보내는 쪽이 되돌리는 기능이라 다르다. |
| 확인 기간과 자동 확정 | 자동 확정은 안전 업무 사례와 반대, 30분 값은 근거 없음 | 기간이 지나면 자동 승인하는 사례는 ICANN(5일)과 Dynamics 365 자동 배정뿐이다. 온콜과 출동 지령에서는 기간이 지나면 사람에게 다시 알린다. PagerDuty acknowledgement timeout에서는 확인 전 상태로 되돌아간다. 특정 분 값의 이유를 적은 출처는 없었다. PagerDuty와 캘리포니아 규정의 30분은 의미가 달라 30분의 근거가 되지 않는다. |
| 확정 전 재이관 금지 | 근거 없음 | 재이관 제한의 1차 근거는 ICANN의 이관 후 60일 거부 사유뿐이다. 이 제한은 확정 뒤에 적용된다. CAD와 PagerDuty에서는 배정 담당자가 확인 전에도 재배정할 수 있다. |
| 오프라인 | 앱 밖의 확인 수단은 지지, 확인 기간 계산은 근거 없음 | INSARAG의 종이 양식, CAD의 음성 확인, Joint Commission의 전화나 화상 인계, ICS의 "가능하면 대면" 원칙은 앱이 안 될 때의 다른 수단을 둔다. 받는 쪽이 오프라인인 동안 확인 기간을 어떻게 세는지 다룬 사례는 찾지 못했다. 서버 시각으로 세면 받는 팀이 확인 카드를 보지 못한 채 확정될 수 있다. |

설계를 정하기 전에 사용자와 합의할 질문은 아래와 같다. 조사로 답을 정하지 않았다.

1. 받는 팀이 확인하기 전, 그 구역의 수색 책임은 어느 팀에 있는가? 조사한 안전 업무 사례는 대부분 인계 절차를 마친 때를 책임 이전 시점으로 둔다.
2. 확인 기간이 지났는데 받는 팀이 확인하지 않았다면, 지휘관이 알아야 하는가? 온콜과 출동 지령 사례에서는 시간이 지나면 사람에게 알림이 간다. 다만 수리맵의 자동 판단 금지 원칙에 맞는 표시 방식인지 함께 정해야 한다.
3. 되돌린 배정이 넘겨준 팀으로 가는 것이 맞는가? 조사한 배정 사례에서 거절된 배정은 대부분 배정 담당자에게 돌아간다. 수리맵에서 그 역할은 웹 상황판의 지휘관일 수 있다. 다만 앱 전용 write와 웹 전용 command를 섞지 않는 원칙과의 관계를 확인해야 한다.
4. 받는 팀이 오프라인일 때 확인 기간을 서버 시각으로 셀 것인가, 받는 팀이 확인 카드를 처음 본 시각부터 셀 것인가?

## 출처

### 병원과 응급 의료

- Joint Commission, Sentinel Event Alert Issue 58: Inadequate hand-off communication(2017-09): https://digitalassets.jointcommission.org/api/public/content/a05e74ef89484e2084b6511189b73a99
- Starmer 외, I-PASS, a Mnemonic to Standardize Verbal Handoffs, Pediatrics 2012;129(2):201-204(PMC): https://pmc.ncbi.nlm.nih.gov/articles/PMC9923540
- AHRQ PSNet, I-PASS 논문 요약: https://psnet.ahrq.gov/issue/i-pass-mnemonic-standardize-verbal-handoffs
- IHI, SBAR Tool: https://www.ihi.org/library/tools/sbar-tool-situation-background-assessment-recommendation
- California EMS Authority, AB 40 APOT 규정 승인본(2025-12-05 OAL 편집본): https://emsa.ca.gov/wp-content/uploads/sites/71/2026/03/Approved-_-AB-40-APOT-Regulations-Reg-Text-12.05.25-OAL-Edit.pdf
- California EMS Authority, AB 40 시행 규정 제정 이유서(ISOR): https://emsa.ca.gov/wp-content/uploads/sites/71/2026/01/ISOR-_-Implementation-of-AB-40.pdf

### 재난 지휘와 수색 구조

- FEMA, National Incident Management System 3판(2017-10): https://www.fema.gov/sites/default/files/2020-07/fema_nims_doctrine-2017.pdf
- FEMA, Transfer of Command(IS-0200.c 발췌, 2018-03): https://training.fema.gov/emiweb/is/icsresource/assets/Transfer%20of%20Command.pdf
- INSARAG, Guidelines 2020 Volume II Manual B Operations: https://insarag.org/wp-content/uploads/2021/09/INSARAG20Guidelines20Vol20II2C20Man20B.pdf

### 경찰과 출동 지령

- 국가법령정보센터, 지역경찰의 조직 및 운영에 관한 규칙(경찰청예규 제602호): https://www.law.go.kr/행정규칙/지역경찰의조직및운영에관한규칙
- LEITSC, Standard Functional Specifications for Law Enforcement Computer Aided Dispatch (CAD) Systems: https://bja.ojp.gov/sites/g/files/xyckuh186/files/media/document/leitsc_law_enforcement_cad_systems.pdf

### 현장 업무 관리와 고객 응대 라우팅

- ServiceNow, Accept or reject requests from push notifications(Brazil 릴리스): https://www.servicenow.com/docs/r/field-service-management/work-order-management/accept-reject-wot-push-notification.html
- ServiceNow, Accept or reject a work order task on a mobile device(Xanadu 릴리스): https://www.servicenow.com/docs/r/xanadu/field-service-management/self-assign.html
- Microsoft, Manage notification settings and templates: https://learn.microsoft.com/en-us/dynamics365/customer-service/administer/notification-templates
- Microsoft, Enable conversations to be assigned automatically: https://learn.microsoft.com/en-us/dynamics365/customer-service/administer/enable-auto-assign-conversations
- Microsoft, Enable missed notifications: https://learn.microsoft.com/en-us/dynamics365/customer-service/administer/manage-missed-notifications
- Salesforce, AgentWork 객체 레퍼런스(본문 403, 검색 발췌로 확인): https://developer.salesforce.com/docs/atlas.en-us.object_reference.meta/object_reference/sforce_api_objects_agentwork.htm

### 온콜 이관

- PagerDuty, Incidents: https://docs.pagerduty.com/incident-management/incidents/overview
- PagerDuty, Escalation Policies: https://docs.pagerduty.com/incident-management/schedules/escalation-policies
- PagerDuty, Reassign Incidents: https://docs.pagerduty.com/incident-management/incidents/reassign-incidents
- PagerDuty, Configurable Service Settings: https://docs.pagerduty.com/incident-management/services/configurable-service-settings
- PagerDuty, Notification Rules: https://docs.pagerduty.com/account-admin/user-profile/notification-rules
- Atlassian, How do escalations work in Opsgenie: https://support.atlassian.com/opsgenie/docs/how-do-escalations-work-in-opsgenie/

### 일반 소유권 이관

- Google Drive Help, Transfer file ownership: https://support.google.com/drive/answer/2494892?hl=en
- Gmail 고객센터, 보내기 취소 도움말: https://support.google.com/mail/answer/2819488?hl=en
- GitHub Docs, Transferring a repository: https://docs.github.com/en/repositories/creating-and-managing-repositories/transferring-a-repository
- IETF, RFC 5730 Extensible Provisioning Protocol (EPP): https://www.rfc-editor.org/rfc/rfc5730.html
- IETF, RFC 5731 EPP Domain Name Mapping: https://www.rfc-editor.org/rfc/rfc5731.html
- ICANN, Transfer Policy(2016-06-01 시행본): https://www.icann.org/resources/pages/transfer-policy-2016-06-01-en
- ICANN, Transfer Policy Review 작업반 최종 보고서 공개 의견 요약: https://itp.cdn.icann.org/en/files/public-comment/summary-report-transfer-policy-review-wg-final-report-icann-board-consideration-02-07-2025-en.pdf
- DomainIncite(업계 기사, 2차 자료): https://domainincite.com/?p=30891
