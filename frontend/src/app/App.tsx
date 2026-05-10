import { useState } from 'react';
import { Navigate, Route, Routes, useNavigate, useParams } from 'react-router-dom';

import { AreaEditPage } from '../features/areaEdit/presentation/pages/AreaEditPage';
import { IncidentClosePage } from '../features/incidentClose/presentation/pages/IncidentClosePage';
import { IncidentListPage } from '../features/incidents/presentation/pages/IncidentListPage';
import { clearLoginSession, readStoredLoginAccount } from '../features/login/data/login';
import { LoginPage } from '../features/login/presentation/pages/LoginPage';
import type { LoginAccount } from '../features/login/presentation/types/login';
import { SituationBoardPage } from '../features/situationBoard/presentation/pages/SituationBoardPage';
import type { CompletedAreaDraft } from '../shared/model/areaDraft';
import type { MarkerNotification } from '../shared/ui';
import {
  BOOTSTRAP_INCIDENT_ID,
  getAreaEditPath,
  getIncidentBoardPath,
  getIncidentClosePath,
  ROUTES,
} from './routes';

const initialMarkerNotifications: MarkerNotification[] = [
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
];

function useRouteIncidentId() {
  const { incidentId } = useParams();
  return incidentId ?? BOOTSTRAP_INCIDENT_ID;
}

type SituationBoardRouteProps = {
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  onCloseMarkerNotifications: () => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  savedAreaDrafts: CompletedAreaDraft[];
};

function SituationBoardRoute({
  markerNotificationIndex,
  markerNotifications,
  onCloseMarkerNotifications,
  onMoveMarkerNotification,
  savedAreaDrafts,
}: SituationBoardRouteProps) {
  const incidentId = useRouteIncidentId();
  const navigate = useNavigate();

  return (
    <SituationBoardPage
      markerNotificationIndex={markerNotificationIndex}
      markerNotifications={markerNotifications}
      onCloseMarkerNotifications={onCloseMarkerNotifications}
      onMoveMarkerNotification={onMoveMarkerNotification}
      savedAreaDrafts={savedAreaDrafts}
      onOpenIncidentList={() => navigate(ROUTES.incidentList)}
      onOpenAreaEdit={() => navigate(getAreaEditPath(incidentId))}
    />
  );
}

type AreaEditRouteProps = {
  markerNotificationIndex: number;
  markerNotifications: MarkerNotification[];
  onCloseMarkerNotifications: () => void;
  onMoveMarkerNotification: (nextIndex: number) => void;
  onSaveAssignedAreas: (drafts: CompletedAreaDraft[]) => void;
};

function AreaEditRoute({
  markerNotificationIndex,
  markerNotifications,
  onCloseMarkerNotifications,
  onMoveMarkerNotification,
  onSaveAssignedAreas,
}: AreaEditRouteProps) {
  const incidentId = useRouteIncidentId();
  const navigate = useNavigate();

  return (
    <AreaEditPage
      markerNotificationIndex={markerNotificationIndex}
      markerNotifications={markerNotifications}
      onBackToSituationBoard={() => navigate(getIncidentBoardPath(incidentId))}
      onCloseMarkerNotifications={onCloseMarkerNotifications}
      onMoveMarkerNotification={onMoveMarkerNotification}
      onOpenIncidentList={() => navigate(ROUTES.incidentList)}
      onSaveAssignedAreas={onSaveAssignedAreas}
    />
  );
}

function IncidentCloseRoute() {
  const navigate = useNavigate();

  return (
    <IncidentClosePage
      onBackToIncidents={() => navigate(ROUTES.incidentList)}
      onOpenLogin={() => navigate(ROUTES.login)}
    />
  );
}

export function App() {
  const navigate = useNavigate();
  const [currentUserAccount, setCurrentUserAccount] = useState<LoginAccount | null>(() => readStoredLoginAccount());
  const [savedAreaDrafts, setSavedAreaDrafts] = useState<CompletedAreaDraft[]>([]);
  const [markerNotifications, setMarkerNotifications] = useState<MarkerNotification[]>(initialMarkerNotifications);
  const [markerNotificationIndex, setMarkerNotificationIndex] = useState(0);

  const closeMarkerNotifications = () => {
    setMarkerNotifications([]);
    setMarkerNotificationIndex(0);
  };

  const moveMarkerNotification = (nextIndex: number) => {
    setMarkerNotificationIndex(Math.max(0, Math.min(nextIndex, markerNotifications.length - 1)));
  };

  const openLogin = () => {
    clearLoginSession();
    setCurrentUserAccount(null);
    navigate(ROUTES.login);
  };

  const handleLoginSuccess = (account: LoginAccount) => {
    setCurrentUserAccount(account);
    navigate(ROUTES.incidentList);
  };

  return (
    <Routes>
      <Route path={ROUTES.home} element={<Navigate to={ROUTES.incidentList} replace />} />
      <Route
        path={ROUTES.incidentList}
        element={
          currentUserAccount ? (
            <IncidentListPage
              onOpenSituationBoard={() => navigate(getIncidentBoardPath(BOOTSTRAP_INCIDENT_ID))}
              onOpenLogin={openLogin}
              currentUserRole={currentUserAccount.role}
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
              markerNotificationIndex={markerNotificationIndex}
              markerNotifications={markerNotifications}
              onCloseMarkerNotifications={closeMarkerNotifications}
              onMoveMarkerNotification={moveMarkerNotification}
              savedAreaDrafts={savedAreaDrafts}
            />
          ) : (
            <Navigate to={ROUTES.login} replace />
          )
        }
      />
      <Route
        path={ROUTES.areaEdit}
        element={
          <AreaEditRoute
            markerNotificationIndex={markerNotificationIndex}
            markerNotifications={markerNotifications}
            onCloseMarkerNotifications={closeMarkerNotifications}
            onMoveMarkerNotification={moveMarkerNotification}
            onSaveAssignedAreas={setSavedAreaDrafts}
          />
        }
      />
      <Route path={ROUTES.incidentClose} element={<IncidentCloseRoute />} />
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
