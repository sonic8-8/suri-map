import { Component, lazy, Suspense, useCallback, useEffect, useState, type ErrorInfo, type ReactNode } from 'react';
import { Navigate, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom';

import { completeKeycloakLogin, logoutCurrentSession, readStoredLoginAccount } from '../features/login/data/login';
import { LoginPage } from '../features/login/presentation/pages/LoginPage';
import type { LoginAccount } from '../features/login/presentation/types/login';
import { SearchHistoryPage } from '../features/searchHistory/presentation/pages/SearchHistoryPage';
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

type RouteErrorBoundaryProps = {
  children: ReactNode;
  resetKey: string;
  onOpenIncidentList: () => void;
};

type RouteErrorBoundaryState = {
  error: Error | null;
};

class RouteErrorBoundary extends Component<RouteErrorBoundaryProps, RouteErrorBoundaryState> {
  state: RouteErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error) {
    return { error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('Route render error', {
      error,
      componentStack: errorInfo.componentStack,
      route: this.props.resetKey,
    });
  }

  componentDidUpdate(previousProps: RouteErrorBoundaryProps) {
    if (previousProps.resetKey !== this.props.resetKey && this.state.error) {
      this.setState({ error: null });
    }
  }
  render() {
    if (!this.state.error) {
      return this.props.children;
    }

    return (
      <main className="situation-board-page situation-board-page-loading" role="alert">
        <section className="situation-board-loading-screen" aria-label="Page error">
          <strong>페이지를 표시하지 못했습니다.</strong>
          <span>일시적인 화면 오류가 발생했습니다. 사건 목록으로 돌아간 뒤 다시 열어주세요.</span>
          <button type="button" onClick={this.props.onOpenIncidentList}>
            사건 목록으로 돌아가기
          </button>
        </section>
      </main>
    );
  }
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

function readLoginErrorMessage(state: unknown) {
  if (state === null || typeof state !== 'object' || !('authError' in state)) {
    return '';
  }

  const authError = (state as { authError?: unknown }).authError;
  if (typeof authError !== 'string' || !authError) {
    return '';
  }

  return `SSO 로그인 실패: ${authError}`;
}

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
  const openIncidentListFromHistory = useCallback(() => navigate(ROUTES.incidentList, { replace: true }), [navigate]);
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
      isHandoverWorkspaceRoute={workspace === 'handover'}
      markerNotificationIndex={markerNotificationIndex}
      markerNotifications={markerNotifications}
      onCloseMarkerNotifications={onCloseMarkerNotifications}
      onMoveMarkerNotification={onMoveMarkerNotification}
      onCloseAreaWorkspaceRoute={() => navigate(getIncidentBoardPath(incidentId))}
      onCloseHandoverWorkspaceRoute={() => navigate(getIncidentBoardPath(incidentId))}
      onOpenAreaWorkspaceRoute={() => navigate(getAreaEditPath(incidentId))}
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
      onOpenLogin={onOpenLogin}
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
  onLoginFailure: (error: Error) => void;
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
      .catch((error) => {
        if (isActive) {
          const loginError = error instanceof Error ? error : new Error('oidc_login_failed');
          console.warn('OIDC login failed', loginError);
          onLoginFailure(loginError);
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
  const loginErrorMessage = readLoginErrorMessage(location.state);
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

  const handleOidcCallbackFailure = useCallback(
    (error: Error) => {
      setCurrentUserAccount(null);
      navigate(ROUTES.login, { replace: true, state: { authError: error.message } });
    },
    [navigate],
  );

  const saveAssignedAreas = (incidentId: string, drafts: CompletedAreaDraft[]) => {
    setSavedAreaDraftsByIncidentId((currentDraftsByIncidentId) => ({
      ...currentDraftsByIncidentId,
      [incidentId]: drafts,
    }));
  };
  const refreshOperationalPeriodViews = useCallback((incidentId: string) => {
    setOpRefreshVersionByIncidentId((currentVersions) => ({
      ...currentVersions,
      [incidentId]: (currentVersions[incidentId] ?? 0) + 1,
    }));
  }, []);

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
