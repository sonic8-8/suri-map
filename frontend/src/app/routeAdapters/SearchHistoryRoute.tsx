import { useCallback } from 'react';
import { useNavigate } from 'react-router-dom';

import type { LoginAccount } from '../../features/login/presentation/types/login';
import { useIncidentMarkerNotifications } from '../../features/markerNotifications/presentation/hooks/useIncidentMarkerNotifications';
import { SearchHistoryPage } from '../../features/searchHistory/presentation/pages/SearchHistoryPage';
import type { MarkerNotification } from '../../shared/ui';
import {
  ROUTES,
  getIncidentBoardPath,
  getIncidentDetailPath,
  getIncidentHandoverPath,
  getIncidentSearchHistoryPath,
} from '../routes';
import { useRouteIncidentId } from '../useRouteIncidentId';

export type SearchHistoryRouteProps = {
  currentUserAccount: LoginAccount;
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  onCloseMarkerNotifications: () => void;
  onMarkerNotification: (notification: MarkerNotification) => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  onOperationalPeriodCreated: (incidentId: string) => void;
  onOpenOfflinePackage: (incidentId: string) => void;
  onOpenLogin: () => void;
};

export function SearchHistoryRoute({
  currentUserAccount,
  markerNotificationIndex,
  markerNotifications,
  onCloseMarkerNotifications,
  onMarkerNotification,
  onMoveMarkerNotification,
  onOperationalPeriodCreated,
  onOpenOfflinePackage,
  onOpenLogin,
}: SearchHistoryRouteProps) {
  const incidentId = useRouteIncidentId();
  const navigate = useNavigate();
  const openIncidentListFromHistory = useCallback(() => navigate(ROUTES.incidentList, { replace: true }), [navigate]);

  useIncidentMarkerNotifications({
    incidentId,
    enabled: true,
    onNotification: onMarkerNotification,
  });

  return (
    <SearchHistoryPage
      incidentId={incidentId}
      currentUserAccount={currentUserAccount}
      markerNotificationIndex={markerNotificationIndex}
      markerNotifications={markerNotifications}
      onCloseMarkerNotifications={onCloseMarkerNotifications}
      onMoveMarkerNotification={onMoveMarkerNotification}
      onOpenIncidentList={() => navigate(ROUTES.incidentList)}
      onBrowserBackToIncidentList={openIncidentListFromHistory}
      onOpenIncidentDetail={() => navigate(getIncidentDetailPath(incidentId))}
      onOpenSituationBoard={() => navigate(getIncidentBoardPath(incidentId))}
      onOpenHandover={() => navigate(getIncidentHandoverPath(incidentId))}
      onOpenSearchHistory={() => navigate(getIncidentSearchHistoryPath(incidentId))}
      onOpenOfflinePackage={() => onOpenOfflinePackage(incidentId)}
      onOpenLogin={onOpenLogin}
      onOperationalPeriodCreated={() => onOperationalPeriodCreated(incidentId)}
    />
  );
}
