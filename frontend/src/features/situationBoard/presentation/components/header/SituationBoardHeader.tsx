import { SuriMapPageHeader, type MarkerNotification } from '../../../../../shared/ui';

type SituationBoardHeaderProps = {
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  onCloseMarkerNotifications: () => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  onOpenIncidentList: () => void;
  onOpenAreaEdit: () => void;
};

export function SituationBoardHeader({
  markerNotificationIndex,
  markerNotifications,
  onCloseMarkerNotifications,
  onMoveMarkerNotification,
  onOpenIncidentList,
  onOpenAreaEdit,
}: SituationBoardHeaderProps) {
  return (
    <SuriMapPageHeader
      activeTab="situationBoard"
      markerNotificationIndex={markerNotificationIndex}
      markerNotifications={markerNotifications}
      onCloseMarkerNotifications={onCloseMarkerNotifications}
      onMoveMarkerNotification={onMoveMarkerNotification}
      onOpenAreaEdit={onOpenAreaEdit}
      onOpenIncidentList={onOpenIncidentList}
    />
  );
}
