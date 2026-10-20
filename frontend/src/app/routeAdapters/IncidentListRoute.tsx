import { lazy } from 'react';
import { useNavigate } from 'react-router-dom';

import type { LoginAccount } from '../../features/login/presentation/types/login';
import { getIncidentBoardPath } from '../routes';

const IncidentListPage = lazy(() =>
  import('../../features/incidents/presentation/pages/IncidentListPage').then((module) => ({
    default: module.IncidentListPage,
  })),
);

export type IncidentListRouteProps = {
  currentUserAccount: LoginAccount;
  onOpenLogin: () => void;
};

export function IncidentListRoute({ currentUserAccount, onOpenLogin }: IncidentListRouteProps) {
  const navigate = useNavigate();

  return (
    <IncidentListPage
      currentUserAccount={currentUserAccount}
      onOpenSituationBoard={(incidentId, activeOpId) =>
        navigate(getIncidentBoardPath(incidentId), { state: activeOpId ? { activeOpId } : null })
      }
      onOpenLogin={onOpenLogin}
    />
  );
}
