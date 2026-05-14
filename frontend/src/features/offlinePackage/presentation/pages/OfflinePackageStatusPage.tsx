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
  StatusBadge,
  type MarkerNotification,
  type StatusBadgeTone,
  SuriMapPageHeader,
  type SuriMapPageHeaderIncidentContext,
} from '../../../../shared';
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
  onOpenIncidentList: () => void;
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
  itemKey: string;
  sourceVersion: string;
  sourceHash: string;
  note: string;
};

type TileSummary = {
  count: number;
  totalBytes: number;
  styleIds: string;
  zRange: string;
  xRange: string;
  yRange: string;
  checksumLabel: string;
};

const manifestGroupLabels: Record<OfflinePackageItemType, string> = {
  INCIDENT_META: '사건 메타',
  MISSING_PERSON_CACHE: '실종자 캐시',
  OP_LIST: '작전 기간 목록',
  ASSIGNED_AREA: '배정 구역',
  INITIAL_MARKER: '초기 기준 마커',
  OVERALL_SEARCH_AREA: '전체 수색 구역',
  TILE: '타일',
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
  STALE: '재적재 필요',
  FAILED: '실패',
  PARTIAL: '일부 미완료',
  MISSING: '확인 전',
  PURGED: '삭제됨',
};

export function OfflinePackageStatusPage({
  currentUserAccount,
  incidentId,
  markerNotificationIndex,
  markerNotifications,
  onBackToSituationBoard,
  onCloseMarkerNotifications,
  onMoveMarkerNotification,
  onOpenHandover,
  onOpenIncidentList,
  onOpenOfflinePackage,
}: OfflinePackageStatusPageProps) {
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
  const tileSummary = useMemo(() => createTileSummary(manifestQuery.data?.tileItems ?? []), [manifestQuery.data]);
  const serverTs = boardQuery.data?.serverTs ?? null;
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
        onOpenIncidentList={onOpenIncidentList}
        onOpenOfflinePackage={onOpenOfflinePackage}
        onOpenSituationBoard={onBackToSituationBoard}
      />

      <section className={styles.summaryBar} aria-label="오프라인 패키지 요약">
        <div>
          <span>사용 가능 단말</span>
          <strong>{summary.readyCount}대</strong>
        </div>
        <div>
          <span>재확인 필요 단말</span>
          <strong>{summary.warningCount}대</strong>
        </div>
        <div>
          <span>삭제된 패키지</span>
          <strong>{summary.purgedCount}대</strong>
        </div>
      </section>

      {isOffline ? (
        <div className={styles.offlineBanner} role="status">
          현재 브라우저가 오프라인입니다. 표시 중인 단말별 상태는 마지막 조회 결과일 수 있습니다.
        </div>
      ) : null}

      <div className={styles.contentStack}>
        <section className={styles.content} aria-label="단말별 적재 상태">
          <SectionTitle
            title="단말별 적재 상태"
            description="상황판 package_badge 슬롯 기준으로 각 PolicePhone의 현재 적재 상태를 표시합니다."
          />
          {isLoading ? (
            <PackageStatusSkeleton />
          ) : boardErrorMessage ? (
            <div className={styles.emptyState} role="alert">
              <strong>{boardErrorMessage}</strong>
              <span>상황판 package_badge 조회가 실패했습니다. 패키지 구성 목록은 별도로 확인할 수 있습니다.</span>
              <ActionButton label="다시 조회" onClick={() => void boardQuery.refetch()} />
            </div>
          ) : isEmpty ? (
            <div className={styles.emptyState}>
              <strong>표시할 단말별 적재 상태가 없습니다.</strong>
              <span>앱 단말이 패키지 적재 상태를 보고하면 이 영역에 표시됩니다.</span>
            </div>
          ) : (
            <div className={styles.tableShell}>
              <table className={styles.statusTable}>
                <thead>
                  <tr>
                    <th scope="col">단말</th>
                    <th scope="col">패키지 상태</th>
                    <th scope="col">계정</th>
                    <th scope="col">Manifest 버전</th>
                    <th scope="col">오프라인 사용</th>
                    <th scope="col">확인 사유</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => {
                    const statusView = getStatusView(row);

                    return (
                      <tr key={row.id}>
                        <td>
                          <strong>{createDeviceTitle(row)}</strong>
                          <span>{createDeviceMeta(row)}</span>
                        </td>
                        <td>
                          <StatusBadge status={statusView.label} tone={statusView.tone} />
                        </td>
                        <td>
                          <strong>{formatAccountType(row.accountType)}</strong>
                          <span>{formatIncidentRole(row.incidentRole)}</span>
                        </td>
                        <td>{formatManifestVersion(row)}</td>
                        <td>{row.readyForOfflineUse ? '사용 가능' : '패키지 미완료'}</td>
                        <td>{row.warningRaised ? formatWarningReason(row.warningReason) : '추가 확인 없음'}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <section className={styles.content} aria-label="패키지 구성 목록">
          <SectionTitle
            title="패키지 구성 목록"
            description="Manifest packageItems 기준으로 앱에 포함되어야 하는 사건 패키지 항목을 표시합니다."
          />
          <ManifestContent
            groups={manifestGroups}
            isError={manifestQuery.isError}
            isLoading={manifestQuery.isLoading}
            manifest={manifestQuery.data}
            tileSummary={tileSummary}
            onRetry={() => void manifestQuery.refetch()}
          />
        </section>
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

function ManifestContent({
  groups,
  isError,
  isLoading,
  manifest,
  tileSummary,
  onRetry,
}: {
  groups: readonly ManifestGroup[];
  isError: boolean;
  isLoading: boolean;
  manifest: OfflinePackageManifestResponse | undefined;
  tileSummary: TileSummary;
  onRetry: () => void;
}) {
  if (isLoading) {
    return <PackageStatusSkeleton />;
  }

  if (isError) {
    return (
      <div className={styles.emptyState} role="alert">
        <strong>패키지 구성 목록을 불러오지 못했습니다.</strong>
        <span>단말별 적재 상태와 별개로 manifest 조회만 실패했습니다.</span>
        <ActionButton label="구성 목록 다시 조회" onClick={onRetry} />
      </div>
    );
  }

  if (!manifest) {
    return (
      <div className={styles.emptyState}>
        <strong>패키지 구성 목록이 없습니다.</strong>
        <span>Manifest 조회 결과가 준비되면 구성 항목이 표시됩니다.</span>
      </div>
    );
  }

  return (
    <div className={styles.manifestStack}>
      <div className={styles.manifestMeta} aria-label="Manifest 메타">
        <div>
          <span>Manifest</span>
          <strong>v{manifest.manifestVersion}</strong>
        </div>
        <div>
          <span>만료 시각</span>
          <strong>{formatKstDateTime(new Date(manifest.expiresAt))}</strong>
        </div>
        <div>
          <span>패키지 Hash</span>
          <strong>{shortHash(manifest.packageHash)}</strong>
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
              <th scope="col">항목 수</th>
              <th scope="col">상태</th>
              <th scope="col">대표 itemKey</th>
              <th scope="col">sourceVersion</th>
              <th scope="col">sourceHash</th>
            </tr>
          </thead>
          <tbody>
            {groups.map((group) => (
              <tr key={group.type}>
                <td>
                  <strong>{group.label}</strong>
                  <span>{group.note}</span>
                </td>
                <td>{group.countLabel}</td>
                <td>
                  <StatusBadge status={group.statusLabel} tone={group.statusTone} />
                </td>
                <td>{group.itemKey}</td>
                <td>{group.sourceVersion}</td>
                <td>{group.sourceHash}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className={styles.tileSummary} aria-label="타일 목록 요약">
        <div>
          <span>타일 수</span>
          <strong>{tileSummary.count}개</strong>
        </div>
        <div>
          <span>총 용량</span>
          <strong>{formatBytes(tileSummary.totalBytes)}</strong>
        </div>
        <div>
          <span>styleId</span>
          <strong>{tileSummary.styleIds}</strong>
        </div>
        <div>
          <span>z 범위</span>
          <strong>{tileSummary.zRange}</strong>
        </div>
        <div>
          <span>x 범위</span>
          <strong>{tileSummary.xRange}</strong>
        </div>
        <div>
          <span>y 범위</span>
          <strong>{tileSummary.yRange}</strong>
        </div>
        <div>
          <span>checksum</span>
          <strong>{tileSummary.checksumLabel}</strong>
        </div>
      </div>
    </div>
  );
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
  const assignmentLabel = assignments.length > 0 ? `${assignments.length}개 배정` : '배정 없음';
  const status = incidentDetail?.status ?? 'OPEN';

  return {
    avatarLabel: createAvatarLabel(displayName),
    eyebrow: `${incidentId} / v${incidentDetail?.version ?? '-'}`,
    title: displayName ? `${displayName} 실종 사건` : `사건 ${incidentId}`,
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
  return {
    avatarLabel: '종료',
    eyebrow: `${incidentId} / v${incidentDetail?.version ?? '-'}`,
    title: '종료된 사건',
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
  return manifestGroupOrder.map((type) => {
    const items = manifest?.packageItems.filter((item) => item.itemType === type) ?? [];
    const primary = items[0] ?? null;
    const count = countManifestSourceItems(type, manifest);
    const statusView = getManifestGroupStatus(items);

    return {
      type,
      label: manifestGroupLabels[type],
      countLabel: `${count}개`,
      statusLabel: statusView.label,
      statusTone: statusView.tone,
      itemKey: primary?.itemKey ?? '-',
      sourceVersion: primary ? String(primary.sourceVersion) : '-',
      sourceHash: primary ? shortHash(primary.sourceHash) : '-',
      note: createManifestGroupNote(type, manifest, items),
    };
  });
}

function countManifestSourceItems(type: OfflinePackageItemType, manifest: OfflinePackageManifestResponse | undefined) {
  if (!manifest) return 0;

  if (type === 'OP_LIST') return manifest.operationalPeriods.length;
  if (type === 'ASSIGNED_AREA') return manifest.assignedAreas.length;
  if (type === 'INITIAL_MARKER') return manifest.initialMarkers.length;
  if (type === 'OVERALL_SEARCH_AREA') return manifest.overallSearchArea ? 1 : 0;
  if (type === 'TILE') return manifest.tileItems.length;
  if (type === 'MISSING_PERSON_CACHE') return manifest.missingPerson ? 1 : 0;
  return manifest.incident ? 1 : 0;
}

function createManifestGroupNote(
  type: OfflinePackageItemType,
  manifest: OfflinePackageManifestResponse | undefined,
  items: readonly OfflinePackagePackageItem[],
) {
  if (!manifest) return 'Manifest 조회 전';
  if (type === 'ASSIGNED_AREA' && manifest.assignedAreas.length === 0) return `${type} / 배정 구역 없음`;
  if (items.length === 0) return `${type} / packageItems 항목 없음`;
  return `${type} / ${items.length}개 packageItems 항목`;
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
      xRange: '-',
      yRange: '-',
      checksumLabel: '없음',
    };
  }

  return {
    count: tileItems.length,
    totalBytes: tileItems.reduce((sum, item) => sum + item.bytes, 0),
    styleIds: uniqueValues(tileItems.map((item) => item.styleId)).join(', '),
    zRange: createRangeLabel(tileItems.map((item) => item.z)),
    xRange: createRangeLabel(tileItems.map((item) => item.x)),
    yRange: createRangeLabel(tileItems.map((item) => item.y)),
    checksumLabel: tileItems.every((item) => Boolean(item.checksum)) ? '전체 있음' : '일부 없음',
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
  return row.accountName || row.policePhoneName || row.policePhoneCode || row.policePhoneId;
}

function createDeviceMeta(row: PackageBadgeRow) {
  const title = createDeviceTitle(row);
  const phoneName = row.policePhoneName && row.policePhoneName !== title ? row.policePhoneName : '';
  const phoneCode = row.policePhoneCode && row.policePhoneCode !== title ? row.policePhoneCode : '';
  return [phoneName, phoneCode, row.policePhoneId].filter(Boolean).join(' / ');
}

function formatAccountType(accountType: string) {
  if (accountType === 'TEAM') return '팀 단말';
  if (accountType === 'PATROL_CAR') return '순찰차 단말';
  if (accountType === 'COMMAND') return '지휘 계정';
  return accountType || '계정 미확인';
}

function formatIncidentRole(role: string) {
  if (role === 'MEMBER') return '수색 대원';
  if (role === 'FIELD_COMMANDER') return '현장 지휘';
  if (role === 'INCIDENT_COMMANDER') return '사건 지휘';
  return role || '역할 미확인';
}

function getStatusView(row: PackageBadgeRow): { label: string; tone: StatusBadgeTone } {
  if (row.packageStatus === 'PURGED') return { label: packageStatusLabels.PURGED, tone: 'closed' };
  if (row.packageStatus === 'READY' && row.readyForOfflineUse && !row.warningRaised) {
    return { label: packageStatusLabels.READY, tone: 'active' };
  }
  if (row.packageStatus === 'STALE') return { label: packageStatusLabels.STALE, tone: 'waiting' };
  if (row.packageStatus === 'FAILED') return { label: packageStatusLabels.FAILED, tone: 'danger' };
  if (row.packageStatus === 'PARTIAL') return { label: packageStatusLabels.PARTIAL, tone: 'waiting' };
  if (row.packageStatus === 'MISSING') return { label: packageStatusLabels.MISSING, tone: 'waiting' };

  return { label: row.packageStatus || '확인 전', tone: 'waiting' };
}

function formatManifestVersion(row: PackageBadgeRow) {
  if (row.packageStatus === 'PURGED') return '-';
  if (row.manifestVersion === null) return '확인 전';
  if (row.activeManifestVersion !== null && row.activeManifestVersion !== row.manifestVersion) {
    return `v${row.manifestVersion} / 최신 v${row.activeManifestVersion}`;
  }

  return `v${row.manifestVersion}`;
}

function formatWarningReason(reason: string) {
  if (reason === 'manifest_stale' || reason === 'stale_manifest') return '재적재 필요';
  if (reason === 'package_incomplete' || reason === 'partial') return '패키지 미완료';
  return reason || '패키지 미완료';
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
