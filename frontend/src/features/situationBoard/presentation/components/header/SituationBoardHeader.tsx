import {
  SuriMapPageHeader,
  createSharedIncidentContext,
  type MarkerNotification,
  type SuriMapPageHeaderSyncStatus,
  type SuriMapPageHeaderTabId,
} from '../../../../../shared/ui';
import type { LoginAccount } from '../../../../login/presentation/types/login';
import type { IncidentDetailDto } from '../../../data/getIncidentDetail';
import type { SituationBoardResponseDto } from '../../../data/getSituationBoard';
import { toPackageBadgeSummary } from '../../utils/packageBadgeBoardMapper';

type SituationBoardHeaderProps = {
  activeTab?: SuriMapPageHeaderTabId;
  apiBoard: SituationBoardResponseDto | null;
  activeOperationalPeriodLabel?: string | null;
  currentUserAccount: LoginAccount;
  incidentDetail: IncidentDetailDto | null;
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  syncStatus?: SuriMapPageHeaderSyncStatus | null;
  onCloseMarkerNotifications: () => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  onOpenIncidentList: () => void;
  onOpenIncidentDetail?: () => void;
  onOpenSituationBoard?: () => void;
  onOpenHandover?: () => void;
  onOpenSearchHistory?: () => void;
  onOpenOfflinePackage?: () => void;
  onOpenLogin?: () => void;
};

export function SituationBoardHeader({
  activeTab = 'situationBoard',
  apiBoard,
  activeOperationalPeriodLabel,
  currentUserAccount,
  incidentDetail,
  markerNotificationIndex,
  markerNotifications,
  syncStatus,
  onCloseMarkerNotifications,
  onMoveMarkerNotification,
  onOpenIncidentList,
  onOpenIncidentDetail,
  onOpenSituationBoard,
  onOpenHandover,
  onOpenSearchHistory,
  onOpenOfflinePackage,
  onOpenLogin,
}: SituationBoardHeaderProps) {
  const currentAccountLabel = currentUserAccount.name;
  const timestampLabel = apiBoard?.serverTs ? formatKstDateTime(new Date(apiBoard.serverTs)) : '동기화 전';
  const incidentContext = createSharedIncidentContext({
    ...(incidentDetail ?? {}),
    activeOperationalPeriodLabel,
  });
  const packageBadgeSummary = toPackageBadgeSummary(apiBoard);

  return (
    <SuriMapPageHeader
      activeTab={activeTab}
      currentAccountLabel={currentAccountLabel}
      incidentContext={incidentContext}
      markerNotificationIndex={markerNotificationIndex}
      markerNotifications={markerNotifications}
      packageWarning={packageBadgeSummary}
      syncStatus={syncStatus}
      timestampLabel={timestampLabel}
      onCloseMarkerNotifications={onCloseMarkerNotifications}
      onMoveMarkerNotification={onMoveMarkerNotification}
      onOpenHandover={onOpenHandover}
      onOpenSearchHistory={onOpenSearchHistory}
      onOpenIncidentDetail={onOpenIncidentDetail}
      onOpenIncidentList={onOpenIncidentList}
      onOpenOfflinePackage={onOpenOfflinePackage}
      onOpenLogin={onOpenLogin}
      onOpenSituationBoard={onOpenSituationBoard}
    />
  );
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

  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}`;
}
