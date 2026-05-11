export const BOOTSTRAP_INCIDENT_ID = 'inc-precinct-first-001';

export const ROUTES = {
  home: '/',
  login: '/login',
  incidentList: '/incidents',
  incidentBoard: '/incidents/:incidentId/board',
  incidentHandover: '/incidents/:incidentId/handover',
  areaEdit: '/incidents/:incidentId/area-edit',
  incidentClose: '/incidents/:incidentId/close',
  legacyAreaEdit: '/area-edit',
  legacyIncidentClose: '/incident-close',
} as const;

export function getIncidentBoardPath(incidentId: string) {
  return `/incidents/${incidentId}/board`;
}

export function getIncidentHandoverPath(incidentId: string) {
  return `/incidents/${incidentId}/handover`;
}

export function getAreaEditPath(incidentId: string) {
  return getIncidentBoardPath(incidentId);
}

export function getIncidentClosePath(incidentId: string) {
  return `/incidents/${incidentId}/close`;
}
