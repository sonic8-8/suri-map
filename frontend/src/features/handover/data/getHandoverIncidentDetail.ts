import { apiRequest } from '../../../shared/api/client';

export type HandoverIncidentDetailMissingPersonDto = {
  incidentId: string;
  displayName: string | null;
  photoObjectKey: string | null;
  appearanceText: string | null;
  lastSeenLocationText: string | null;
  lastSeenAt: string | null;
};

export type ActiveHandoverIncidentDetailDto = {
  id: string;
  incidentId: string;
  status: 'OPEN' | string;
  version: number;
  missingPerson: HandoverIncidentDetailMissingPersonDto | null;
  assignments: unknown[];
};

export type TerminalHandoverIncidentDetailDto = {
  id: string;
  incidentId: string;
  status: 'CLOSED' | string;
  version: number;
  closedAt: string;
  writeDisabledReason: string;
};

export type HandoverIncidentDetailDto =
  | ActiveHandoverIncidentDetailDto
  | TerminalHandoverIncidentDetailDto;

export function getHandoverIncidentDetail(incidentId: string) {
  return apiRequest<HandoverIncidentDetailDto>(`/incidents/${encodeURIComponent(incidentId)}`);
}
