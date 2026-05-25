import { Navigate } from 'react-router-dom';

import { getIncidentSearchHistoryPath } from '../routes';
import { useRouteIncidentId } from '../useRouteIncidentId';

export function HandoverRoute() {
  const incidentId = useRouteIncidentId();
  return <Navigate to={getIncidentSearchHistoryPath(incidentId)} replace />;
}
