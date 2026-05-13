import { useEffect, useMemo, useState } from 'react';

import { getIncidentDetail, type IncidentDetailDto } from '../../../situationBoard/data/getIncidentDetail';
import type { SituationBoardResponseDto } from '../../../situationBoard/data/getSituationBoard';
import {
  isIncidentTerminalClosed,
  toIncidentTerminal,
  type IncidentTerminalViewModel,
} from '../../../situationBoard/presentation/utils/incidentTerminalBoardMapper';
import { useIncidentBoardQuery } from '../../../board/api/incidentBoardApi';
import type { LoginAccount } from '../../../login/presentation/types/login';
import {
  ActionButton,
  StatusBadge,
  SuriMapPageHeader,
  type MarkerNotification,
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
  const [isOffline, setIsOffline] = useState(() => !navigator.onLine);

  const boardQuery = useIncidentBoardQuery({ incidentId, includeSlots: ['package_badge', 'incident_terminal'] });
  const rows = useMemo(() => readPackageBadgeRows(boardQuery.data?.slots.package_badge), [boardQuery.data]);
  const incidentTerminal = useMemo(
    () => (boardQuery.data ? toIncidentTerminal(boardQuery.data as unknown as SituationBoardResponseDto) : null),
    [boardQuery.data],
  );
  const serverTs = boardQuery.data?.serverTs ?? null;
  const isLoading = boardQuery.isLoading;
  const errorMessage = boardQuery.isError ? '오프라인 패키지 상태를 불러오지 못했습니다.' : '';

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
  const isEmpty = !isLoading && !errorMessage && rows.length === 0;

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
          <span>준비 완료</span>
          <strong>{summary.readyCount}대</strong>
        </div>
        <div>
          <span>확인 필요</span>
          <strong>{summary.warningCount}대</strong>
        </div>
        <div>
          <span>파기됨</span>
          <strong>{summary.purgedCount}대</strong>
        </div>
      </section>

      {isOffline ? (
        <div className={styles.offlineBanner} role="status">
          오프라인 - 마지막 상태를 확인하세요.
        </div>
      ) : null}

      <section className={styles.content} aria-label="오프라인 패키지 상태 목록">
        {isLoading ? (
          <PackageStatusSkeleton />
        ) : errorMessage ? (
          <div className={styles.emptyState} role="alert">
            <strong>{errorMessage}</strong>
            <span>네트워크 상태와 API 응답을 확인한 뒤 다시 시도하세요.</span>
            <ActionButton label="다시 시도" onClick={() => void boardQuery.refetch()} />
          </div>
        ) : isEmpty ? (
          <div className={styles.emptyState}>
            <strong>배정된 업무폰 패키지 상태가 없습니다.</strong>
            <span>팀 업무폰 또는 순찰차 업무폰이 오프라인 패키지 적재 상태를 보고하면 이곳에 표시됩니다.</span>
          </div>
        ) : (
          <div className={styles.tableShell}>
            <table className={styles.statusTable}>
              <thead>
                <tr>
                  <th scope="col">업무폰</th>
                  <th scope="col">패키지 상태</th>
                  <th scope="col">유형</th>
                  <th scope="col">패키지 버전</th>
                  <th scope="col">오프라인 사용</th>
                  <th scope="col">확인 항목</th>
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
                      <td>{row.readyForOfflineUse ? '사용 가능' : '확인 필요'}</td>
                      <td>{row.warningRaised ? row.warningReason : '필수 항목 정상'}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </main>
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
  const lastSeenLabel = createLastSeenLabel(missingPerson?.lastSeenAt ?? null, missingPerson?.lastSeenLocationText ?? null);
  const assignmentLabel = assignments.length > 0 ? `${assignments.length}개 계정` : '배정 계정 없음';
  const status = incidentDetail?.status ?? 'OPEN';

  return {
    avatarLabel: createAvatarLabel(displayName),
    eyebrow: `${incidentId} · v${incidentDetail?.version ?? '-'}`,
    title: displayName ? `${displayName} 실종 사건` : `사건 ${incidentId}`,
    metrics: [
      { label: '실종자', value: displayName ?? '-' },
      { label: '마지막 목격', value: lastSeenLabel },
      { label: '배정 계정', value: assignmentLabel },
    ],
    statusLabel: `${status === 'CLOSED' ? '종료' : '진행 중'} · 오프라인 패키지`,
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
    eyebrow: `${incidentId} · v${incidentDetail?.version ?? '-'}`,
    title: '종료된 사건',
    metrics: [
      {
        label: '종료 시각',
        value: incidentTerminal.closedAt ? formatKstDateTime(new Date(incidentTerminal.closedAt)) : '-',
      },
      {
        label: '쓰기 상태',
        value: writeDisabledReasonLabels[incidentTerminal.writeDisabledReason],
      },
      {
        label: '로컬 정리',
        value: localPurgeStateLabels[incidentTerminal.localPurgeState],
      },
    ],
    statusLabel: `${terminalStatusLabels[incidentTerminal.terminalStatus]} · 오프라인 패키지`,
    statusTone: 'terminal',
  };
}

const terminalStatusLabels: Record<IncidentTerminalViewModel['terminalStatus'], string> = {
  OPEN: '진행 중',
  CLOSED: '종료',
  PURGE_PENDING: '파기 대기',
  PURGED: '파기 완료',
};

const writeDisabledReasonLabels: Record<IncidentTerminalViewModel['writeDisabledReason'], string> = {
  none: '제한 없음',
  incident_closed: '사건 종료로 쓰기 불가',
  purged: '데이터 파기 완료로 쓰기 불가',
};

const localPurgeStateLabels: Record<IncidentTerminalViewModel['localPurgeState'], string> = {
  not_started: '정리 시작 전',
  queued: '정리 대기',
  in_progress: '정리 진행 중',
  completed: '정리 완료',
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
    return `${timeLabel} · ${locationLabel}`;
  }

  return timeLabel ?? locationLabel ?? '-';
}

function PackageStatusSkeleton() {
  return (
    <div className={styles.skeletonList} aria-label="오프라인 패키지 상태 불러오는 중">
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
    warningReason: readString(localWarningInput, 'reason') ?? '상태 확인이 필요합니다.',
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

function createDeviceTitle(row: PackageBadgeRow) {
  return row.accountName || row.policePhoneName || row.policePhoneCode || row.policePhoneId;
}

function createDeviceMeta(row: PackageBadgeRow) {
  const title = createDeviceTitle(row);
  const phoneName = row.policePhoneName && row.policePhoneName !== title ? row.policePhoneName : '';
  const phoneCode = row.policePhoneCode && row.policePhoneCode !== title ? row.policePhoneCode : '';
  return [phoneName, phoneCode, row.policePhoneId].filter(Boolean).join(' · ');
}

function formatAccountType(accountType: string) {
  if (accountType === 'TEAM') return '팀 업무폰';
  if (accountType === 'PATROL_CAR') return '순찰차 업무폰';
  if (accountType === 'COMMAND') return '지휘 업무폰';
  return accountType || '미확인 단말';
}

function formatIncidentRole(role: string) {
  if (role === 'MEMBER') return '수색대원';
  if (role === 'FIELD_COMMANDER') return '현장지휘관';
  if (role === 'INCIDENT_COMMANDER') return '사건지휘관';
  return role || '역할 미지정';
}

function getStatusView(row: PackageBadgeRow): { label: string; tone: 'active' | 'waiting' | 'closed' } {
  if (row.packageStatus === 'PURGED') return { label: '파기됨', tone: 'closed' };
  if (row.packageStatus === 'READY' && row.readyForOfflineUse && !row.warningRaised) {
    return { label: '준비 완료', tone: 'active' };
  }
  if (row.packageStatus === 'STALE') return { label: '갱신 필요', tone: 'waiting' };
  if (row.packageStatus === 'FAILED') return { label: '일부 실패', tone: 'waiting' };
  if (row.packageStatus === 'PARTIAL') return { label: '일부 적재', tone: 'waiting' };
  if (row.packageStatus === 'MISSING') return { label: '상태 없음', tone: 'waiting' };

  return { label: row.packageStatus, tone: 'waiting' };
}

function formatManifestVersion(row: PackageBadgeRow) {
  if (row.packageStatus === 'PURGED') return '-';
  if (row.manifestVersion === null) return '확인 전';
  if (row.activeManifestVersion !== null && row.activeManifestVersion !== row.manifestVersion) {
    return `v${row.manifestVersion} / 최신 v${row.activeManifestVersion}`;
  }

  return `v${row.manifestVersion}`;
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
