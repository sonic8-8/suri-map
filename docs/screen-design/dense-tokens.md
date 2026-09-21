# 화면 토큰을 선택한 배경

2026-05 시안의 색·크기·간격 선택 이유를 남긴다. 토큰 전체를 Markdown과 코드에 이중 관리하지 않는다. 실제 적용값은 아래 CSS·Compose 정의와 사용처에서 확인한다.

## 현재 값과 원본 시안

| 확인할 것 | 위치 |
|---|---|
| Web 공통 색 | [tokens.css](../../frontend/src/shared/tokens.css). 현재 공통 접두어는 `--color-*`이며 feature CSS도 함께 확인 |
| Android 색·글자·간격·모양 | [Color.kt](../../android/app/src/main/java/com/surimap/ui/theme/Color.kt), [Type.kt](../../android/app/src/main/java/com/surimap/ui/theme/Type.kt), [Dimens.kt](../../android/app/src/main/java/com/surimap/ui/theme/Dimens.kt), [Shape.kt](../../android/app/src/main/java/com/surimap/ui/theme/Shape.kt) |
| 업무폰 HTML 시안 | [lo-polifon-wireframes.css](artifacts/lo/lo-polifon-wireframes.css)와 각 HTML의 스타일. `--poli-*`는 당시 시안 이름 |
| 가져온 디자인 시스템 | [출처 설명](<Korean Government UIUX Design System/README.md>), [CSS](<Korean Government UIUX Design System/colors_and_type.css>) |
| 과거 전체 토큰표 | [정리 전 원문](https://github.com/sonic8-8/suri-map/blob/3cd777752e2178ebb3470e6f749b1521ce404e7a/docs/screen-design/dense-tokens.md). `--krds-ops-*` 명세 전체가 제품에 적용됐다는 뜻은 아님 |

외부 디자인 자료는 Figma 시안 재구성이라고 자체 설명한다. 원래 스타일 가이드를 모두 대조한 공식 배포물로 취급하지 않는다. 폰트·이미지·아이콘의 출처와 재배포 조건도 별도 확인 대상이다.

## 유지할 설계 이유

| 선택 의도 | 이유·주의 |
|---|---|
| Web은 밝은 화면과 조밀한 정보, 앱은 어두운 배경과 큰 핵심 행동 | 지휘자의 비교·조회와 현장 한 손 입력이 다름. 다크 모드가 야외 가독성을 보장한다고 단정하지 않음 |
| 경찰 블루는 핵심 행동, 초록은 동기화·기록 상태 | 행동과 상태의 의미를 분리 |
| 버튼 배경색과 글자·아이콘 색을 분리 | `PoliPrimary`를 어두운 배경 위 글자로 그대로 쓰지 않도록 `PoliPrimaryFg/Mid`를 둔 배경 |
| 차량·도보·미분류, 마커 유형, 구역 상태를 다른 형태·라벨로 보조 | 색만으로 구분하지 않음. 임의의 위험도 색을 만들지 않음 |
| 최신 위치와 오래된 위치를 경과 시각·외곽선으로 구분 | 통신 최신성을 안전 판정으로 혼동하지 않음 |
| 전체 구역 outline·하위 구역·경로·강조 마커·자기 업무폰의 시각 우선순위 구분 | 같은 좌표의 중첩과 배경 지도에 정보가 묻히는 문제 예방 |
| 지도 위 글자에 배경·외곽선, 시트·알림의 겹침 확인 | 타일 스타일과 알림 개수가 달라져도 핵심 정보를 읽을 수 있어야 함 |
| 터치 영역·간격·하단 안전 영역 확보 | 장갑·한 손·OS 탐색 영역을 고려. CSS px와 Compose dp를 동일 측정으로 취급하지 않음 |
| 모션 축소 환경에서는 점멸·이동 효과 줄임 | 정보 의미는 유지하면서 움직임에만 의존하지 않음 |

## 값의 적용 범위

- 280/360px 패널, 업무폰 12~15대·마커 40개, 차수 5개, 미전송 5/20건은 [현장 가정](field-context.md)의 시연 수치다. 서비스 한계·현장 허용 지연이 아니다.
- 최신성 60초·위치 끊김 5분은 과거 설계표 값이다. 실제 시각 기준·상태 계산은 [업무폰 최신성](../authentication.md#통신-최신성과-알림-토큰)과 대조한다.
- 글자 대비·크기·글꼴·터치 크기의 숫자는 실제 화면 조합과 기기에서 확인한다. 옛 문서의 대비 계산이나 라이선스 설명을 이번에 재검증한 것으로 보고하지 않는다.
- 본문 크기 17px와 19~21px 검토안, 주간 자동 전환·구역 점선 패턴·지도 blur 대체 표현은 과거 미결 항목이었다. 현장 피드백 없이 새 확정값을 만들지 않는다.

검증 범위·과거 목표는 [화면 검증 항목](measurement-gates.md), Android 적용 위치는 [Compose 대응](compose-mapping.md)에 있다.
