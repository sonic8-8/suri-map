import styles from './SuriMapPageHeader.module.css';

export type SuriMapPageHeaderTabId = 'situationBoard' | 'areaEdit' | 'handover' | 'offlinePackage';

export type MarkerNotification = {
  id: string;
  title: string;
  markerType: string;
  reporter: string;
  areaLabel: string;
  receivedAtLabel: string;
  coordinateLabel: string;
};

export type SuriMapPageHeaderProps = {
  activeTab: SuriMapPageHeaderTabId;
  markerNotificationIndex?: number;
  markerNotifications?: MarkerNotification[];
  onOpenIncidentList: () => void;
  onOpenSituationBoard?: () => void;
  onOpenAreaEdit?: () => void;
  onCloseMarkerNotifications?: () => void;
  onMoveMarkerNotification?: (nextIndex: number) => void;
};

type NavItem = {
  id: SuriMapPageHeaderTabId;
  label: string;
  onClick?: () => void;
};

const INCIDENT_CONTEXT_METRICS = [
  { label: '실종자', value: '김OO · 73세 · 남' },
  { label: '마지막 목격', value: '오늘 08:40, 반포동 진입로 인근' },
  { label: '현장 지휘관', value: '실종팀 지휘 계정 · 서초경찰서 지휘 계정' },
];

export function SuriMapPageHeader({
  activeTab,
  markerNotificationIndex = 0,
  markerNotifications = [],
  onCloseMarkerNotifications,
  onOpenAreaEdit,
  onOpenIncidentList,
  onMoveMarkerNotification,
  onOpenSituationBoard,
}: SuriMapPageHeaderProps) {
  const navItems: NavItem[] = [
    { id: 'situationBoard', label: '상황판', onClick: onOpenSituationBoard },
    { id: 'areaEdit', label: '구역 분할', onClick: onOpenAreaEdit },
    { id: 'handover', label: '인수인계' },
    { id: 'offlinePackage', label: '오프라인 패키지' },
  ];
  const activeMarkerNotification = markerNotifications[markerNotificationIndex] ?? null;
  const hasPreviousMarkerNotification = markerNotificationIndex > 0;
  const hasNextMarkerNotification = markerNotificationIndex < markerNotifications.length - 1;

  return (
    <header className={styles.header}>
      <nav className={styles.productNav} aria-label="Suri-Map 화면 이동">
        <button type="button" className={styles.backButton} onClick={onOpenIncidentList}>
          사건 목록
        </button>
        <div className={styles.navTabs} role="list" aria-label="상황판 화면 이동">
          {navItems.map(({ id, label, onClick }) => {
            const isActive = id === activeTab;

            return (
              <button
                key={id}
                type="button"
                className={`${styles.navButton}${isActive ? ` ${styles.navButtonActive}` : ''}`}
                aria-current={isActive ? 'page' : undefined}
                disabled={isActive || onClick === undefined}
                onClick={onClick}
              >
                {label}
              </button>
            );
          })}
        </div>
        <div className={styles.meta}>
          <span>
            현재 계정 <b>실종팀 지휘 계정</b>
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
      <section className={styles.incidentContextBar} aria-label="사건 상황 요약">
        <div className={styles.incidentContextMain}>
          <div className={styles.incidentAvatar} aria-hidden="true">
            김OO
          </div>
          <div className={styles.incidentContextTitle}>
            <span>INC-2026-0428-031 · mock</span>
            <strong>서초구 반포동 실종 사건</strong>
          </div>
        </div>
        <span className={styles.incidentContextDivider} aria-hidden="true" />
        <div className={styles.incidentContextMetrics}>
          {INCIDENT_CONTEXT_METRICS.map(({ label, value }) => (
            <div key={label}>
              <span>{label}</span>
              <strong>{value}</strong>
            </div>
          ))}
        </div>
        <div className={styles.incidentContextActions}>
          <div className={`${styles.headerStatus} ${styles.headerStatusInProgress}`} aria-label="현재 운영 상태">
            진행 중 · OP 2차
          </div>
        </div>
      </section>
      {activeMarkerNotification ? (
        <section className={styles.markerPopup} role="alertdialog" aria-label="신규 마커 알림" aria-live="assertive">
          {hasPreviousMarkerNotification ? (
            <button
              type="button"
              className={`${styles.markerPageButton} ${styles.markerPageButtonPrevious}`}
              aria-label="이전 마커 알림"
              onClick={() => onMoveMarkerNotification?.(markerNotificationIndex - 1)}
            >
              &lt;
            </button>
          ) : null}
          {hasNextMarkerNotification ? (
            <button
              type="button"
              className={`${styles.markerPageButton} ${styles.markerPageButtonNext}`}
              aria-label="다음 마커 알림"
              onClick={() => onMoveMarkerNotification?.(markerNotificationIndex + 1)}
            >
              &gt;
            </button>
          ) : null}
          {!hasNextMarkerNotification ? (
            <button type="button" className={styles.markerCloseButton} aria-label="마커 알림 닫기" onClick={onCloseMarkerNotifications}>
              X
            </button>
          ) : null}
          <div className={styles.markerPopupIcon} aria-hidden="true">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
              <circle cx="11" cy="11" r="6" />
              <line x1="15.5" y1="15.5" x2="20" y2="20" />
            </svg>
          </div>
          <div className={styles.markerPopupTitle}>{activeMarkerNotification.title}</div>
          <div className={styles.markerPopupMeta}>
            <b>{activeMarkerNotification.markerType}</b> · {activeMarkerNotification.reporter}
            <br />
            {activeMarkerNotification.areaLabel} · {activeMarkerNotification.receivedAtLabel}
            <br />
            {activeMarkerNotification.coordinateLabel}
          </div>
          <div className={styles.markerPopupPager} aria-label="마커 알림 페이지">
            {markerNotificationIndex + 1} / {markerNotifications.length}
          </div>
        </section>
      ) : null}
    </header>
  );
}
