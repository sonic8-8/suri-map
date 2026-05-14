import { apiRequest, createIdempotencyKey } from '../../../shared/api/client';

export type IncidentTerminalSnapshotDto = {
  id: string;
  incidentId: string;
  status: 'CLOSED' | string;
  version: number;
  closedAt: string;
  writeDisabledReason: string;
};

export type CloseIncidentRequestDto = {
  closeReason: string;
  confirmPersonalDataRemoval: true;
};

export type CloseIncidentResponseDto = {
  id: string;
  incidentId: string;
  status: 'CLOSED' | string;
  version: number;
  closedAt: string;
  terminalSnapshot: IncidentTerminalSnapshotDto;
  writeDisabledReason: string;
};

export function closeIncident(incidentId: string, request: CloseIncidentRequestDto) {
  return apiRequest<CloseIncidentResponseDto>(`/incidents/${encodeURIComponent(incidentId)}/close`, {
    method: 'POST',
    body: request,
    idempotencyKey: createIdempotencyKey('incident-close'),
  });
}
