import { lazy, Suspense, useCallback, useEffect, useState, type ReactNode } from 'react';
import { Navigate, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom';

import { completeKeycloakLogin, logoutCurrentSession, readStoredLoginAccount } from '../features/login/data/login';
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
  getIncidentDetailPath,
  getIncidentHandoverPath,
  getIncidentBoardPath,
  getIncidentClosePath,
  getIncidentOfflinePackagePath,
  ROUTES,
} from './routes';

const IncidentListPage = lazy(() =>
  import('../features/incidents/presentation/pages/IncidentListPage').then((module) => ({
    default: module.IncidentListPage,
  })),
);

const HandoverPage = lazy(() =>
  import('../features/handover/presentation/pages/HandoverPage').then((module) => ({
    default: module.HandoverPage,
  })),
);

const IncidentDetailPage = lazy(() =>
  import('../features/incident/presentation/pages/IncidentDetailPage').then((module) => ({
    default: module.IncidentDetailPage,
  })),
);

const IncidentClosePage = lazy(() =>
  import('../features/incidentClose/presentation/pages/IncidentClosePage').then((module) => ({
    default: module.IncidentClosePage,
  })),
);

function LazyRoute({ children }: { children: ReactNode }) {
  return <Suspense fallback={<RouteLoadingScreen />}>{children}</Suspense>;
}

function RouteLoadingScreen() {
  return (
    <main className="situation-board-page situation-board-page-loading" aria-busy="true">
      <section className="situation-board-loading-screen" role="status" aria-live="polite" aria-label="Loading page">
        <span className="situation-board-loading-spinner" aria-hidden="true" />
      </section>
    </main>
  );
}

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

function createCurrentRoutePath() {
  return `${window.location.pathname}${window.location.search}${window.location.hash}`;
}

function readLoginRedirectPath(state: unknown) {
  if (state === null || typeof state !== 'object' || !('from' in state)) {
    return null;
  }

  const from = (state as { from?: unknown }).from;

  if (typeof from !== 'string' || !from.startsWith('/') || from.startsWith('//') || from === ROUTES.login) {
    return null;
  }

  return from;
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
  const openIncidentListFromHistory = useCallback(
    () => navigate(ROUTES.incidentList, { replace: true }),
    [navigate],
  );
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
      onOpenIncidentDetail={() => navigate(getIncidentDetailPath(incidentId))}
      onOpenOfflinePackage={() => navigate(getIncidentOfflinePackagePath(incidentId))}
      onSaveAssignedAreas={(drafts) => onSaveAssignedAreas(incidentId, drafts)}
      savedAreaDrafts={savedAreaDrafts}
      refreshVersion={refreshVersion}
      onOpenIncidentList={() => navigate(ROUTES.incidentList)}
      onBrowserBackToIncidentList={openIncidentListFromHistory}
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
  const openIncidentListFromHistory = useCallback(
    () => navigate(ROUTES.incidentList, { replace: true }),
    [navigate],
  );

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
      onBrowserBackToIncidentList={openIncidentListFromHistory}
      onOpenIncidentDetail={() => navigate(getIncidentDetailPath(incidentId))}
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
  const openIncidentListFromHistory = useCallback(
    () => navigate(ROUTES.incidentList, { replace: true }),
    [navigate],
  );
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
      onOpenIncidentDetail={() => navigate(getIncidentDetailPath(incidentId))}
      onOpenIncidentList={() => navigate(ROUTES.incidentList)}
      onBrowserBackToIncidentList={openIncidentListFromHistory}
      onOpenOfflinePackage={() => onOpenOfflinePackage(incidentId)}
    />
  );
}

type IncidentDetailRouteProps = {
  currentUserAccount: LoginAccount;
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  onCloseMarkerNotifications: () => void;
  onMarkerNotification: (notification: MarkerNotification) => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  onOpenOfflinePackage: (incidentId: string) => void;
};

function IncidentDetailRoute({
  currentUserAccount,
  markerNotificationIndex,
  markerNotifications,
  onCloseMarkerNotifications,
  onMarkerNotification,
  onMoveMarkerNotification,
  onOpenOfflinePackage,
}: IncidentDetailRouteProps) {
  const incidentId = useRouteIncidentId();
  const navigate = useNavigate();
  const openIncidentListFromHistory = useCallback(
    () => navigate(ROUTES.incidentList, { replace: true }),
    [navigate],
  );
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
      onOpenIncidentList={() => navigate(ROUTES.incidentList)}
      onBrowserBackToIncidentList={openIncidentListFromHistory}
      onOpenOfflinePackage={() => onOpenOfflinePackage(incidentId)}
      onOpenSituationBoard={() => navigate(getIncidentBoardPath(incidentId))}
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

type AuthCallbackRouteProps = {
  onLoginSuccess: (account: LoginAccount, returnPath: string) => void;
  onLoginFailure: () => void;
};

function AuthCallbackRoute({ onLoginSuccess, onLoginFailure }: AuthCallbackRouteProps) {
  useEffect(() => {
    let isActive = true;

    completeKeycloakLogin()
      .then(({ account, returnPath }) => {
        if (isActive) {
          onLoginSuccess(account, returnPath);
        }
      })
      .catch(() => {
        if (isActive) {
          onLoginFailure();
        }
      });

    return () => {
      isActive = false;
    };
  }, [onLoginFailure, onLoginSuccess]);

  return (
    <main>
      <div>로그인 처리 중</div>
    </main>
  );
}

export function App() {
  const navigate = useNavigate();
  const location = useLocation();
  const [currentUserAccount, setCurrentUserAccount] = useState<LoginAccount | null>(() => readStoredLoginAccount());
  const [savedAreaDraftsByIncidentId, setSavedAreaDraftsByIncidentId] = useState<Record<string, CompletedAreaDraft[]>>(
    {},
  );
  const [opRefreshVersionByIncidentId, setOpRefreshVersionByIncidentId] = useState<Record<string, number>>({});
  const [markerNotifications, setMarkerNotifications] = useState<MarkerNotification[]>([]);
  const [markerNotificationIndex, setMarkerNotificationIndex] = useState(0);
  const loginRedirectPath = readLoginRedirectPath(location.state);
  const loginRedirectState = { from: `${location.pathname}${location.search}${location.hash}` };
  const loginRedirectElement = <Navigate to={ROUTES.login} replace state={loginRedirectState} />;

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
      navigate(ROUTES.login, { replace: true, state: { from: createCurrentRoutePath() } });
    };

    window.addEventListener(API_UNAUTHORIZED_EVENT, handleUnauthorized);
    return () => window.removeEventListener(API_UNAUTHORIZED_EVENT, handleUnauthorized);
  }, [navigate]);

  const handleOidcCallbackSuccess = useCallback(
    (account: LoginAccount, returnPath: string) => {
      setCurrentUserAccount(account);
      navigate(returnPath, { replace: true });
    },
    [navigate],
  );

  const handleOidcCallbackFailure = useCallback(() => {
    setCurrentUserAccount(null);
    navigate(ROUTES.login, { replace: true });
  }, [navigate]);

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
            <LazyRoute>
              <IncidentListPage
                onOpenSituationBoard={(incidentId) => navigate(getIncidentBoardPath(incidentId))}
                onOpenLogin={openLogin}
                currentUserAccount={currentUserAccount}
              />
            </LazyRoute>
          ) : (
            loginRedirectElement
          )
        }
      />
      <Route path={ROUTES.login} element={<LoginPage redirectPath={loginRedirectPath ?? ROUTES.incidentList} />} />
      <Route
        path={ROUTES.authCallback}
        element={
          <AuthCallbackRoute onLoginSuccess={handleOidcCallbackSuccess} onLoginFailure={handleOidcCallbackFailure} />
        }
      />
      <Route
        path={ROUTES.incidentDetail}
        element={
          currentUserAccount ? (
            <LazyRoute>
              <IncidentDetailRoute
                currentUserAccount={currentUserAccount}
                markerNotificationIndex={markerNotificationIndex}
                markerNotifications={markerNotifications}
                onCloseMarkerNotifications={closeMarkerNotifications}
                onMarkerNotification={addMarkerNotification}
                onMoveMarkerNotification={moveMarkerNotification}
                onOpenOfflinePackage={(nextIncidentId) => navigate(getIncidentOfflinePackagePath(nextIncidentId))}
              />
            </LazyRoute>
          ) : (
            loginRedirectElement
          )
        }
      />
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
            loginRedirectElement
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
            loginRedirectElement
          )
        }
      />
      <Route
        path={ROUTES.incidentHandover}
        element={
          currentUserAccount ? (
            <LazyRoute>
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
            </LazyRoute>
          ) : (
            loginRedirectElement
          )
        }
      />
      <Route
        path={ROUTES.incidentOfflinePackage}
        element={
          currentUserAccount ? (
            <LazyRoute>
              <OfflinePackageRoute
                currentUserAccount={currentUserAccount}
                markerNotificationIndex={markerNotificationIndex}
                markerNotifications={markerNotifications}
                onCloseMarkerNotifications={closeMarkerNotifications}
                onMoveMarkerNotification={moveMarkerNotification}
                onMarkerNotification={addMarkerNotification}
                onOpenOfflinePackage={(nextIncidentId) => navigate(getIncidentOfflinePackagePath(nextIncidentId))}
              />
            </LazyRoute>
          ) : (
            loginRedirectElement
          )
        }
      />
      <Route
        path={ROUTES.incidentClose}
        element={
          currentUserAccount ? (
            <LazyRoute>
              <IncidentCloseRoute />
            </LazyRoute>
          ) : (
            loginRedirectElement
          )
        }
      />
      <Route path={ROUTES.legacyAreaEdit} element={<Navigate to={getAreaEditPath(BOOTSTRAP_INCIDENT_ID)} replace />} />
      <Route
        path={ROUTES.legacyIncidentClose}
        element={<Navigate to={getIncidentClosePath(BOOTSTRAP_INCIDENT_ID)} replace />}
      />
      <Route path="*" element={<Navigate to={ROUTES.incidentList} replace />} />
    </Routes>
  );
}
