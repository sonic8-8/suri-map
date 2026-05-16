import { useEffect, useMemo, useState } from 'react';

import { useIncidentBoardQuery } from '../../../board/api/incidentBoardApi';
import { useOfflinePackageManifestQuery } from '../../api/offlinePackageApi';
import type {
  OfflinePackageItemType,
  OfflinePackageManifestResponse,
  OfflinePackagePackageItem,
  OfflinePackageTileItem,
} from '../../api/offlinePackageApi';
import { getIncidentDetail, type IncidentDetailDto } from '../../../situationBoard/data/getIncidentDetail';
import type { SituationBoardResponseDto } from '../../../situationBoard/data/getSituationBoard';
import {
  isIncidentTerminalClosed,
  toIncidentTerminal,
  type IncidentTerminalViewModel,
} from '../../../situationBoard/presentation/utils/incidentTerminalBoardMapper';
import type { LoginAccount } from '../../../login/presentation/types/login';
import {
  ActionButton,
  type MarkerNotification,
  type StatusBadgeTone,
  SuriMapPageHeader,
  type SuriMapPageHeaderIncidentContext,
} from '../../../../shared';
import { ApiHttpError } from '../../../../shared/api';
import { useBrowserBackToIncidentList } from '../../../../shared/hooks/useBrowserBackToIncidentList';
import styles from './OfflinePackageStatusPage.module.css';

type OfflinePackageStatusPageProps = {
  currentUserAccount: LoginAccount;
  incidentId: string;
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  onBackToSituationBoard: () => void;
  onCloseMarkerNotifications: () => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  onOpenHandover: () => void;
  onOpenIncidentDetail?: () => void;
  onOpenIncidentList: () => void;
  onBrowserBackToIncidentList?: () => void;
  onOpenOfflinePackage: () => void;
};

type PackageBadgeRow = {
  id: string;
  incidentId: string;
  policePhoneId: string;
  policePhoneCode: string;
  policePhoneName: string;
  accountId: string;
  accountName: string;
  accountType: string;
  organizationType: string;
  incidentRole: string;
  packageStatus: string;
  manifestVersion: number | null;
  activeManifestVersion: number | null;
  readyForOfflineUse: boolean;
  warningRaised: boolean;
  warningReason: string;
};

type PackageSummary = {
  readyCount: number;
  warningCount: number;
  purgedCount: number;
};

type ManifestGroup = {
  type: OfflinePackageItemType;
  label: string;
  countLabel: string;
  statusLabel: string;
  statusTone: StatusBadgeTone;
  detailLabel: string;
};

type TileSummary = {
  count: number;
  totalBytes: number;
  styleIds: string;
  zRange: string;
  checksumLabel: string;
};

const manifestGroupLabels: Record<OfflinePackageItemType, string> = {
  INCIDENT_META: '사건 정보',
  MISSING_PERSON_CACHE: '실종자 정보',
  OP_LIST: '작전 차수 정보',
  ASSIGNED_AREA: '배정 수색 구역',
  INITIAL_MARKER: '초기 마커',
  OVERALL_SEARCH_AREA: '전체 수색 구역',
  TILE: '오프라인 지도',
};

const manifestGroupOrder: readonly OfflinePackageItemType[] = [
  'INCIDENT_META',
  'MISSING_PERSON_CACHE',
  'OP_LIST',
  'ASSIGNED_AREA',
  'INITIAL_MARKER',
  'OVERALL_SEARCH_AREA',
  'TILE',
];

const packageStatusLabels: Record<string, string> = {
  READY: '오프라인 사용 가능',
  DOWNLOADING: '자동 설치 중',
  STALE: '갱신 필요',
  FAILED: '실패',
  PARTIAL: '일부 미완료',
  MISSING: '확인 전',
  PURGED: '삭제됨',
};

const packageStatusBadgeToneClassNames: Record<StatusBadgeTone, string> = {
  active: styles.packageStatusBadgeActive,
  waiting: styles.packageStatusBadgeWaiting,
  danger: styles.packageStatusBadgeDanger,
  closed: styles.packageStatusBadgeClosed,
  neutral: styles.packageStatusBadgeNeutral,
};

function getPackageStatusBadgeClassName(tone: StatusBadgeTone) {
  return `${styles.packageStatusBadge} ${packageStatusBadgeToneClassNames[tone]}`;
}

export function OfflinePackageStatusPage({
  currentUserAccount,
  incidentId,
  markerNotificationIndex,
  markerNotifications,
  onBackToSituationBoard,
  onCloseMarkerNotifications,
  onMoveMarkerNotification,
  onOpenHandover,
  onOpenIncidentDetail,
  onOpenIncidentList,
  onBrowserBackToIncidentList,
  onOpenOfflinePackage,
}: OfflinePackageStatusPageProps) {
  useBrowserBackToIncidentList(onBrowserBackToIncidentList);
  const [incidentDetail, setIncidentDetail] = useState<IncidentDetailDto | null>(null);
  const [isOffline, setIsOffline] = useState(() => (typeof navigator === 'undefined' ? false : !navigator.onLine));

  const boardQuery = useIncidentBoardQuery({ incidentId, includeSlots: ['package_badge', 'incident_terminal'] });
  const manifestQuery = useOfflinePackageManifestQuery({ incidentId });
  const rows = useMemo(() => readPackageBadgeRows(boardQuery.data?.slots.package_badge), [boardQuery.data]);
  const incidentTerminal = useMemo(
    () => (boardQuery.data ? toIncidentTerminal(boardQuery.data as unknown as SituationBoardResponseDto) : null),
    [boardQuery.data],
  );
  const manifestGroups = useMemo(() => createManifestGroups(manifestQuery.data), [manifestQuery.data]);
  const tileSummary = useMemo(() => createTileSummary(readManifestTileItems(manifestQuery.data)), [manifestQuery.data]);
  const serverTs = boardQuery.data?.serverTs ?? null;
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
  const currentAccountLabel = `${currentUserAccount.name} / ${currentUserAccount.organization}`;
  const incidentContext = createIncidentContext(incidentId, incidentDetail, incidentTerminal);
  const isClosedTerminalBoard = isIncidentTerminalClosed(incidentTerminal);
  const timestampLabel = serverTs ? formatKstDateTime(new Date(serverTs)) : '동기화 전';
  const isEmpty = !isLoading && !boardErrorMessage && rows.length === 0;
  const isPackageSummaryPlaceholder = isLoading || (Boolean(boardErrorMessage) && rows.length === 0);
  const readyCountLabel = isPackageSummaryPlaceholder ? '-대' : `${summary.readyCount}대`;
  const warningCountLabel = isPackageSummaryPlaceholder ? '-대' : `${summary.warningCount}대`;
  const purgedCountLabel = isPackageSummaryPlaceholder ? '-대' : `${summary.purgedCount}대`;

  return (
    <main className={styles.page}>
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
        onOpenOfflinePackage={onOpenOfflinePackage}
        onOpenSituationBoard={onBackToSituationBoard}
      />

      <div className={styles.scrollBody}>
      <section className={styles.summaryBar} aria-label="오프라인 패키지 요약">
        <SummaryMetricCard icon="▦" label="사용 가능 단말" value={readyCountLabel} tone="blue" />
        <SummaryMetricCard icon="▯" label="재확인 필요 단말" value={warningCountLabel} tone="purple" />
        <SummaryMetricCard icon="▣" label="삭제된 패키지" value={purgedCountLabel} tone="green" />
      </section>

      {isOffline ? (
        <div className={styles.offlineBanner} role="status">
          현재 브라우저가 오프라인입니다. 표시 중인 단말별 상태는 마지막 조회 결과일 수 있습니다.
        </div>
      ) : null}

      <PackageReadinessSummary
        groups={manifestGroups}
        isLoading={manifestQuery.isLoading}
        manifest={manifestQuery.data}
        referenceNow={referenceNow}
        summary={summary}
      />

      <div className={styles.contentStack}>
        <section className={styles.content} aria-label="단말별 적재 상태">
          <SectionTitle
            title="단말별 적재 상태"
            description="현장 단말이 오프라인에서도 수색 자료를 사용할 수 있는지 확인합니다."
          />
          {isLoading ? (
            <PackageStatusSkeleton />
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
            <div className={styles.deviceList}>
              {rows.map((row) => (
                <DeviceStatusCard key={row.id} row={row} />
              ))}
            </div>
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
            manifest={manifestQuery.data}
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

function SectionTitle({ title, description }: { title: string; description: string }) {
  return (
    <div className={styles.sectionTitle}>
      <h2>{title}</h2>
      <p>{description}</p>
    </div>
  );
}

type SummaryMetricTone = 'blue' | 'purple' | 'green' | 'cyan';

function SummaryMetricCard({
  compact = false,
  icon,
  label,
  tone,
  value,
}: {
  compact?: boolean;
  icon: string;
  label: string;
  tone: SummaryMetricTone;
  value: string;
}) {
  return (
    <div className={`${styles.summaryMetricCard} ${styles[`summaryMetricTone${capitalize(tone)}`]} ${compact ? styles.summaryMetricCardCompact : ''}`}>
      <span className={styles.summaryMetricIcon} aria-hidden="true">{icon}</span>
      <span className={styles.summaryMetricText}>
        <span>{label}</span>
        <strong>{value}</strong>
      </span>
    </div>
  );
}

function PackageReadinessSummary({
  groups,
  isLoading,
  manifest,
  referenceNow,
  summary,
}: {
  groups: readonly ManifestGroup[];
  isLoading: boolean;
  manifest: OfflinePackageManifestResponse | undefined;
  referenceNow: Date;
  summary: PackageSummary;
}) {
  if (isLoading && !manifest) {
    return (
      <section className={styles.readinessCard} aria-label="패키지 준비 상태">
        <div className={styles.skeletonRow} />
      </section>
    );
  }

  const completedGroupCount = groups.filter((group) => group.statusTone === 'active').length;
  const waitingGroupCount = groups.filter((group) => group.statusTone === 'waiting').length;
  const failedGroupCount = groups.filter((group) => group.statusTone === 'danger').length;
  const manifestFreshness = manifest ? createManifestFreshness(manifest, referenceNow) : null;
  const refreshNeededCount = manifestFreshness?.isExpired ? 1 : 0;
  const totalGroupCount = Math.max(groups.length, 1);
  const progressPercent = Math.round((completedGroupCount / totalGroupCount) * 100);
  const totalManifestItems = manifest ? formatManifestItemTotal(manifest) : 0;

  return (
    <section className={styles.readinessCard} aria-label="패키지 준비 상태">
      <div className={styles.readinessHeader}>
        <div>
          <h2>패키지 준비 상태</h2>
          <p>단말 적재 상태와 오프라인 구성 자료를 한 번에 점검합니다.</p>
        </div>
        <div className={styles.readinessPercent} aria-label={`패키지 구성 완료율 ${progressPercent}%`}>
          <span>{progressPercent}%</span>
          <small>구성 완료</small>
        </div>
      </div>

      <div className={styles.readinessGrid}>
        <div className={styles.progressPanel}>
          <div className={styles.progressTrack} aria-hidden="true">
            <span style={{ width: `${progressPercent}%` }} />
          </div>
          <div className={styles.readinessStats}>
            <VisualStat icon="✓" label="완료" value={`${completedGroupCount}개`} tone="active" />
            <VisualStat icon="◷" label="대기" value={`${waitingGroupCount}개`} tone="waiting" />
            <VisualStat icon="↻" label="갱신 필요" value={`${refreshNeededCount + failedGroupCount}개`} tone="danger" />
          </div>
        </div>

        <div className={styles.packageComposition}>
          <div className={styles.packageCompositionHeader}>
            <strong>포함 자료 구성</strong>
            <span>총 {totalManifestItems}개</span>
          </div>
          <div className={styles.stackedBar} aria-label="포함 자료 구성 막대">
            {groups.map((group, index) => {
              const count = parseCountLabel(group.countLabel);
              const percent = totalManifestItems > 0 ? Math.max(5, (count / totalManifestItems) * 100) : 100 / groups.length;
              return (
                <span
                  key={group.type}
                  className={styles[`stackedSegment${index % 7}`]}
                  style={{ width: `${percent}%` }}
                  title={`${group.label} ${group.countLabel}`}
                />
              );
            })}
          </div>
          <div className={styles.compositionLegend}>
            {groups.map((group) => (
              <span key={group.type}>
                <i aria-hidden="true">{getManifestGroupIcon(group.type)}</i>
                {group.label.replace(' 정보', '').replace(' 수색 구역', ' 구역')} {group.countLabel}
              </span>
            ))}
          </div>
        </div>
      </div>

      <div className={styles.deviceSummaryLine}>
        <span>단말 상태</span>
        <strong>사용 가능 {summary.readyCount}대</strong>
        <strong>재확인 {summary.warningCount}대</strong>
        <strong>삭제 {summary.purgedCount}대</strong>
      </div>
    </section>
  );
}

function DeviceStatusCard({ row }: { row: PackageBadgeRow }) {
  const statusView = getStatusView(row);

  return (
    <article className={styles.deviceCard}>
      <div className={styles.deviceIdentity}>
        <span className={styles.deviceIcon} aria-hidden="true">▯</span>
        <span>
          <strong>{createDeviceTitle(row)}</strong>
          <small>{createDeviceMeta(row)}</small>
        </span>
      </div>
      <div className={styles.deviceAssignee}>
        <span>담당</span>
        <strong>{createAssigneeTitle(row)}</strong>
        <small>{createAssigneeMeta(row)}</small>
      </div>
      <div className={styles.deviceStepper} aria-label={`${createDeviceTitle(row)} 패키지 적재 단계`}>
        <DeviceStep icon="↓" label="설치 정보" value={formatManifestVersion(row)} tone="info" />
        <DeviceStep icon={statusView.tone === 'danger' ? '!' : statusView.tone === 'active' ? '✓' : '◷'} label="확인 내용" value={formatPackageCheckMessage(row)} tone={statusView.tone} />
        <DeviceStep icon={statusView.tone === 'active' ? '✓' : statusView.tone === 'danger' ? '!' : '↻'} label="상태" value={statusView.label} tone={statusView.tone} />
        <DeviceStep icon="…" label="조치" value={formatPackageAction(row)} tone="neutral" />
      </div>
      <span className={styles.deviceMore} aria-hidden="true">…</span>
    </article>
  );
}

function DeviceStep({ icon, label, tone, value }: { icon: string; label: string; tone: StatusBadgeTone | 'info' | 'neutral'; value: string }) {
  return (
    <span className={`${styles.deviceStep} ${styles[`deviceStep${capitalize(tone)}`]}`}>
      <i aria-hidden="true">{icon}</i>
      <span>{label}</span>
      <strong>{value}</strong>
    </span>
  );
}

function VisualStat({ icon, label, tone, value }: { icon: string; label: string; tone: StatusBadgeTone; value: string }) {
  return (
    <span className={`${styles.visualStat} ${styles[`visualStat${capitalize(tone)}`]}`}>
      <i aria-hidden="true">{icon}</i>
      <span>{label}</span>
      <strong>{value}</strong>
    </span>
  );
}

function VisualStatusBadge({ label, tone }: { label: string; tone: StatusBadgeTone }) {
  return (
    <span className={`${getPackageStatusBadgeClassName(tone)} ${styles.visualStatusBadge}`}>
      <i aria-hidden="true">{getStatusIcon(tone, label)}</i>
      {label}
    </span>
  );
}

function getManifestGroupIcon(type: OfflinePackageItemType) {
  const icons: Record<OfflinePackageItemType, string> = {
    INCIDENT_META: '▤',
    MISSING_PERSON_CACHE: '☻',
    OP_LIST: '▦',
    ASSIGNED_AREA: '▣',
    INITIAL_MARKER: '⌖',
    OVERALL_SEARCH_AREA: '▥',
    TILE: '◒',
  };
  return icons[type];
}

function getStatusIcon(tone: StatusBadgeTone, label: string) {
  if (tone === 'active') return '✓';
  if (tone === 'danger') return '!';
  if (tone === 'closed') return '×';
  if (label.includes('대기')) return '◷';
  if (label.includes('완료')) return '✓';
  return '•';
}

function parseCountLabel(label: string) {
  const match = label.match(/\d+/);
  return match ? Number(match[0]) : 0;
}

function capitalize(value: string) {
  return `${value.charAt(0).toUpperCase()}${value.slice(1)}`;
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
    return <PackageStatusSkeleton />;
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
                  <span className={styles.manifestItemIcon} aria-hidden="true">{getManifestGroupIcon(group.type)}</span>
                  <strong>{group.label}</strong>
                </td>
                <td>{group.detailLabel}</td>
                <td>{group.countLabel}</td>
                <td>
                  <VisualStatusBadge label={group.statusLabel} tone={group.statusTone} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className={styles.tileSummary} aria-label="타일 목록 요약">
        <SummaryMetricCard icon="▣" label="타일 수" value={`${tileSummary.count}개`} tone="blue" compact />
        <SummaryMetricCard icon="▲" label="총 용량" value={formatBytes(tileSummary.totalBytes)} tone="purple" compact />
        <SummaryMetricCard icon="⬡" label="지도 종류" value={tileSummary.styleIds} tone="cyan" compact />
        <SummaryMetricCard icon="▥" label="확대 단계" value={tileSummary.zRange} tone="blue" compact />
        <SummaryMetricCard icon="✓" label="검증 상태" value={tileSummary.checksumLabel} tone="green" compact />
      </div>
    </div>
  );
}

function getOfflinePackageManifestErrorMessage(error: unknown) {
  if (error instanceof ApiHttpError) {
    if (error.code === 'package_manifest_not_ready') {
      return {
        title: '오프라인 패키지를 아직 만들 수 없습니다.',
        description: '현재 사건에 OP 또는 전체 수색 구역이 준비되지 않았습니다. 전체 수색 구역을 저장한 뒤 다시 조회하세요.',
      };
    }
    if (error.code === 'package_purged') {
      return {
        title: '오프라인 패키지가 삭제되었습니다.',
        description: '종료 또는 파기 처리된 사건이라 패키지 구성 목록을 다시 내려받을 수 없습니다.',
      };
    }
  }

  return {
    title: '패키지 구성 목록을 불러오지 못했습니다.',
    description: '단말별 적재 상태와 별개로 구성 목록 조회만 실패했습니다.',
  };
}

function createIncidentContext(
  incidentId: string,
  incidentDetail: IncidentDetailDto | null,
  incidentTerminal: IncidentTerminalViewModel | null,
): SuriMapPageHeaderIncidentContext {
  if (isIncidentTerminalClosed(incidentTerminal)) {
    return createTerminalIncidentContext(incidentId, incidentDetail, incidentTerminal);
  }

  const missingPerson = incidentDetail && 'missingPerson' in incidentDetail ? incidentDetail.missingPerson : null;
  const assignments = incidentDetail && 'assignments' in incidentDetail ? incidentDetail.assignments : [];
  const displayName = missingPerson?.displayName?.trim() || null;
  const lastSeenLabel = createLastSeenLabel(
    missingPerson?.lastSeenAt ?? null,
    missingPerson?.lastSeenLocationText ?? null,
  );
  const assignmentLabel = incidentDetail
    ? assignments.length > 0
      ? `${assignments.length}개 배정`
      : '배정 없음'
    : '-개';
  const status = incidentDetail?.status ?? 'OPEN';
  const incidentTitle = readIncidentTitle(incidentDetail);
  const versionLabel = incidentDetail?.version ? `정보 버전 ${incidentDetail.version}` : '정보 버전 확인 전';

  return {
    avatarLabel: createAvatarLabel(displayName),
    eyebrow: versionLabel,
    title: incidentTitle ?? (displayName ? `${displayName} 실종 사건` : '오프라인 패키지 대상 사건'),
    metrics: [
      { label: '실종자', value: displayName ?? '-' },
      { label: '마지막 확인', value: lastSeenLabel },
      { label: '배정', value: assignmentLabel },
    ],
    statusLabel: `${status === 'CLOSED' ? '종료' : '진행 중'} / 오프라인 패키지`,
    statusTone: status === 'CLOSED' ? 'terminal' : 'active',
  };
}

function createTerminalIncidentContext(
  incidentId: string,
  incidentDetail: IncidentDetailDto | null,
  incidentTerminal: IncidentTerminalViewModel,
): SuriMapPageHeaderIncidentContext {
  const incidentTitle = readIncidentTitle(incidentDetail);
  const versionLabel = incidentDetail?.version ? `정보 버전 ${incidentDetail.version}` : '종료 사건';

  return {
    avatarLabel: '종료',
    eyebrow: versionLabel,
    title: incidentTitle ?? `종료된 사건 ${formatShortId(incidentId)}`,
    metrics: [
      {
        label: '종료 시각',
        value: incidentTerminal.closedAt ? formatKstDateTime(new Date(incidentTerminal.closedAt)) : '-',
      },
      {
        label: '쓰기 제한',
        value: writeDisabledReasonLabels[incidentTerminal.writeDisabledReason],
      },
      {
        label: '로컬 삭제',
        value: localPurgeStateLabels[incidentTerminal.localPurgeState],
      },
    ],
    statusLabel: `${terminalStatusLabels[incidentTerminal.terminalStatus]} / 오프라인 패키지`,
    statusTone: 'terminal',
  };
}

const terminalStatusLabels: Record<IncidentTerminalViewModel['terminalStatus'], string> = {
  OPEN: '진행 중',
  CLOSED: '종료됨',
  PURGE_PENDING: '삭제 대기',
  PURGED: '삭제됨',
};

const writeDisabledReasonLabels: Record<IncidentTerminalViewModel['writeDisabledReason'], string> = {
  none: '제한 없음',
  incident_closed: '사건 종료',
  purged: '데이터 삭제됨',
};

const localPurgeStateLabels: Record<IncidentTerminalViewModel['localPurgeState'], string> = {
  not_started: '시작 전',
  queued: '대기',
  in_progress: '진행 중',
  completed: '완료',
  failed_retryable: '재시도 필요',
};

function createAvatarLabel(displayName: string | null) {
  if (!displayName) return '사건';

  return displayName.length > 4 ? displayName.slice(0, 4) : displayName;
}

function readIncidentTitle(incidentDetail: IncidentDetailDto | null) {
  if (!incidentDetail || !('title' in incidentDetail)) return null;
  const title = incidentDetail.title.trim();
  return title || null;
}

function createLastSeenLabel(lastSeenAt: string | null, lastSeenLocationText: string | null) {
  const timeLabel = lastSeenAt ? formatKstDateTime(new Date(lastSeenAt)) : null;
  const locationLabel = lastSeenLocationText?.trim() || null;

  if (timeLabel && locationLabel) {
    return `${timeLabel} / ${locationLabel}`;
  }

  return timeLabel ?? locationLabel ?? '-';
}

function PackageStatusSkeleton() {
  return (
    <div className={styles.skeletonList} aria-label="오프라인 패키지 상태를 불러오는 중">
      {Array.from({ length: 5 }, (_, index) => (
        <div key={index} className={styles.skeletonRow} />
      ))}
    </div>
  );
}

function readPackageBadgeRows(value: unknown): PackageBadgeRow[] {
  if (!Array.isArray(value)) return [];

  return value.map(readPackageBadgeRow).filter((row): row is PackageBadgeRow => row !== null);
}

function readPackageBadgeRow(value: unknown): PackageBadgeRow | null {
  if (!isRecord(value)) return null;

  const policePhoneId = readString(value, 'policePhoneId');
  if (!policePhoneId) return null;

  const localWarningInput = isRecord(value.localWarningInput) ? value.localWarningInput : {};
  const id = readString(value, 'id') ?? `package-${policePhoneId}`;

  return {
    id,
    incidentId: readString(value, 'incidentId') ?? '',
    policePhoneId,
    policePhoneCode: readString(value, 'policePhoneCode') ?? '',
    policePhoneName: readString(value, 'policePhoneName') ?? '',
    accountId: readString(value, 'accountId') ?? '',
    accountName: readString(value, 'accountName') ?? '',
    accountType: readString(value, 'accountType') ?? '',
    organizationType: readString(value, 'organizationType') ?? '',
    incidentRole: readString(value, 'incidentRole') ?? '',
    packageStatus: readString(value, 'packageStatus') ?? readString(value, 'status') ?? 'UNKNOWN',
    manifestVersion: readNumber(value, 'manifestVersion'),
    activeManifestVersion: readNumber(localWarningInput, 'activeManifestVersion'),
    readyForOfflineUse: readBoolean(value, 'readyForOfflineUse'),
    warningRaised: readBoolean(localWarningInput, 'raised'),
    warningReason: readString(localWarningInput, 'reason') ?? '패키지 미완료',
  };
}

function createSummary(rows: PackageBadgeRow[]): PackageSummary {
  return rows.reduce<PackageSummary>(
    (summary, row) => {
      if (row.packageStatus === 'PURGED') {
        summary.purgedCount += 1;
      } else if (row.packageStatus === 'READY' && row.readyForOfflineUse && !row.warningRaised) {
        summary.readyCount += 1;
      } else {
        summary.warningCount += 1;
      }

      return summary;
    },
    { readyCount: 0, warningCount: 0, purgedCount: 0 },
  );
}

function createManifestGroups(manifest: OfflinePackageManifestResponse | undefined): readonly ManifestGroup[] {
  const packageItems = readManifestPackageItems(manifest);

  return manifestGroupOrder.map((type) => {
    const items = packageItems.filter((item) => item.itemType === type);
    const count = countManifestSourceItems(type, manifest);
    const statusView = getManifestGroupStatus(items);

    return {
      type,
      label: manifestGroupLabels[type],
      countLabel: `${count}개`,
      statusLabel: statusView.label,
      statusTone: statusView.tone,
      detailLabel: createManifestGroupDetail(type, manifest),
    };
  });
}

function countManifestSourceItems(type: OfflinePackageItemType, manifest: OfflinePackageManifestResponse | undefined) {
  if (!manifest) return 0;

  if (type === 'OP_LIST') return readManifestOperationalPeriods(manifest).length;
  if (type === 'ASSIGNED_AREA') return readManifestAssignedAreas(manifest).length;
  if (type === 'INITIAL_MARKER') return readManifestInitialMarkers(manifest).length;
  if (type === 'OVERALL_SEARCH_AREA') return manifest.overallSearchArea ? 1 : 0;
  if (type === 'TILE') return readManifestTileItems(manifest).length;
  if (type === 'MISSING_PERSON_CACHE') return manifest.missingPerson ? 1 : 0;
  return manifest.incident ? 1 : 0;
}

function createManifestGroupDetail(
  type: OfflinePackageItemType,
  manifest: OfflinePackageManifestResponse | undefined,
) {
  if (!manifest) return '자료 확인 전';

  const operationalPeriods = readManifestOperationalPeriods(manifest);
  const assignedAreas = readManifestAssignedAreas(manifest);
  const initialMarkers = readManifestInitialMarkers(manifest);
  const tileItems = readManifestTileItems(manifest);

  if (type === 'INCIDENT_META') {
    return `${formatIncidentStatus(manifest.incident?.status ?? '')} 사건 정보`;
  }
  if (type === 'MISSING_PERSON_CACHE') {
    const name = manifest.missingPerson?.displayName?.trim() || '실종자 정보';
    const photoLabel = manifest.missingPerson?.photoObjectKey ? '사진 포함' : '사진 없음';
    return `${name} · ${photoLabel}`;
  }
  if (type === 'OP_LIST') {
    const activeCount = operationalPeriods.filter((op) => op.status === 'ACTIVE').length;
    return activeCount > 0 ? `진행 중인 차수 포함` : '작전 차수 기록 포함';
  }
  if (type === 'ASSIGNED_AREA') {
    if (assignedAreas.length === 0) return '아직 배정된 수색 구역이 없습니다';
    const completedCount = assignedAreas.filter((area) => area.status === 'COMPLETED').length;
    const activeCount = assignedAreas.filter((area) => area.status === 'ACTIVE').length;
    return `진행 ${activeCount}개 · 완료 ${completedCount}개`;
  }
  if (type === 'INITIAL_MARKER') {
    return initialMarkers.length > 0 ? '초기 확인 지점 포함' : '초기 마커 없음';
  }
  if (type === 'OVERALL_SEARCH_AREA') {
    return manifest.overallSearchArea ? '전체 수색 범위 포함' : '전체 수색 범위 없음';
  }

  return tileItems.length > 0
    ? `${tileItems.length}개 지도 타일 · ${formatBytes(createTileSummary(tileItems).totalBytes)}`
    : '오프라인 지도 없음';
}

function readManifestPackageItems(manifest: OfflinePackageManifestResponse | undefined) {
  return Array.isArray(manifest?.packageItems) ? manifest.packageItems : [];
}

function readManifestOperationalPeriods(manifest: OfflinePackageManifestResponse | undefined) {
  return Array.isArray(manifest?.operationalPeriods) ? manifest.operationalPeriods : [];
}

function readManifestAssignedAreas(manifest: OfflinePackageManifestResponse | undefined) {
  return Array.isArray(manifest?.assignedAreas) ? manifest.assignedAreas : [];
}

function readManifestInitialMarkers(manifest: OfflinePackageManifestResponse | undefined) {
  return Array.isArray(manifest?.initialMarkers) ? manifest.initialMarkers : [];
}

function readManifestTileItems(manifest: OfflinePackageManifestResponse | undefined) {
  return Array.isArray(manifest?.tileItems) ? manifest.tileItems : [];
}

function getManifestGroupStatus(items: readonly OfflinePackagePackageItem[]): { label: string; tone: StatusBadgeTone } {
  if (items.length === 0) return { label: '대기', tone: 'waiting' };
  if (items.some((item) => item.status === 'FAILED')) return { label: '실패', tone: 'danger' };
  if (items.every((item) => item.status === 'DOWNLOADED' || item.status === 'READY')) {
    return { label: '완료', tone: 'active' };
  }
  if (items.some((item) => item.status === 'DOWNLOADED' || item.status === 'READY')) {
    return { label: '일부 미완료', tone: 'waiting' };
  }
  return { label: '대기', tone: 'waiting' };
}

function createTileSummary(tileItems: readonly OfflinePackageTileItem[]): TileSummary {
  if (tileItems.length === 0) {
    return {
      count: 0,
      totalBytes: 0,
      styleIds: '-',
      zRange: '-',
      checksumLabel: '없음',
    };
  }

  return {
    count: tileItems.length,
    totalBytes: tileItems.reduce((sum, item) => sum + item.bytes, 0),
    styleIds: uniqueValues(tileItems.map((item) => item.styleId)).join(', '),
    zRange: `${createRangeLabel(tileItems.map((item) => item.z))}단계`,
    checksumLabel: tileItems.every((item) => Boolean(item.checksum)) ? '검증 가능' : '일부 확인 필요',
  };
}

function createRangeLabel(values: readonly number[]) {
  const min = Math.min(...values);
  const max = Math.max(...values);
  return min === max ? String(min) : `${min}..${max}`;
}

function uniqueValues(values: readonly string[]) {
  return [...new Set(values.filter(Boolean))];
}

function createDeviceTitle(row: PackageBadgeRow) {
  return row.policePhoneName || formatPhoneCode(row.policePhoneCode) || `단말 ${formatShortId(row.policePhoneId)}`;
}

function createDeviceMeta(row: PackageBadgeRow) {
  const title = createDeviceTitle(row);
  const phoneCode = formatPhoneCode(row.policePhoneCode);
  const phoneCodeLabel = phoneCode && phoneCode !== title ? phoneCode : '';
  const shortId = `단말 식별 ${formatShortId(row.policePhoneId)}`;
  return [phoneCodeLabel, shortId].filter(Boolean).join(' / ');
}

function createAssigneeTitle(row: PackageBadgeRow) {
  return row.accountName || formatOrganizationType(row.organizationType) || '담당 정보 없음';
}

function createAssigneeMeta(row: PackageBadgeRow) {
  const role = formatIncidentRole(row.incidentRole);
  const accountType = formatAccountType(row.accountType);
  return [accountType, role].filter((item) => item !== '담당 유형 확인 전' && item !== '역할 확인 전').join(' / ')
    || '담당 배정 확인 전';
}

function formatAccountType(accountType: string) {
  if (accountType === 'TEAM') return '팀 단말';
  if (accountType === 'PATROL_CAR') return '순찰차 단말';
  if (accountType === 'COMMAND') return '지휘 계정';
  return accountType || '담당 유형 확인 전';
}

function formatIncidentRole(role: string) {
  if (role === 'MEMBER') return '수색 대원';
  if (role === 'FIELD_COMMANDER') return '현장 지휘';
  if (role === 'INCIDENT_COMMANDER') return '사건 지휘';
  return role || '역할 확인 전';
}

function formatOrganizationType(organizationType: string) {
  if (organizationType === 'MISSING_TEAM') return '실종팀';
  if (organizationType === 'SUPPORT_UNIT') return '지원 부대';
  if (organizationType === 'POLICE_SUBSTATION') return '지구대';
  return '';
}

function getStatusView(row: PackageBadgeRow): { label: string; tone: StatusBadgeTone } {
  if (row.packageStatus === 'PURGED') return { label: packageStatusLabels.PURGED, tone: 'closed' };
  if (row.packageStatus === 'READY' && row.readyForOfflineUse && !row.warningRaised) {
    return { label: packageStatusLabels.READY, tone: 'active' };
  }
  if (row.packageStatus === 'STALE') return { label: packageStatusLabels.STALE, tone: 'waiting' };
  if (row.packageStatus === 'DOWNLOADING') return { label: packageStatusLabels.DOWNLOADING, tone: 'waiting' };
  if (row.packageStatus === 'FAILED') return { label: packageStatusLabels.FAILED, tone: 'danger' };
  if (row.packageStatus === 'PARTIAL') return { label: packageStatusLabels.PARTIAL, tone: 'waiting' };
  if (row.packageStatus === 'MISSING') return { label: packageStatusLabels.MISSING, tone: 'waiting' };

  return { label: row.packageStatus || '확인 전', tone: 'waiting' };
}

function formatManifestVersion(row: PackageBadgeRow) {
  if (row.packageStatus === 'PURGED') return '-';
  if (row.manifestVersion === null) return '설치 정보 확인 전';
  if (row.activeManifestVersion !== null && row.activeManifestVersion !== row.manifestVersion) {
    return `설치 v${row.manifestVersion} · 최신 v${row.activeManifestVersion} 필요`;
  }

  return `설치 v${row.manifestVersion}`;
}

function formatWarningReason(reason: string) {
  if (reason === 'S7 package status is MISSING, STALE, EXPIRED, or any required item failed') {
    return '패키지 누락/만료/구버전 또는 필수 항목 실패';
  }
  if (reason === 'S7 package status is COMPLETE for the active manifestVersion') {
    return '추가 확인 없음';
  }
  if (
    reason.includes('MISSING') ||
    reason.includes('STALE') ||
    reason.includes('EXPIRED') ||
    reason.includes('failed')
  ) {
    return '패키지 누락/만료/구버전 또는 필수 항목 실패';
  }
  if (reason === 'manifest_stale' || reason === 'stale_manifest') return '갱신 필요';
  if (reason === 'package_incomplete' || reason === 'partial') return '패키지 미완료';
  if (/S7|MISSING|STALE|EXPIRED|FAILED|required item/i.test(reason)) {
    return '패키지 상태를 다시 확인해야 합니다.';
  }
  return reason || '패키지 미완료';
}

function formatPackageCheckMessage(row: PackageBadgeRow) {
  if (row.packageStatus === 'READY' && row.readyForOfflineUse && !row.warningRaised) {
    return '필수 자료 설치 완료';
  }
  if (row.packageStatus === 'DOWNLOADING') return '앱에서 자동 설치 중';
  if (row.packageStatus === 'STALE') return '업데이트 필요';
  if (row.packageStatus === 'FAILED') return '자동 설치 실패';
  if (row.packageStatus === 'PARTIAL') return '일부 자료 설치 실패';
  if (row.packageStatus === 'MISSING') return '설치 보고 없음';
  if (row.packageStatus === 'PURGED') return '사건 종료 후 자료 삭제됨';

  const reason = row.warningRaised ? formatWarningReason(row.warningReason) : '';
  return reason || '설치 상태를 확인해야 합니다.';
}

function formatPackageAction(row: PackageBadgeRow) {
  if (row.packageStatus === 'READY' && row.readyForOfflineUse && !row.warningRaised) return '조치 없음';
  if (row.packageStatus === 'PURGED') return '조치 없음';
  if (row.packageStatus === 'DOWNLOADING') return '완료될 때까지 대기';
  if (row.packageStatus === 'STALE') return '앱 연결 시 최신 패키지 갱신';
  if (row.packageStatus === 'FAILED') return '단말 네트워크 확인 또는 수동 재시도 필요';
  if (row.packageStatus === 'PARTIAL') return '단말 네트워크 연결 후 자동 재시도 확인';
  if (row.packageStatus === 'MISSING') return '폴리폰에서 사건 진입 필요';
  return '단말 상태 재확인';
}

function createManifestFreshness(manifest: OfflinePackageManifestResponse, referenceNow: Date) {
  const expiresAt = new Date(manifest.expiresAt);
  if (Number.isNaN(expiresAt.getTime())) {
    return { isExpired: false, label: '만료 시각 확인 전' };
  }

  if (expiresAt.getTime() <= referenceNow.getTime()) {
    return { isExpired: true, label: '만료됨' };
  }

  return { isExpired: false, label: `${formatKstDateTime(expiresAt)}까지 유효` };
}

function formatManifestItemTotal(manifest: OfflinePackageManifestResponse) {
  return [
    manifest.incident ? 1 : 0,
    manifest.missingPerson ? 1 : 0,
    readManifestOperationalPeriods(manifest).length,
    readManifestAssignedAreas(manifest).length,
    readManifestInitialMarkers(manifest).length,
    manifest.overallSearchArea ? 1 : 0,
    readManifestTileItems(manifest).length,
  ].reduce((sum, count) => sum + count, 0);
}

function formatShortId(id: string) {
  const normalized = id.trim();
  if (!normalized) return '확인 전';
  const compact = normalized.replaceAll('-', '');
  return compact.length > 4 ? compact.slice(-4).toUpperCase() : compact.toUpperCase();
}

function formatPhoneCode(phoneCode: string) {
  const normalized = phoneCode.trim();
  if (!normalized || isUuidLike(normalized)) return '';
  return normalized;
}

function isUuidLike(value: string) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
}

function formatIncidentStatus(status: string) {
  if (status === 'OPEN') return '진행 중';
  if (status === 'CLOSED') return '종료';
  return status || '-';
}

function shortHash(hash: string) {
  if (!hash) return '-';
  return hash.length > 20 ? `${hash.slice(0, 17)}...` : hash;
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  const kib = bytes / 1024;
  if (kib < 1024) return `${kib.toFixed(1)} KiB`;
  return `${(kib / 1024).toFixed(1)} MiB`;
}

function formatKstDateTime(date: Date) {
  if (Number.isNaN(date.getTime())) return '-';

  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
    .formatToParts(date)
    .reduce<Record<string, string>>((dateParts, part) => {
      dateParts[part.type] = part.value;
      return dateParts;
    }, {});

  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute} KST`;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function readString(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'string' ? value : null;
}

function readNumber(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function readBoolean(row: Record<string, unknown>, key: string) {
  const value = row[key];
  return typeof value === 'boolean' ? value : false;
}
