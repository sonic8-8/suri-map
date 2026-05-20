import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient, type ApiClient, type ApiQuery } from '../../../shared/api';

export type DutyShiftStatus = 'ACTIVE' | 'ENDED';
export type HandoverMemoTargetType =
  | 'OPERATIONAL_PERIOD'
  | 'DUTY_SHIFT'
  | 'SEARCH_PATH'
  | 'SEARCH_AREA'
  | 'MARKER';
export type SearchHistorySummaryStatus = 'GENERATING' | 'READY' | 'FAILED';
export type SearchHistorySummaryDisplayStatus = 'LOADING' | 'READY' | 'UNAVAILABLE';
export type SearchHistorySummarySourceReadiness = 'PENDING_SYNC' | 'READY' | 'STALE';
export type SearchHistorySummaryScopeType = 'OP' | 'DUTY_SHIFT';

export interface DutyShiftQuery {
  incidentId: string;
  opId?: string;
  policePhoneId?: string;
  accountId?: string;
  status?: DutyShiftStatus;
}

export interface DutyShiftResponse {
  id: string;
  incidentId: string;
  opId: string;
  policePhoneId: string;
  status: DutyShiftStatus;
  version: number;
}

export interface DutyShiftListResponse {
  items: DutyShiftResponse[];
}

export interface HandoverMemoQuery {
  incidentId: string;
  opId?: string;
  memoTargetType?: HandoverMemoTargetType;
  memoTargetId?: string;
}

export interface CreateHandoverMemoRequest {
  incidentId: string;
  opId: string;
  memoTargetType: HandoverMemoTargetType;
  memoTargetId?: string;
  content: string;
  clientTs: string;
}

export interface HandoverMemoResponse {
  id: string;
  opId: string;
  version: number;
  memoTargetType: HandoverMemoTargetType;
  memoTargetId: string;
}

export interface HandoverMemoListItem {
  id: string;
  incidentId: string;
  opId: string;
  memoTargetType: HandoverMemoTargetType;
  memoTargetId: string;
  content: string;
  createdByAccountId: string;
  createdAt: string;
  version: number;
}

export interface HandoverMemoListResponse {
  items: HandoverMemoListItem[];
}

export interface SearchHistorySummaryQuery {
  incidentId: string;
  scopeType?: SearchHistorySummaryScopeType;
  scopeId?: string;
  dutyShiftId?: string;
  status?: SearchHistorySummaryStatus;
}

export interface SearchHistorySummaryItem {
  summaryId: string;
  opId: string;
  scopeType: SearchHistorySummaryScopeType;
  scopeId: string;
  dutyShiftId?: string;
  status: SearchHistorySummaryStatus;
  displayStatus: SearchHistorySummaryDisplayStatus;
  content?: string;
  sourceReadiness: SearchHistorySummarySourceReadiness;
  sourceHash: string;
  generatedAt?: string;
  version: number;
}

export interface SearchHistorySummaryListResponse {
  items: SearchHistorySummaryItem[];
}

export interface HandoverApi {
  listDutyShifts(query: DutyShiftQuery): Promise<DutyShiftListResponse>;
  createHandoverMemo(
    request: CreateHandoverMemoRequest,
    idempotencyKey: string,
  ): Promise<HandoverMemoResponse>;
  listHandoverMemos(query: HandoverMemoQuery): Promise<HandoverMemoListResponse>;
  listSearchHistorySummaries(
    operationalPeriodId: string,
    query: SearchHistorySummaryQuery,
  ): Promise<SearchHistorySummaryListResponse>;
}

export interface CreateHandoverMemoMutationVariables {
  request: CreateHandoverMemoRequest;
  idempotencyKey: string;
}

type QueryRefetchInterval<TData> =
  | number
  | false
  | ((query: { state: { data: TData | undefined } }) => number | false | undefined);

type QueryOptions<TData> = {
  refetchInterval?: QueryRefetchInterval<TData>;
};

export const handoverQueryKeys = {
  all: ['handover'] as const,
  dutyShifts: (query: DutyShiftQuery) => [...handoverQueryKeys.all, 'dutyShifts', query] as const,
  memos: (query: HandoverMemoQuery) => [...handoverQueryKeys.all, 'memos', query] as const,
  summaries: (operationalPeriodId: string, query: SearchHistorySummaryQuery) =>
    [...handoverQueryKeys.all, 'summaries', operationalPeriodId, query] as const,
};

export function createHandoverApi(client: ApiClient = apiClient): HandoverApi {
  return {
    listDutyShifts: (query) =>
      client.get<DutyShiftListResponse>('/duty-shifts', {
        query: toDutyShiftQuery(query),
      }),
    createHandoverMemo: (request, idempotencyKey) =>
      client.post<HandoverMemoResponse, CreateHandoverMemoRequest>(
        '/handover-memos',
        request,
        idempotencyOptions(idempotencyKey),
      ),
    listHandoverMemos: (query) =>
      client.get<HandoverMemoListResponse>('/handover-memos', {
        query: toHandoverMemoQuery(query),
      }),
    listSearchHistorySummaries: (operationalPeriodId, query) =>
      client.get<SearchHistorySummaryListResponse>(
        `/operational-periods/${encodeURIComponent(operationalPeriodId)}/search-history-summaries`,
        { query: toSearchHistorySummaryQuery(query) },
      ),
  };
}

export const handoverApi = createHandoverApi();

export function useDutyShiftListQuery(query: DutyShiftQuery, api: HandoverApi = handoverApi) {
  return useQuery({
    queryKey: handoverQueryKeys.dutyShifts(query),
    queryFn: () => api.listDutyShifts(query),
    enabled: Boolean(query.incidentId),
  });
}

export function useHandoverMemoListQuery(query: HandoverMemoQuery, api: HandoverApi = handoverApi) {
  return useQuery({
    queryKey: handoverQueryKeys.memos(query),
    queryFn: () => api.listHandoverMemos(query),
    enabled: Boolean(query.incidentId),
  });
}

export function useCreateHandoverMemoMutation(api: HandoverApi = handoverApi) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ request, idempotencyKey }: CreateHandoverMemoMutationVariables) =>
      api.createHandoverMemo(request, idempotencyKey),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: handoverQueryKeys.all });
    },
  });
}

export function useSearchHistorySummaryListQuery(
  operationalPeriodId: string | null | undefined,
  query: SearchHistorySummaryQuery,
  api: HandoverApi = handoverApi,
  options: QueryOptions<SearchHistorySummaryListResponse> = {},
) {
  return useQuery({
    queryKey: handoverQueryKeys.summaries(operationalPeriodId ?? '', query),
    queryFn: () => api.listSearchHistorySummaries(operationalPeriodId ?? '', query),
    enabled: Boolean(operationalPeriodId && query.incidentId),
    refetchInterval: options.refetchInterval,
  });
}

function idempotencyOptions(idempotencyKey: string) {
  return {
    headers: {
      'Idempotency-Key': idempotencyKey,
    },
  };
}

function toDutyShiftQuery(query: DutyShiftQuery): ApiQuery {
  return {
    incidentId: query.incidentId,
    opId: query.opId,
    policePhoneId: query.policePhoneId,
    accountId: query.accountId,
    status: query.status,
  };
}

function toHandoverMemoQuery(query: HandoverMemoQuery): ApiQuery {
  return {
    incidentId: query.incidentId,
    opId: query.opId,
    memoTargetType: query.memoTargetType,
    memoTargetId: query.memoTargetId,
  };
}

function toSearchHistorySummaryQuery(query: SearchHistorySummaryQuery): ApiQuery {
  return {
    incidentId: query.incidentId,
    scopeType: query.scopeType,
    scopeId: query.scopeId,
    dutyShiftId: query.dutyShiftId,
    status: query.status,
  };
}
