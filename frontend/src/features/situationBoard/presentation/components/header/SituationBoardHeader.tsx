import styles from './SituationBoardHeader.module.css';

type SituationBoardHeaderProps = {
  onOpenIncidentList: () => void;
};

const productNavigationLabels = [
  '상황판',
  'OP 비교',
  '구역 분할',
  '인수인계',
  '오프라인 패키지',
];

// 헤더는 상단 화면 이동, 사건 요약, 현재 운용 상태를 한 줄기로 묶는다.
export function SituationBoardHeader({ onOpenIncidentList }: SituationBoardHeaderProps) {
  return (
    <header className={styles.header}>
      {/* 상단 화면 이동과 브랜드/메타 정보를 담는 첫 번째 줄. */}
      <nav className={styles.productNav} aria-label="Suri-Map 주요 화면">
        {/* 사건 목록으로 돌아가는 보조 네비게이션. */}
        <button type="button" className={styles.backButton} onClick={onOpenIncidentList}>
          ← 사건 목록
        </button>
        {/* 현재 화면과 관련 화면을 가로 탭처럼 보여준다. */}
        <div className={styles.navTabs} role="list" aria-label="상단 화면 이동">
          {productNavigationLabels.map((label) => (
            <button
              key={label}
              type="button"
              className={`${styles.navButton}${label === '상황판' ? ` ${styles.navButtonActive}` : ''}`}
              aria-current={label === '상황판' ? 'page' : undefined}
            >
              {label}
            </button>
          ))}
        </div>
        {/* 사용자, 시각, 브랜드를 우측 메타 영역에 배치한다. */}
        <div className={styles.meta}>
          <span>
            현재 사용자: <b>실종팀 1팀장 박OO</b>
          </span>
          <span className={styles.metaDivider} aria-hidden="true" />
          <span>2026-05-04 16:42 KST · mock</span>
          <span className={styles.metaDivider} aria-hidden="true" />
          <div className={styles.brand}>
            <svg width="22" height="22" viewBox="0 0 22 22" fill="none" aria-hidden="true">
              <path
                className={styles.brandMark}
                d="M11 1.5 L19.5 5 V11 C19.5 15.5 16 19.3 11 20.5 C6 19.3 2.5 15.5 2.5 11 V5 Z"
              />
              <path
                d="M11 6.5 a4.5 4.5 0 1 0 0 9 a4.5 4.5 0 1 0 0 -9 z M11 9 v3.5 M11 14 v.1"
                stroke="currentColor"
                strokeWidth="1.6"
                strokeLinecap="round"
                fill="none"
              />
            </svg>
            <div>Suri-Map</div>
          </div>
        </div>
      </nav>
      {/* 사건 코드, 실종자, 마지막 목격, 현장 지휘관을 요약하는 두 번째 줄. */}
      <section className={styles.incidentContextBar} aria-label="사건 상황 요약">
        <div className={styles.incidentContextMain}>
          {/* 요약의 시각적 기준점이 되는 아바타 칩. */}
          <div className={styles.incidentAvatar} aria-hidden="true">
            김OO
          </div>
          {/* 사건 코드와 사건명을 묶어 보여준다. */}
          <div className={styles.incidentContextTitle}>
            <span>INC-2026-0428-031 · 실종프로파일링 (mock)</span>
            <strong>시흥시 매화동 외곽 실종 사건</strong>
          </div>
        </div>
        {/* 제목 블록과 메트릭 블록을 구분하는 세로 구분선. */}
        <span className={styles.incidentContextDivider} aria-hidden="true" />
        {/* 실종자, 마지막 목격, 지휘관 정보를 가로 메트릭으로 배치한다. */}
        <div className={styles.incidentContextMetrics}>
          <div>
            <span>실종자</span>
            <strong>김OO · 73세 · 남</strong>
          </div>
          <div>
            <span>마지막 목격</span>
            <strong>오늘 08:40, 매화동 마을회관 앞 (가족 신고)</strong>
          </div>
          <div>
            <span>현장 지휘관</span>
            <strong>실종팀 1팀장 박OO · 시흥경찰서 매화지구대 당직 이OO</strong>
          </div>
        </div>
        {/* 현재 운용 상태를 강조 배지로 보여준다. */}
        <div className={styles.incidentContextActions}>
          <div className={`${styles.headerStatus} ${styles.headerStatusInProgress}`} aria-label="현재 운용 상태">
            진행 중 · OP 2차
          </div>
        </div>
      </section>
    </header>
  );
}
