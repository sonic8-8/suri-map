import { useEffect, useState } from 'react';

import { IncidentListPage } from '../features/incidents/presentation/pages/IncidentListPage';
import { LoginPage } from '../features/login/presentation/pages/LoginPage';
import { SituationBoardPage } from '../features/situationBoard/presentation/pages/SituationBoardPage';
import { ROUTES } from './routes';

export function App() {
  const [pathname, setPathname] = useState(() => window.location.pathname);

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
      />
    );
  }

  if (pathname === ROUTES.login) {
    return <LoginPage onLoginSuccess={() => navigate(ROUTES.incidentList)} />;
  }

  return <SituationBoardPage onOpenIncidentList={() => navigate(ROUTES.incidentList)} />;
}
