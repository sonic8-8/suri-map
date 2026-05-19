import { useEffect, useMemo, useRef, useState } from 'react';
import {
  CheckCircle2,
  Grid3X3,
  HardDrive,
  Map,
  RefreshCcw,
  Trash2,
} from 'lucide-react';

import { useIncidentBoardQuery } from '../../../board/api/incidentBoardApi';
import { useOfflinePackageManifestQuery } from '../../api/offlinePackageApi';
import {
  createDeviceIconClassName,
  DeviceTypeIcon,
  ManifestGroupIcon,
  manifestGroupIconClassName,
  SectionTitle,
} from '../components/OfflinePackageStatusAtoms';
import {
  PackageLoadGauge as OfflinePackageLoadGauge,
  PackageStatusSkeleton as OfflinePackageStatusSkeleton,
} from '../components/OfflinePackageStatusWidgets';
import type {
  OfflinePackageManifestResponse,
} from '../../api/offlinePackageApi';
import {
  createPackageLoadGauge,
  createSummary,
  createTileSummary,
  formatBytes,
  formatKstDateTime,
  type ManifestGroup,
  type TileSummary,
} from '../model/offlinePackageStatusView';
import {
  createAssigneeMeta,
  createAssigneeTitle,
  createDeviceMeta,
  createDeviceTitle,
  createIncidentContext,
  createManifestFreshness,
  createManifestGroups,
  formatIncidentStatus,
  formatManifestItemTotal,
  formatManifestVersion,
  formatPackageAction,
  formatPackageCheckMessage,
  getOfflinePackageManifestErrorMessage,
  getStatusView,
  readOfflinePackageTileItems,
  readPackageBadgeRows,
} from '../model/offlinePackageStatusPageViewModel';
import { getIncidentDetail, type IncidentDetailDto } from '../../../situationBoard/data/getIncidentDetail';
import type { SituationBoardResponseDto } from '../../../situationBoard/data/getSituationBoard';
import {
  isIncidentTerminalClosed,
  toIncidentTerminal,
  type IncidentTerminalViewModel,
} from '../../../situationBoard/presentation/utils/incidentTerminalBoardMapper';
import type { LoginAccount } from '../../../login/presentation/types/login';
import { mergeWithPreviousCriticalSlots } from '../../../situationBoard/presentation/hooks/useSituationBoardData';
import {
  ActionButton,
  createSharedIncidentContext,
  StatusBadge,
  type MarkerNotification,
  type StatusBadgeTone,
  SuriMapPageHeader,
} from '../../../../shared';
import { useBrowserBackToIncidentList } from '../../../../shared/hooks/useBrowserBackToIncidentList';
import styles from './OfflinePackageStatusPage.module.css';

type OfflinePackageStatusPageProps = {
  currentUserAccount: LoginAccount;
  incidentId: string;
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  onBackToSituationBoard: () => void;
  onBrowserBackToIncidentList?: () => void;
  onCloseMarkerNotifications: () => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  onOpenHandover: () => void;
  onOpenIncidentDetail?: () => void;
  onOpenIncidentList: () => void;
  onOpenSearchHistory?: () => void;
  onOpenOfflinePackage: () => void;
  onOpenLogin?: () => void;
};

const packageStatusBadgeToneClassNames: Record<StatusBadgeTone, string> = {
  active: styles.packageStatusBadgeActive,
  waiting: styles.packageStatusBadgeWaiting,
  danger: styles.packageStatusBadgeDanger,
  closed: styles.packageStatusBadgeClosed,
  neutral: styles.packageStatusBadgeNeutral,
};

// TODO: package_badge 목록은 현재 board 슬롯 전체를 받아 프론트에서 페이징한다.
// 단말별 적재 상태를 서버에서 페이지 단위로 내려주는 API가 필요하면 새 계약을 추가해야 한다.
const DEVICE_STATUS_PAGE_SIZE = 5;

function getPackageStatusBadgeClassName(tone: StatusBadgeTone) {
  return `${styles.packageStatusBadge} ${packageStatusBadgeToneClassNames[tone]}`;
}

export function OfflinePackageStatusPage({
  currentUserAccount,
  incidentId,
  markerNotificationIndex,
  markerNotifications,
  onBackToSituationBoard,
  onBrowserBackToIncidentList,
  onCloseMarkerNotifications,
  onMoveMarkerNotification,
  onOpenHandover,
  onOpenIncidentDetail,
  onOpenIncidentList,
  onOpenSearchHistory,
  onOpenOfflinePackage,
  onOpenLogin,
}: OfflinePackageStatusPageProps) {
  const [incidentDetail, setIncidentDetail] = useState<IncidentDetailDto | null>(null);
  const [isOffline, setIsOffline] = useState(() => (typeof navigator === 'undefined' ? false : !navigator.onLine));
  const [deviceStatusPage, setDeviceStatusPage] = useState(1);
  const stableBoardRef = useRef<SituationBoardResponseDto | null>(null);

  useBrowserBackToIncidentList(onBrowserBackToIncidentList);

  const boardQuery = useIncidentBoardQuery({ incidentId, includeSlots: ['package_badge', 'incident_terminal'] });
  const manifestQuery = useOfflinePackageManifestQuery({ incidentId });
  const currentBoard = useMemo<SituationBoardResponseDto | null>(() => {
    const data = (boardQuery.data ?? null) as SituationBoardResponseDto | null;
    return data && data.incidentId === incidentId ? data : null;
  }, [boardQuery.data, incidentId]);
  const board = useMemo<SituationBoardResponseDto | null>(() => {
    const mergedBoard = mergeWithPreviousCriticalSlots(
      currentBoard,
      stableBoardRef.current,
    );

    if (mergedBoard) {
      stableBoardRef.current = mergedBoard;
    }

    return mergedBoard;
  }, [currentBoard]);

  useEffect(() => {
    stableBoardRef.current = null;
    setIncidentDetail(null);
  }, [incidentId]);

  const rows = useMemo(() => readPackageBadgeRows(board?.slots.package_badge), [board]);
  const incidentTerminal = useMemo(
    () => (board ? toIncidentTerminal(board as unknown as SituationBoardResponseDto) : null),
    [board],
  );
  const currentManifest = useMemo<OfflinePackageManifestResponse | null>(() => {
    const data = manifestQuery.data ?? null;
    return data && data.incidentId === incidentId ? data : null;
  }, [incidentId, manifestQuery.data]);
  const currentTileItems = useMemo(() => readOfflinePackageTileItems(currentManifest?.tileItems), [currentManifest]);
  const manifestGroups = useMemo(() => createManifestGroups(currentManifest ?? undefined), [currentManifest]);
  const tileSummary = useMemo(() => createTileSummary(currentTileItems), [currentTileItems]);
  const serverTs = board?.serverTs ?? null;
  const referenceNow = useMemo(() => (serverTs ? new Date(serverTs) : new Date()), [serverTs]);
  const isLoading = boardQuery.isLoading;
  const boardErrorMessage = boardQuery.isError ? '단말별 적재 상태를 불러오지 못했습니다.' : '';

  useEffect(() => {
    let cancelled = false;
    void getIncidentDetail(incidentId)
      .then((detail) => {
        if (!cancelled) setIncidentDetail(detail);
      })
      .catch(() => {
        if (!cancelled) setIncidentDetail(null);
      });
    return () => {
      cancelled = true;
    };
  }, [incidentId]);

  useEffect(() => {
    const handleOnline = () => setIsOffline(false);
    const handleOffline = () => setIsOffline(true);

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  const summary = useMemo(() => createSummary(rows), [rows]);
  const packageLoadGauge = useMemo(() => createPackageLoadGauge(rows), [rows]);
  const deviceStatusTotalPages = Math.max(1, Math.ceil(rows.length / DEVICE_STATUS_PAGE_SIZE));
  const currentDeviceStatusPage = Math.min(deviceStatusPage, deviceStatusTotalPages);
  const deviceStatusPageStart = (currentDeviceStatusPage - 1) * DEVICE_STATUS_PAGE_SIZE;
  const visibleRows = rows.slice(deviceStatusPageStart, deviceStatusPageStart + DEVICE_STATUS_PAGE_SIZE);
  const hasDeviceStatusPagination = rows.length > DEVICE_STATUS_PAGE_SIZE;
  const currentAccountLabel = currentUserAccount.name;
  const activeOperationalPeriod = manifestQuery.data?.operationalPeriods.find((period) => period.status === 'ACTIVE') ?? null;
  const incidentContext = createSharedIncidentContext({
    ...(incidentDetail ?? {}),
    activeOperationalPeriodLabel: activeOperationalPeriod ? `OP ${activeOperationalPeriod.sequenceNumber}차` : null,
  });
  const isClosedTerminalBoard = isIncidentTerminalClosed(incidentTerminal);
  const timestampLabel = serverTs ? formatKstDateTime(new Date(serverTs)) : '동기화 전';
  const isEmpty = !isLoading && !boardErrorMessage && rows.length === 0;
  const isPackageSummaryPlaceholder = isLoading || (Boolean(boardErrorMessage) && rows.length === 0);
  const readyCountLabel = isPackageSummaryPlaceholder ? '-대' : `${summary.readyCount}대`;
  const warningCountLabel = isPackageSummaryPlaceholder ? '-대' : `${summary.warningCount}대`;
  const purgedCountLabel = isPackageSummaryPlaceholder ? '-대' : `${summary.purgedCount}대`;

  useEffect(() => {
    setDeviceStatusPage(1);
  }, [incidentId, rows.length]);

  return (
    <main className={styles.page}>
      <div className={styles.headerTheme}>
        <SuriMapPageHeader
          activeTab="offlinePackage"
          currentAccountLabel={currentAccountLabel}
          incidentContext={incidentContext}
          markerNotificationIndex={markerNotificationIndex}
          markerNotifications={markerNotifications}
          timestampLabel={timestampLabel}
          onCloseMarkerNotifications={onCloseMarkerNotifications}
          onMoveMarkerNotification={onMoveMarkerNotification}
          onOpenHandover={isClosedTerminalBoard ? undefined : onOpenHandover}
          onOpenIncidentDetail={onOpenIncidentDetail}
          onOpenIncidentList={onOpenIncidentList}
          onOpenSearchHistory={isClosedTerminalBoard ? undefined : onOpenSearchHistory}
          onOpenOfflinePackage={onOpenOfflinePackage}
          onOpenLogin={onOpenLogin}
          onOpenSituationBoard={onBackToSituationBoard}
        />
      </div>

      <div className={styles.scrollBody}>
      {isOffline ? (
        <div className={styles.offlineBanner} role="status">
          현재 브라우저가 오프라인입니다. 표시 중인 단말별 상태는 마지막 조회 결과일 수 있습니다.
        </div>
      ) : null}

      <div className={styles.contentStack}>
        <section className={styles.content} aria-label="단말별 적재 상태">
          <SectionTitle
            title="단말별 적재 상태"
            description="현장 단말이 오프라인에서도 수색 자료를 사용할 수 있는지 확인합니다."
          />
          {isLoading ? (
            <OfflinePackageStatusSkeleton />
          ) : boardErrorMessage ? (
            <div className={styles.emptyState} role="alert">
              <strong>{boardErrorMessage}</strong>
              <span>단말별 상태 조회가 실패했습니다. 패키지 구성 목록은 별도로 확인할 수 있습니다.</span>
              <ActionButton label="다시 조회" onClick={() => void boardQuery.refetch()} />
            </div>
          ) : isEmpty ? (
            <div className={styles.emptyState}>
              <strong>표시할 단말별 적재 상태가 없습니다.</strong>
              <span>앱 단말이 패키지 적재 상태를 보고하면 이 영역에 표시됩니다.</span>
            </div>
          ) : (
            <>
              <div className={styles.packageLoadOverview}>
                <OfflinePackageLoadGauge gauge={packageLoadGauge} />
                <div className={styles.summaryBar} aria-label="오프라인 패키지 요약">
                  <div>
                    <span className={`${styles.summaryIcon} ${styles.summaryIconReady}`} aria-hidden="true">
                      <CheckCircle2 className={styles.summaryIconGlyph} size={46} strokeWidth={2.1} />
                    </span>
                    <div className={styles.summaryMetric}>
                      <span>사용 가능 단말</span>
                      <strong>{readyCountLabel}</strong>
                    </div>
                  </div>
                  <div>
                    <span className={`${styles.summaryIcon} ${styles.summaryIconWarning}`} aria-hidden="true">
                      <RefreshCcw className={styles.summaryIconGlyph} size={46} strokeWidth={2.1} />
                    </span>
                    <div className={styles.summaryMetric}>
                      <span>재확인 필요 단말</span>
                      <strong>{warningCountLabel}</strong>
                    </div>
                  </div>
                  <div>
                    <span className={`${styles.summaryIcon} ${styles.summaryIconPurged}`} aria-hidden="true">
                      <Trash2 className={styles.summaryIconGlyph} size={46} strokeWidth={2.1} />
                    </span>
                    <div className={styles.summaryMetric}>
                      <span>삭제된 패키지</span>
                      <strong>{purgedCountLabel}</strong>
                    </div>
                  </div>
                </div>
              </div>
              <div className={styles.tableShell}>
                <table className={styles.statusTable}>
                  <thead>
                    <tr>
                      <th scope="col">단말</th>
                      <th scope="col">담당</th>
                      <th scope="col">상태</th>
                      <th scope="col">설치 정보</th>
                      <th scope="col">확인 내용</th>
                      <th scope="col">조치</th>
                    </tr>
                  </thead>
                  <tbody>
                    {visibleRows.map((row) => {
                      const statusView = getStatusView(row);

                      return (
                        <tr key={row.id}>
                          <td>
                            <div className={styles.deviceRowTitle}>
                              <span className={createDeviceIconClassName(row.accountType)} aria-hidden="true">
                                <DeviceTypeIcon accountType={row.accountType} />
                              </span>
                              <strong>{createDeviceTitle(row)}</strong>
                            </div>
                            <span>{createDeviceMeta(row)}</span>
                          </td>
                          <td>
                            <strong>{createAssigneeTitle(row)}</strong>
                            <span>{createAssigneeMeta(row)}</span>
                          </td>
                          <td>
                            <StatusBadge
                              className={getPackageStatusBadgeClassName(statusView.tone)}
                              status={statusView.label}
                              tone={statusView.tone}
                            />
                          </td>
                          <td>{formatManifestVersion(row)}</td>
                          <td>{formatPackageCheckMessage(row)}</td>
                          <td>{formatPackageAction(row)}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
              {hasDeviceStatusPagination ? (
                <div className={styles.deviceStatusPagination}>
                  <div className={styles.deviceStatusPaginationControls}>
                    <button
                      type="button"
                      className={styles.deviceStatusPaginationButton}
                      onClick={() => setDeviceStatusPage(1)}
                      disabled={currentDeviceStatusPage <= 1}
                      aria-label="첫 페이지"
                    >
                      {'<<'}
                    </button>
                    <button
                      type="button"
                      className={styles.deviceStatusPaginationButton}
                      onClick={() => setDeviceStatusPage((current) => Math.max(1, current - 1))}
                      disabled={currentDeviceStatusPage <= 1}
                      aria-label="이전 페이지"
                    >
                      {'<'}
                    </button>
                    {Array.from({ length: deviceStatusTotalPages }, (_, index) => {
                      const pageNumber = index + 1;

                      return (
                        <button
                          key={pageNumber}
                          type="button"
                          className={`${styles.deviceStatusPaginationButton} ${
                            pageNumber === currentDeviceStatusPage ? styles.deviceStatusPaginationButtonActive : ''
                          }`}
                          onClick={() => setDeviceStatusPage(pageNumber)}
                          aria-current={pageNumber === currentDeviceStatusPage ? 'page' : undefined}
                        >
                          {pageNumber}
                        </button>
                      );
                    })}
                    <button
                      type="button"
                      className={styles.deviceStatusPaginationButton}
                      onClick={() => setDeviceStatusPage((current) => Math.min(deviceStatusTotalPages, current + 1))}
                      disabled={currentDeviceStatusPage >= deviceStatusTotalPages}
                      aria-label="다음 페이지"
                    >
                      {'>'}
                    </button>
                    <button
                      type="button"
                      className={styles.deviceStatusPaginationButton}
                      onClick={() => setDeviceStatusPage(deviceStatusTotalPages)}
                      disabled={currentDeviceStatusPage >= deviceStatusTotalPages}
                      aria-label="마지막 페이지"
                    >
                      {'>>'}
                    </button>
                  </div>
                  <span className={styles.deviceStatusPaginationInfo}>
                    {deviceStatusPageStart + 1}-{Math.min(deviceStatusPageStart + DEVICE_STATUS_PAGE_SIZE, rows.length)} / {rows.length}
                  </span>
                </div>
              ) : null}
            </>
          )}
        </section>

        <section className={styles.content} aria-label="패키지 구성 목록">
          <SectionTitle
            title="패키지 구성 목록"
            description="현장 앱이 오프라인으로 보관하는 사건 자료와 지도 자료를 확인합니다."
          />
          <ManifestContent
            groups={manifestGroups}
            error={manifestQuery.error}
            isError={manifestQuery.isError}
            isLoading={manifestQuery.isLoading}
            manifest={currentManifest ?? undefined}
            referenceNow={referenceNow}
            tileSummary={tileSummary}
            onRetry={() => void manifestQuery.refetch()}
          />
        </section>
      </div>
      </div>
    </main>
  );
}

function ManifestContent({
  groups,
  error,
  isError,
  isLoading,
  manifest,
  referenceNow,
  tileSummary,
  onRetry,
}: {
  groups: readonly ManifestGroup[];
  error: unknown;
  isError: boolean;
  isLoading: boolean;
  manifest: OfflinePackageManifestResponse | undefined;
  referenceNow: Date;
  tileSummary: TileSummary;
  onRetry: () => void;
}) {
  if (isLoading) {
    return <OfflinePackageStatusSkeleton />;
  }

  if (isError) {
    const message = getOfflinePackageManifestErrorMessage(error);

    return (
      <div className={styles.emptyState} role="alert">
        <strong>{message.title}</strong>
        <span>{message.description}</span>
        <ActionButton label="구성 목록 다시 조회" onClick={onRetry} />
      </div>
    );
  }

  if (!manifest) {
    return (
      <div className={styles.emptyState}>
        <strong>패키지 구성 목록이 없습니다.</strong>
        <span>패키지 구성 조회 결과가 준비되면 구성 항목이 표시됩니다.</span>
      </div>
    );
  }

  const manifestFreshness = createManifestFreshness(manifest, referenceNow);

  return (
    <div className={styles.manifestStack}>
      {manifestFreshness.isExpired ? (
        <div className={styles.manifestWarning} role="status">
          패키지가 만료되었습니다. 현장 단말의 패키지 갱신이 필요합니다.
        </div>
      ) : null}

      <div className={styles.manifestMeta} aria-label="패키지 요약">
        <div>
          <span>패키지 버전</span>
          <strong>v{manifest.manifestVersion}</strong>
        </div>
        <div>
          <span>최신 여부</span>
          <strong>{manifestFreshness.label}</strong>
        </div>
        <div>
          <span>포함 자료</span>
          <strong>{formatManifestItemTotal(manifest)}개</strong>
        </div>
        <div>
          <span>사건 상태</span>
          <strong>{formatIncidentStatus(manifest.incident?.status ?? '')}</strong>
        </div>
      </div>

      <div className={styles.tableShell}>
        <table className={styles.statusTable}>
          <thead>
            <tr>
              <th scope="col">구성</th>
              <th scope="col">포함 내용</th>
              <th scope="col">항목 수</th>
              <th scope="col">상태</th>
            </tr>
          </thead>
              <tbody>
            {groups.map((group) => (
              <tr key={group.type}>
                <td>
                  <div className={styles.manifestRowTitle}>
                    <span className={manifestGroupIconClassName(group.type)} aria-hidden="true">
                      <ManifestGroupIcon type={group.type} />
                    </span>
                    <strong>{group.label}</strong>
                  </div>
                </td>
                <td>{group.detailLabel}</td>
                <td>{group.countLabel}</td>
                <td>
                  <StatusBadge
                    className={getPackageStatusBadgeClassName(group.statusTone)}
                    status={group.statusLabel}
                    tone={group.statusTone}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className={styles.tileSummary} aria-label="타일 목록 요약">
        <div>
          <span className={`${styles.tileSummaryIcon} ${styles.tileSummaryIconCount}`} aria-hidden="true">
            <Grid3X3 className={styles.tileSummaryIconGlyph} size={42} strokeWidth={2.1} />
          </span>
          <div className={styles.tileSummaryMetric}>
            <span>타일 수</span>
            <strong>{tileSummary.count}개</strong>
          </div>
        </div>
        <div>
          <span className={`${styles.tileSummaryIcon} ${styles.tileSummaryIconBytes}`} aria-hidden="true">
            <HardDrive className={styles.tileSummaryIconGlyph} size={42} strokeWidth={2.1} />
          </span>
          <div className={styles.tileSummaryMetric}>
            <span>총 용량</span>
            <strong>{formatBytes(tileSummary.totalBytes)}</strong>
          </div>
        </div>
        <div>
          <span className={`${styles.tileSummaryIcon} ${styles.tileSummaryIconMap}`} aria-hidden="true">
            <Map className={styles.tileSummaryIconGlyph} size={42} strokeWidth={2.1} />
          </span>
          <div className={styles.tileSummaryMetric}>
            <span>지도 종류</span>
            <strong>{tileSummary.styleIds}</strong>
          </div>
        </div>
      </div>
    </div>
  );
}
