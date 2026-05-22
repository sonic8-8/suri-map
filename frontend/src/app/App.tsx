import { lazy, useCallback, useEffect, useState } from 'react';
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';

import { logoutCurrentSession, readStoredLoginAccount } from '../features/login/data/login';
import { LoginPage } from '../features/login/presentation/pages/LoginPage';
import type { LoginAccount } from '../features/login/presentation/types/login';
import { SearchHistoryPage } from '../features/searchHistory/presentation/pages/SearchHistoryPage';
import { useIncidentMarkerNotifications } from '../features/markerNotifications/presentation/hooks/useIncidentMarkerNotifications';
import { OfflinePackageStatusPage } from '../features/offlinePackage/presentation/pages/OfflinePackageStatusPage';
import { SituationBoardPage } from '../features/situationBoard/presentation/pages/SituationBoardPage';
import type { CompletedAreaDraft } from '../shared/model/areaDraft';
import type { MarkerNotification } from '../shared/ui';
import { API_UNAUTHORIZED_EVENT } from '../shared/api/client';
import { AuthCallbackRoute } from './AuthCallbackRoute';
import { LazyRoute, RouteErrorBoundary } from './AppRouteShell';
import { IncidentCloseRoute } from './routeAdapters/IncidentCloseRoute';
import { createCurrentRoutePath, readLoginErrorMessage, readLoginRedirectPath } from './loginRouteState';
import { useIncidentWorkspaceState } from './useIncidentWorkspaceState';
import { useMarkerNotificationQueue } from './useMarkerNotificationQueue';
import { useRouteIncidentId } from './useRouteIncidentId';
import {
  BOOTSTRAP_INCIDENT_ID,
  getAreaEditPath,
  getIncidentDetailPath,
  getIncidentHandoverPath,
  getIncidentSearchHistoryPath,
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

const IncidentDetailPage = lazy(() =>
  import('../features/incident/presentation/pages/IncidentDetailPage').then((module) => ({
    default: module.IncidentDetailPage,
  })),
);

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

type SituationBoardRouteProps = {
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

type HandoverRouteProps = {
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

function HandoverRoute(_props: HandoverRouteProps) {
  const incidentId = useRouteIncidentId();
  return <Navigate to={getIncidentSearchHistoryPath(incidentId)} replace />;
}

function SearchHistoryRoute({
  currentUserAccount,
  markerNotificationIndex,
  markerNotifications,
  onCloseMarkerNotifications,
  onMarkerNotification,
  onMoveMarkerNotification,
  onOperationalPeriodCreated,
  onOpenOfflinePackage,
  onOpenLogin,
}: HandoverRouteProps) {
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

type OfflinePackageRouteProps = {
  currentUserAccount: LoginAccount;
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  onCloseMarkerNotifications: () => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  onMarkerNotification: (notification: MarkerNotification) => void;
  onOpenOfflinePackage: (incidentId: string) => void;
  onOpenLogin: () => void;
};

function OfflinePackageRoute({
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

type IncidentDetailRouteProps = {
  currentUserAccount: LoginAccount;
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  onCloseMarkerNotifications: () => void;
  onMarkerNotification: (notification: MarkerNotification) => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  onOpenOfflinePackage: (incidentId: string) => void;
  onOpenLogin: () => void;
};

function IncidentDetailRoute({
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

export function App() {
  const navigate = useNavigate();
  const location = useLocation();
  const [currentUserAccount, setCurrentUserAccount] = useState<LoginAccount | null>(() => readStoredLoginAccount());
  const {
    opRefreshVersionByIncidentId,
    refreshOperationalPeriodViews,
    savedAreaDraftsByIncidentId,
    saveAssignedAreas,
  } = useIncidentWorkspaceState();
  const {
    addMarkerNotification,
    closeMarkerNotifications,
    markerNotificationIndex,
    markerNotifications,
    moveMarkerNotification,
  } = useMarkerNotificationQueue();
  const loginRedirectPath = readLoginRedirectPath(location.state);
  const loginErrorMessage = readLoginErrorMessage(location.state);
  const loginRedirectState = { from: `${location.pathname}${location.search}${location.hash}` };
  const loginRedirectElement = <Navigate to={ROUTES.login} replace state={loginRedirectState} />;

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

  const handleOidcCallbackFailure = useCallback(
    (error: Error) => {
      setCurrentUserAccount(null);
      navigate(ROUTES.login, { replace: true, state: { authError: error.message } });
    },
    [navigate],
  );

  return (
    <RouteErrorBoundary
      resetKey={`${location.pathname}${location.search}${location.hash}`}
      onOpenIncidentList={() => navigate(ROUTES.incidentList, { replace: true })}
    >
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
        <Route
          path={ROUTES.login}
          element={
            <LoginPage
              redirectPath={loginRedirectPath ?? ROUTES.incidentList}
              initialErrorMessage={loginErrorMessage}
            />
          }
        />
        {import.meta.env.DEV ? (
          <Route
            path={ROUTES.devLoginPreview}
            element={
              <LoginPage
                redirectPath={loginRedirectPath ?? ROUTES.incidentList}
                initialErrorMessage={loginErrorMessage}
              />
            }
          />
        ) : null}
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
                  onOpenLogin={openLogin}
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
                onOpenLogin={openLogin}
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
                onOpenLogin={openLogin}
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
              <HandoverRoute
                currentUserAccount={currentUserAccount}
                markerNotificationIndex={markerNotificationIndex}
                markerNotifications={markerNotifications}
                onCloseMarkerNotifications={closeMarkerNotifications}
                onMarkerNotification={addMarkerNotification}
                onMoveMarkerNotification={moveMarkerNotification}
                onOpenOfflinePackage={(nextIncidentId) => navigate(getIncidentOfflinePackagePath(nextIncidentId))}
                onOperationalPeriodCreated={refreshOperationalPeriodViews}
                onOpenLogin={openLogin}
              />
            ) : (
              loginRedirectElement
            )
          }
        />
        <Route
          path={ROUTES.incidentSearchHistory}
          element={
            currentUserAccount ? (
              <SearchHistoryRoute
                currentUserAccount={currentUserAccount}
                markerNotificationIndex={markerNotificationIndex}
                markerNotifications={markerNotifications}
                onCloseMarkerNotifications={closeMarkerNotifications}
                onMarkerNotification={addMarkerNotification}
                onMoveMarkerNotification={moveMarkerNotification}
                onOpenOfflinePackage={(nextIncidentId) => navigate(getIncidentOfflinePackagePath(nextIncidentId))}
                onOperationalPeriodCreated={refreshOperationalPeriodViews}
                onOpenLogin={openLogin}
              />
            ) : (
              loginRedirectElement
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
                onOpenLogin={openLogin}
              />
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
    </RouteErrorBoundary>
  );
}
