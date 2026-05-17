import { incidentReadApi, type IncidentDetailResponse } from '../../incident/api/incidentReadApi';

export type IncidentDetailMissingPersonDto = {
  incidentId: string;
  displayName: string | null;
  photoObjectKey: string | null;
  appearanceText: string | null;
  lastSeenLocationText: string | null;
  lastSeenAt: string | null;
};

export type IncidentAssignmentDto = {
  accountId: string;
  accountDisplayName?: string | null;
  accountType?: string | null;
  organizationType?: string | null;
  incidentRole: string;
  assignedAt?: string;
};

export type ActiveIncidentDetailDto = {
  id: string;
  incidentId: string;
  status: 'OPEN' | string;
  version: number;
  missingPerson: IncidentDetailMissingPersonDto | null;
  assignments: IncidentAssignmentDto[];
};

export type TerminalIncidentDetailDto = {
  id: string;
  incidentId: string;
  status: 'CLOSED' | string;
  version: number;
  closedAt: string;
  writeDisabledReason: string;
};

export type IncidentDetailDto = IncidentDetailResponse;

export function getIncidentDetail(incidentId: string) {
  return incidentReadApi.detail(incidentId);
}
