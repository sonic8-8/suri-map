import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient, type ApiClient, type ApiQuery } from '../../../shared/api';
import type { AreaColorToken } from '../../../shared/constants/areaColorTokens';

export type SearchAreaLevel = 'OVERALL' | 'UNIT' | 'TEAM';
export type SearchAreaStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELLED';

export interface GeoJsonPolygon {
  type: 'Polygon';
  coordinates: number[][][];
}

export interface CreateSearchAreaRequest {
  incidentId: string;
  opId?: string;
  areaLevel: SearchAreaLevel;
  geometry: GeoJsonPolygon;
  memo?: string;
  clientTs: string;
}

export interface UpdateSearchAreaRequest {
  opId?: string;
  geometry?: GeoJsonPolygon;
  memo?: string;
  expectedVersion?: number;
  nextStatus?: SearchAreaStatus;
  clientTs: string;
}

export interface SplitSearchAreaRequest {
  opId: string;
  children: GeoJsonPolygon[];
  memo?: string;
  expectedVersion: number;
  clientTs: string;
}

export interface AssignSearchAreaRequest {
  incidentId: string;
  opId: string;
  assigneeAccountIds: string[];
  memo?: string;
  clientTs: string;
}

export interface SearchAreaQuery {
  incidentId: string;
  opId?: string;
  areaLevel?: SearchAreaLevel;
  status?: SearchAreaStatus;
}

export interface SearchAreaResponse {
  id: string;
  incidentId: string;
  opId?: string;
  parentAreaId?: string;
  areaLevel?: SearchAreaLevel;
  status: SearchAreaStatus;
  colorToken?: AreaColorToken;
  historyCount?: number;
  version: number;
  geometry: GeoJsonPolygon;
  bbox?: number[];
  updatedAt?: string;
}

export interface SearchAreaCollectionResponse {
  incidentId: string;
  sourceVersion: number;
  areas: SearchAreaResponse[];
}

export interface SearchAreaSplitResponse {
  parentAreaId: string;
  parent: SearchAreaResponse;
  createdAreaIds: string[];
  children: SearchAreaResponse[];
}

export interface SearchAreaAssignmentResponse {
  searchAreaId: string;
  opId: string;
  assignmentIds: string[];
  version: number;
}

export interface SearchAreaApi {
  create(request: CreateSearchAreaRequest, idempotencyKey: string): Promise<SearchAreaResponse>;
  fetchActiveOverall(incidentId: string): Promise<SearchAreaResponse>;
  list(query: SearchAreaQuery): Promise<SearchAreaCollectionResponse>;
  update(searchAreaId: string, request: UpdateSearchAreaRequest, idempotencyKey: string): Promise<SearchAreaResponse>;
  split(
    searchAreaId: string,
    request: SplitSearchAreaRequest,
    idempotencyKey: string,
  ): Promise<SearchAreaSplitResponse>;
  assign(
    searchAreaId: string,
    request: AssignSearchAreaRequest,
    idempotencyKey: string,
  ): Promise<SearchAreaAssignmentResponse>;
}

export interface CreateSearchAreaMutationVariables {
  request: CreateSearchAreaRequest;
  idempotencyKey: string;
}

export interface UpdateSearchAreaMutationVariables {
  searchAreaId: string;
  request: UpdateSearchAreaRequest;
  idempotencyKey: string;
}

export interface SplitSearchAreaMutationVariables {
  searchAreaId: string;
  request: SplitSearchAreaRequest;
  idempotencyKey: string;
}

export interface AssignSearchAreaMutationVariables {
  searchAreaId: string;
  request: AssignSearchAreaRequest;
  idempotencyKey: string;
}

export const searchAreaQueryKeys = {
  all: ['searchAreas'] as const,
  activeOverall: (incidentId: string) => [...searchAreaQueryKeys.all, 'activeOverall', incidentId] as const,
  list: (query: SearchAreaQuery) => [...searchAreaQueryKeys.all, 'list', query] as const,
};

export function createSearchAreaApi(client: ApiClient = apiClient): SearchAreaApi {
  return {
    create: (request, idempotencyKey) =>
      client.post<SearchAreaResponse, CreateSearchAreaRequest>(
        '/search-areas',
        request,
        idempotencyOptions(idempotencyKey),
      ),
    fetchActiveOverall: (incidentId) =>
      client.get<SearchAreaResponse>('/search-areas', {
        query: {
          incidentId,
          areaLevel: 'OVERALL',
          status: 'ACTIVE',
        },
      }),
    list: (query) =>
      client.get<SearchAreaCollectionResponse>('/search-areas', {
        query: toApiQuery(query),
      }),
    update: (searchAreaId, request, idempotencyKey) =>
      client.patch<SearchAreaResponse, UpdateSearchAreaRequest>(
        `/search-areas/${searchAreaId}`,
        request,
        idempotencyOptions(idempotencyKey),
      ),
    split: (searchAreaId, request, idempotencyKey) =>
      client.post<SearchAreaSplitResponse, SplitSearchAreaRequest>(
        `/search-areas/${searchAreaId}/split`,
        request,
        idempotencyOptions(idempotencyKey),
      ),
    assign: (searchAreaId, request, idempotencyKey) =>
      client.post<SearchAreaAssignmentResponse, AssignSearchAreaRequest>(
        `/search-areas/${searchAreaId}/assignments`,
        request,
        idempotencyOptions(idempotencyKey),
      ),
  };
}

export const searchAreaApi = createSearchAreaApi();

export function useActiveOverallSearchAreaQuery(
  incidentId: string | null | undefined,
  api: SearchAreaApi = searchAreaApi,
) {
  return useQuery({
    queryKey: searchAreaQueryKeys.activeOverall(incidentId ?? ''),
    queryFn: () => api.fetchActiveOverall(incidentId ?? ''),
    enabled: Boolean(incidentId),
  });
}

export function useSearchAreaListQuery(query: SearchAreaQuery, api: SearchAreaApi = searchAreaApi) {
  return useQuery({
    queryKey: searchAreaQueryKeys.list(query),
    queryFn: () => api.list(query),
    enabled: Boolean(query.incidentId),
  });
}

export function useCreateSearchAreaMutation(api: SearchAreaApi = searchAreaApi) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ request, idempotencyKey }: CreateSearchAreaMutationVariables) => api.create(request, idempotencyKey),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: searchAreaQueryKeys.all });
    },
  });
}

export function useUpdateSearchAreaMutation(api: SearchAreaApi = searchAreaApi) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ searchAreaId, request, idempotencyKey }: UpdateSearchAreaMutationVariables) =>
      api.update(searchAreaId, request, idempotencyKey),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: searchAreaQueryKeys.all });
    },
  });
}

export function useSplitSearchAreaMutation(api: SearchAreaApi = searchAreaApi) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ searchAreaId, request, idempotencyKey }: SplitSearchAreaMutationVariables) =>
      api.split(searchAreaId, request, idempotencyKey),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: searchAreaQueryKeys.all });
    },
  });
}

export function useAssignSearchAreaMutation(api: SearchAreaApi = searchAreaApi) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ searchAreaId, request, idempotencyKey }: AssignSearchAreaMutationVariables) =>
      api.assign(searchAreaId, request, idempotencyKey),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: searchAreaQueryKeys.all });
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

function toApiQuery(query: SearchAreaQuery): ApiQuery {
  return {
    incidentId: query.incidentId,
    opId: query.opId,
    areaLevel: query.areaLevel,
    status: query.status,
  };
}
