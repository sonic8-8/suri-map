import { apiRequest } from '../../../shared/api/client';

export type IncidentListItemDto = {
  id: string;
  incidentId: string;
  title: string;
  status: 'OPEN' | 'CLOSED' | string;
  version: number;
  closedAt: string | null;
};

export type IncidentListResponseDto = {
  items: IncidentListItemDto[];
};

export function getIncidents() {
  return apiRequest<IncidentListResponseDto>('/incidents');
}
