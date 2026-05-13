import { ChevronLeft, ChevronRight } from 'lucide-react';

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
};

export type SuriMapPageHeaderProps = {
  activeTab: SuriMapPageHeaderTabId;
  currentAccountLabel?: string;
  incidentContext?: SuriMapPageHeaderIncidentContext;
  markerNotificationIndex?: number;
  markerNotifications?: MarkerNotification[];
  timestampLabel?: string;
  onOpenIncidentList: () => void;
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
  avatarLabel: 'INC',
  eyebrow: 'Incident context',
  title: 'Incident dashboard',
  metrics: [
    { label: 'Missing person', value: '-' },
    { label: 'Search areas', value: '-' },
    { label: 'Assigned accounts', value: '-' },
  ],
  statusLabel: 'Active',
};

export function SuriMapPageHeader({
  activeTab,
  currentAccountLabel = '-',
  incidentContext = DEFAULT_INCIDENT_CONTEXT,
  markerNotificationIndex = 0,
  markerNotifications = [],
  onCloseMarkerNotifications,
  onOpenIncidentList,
  onMoveMarkerNotification,
  onOpenHandover,
  onOpenOfflinePackage,
  onOpenSituationBoard,
  timestampLabel = 'Live',
}: SuriMapPageHeaderProps) {
  const navItems: NavItem[] = [
    { id: 'situationBoard', label: 'Situation board', onClick: onOpenSituationBoard },
    { id: 'handover', label: 'Handover', onClick: onOpenHandover },
    { id: 'offlinePackage', label: 'Offline package', onClick: onOpenOfflinePackage },
  ];
  const activeMarkerNotification = markerNotifications[markerNotificationIndex] ?? null;
  const hasPreviousMarkerNotification = markerNotificationIndex > 0;
  const hasNextMarkerNotification = markerNotificationIndex < markerNotifications.length - 1;

  return (
    <header className={styles.header}>
      <nav className={styles.productNav} aria-label="Suri-Map navigation">
        <button type="button" className={styles.backButton} onClick={onOpenIncidentList}>
          Incident list
        </button>
        <div className={styles.navTabs} role="list" aria-label="Board navigation">
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
            Account <b>{currentAccountLabel}</b>
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
      <section className={styles.incidentContextBar} aria-label="Incident context">
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
          <div className={`${styles.headerStatus} ${styles.headerStatusInProgress}`} aria-label="Incident status">
            {incidentContext.statusLabel}
          </div>
        </div>
      </section>
      {activeMarkerNotification ? (
        <section className={styles.markerPopup} role="alertdialog" aria-label="Marker notification" aria-live="assertive">
          {hasPreviousMarkerNotification ? (
            <button
              type="button"
              className={`${styles.markerPageButton} ${styles.markerPageButtonPrevious}`}
              aria-label="Previous marker notification"
              onClick={() => onMoveMarkerNotification?.(markerNotificationIndex - 1)}
            >
              <ChevronLeft size={30} strokeWidth={2.2} aria-hidden="true" />
            </button>
          ) : null}
          {hasNextMarkerNotification ? (
            <button
              type="button"
              className={`${styles.markerPageButton} ${styles.markerPageButtonNext}`}
              aria-label="Next marker notification"
              onClick={() => onMoveMarkerNotification?.(markerNotificationIndex + 1)}
            >
              <ChevronRight size={30} strokeWidth={2.2} aria-hidden="true" />
            </button>
          ) : null}
          {!hasNextMarkerNotification ? (
            <button
              type="button"
              className={styles.markerCloseButton}
              aria-label="Close marker notification"
              onClick={onCloseMarkerNotifications}
            >
              Close
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
          <div className={styles.markerPopupPager} aria-label="Marker notification page">
            {markerNotificationIndex + 1} / {markerNotifications.length}
          </div>
        </section>
      ) : null}
    </header>
  );
}
