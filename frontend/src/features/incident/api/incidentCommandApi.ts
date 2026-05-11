import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, type ApiClient } from '../../../shared/api';
import { incidentQueryKeys, type IncidentTerminalSnapshot } from './incidentReadApi';

export interface ImportIncidentRequest {
  sourceIncidentId: string;
}

export interface ImportIncidentResponse {
  id: string;
  incidentId: string;
  status: string;
  version: number;
  assignmentAccountIds: string[];
}

export interface CloseIncidentRequest {
  closeReason: string;
  confirmPersonalDataRemoval: boolean;
}

export interface CloseIncidentResponse {
  id: string;
  incidentId: string;
  status: 'CLOSED';
  version: number;
  closedAt: string;
  terminalSnapshot: IncidentTerminalSnapshot;
  writeDisabledReason: string;
}

export interface IncidentCommandApi {
  importIncident(
    request: ImportIncidentRequest,
    idempotencyKey: string,
  ): Promise<ImportIncidentResponse>;
  closeIncident(
    incidentId: string,
    request: CloseIncidentRequest,
    idempotencyKey: string,
  ): Promise<CloseIncidentResponse>;
}

export interface ImportIncidentMutationVariables {
  request: ImportIncidentRequest;
  idempotencyKey: string;
}

export interface CloseIncidentMutationVariables {
  incidentId: string;
  request: CloseIncidentRequest;
  idempotencyKey: string;
}

export function createIncidentCommandApi(client: ApiClient = apiClient): IncidentCommandApi {
  return {
    importIncident: (request, idempotencyKey) =>
      client.post<ImportIncidentResponse, ImportIncidentRequest>(
        '/incidents/import',
        request,
        idempotencyOptions(idempotencyKey),
      ),
    closeIncident: (incidentId, request, idempotencyKey) =>
      client.post<CloseIncidentResponse, CloseIncidentRequest>(
        `/incidents/${incidentId}/close`,
        request,
        idempotencyOptions(idempotencyKey),
      ),
  };
}

export const incidentCommandApi = createIncidentCommandApi();

export function useImportIncidentMutation(commandApi: IncidentCommandApi = incidentCommandApi) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ request, idempotencyKey }: ImportIncidentMutationVariables) =>
      commandApi.importIncident(request, idempotencyKey),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: incidentQueryKeys.all });
    },
  });
}

export function useCloseIncidentMutation(commandApi: IncidentCommandApi = incidentCommandApi) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ incidentId, request, idempotencyKey }: CloseIncidentMutationVariables) =>
      commandApi.closeIncident(incidentId, request, idempotencyKey),
    onSuccess: (_response, variables) => {
      void queryClient.invalidateQueries({ queryKey: incidentQueryKeys.all });
      void queryClient.invalidateQueries({
        queryKey: incidentQueryKeys.detail(variables.incidentId),
      });
    },
  });
}

function idempotencyOptions(idempotencyKey: string) {
  return {
    headers: {
      'Idempotency-Key': idempotencyKey,
    },
  };
}
