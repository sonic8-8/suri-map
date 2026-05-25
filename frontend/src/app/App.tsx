import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';

import { AuthCallbackRoute } from './AuthCallbackRoute';
import { ProtectedRoute, RouteErrorBoundary } from './AppRouteShell';
import { HandoverRoute } from './routeAdapters/HandoverRoute';
import { IncidentCloseRoute } from './routeAdapters/IncidentCloseRoute';
import { IncidentDetailRoute } from './routeAdapters/IncidentDetailRoute';
import { IncidentListRoute } from './routeAdapters/IncidentListRoute';
import { LoginRoute } from './routeAdapters/LoginRoute';
import { OfflinePackageRoute } from './routeAdapters/OfflinePackageRoute';
import { SearchHistoryRoute } from './routeAdapters/SearchHistoryRoute';
import { SituationBoardRoute } from './routeAdapters/SituationBoardRoute';
import { useAppSession } from './useAppSession';
import { useIncidentWorkspaceState } from './useIncidentWorkspaceState';
import { useMarkerNotificationQueue } from './useMarkerNotificationQueue';
import {
  BOOTSTRAP_INCIDENT_ID,
  getAreaEditPath,
  getIncidentClosePath,
  getIncidentOfflinePackagePath,
  ROUTES,
} from './routes';

export function App() {
  const navigate = useNavigate();
  const location = useLocation();
  const { currentUserAccount, handleOidcCallbackFailure, handleOidcCallbackSuccess, openLogin } = useAppSession();
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
  const markerNotificationRouteProps = {
    markerNotificationIndex,
    markerNotifications,
    onCloseMarkerNotifications: closeMarkerNotifications,
    onMarkerNotification: addMarkerNotification,
    onMoveMarkerNotification: moveMarkerNotification,
  };
  const loginRedirectState = { from: `${location.pathname}${location.search}${location.hash}` };
  const loginRedirectElement = <Navigate to={ROUTES.login} replace state={loginRedirectState} />;
  const openOfflinePackage = (nextIncidentId: string) => navigate(getIncidentOfflinePackagePath(nextIncidentId));

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
            <ProtectedRoute value={currentUserAccount} fallback={loginRedirectElement} lazy>
              {(account) => <IncidentListRoute currentUserAccount={account} onOpenLogin={openLogin} />}
            </ProtectedRoute>
          }
        />
        <Route path={ROUTES.login} element={<LoginRoute />} />
        {import.meta.env.DEV ? <Route path={ROUTES.devLoginPreview} element={<LoginRoute />} /> : null}
        <Route
          path={ROUTES.authCallback}
          element={
            <AuthCallbackRoute onLoginSuccess={handleOidcCallbackSuccess} onLoginFailure={handleOidcCallbackFailure} />
          }
        />
        <Route
          path={ROUTES.incidentDetail}
          element={
            <ProtectedRoute value={currentUserAccount} fallback={loginRedirectElement} lazy>
              {(account) => (
                <IncidentDetailRoute
                  currentUserAccount={account}
                  {...markerNotificationRouteProps}
                  onOpenOfflinePackage={openOfflinePackage}
                  onOpenLogin={openLogin}
                />
              )}
            </ProtectedRoute>
          }
        />
        <Route
          path={ROUTES.incidentBoard}
          element={
            <ProtectedRoute value={currentUserAccount} fallback={loginRedirectElement}>
              {(account) => (
                <SituationBoardRoute
                  currentUserAccount={account}
                  {...markerNotificationRouteProps}
                  onSaveAssignedAreas={saveAssignedAreas}
                  savedAreaDraftsByIncidentId={savedAreaDraftsByIncidentId}
                  opRefreshVersionByIncidentId={opRefreshVersionByIncidentId}
                  onOpenLogin={openLogin}
                />
              )}
            </ProtectedRoute>
          }
        />
        <Route
          path={ROUTES.areaEdit}
          element={
            <ProtectedRoute value={currentUserAccount} fallback={loginRedirectElement}>
              {(account) => (
                <SituationBoardRoute
                  currentUserAccount={account}
                  workspace="area"
                  {...markerNotificationRouteProps}
                  onSaveAssignedAreas={saveAssignedAreas}
                  savedAreaDraftsByIncidentId={savedAreaDraftsByIncidentId}
                  opRefreshVersionByIncidentId={opRefreshVersionByIncidentId}
                  onOpenLogin={openLogin}
                />
              )}
            </ProtectedRoute>
          }
        />
        <Route
          path={ROUTES.incidentHandover}
          element={
            <ProtectedRoute value={currentUserAccount} fallback={loginRedirectElement}>
              {() => <HandoverRoute />}
            </ProtectedRoute>
          }
        />
        <Route
          path={ROUTES.incidentSearchHistory}
          element={
            <ProtectedRoute value={currentUserAccount} fallback={loginRedirectElement}>
              {(account) => (
                <SearchHistoryRoute
                  currentUserAccount={account}
                  {...markerNotificationRouteProps}
                  onOpenOfflinePackage={openOfflinePackage}
                  onOperationalPeriodCreated={refreshOperationalPeriodViews}
                  onOpenLogin={openLogin}
                />
              )}
            </ProtectedRoute>
          }
        />
        <Route
          path={ROUTES.incidentOfflinePackage}
          element={
            <ProtectedRoute value={currentUserAccount} fallback={loginRedirectElement}>
              {(account) => (
                <OfflinePackageRoute
                  currentUserAccount={account}
                  {...markerNotificationRouteProps}
                  onOpenOfflinePackage={openOfflinePackage}
                  onOpenLogin={openLogin}
                />
              )}
            </ProtectedRoute>
          }
        />
        <Route
          path={ROUTES.incidentClose}
          element={
            <ProtectedRoute value={currentUserAccount} fallback={loginRedirectElement} lazy>
              {() => <IncidentCloseRoute />}
            </ProtectedRoute>
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
