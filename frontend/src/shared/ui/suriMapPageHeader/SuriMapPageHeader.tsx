import { useEffect, useRef, useState, type ReactNode } from 'react';

import { ChevronLeft, ChevronRight } from 'lucide-react';

import { SuriMapLogo } from '../suriMapLogo';

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

export type SuriMapPageHeaderSyncStatus = {
  label: string;
  tone: 'syncing' | 'stale' | 'error';
};

export type SuriMapPageHeaderProps = {
  activeTab: SuriMapPageHeaderTabId;
  currentAccountLabel?: string;
  incidentContext?: SuriMapPageHeaderIncidentContext;
  markerNotificationIndex?: number;
  markerNotifications?: MarkerNotification[];
  syncStatus?: SuriMapPageHeaderSyncStatus | null;
  timestampLabel?: string;
  showIncidentContextBar?: boolean;
  onOpenIncidentList: () => void;
  onOpenIncidentDetail?: () => void;
  onOpenSituationBoard?: () => void;
  onOpenHandover?: () => void;
  onOpenOfflinePackage?: () => void;
  onOpenLogin?: () => void;
  onCloseMarkerNotifications?: () => void;
  onMoveMarkerNotification?: (nextIndex: number) => void;
};

type NavItem = {
  id: SuriMapPageHeaderTabId;
  label: string;
  onClick?: () => void;
};

type TruncatedTooltipTextProps = {
  value: string;
  children?: ReactNode;
  anchorClassName?: string;
  textClassName?: string;
};

function TruncatedTooltipText({ value, children = value, anchorClassName, textClassName }: TruncatedTooltipTextProps) {
  const textRef = useRef<HTMLSpanElement | null>(null);
  const [isOverflowing, setIsOverflowing] = useState(false);

  useEffect(() => {
    const updateOverflowState = () => {
      const element = textRef.current;
      if (!element) return;

      const nextIsOverflowing = element.scrollWidth > element.clientWidth + 1;
      setIsOverflowing((current) => (current === nextIsOverflowing ? current : nextIsOverflowing));
    };

    updateOverflowState();
    window.addEventListener('resize', updateOverflowState);

    return () => window.removeEventListener('resize', updateOverflowState);
  }, [value]);

  return (
    <span
      className={`${styles.tooltipAnchor}${anchorClassName ? ` ${anchorClassName}` : ''}`}
      data-tooltip={isOverflowing ? value : undefined}
    >
      <span ref={textRef} className={`${styles.tooltipText}${textClassName ? ` ${textClassName}` : ''}`}>
        {children}
      </span>
    </span>
  );
}

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
  syncStatus = null,
  showIncidentContextBar = true,
  onCloseMarkerNotifications,
  onOpenIncidentDetail,
  onOpenIncidentList,
  onMoveMarkerNotification,
  onOpenHandover,
  onOpenOfflinePackage,
  onOpenLogin,
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
        <button type="button" className={styles.brand} onClick={onOpenIncidentList}>
          <SuriMapLogo className={styles.brandMark} size={26} />
          <div>Suri-Map</div>
        </button>
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
          <TruncatedTooltipText anchorClassName={styles.metaAccount} textClassName={styles.metaAccountText} value={currentAccountLabel}>
            <b>{currentAccountLabel}</b>
          </TruncatedTooltipText>
          <span className={styles.metaDivider} aria-hidden="true" />
          <span>{timestampLabel}</span>
          {onOpenLogin ? (
            <>
              <span className={styles.metaDivider} aria-hidden="true" />
              <button type="button" className={styles.logoutButton} onClick={onOpenLogin}>
                로그아웃
              </button>
            </>
          ) : null}
        </div>
      </nav>
      {showIncidentContextBar ? (
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
            <TruncatedTooltipText anchorClassName={styles.incidentContextTitleValueAnchor} textClassName={styles.incidentContextTitleValue} value={incidentContext.title} />
          </div>
        </div>
        <span className={styles.incidentContextDivider} aria-hidden="true" />
        <div className={styles.incidentContextMetrics}>
          {incidentContext.metrics.map(({ label, value }) => (
            <div key={label}>
              <span>{label}</span>
              <TruncatedTooltipText anchorClassName={styles.incidentContextMetricValueAnchor} textClassName={styles.incidentContextMetricValue} value={value} />
            </div>
          ))}
        </div>
        <div className={styles.incidentContextActions}>
          {syncStatus ? (
            <div className={`${styles.syncStatus} ${syncStatusClassName(syncStatus.tone)}`}>{syncStatus.label}</div>
          ) : null}
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
      ) : null}
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

function syncStatusClassName(tone: SuriMapPageHeaderSyncStatus['tone']) {
  if (tone === 'syncing') return styles.syncStatusSyncing;
  if (tone === 'stale') return styles.syncStatusStale;
  return styles.syncStatusError;
}
