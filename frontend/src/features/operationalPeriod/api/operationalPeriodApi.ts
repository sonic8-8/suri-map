import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient, type ApiClient } from '../../../shared/api';

export type OperationalPeriodStatus = 'ACTIVE' | 'ENDED';
export type OperationalPeriodReason = 'INITIAL' | 'RE_SEARCH' | 'AREA_CHANGED' | 'OTHER';
export type CreateOperationalPeriodReason = Exclude<OperationalPeriodReason, 'INITIAL'>;

export interface OperationalPeriodListItem {
  id: string;
  status: OperationalPeriodStatus;
  reason: OperationalPeriodReason;
  sequenceNumber: number;
  openedAt: string;
  endedAt: string | null;
  version: number;
}

export interface OperationalPeriodListResponse {
  currentOpId: string | null;
  items: OperationalPeriodListItem[];
}

export interface CreateOperationalPeriodRequest {
  incidentId: string;
  reason: CreateOperationalPeriodReason;
  reasonMemo?: string;
  handoverMemo?: string;
  clientTs: string;
}

export interface CreateOperationalPeriodResponse {
  id: string;
  incidentId: string;
  status: 'ACTIVE';
  reason: string;
  version: number;
  sequenceNumber: number;
  openedAt: string;
  endedAt: string | null;
}

export interface OperationalPeriodApi {
  list(incidentId: string): Promise<OperationalPeriodListResponse>;
  create(
    request: CreateOperationalPeriodRequest,
    idempotencyKey: string,
  ): Promise<CreateOperationalPeriodResponse>;
}

export interface CreateOperationalPeriodMutationVariables {
  request: CreateOperationalPeriodRequest;
  idempotencyKey: string;
}

export const operationalPeriodQueryKeys = {
  all: ['operationalPeriods'] as const,
  list: (incidentId: string) => [...operationalPeriodQueryKeys.all, 'list', incidentId] as const,
};

export function createOperationalPeriodApi(client: ApiClient = apiClient): OperationalPeriodApi {
  return {
    list: (incidentId) => client.get<OperationalPeriodListResponse>(operationalPeriodListPath(incidentId)),
    create: (request, idempotencyKey) =>
      client.post<CreateOperationalPeriodResponse, CreateOperationalPeriodRequest>(
        '/operational-periods',
        request,
        idempotencyOptions(idempotencyKey),
      ),
  };
}

export const operationalPeriodApi = createOperationalPeriodApi();

export function useOperationalPeriodListQuery(
  incidentId: string | null | undefined,
  api: OperationalPeriodApi = operationalPeriodApi,
) {
  return useQuery({
    queryKey: operationalPeriodQueryKeys.list(incidentId ?? ''),
    queryFn: () => api.list(incidentId ?? ''),
    enabled: Boolean(incidentId),
  });
}

export function useCreateOperationalPeriodMutation(api: OperationalPeriodApi = operationalPeriodApi) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ request, idempotencyKey }: CreateOperationalPeriodMutationVariables) =>
      api.create(request, idempotencyKey),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: operationalPeriodQueryKeys.all });
    },
  });
}

function operationalPeriodListPath(incidentId: string): string {
  return `/incidents/${encodeURIComponent(incidentId)}/operational-periods`;
}

function idempotencyOptions(idempotencyKey: string) {
  return {
    headers: {
      'Idempotency-Key': idempotencyKey,
    },
  };
}
