export const BOOTSTRAP_INCIDENT_ID = 'inc-precinct-first-001';

export const ROUTES = {
  home: '/',
  login: '/login',
  authCallback: '/auth/callback',
  incidentList: '/incidents',
  incidentDetail: '/incidents/:incidentId',
  incidentBoard: '/incidents/:incidentId/board',
  incidentHandover: '/incidents/:incidentId/handover',
  incidentOfflinePackage: '/incidents/:incidentId/offline-package',
  areaEdit: '/incidents/:incidentId/area-edit',
  incidentClose: '/incidents/:incidentId/close',
  legacyAreaEdit: '/area-edit',
  legacyIncidentClose: '/incident-close',
} as const;

export function getIncidentBoardPath(incidentId: string) {
  return `/incidents/${incidentId}/board`;
}

export function getIncidentDetailPath(incidentId: string) {
  return `/incidents/${incidentId}`;
}

export function getIncidentHandoverPath(incidentId: string) {
  return `/incidents/${incidentId}/handover`;
}

export function getIncidentOfflinePackagePath(incidentId: string) {
  return `/incidents/${incidentId}/offline-package`;
}

export function getAreaEditPath(incidentId: string) {
  return `/incidents/${incidentId}/area-edit`;
}

export function getIncidentClosePath(incidentId: string) {
  return `/incidents/${incidentId}/close`;
}
