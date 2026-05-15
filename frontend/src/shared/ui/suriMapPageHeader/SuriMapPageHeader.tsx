import { ChevronLeft, ChevronRight } from 'lucide-react';

import styles from './SuriMapPageHeader.module.css';

export type SuriMapPageHeaderTabId =
  | 'incidentDetail'
  | 'situationBoard'
  | 'areaEdit'
  | 'handover'
  | 'offlinePackage';

export type MarkerNotification = {
  id: string;
  title: string;
  markerType: string;
  reporter: string;
  areaLabel: string;
  receivedAtLabel: string;
  coordinateLabel: string;
};

export type SuriMapPageHeaderMetric = {
  label: string;
  value: string;
};

export type SuriMapPageHeaderIncidentContext = {
  avatarLabel: string;
  eyebrow: string;
  title: string;
  metrics: SuriMapPageHeaderMetric[];
  statusLabel: string;
  statusTone?: 'active' | 'terminal';
};

export type SuriMapPageHeaderProps = {
  activeTab: SuriMapPageHeaderTabId;
  currentAccountLabel?: string;
  incidentContext?: SuriMapPageHeaderIncidentContext;
  markerNotificationIndex?: number;
  markerNotifications?: MarkerNotification[];
  timestampLabel?: string;
  onOpenIncidentList: () => void;
  onOpenIncidentDetail?: () => void;
  onOpenSituationBoard?: () => void;
  onOpenHandover?: () => void;
  onOpenOfflinePackage?: () => void;
  onCloseMarkerNotifications?: () => void;
  onMoveMarkerNotification?: (nextIndex: number) => void;
};

type NavItem = {
  id: SuriMapPageHeaderTabId;
  label: string;
  onClick?: () => void;
};

const DEFAULT_INCIDENT_CONTEXT: SuriMapPageHeaderIncidentContext = {
  avatarLabel: '사건',
  eyebrow: '사건 컨텍스트',
  title: '사건 상황판',
  metrics: [
    { label: '실종자', value: '-' },
    { label: '수색 구역', value: '-' },
    { label: '배정 계정', value: '-' },
  ],
  statusLabel: '진행 중',
  statusTone: 'active',
};

export function SuriMapPageHeader({
  activeTab,
  currentAccountLabel = '-',
  incidentContext = DEFAULT_INCIDENT_CONTEXT,
  markerNotificationIndex = 0,
  markerNotifications = [],
  onCloseMarkerNotifications,
  onOpenIncidentDetail,
  onOpenIncidentList,
  onMoveMarkerNotification,
  onOpenHandover,
  onOpenOfflinePackage,
  onOpenSituationBoard,
  timestampLabel = '실시간',
}: SuriMapPageHeaderProps) {
  const navItems: NavItem[] = [
    { id: 'incidentDetail', label: '사건 상세', onClick: onOpenIncidentDetail },
    { id: 'situationBoard', label: '상황판', onClick: onOpenSituationBoard },
    { id: 'handover', label: '인수인계', onClick: onOpenHandover },
    { id: 'offlinePackage', label: '오프라인 패키지', onClick: onOpenOfflinePackage },
  ];
  const activeMarkerNotification = markerNotifications[markerNotificationIndex] ?? null;
  const hasPreviousMarkerNotification = markerNotificationIndex > 0;
  const hasNextMarkerNotification = markerNotificationIndex < markerNotifications.length - 1;

  return (
    <header className={styles.header}>
      <nav className={styles.productNav} aria-label="Suri-Map 내비게이션">
        <button type="button" className={styles.backButton} onClick={onOpenIncidentList}>
          사건 목록
        </button>
        <div className={styles.navTabs} role="list" aria-label="상황판 내비게이션">
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
            계정 <b>{currentAccountLabel}</b>
          </span>
          <span className={styles.metaDivider} aria-hidden="true" />
          <span>{timestampLabel}</span>
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
      <section
        className={`${styles.incidentContextBar} ${
          incidentContext.statusTone === 'terminal' ? styles.incidentContextBarTerminal : ''
        }`}
        aria-label="사건 컨텍스트"
      >
        <div className={styles.incidentContextMain}>
          <div className={styles.incidentAvatar} aria-hidden="true">
            {incidentContext.avatarLabel}
          </div>
          <div className={styles.incidentContextTitle}>
            <span>{incidentContext.eyebrow}</span>
            <strong>{incidentContext.title}</strong>
          </div>
        </div>
        <span className={styles.incidentContextDivider} aria-hidden="true" />
        <div className={styles.incidentContextMetrics}>
          {incidentContext.metrics.map(({ label, value }) => (
            <div key={label}>
              <span>{label}</span>
              <strong>{value}</strong>
            </div>
          ))}
        </div>
        <div className={styles.incidentContextActions}>
          <div
            className={`${styles.headerStatus} ${
              incidentContext.statusTone === 'terminal'
                ? styles.headerStatusTerminal
                : styles.headerStatusInProgress
            }`}
            aria-label="사건 상태"
          >
            {incidentContext.statusLabel}
          </div>
        </div>
      </section>
      {activeMarkerNotification ? (
        <section className={styles.markerPopup} role="alertdialog" aria-label="마커 알림" aria-live="assertive">
          {hasPreviousMarkerNotification ? (
            <button
              type="button"
              className={`${styles.markerPageButton} ${styles.markerPageButtonPrevious}`}
              aria-label="이전 마커 알림"
              onClick={() => onMoveMarkerNotification?.(markerNotificationIndex - 1)}
            >
              <ChevronLeft size={30} strokeWidth={2.2} aria-hidden="true" />
            </button>
          ) : null}
          {hasNextMarkerNotification ? (
            <button
              type="button"
              className={`${styles.markerPageButton} ${styles.markerPageButtonNext}`}
              aria-label="다음 마커 알림"
              onClick={() => onMoveMarkerNotification?.(markerNotificationIndex + 1)}
            >
              <ChevronRight size={30} strokeWidth={2.2} aria-hidden="true" />
            </button>
          ) : null}
          {!hasNextMarkerNotification ? (
            <button
              type="button"
              className={styles.markerCloseButton}
              aria-label="마커 알림 닫기"
              onClick={onCloseMarkerNotifications}
            >
              닫기
            </button>
          ) : null}
          <div className={styles.markerPopupIcon} aria-hidden="true">
            <svg
              width="22"
              height="22"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.5"
              strokeLinecap="round"
            >
              <circle cx="11" cy="11" r="6" />
              <line x1="15.5" y1="15.5" x2="20" y2="20" />
            </svg>
          </div>
          <div className={styles.markerPopupTitle}>{activeMarkerNotification.title}</div>
          <div className={styles.markerPopupMeta}>
            <b className={styles.markerPopupType}>{activeMarkerNotification.markerType}</b>
            {activeMarkerNotification.reporter}
            <br />
            {activeMarkerNotification.areaLabel} / {activeMarkerNotification.receivedAtLabel}
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
