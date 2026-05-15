import { useCallback, useEffect, useState } from 'react';
import { Navigate, Route, Routes, useNavigate, useParams } from 'react-router-dom';

import { HandoverPage } from '../features/handover/presentation/pages/HandoverPage';
import { IncidentClosePage } from '../features/incidentClose/presentation/pages/IncidentClosePage';
import { IncidentListPage } from '../features/incidents/presentation/pages/IncidentListPage';
import { clearLoginSession, logoutCurrentSession, readStoredLoginAccount } from '../features/login/data/login';
import { LoginPage } from '../features/login/presentation/pages/LoginPage';
import type { LoginAccount } from '../features/login/presentation/types/login';
import { useIncidentMarkerNotifications } from '../features/markerNotifications/presentation/hooks/useIncidentMarkerNotifications';
import { OfflinePackageStatusPage } from '../features/offlinePackage/presentation/pages/OfflinePackageStatusPage';
import { SituationBoardPage } from '../features/situationBoard/presentation/pages/SituationBoardPage';
import type { CompletedAreaDraft } from '../shared/model/areaDraft';
import type { MarkerNotification } from '../shared/ui';
import { API_UNAUTHORIZED_EVENT } from '../shared/api/client';
import {
  BOOTSTRAP_INCIDENT_ID,
  getAreaEditPath,
  getIncidentHandoverPath,
  getIncidentBoardPath,
  getIncidentClosePath,
  getIncidentOfflinePackagePath,
  ROUTES,
} from './routes';

/*
  {
    id: 'marker-notification-clue-001',
    title: '신규 마커 수신',
    markerType: '단서',
    reporter: '기동대 1부대 A팀',
    areaLabel: '북측 능선 구역',
    receivedAtLabel: '14:48',
    coordinateLabel: '35.16N · 126.91E',
  },
  {
    id: 'marker-notification-support-001',
    title: '신규 마커 수신',
    markerType: '지원 요청',
    reporter: '광주 북구 지구대 폴리폰',
    areaLabel: '진입로 하단',
    receivedAtLabel: '14:52',
    coordinateLabel: '35.14N · 126.98E',
  },
*/

function useRouteIncidentId() {
  const { incidentId } = useParams();
  return incidentId ?? BOOTSTRAP_INCIDENT_ID;
}

type SituationBoardRouteProps = {
  currentUserAccount: LoginAccount;
  workspace?: 'board' | 'area';
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  onCloseMarkerNotifications: () => void;
  onMarkerNotification: (notification: MarkerNotification) => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  onSaveAssignedAreas: (incidentId: string, drafts: CompletedAreaDraft[]) => void;
  savedAreaDraftsByIncidentId: Record<string, CompletedAreaDraft[]>;
  opRefreshVersionByIncidentId: Record<string, number>;
};

function SituationBoardRoute({
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
}: SituationBoardRouteProps) {
  const incidentId = useRouteIncidentId();
  const navigate = useNavigate();
  const savedAreaDrafts = savedAreaDraftsByIncidentId[incidentId] ?? [];
  const refreshVersion = opRefreshVersionByIncidentId[incidentId] ?? 0;

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
      markerNotificationIndex={markerNotificationIndex}
      markerNotifications={markerNotifications}
      onCloseMarkerNotifications={onCloseMarkerNotifications}
      onMoveMarkerNotification={onMoveMarkerNotification}
      onCloseAreaWorkspaceRoute={() => navigate(getIncidentBoardPath(incidentId))}
      onOpenAreaWorkspaceRoute={() => navigate(getAreaEditPath(incidentId))}
      onOpenOfflinePackage={() => navigate(getIncidentOfflinePackagePath(incidentId))}
      onSaveAssignedAreas={(drafts) => onSaveAssignedAreas(incidentId, drafts)}
      savedAreaDrafts={savedAreaDrafts}
      refreshVersion={refreshVersion}
      onOpenIncidentList={() => navigate(ROUTES.incidentList)}
    />
  );
}

type HandoverRouteProps = {
  currentUserAccount: LoginAccount;
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  onCloseMarkerNotifications: () => void;
  onMarkerNotification: (notification: MarkerNotification) => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  onOperationalPeriodCreated: (incidentId: string) => void;
  onOpenOfflinePackage: (incidentId: string) => void;
};

function HandoverRoute({
  currentUserAccount,
  markerNotificationIndex,
  markerNotifications,
  onCloseMarkerNotifications,
  onMarkerNotification,
  onMoveMarkerNotification,
  onOperationalPeriodCreated,
  onOpenOfflinePackage,
}: HandoverRouteProps) {
  const incidentId = useRouteIncidentId();
  const navigate = useNavigate();
  useIncidentMarkerNotifications({
    incidentId,
    enabled: true,
    onNotification: onMarkerNotification,
  });

  return (
    <HandoverPage
      incidentId={incidentId}
      currentUserAccount={currentUserAccount}
      markerNotificationIndex={markerNotificationIndex}
      markerNotifications={markerNotifications}
      onCloseMarkerNotifications={onCloseMarkerNotifications}
      onMoveMarkerNotification={onMoveMarkerNotification}
      onOpenIncidentList={() => navigate(ROUTES.incidentList)}
      onOpenSituationBoard={() => navigate(getIncidentBoardPath(incidentId))}
      onOpenOfflinePackage={() => onOpenOfflinePackage(incidentId)}
      onOperationalPeriodCreated={() => onOperationalPeriodCreated(incidentId)}
    />
  );
}

type OfflinePackageRouteProps = {
  currentUserAccount: LoginAccount;
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  onCloseMarkerNotifications: () => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  onMarkerNotification: (notification: MarkerNotification) => void;
  onOpenOfflinePackage: (incidentId: string) => void;
};

function OfflinePackageRoute({
  currentUserAccount,
  markerNotificationIndex,
  markerNotifications,
  onCloseMarkerNotifications,
  onMoveMarkerNotification,
  onMarkerNotification,
  onOpenOfflinePackage,
}: OfflinePackageRouteProps) {
  const incidentId = useRouteIncidentId();
  const navigate = useNavigate();
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
      onOpenIncidentList={() => navigate(ROUTES.incidentList)}
      onOpenOfflinePackage={() => onOpenOfflinePackage(incidentId)}
    />
  );
}

function IncidentCloseRoute() {
  const incidentId = useRouteIncidentId();
  const navigate = useNavigate();

  return (
    <IncidentClosePage
      incidentId={incidentId}
      onBackToIncidents={() => navigate(ROUTES.incidentList)}
      onOpenLogin={() => navigate(ROUTES.login)}
    />
  );
}

export function App() {
  const navigate = useNavigate();
  const [currentUserAccount, setCurrentUserAccount] = useState<LoginAccount | null>(() => readStoredLoginAccount());
  const [savedAreaDraftsByIncidentId, setSavedAreaDraftsByIncidentId] = useState<Record<string, CompletedAreaDraft[]>>({});
  const [opRefreshVersionByIncidentId, setOpRefreshVersionByIncidentId] = useState<Record<string, number>>({});
  const [markerNotifications, setMarkerNotifications] = useState<MarkerNotification[]>([]);
  const [markerNotificationIndex, setMarkerNotificationIndex] = useState(0);

  const closeMarkerNotifications = () => {
    setMarkerNotifications([]);
    setMarkerNotificationIndex(0);
  };

  const moveMarkerNotification = (nextIndex: number) => {
    setMarkerNotificationIndex(Math.max(0, Math.min(nextIndex, markerNotifications.length - 1)));
  };

  const addMarkerNotification = useCallback((notification: MarkerNotification) => {
    setMarkerNotifications((currentNotifications) => {
      const existingIndex = currentNotifications.findIndex((current) => current.id === notification.id);
      if (existingIndex >= 0) {
        setMarkerNotificationIndex(existingIndex);
        return currentNotifications;
      }

      setMarkerNotificationIndex(currentNotifications.length);
      return [...currentNotifications, notification];
    });
  }, []);

  const openLogin = () => {
    void logoutCurrentSession().finally(() => {
      setCurrentUserAccount(null);
      navigate(ROUTES.login);
    });
  };

  useEffect(() => {
    const handleUnauthorized = () => {
      setCurrentUserAccount(null);
      navigate(ROUTES.login, { replace: true });
    };

    window.addEventListener(API_UNAUTHORIZED_EVENT, handleUnauthorized);
    return () => window.removeEventListener(API_UNAUTHORIZED_EVENT, handleUnauthorized);
  }, [navigate]);

  const handleLoginSuccess = (account: LoginAccount) => {
    setCurrentUserAccount(account);
    navigate(ROUTES.incidentList);
  };

  const saveAssignedAreas = (incidentId: string, drafts: CompletedAreaDraft[]) => {
    setSavedAreaDraftsByIncidentId((currentDraftsByIncidentId) => ({
      ...currentDraftsByIncidentId,
      [incidentId]: drafts,
    }));
  };

  const refreshOperationalPeriodViews = (incidentId: string) => {
    setOpRefreshVersionByIncidentId((currentVersionsByIncidentId) => ({
      ...currentVersionsByIncidentId,
      [incidentId]: (currentVersionsByIncidentId[incidentId] ?? 0) + 1,
    }));
  };

  return (
    <Routes>
      <Route path={ROUTES.home} element={<Navigate to={ROUTES.incidentList} replace />} />
      <Route
        path={ROUTES.incidentList}
        element={
          currentUserAccount ? (
          <IncidentListPage
            onOpenSituationBoard={(incidentId) => navigate(getIncidentBoardPath(incidentId))}
            onOpenLogin={openLogin}
            currentUserAccount={currentUserAccount}
          />
          ) : (
            <Navigate to={ROUTES.login} replace />
          )
        }
      />
      <Route path={ROUTES.login} element={<LoginPage onLoginSuccess={handleLoginSuccess} />} />
      <Route
        path={ROUTES.incidentBoard}
        element={
          currentUserAccount ? (
            <SituationBoardRoute
              currentUserAccount={currentUserAccount}
              markerNotificationIndex={markerNotificationIndex}
              markerNotifications={markerNotifications}
              onCloseMarkerNotifications={closeMarkerNotifications}
              onMarkerNotification={addMarkerNotification}
              onMoveMarkerNotification={moveMarkerNotification}
              onSaveAssignedAreas={saveAssignedAreas}
              savedAreaDraftsByIncidentId={savedAreaDraftsByIncidentId}
              opRefreshVersionByIncidentId={opRefreshVersionByIncidentId}
            />
          ) : (
            <Navigate to={ROUTES.login} replace />
          )
        }
      />
      <Route
        path={ROUTES.areaEdit}
        element={
          currentUserAccount ? (
            <SituationBoardRoute
              currentUserAccount={currentUserAccount}
              workspace="area"
              markerNotificationIndex={markerNotificationIndex}
              markerNotifications={markerNotifications}
              onCloseMarkerNotifications={closeMarkerNotifications}
              onMarkerNotification={addMarkerNotification}
              onMoveMarkerNotification={moveMarkerNotification}
              onSaveAssignedAreas={saveAssignedAreas}
              savedAreaDraftsByIncidentId={savedAreaDraftsByIncidentId}
              opRefreshVersionByIncidentId={opRefreshVersionByIncidentId}
            />
          ) : (
            <Navigate to={ROUTES.login} replace />
          )
        }
      />
      <Route
        path={ROUTES.incidentHandover}
        element={
          currentUserAccount ? (
            <HandoverRoute
              currentUserAccount={currentUserAccount}
              markerNotificationIndex={markerNotificationIndex}
              markerNotifications={markerNotifications}
              onCloseMarkerNotifications={closeMarkerNotifications}
              onMarkerNotification={addMarkerNotification}
              onMoveMarkerNotification={moveMarkerNotification}
              onOpenOfflinePackage={(nextIncidentId) => navigate(getIncidentOfflinePackagePath(nextIncidentId))}
              onOperationalPeriodCreated={refreshOperationalPeriodViews}
            />
          ) : (
            <Navigate to={ROUTES.login} replace />
          )
        }
      />
      <Route
        path={ROUTES.incidentOfflinePackage}
        element={
          currentUserAccount ? (
            <OfflinePackageRoute
              currentUserAccount={currentUserAccount}
              markerNotificationIndex={markerNotificationIndex}
              markerNotifications={markerNotifications}
              onCloseMarkerNotifications={closeMarkerNotifications}
              onMoveMarkerNotification={moveMarkerNotification}
              onMarkerNotification={addMarkerNotification}
              onOpenOfflinePackage={(nextIncidentId) => navigate(getIncidentOfflinePackagePath(nextIncidentId))}
            />
          ) : (
            <Navigate to={ROUTES.login} replace />
          )
        }
      />
      <Route
        path={ROUTES.incidentClose}
        element={
          currentUserAccount ? (
            <IncidentCloseRoute />
          ) : (
            <Navigate to={ROUTES.login} replace />
          )
        }
      />
      <Route
        path={ROUTES.legacyAreaEdit}
        element={<Navigate to={getAreaEditPath(BOOTSTRAP_INCIDENT_ID)} replace />}
      />
      <Route
        path={ROUTES.legacyIncidentClose}
        element={<Navigate to={getIncidentClosePath(BOOTSTRAP_INCIDENT_ID)} replace />}
      />
      <Route path="*" element={<Navigate to={ROUTES.incidentList} replace />} />
    </Routes>
  );
}
