import { useEffect, useState } from 'react';

import { AreaEditPage } from '../features/areaEdit/presentation/pages/AreaEditPage';
import { IncidentClosePage } from '../features/incidentClose/presentation/pages/IncidentClosePage';
import { IncidentListPage } from '../features/incidents/presentation/pages/IncidentListPage';
import { LoginPage } from '../features/login/presentation/pages/LoginPage';
import { MOCK_LOGIN_ACCOUNT } from '../features/login/presentation/constants/mockLogin';
import { SituationBoardPage } from '../features/situationBoard/presentation/pages/SituationBoardPage';
import type { CompletedAreaDraft } from '../shared/model/areaDraft';
import { ROUTES } from './routes';

export function App() {
  const [pathname, setPathname] = useState(() => window.location.pathname);
  const [savedAreaDrafts, setSavedAreaDrafts] = useState<CompletedAreaDraft[]>([]);

  useEffect(() => {
    const handlePopState = () => {
      setPathname(window.location.pathname);
    };

    window.addEventListener('popstate', handlePopState);

    return () => {
      window.removeEventListener('popstate', handlePopState);
    };
  }, []);

  const navigate = (nextPathname: string) => {
    if (window.location.pathname !== nextPathname) {
      window.history.pushState({}, '', nextPathname);
    }

    setPathname(nextPathname);
  };

  if (pathname === ROUTES.incidentList) {
    return (
      <IncidentListPage
        onOpenSituationBoard={() => navigate(ROUTES.home)}
        onOpenLogin={() => navigate(ROUTES.login)}
        currentUserRole={MOCK_LOGIN_ACCOUNT.role}
      />
    );
  }

  if (pathname === ROUTES.incidentClose) {
    return (
      <IncidentClosePage
        onBackToIncidents={() => navigate(ROUTES.incidentList)}
        onOpenLogin={() => navigate(ROUTES.login)}
      />
    );
  }

  if (pathname === ROUTES.login) {
    return <LoginPage onLoginSuccess={() => navigate(ROUTES.incidentList)} />;
  }

  if (pathname === ROUTES.areaEdit) {
    return (
      <AreaEditPage
        onBackToSituationBoard={() => navigate(ROUTES.home)}
        onSaveAssignedAreas={setSavedAreaDrafts}
      />
    );
  }

  return (
    <SituationBoardPage
      savedAreaDrafts={savedAreaDrafts}
      onOpenIncidentList={() => navigate(ROUTES.incidentList)}
      onOpenAreaEdit={() => navigate(ROUTES.areaEdit)}
    />
  );
}
