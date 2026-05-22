import { lazy } from 'react';
import { useNavigate } from 'react-router-dom';

import { ROUTES } from '../routes';
import { useRouteIncidentId } from '../useRouteIncidentId';

const IncidentClosePage = lazy(() =>
  import('../../features/incidentClose/presentation/pages/IncidentClosePage').then((module) => ({
    default: module.IncidentClosePage,
  })),
);

export function IncidentCloseRoute() {
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
