import { apiRequest } from '../../../shared/api/client';

export type AreaEditIncidentDetailMissingPersonDto = {
  incidentId: string;
  displayName: string | null;
  photoObjectKey: string | null;
  appearanceText: string | null;
  lastSeenLocationText: string | null;
  lastSeenAt: string | null;
};

export type AreaEditIncidentAssignmentDto = {
  accountId: string;
  incidentRole: string;
};

export type AreaEditActiveIncidentDetailDto = {
  id: string;
  incidentId: string;
  status: 'OPEN' | string;
  version: number;
  missingPerson: AreaEditIncidentDetailMissingPersonDto | null;
  assignments: AreaEditIncidentAssignmentDto[];
};

export type AreaEditTerminalIncidentDetailDto = {
  id: string;
  incidentId: string;
  status: 'CLOSED' | string;
  version: number;
  closedAt: string;
  writeDisabledReason: string;
};

export type AreaEditIncidentDetailDto = AreaEditActiveIncidentDetailDto | AreaEditTerminalIncidentDetailDto;

export function getAreaEditIncidentDetail(incidentId: string) {
  return apiRequest<AreaEditIncidentDetailDto>(`/incidents/${encodeURIComponent(incidentId)}`);
}
