import { ApiError, apiRequest, getStoredAccessToken } from '../../../shared/api/client';

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
  const accessToken = getStoredAccessToken();
  if (!accessToken) {
    throw new ApiError(401, 'unauthorized');
  }

  return apiRequest<IncidentListResponseDto>('/incidents', { accessToken });
}
