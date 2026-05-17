import { incidentReadApi, type IncidentDetailResponse } from '../../incident/api/incidentReadApi';

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
  accountDisplayName?: string | null;
  accountType?: string | null;
  organizationType?: string | null;
  incidentRole: string;
  assignedAt?: string;
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

export type AreaEditIncidentDetailDto = IncidentDetailResponse;

export function getAreaEditIncidentDetail(incidentId: string) {
  return incidentReadApi.detail(incidentId);
}
