import { apiRequest, createIdempotencyKey } from '../../../shared/api/client';

export type ImportIncidentResponseDto = {
  id: string;
  incidentId: string;
  status: 'OPEN' | 'CLOSED' | string;
  version: number;
  assignmentAccountIds: string[];
};

export async function importIncident(sourceIncidentId: string) {
  return apiRequest<ImportIncidentResponseDto>('/incidents/import', {
    method: 'POST',
    idempotencyKey: createIdempotencyKey('incident-import'),
    body: {
      sourceIncidentId,
    },
  });
}
