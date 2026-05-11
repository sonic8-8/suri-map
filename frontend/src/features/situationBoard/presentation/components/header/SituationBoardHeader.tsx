import {
  SuriMapPageHeader,
  type MarkerNotification,
  type SuriMapPageHeaderIncidentContext,
  type SuriMapPageHeaderTabId,
} from '../../../../../shared/ui';
import type { LoginAccount } from '../../../../login/presentation/types/login';
import type { IncidentDetailDto } from '../../../data/getIncidentDetail';
import type { SituationBoardResponseDto } from '../../../data/getSituationBoard';
import type { SituationBoardFallbackData } from '../../constants/mockSituationBoard';

type SituationBoardHeaderProps = {
  activeTab?: SuriMapPageHeaderTabId;
  apiBoard: SituationBoardResponseDto | null;
  board: SituationBoardFallbackData;
  currentUserAccount: LoginAccount;
  incidentDetail: IncidentDetailDto | null;
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  onCloseMarkerNotifications: () => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  onOpenIncidentList: () => void;
  onOpenSituationBoard?: () => void;
  onOpenHandover: () => void;
};

export function SituationBoardHeader({
  activeTab = 'situationBoard',
  apiBoard,
  board,
  currentUserAccount,
  incidentDetail,
  markerNotificationIndex,
  markerNotifications,
  onCloseMarkerNotifications,
  onMoveMarkerNotification,
  onOpenIncidentList,
  onOpenSituationBoard,
  onOpenHandover,
}: SituationBoardHeaderProps) {
  const currentAccountLabel = `${currentUserAccount.name} / ${currentUserAccount.organization}`;
  const timestampLabel = apiBoard?.serverTs ? formatKstDateTime(new Date(apiBoard.serverTs)) : '동기화 전';
  const incidentContext = createIncidentContext(board, incidentDetail);

  return (
    <SuriMapPageHeader
      activeTab={activeTab}
      currentAccountLabel={currentAccountLabel}
      incidentContext={incidentContext}
      markerNotificationIndex={markerNotificationIndex}
      markerNotifications={markerNotifications}
      timestampLabel={timestampLabel}
      onCloseMarkerNotifications={onCloseMarkerNotifications}
      onMoveMarkerNotification={onMoveMarkerNotification}
      onOpenHandover={onOpenHandover}
      onOpenIncidentList={onOpenIncidentList}
      onOpenSituationBoard={onOpenSituationBoard}
    />
  );
}

function createIncidentContext(
  board: SituationBoardFallbackData,
  incidentDetail: IncidentDetailDto | null,
): SuriMapPageHeaderIncidentContext {
  const missingPerson = incidentDetail && 'missingPerson' in incidentDetail ? incidentDetail.missingPerson : null;
  const assignments = incidentDetail && 'assignments' in incidentDetail ? incidentDetail.assignments : [];
  const status = incidentDetail?.status ?? 'OPEN';
  const activeOperationalPeriod = board.operationalPeriods.find((period) => period.state === 'current');
  const activeOperationalPeriodLabel = activeOperationalPeriod?.label ?? 'OP 정보 없음';
  const displayName = missingPerson?.displayName?.trim() || null;
  const lastSeenLabel = createLastSeenLabel(missingPerson?.lastSeenAt ?? null, missingPerson?.lastSeenLocationText ?? null);
  const assignmentLabel =
    assignments.length > 0
      ? `${assignments.length}개 계정`
      : '배정 계정 없음';

  return {
    avatarLabel: createAvatarLabel(displayName),
    eyebrow: `${board.incidentId} · v${incidentDetail?.version ?? '-'}`,
    title: displayName ? `${displayName} 실종 사건` : `사건 ${board.incidentId}`,
    metrics: [
      { label: '실종자', value: displayName ?? '-' },
      { label: '마지막 목격', value: lastSeenLabel },
      { label: '배정 계정', value: assignmentLabel },
    ],
    statusLabel: `${getIncidentStatusLabel(status)} · ${activeOperationalPeriodLabel}`,
  };
}

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
