import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, type ApiClient } from '../../../shared/api';

export type OpComparisonAnalysisStatus = 'GENERATING' | 'READY' | 'FAILED';
export type OpComparisonNarrativeStatus = 'SKIPPED' | 'GENERATING' | 'READY' | 'FAILED';
export type OpComparisonDiffFactType = 'METRIC_DIFF' | 'REGION_TIME_DIFF';
export type OpComparisonRegionFactType = 'COMMON_REGION' | 'DIFFERENT_REGION';

export interface CreateOpComparisonRequest {
  incidentId: string;
  operationalPeriodIds: string[];
}

export interface OpComparisonMetric {
  operationalPeriodId: string;
  sequenceNumber: number;
  startedAt: string;
  endedAt: string | null;
  pathDistanceMeters: number;
  walkingDistanceMeters: number;
  drivingDistanceMeters: number;
  walkingRatioPercent: number;
  averageSpeedKmh: number;
  stoppedSegmentCount: number;
  stoppedDurationSeconds: number;
  markerCount: number;
  handoverMemoCount: number;
}

export interface OpComparisonDiffFact {
  factId: string;
  type: OpComparisonDiffFactType;
  metricKey: string;
  leftOperationalPeriodId: string;
  rightOperationalPeriodId: string;
  leftValue: number;
  rightValue: number;
  delta: number;
  threshold: string;
}

export interface OpComparisonRegionOccupancy {
  operationalPeriodId: string;
  firstObservedAt: string;
  lastObservedAt: string;
  durationSeconds: number;
}

export interface OpComparisonRegionFact {
  factId: string;
  type: OpComparisonRegionFactType;
  operationalPeriodIds: string[];
  geometryGeojson: string | null;
  areaSquareMeters: number;
  occupancies: OpComparisonRegionOccupancy[];
}

export interface OpComparisonObservation {
  sentence: string;
  factIds: string[];
}

export interface OpComparisonObservationEnvelope {
  observations: OpComparisonObservation[];
}

export interface OpComparisonResponse {
  comparisonId: string;
  incidentId: string;
  operationalPeriodIds: string[];
  sourceHash: string;
  status: OpComparisonAnalysisStatus;
  narrativeStatus: OpComparisonNarrativeStatus;
  metrics: OpComparisonMetric[];
  diffFacts: OpComparisonDiffFact[];
  regionFacts: OpComparisonRegionFact[];
  observations?: OpComparisonObservationEnvelope | null;
  failureReason?: string | null;
  requestedAt: string;
  generatedAt?: string | null;
  version: number;
}

export interface OpComparisonApi {
  create(
    request: CreateOpComparisonRequest,
    idempotencyKey: string,
  ): Promise<OpComparisonResponse>;
}

export interface CreateOpComparisonMutationVariables {
  request: CreateOpComparisonRequest;
  idempotencyKey: string;
}

export const opComparisonQueryKeys = {
  all: ['opComparisonAnalysis'] as const,
  byIncident: (incidentId: string) => [...opComparisonQueryKeys.all, 'incident', incidentId] as const,
};

export function createOpComparisonApi(client: ApiClient = apiClient): OpComparisonApi {
  return {
    create: (request, idempotencyKey) =>
      client.post<OpComparisonResponse, CreateOpComparisonRequest>(
        '/operational-periods/comparisons',
        request,
        idempotencyOptions(idempotencyKey),
      ),
  };
}

export const opComparisonApi = createOpComparisonApi();

export function useCreateOpComparisonMutation(api: OpComparisonApi = opComparisonApi) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ request, idempotencyKey }: CreateOpComparisonMutationVariables) =>
      api.create(request, idempotencyKey),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: opComparisonQueryKeys.all });
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
