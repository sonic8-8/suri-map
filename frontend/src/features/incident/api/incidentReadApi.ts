import { useQuery } from '@tanstack/react-query';
import { apiClient, type ApiClient } from '../../../shared/api';

export interface IncidentListResponse {
  items: IncidentListItem[];
}

export interface IncidentListItem {
  id: string;
  incidentId: string;
  title: string;
  status: string;
  version: number;
  closedAt: string | null;
}

export type IncidentDetailResponse = ActiveIncidentDetailResponse | TerminalIncidentDetailResponse;

export interface ActiveIncidentDetailResponse {
  id: string;
  incidentId: string;
  title: string;
  status: 'OPEN';
  openedAt: string | null;
  version: number;
  missingPerson: IncidentMissingPersonSummary | null;
  assignments: IncidentAssignmentSummary[];
}

export interface TerminalIncidentDetailResponse {
  id: string;
  incidentId: string;
  status: 'CLOSED';
  version: number;
  closedAt: string;
  terminalSnapshot: IncidentTerminalSnapshot;
  writeDisabledReason: string;
}

export interface IncidentMissingPersonSummary {
  incidentId: string;
  displayName: string;
  photoObjectKey: string;
  appearanceText: string;
  lastSeenLocationText: string;
  lastSeenAt: string;
}

export interface IncidentAssignmentSummary {
  accountId: string;
  accountDisplayName: string | null;
  accountType: string | null;
  organizationType: string | null;
  incidentRole: string;
  assignedAt: string;
}

export interface IncidentTerminalSnapshot {
  id: string;
  incidentId: string;
  status: string;
  version: number;
  closedAt: string;
  writeDisabledReason: string;
}

export const incidentQueryKeys = {
  all: ['incidents'] as const,
  list: (status?: string) => [...incidentQueryKeys.all, 'list', status ?? 'all'] as const,
  detail: (incidentId: string) => [...incidentQueryKeys.all, 'detail', incidentId] as const,
};

export function createIncidentReadApi(client: ApiClient = apiClient) {
  return {
    list: (status?: string) =>
      client.get<IncidentListResponse>('/incidents', {
        query: status ? { status } : undefined,
      }),
    detail: (incidentId: string) => client.get<IncidentDetailResponse>(`/incidents/${incidentId}`),
  };
}

export const incidentReadApi = createIncidentReadApi();

export function useIncidentListQuery(status?: string) {
  return useQuery({
    queryKey: incidentQueryKeys.list(status),
    queryFn: () => incidentReadApi.list(status),
  });
}

export function useIncidentDetailQuery(incidentId: string | null | undefined) {
  return useQuery({
    queryKey: incidentQueryKeys.detail(incidentId ?? ''),
    queryFn: () => incidentReadApi.detail(incidentId ?? ''),
    enabled: Boolean(incidentId),
  });
}
