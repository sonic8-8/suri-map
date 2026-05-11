import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient, type ApiClient, type ApiQuery } from '../../../shared/api';

export interface SearchPathListQuery {
  incidentId: string;
  opId?: string;
  policePhoneId?: string;
  includeGeometry?: boolean;
  geometryMode?: string;
  sinceVersion?: number;
  limit?: number;
  sort?: string;
  movementType?: string;
}

export interface SearchPathListResponse {
  paths: SearchPathItem[];
}

export interface SearchPathItem {
  id: string;
  incidentId: string;
  opId: string;
  policePhoneId: string;
  status: string;
  version: number;
}

export interface CorrectSearchPathSegmentRequest {
  movementType: string;
  reason?: string;
}

export interface CorrectSearchPathSegmentResponse {
  id: string;
  movementType: string;
  movementTypeSource: string;
  opId: string;
  policePhoneId: string;
  correctedByAccountId: string;
  correctedAt: string;
  version: number;
}

export interface CorrectSearchPathSegmentMutationVariables {
  searchPathSegmentId: string;
  request: CorrectSearchPathSegmentRequest;
  idempotencyKey: string;
}

export interface SearchPathApi {
  list(query: SearchPathListQuery): Promise<SearchPathListResponse>;
  correctSegment(
    searchPathSegmentId: string,
    request: CorrectSearchPathSegmentRequest,
    idempotencyKey: string,
  ): Promise<CorrectSearchPathSegmentResponse>;
}

export const searchPathQueryKeys = {
  all: ['searchPaths'] as const,
  list: (query: SearchPathListQuery) => [...searchPathQueryKeys.all, 'list', query] as const,
};

export function createSearchPathApi(client: ApiClient = apiClient): SearchPathApi {
  return {
    list: (query) =>
      client.get<SearchPathListResponse>('/search-paths', {
        query: toApiQuery(query),
      }),
    correctSegment: (searchPathSegmentId, request, idempotencyKey) =>
      client.patch<CorrectSearchPathSegmentResponse, CorrectSearchPathSegmentRequest>(
        `/search-path-segments/${searchPathSegmentId}`,
        request,
        idempotencyOptions(idempotencyKey),
      ),
  };
}

export const searchPathApi = createSearchPathApi();

export function useSearchPathListQuery(
  query: SearchPathListQuery,
  api: SearchPathApi = searchPathApi,
) {
  return useQuery({
    queryKey: searchPathQueryKeys.list(query),
    queryFn: () => api.list(query),
    enabled: Boolean(query.incidentId),
  });
}

export function useCorrectSearchPathSegmentMutation(api: SearchPathApi = searchPathApi) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      searchPathSegmentId,
      request,
      idempotencyKey,
    }: CorrectSearchPathSegmentMutationVariables) =>
      api.correctSegment(searchPathSegmentId, request, idempotencyKey),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: searchPathQueryKeys.all });
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

function toApiQuery(query: SearchPathListQuery): ApiQuery {
  return {
    incidentId: query.incidentId,
    opId: query.opId,
    policePhoneId: query.policePhoneId,
    includeGeometry: query.includeGeometry,
    geometryMode: query.geometryMode,
    sinceVersion: query.sinceVersion,
    limit: query.limit,
    sort: query.sort,
    movementType: query.movementType,
  };
}
