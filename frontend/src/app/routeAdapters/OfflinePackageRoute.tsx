import { useCallback } from 'react';
import { useNavigate } from 'react-router-dom';

import type { LoginAccount } from '../../features/login/presentation/types/login';
import { useIncidentMarkerNotifications } from '../../features/markerNotifications/presentation/hooks/useIncidentMarkerNotifications';
import { OfflinePackageStatusPage } from '../../features/offlinePackage/presentation/pages/OfflinePackageStatusPage';
import type { MarkerNotification } from '../../shared/ui';
import {
  ROUTES,
  getIncidentBoardPath,
  getIncidentDetailPath,
  getIncidentHandoverPath,
  getIncidentSearchHistoryPath,
} from '../routes';
import { useRouteIncidentId } from '../useRouteIncidentId';

export type OfflinePackageRouteProps = {
  currentUserAccount: LoginAccount;
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  onCloseMarkerNotifications: () => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  onMarkerNotification: (notification: MarkerNotification) => void;
  onOpenOfflinePackage: (incidentId: string) => void;
  onOpenLogin: () => void;
};

export function OfflinePackageRoute({
  currentUserAccount,
  markerNotificationIndex,
  markerNotifications,
  onCloseMarkerNotifications,
  onMoveMarkerNotification,
  onMarkerNotification,
  onOpenOfflinePackage,
  onOpenLogin,
}: OfflinePackageRouteProps) {
  const incidentId = useRouteIncidentId();
  const navigate = useNavigate();
  const openIncidentListFromHistory = useCallback(() => navigate(ROUTES.incidentList, { replace: true }), [navigate]);

  useIncidentMarkerNotifications({
    incidentId,
    enabled: true,
    onNotification: onMarkerNotification,
  });

  return (
    <OfflinePackageStatusPage
      currentUserAccount={currentUserAccount}
      incidentId={incidentId}
      markerNotificationIndex={markerNotificationIndex}
      markerNotifications={markerNotifications}
      onBackToSituationBoard={() => navigate(getIncidentBoardPath(incidentId))}
      onCloseMarkerNotifications={onCloseMarkerNotifications}
      onMoveMarkerNotification={onMoveMarkerNotification}
      onOpenHandover={() => navigate(getIncidentHandoverPath(incidentId))}
      onOpenSearchHistory={() => navigate(getIncidentSearchHistoryPath(incidentId))}
      onOpenIncidentDetail={() => navigate(getIncidentDetailPath(incidentId))}
      onOpenIncidentList={() => navigate(ROUTES.incidentList)}
      onBrowserBackToIncidentList={openIncidentListFromHistory}
      onOpenOfflinePackage={() => onOpenOfflinePackage(incidentId)}
      onOpenLogin={onOpenLogin}
    />
  );
}
