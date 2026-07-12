import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient, type ApiClient, type ApiQuery } from '../../../shared/api';

export interface SearchPathListQuery {
  incidentId: string;
  opId?: string;
  accountId?: string;
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
  accountId: string;
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
  correctedByAccountId: string;
  correctedAt: string;
  version: number;
}

export interface StartSearchPathResponse {
  id: string;
  incidentId: string;
  opId: string;
  accountId: string;
  version: number;
  status: string;
}

export interface AppendSearchPathBatchResponse {
  id: string;
  dutyShiftId?: string | null;
  opId: string;
  accountId: string;
  acceptedPointCount: number;
  excludedPointCount: number;
  version: number;
  status: string;
}

export interface PatchSearchPathResponse {
  id: string;
  version: number;
  status: string;
}

export interface ManualSearchPathPointInput {
  pointId: string;
  lon: number;
  lat: number;
  clientTs: string;
  speedMps?: number | null;
  horizontalAccuracyM?: number | null;
}

export interface CreateManualSearchPathRequest {
  incidentId: string;
  opId: string;
  policePhoneId: string;
  searchPathId: string;
  startedAt: string;
  endedAt: string;
  points: ManualSearchPathPointInput[];
  clockOffsetMs?: number | null;
}

export interface CreateManualSearchPathResponse {
  searchPathId: string;
  start: StartSearchPathResponse;
  batch: AppendSearchPathBatchResponse;
  end: PatchSearchPathResponse;
}

export interface CorrectSearchPathSegmentMutationVariables {
  searchPathSegmentId: string;
  request: CorrectSearchPathSegmentRequest;
  idempotencyKey: string;
}

export interface CreateManualSearchPathMutationVariables {
  request: CreateManualSearchPathRequest;
  idempotencyKey: string;
}

export interface SearchPathApi {
  list(query: SearchPathListQuery): Promise<SearchPathListResponse>;
  createManualPath(
    request: CreateManualSearchPathRequest,
    idempotencyKey: string,
  ): Promise<CreateManualSearchPathResponse>;
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
    createManualPath: async (request, idempotencyKey) => {
      const start = await client.post<StartSearchPathResponse, StartSearchPathRequest>(
        '/search-paths',
        {
          searchPathId: request.searchPathId,
          incidentId: request.incidentId,
          opId: request.opId,
          clientTs: request.startedAt,
          ...(request.clockOffsetMs === undefined ? {} : { clockOffsetMs: request.clockOffsetMs }),
        },
        manualRouteWriteOptions(`${idempotencyKey}:start`, request.policePhoneId),
      );
      const batch = await client.post<AppendSearchPathBatchResponse, AppendSearchPathBatchRequest>(
        '/search-paths/batch',
        {
          incidentId: request.incidentId,
          opId: request.opId,
          pathId: request.searchPathId,
          points: request.points.map(toPathBatchPointRequest),
          ...(request.clockOffsetMs === undefined ? {} : { clockOffsetMs: request.clockOffsetMs }),
        },
        manualRouteWriteOptions(`${idempotencyKey}:batch`, request.policePhoneId),
      );
      const end = await client.patch<PatchSearchPathResponse, PatchSearchPathRequest>(
        `/search-paths/${request.searchPathId}`,
        {
          action: 'END',
          clientTs: request.endedAt,
          ...(request.clockOffsetMs === undefined ? {} : { clockOffsetMs: request.clockOffsetMs }),
        },
        manualRouteWriteOptions(`${idempotencyKey}:end`, request.policePhoneId),
      );
      return { searchPathId: request.searchPathId, start, batch, end };
    },
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

export function useCreateManualSearchPathMutation(api: SearchPathApi = searchPathApi) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ request, idempotencyKey }: CreateManualSearchPathMutationVariables) =>
      api.createManualPath(request, idempotencyKey),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: searchPathQueryKeys.all });
    },
  });
}

interface StartSearchPathRequest {
  searchPathId?: string;
  incidentId: string;
  opId: string;
  clientTs: string;
  clockOffsetMs?: number | null;
}

interface AppendSearchPathBatchRequest {
  incidentId: string;
  opId: string;
  pathId: string;
  points: PathBatchPointRequest[];
  clockOffsetMs?: number | null;
}

interface PathBatchPointRequest {
  pointId: string;
  lon: number;
  lat: number;
  speedMps?: number | null;
  horizontalAccuracyM?: number | null;
  clientTs: string;
}

interface PatchSearchPathRequest {
  action: 'END';
  clientTs: string;
  clockOffsetMs?: number | null;
}

function idempotencyOptions(idempotencyKey: string) {
  return {
    headers: {
      'Idempotency-Key': idempotencyKey,
    },
  };
}

function manualRouteWriteOptions(idempotencyKey: string, policePhoneId: string) {
  return {
    idempotencyKey,
    clientChannel: 'WEB' as const,
    policePhoneId,
  };
}

function toPathBatchPointRequest(point: ManualSearchPathPointInput): PathBatchPointRequest {
  return {
    pointId: point.pointId,
    lon: point.lon,
    lat: point.lat,
    speedMps: point.speedMps ?? null,
    horizontalAccuracyM: point.horizontalAccuracyM ?? null,
    clientTs: point.clientTs,
  };
}

function toApiQuery(query: SearchPathListQuery): ApiQuery {
  return {
    incidentId: query.incidentId,
    opId: query.opId,
    accountId: query.accountId,
    includeGeometry: query.includeGeometry,
    geometryMode: query.geometryMode,
    sinceVersion: query.sinceVersion,
    limit: query.limit,
    sort: query.sort,
    movementType: query.movementType,
  };
}
