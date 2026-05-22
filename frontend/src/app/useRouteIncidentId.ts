import { useParams } from 'react-router-dom';

import { BOOTSTRAP_INCIDENT_ID } from './routes';

export function useRouteIncidentId() {
  const { incidentId } = useParams();
  return incidentId ?? BOOTSTRAP_INCIDENT_ID;
}
