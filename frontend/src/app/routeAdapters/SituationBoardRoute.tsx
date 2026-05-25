import { useCallback } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import type { LoginAccount } from '../../features/login/presentation/types/login';
import { useIncidentMarkerNotifications } from '../../features/markerNotifications/presentation/hooks/useIncidentMarkerNotifications';
import { SituationBoardPage } from '../../features/situationBoard/presentation/pages/SituationBoardPage';
import type { CompletedAreaDraft } from '../../shared/model/areaDraft';
import type { MarkerNotification } from '../../shared/ui';
import {
  ROUTES,
  getAreaEditPath,
  getIncidentBoardPath,
  getIncidentDetailPath,
  getIncidentOfflinePackagePath,
  getIncidentSearchHistoryPath,
} from '../routes';
import { useRouteIncidentId } from '../useRouteIncidentId';

export type SituationBoardRouteProps = {
  currentUserAccount: LoginAccount;
  workspace?: 'board' | 'area' | 'handover';
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  onCloseMarkerNotifications: () => void;
  onMarkerNotification: (notification: MarkerNotification) => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  onSaveAssignedAreas: (incidentId: string, drafts: CompletedAreaDraft[]) => void;
  savedAreaDraftsByIncidentId: Record<string, CompletedAreaDraft[]>;
  opRefreshVersionByIncidentId: Record<string, number>;
  onOpenLogin: () => void;
};

function readInitialSplitParentAreaId(state: unknown) {
  if (typeof state !== 'object' || state === null || !('splitParentAreaId' in state)) {
    return null;
  }

  const splitParentAreaId = (state as { splitParentAreaId?: unknown }).splitParentAreaId;
  return typeof splitParentAreaId === 'string' && splitParentAreaId.trim() ? splitParentAreaId : null;
}

export function SituationBoardRoute({
  currentUserAccount,
  workspace = 'board',
  markerNotificationIndex,
  markerNotifications,
  onCloseMarkerNotifications,
  onMarkerNotification,
  onMoveMarkerNotification,
  onSaveAssignedAreas,
  savedAreaDraftsByIncidentId,
  opRefreshVersionByIncidentId,
  onOpenLogin,
}: SituationBoardRouteProps) {
  const incidentId = useRouteIncidentId();
  const navigate = useNavigate();
  const location = useLocation();
  const openIncidentListFromHistory = useCallback(() => navigate(ROUTES.incidentList, { replace: true }), [navigate]);
  const savedAreaDrafts = savedAreaDraftsByIncidentId[incidentId] ?? [];
  const refreshVersion = opRefreshVersionByIncidentId[incidentId] ?? 0;
  const initialSplitParentAreaId = readInitialSplitParentAreaId(location.state);

  useIncidentMarkerNotifications({
    incidentId,
    enabled: true,
    onNotification: onMarkerNotification,
  });

  return (
    <SituationBoardPage
      incidentId={incidentId}
      currentUserAccount={currentUserAccount}
      isAreaWorkspaceRoute={workspace === 'area'}
      isHandoverWorkspaceRoute={workspace === 'handover'}
      initialSplitParentAreaId={initialSplitParentAreaId}
      markerNotificationIndex={markerNotificationIndex}
      markerNotifications={markerNotifications}
      onCloseMarkerNotifications={onCloseMarkerNotifications}
      onMoveMarkerNotification={onMoveMarkerNotification}
      onCloseAreaWorkspaceRoute={() => navigate(getIncidentBoardPath(incidentId))}
      onCloseHandoverWorkspaceRoute={() => navigate(getIncidentBoardPath(incidentId))}
      onOpenAreaWorkspaceRoute={(splitParentAreaId) =>
        navigate(getAreaEditPath(incidentId), {
          state: splitParentAreaId ? { splitParentAreaId } : null,
        })
      }
      onOpenIncidentDetail={() => navigate(getIncidentDetailPath(incidentId))}
      onOpenSearchHistory={() => navigate(getIncidentSearchHistoryPath(incidentId))}
      onOpenOfflinePackage={() => navigate(getIncidentOfflinePackagePath(incidentId))}
      onOpenLogin={onOpenLogin}
      onSaveAssignedAreas={(drafts) => onSaveAssignedAreas(incidentId, drafts)}
      savedAreaDrafts={savedAreaDrafts}
      refreshVersion={refreshVersion}
      onOpenIncidentList={() => navigate(ROUTES.incidentList)}
      onBrowserBackToIncidentList={openIncidentListFromHistory}
    />
  );
}
