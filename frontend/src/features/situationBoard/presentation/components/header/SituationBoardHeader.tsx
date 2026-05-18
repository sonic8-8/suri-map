import {
  formatIncidentContextEyebrow,
  formatMissingPersonIncidentTitle,
  SuriMapPageHeader,
  type MarkerNotification,
  type SuriMapPageHeaderIncidentContext,
  type SuriMapPageHeaderSyncStatus,
  type SuriMapPageHeaderTabId,
} from '../../../../../shared/ui';
import type { LoginAccount } from '../../../../login/presentation/types/login';
import type { IncidentDetailDto } from '../../../data/getIncidentDetail';
import type { SituationBoardResponseDto } from '../../../data/getSituationBoard';
import type { SituationBoardFallbackData } from '../../constants/mockSituationBoard';
import {
  isIncidentTerminalClosed,
  type IncidentTerminalViewModel,
} from '../../utils/incidentTerminalBoardMapper';

type SituationBoardHeaderProps = {
  activeTab?: SuriMapPageHeaderTabId;
  apiBoard: SituationBoardResponseDto | null;
  board: SituationBoardFallbackData;
  currentUserAccount: LoginAccount;
  incidentDetail: IncidentDetailDto | null;
  incidentTerminal: IncidentTerminalViewModel | null;
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  syncStatus?: SuriMapPageHeaderSyncStatus | null;
  onCloseMarkerNotifications: () => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  onOpenIncidentList: () => void;
  onOpenIncidentDetail?: () => void;
  onOpenSituationBoard?: () => void;
  onOpenHandover?: () => void;
  onOpenOfflinePackage?: () => void;
};

export function SituationBoardHeader({
  activeTab = 'situationBoard',
  apiBoard,
  board,
  currentUserAccount,
  incidentDetail,
  incidentTerminal,
  markerNotificationIndex,
  markerNotifications,
  syncStatus,
  onCloseMarkerNotifications,
  onMoveMarkerNotification,
  onOpenIncidentList,
  onOpenIncidentDetail,
  onOpenSituationBoard,
  onOpenHandover,
  onOpenOfflinePackage,
}: SituationBoardHeaderProps) {
  const currentAccountLabel = currentUserAccount.name;
  const timestampLabel = apiBoard?.serverTs ? formatKstDateTime(new Date(apiBoard.serverTs)) : '동기화 전';
  const incidentContext = createIncidentContext(board, incidentDetail, incidentTerminal);

  return (
    <SuriMapPageHeader
      activeTab={activeTab}
      currentAccountLabel={currentAccountLabel}
      incidentContext={incidentContext}
      markerNotificationIndex={markerNotificationIndex}
      markerNotifications={markerNotifications}
      syncStatus={syncStatus}
      timestampLabel={timestampLabel}
      onCloseMarkerNotifications={onCloseMarkerNotifications}
      onMoveMarkerNotification={onMoveMarkerNotification}
      onOpenHandover={onOpenHandover}
      onOpenIncidentDetail={onOpenIncidentDetail}
      onOpenIncidentList={onOpenIncidentList}
      onOpenOfflinePackage={onOpenOfflinePackage}
      onOpenSituationBoard={onOpenSituationBoard}
    />
  );
}

function createIncidentContext(
  board: SituationBoardFallbackData,
  incidentDetail: IncidentDetailDto | null,
  incidentTerminal: IncidentTerminalViewModel | null,
): SuriMapPageHeaderIncidentContext {
  if (isIncidentTerminalClosed(incidentTerminal)) {
    return createTerminalIncidentContext(board, incidentDetail, incidentTerminal);
  }

  const missingPerson = incidentDetail && 'missingPerson' in incidentDetail ? incidentDetail.missingPerson : null;
  const assignments = incidentDetail && 'assignments' in incidentDetail ? incidentDetail.assignments : [];
  const status = incidentDetail?.status ?? 'OPEN';
  const activeOperationalPeriod = board.operationalPeriods.find((period) => period.state === 'current');
  const activeOperationalPeriodLabel = activeOperationalPeriod?.label ?? 'OP 정보 없음';
  const displayName = missingPerson?.displayName?.trim() || null;
  const lastSeenLabel = createLastSeenLabel(missingPerson?.lastSeenAt ?? null, missingPerson?.lastSeenLocationText ?? null);
  const assignmentLabel =
    incidentDetail && 'assignments' in incidentDetail
      ? assignments.length > 0
        ? `${assignments.length}개 계정`
        : '배정 계정 없음'
      : '-개';

  return {
    avatarLabel: createAvatarLabel(displayName),
    eyebrow: formatIncidentContextEyebrow(incidentDetail?.version),
    title: formatMissingPersonIncidentTitle(displayName),
    metrics: [
      { label: '실종자', value: displayName ?? '-' },
      { label: '마지막 목격', value: lastSeenLabel },
      { label: '배정 계정', value: assignmentLabel },
    ],
    statusLabel: `${getIncidentStatusLabel(status)} · ${activeOperationalPeriodLabel}`,
    statusTone: 'active',
  };
}

function createTerminalIncidentContext(
  _board: SituationBoardFallbackData,
  incidentDetail: IncidentDetailDto | null,
  incidentTerminal: IncidentTerminalViewModel,
): SuriMapPageHeaderIncidentContext {
  return {
    avatarLabel: '종료',
    eyebrow: formatIncidentContextEyebrow(incidentDetail?.version),
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
    statusLabel: terminalStatusLabels[incidentTerminal.terminalStatus],
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
  if (!displayName) {
    return '사건';
  }

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

function getIncidentStatusLabel(status: string) {
  if (status === 'CLOSED') {
    return '종료';
  }

  return '진행 중';
}

function formatKstDateTime(date: Date) {
  if (Number.isNaN(date.getTime())) {
    return '-';
  }

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
