import { lazy, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';

import type { LoginAccount } from '../../features/login/presentation/types/login';
import { useIncidentMarkerNotifications } from '../../features/markerNotifications/presentation/hooks/useIncidentMarkerNotifications';
import type { MarkerNotification } from '../../shared/ui';
import {
  ROUTES,
  getIncidentBoardPath,
  getIncidentClosePath,
  getIncidentHandoverPath,
  getIncidentSearchHistoryPath,
} from '../routes';
import { useRouteIncidentId } from '../useRouteIncidentId';

const IncidentDetailPage = lazy(() =>
  import('../../features/incident/presentation/pages/IncidentDetailPage').then((module) => ({
    default: module.IncidentDetailPage,
  })),
);

export type IncidentDetailRouteProps = {
  currentUserAccount: LoginAccount;
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  onCloseMarkerNotifications: () => void;
  onMarkerNotification: (notification: MarkerNotification) => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  onOpenOfflinePackage: (incidentId: string) => void;
  onOpenLogin: () => void;
};

export function IncidentDetailRoute({
  currentUserAccount,
  markerNotificationIndex,
  markerNotifications,
  onCloseMarkerNotifications,
  onMarkerNotification,
  onMoveMarkerNotification,
  onOpenOfflinePackage,
  onOpenLogin,
}: IncidentDetailRouteProps) {
  const incidentId = useRouteIncidentId();
  const navigate = useNavigate();
  const openIncidentListFromHistory = useCallback(() => navigate(ROUTES.incidentList, { replace: true }), [navigate]);

  useIncidentMarkerNotifications({
    incidentId,
    enabled: true,
    onNotification: onMarkerNotification,
  });

  return (
    <IncidentDetailPage
      currentUserAccount={currentUserAccount}
      incidentId={incidentId}
      markerNotificationIndex={markerNotificationIndex}
      markerNotifications={markerNotifications}
      onCloseMarkerNotifications={onCloseMarkerNotifications}
      onMoveMarkerNotification={onMoveMarkerNotification}
      onOpenHandover={() => navigate(getIncidentHandoverPath(incidentId))}
      onOpenSearchHistory={() => navigate(getIncidentSearchHistoryPath(incidentId))}
      onOpenIncidentList={() => navigate(ROUTES.incidentList)}
      onBrowserBackToIncidentList={openIncidentListFromHistory}
      onOpenOfflinePackage={() => onOpenOfflinePackage(incidentId)}
      onOpenSituationBoard={() => navigate(getIncidentBoardPath(incidentId))}
      onOpenIncidentClose={() => navigate(getIncidentClosePath(incidentId))}
      onOpenLogin={onOpenLogin}
    />
  );
}
